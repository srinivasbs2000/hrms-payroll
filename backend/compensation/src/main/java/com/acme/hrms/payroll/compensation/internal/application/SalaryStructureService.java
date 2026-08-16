package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.CtcPolicyTreatmentView;
import com.acme.hrms.payroll.compensation.CtcPolicyView;
import com.acme.hrms.payroll.compensation.EligibilityRuleView.EvaluationView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSimulationRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.SalaryStructureWriteRequest;
import com.acme.hrms.payroll.compensation.internal.infrastructure.CtcPolicyRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.EligibilityRuleRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class SalaryStructureService {
  private static final String OBJECT_TYPE = "SALARY_STRUCTURE";
  private static final String DISCLAIMER =
      "DESIGN-TIME SALARY-STRUCTURE SIMULATION — "
          + "NOT AN EMPLOYEE PAYROLL RESULT";
  private static final BigDecimal ONE_HUNDRED =
      new BigDecimal("100.000000");
  private static final BigDecimal TWELVE =
      new BigDecimal("12");

  private final SalaryStructureRepository repository;
  private final CtcPolicyRepository ctcPolicies;
  private final EligibilityRuleService eligibilityRules;
  private final EligibilityRuleRepository eligibilityRuleRepository;
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
      CtcPolicyRepository ctcPolicies,
      EligibilityRuleService eligibilityRules,
      EligibilityRuleRepository eligibilityRuleRepository,
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
    this.ctcPolicies = ctcPolicies;
    this.eligibilityRules = eligibilityRules;
    this.eligibilityRuleRepository = eligibilityRuleRepository;
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
    String configurationHash = configurationHash(request);
    return idempotent(
        "salary-structure:create",
        key,
        request,
        SalaryStructureView.class,
        () -> {
          SalaryStructureView created = repository.create(
              request,
              configurationHash,
              actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public SalaryStructureView addVersion(
      UUID identityId,
      String key,
      SalaryStructureWriteRequest request) {
    request.validate(false);
    String configurationHash = configurationHash(request);
    return idempotent(
        "salary-structure:version-create:" + identityId,
        key,
        request,
        SalaryStructureView.class,
        () -> {
          SalaryStructureView created = repository.addVersion(
              identityId,
              request,
              null,
              configurationHash,
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
    String configurationHash = configurationHash(request);
    return idempotent(
        "salary-structure:version-correct:" + versionId,
        key,
        request,
        SalaryStructureView.class,
        () -> {
          SalaryStructureView previous = repository.version(versionId);
          requireIdentity(previous, identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft salary-structure "
                    + "version can be corrected");
          }
          SalaryStructureView corrected = repository.addVersion(
              identityId,
              request,
              versionId,
              configurationHash,
              actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public SalaryStructureValidationView simulate(
      UUID identityId,
      UUID versionId,
      String key,
      SalaryStructureSimulationRequest request) {
    request.validate();
    return idempotent(
        "salary-structure:simulate:" + versionId,
        key,
        request,
        SalaryStructureValidationView.class,
        () -> {
          SalaryStructureValidationView validation =
              calculateDraft(identityId, versionId, request);

          var existing = repository.findValidation(
              versionId,
              validation.resultHash());
          if (existing.isPresent()) {
            return existing.get();
          }
          SalaryStructureValidationView persisted =
              repository.saveValidation(validation, actor.require());
          recordValidation(persisted);
          return persisted;
        });
  }

  SalaryStructureValidationView calculateDraft(
      UUID identityId,
      UUID versionId,
      SalaryStructureSimulationRequest request) {
    request.validate();
    return transactions.read(
        () -> calculateDraftInTenantTransaction(
            identityId,
            versionId,
            request));
  }

  private SalaryStructureValidationView calculateDraftInTenantTransaction(
      UUID identityId,
      UUID versionId,
      SalaryStructureSimulationRequest request) {
    SalaryStructureView structure = repository.version(versionId);
    requireIdentity(structure, identityId);
    requireSimulationDraft(structure, request.effectiveDate());

    CtcPolicyView policy =
        ctcPolicies.version(structure.ctcPolicyVersionId());
    EvaluationView eligibility = evaluateEligibility(
        structure,
        request);
    return calculate(
        structure,
        policy,
        eligibility,
        request);
  }

  public List<SalaryStructureValidationView> validations(
      UUID identityId,
      UUID versionId) {
    return transactions.read(() -> {
      SalaryStructureView structure = repository.version(versionId);
      requireIdentity(structure, identityId);
      return repository.validations(versionId);
    });
  }

  public SalaryStructureView bindValidation(
      UUID identityId,
      UUID versionId,
      UUID validationId,
      String key,
      long expectedVersion) {
    return idempotent(
        "salary-structure:validation-bind:"
            + versionId + ":" + validationId,
        key,
        Map.of(
            "validationId", validationId,
            "expectedVersion", expectedVersion),
        SalaryStructureView.class,
        () -> {
          SalaryStructureView before = repository.version(versionId);
          requireIdentity(before, identityId);
          SalaryStructureView bound = repository.bindValidation(
              versionId,
              validationId,
              expectedVersion,
              actor.require(),
              clock.instant());
          record("VALIDATION_BOUND", bound, before);
          return bound;
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
        SalaryStructureView.class,
        () -> {
          SalaryStructureView before = repository.version(versionId);
          requireIdentity(before, identityId);
          SalaryStructureView approved = repository.approve(
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
        SalaryStructureView.class,
        () -> {
          SalaryStructureView before = repository.version(versionId);
          requireIdentity(before, identityId);
          SalaryStructureView ended = repository.endDate(
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
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  public List<SalaryStructureView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }

  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private EvaluationView evaluateEligibility(
      SalaryStructureView structure,
      SalaryStructureSimulationRequest request) {
    if (structure.eligibilityRuleVersionId() == null) {
      if (request.eligibilityFacts() != null
          && !request.eligibilityFacts().isEmpty()) {
        throw new IllegalArgumentException(
            "eligibilityFacts must be absent when no rule is configured");
      }
      return null;
    }
    if (request.eligibilityFacts() == null
        || request.eligibilityFacts().isEmpty()) {
      throw new IllegalArgumentException(
          "eligibilityFacts are required for the configured rule");
    }
    var rule = eligibilityRuleRepository.version(
        structure.eligibilityRuleVersionId());
    return eligibilityRules.evaluate(
        rule.identityId(),
        rule.versionId(),
        request.eligibilityFacts());
  }

  private SalaryStructureValidationView calculate(
      SalaryStructureView structure,
      CtcPolicyView policy,
      EvaluationView eligibility,
      SalaryStructureSimulationRequest request) {
    List<Map<String, Object>> errors = new ArrayList<>();
    List<Map<String, Object>> warnings = new ArrayList<>();
    Map<String, BigDecimal> amountByCode = new LinkedHashMap<>();
    Map<UUID, BigDecimal> amountByVersion = new LinkedHashMap<>();
    List<SalaryStructureValidationLineView> lines = new ArrayList<>();

    if (!"STRUCTURAL".equals(structure.targetExecutionMode())) {
      return calculationEngineBoundary(
          structure,
          eligibility,
          request);
    }

    List<SalaryStructureLineView> ordered = structure.lines().stream()
        .sorted(Comparator.comparingInt(SalaryStructureLineView::sequenceNo))
        .toList();
    BigDecimal nonResidualTotal = BigDecimal.ZERO.setScale(4);
    SalaryStructureLineView residualLine = null;

    for (SalaryStructureLineView line : ordered) {
      if ("RESIDUAL".equals(line.lineType())) {
        residualLine = line;
        continue;
      }
      BigDecimal amount = calculateNonResidual(
          line,
          amountByCode,
          errors);
      amount = amount.setScale(4, RoundingMode.HALF_UP);
      applyBounds(line, amount, errors);
      amountByCode.put(line.componentCode(), amount);
      amountByVersion.put(line.componentVersionId(), amount);
      nonResidualTotal = nonResidualTotal.add(amount);
      lines.add(validationLine(
          line,
          amount,
          classification(line, policy, request.effectiveDate()),
          Map.of(
              "calculationType", line.lineType(),
              "percentageBaseCode",
              line.percentageBaseCode() == null
                  ? "" : line.percentageBaseCode(),
              "withinConfiguredBounds",
              withinBounds(line, amount))));
    }

    if (residualLine == null) {
      errors.add(issue(
          "RESIDUAL_LINE_MISSING",
          "Exactly one residual line is required"));
    } else {
      BigDecimal rawResidual = structure.targetAnnualAmount()
          .subtract(nonResidualTotal)
          .setScale(4, RoundingMode.HALF_UP);
      BigDecimal persistedResidual = rawResidual;
      if (rawResidual.signum() < 0) {
        errors.add(issue(
            "NEGATIVE_RESIDUAL",
            "Non-residual values exceed the configured target"));
        persistedResidual = BigDecimal.ZERO.setScale(4);
      }
      applyBounds(residualLine, persistedResidual, errors);
      amountByCode.put(residualLine.componentCode(), persistedResidual);
      amountByVersion.put(
          residualLine.componentVersionId(), persistedResidual);
      lines.add(validationLine(
          residualLine,
          persistedResidual,
          "RESIDUAL",
          Map.of(
              "calculationType", "RESIDUAL",
              "rawResidualAmount", rawResidual,
              "withinConfiguredBounds",
              withinBounds(residualLine, persistedResidual))));
    }

    lines.sort(Comparator.comparingInt(
        SalaryStructureValidationLineView::lineSequence));
    BigDecimal total = amountByVersion.values().stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(4, RoundingMode.HALF_UP);
    BigDecimal delta = structure.targetAnnualAmount()
        .subtract(total)
        .abs()
        .setScale(4, RoundingMode.HALF_UP);
    if (delta.compareTo(structure.toleranceAmount()) > 0) {
      errors.add(issue(
          "TARGET_RECONCILIATION_FAILED",
          "Calculated total differs from target beyond tolerance"));
    }

    if (eligibility != null
        && !"ELIGIBLE".equals(eligibility.result())) {
      warnings.add(issue(
          "SYNTHETIC_ELIGIBILITY_" + eligibility.result(),
          "Synthetic facts produced " + eligibility.result()));
    }

    Map<String, BigDecimal> costViews = costViews(
        policy,
        request.effectiveDate(),
        structure.targetAnnualAmount(),
        amountByVersion,
        warnings);
    warnings.add(issue(
        "MINIMUM_WAGE_RULESET_NOT_BOUND",
        "P5-A3 has no authoritative minimum-wage rule source; "
            + "this simulation proves structural compatibility only"));
    String status = errors.isEmpty() ? "PASS" : "FAIL";
    String requestHash = canonical.hash(request);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("disclaimer", DISCLAIMER);
    summary.put("targetType", structure.targetType());
    summary.put("targetFrequency", structure.targetFrequency());
    summary.put("targetSourceAmount", structure.targetSourceAmount());
    summary.put(
        "targetAnnualizationFactor",
        structure.targetAnnualizationFactor());
    summary.put("targetExecutionMode", structure.targetExecutionMode());
    summary.put(
        "inclusivePayrollBaseVersionId",
        structure.inclusivePayrollBaseVersionId() == null
            ? "" : structure.inclusivePayrollBaseVersionId().toString());
    summary.put(
        "exclusivePayrollBaseVersionId",
        structure.exclusivePayrollBaseVersionId() == null
            ? "" : structure.exclusivePayrollBaseVersionId().toString());
    summary.put("targetAnnualAmount", structure.targetAnnualAmount());
    summary.put("totalAnnualAmount", total);
    summary.put("reconciliationDelta", delta);
    summary.put("toleranceAmount", structure.toleranceAmount());
    summary.put("eligibilityResult",
        eligibility == null ? "NOT_CONFIGURED" : eligibility.result());
    summary.put("eligibilityEvaluationHash",
        eligibility == null ? "" : eligibility.evaluationHash());
    summary.put("costViews", costViews);
    summary.put("statutoryCompatibilityStatus", "STRUCTURAL_ONLY");
    summary.put("blockingErrors", List.copyOf(errors));
    summary.put("warnings", List.copyOf(warnings));

    Map<String, Object> resultState = new LinkedHashMap<>();
    resultState.put("versionId", structure.versionId());
    resultState.put("configurationHash", structure.configurationHash());
    resultState.put("requestHash", requestHash);
    resultState.put("validationStatus", status);
    resultState.put("summary", summary);
    resultState.put("lines", lines.stream()
        .map(this::validationLineState)
        .toList());
    String resultHash = canonical.hash(resultState);

    return new SalaryStructureValidationView(
        UUID.randomUUID(),
        structure.identityId(),
        structure.versionId(),
        structure.ctcPolicyVersionId(),
        structure.eligibilityRuleVersionId(),
        request.effectiveDate(),
        structure.targetAnnualAmount(),
        status,
        requestHash,
        structure.configurationHash(),
        resultHash,
        errors.size(),
        warnings.size(),
        Map.copyOf(summary),
        null,
        actor.require(),
        DISCLAIMER,
        List.copyOf(lines));
  }

  private SalaryStructureValidationView calculationEngineBoundary(
      SalaryStructureView structure,
      EvaluationView eligibility,
      SalaryStructureSimulationRequest request) {
    boolean calculationEngine =
        "CALCULATION_ENGINE".equals(structure.targetExecutionMode());
    Map<String, Object> blocking = issue(
        calculationEngine
            ? "TARGET_REQUIRES_CALCULATION_ENGINE"
            : "TARGET_RESOLUTION_POLICY_REQUIRED",
        calculationEngine
            ? "This target requires calculation-engine rate/gross-up resolution"
            : "This target requires an explicit component-base target resolver");
    String requestHash = canonical.hash(request);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("disclaimer", DISCLAIMER);
    summary.put("targetType", structure.targetType());
    summary.put("targetFrequency", structure.targetFrequency());
    summary.put("targetSourceAmount", structure.targetSourceAmount());
    summary.put(
        "targetAnnualizationFactor",
        structure.targetAnnualizationFactor());
    summary.put("targetExecutionMode", structure.targetExecutionMode());
    summary.put(
        "inclusivePayrollBaseVersionId",
        structure.inclusivePayrollBaseVersionId() == null
            ? "" : structure.inclusivePayrollBaseVersionId().toString());
    summary.put(
        "exclusivePayrollBaseVersionId",
        structure.exclusivePayrollBaseVersionId() == null
            ? "" : structure.exclusivePayrollBaseVersionId().toString());
    summary.put("targetAnnualAmount", structure.targetAnnualAmount());
    summary.put(
        "eligibilityResult",
        eligibility == null ? "NOT_CONFIGURED" : eligibility.result());
    summary.put("costViews", Map.of());
    summary.put(
        "statutoryCompatibilityStatus",
        calculationEngine
            ? "CALCULATION_ENGINE_REQUIRED"
            : "TARGET_RESOLUTION_POLICY_REQUIRED");
    summary.put("blockingErrors", List.of(blocking));
    summary.put("warnings", List.of());

    Map<String, Object> resultState = new LinkedHashMap<>();
    resultState.put("versionId", structure.versionId());
    resultState.put("configurationHash", structure.configurationHash());
    resultState.put("requestHash", requestHash);
    resultState.put("validationStatus", "FAIL");
    resultState.put("summary", summary);
    resultState.put("lines", List.of());
    String resultHash = canonical.hash(resultState);

    return new SalaryStructureValidationView(
        UUID.randomUUID(),
        structure.identityId(),
        structure.versionId(),
        structure.ctcPolicyVersionId(),
        structure.eligibilityRuleVersionId(),
        request.effectiveDate(),
        structure.targetAnnualAmount() == null
            ? structure.targetSourceAmount()
            : structure.targetAnnualAmount(),
        "FAIL",
        requestHash,
        structure.configurationHash(),
        resultHash,
        1,
        0,
        Map.copyOf(summary),
        null,
        actor.require(),
        DISCLAIMER,
        List.of());
  }

  private BigDecimal calculateNonResidual(
      SalaryStructureLineView line,
      Map<String, BigDecimal> amountByCode,
      List<Map<String, Object>> errors) {
    if ("FIXED".equals(line.lineType())) {
      return line.targetAmount();
    }
    if ("PERCENTAGE".equals(line.lineType())) {
      BigDecimal base = amountByCode.get(line.percentageBaseCode());
      if (base == null) {
        errors.add(issue(
            "PERCENTAGE_BASE_UNRESOLVED",
            "Percentage base must reference a prior calculated component: "
                + line.percentageBaseCode()));
        return BigDecimal.ZERO;
      }
      return base.multiply(line.targetPercentage())
          .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }
    errors.add(issue(
        "UNSUPPORTED_LINE_TYPE",
        "Unsupported non-residual line type: " + line.lineType()));
    return BigDecimal.ZERO;
  }

  private void applyBounds(
      SalaryStructureLineView line,
      BigDecimal amount,
      List<Map<String, Object>> errors) {
    if (!withinBounds(line, amount)) {
      errors.add(issue(
          "LINE_AMOUNT_OUTSIDE_BOUNDS",
          "Calculated amount is outside configured bounds for "
              + line.componentCode()));
    }
  }

  private boolean withinBounds(
      SalaryStructureLineView line,
      BigDecimal amount) {
    return (line.minimumAmount() == null
            || amount.compareTo(line.minimumAmount()) >= 0)
        && (line.maximumAmount() == null
            || amount.compareTo(line.maximumAmount()) <= 0);
  }

  private SalaryStructureValidationLineView validationLine(
      SalaryStructureLineView line,
      BigDecimal annualAmount,
      String classification,
      Map<String, Object> evidence) {
    return new SalaryStructureValidationLineView(
        UUID.randomUUID(),
        line.sequenceNo(),
        line.componentId(),
        line.componentVersionId(),
        line.componentCode(),
        line.componentName(),
        annualAmount,
        annualAmount.divide(TWELVE, 4, RoundingMode.HALF_UP),
        classification,
        Map.copyOf(evidence));
  }

  private String classification(
      SalaryStructureLineView line,
      CtcPolicyView policy,
      LocalDate effectiveDate) {
    if ("RESIDUAL".equals(line.lineType())) {
      return "RESIDUAL";
    }
    return policy.treatments().stream()
        .filter(treatment -> treatment.componentVersionId()
            .equals(line.componentVersionId()))
        .filter(treatment -> active(treatment, effectiveDate))
        .map(CtcPolicyTreatmentView::treatmentType)
        .map(type -> switch (type) {
          case "EMPLOYER_CONTRIBUTION" -> "EMPLOYER_CONTRIBUTION";
          case "PROVISION" -> "PROVISION";
          case "BENEFIT_PREMIUM" -> "BENEFIT";
          case "INFORMATIONAL", "EXCLUDE" -> "INFORMATIONAL";
          default -> "PERCENTAGE".equals(line.lineType())
              ? "VARIABLE" : "FIXED";
        })
        .findFirst()
        .orElse("PERCENTAGE".equals(line.lineType())
            ? "VARIABLE" : "FIXED");
  }

  private Map<String, BigDecimal> costViews(
      CtcPolicyView policy,
      LocalDate effectiveDate,
      BigDecimal target,
      Map<UUID, BigDecimal> amounts,
      List<Map<String, Object>> warnings) {
    Map<String, BigDecimal> views = new TreeMap<>();
    for (String view : List.of(
        "OFFERED", "TARGET", "ACCRUED", "ACTUAL_EMPLOYER_COST")) {
      views.put(view, BigDecimal.ZERO.setScale(4));
    }

    for (CtcPolicyTreatmentView treatment : policy.treatments()) {
      if (!active(treatment, effectiveDate)) {
        continue;
      }
      BigDecimal value = switch (treatment.treatmentType()) {
        case "FIXED_VALUE" -> treatment.fixedValue();
        case "TARGET_VALUE" -> target
            .multiply(treatment.targetPercentage())
            .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        case "EXCLUDE", "INFORMATIONAL" -> BigDecimal.ZERO;
        default -> amounts.get(treatment.componentVersionId());
      };
      if (value == null) {
        warnings.add(issue(
            "CTC_TREATMENT_COMPONENT_NOT_IN_STRUCTURE",
            "No structure amount exists for treatment component "
                + treatment.componentCode()));
        value = BigDecimal.ZERO;
      }
      views.merge(
          treatment.costView(),
          value.setScale(4, RoundingMode.HALF_UP),
          BigDecimal::add);
    }
    return Map.copyOf(views);
  }

  private boolean active(
      CtcPolicyTreatmentView treatment,
      LocalDate date) {
    return !date.isBefore(treatment.effectiveFrom())
        && (treatment.effectiveTo() == null
            || date.isBefore(treatment.effectiveTo()));
  }

  private Map<String, Object> issue(String code, String message) {
    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put("code", code);
    issue.put("message", message);
    return Map.copyOf(issue);
  }

  private Map<String, Object> validationLineState(
      SalaryStructureValidationLineView line) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("lineSequence", line.lineSequence());
    state.put("componentVersionId", line.componentVersionId());
    state.put("annualAmount", line.annualAmount());
    state.put("monthlyAmount", line.monthlyAmount());
    state.put("classification", line.classification());
    state.put("evidence", line.evidence());
    return state;
  }

  private String configurationHash(
      SalaryStructureWriteRequest request) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("name", request.name().trim());
    state.put("currency", request.resolvedCurrency());
    state.put("structureType", request.structureType());
    state.put("payFrequency", request.payFrequency());
    state.put("confidentialityLevel", request.confidentialityLevel());
    state.put("ctcPolicyVersionId", request.ctcPolicyVersionId());
    state.put("eligibilityRuleVersionId", request.eligibilityRuleVersionId());
    state.put("targetType", request.targetType());
    state.put("targetFrequency", request.resolvedTargetFrequency());
    state.put("targetSourceAmount", request.targetSourceAmount());
    state.put(
        "targetAnnualizationFactor",
        request.resolvedTargetAnnualizationFactor());
    state.put("targetExecutionMode", request.targetExecutionMode());
    state.put(
        "inclusivePayrollBaseVersionId",
        request.inclusivePayrollBaseVersionId());
    state.put(
        "exclusivePayrollBaseVersionId",
        request.exclusivePayrollBaseVersionId());
    state.put(
        "targetAnnualAmount",
        request.resolvedTargetAnnualAmount());
    state.put("toleranceAmount", request.toleranceAmount());
    state.put("residualComponentVersionId",
        request.residualComponentVersionId());
    state.put("effectiveFrom", request.effectiveFrom());
    state.put("effectiveTo", request.effectiveTo());
    state.put("lines", request.lines().stream()
        .sorted(Comparator.comparingInt(
            SalaryStructureLineWriteRequest::sequenceNo))
        .map(this::lineConfigurationState)
        .toList());
    return canonical.hash(state);
  }

  private Map<String, Object> lineConfigurationState(
      SalaryStructureLineWriteRequest line) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("componentVersionId", line.componentVersionId());
    state.put("sequenceNo", line.sequenceNo());
    state.put("lineType", line.lineType());
    state.put("targetAmount", line.targetAmount());
    state.put("targetPercentage", line.targetPercentage());
    state.put("percentageBaseCode", line.percentageBaseCode());
    state.put("minimumAmount", line.minimumAmount());
    state.put("maximumAmount", line.maximumAmount());
    state.put("mandatory", line.mandatory());
    state.put("overridePolicy", line.overridePolicy());
    state.put("ctcDisplayOrder", line.ctcDisplayOrder());
    state.put("payslipDisplayOrder", line.payslipDisplayOrder());
    return state;
  }

  private void requireSimulationDraft(
      SalaryStructureView structure,
      LocalDate effectiveDate) {
    if (structure.structureSchemaVersion() != 1
        || !"DRAFT".equals(structure.approvalStatus())
        || structure.superseded()) {
      throw new ConflictException(
          "Simulation requires a non-superseded schema-1 draft structure");
    }
    if (effectiveDate.isBefore(structure.effectiveFrom())
        || (structure.effectiveTo() != null
            && !effectiveDate.isBefore(structure.effectiveTo()))) {
      throw new IllegalArgumentException(
          "effectiveDate must be inside the structure effective range");
    }
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

  private void recordValidation(
      SalaryStructureValidationView validation) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("validationId", validation.validationId());
    summary.put("versionId", validation.versionId());
    summary.put("validationStatus", validation.validationStatus());
    summary.put("requestHash", validation.requestHash());
    summary.put("configurationHash", validation.configurationHash());
    summary.put("resultHash", validation.resultHash());
    summary.put("blockingErrorCount", validation.blockingErrorCount());
    summary.put("warningCount", validation.warningCount());

    audit.append(
        "SIMULATED",
        OBJECT_TYPE,
        validation.identityId(),
        null,
        summary,
        Map.of("versionId", validation.versionId()),
        actor.require());
    var event = events.create(
        "SalaryStructureSIMULATED",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        validation.identityId(),
        1,
        summary);
    outbox.append(event);
  }

  private Map<String, Object> state(SalaryStructureView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("currency", view.currency());
    state.put("structureSchemaVersion", view.structureSchemaVersion());
    state.put("structureType", view.structureType());
    state.put("payFrequency", view.payFrequency());
    state.put("ctcPolicyVersionId", view.ctcPolicyVersionId());
    state.put("eligibilityRuleVersionId", view.eligibilityRuleVersionId());
    state.put("targetType", view.targetType());
    state.put("targetFrequency", view.targetFrequency());
    state.put("targetSourceAmount", view.targetSourceAmount());
    state.put(
        "targetAnnualizationFactor",
        view.targetAnnualizationFactor());
    state.put("targetExecutionMode", view.targetExecutionMode());
    state.put(
        "inclusivePayrollBaseVersionId",
        view.inclusivePayrollBaseVersionId());
    state.put(
        "exclusivePayrollBaseVersionId",
        view.exclusivePayrollBaseVersionId());
    state.put("targetAnnualAmount", view.targetAnnualAmount());
    state.put("configurationHash", view.configurationHash());
    state.put("validationFingerprint", view.validationFingerprint());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("lineCount", view.lines().size());
    return state;
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<T> work) {
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
              responseType);
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

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      SalaryStructureView version,
      UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to salary-structure identity");
    }
  }
}
