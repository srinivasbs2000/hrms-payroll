package com.acme.hrms.payroll.compensation.internal.application;
import com.acme.hrms.payroll.compensation.SalaryStructureEventContract;

import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.CompatibilityEvaluationView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.RetireBindingRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.RuleVersionOption;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureStatutoryCompatibilityRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
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
public class SalaryStructureStatutoryCompatibilityService {
  private static final String OBJECT_TYPE = "SALARY_STRUCTURE";
  private final SalaryStructureStatutoryCompatibilityRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public SalaryStructureStatutoryCompatibilityService(
      SalaryStructureStatutoryCompatibilityRepository repository,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
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
    this.events = events;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public List<RuleVersionOption> ruleVersions(LocalDate asOf) {
    LocalDate effectiveDate = asOf == null ? LocalDate.now(clock) : asOf;
    return transactions.read(() -> repository.ruleVersions(effectiveDate));
  }

  public List<BindingView> bindings(UUID identityId, UUID versionId) {
    return transactions.read(() -> {
      repository.requireStructureVersion(identityId, versionId);
      return repository.bindings(versionId);
    });
  }

  public BindingView bind(
      UUID identityId,
      UUID versionId,
      String key,
      BindingRequest request) {
    request.validate();
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("identityId", identityId);
    command.put("versionId", versionId);
    command.put("request", request);
    return idempotent(
        "salary-structure:statutory-bind:" + versionId,
        key,
        command,
        BindingView.class,
        () -> {
          repository.requireStructureVersion(identityId, versionId);
          BindingView created = repository.bind(
              identityId,
              versionId,
              request,
              actor.require(),
              clock.instant());
          recordBinding("STATUTORY_BINDING_CREATED", identityId, created, null);
          return created;
        });
  }

  public BindingView retire(
      UUID identityId,
      UUID versionId,
      UUID bindingId,
      String key,
      RetireBindingRequest request) {
    request.validate();
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("identityId", identityId);
    command.put("versionId", versionId);
    command.put("bindingId", bindingId);
    command.put("expectedVersion", request.expectedVersion());
    return idempotent(
        "salary-structure:statutory-retire:" + bindingId,
        key,
        command,
        BindingView.class,
        () -> {
          repository.requireStructureVersion(identityId, versionId);
          BindingView before = repository.binding(bindingId);
          BindingView retired = repository.retire(
              identityId,
              versionId,
              bindingId,
              request.expectedVersion(),
              actor.require(),
              clock.instant());
          recordBinding(
              "STATUTORY_BINDING_RETIRED",
              identityId,
              retired,
              before);
          return retired;
        });
  }

  public CompatibilityEvaluationView evaluate(
      UUID identityId,
      UUID versionId,
      UUID validationId,
      String key) {
    Map<String, Object> command = Map.of(
        "identityId", identityId,
        "versionId", versionId,
        "validationId", validationId);
    return idempotent(
        "salary-structure:statutory-evaluate:" + validationId,
        key,
        command,
        CompatibilityEvaluationView.class,
        () -> {
          repository.requireStructureVersion(identityId, versionId);
          CompatibilityEvaluationView evaluated = repository.evaluate(
              identityId,
              versionId,
              validationId,
              actor.require(),
              clock.instant());
          recordEvaluation(identityId, evaluated);
          return evaluated;
        });
  }

  public List<CompatibilityEvaluationView> evaluations(
      UUID identityId,
      UUID versionId,
      UUID validationId) {
    return transactions.read(() -> {
      repository.requireStructureVersion(identityId, versionId);
      return repository.evaluations(versionId, validationId);
    });
  }

  private void recordBinding(
      String action,
      UUID identityId,
      BindingView after,
      BindingView before) {
    String principal = actor.require();
    Map<String, Object> beforeState = bindingSummary(before);
    Map<String, Object> afterState = bindingSummary(after);
    audit.append(
        action,
        OBJECT_TYPE,
        identityId,
        beforeState,
        afterState,
        Map.of(
            "source", "P5-SSC-01-E04-009",
            "versionId", after.salaryStructureVersionId(),
            "bindingId", after.bindingId()),
        principal);

    String eventType =
        SalaryStructureEventContract.statutoryBindingEventType(action);
    var event = events.create(
        eventType,
        SalaryStructureEventContract.SCHEMA_VERSION,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        identityId,
        after.versionNo() + 1,
        SalaryStructureEventContract.validatePayload(
            eventType,
            afterState));
    outbox.append(event);
  }

  private void recordEvaluation(
      UUID identityId,
      CompatibilityEvaluationView evaluation) {
    String principal = actor.require();
    Map<String, Object> state = evaluationSummary(evaluation);
    audit.append(
        "STATUTORY_COMPATIBILITY_EVALUATED",
        OBJECT_TYPE,
        identityId,
        null,
        state,
        Map.of(
            "source", "P5-SSC-01-E04-009",
            "versionId", evaluation.salaryStructureVersionId(),
            "validationId", evaluation.validationId()),
        principal);
    var event = events.create(
        SalaryStructureEventContract.STATUTORY_COMPATIBILITY_EVALUATED,
        SalaryStructureEventContract.SCHEMA_VERSION,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        identityId,
        evaluation.statutoryBindingRevision() + 1,
        SalaryStructureEventContract.validatePayload(
            SalaryStructureEventContract.STATUTORY_COMPATIBILITY_EVALUATED,
            state));
    outbox.append(event);
  }

  private Map<String, Object> bindingSummary(BindingView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("bindingId", view.bindingId());
    state.put("salaryStructureVersionId", view.salaryStructureVersionId());
    state.put("statutoryRuleId", view.statutoryRuleId());
    state.put("statutoryRuleVersionId", view.statutoryRuleVersionId());
    state.put("bindingPurpose", view.bindingPurpose());
    state.put("enforcementLevel", view.enforcementLevel());
    state.put("componentVersionId", view.componentVersionId());
    state.put("status", view.status());
    state.put("versionNo", view.versionNo());
    return state;
  }

  private Map<String, Object> evaluationSummary(
      CompatibilityEvaluationView evaluation) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("evaluationId", evaluation.evaluationId());
    state.put("validationId", evaluation.validationId());
    state.put("salaryStructureVersionId", evaluation.salaryStructureVersionId());
    state.put("statutoryBindingRevision", evaluation.statutoryBindingRevision());
    state.put("validationStatus", evaluation.validationStatus());
    state.put("blockingIssueCount", evaluation.blockingIssueCount());
    state.put("advisoryIssueCount", evaluation.advisoryIssueCount());
    state.put("evidenceHash", evaluation.evidenceHash());
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
          return objectMapper.readValue(saved.get().body(), responseType);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored idempotent response is invalid", exception);
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
            "Idempotency-Key is already in use", exception);
      }
      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }
}
