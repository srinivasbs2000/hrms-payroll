package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.CtcPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.CtcPolicyTreatmentView;
import com.acme.hrms.payroll.compensation.CtcPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.CtcPolicyView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.CtcPolicyRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class CtcPolicyService {
  private static final String OBJECT_TYPE = "CTC_POLICY";

  private final CtcPolicyRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final AuditReader auditReader;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public CtcPolicyService(
      CtcPolicyRepository repository,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      AuditReader auditReader,
      DomainEventFactory events,
      OutboxWriter outbox,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
    this.audit = audit;
    this.auditReader = auditReader;
    this.events = events;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public CtcPolicyView create(
      String key,
      CtcPolicyCreateRequest request) {
    request.validate();
    return idempotent(
        "ctc-policy:create",
        key,
        request,
        () -> {
          CtcPolicyView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public CtcPolicyView addVersion(
      UUID identityId,
      String key,
      CtcPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "ctc-policy:version-create:" + identityId,
        key,
        request,
        () -> {
          CtcPolicyView created = repository.addVersion(
              identityId,
              request,
              null,
              actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public CtcPolicyView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      CtcPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "ctc-policy:version-correct:" + versionId,
        key,
        request,
        () -> {
          CtcPolicyView previous = repository.version(versionId);
          requireIdentity(previous, identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom()
                  .isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft CTC policy version "
                    + "can be corrected");
          }
          CtcPolicyView corrected = repository.addVersion(
              identityId,
              request,
              versionId,
              actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public CtcPolicyView approve(
      UUID identityId,
      UUID versionId,
      String key) {
    return idempotent(
        "ctc-policy:version-approve:"
            + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        () -> {
          CtcPolicyView before = repository.version(versionId);
          requireIdentity(before, identityId);
          CtcPolicyView approved = repository.approve(
              versionId,
              actor.require(),
              clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public CtcPolicyView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "ctc-policy:version-end-date:" + versionId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        () -> {
          CtcPolicyView before = repository.version(versionId);
          requireIdentity(before, identityId);
          CtcPolicyView ended = repository.endDate(
              versionId,
              effectiveTo,
              expectedVersion,
              actor.require(),
              clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public CtcPolicyView retire(
      UUID identityId,
      String key,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason) {
    if (effectiveDate == null) {
      throw new IllegalArgumentException(
          "retirement effective date is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException(
          "retirement reason is required");
    }
    return idempotent(
        "ctc-policy:retire:" + identityId,
        key,
        Map.of(
            "effectiveDate", effectiveDate,
            "expectedVersion", expectedVersion,
            "reason", reason),
        () -> {
          CtcPolicyView before = repository.latest(identityId);
          CtcPolicyView retired = repository.retire(
              identityId,
              effectiveDate,
              expectedVersion,
              reason,
              actor.require(),
              clock.instant());
          record("RETIRED", retired, before);
          return retired;
        });
  }

  public List<CtcPolicyView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public CtcPolicyView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId,
            effectiveDate(asOf)));
  }

  public List<CtcPolicyView> history(UUID identityId) {
    return transactions.read(
        () -> repository.history(identityId));
  }

  public List<AuditReader.AuditEventView> audit(
      UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private void record(
      String action,
      CtcPolicyView after,
      CtcPolicyView before) {
    String principal = actor.require();
    Map<String, Object> beforeState = summary(before);
    Map<String, Object> afterState = summary(after);

    audit.append(
        action,
        OBJECT_TYPE,
        after.identityId(),
        beforeState,
        afterState,
        Map.of(
            "versionId", after.versionId(),
            "configurationHash", configurationHash(after)),
        principal);

    var event = events.create(
        "CtcPolicy" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.identityId(),
        after.versionSequence(),
        afterState);
    outbox.append(event);
  }

  private Map<String, Object> summary(CtcPolicyView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("versionSequence", view.versionSequence());
    state.put("annualisationMethod", view.annualisationMethod());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("residualComponentId", view.residualComponentId());
    state.put(
        "residualComponentVersionId",
        view.residualComponentVersionId());
    state.put("treatmentCount", view.treatments().size());
    state.put(
        "costViews",
        view.treatments().stream()
            .map(CtcPolicyTreatmentView::costView)
            .distinct()
            .sorted()
            .toList());
    state.put("configurationHash", configurationHash(view));
    state.put(
        "retirementEffectiveDate",
        view.retirementEffectiveDate());
    return state;
  }

  private String configurationHash(CtcPolicyView view) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("versionId", view.versionId());
    configuration.put("name", view.name());
    configuration.put("currency", view.currency());
    configuration.put(
        "annualisationMethod",
        view.annualisationMethod());
    configuration.put("toleranceAmount", view.toleranceAmount());
    configuration.put(
        "residualComponentVersionId",
        view.residualComponentVersionId());
    configuration.put("effectiveFrom", view.effectiveFrom());
    configuration.put("effectiveTo", view.effectiveTo());
    configuration.put(
        "treatments",
        view.treatments().stream()
            .sorted(java.util.Comparator.comparingInt(
                CtcPolicyTreatmentView::treatmentSequence))
            .map(this::treatmentConfiguration)
            .toList());
    return canonical.hash(configuration);
  }

  private Map<String, Object> treatmentConfiguration(
      CtcPolicyTreatmentView treatment) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put(
        "componentVersionId",
        treatment.componentVersionId());
    configuration.put(
        "treatmentSequence",
        treatment.treatmentSequence());
    configuration.put("costView", treatment.costView());
    configuration.put(
        "treatmentType",
        treatment.treatmentType());
    configuration.put("fixedValue", treatment.fixedValue());
    configuration.put(
        "targetPercentage",
        treatment.targetPercentage());
    configuration.put(
        "payrollBaseVersionId",
        treatment.payrollBaseVersionId());
    configuration.put(
        "effectiveFrom",
        treatment.effectiveFrom());
    configuration.put("effectiveTo", treatment.effectiveTo());
    return configuration;
  }

  private CtcPolicyView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<CtcPolicyView> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required");
    }

    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException(
              "Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException(
              "Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(
              saved.get().body(),
              CtcPolicyView.class);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored idempotent response is invalid",
              exception);
        }
      }

      try {
        idempotency.reserve(
            operation,
            key,
            requestHash,
            clock.instant().plus(Duration.ofHours(24)));
      } catch (IllegalStateException exception) {
        throw new ConflictException(
            "Idempotency-Key is already in use",
            exception);
      }

      CtcPolicyView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      CtcPolicyView version,
      UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to CTC policy identity");
    }
  }
}
