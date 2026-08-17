package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanCreateRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanView;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionValidationRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionValidationView;
import com.acme.hrms.payroll.compensation.EligibilityRuleView.EvaluationView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.FlexBenefitPlanRepository;
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
public class FlexBenefitPlanService {
  private static final String OBJECT_TYPE = "FLEX_BENEFIT_PLAN";
  private final FlexBenefitPlanRepository repository;
  private final EligibilityRuleService eligibilityRules;
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

  public FlexBenefitPlanService(
      FlexBenefitPlanRepository repository, EligibilityRuleService eligibilityRules, TenantTransactionExecutor transactions,
      AuthenticatedActor actor, Clock clock, AuditWriter audit, AuditReader auditReader,
      DomainEventFactory events, OutboxWriter outbox, IdempotencyStore idempotency,
      CanonicalJsonHasher canonical, ObjectMapper objectMapper) {
    this.repository = repository;
    this.eligibilityRules = eligibilityRules;
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

  public FlexBenefitPlanView create(String key, FlexBenefitPlanCreateRequest request) {
    request.validate();
    return idempotent("flex-benefit-plan:create", key, request, () -> {
      FlexBenefitPlanView created = repository.create(request, actor.require());
      record("CREATED", created, null);
      return created;
    });
  }

  public FlexBenefitPlanView addVersion(
      UUID identityId, String key, FlexBenefitPlanVersionWriteRequest request) {
    request.validate();
    return idempotent("flex-benefit-plan:version-create:" + identityId, key, request, () -> {
      FlexBenefitPlanView created = repository.addVersion(identityId, request, null, actor.require());
      record("VERSION_CREATED", created, null);
      return created;
    });
  }

  public FlexBenefitPlanView correctFuture(
      UUID identityId, UUID versionId, String key, FlexBenefitPlanVersionWriteRequest request) {
    request.validate();
    return idempotent("flex-benefit-plan:version-correct:" + versionId, key, request, () -> {
      FlexBenefitPlanView previous = repository.version(versionId);
      requireIdentity(previous, identityId);
      if (!"DRAFT".equals(previous.approvalStatus()) || previous.superseded()
          || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
        throw new ConflictException("Only a non-superseded future flex-benefit draft can be corrected");
      }
      FlexBenefitPlanView corrected =
          repository.addVersion(identityId, request, versionId, actor.require());
      record("VERSION_CORRECTED", corrected, previous);
      return corrected;
    });
  }

  public FlexBenefitPlanView approve(UUID identityId, UUID versionId, String key) {
    return idempotent(
        "flex-benefit-plan:version-approve:" + versionId, key,
        Map.of("identityId", identityId, "versionId", versionId), () -> {
          FlexBenefitPlanView before = repository.version(versionId);
          requireIdentity(before, identityId);
          FlexBenefitPlanView approved = repository.approve(versionId, actor.require(), clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public List<FlexBenefitPlanView> list(LocalDate asOf) {
    return transactions.read(() -> repository.list(effectiveDate(asOf)));
  }
  public FlexBenefitPlanView current(UUID identityId, LocalDate asOf) {
    return transactions.read(() -> repository.current(identityId, effectiveDate(asOf)));
  }
  public List<FlexBenefitPlanView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }
  public FlexElectionValidationView validateElection(
      UUID identityId, UUID versionId, FlexElectionValidationRequest request) {
    request.validate();
    FlexBenefitPlanView plan = transactions.read(() -> {
      FlexBenefitPlanView loaded = repository.version(versionId);
      requireIdentity(loaded, identityId);
      return loaded;
    });
    FlexElectionValidationView structural =
        FlexBenefitPlanControls.validateElection(plan, request);
    if (plan.eligibilityRuleVersionId() == null) {
      return structural;
    }
    EvaluationView eligibility = eligibilityRules.evaluate(
        plan.eligibilityRuleId(),
        plan.eligibilityRuleVersionId(),
        request.resolvedEligibilityFacts());
    List<String> blockers = new java.util.ArrayList<>(structural.blockers());
    List<String> warnings = new java.util.ArrayList<>(structural.warnings());
    if ("NOT_ELIGIBLE".equals(eligibility.result())) {
      blockers.add("FLEX_PLAN_NOT_ELIGIBLE");
    } else if ("REQUIRES_APPROVAL".equals(eligibility.result())
        && !request.approvedPolicyException()) {
      blockers.add("FLEX_PLAN_ELIGIBILITY_REQUIRES_APPROVAL");
    } else if ("REQUIRES_APPROVAL".equals(eligibility.result())) {
      warnings.add("FLEX_PLAN_ELIGIBILITY_APPROVAL_SUPPLIED");
    }
    return new FlexElectionValidationView(
        blockers.isEmpty() ? "PASS" : "FAIL",
        structural.annualBasketAmount(),
        structural.electedAnnualAmount(),
        structural.residualAnnualAmount(),
        structural.residualTreatment(),
        List.copyOf(blockers),
        List.copyOf(warnings),
        structural.disclaimer());
  }
  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(() -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private void record(String action, FlexBenefitPlanView after, FlexBenefitPlanView before) {
    String principal = actor.require();
    Map<String, Object> beforeState = summary(before);
    Map<String, Object> afterState = summary(after);
    audit.append(action, OBJECT_TYPE, after.identityId(), beforeState, afterState,
        Map.of("versionId", after.versionId(), "configurationHash", configurationHash(after)), principal);
    var event = events.create(
        "FlexBenefitPlan" + action, 1, TenantContext.require(), null, OBJECT_TYPE,
        after.identityId(), after.versionSequence(), afterState);
    outbox.append(event);
  }

  private Map<String, Object> summary(FlexBenefitPlanView view) {
    if (view == null) return null;
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("versionSequence", view.versionSequence());
    state.put("approvalStatus", view.approvalStatus());
    state.put("supplementalPlanVersionId", view.supplementalPlanVersionId());
    state.put("annualBasketAmount", view.annualBasketAmount());
    state.put("electionWindowStart", view.electionWindowStart());
    state.put("electionWindowEnd", view.electionWindowEnd());
    state.put("unusedBalanceRule", view.unusedBalanceRule());
    state.put("optionCount", view.options().size());
    state.put("configurationHash", configurationHash(view));
    return state;
  }

  private String configurationHash(FlexBenefitPlanView view) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("versionId", view.versionId());
    configuration.put("name", view.name());
    configuration.put("currency", view.currency());
    configuration.put("supplementalPlanVersionId", view.supplementalPlanVersionId());
    configuration.put("eligibilityRuleVersionId", view.eligibilityRuleVersionId());
    configuration.put("annualBasketAmount", view.annualBasketAmount());
    configuration.put("electionWindowStart", view.electionWindowStart());
    configuration.put("electionWindowEnd", view.electionWindowEnd());
    configuration.put("midYearJoiningRule", view.midYearJoiningRule());
    configuration.put("joiningElectionWindowDays", view.joiningElectionWindowDays());
    configuration.put("midYearChangeRule", view.midYearChangeRule());
    configuration.put("unusedBalanceRule", view.unusedBalanceRule());
    configuration.put("carryForwardLimit", view.carryForwardLimit());
    configuration.put("taxableFallbackComponentVersionId", view.taxableFallbackComponentVersionId());
    configuration.put("encashmentComponentVersionId", view.encashmentComponentVersionId());
    configuration.put("finalSettlementRule", view.finalSettlementRule());
    configuration.put("retroCorrectionRule", view.retroCorrectionRule());
    configuration.put("allowTotalCompensationChange", view.allowTotalCompensationChange());
    configuration.put("effectiveFrom", view.effectiveFrom());
    configuration.put("effectiveTo", view.effectiveTo());
    configuration.put("options", view.options());
    return canonical.hash(configuration);
  }

  private FlexBenefitPlanView idempotent(
      String operation, String key, Object request, Supplier<FlexBenefitPlanView> work) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException("Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(saved.get().body(), FlexBenefitPlanView.class);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Stored idempotent response is invalid", exception);
        }
      }
      try {
        idempotency.reserve(operation, key, requestHash, clock.instant().plus(Duration.ofHours(24)));
      } catch (IllegalStateException exception) {
        throw new ConflictException("Idempotency-Key is already in use", exception);
      }
      FlexBenefitPlanView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }
  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }
  private void requireIdentity(FlexBenefitPlanView version, UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException("Version does not belong to flex-benefit plan identity");
    }
  }
}
