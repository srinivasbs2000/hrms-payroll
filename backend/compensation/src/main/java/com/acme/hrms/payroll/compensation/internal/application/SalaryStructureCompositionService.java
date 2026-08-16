package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.CtcPolicyTreatmentView;
import com.acme.hrms.payroll.compensation.CtcPolicyView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureSimulationRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanView;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.CtcPolicyRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureSupplementalPlanRepository;
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
public class SalaryStructureCompositionService {
  private static final String OBJECT_TYPE = "SALARY_STRUCTURE";
  private static final String DISCLAIMER =
      "DESIGN-TIME SALARY-STRUCTURE COMPOSITION SIMULATION — "
          + "NOT AN EMPLOYEE PAYROLL RESULT";
  private static final BigDecimal ONE_HUNDRED =
      new BigDecimal("100.000000");
  private static final BigDecimal TWELVE = new BigDecimal("12");

  private final SalaryStructureService baseService;
  private final SalaryStructureRepository structures;
  private final SalaryStructureSupplementalPlanRepository supplementalPlans;
  private final CtcPolicyRepository ctcPolicies;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public SalaryStructureCompositionService(
      SalaryStructureService baseService,
      SalaryStructureRepository structures,
      SalaryStructureSupplementalPlanRepository supplementalPlans,
      CtcPolicyRepository ctcPolicies,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      DomainEventFactory events,
      OutboxWriter outbox,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper) {
    this.baseService = baseService;
    this.structures = structures;
    this.supplementalPlans = supplementalPlans;
    this.ctcPolicies = ctcPolicies;
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

  public SalaryStructureValidationView simulate(
      UUID identityId,
      UUID versionId,
      String key,
      SalaryStructureSimulationRequest request) {
    request.validate();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }

    SalaryStructureValidationView baseValidation =
        baseService.simulate(identityId, versionId, key, request);

    CompositionSnapshot snapshot = transactions.read(
        () -> snapshot(identityId, versionId));

    Map<String, Object> requestState = new LinkedHashMap<>();
    requestState.put("request", request);
    requestState.put("baseResultHash", baseValidation.resultHash());
    requestState.put(
        "compositionRevision",
        snapshot.compositionRevision());

    return idempotent(
        "salary-structure:composed-simulate:" + versionId,
        key,
        requestState,
        SalaryStructureValidationView.class,
        () -> {
          long currentRevision =
              supplementalPlans.compositionRevision(versionId);
          if (currentRevision != snapshot.compositionRevision()) {
            throw new ConflictException(
                "Salary-structure composition changed during simulation");
          }

          CtcPolicyView policy =
              ctcPolicies.version(snapshot.structure().ctcPolicyVersionId());
          SalaryStructureValidationView composed =
              compose(snapshot, baseValidation, policy, request);

          var existing = structures.findValidation(
              versionId,
              composed.resultHash());
          if (existing.isPresent()) {
            return existing.get();
          }

          SalaryStructureValidationView saved =
              structures.saveValidation(composed, actor.require());
          recordValidation(saved, snapshot.compositionRevision());
          return saved;
        });
  }

  private CompositionSnapshot snapshot(
      UUID identityId,
      UUID versionId) {
    SalaryStructureView structure = structures.version(versionId);
    requireIdentity(structure, identityId);
    if (structure.structureSchemaVersion() != 1
        || !"DRAFT".equals(structure.approvalStatus())
        || structure.superseded()) {
      throw new ConflictException(
          "Composed simulation requires a non-superseded schema-1 draft");
    }

    long revision = supplementalPlans.compositionRevision(versionId);
    List<SupplementalPlanBindingView> bindings =
        supplementalPlans.bindings(identityId, versionId).stream()
            .sorted(Comparator.comparingInt(
                SupplementalPlanBindingView::sequenceNo))
            .toList();

    Map<UUID, SupplementalPlanView> plans = new LinkedHashMap<>();
    for (SupplementalPlanBindingView binding : bindings) {
      SupplementalPlanView plan =
          supplementalPlans.version(binding.supplementalPlanVersionId());
      if (!"APPROVED".equals(plan.approvalStatus())
          || !"ACTIVE".equals(plan.lifecycleStatus())) {
        throw new ConflictException(
            "Bound supplemental plan is no longer active and approved");
      }
      plans.put(binding.supplementalPlanVersionId(), plan);
    }

    return new CompositionSnapshot(
        structure,
        revision,
        bindings,
        Map.copyOf(plans));
  }

  private SalaryStructureValidationView compose(
      CompositionSnapshot snapshot,
      SalaryStructureValidationView baseValidation,
      CtcPolicyView policy,
      SalaryStructureSimulationRequest request) {
    SalaryStructureView structure = snapshot.structure();
    List<Map<String, Object>> errors = new ArrayList<>();
    List<Map<String, Object>> warnings = new ArrayList<>();
    copyIssues(
        baseValidation.summary().get("warnings"),
        warnings);
    warnings.removeIf(issue ->
        "CTC_TREATMENT_COMPONENT_NOT_IN_STRUCTURE".equals(
            String.valueOf(issue.get("code"))));

    if (!"PASS".equals(baseValidation.validationStatus())) {
      errors.add(issue(
          "BASE_VALIDATION_FAILED",
          "Base salary-structure simulation must pass before composition"));
    }

    Map<UUID, BigDecimal> activeAmounts = new LinkedHashMap<>();
    List<SalaryStructureValidationLineView> lines = new ArrayList<>();
    int nextSequence = 1;
    SalaryStructureValidationLineView baseResidual = null;

    for (SalaryStructureValidationLineView baseLine
        : baseValidation.lines().stream()
            .sorted(Comparator.comparingInt(
                SalaryStructureValidationLineView::lineSequence))
            .toList()) {
      if (structure.residualComponentVersionId()
          .equals(baseLine.componentVersionId())) {
        baseResidual = baseLine;
        continue;
      }

      BigDecimal amount = scaled(baseLine.annualAmount());
      if (baseLine.componentVersionId() != null) {
        activeAmounts.put(baseLine.componentVersionId(), amount);
      }

      Map<String, Object> evidence =
          copyEvidence(baseLine.evidence());
      evidence.put("sourceType", "BASE");
      evidence.put(
          "sourceLineSequence",
          baseLine.lineSequence());

      lines.add(new SalaryStructureValidationLineView(
          UUID.randomUUID(),
          nextSequence++,
          baseLine.componentId(),
          baseLine.componentVersionId(),
          baseLine.componentCode(),
          baseLine.componentName(),
          amount,
          monthly(amount),
          baseLine.classification(),
          evidence));
    }

    if (baseResidual == null) {
      errors.add(issue(
          "BASE_RESIDUAL_MISSING",
          "Base validation does not contain the configured residual component"));
    }

    BigDecimal supplementalTotal = BigDecimal.ZERO.setScale(4);
    for (SupplementalPlanBindingView binding : snapshot.bindings()) {
      SupplementalPlanView plan =
          snapshot.plans().get(binding.supplementalPlanVersionId());
      for (SupplementalPlanLineView planLine : plan.lines().stream()
          .sorted(Comparator.comparingInt(
              SupplementalPlanLineView::sequenceNo))
          .toList()) {
        boolean active =
            active(binding.effectiveFrom(), binding.effectiveTo(),
                request.effectiveDate())
                && active(
                    planLine.effectiveFrom(),
                    planLine.effectiveTo(),
                    request.effectiveDate());

        BigDecimal amount = BigDecimal.ZERO.setScale(4);
        String calculationType =
            planLine.defaultAmount() != null ? "FIXED" : "PERCENTAGE";

        if (active) {
          if (planLine.defaultAmount() != null) {
            amount = scaled(planLine.defaultAmount());
          } else {
            BigDecimal baseAmount = activeAmounts.get(
                planLine.percentageBaseComponentVersionId());
            if (baseAmount == null) {
              errors.add(issue(
                  "SUPPLEMENTAL_PERCENTAGE_BASE_UNRESOLVED",
                  "Percentage base is not available before supplemental line "
                      + planLine.componentCode()));
            } else {
              amount = baseAmount
                  .multiply(planLine.defaultPercentage())
                  .divide(
                      ONE_HUNDRED,
                      4,
                      RoundingMode.HALF_UP);
            }
          }

          if (!withinBounds(planLine, amount)) {
            errors.add(issue(
                "SUPPLEMENTAL_LINE_OUTSIDE_BOUNDS",
                "Calculated supplemental amount is outside configured bounds for "
                    + planLine.componentCode()));
          }

          if (activeAmounts.containsKey(planLine.componentVersionId())) {
            errors.add(issue(
                "DUPLICATE_ACTIVE_SUPPLEMENTAL_COMPONENT",
                "More than one active contribution resolves to component "
                    + planLine.componentCode()));
          } else {
            activeAmounts.put(planLine.componentVersionId(), amount);
            supplementalTotal = supplementalTotal.add(amount);
          }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceType", "SUPPLEMENTAL");
        evidence.put("sourceBindingId", binding.bindingId().toString());
        evidence.put("sourcePlanLineId", planLine.lineId().toString());
        evidence.put(
            "sourcePlanVersionId",
            plan.versionId().toString());
        evidence.put("bindingSequence", binding.sequenceNo());
        evidence.put("planLineSequence", planLine.sequenceNo());
        evidence.put("activeAtEffectiveDate", active);
        evidence.put("calculationType", calculationType);
        if (planLine.percentageBaseComponentVersionId() != null) {
          evidence.put(
              "percentageBaseComponentVersionId",
              planLine.percentageBaseComponentVersionId().toString());
        }

        lines.add(new SalaryStructureValidationLineView(
            UUID.randomUUID(),
            nextSequence++,
            planLine.componentId(),
            planLine.componentVersionId(),
            planLine.componentCode(),
            planLine.componentName(),
            amount,
            monthly(amount),
            classification(planLine, policy, request.effectiveDate()),
            evidence));
      }
    }

    BigDecimal baseResidualAmount =
        baseResidual == null
            ? BigDecimal.ZERO.setScale(4)
            : scaled(baseResidual.annualAmount());
    BigDecimal rawResidual =
        baseResidualAmount.subtract(supplementalTotal)
            .setScale(4, RoundingMode.HALF_UP);
    BigDecimal adjustedResidual = rawResidual;
    if (rawResidual.signum() < 0) {
      errors.add(issue(
          "NEGATIVE_COMPOSED_RESIDUAL",
          "Supplemental contributions exceed the available residual amount"));
      adjustedResidual = BigDecimal.ZERO.setScale(4);
    }

    SalaryStructureLineView residualConfiguration =
        structure.lines().stream()
            .filter(line -> structure.residualComponentVersionId()
                .equals(line.componentVersionId()))
            .findFirst()
            .orElse(null);
    if (residualConfiguration == null) {
      errors.add(issue(
          "RESIDUAL_CONFIGURATION_MISSING",
          "Configured residual component is not present in the base structure"));
    } else if (!withinBounds(
        residualConfiguration,
        adjustedResidual)) {
      errors.add(issue(
          "COMPOSED_RESIDUAL_OUTSIDE_BOUNDS",
          "Adjusted residual amount is outside configured bounds"));
    }

    if (baseResidual != null) {
      activeAmounts.put(
          baseResidual.componentVersionId(),
          adjustedResidual);
      Map<String, Object> residualEvidence =
          copyEvidence(baseResidual.evidence());
      residualEvidence.put("sourceType", "BASE");
      residualEvidence.put(
          "sourceLineSequence",
          baseResidual.lineSequence());
      residualEvidence.put(
          "baseResidualAmount",
          baseResidualAmount);
      residualEvidence.put(
          "supplementalActiveTotal",
          supplementalTotal);
      residualEvidence.put(
          "rawComposedResidualAmount",
          rawResidual);

      lines.add(new SalaryStructureValidationLineView(
          UUID.randomUUID(),
          nextSequence,
          baseResidual.componentId(),
          baseResidual.componentVersionId(),
          baseResidual.componentCode(),
          baseResidual.componentName(),
          adjustedResidual,
          monthly(adjustedResidual),
          "RESIDUAL",
          residualEvidence));
    }

    BigDecimal total = lines.stream()
        .map(SalaryStructureValidationLineView::annualAmount)
        .filter(amount -> amount != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(4, RoundingMode.HALF_UP);
    BigDecimal delta = structure.targetAnnualAmount()
        .subtract(total)
        .abs()
        .setScale(4, RoundingMode.HALF_UP);
    if (delta.compareTo(structure.toleranceAmount()) > 0) {
      errors.add(issue(
          "COMPOSED_TARGET_RECONCILIATION_FAILED",
          "Composed total differs from target beyond tolerance"));
    }

    Map<String, BigDecimal> costViews = costViews(
        policy,
        request.effectiveDate(),
        structure.targetAnnualAmount(),
        activeAmounts,
        warnings);

    String status = errors.isEmpty() ? "PASS" : "FAIL";
    String requestHash = canonical.hash(request);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("disclaimer", DISCLAIMER);
    summary.put("composedSimulation", true);
    summary.put(
        "compositionRevision",
        snapshot.compositionRevision());
    summary.put("baseResultHash", baseValidation.resultHash());
    summary.put("targetType", structure.targetType());
    summary.put(
        "targetAnnualAmount",
        structure.targetAnnualAmount());
    summary.put("totalAnnualAmount", total);
    summary.put("reconciliationDelta", delta);
    summary.put("toleranceAmount", structure.toleranceAmount());
    summary.put(
        "supplementalBindingIds",
        snapshot.bindings().stream()
            .map(binding -> binding.bindingId().toString())
            .toList());
    summary.put(
        "supplementalPlanVersionIds",
        snapshot.bindings().stream()
            .map(binding ->
                binding.supplementalPlanVersionId().toString())
            .toList());
    summary.put(
        "supplementalActiveTotal",
        supplementalTotal);
    summary.put("baseResidualAmount", baseResidualAmount);
    summary.put("adjustedResidualAmount", adjustedResidual);
    summary.put("costViews", costViews);
    summary.put(
        "statutoryCompatibilityStatus",
        baseValidation.summary().getOrDefault(
            "statutoryCompatibilityStatus",
            "STRUCTURAL_ONLY"));
    summary.put("blockingErrors", List.copyOf(errors));
    summary.put("warnings", List.copyOf(warnings));

    Map<String, Object> resultState = new LinkedHashMap<>();
    resultState.put("versionId", structure.versionId());
    resultState.put(
        "configurationHash",
        structure.configurationHash());
    resultState.put("requestHash", requestHash);
    resultState.put("baseResultHash", baseValidation.resultHash());
    resultState.put(
        "compositionRevision",
        snapshot.compositionRevision());
    resultState.put("validationStatus", status);
    resultState.put("summary", summary);
    resultState.put(
        "lines",
        lines.stream().map(this::lineState).toList());
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
        summary,
        null,
        actor.require(),
        DISCLAIMER,
        List.copyOf(lines));
  }

  private Map<String, Object> lineState(
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
      if (!active(
          treatment.effectiveFrom(),
          treatment.effectiveTo(),
          effectiveDate)) {
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
            "CTC_TREATMENT_COMPONENT_NOT_IN_COMPOSITION",
            "No composed amount exists for treatment component "
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

  private String classification(
      SupplementalPlanLineView line,
      CtcPolicyView policy,
      LocalDate effectiveDate) {
    return policy.treatments().stream()
        .filter(treatment -> treatment.componentVersionId()
            .equals(line.componentVersionId()))
        .filter(treatment -> active(
            treatment.effectiveFrom(),
            treatment.effectiveTo(),
            effectiveDate))
        .map(CtcPolicyTreatmentView::treatmentType)
        .map(type -> switch (type) {
          case "EMPLOYER_CONTRIBUTION" -> "EMPLOYER_CONTRIBUTION";
          case "PROVISION" -> "PROVISION";
          case "BENEFIT_PREMIUM" -> "BENEFIT";
          case "INFORMATIONAL", "EXCLUDE" -> "INFORMATIONAL";
          default -> line.defaultPercentage() != null
              ? "VARIABLE" : "FIXED";
        })
        .findFirst()
        .orElse(line.defaultPercentage() != null
            ? "VARIABLE" : "FIXED");
  }

  private boolean withinBounds(
      SupplementalPlanLineView line,
      BigDecimal amount) {
    return (line.minimumAmount() == null
            || amount.compareTo(line.minimumAmount()) >= 0)
        && (line.maximumAmount() == null
            || amount.compareTo(line.maximumAmount()) <= 0);
  }

  private boolean withinBounds(
      SalaryStructureLineView line,
      BigDecimal amount) {
    return (line.minimumAmount() == null
            || amount.compareTo(line.minimumAmount()) >= 0)
        && (line.maximumAmount() == null
            || amount.compareTo(line.maximumAmount()) <= 0);
  }

  private boolean active(
      LocalDate from,
      LocalDate to,
      LocalDate date) {
    return !date.isBefore(from)
        && (to == null || date.isBefore(to));
  }

  private BigDecimal scaled(BigDecimal value) {
    return value == null
        ? BigDecimal.ZERO.setScale(4)
        : value.setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal monthly(BigDecimal annual) {
    return scaled(annual)
        .divide(TWELVE, 4, RoundingMode.HALF_UP);
  }

  private Map<String, Object> copyEvidence(
      Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    if (source != null) {
      copy.putAll(source);
    }
    return copy;
  }

  @SuppressWarnings("unchecked")
  private void copyIssues(
      Object source,
      List<Map<String, Object>> target) {
    if (!(source instanceof List<?> list)) {
      return;
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) ->
            copy.put(String.valueOf(key), value));
        target.add(copy);
      }
    }
  }

  private Map<String, Object> issue(
      String code,
      String message) {
    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put("code", code);
    issue.put("message", message);
    return issue;
  }

  private void recordValidation(
      SalaryStructureValidationView validation,
      long compositionRevision) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("validationId", validation.validationId());
    summary.put("versionId", validation.versionId());
    summary.put(
        "validationStatus",
        validation.validationStatus());
    summary.put("requestHash", validation.requestHash());
    summary.put(
        "configurationHash",
        validation.configurationHash());
    summary.put("resultHash", validation.resultHash());
    summary.put(
        "blockingErrorCount",
        validation.blockingErrorCount());
    summary.put("warningCount", validation.warningCount());
    summary.put("compositionRevision", compositionRevision);

    audit.append(
        "COMPOSITION_SIMULATED",
        OBJECT_TYPE,
        validation.identityId(),
        null,
        summary,
        Map.of(
            "versionId",
            validation.versionId(),
            "compositionRevision",
            compositionRevision),
        actor.require());

    var event = events.create(
        "SalaryStructureCompositionSIMULATED",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        validation.identityId(),
        compositionRevision + 1,
        summary);
    outbox.append(event);
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<T> work) {
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException(
              "Idempotency-Key was already used with a different composition");
        }
        if (!saved.get().completed()) {
          throw new ConflictException(
              "Idempotent composition simulation is still in progress");
        }
        try {
          return objectMapper.readValue(
              saved.get().body(),
              responseType);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored idempotent composition response is invalid",
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

  private void requireIdentity(
      SalaryStructureView structure,
      UUID identityId) {
    if (!structure.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to salary-structure identity");
    }
  }

  private record CompositionSnapshot(
      SalaryStructureView structure,
      long compositionRevision,
      List<SupplementalPlanBindingView> bindings,
      Map<UUID, SupplementalPlanView> plans) {}
}
