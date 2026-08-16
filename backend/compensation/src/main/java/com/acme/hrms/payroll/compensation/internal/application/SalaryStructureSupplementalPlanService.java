package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanCreateRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureSupplementalPlanRepository;
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
public class SalaryStructureSupplementalPlanService {
  private static final String PLAN_OBJECT = "SALARY_SUPPLEMENTAL_PLAN";
  private static final String STRUCTURE_OBJECT = "SALARY_STRUCTURE";

  private final SalaryStructureSupplementalPlanRepository repository;
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

  public SalaryStructureSupplementalPlanService(
      SalaryStructureSupplementalPlanRepository repository,
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

  public SupplementalPlanView create(
      String key,
      SupplementalPlanCreateRequest request) {
    request.validate();
    return idempotent(
        "salary-supplemental-plan:create",
        key,
        request,
        SupplementalPlanView.class,
        () -> {
          SupplementalPlanView created =
              repository.create(request, actor.require());
          recordPlan("CREATED", created, null);
          return created;
        });
  }

  public SupplementalPlanView addVersion(
      UUID identityId,
      String key,
      SupplementalPlanVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "salary-supplemental-plan:version-create:" + identityId,
        key,
        request,
        SupplementalPlanView.class,
        () -> {
          SupplementalPlanView created =
              repository.addVersion(identityId, request, actor.require());
          recordPlan("VERSION_CREATED", created, null);
          return created;
        });
  }

  public SupplementalPlanView approve(
      UUID identityId,
      UUID versionId,
      String key) {
    return idempotent(
        "salary-supplemental-plan:approve:" + versionId,
        key,
        Map.of("identityId", identityId, "versionId", versionId),
        SupplementalPlanView.class,
        () -> {
          SupplementalPlanView before = repository.version(versionId);
          requireIdentity(before, identityId);
          SupplementalPlanView approved =
              repository.approve(versionId, actor.require(), clock.instant());
          recordPlan("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public List<SupplementalPlanView> list(LocalDate asOf) {
    return transactions.read(() -> repository.list(effectiveDate(asOf)));
  }

  public SupplementalPlanView current(UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  public List<SupplementalPlanView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }

  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(PLAN_OBJECT, identityId));
  }

  public SupplementalPlanBindingView bind(
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      String key,
      SupplementalPlanBindingWriteRequest request) {
    request.validate();
    return idempotent(
        "salary-structure:supplemental-plan-bind:"
            + salaryStructureVersionId,
        key,
        request,
        SupplementalPlanBindingView.class,
        () -> {
          SupplementalPlanBindingView created =
              repository.bind(
                  salaryStructureId,
                  salaryStructureVersionId,
                  request,
                  actor.require());
          recordBinding(created);
          return created;
        });
  }

  public List<SupplementalPlanBindingView> bindings(
      UUID salaryStructureId,
      UUID salaryStructureVersionId) {
    return transactions.read(
        () -> repository.bindings(
            salaryStructureId,
            salaryStructureVersionId));
  }

  private void recordPlan(
      String action,
      SupplementalPlanView after,
      SupplementalPlanView before) {
    String principal = actor.require();
    Map<String, Object> beforeState = planSummary(before);
    Map<String, Object> afterState = planSummary(after);

    audit.append(
        action,
        PLAN_OBJECT,
        after.identityId(),
        beforeState,
        afterState,
        Map.of(
            "versionId",
            after.versionId(),
            "configurationHash",
            canonical.hash(afterState)),
        principal);

    var event = events.create(
        "SalarySupplementalPlan" + action,
        1,
        TenantContext.require(),
        null,
        PLAN_OBJECT,
        after.identityId(),
        after.versionSequence(),
        afterState);
    outbox.append(event);
  }

  private void recordBinding(SupplementalPlanBindingView binding) {
    String principal = actor.require();
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("bindingId", binding.bindingId());
    state.put(
        "salaryStructureVersionId",
        binding.salaryStructureVersionId());
    state.put("supplementalPlanId", binding.supplementalPlanId());
    state.put(
        "supplementalPlanVersionId",
        binding.supplementalPlanVersionId());
    state.put("sequenceNo", binding.sequenceNo());
    state.put("effectiveFrom", binding.effectiveFrom());
    state.put("effectiveTo", binding.effectiveTo());
    state.put("compositionRevision", binding.compositionRevision());

    audit.append(
        "SUPPLEMENTAL_PLAN_BOUND",
        STRUCTURE_OBJECT,
        binding.salaryStructureId(),
        null,
        state,
        Map.of(
            "salaryStructureVersionId",
            binding.salaryStructureVersionId(),
            "compositionRevision",
            binding.compositionRevision(),
            "configurationHash",
            canonical.hash(state)),
        principal);

    var event = events.create(
        "SalaryStructureSupplementalPlanBound",
        1,
        TenantContext.require(),
        null,
        STRUCTURE_OBJECT,
        binding.salaryStructureId(),
        binding.compositionRevision(),
        state);
    outbox.append(event);
  }

  private Map<String, Object> planSummary(SupplementalPlanView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("versionSequence", view.versionSequence());
    state.put("name", view.name());
    state.put("planType", view.planType());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("lineCount", view.lines().size());
    state.put(
        "lines",
        view.lines().stream()
            .map(line -> {
              Map<String, Object> lineState = new LinkedHashMap<>();
              lineState.put("lineId", line.lineId());
              lineState.put(
                  "componentVersionId",
                  line.componentVersionId());
              lineState.put("sequenceNo", line.sequenceNo());
              lineState.put("defaultAmount", line.defaultAmount());
              lineState.put(
                  "defaultPercentage",
                  line.defaultPercentage());
              lineState.put(
                  "percentageBaseComponentVersionId",
                  line.percentageBaseComponentVersionId());
              lineState.put("minimumAmount", line.minimumAmount());
              lineState.put("maximumAmount", line.maximumAmount());
              lineState.put(
                  "employeeOverrideAllowed",
                  line.employeeOverrideAllowed());
              lineState.put("effectiveFrom", line.effectiveFrom());
              lineState.put("effectiveTo", line.effectiveTo());
              return lineState;
            })
            .toList());
    return state;
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<T> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
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
              responseType);
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

      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      SupplementalPlanView version,
      UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to supplemental-plan identity");
    }
  }
}
