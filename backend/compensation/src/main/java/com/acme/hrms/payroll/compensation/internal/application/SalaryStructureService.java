package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.SalaryStructureWriteRequest;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureRepository;
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
public class SalaryStructureService {
  private static final String OBJECT_TYPE =
      "SALARY_STRUCTURE";

  private final SalaryStructureRepository repository;
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

  public SalaryStructureService(
      SalaryStructureRepository repository,
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

  public SalaryStructureView create(
      String key,
      SalaryStructureWriteRequest request) {
    request.validate(true);

    return idempotent(
        "salary-structure:create",
        key,
        request,
        () -> {
          SalaryStructureView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public SalaryStructureView addVersion(
      UUID identityId,
      String key,
      SalaryStructureWriteRequest request) {
    request.validate(false);

    return idempotent(
        "salary-structure:version-create:" + identityId,
        key,
        request,
        () -> {
          SalaryStructureView created =
              repository.addVersion(
                  identityId,
                  request,
                  null,
                  actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public SalaryStructureView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      SalaryStructureWriteRequest request) {
    request.validate(false);

    return idempotent(
        "salary-structure:version-correct:" + versionId,
        key,
        request,
        () -> {
          SalaryStructureView previous =
              repository.version(versionId);
          requireIdentity(previous, identityId);

          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom()
                  .isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft "
                    + "salary-structure version can be corrected");
          }

          SalaryStructureView corrected =
              repository.addVersion(
                  identityId,
                  request,
                  versionId,
                  actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public SalaryStructureView approve(
      UUID identityId,
      UUID versionId,
      String key) {
    return idempotent(
        "salary-structure:version-approve:"
            + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        () -> {
          SalaryStructureView before =
              repository.version(versionId);
          requireIdentity(before, identityId);

          SalaryStructureView approved =
              repository.approve(
                  versionId,
                  actor.require(),
                  clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public SalaryStructureView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "salary-structure:version-end-date:" + versionId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        () -> {
          SalaryStructureView before =
              repository.version(versionId);
          requireIdentity(before, identityId);

          SalaryStructureView ended =
              repository.endDate(
                  versionId,
                  effectiveTo,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public List<SalaryStructureView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public SalaryStructureView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId,
            effectiveDate(asOf)));
  }

  public List<SalaryStructureView> history(
      UUID identityId) {
    return transactions.read(
        () -> repository.history(identityId));
  }

  public List<AuditReader.AuditEventView> audit(
      UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(
            OBJECT_TYPE,
            identityId));
  }

  private void record(
      String action,
      SalaryStructureView after,
      SalaryStructureView before) {
    String principal = actor.require();

    audit.append(
        action,
        OBJECT_TYPE,
        after.identityId(),
        state(before),
        state(after),
        Map.of("versionId", after.versionId()),
        principal);

    var event = events.create(
        "SalaryStructure" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.identityId(),
        after.versionSequence(),
        state(after));

    outbox.append(event);
  }

  private Map<String, Object> state(
      SalaryStructureView view) {
    if (view == null) {
      return null;
    }

    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("currency", view.currency());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put(
        "lines",
        view.lines().stream()
            .map(this::lineState)
            .toList());
    return state;
  }

  private Map<String, Object> lineState(
      SalaryStructureLineView line) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", line.id());
    state.put(
        "componentVersionId",
        line.componentVersionId());
    state.put("componentCode", line.componentCode());
    state.put("sequenceNo", line.sequenceNo());
    state.put("targetAmount", line.targetAmount());
    state.put(
        "targetPercentage",
        line.targetPercentage());
    state.put(
        "percentageBaseCode",
        line.percentageBaseCode());
    return state;
  }

  private SalaryStructureView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<SalaryStructureView> work) {
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
              "Idempotency-Key was already used "
                  + "with a different request");
        }

        if (!saved.get().completed()) {
          throw new ConflictException(
              "Idempotent operation is still in progress");
        }

        try {
          return objectMapper.readValue(
              saved.get().body(),
              SalaryStructureView.class);
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

      SalaryStructureView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null
        ? LocalDate.now(clock)
        : asOf;
  }

  private void requireIdentity(
      SalaryStructureView version,
      UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to "
              + "salary-structure identity");
    }
  }
}