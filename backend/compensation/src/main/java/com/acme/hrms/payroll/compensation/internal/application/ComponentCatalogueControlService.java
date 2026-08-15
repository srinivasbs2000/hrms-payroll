package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.ComponentCatalogueControls;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ComponentImpactView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaDependencyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaValidationRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaValidationView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateLookupView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.StatutoryWageReferenceView;
import com.acme.hrms.payroll.compensation.PayComponentVersionWriteRequest;
import com.acme.hrms.payroll.compensation.PayComponentView;
import com.acme.hrms.payroll.compensation.internal.formula.CalculationPhase;
import com.acme.hrms.payroll.compensation.internal.formula.CompiledFormula;
import com.acme.hrms.payroll.compensation.internal.formula.ComponentDependencyPlanner;
import com.acme.hrms.payroll.compensation.internal.formula.ComponentFormulaDefinition;
import com.acme.hrms.payroll.compensation.internal.formula.RestrictedFormulaCompiler;
import com.acme.hrms.payroll.compensation.internal.infrastructure.ComponentCatalogueControlRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.ComponentCatalogueControlRepository.DependencyTarget;
import com.acme.hrms.payroll.compensation.internal.infrastructure.ComponentCatalogueControlRepository.PlanningRow;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ComponentCatalogueControlService {
  private static final String RATE_OBJECT = "COMPONENT_RATE_TABLE";
  private static final String ROUNDING_OBJECT = "COMPONENT_ROUNDING_POLICY";
  private static final String PRORATION_OBJECT = "COMPONENT_PRORATION_POLICY";

  private final ComponentCatalogueControlRepository repository;
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
  private final RestrictedFormulaCompiler formulaCompiler = new RestrictedFormulaCompiler();
  private final ComponentDependencyPlanner dependencyPlanner = new ComponentDependencyPlanner();

  public ComponentCatalogueControlService(
      ComponentCatalogueControlRepository repository,
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

  public FormulaValidationView validateFormula(FormulaValidationRequest request) {
    String phase = request.resolvedCalculationPhase();
    String result = request.resolvedResultContract();
    CompiledFormula compiled = formulaCompiler.compile(request.expression());
    return new FormulaValidationView(
        compiled.canonicalExpression(),
        List.copyOf(compiled.dependencies()),
        phase,
        result,
        fingerprint(compiled.canonicalExpression(), phase, result));
  }

  /** Called by PayComponentService inside its existing write transaction. */
  public void captureFormula(PayComponentView component, PayComponentVersionWriteRequest request) {
    if (!repository.formulaExists(component.versionId())) {
      captureFormula(
          component,
          request.formulaType(),
          request.formulaExpression(),
          request.fixedAmount(),
          request.resolvedCalculationPhase(),
          request.resolvedResultContract());
    }
    if (!request.resolvedStatutoryWageReferences().isEmpty()) {
      repository.persistStatutoryWageReferences(
          component, request.resolvedStatutoryWageReferences(), actor.require());
    }
  }

  /** Ensures pre-V040 drafts fail closed at runtime approval rather than bypassing formula evidence. */
  public void ensureFormulaReady(PayComponentView component) {
    if (repository.formulaExists(component.versionId())) {
      return;
    }
    captureFormula(
        component,
        component.formulaType(),
        component.formulaExpression(),
        component.fixedAmount(),
        "FIXED".equals(component.formulaType()) ? "INPUT" : "PRE_TAX",
        "DECIMAL");
  }

  public Map<String, Object> formulaEvidence(UUID componentVersionId) {
    return repository.formulaEvidence(componentVersionId);
  }

  public List<FormulaDependencyView> dependencies(UUID componentId) {
    return transactions.read(() -> repository.dependencies(componentId));
  }

  public ComponentImpactView impact(UUID componentId) {
    return transactions.read(() -> repository.impact(componentId));
  }

  public List<StatutoryWageReferenceView> statutoryWageReferences(UUID componentId) {
    return transactions.read(() -> repository.statutoryWageReferences(componentId));
  }

  public RateTableView createRateTable(String key, RateTableCreateRequest request) {
    request.validate();
    return idempotent(
        "component-rate-table:create", key, request, RateTableView.class,
        () -> {
          RateTableView created = repository.createRateTable(request, actor.require());
          recordRate("CREATED", created, null);
          return created;
        });
  }

  public RateTableView addRateTableVersion(
      UUID identityId, String key, RateTableVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-rate-table:version-create:" + identityId,
        key,
        request,
        RateTableView.class,
        () -> {
          RateTableView created = repository.addRateTableVersion(identityId, request, null, actor.require());
          recordRate("VERSION_CREATED", created, null);
          return created;
        });
  }

  public RateTableView correctFutureRateTableVersion(
      UUID identityId, UUID versionId, String key, RateTableVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-rate-table:version-correct:" + versionId, key, request, RateTableView.class,
        () -> {
          RateTableView previous = repository.rateTableVersion(versionId);
          requireIdentity(previous.identityId(), identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft rate-table version can be corrected");
          }
          RateTableView corrected = repository.addRateTableVersion(
              identityId, request, versionId, actor.require());
          recordRate("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public RateTableView approveRateTable(
      UUID identityId, UUID versionId, String key, long expectedVersion) {
    return idempotent(
        "component-rate-table:approve:" + versionId,
        key,
        Map.of("versionId", versionId, "expectedVersion", expectedVersion),
        RateTableView.class,
        () -> {
          RateTableView before = repository.rateTableVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          RateTableView approved = repository.approveRateTable(
              versionId, expectedVersion, actor.require(), clock.instant());
          recordRate("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public RateTableView endDateRateTable(
      UUID identityId, UUID versionId, String key, LocalDate effectiveTo, long expectedVersion) {
    if (effectiveTo == null) {
      throw new IllegalArgumentException("effectiveTo is required");
    }
    return idempotent(
        "component-rate-table:end-date:" + versionId,
        key,
        Map.of("effectiveTo", effectiveTo, "expectedVersion", expectedVersion),
        RateTableView.class,
        () -> {
          RateTableView before = repository.rateTableVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          RateTableView ended = repository.endDateRateTable(
              versionId, effectiveTo, expectedVersion, actor.require(), clock.instant());
          recordRate("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public RateTableView retireRateTable(
      UUID identityId, String key, LocalDate effectiveDate, long expectedVersion, String reason) {
    validateRetirement(effectiveDate, expectedVersion, reason);
    return idempotent(
        "component-rate-table:retire:" + identityId, key,
        Map.of("effectiveDate", effectiveDate, "expectedVersion", expectedVersion, "reason", reason),
        RateTableView.class,
        () -> {
          RateTableView before = repository.rateTableHistory(identityId).getLast();
          RateTableView retired = repository.retireRateTable(
              identityId, effectiveDate, expectedVersion, reason, actor.require(), clock.instant());
          recordRate("RETIRED", retired, before);
          return retired;
        });
  }

  public List<RateTableView> listRateTables(LocalDate asOf) {
    return transactions.read(() -> repository.listRateTables(effectiveDate(asOf)));
  }

  public RateTableView rateTable(UUID identityId, LocalDate asOf) {
    return transactions.read(() -> repository.currentRateTable(identityId, effectiveDate(asOf)));
  }

  public List<RateTableView> rateTableHistory(UUID identityId) {
    return transactions.read(() -> repository.rateTableHistory(identityId));
  }

  public RateLookupView lookupRate(UUID identityId, LocalDate asOf, Map<String, String> dimensions) {
    if (dimensions == null || dimensions.isEmpty()) {
      throw new IllegalArgumentException("dimensions are required");
    }
    return transactions.read(() -> {
      LocalDate date = effectiveDate(asOf);
      RateTableView current = repository.currentRateTable(identityId, date);
      ComponentCatalogueControls.validateRateDimensionValues(current.dimensions(), dimensions);
      return repository.lookupRate(identityId, date, new LinkedHashMap<>(dimensions));
    });
  }

  public RoundingPolicyView createRoundingPolicy(String key, RoundingPolicyCreateRequest request) {
    request.validate();
    return idempotent(
        "component-rounding-policy:create", key, request, RoundingPolicyView.class,
        () -> {
          RoundingPolicyView created = repository.createRoundingPolicy(request, actor.require());
          recordRounding("CREATED", created, null);
          return created;
        });
  }

  public RoundingPolicyView addRoundingPolicyVersion(
      UUID identityId, String key, RoundingPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-rounding-policy:version-create:" + identityId,
        key,
        request,
        RoundingPolicyView.class,
        () -> {
          RoundingPolicyView created = repository.addRoundingPolicyVersion(
              identityId, request, null, actor.require());
          recordRounding("VERSION_CREATED", created, null);
          return created;
        });
  }

  public RoundingPolicyView correctFutureRoundingPolicyVersion(
      UUID identityId, UUID versionId, String key, RoundingPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-rounding-policy:version-correct:" + versionId, key, request,
        RoundingPolicyView.class,
        () -> {
          RoundingPolicyView previous = repository.roundingVersion(versionId);
          requireIdentity(previous.identityId(), identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft rounding-policy version can be corrected");
          }
          RoundingPolicyView corrected = repository.addRoundingPolicyVersion(
              identityId, request, versionId, actor.require());
          recordRounding("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public RoundingPolicyView approveRoundingPolicy(
      UUID identityId, UUID versionId, String key, long expectedVersion) {
    return idempotent(
        "component-rounding-policy:approve:" + versionId,
        key,
        Map.of("versionId", versionId, "expectedVersion", expectedVersion),
        RoundingPolicyView.class,
        () -> {
          RoundingPolicyView before = repository.roundingVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          RoundingPolicyView approved = repository.approveRoundingPolicy(
              versionId, expectedVersion, actor.require(), clock.instant());
          recordRounding("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public RoundingPolicyView endDateRoundingPolicy(
      UUID identityId, UUID versionId, String key, LocalDate effectiveTo, long expectedVersion) {
    if (effectiveTo == null) {
      throw new IllegalArgumentException("effectiveTo is required");
    }
    return idempotent(
        "component-rounding-policy:end-date:" + versionId,
        key,
        Map.of("effectiveTo", effectiveTo, "expectedVersion", expectedVersion),
        RoundingPolicyView.class,
        () -> {
          RoundingPolicyView before = repository.roundingVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          RoundingPolicyView ended = repository.endDateRoundingPolicy(
              versionId, effectiveTo, expectedVersion, actor.require(), clock.instant());
          recordRounding("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public RoundingPolicyView retireRoundingPolicy(
      UUID identityId, String key, LocalDate effectiveDate, long expectedVersion, String reason) {
    validateRetirement(effectiveDate, expectedVersion, reason);
    return idempotent(
        "component-rounding-policy:retire:" + identityId, key,
        Map.of("effectiveDate", effectiveDate, "expectedVersion", expectedVersion, "reason", reason),
        RoundingPolicyView.class,
        () -> {
          RoundingPolicyView before = repository.roundingHistory(identityId).getLast();
          RoundingPolicyView retired = repository.retireRoundingPolicy(
              identityId, effectiveDate, expectedVersion, reason, actor.require(), clock.instant());
          recordRounding("RETIRED", retired, before);
          return retired;
        });
  }

  public List<RoundingPolicyView> listRoundingPolicies(LocalDate asOf) {
    return transactions.read(() -> repository.listRoundingPolicies(effectiveDate(asOf)));
  }

  public RoundingPolicyView roundingPolicy(UUID identityId, LocalDate asOf) {
    return transactions.read(() -> repository.currentRoundingPolicy(identityId, effectiveDate(asOf)));
  }

  public List<RoundingPolicyView> roundingHistory(UUID identityId) {
    return transactions.read(() -> repository.roundingHistory(identityId));
  }

  public ProrationPolicyView createProrationPolicy(String key, ProrationPolicyCreateRequest request) {
    request.validate();
    return idempotent(
        "component-proration-policy:create", key, request, ProrationPolicyView.class,
        () -> {
          ProrationPolicyView created = repository.createProrationPolicy(request, actor.require());
          recordProration("CREATED", created, null);
          return created;
        });
  }

  public ProrationPolicyView addProrationPolicyVersion(
      UUID identityId, String key, ProrationPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-proration-policy:version-create:" + identityId,
        key,
        request,
        ProrationPolicyView.class,
        () -> {
          ProrationPolicyView created = repository.addProrationPolicyVersion(
              identityId, request, null, actor.require());
          recordProration("VERSION_CREATED", created, null);
          return created;
        });
  }

  public ProrationPolicyView correctFutureProrationPolicyVersion(
      UUID identityId, UUID versionId, String key, ProrationPolicyVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "component-proration-policy:version-correct:" + versionId, key, request,
        ProrationPolicyView.class,
        () -> {
          ProrationPolicyView previous = repository.prorationVersion(versionId);
          requireIdentity(previous.identityId(), identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft proration-policy version can be corrected");
          }
          ProrationPolicyView corrected = repository.addProrationPolicyVersion(
              identityId, request, versionId, actor.require());
          recordProration("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public ProrationPolicyView approveProrationPolicy(
      UUID identityId, UUID versionId, String key, long expectedVersion) {
    return idempotent(
        "component-proration-policy:approve:" + versionId,
        key,
        Map.of("versionId", versionId, "expectedVersion", expectedVersion),
        ProrationPolicyView.class,
        () -> {
          ProrationPolicyView before = repository.prorationVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          ProrationPolicyView approved = repository.approveProrationPolicy(
              versionId, expectedVersion, actor.require(), clock.instant());
          recordProration("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public ProrationPolicyView endDateProrationPolicy(
      UUID identityId, UUID versionId, String key, LocalDate effectiveTo, long expectedVersion) {
    if (effectiveTo == null) {
      throw new IllegalArgumentException("effectiveTo is required");
    }
    return idempotent(
        "component-proration-policy:end-date:" + versionId,
        key,
        Map.of("effectiveTo", effectiveTo, "expectedVersion", expectedVersion),
        ProrationPolicyView.class,
        () -> {
          ProrationPolicyView before = repository.prorationVersion(versionId);
          requireIdentity(before.identityId(), identityId);
          ProrationPolicyView ended = repository.endDateProrationPolicy(
              versionId, effectiveTo, expectedVersion, actor.require(), clock.instant());
          recordProration("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public ProrationPolicyView retireProrationPolicy(
      UUID identityId, String key, LocalDate effectiveDate, long expectedVersion, String reason) {
    validateRetirement(effectiveDate, expectedVersion, reason);
    return idempotent(
        "component-proration-policy:retire:" + identityId, key,
        Map.of("effectiveDate", effectiveDate, "expectedVersion", expectedVersion, "reason", reason),
        ProrationPolicyView.class,
        () -> {
          ProrationPolicyView before = repository.prorationHistory(identityId).getLast();
          ProrationPolicyView retired = repository.retireProrationPolicy(
              identityId, effectiveDate, expectedVersion, reason, actor.require(), clock.instant());
          recordProration("RETIRED", retired, before);
          return retired;
        });
  }

  public List<ProrationPolicyView> listProrationPolicies(LocalDate asOf) {
    return transactions.read(() -> repository.listProrationPolicies(effectiveDate(asOf)));
  }

  public ProrationPolicyView prorationPolicy(UUID identityId, LocalDate asOf) {
    return transactions.read(() -> repository.currentProrationPolicy(identityId, effectiveDate(asOf)));
  }

  public List<ProrationPolicyView> prorationHistory(UUID identityId) {
    return transactions.read(() -> repository.prorationHistory(identityId));
  }

  public List<AuditReader.AuditEventView> rateAudit(UUID identityId) {
    return transactions.read(() -> auditReader.forObject(RATE_OBJECT, identityId));
  }

  public List<AuditReader.AuditEventView> roundingAudit(UUID identityId) {
    return transactions.read(() -> auditReader.forObject(ROUNDING_OBJECT, identityId));
  }

  public List<AuditReader.AuditEventView> prorationAudit(UUID identityId) {
    return transactions.read(() -> auditReader.forObject(PRORATION_OBJECT, identityId));
  }

  private void captureFormula(
      PayComponentView component,
      String formulaType,
      String formulaExpression,
      BigDecimal fixedAmount,
      String phase,
      String resultContract) {
    String canonicalExpression;
    Set<String> dependencyCodes;
    if ("FIXED".equals(formulaType)) {
      if (fixedAmount == null) {
        throw new IllegalArgumentException("FIXED formula metadata requires fixedAmount");
      }
      canonicalExpression = "FIXED(" + fixedAmount.stripTrailingZeros().toPlainString() + ")";
      dependencyCodes = Set.of();
    } else {
      CompiledFormula compiled = formulaCompiler.compile(formulaExpression);
      canonicalExpression = compiled.canonicalExpression();
      dependencyCodes = compiled.dependencies();
    }

    CalculationPhase sourcePhase = CalculationPhase.valueOf(phase);
    List<DependencyTarget> dependencies = new ArrayList<>();
    for (String dependencyCode : dependencyCodes) {
      DependencyTarget dependency = repository.resolveApprovedDependency(
          dependencyCode, component.effectiveFrom(), component.effectiveTo());
      if (dependency.componentId().equals(component.identityId())) {
        throw new IllegalArgumentException("SELF_DEPENDENCY: " + component.code());
      }
      CalculationPhase dependencyPhase = CalculationPhase.valueOf(dependency.phase());
      if (dependencyPhase.compareTo(sourcePhase) > 0) {
        throw new IllegalArgumentException(
            "LATER_PHASE_DEPENDENCY: " + component.code() + " references " + dependencyCode);
      }
      dependencies.add(dependency);
    }

    String fingerprint = fingerprint(canonicalExpression, phase, resultContract);
    String principal = actor.require();
    repository.persistFormula(
        component,
        formulaType,
        phase,
        resultContract,
        canonicalExpression,
        fingerprint,
        dependencies,
        principal);

    validateCompleteGraph();
  }

  private void validateCompleteGraph() {
    Map<String, ComponentFormulaDefinition> definitions = new LinkedHashMap<>();
    Set<String> externalInputs = new LinkedHashSet<>();
    for (PlanningRow row : repository.planningRows()) {
      if ("FIXED".equals(row.formulaType())) {
        externalInputs.add(row.code());
      } else if (row.formulaExpression() != null && !row.formulaExpression().isBlank()) {
        definitions.put(
            row.code(),
            new ComponentFormulaDefinition(
                row.formulaExpression(), CalculationPhase.valueOf(row.calculationPhase())));
      }
    }
    dependencyPlanner.plan(definitions, externalInputs);
  }

  private String fingerprint(String canonicalExpression, String phase, String resultContract) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String value = canonicalExpression + "\n" + phase + "\n" + resultContract;
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private void recordRate(String action, RateTableView after, RateTableView before) {
    record(
        RATE_OBJECT,
        "ComponentRateTable" + action,
        action,
        after.identityId(),
        after.versionSequence(),
        rateState(before),
        rateState(after),
        Map.of("versionId", after.versionId()));
  }

  private void recordRounding(
      String action, RoundingPolicyView after, RoundingPolicyView before) {
    record(
        ROUNDING_OBJECT,
        "ComponentRoundingPolicy" + action,
        action,
        after.identityId(),
        after.versionSequence(),
        roundingState(before),
        roundingState(after),
        Map.of("versionId", after.versionId(), "componentId", after.componentId()));
  }

  private void recordProration(
      String action, ProrationPolicyView after, ProrationPolicyView before) {
    record(
        PRORATION_OBJECT,
        "ComponentProrationPolicy" + action,
        action,
        after.identityId(),
        after.versionSequence(),
        prorationState(before),
        prorationState(after),
        Map.of(
            "versionId", after.versionId(),
            "componentId", after.componentId(),
            "eventType", after.eventType()));
  }

  private void record(
      String objectType,
      String eventType,
      String action,
      UUID identityId,
      long aggregateVersion,
      Map<String, Object> before,
      Map<String, Object> after,
      Map<String, Object> metadata) {
    String principal = actor.require();
    audit.append(action, objectType, identityId, before, after, metadata, principal);
    outbox.append(events.create(
        eventType,
        1,
        TenantContext.require(),
        null,
        objectType,
        identityId,
        aggregateVersion,
        after));
  }

  private Map<String, Object> rateState(RateTableView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("retirementEffectiveDate", view.retirementEffectiveDate());
    state.put("retirementReason", view.retirementReason());
    state.put("valueType", view.valueType());
    state.put("unitCode", view.unitCode());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("dimensions", view.dimensions());
    state.put("cells", view.cells());
    return state;
  }

  private Map<String, Object> roundingState(RoundingPolicyView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("componentId", view.componentId());
    state.put("versionId", view.versionId());
    state.put("method", view.method());
    state.put("scale", view.scale());
    state.put("stage", view.stage());
    state.put("negativeTreatment", view.negativeTreatment());
    state.put("retirementEffectiveDate", view.retirementEffectiveDate());
    state.put("retirementReason", view.retirementReason());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private Map<String, Object> prorationState(ProrationPolicyView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("componentId", view.componentId());
    state.put("eventType", view.eventType());
    state.put("versionId", view.versionId());
    state.put("method", view.method());
    state.put("basis", view.basis());
    state.put("retirementEffectiveDate", view.retirementEffectiveDate());
    state.put("retirementReason", view.retirementReason());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
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
          throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException("Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(saved.get().body(), responseType);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Stored idempotent response is invalid", exception);
        }
      }
      try {
        idempotency.reserve(
            operation, key, requestHash, clock.instant().plus(Duration.ofHours(24)));
      } catch (IllegalStateException exception) {
        throw new ConflictException("Idempotency-Key is already in use", exception);
      }
      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private void validateRetirement(LocalDate effectiveDate, long expectedVersion, String reason) {
    if (effectiveDate == null) {
      throw new IllegalArgumentException("effectiveDate is required");
    }
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("expectedVersion must be non-negative");
    }
    if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
      throw new IllegalArgumentException("reason must contain between 1 and 500 characters");
    }
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(UUID actual, UUID expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("Version does not belong to the requested identity");
    }
  }
}
