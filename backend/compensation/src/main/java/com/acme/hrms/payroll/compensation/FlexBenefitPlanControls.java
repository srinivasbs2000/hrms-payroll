package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FlexBenefitPlanControls {
  private static final Set<String> JOINING_RULES =
      Set.of("OPEN_SPECIAL_WINDOW", "DEFAULT_ELECTION", "NEXT_WINDOW", "APPROVAL_REQUIRED");
  private static final Set<String> CHANGE_RULES =
      Set.of("PROHIBITED", "QUALIFYING_EVENT_ONLY", "APPROVAL_REQUIRED");
  private static final Set<String> UNUSED_RULES =
      Set.of("CARRY_FORWARD", "TAXABLE_FALLBACK", "ENCASH", "FORFEIT");
  private static final Set<String> FINAL_RULES =
      Set.of("ENCASH", "TAXABLE_FALLBACK", "FORFEIT", "POLICY_ENGINE");
  private static final Set<String> RETRO_RULES =
      Set.of("PROHIBITED", "OPEN_PERIOD_ONLY", "APPROVAL_REQUIRED");
  private static final String DISCLAIMER =
      "DESIGN-TIME FLEX-BENEFIT POLICY VALIDATION — NOT AN EMPLOYEE ELECTION OR PAYROLL RESULT";

  private FlexBenefitPlanControls() {}

  public record FlexBenefitPlanCreateRequest(
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
      @NotNull @Valid FlexBenefitPlanVersionWriteRequest version) {
    public void validate() {
      if (code == null || !code.matches("^[A-Z][A-Z0-9_]{1,39}$")) {
        throw new IllegalArgumentException(
            "Flex-benefit plan code must be canonical uppercase");
      }
      if (version == null) {
        throw new IllegalArgumentException("Flex-benefit plan version is required");
      }
      version.validate();
    }
  }

  public record FlexBenefitPlanVersionWriteRequest(
      @NotBlank String name,
      String currency,
      @NotNull UUID supplementalPlanVersionId,
      UUID eligibilityRuleVersionId,
      @NotNull BigDecimal annualBasketAmount,
      @NotNull LocalDate electionWindowStart,
      @NotNull LocalDate electionWindowEnd,
      @NotBlank String midYearJoiningRule,
      Integer joiningElectionWindowDays,
      @NotBlank String midYearChangeRule,
      @NotBlank String unusedBalanceRule,
      BigDecimal carryForwardLimit,
      UUID taxableFallbackComponentVersionId,
      UUID encashmentComponentVersionId,
      @NotBlank String finalSettlementRule,
      @NotBlank String retroCorrectionRule,
      boolean allowTotalCompensationChange,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      @NotEmpty List<@Valid FlexBenefitOptionWriteRequest> options) {

    public void validate() {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Flex-benefit plan name is required");
      }
      if (!"INR".equals(resolvedCurrency())) {
        throw new IllegalArgumentException("Flex-benefit plan currency must be INR");
      }
      if (supplementalPlanVersionId == null) {
        throw new IllegalArgumentException(
            "Approved BENEFIT supplemental-plan version is required");
      }
      if (annualBasketAmount == null || annualBasketAmount.signum() <= 0) {
        throw new IllegalArgumentException("Annual basket amount must be positive");
      }
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom is required");
      }
      if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
        throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
      }
      if (electionWindowStart == null || electionWindowEnd == null
          || !electionWindowEnd.isAfter(electionWindowStart)) {
        throw new IllegalArgumentException(
            "Election window must have a positive half-open date range");
      }
      if (electionWindowStart.isBefore(effectiveFrom)
          || (effectiveTo != null && electionWindowEnd.isAfter(effectiveTo))) {
        throw new IllegalArgumentException(
            "Election window must be contained by the plan version effective period");
      }
      if (!JOINING_RULES.contains(midYearJoiningRule)) {
        throw new IllegalArgumentException("Unsupported mid-year joining rule");
      }
      if ("OPEN_SPECIAL_WINDOW".equals(midYearJoiningRule)) {
        if (joiningElectionWindowDays == null
            || joiningElectionWindowDays < 1
            || joiningElectionWindowDays > 365) {
          throw new IllegalArgumentException(
              "OPEN_SPECIAL_WINDOW requires joiningElectionWindowDays between 1 and 365");
        }
      } else if (joiningElectionWindowDays != null) {
        throw new IllegalArgumentException(
            "joiningElectionWindowDays is only valid for OPEN_SPECIAL_WINDOW");
      }
      if (!CHANGE_RULES.contains(midYearChangeRule)) {
        throw new IllegalArgumentException("Unsupported mid-year change rule");
      }
      if (!UNUSED_RULES.contains(unusedBalanceRule)) {
        throw new IllegalArgumentException("Unsupported unused-balance rule");
      }
      if (!FINAL_RULES.contains(finalSettlementRule)) {
        throw new IllegalArgumentException("Unsupported final-settlement rule");
      }
      if (!RETRO_RULES.contains(retroCorrectionRule)) {
        throw new IllegalArgumentException("Unsupported retro-correction rule");
      }
      validateResidualConfiguration();
      validateOptions();
    }

    public String resolvedCurrency() {
      return currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
    }

    private void validateResidualConfiguration() {
      if ("CARRY_FORWARD".equals(unusedBalanceRule)) {
        if (carryForwardLimit == null
            || carryForwardLimit.compareTo(annualBasketAmount) < 0) {
          throw new IllegalArgumentException(
              "CARRY_FORWARD requires a carry-forward limit covering the full annual basket");
        }
      } else if (carryForwardLimit != null) {
        throw new IllegalArgumentException(
            "carryForwardLimit is only valid with CARRY_FORWARD");
      }
      if (("TAXABLE_FALLBACK".equals(unusedBalanceRule)
              || "TAXABLE_FALLBACK".equals(finalSettlementRule))
          && taxableFallbackComponentVersionId == null) {
        throw new IllegalArgumentException(
            "Taxable fallback treatment requires a fallback component version");
      }
      if (("ENCASH".equals(unusedBalanceRule)
              || "ENCASH".equals(finalSettlementRule))
          && encashmentComponentVersionId == null) {
        throw new IllegalArgumentException(
            "Encashment treatment requires an encashment component version");
      }
    }

    private void validateOptions() {
      if (options == null || options.isEmpty()) {
        throw new IllegalArgumentException("At least one flex-benefit option is required");
      }
      Set<Integer> sequences = new HashSet<>();
      Set<UUID> components = new HashSet<>();
      BigDecimal minimums = BigDecimal.ZERO;
      BigDecimal defaults = BigDecimal.ZERO;
      for (FlexBenefitOptionWriteRequest option : options) {
        if (option == null) {
          throw new IllegalArgumentException("Flex-benefit options cannot contain null");
        }
        option.validate();
        if (!sequences.add(option.optionSequence())) {
          throw new IllegalArgumentException("Flex-benefit option sequence must be unique");
        }
        if (!components.add(option.componentVersionId())) {
          throw new IllegalArgumentException(
              "A component version can appear only once in a flex-benefit plan version");
        }
        minimums = minimums.add(option.resolvedMinimumAnnualAmount());
        defaults = defaults.add(option.resolvedDefaultAnnualAmount());
      }
      if (minimums.compareTo(annualBasketAmount) > 0) {
        throw new IllegalArgumentException(
            "Minimum flex-benefit allocations cannot exceed the annual basket");
      }
      if (defaults.compareTo(annualBasketAmount) > 0) {
        throw new IllegalArgumentException(
            "Default flex-benefit allocations cannot exceed the annual basket");
      }
    }
  }

  public record FlexBenefitOptionWriteRequest(
      int optionSequence,
      @NotNull UUID componentVersionId,
      BigDecimal minimumAnnualAmount,
      BigDecimal maximumAnnualAmount,
      BigDecimal defaultAnnualAmount,
      boolean proofRequired) {
    public void validate() {
      if (optionSequence < 1) {
        throw new IllegalArgumentException("Flex-benefit option sequence must be positive");
      }
      if (componentVersionId == null) {
        throw new IllegalArgumentException("Flex-benefit option component version is required");
      }
      BigDecimal minimum = resolvedMinimumAnnualAmount();
      BigDecimal maximum = maximumAnnualAmount;
      BigDecimal defaultValue = resolvedDefaultAnnualAmount();
      if (minimum.signum() < 0
          || (maximum != null && maximum.signum() < 0)
          || defaultValue.signum() < 0) {
        throw new IllegalArgumentException("Flex-benefit option amounts cannot be negative");
      }
      if (maximum != null && maximum.compareTo(minimum) < 0) {
        throw new IllegalArgumentException(
            "Flex-benefit option maximum cannot be below minimum");
      }
      if (defaultValue.compareTo(minimum) < 0
          || (maximum != null && defaultValue.compareTo(maximum) > 0)) {
        throw new IllegalArgumentException(
            "Default flex-benefit allocation must be within option minimum and maximum");
      }
    }
    public BigDecimal resolvedMinimumAnnualAmount() {
      return minimumAnnualAmount == null ? BigDecimal.ZERO : minimumAnnualAmount;
    }
    public BigDecimal resolvedDefaultAnnualAmount() {
      return defaultAnnualAmount == null ? BigDecimal.ZERO : defaultAnnualAmount;
    }
  }

  public record FlexBenefitOptionView(
      UUID optionId, UUID componentId, UUID componentVersionId,
      String componentCode, String componentName, int optionSequence,
      BigDecimal minimumAnnualAmount, BigDecimal maximumAnnualAmount,
      BigDecimal defaultAnnualAmount, boolean proofRequired, long versionNo) {}

  public record FlexBenefitPlanView(
      UUID identityId, String code, String lifecycleStatus, long identityVersionNo,
      UUID versionId, int versionSequence, long versionNo, String name, String currency,
      UUID supplementalPlanId, UUID supplementalPlanVersionId, String supplementalPlanCode,
      String supplementalPlanName, int supplementalPlanVersionSequence,
      UUID eligibilityRuleId, UUID eligibilityRuleVersionId, String eligibilityRuleCode,
      BigDecimal annualBasketAmount, LocalDate electionWindowStart, LocalDate electionWindowEnd,
      String midYearJoiningRule, Integer joiningElectionWindowDays, String midYearChangeRule,
      String unusedBalanceRule, BigDecimal carryForwardLimit,
      UUID taxableFallbackComponentVersionId, UUID encashmentComponentVersionId,
      String finalSettlementRule, String retroCorrectionRule,
      boolean allowTotalCompensationChange, LocalDate effectiveFrom, LocalDate effectiveTo,
      String approvalStatus, Instant approvedAt, String approvedBy,
      UUID supersedesVersionId, boolean superseded, List<FlexBenefitOptionView> options) {}

  public record FlexElectionAllocationRequest(
      @NotNull UUID componentVersionId, @NotNull BigDecimal annualAmount) {
    public void validate() {
      if (componentVersionId == null) {
        throw new IllegalArgumentException("Election component version is required");
      }
      if (annualAmount == null || annualAmount.signum() < 0) {
        throw new IllegalArgumentException("Election amount cannot be negative");
      }
    }
  }

  public record FlexElectionValidationRequest(
      @NotNull LocalDate electionDate, LocalDate joiningDate, boolean midYearChange,
      boolean qualifyingEvent, boolean approvedPolicyException,
      boolean approvedCompensationAdjustment,
      Map<String, JsonNode> eligibilityFacts,
      List<@Valid FlexElectionAllocationRequest> allocations) {
    public void validate() {
      if (electionDate == null) {
        throw new IllegalArgumentException("Election date is required");
      }
      if (allocations != null) {
        for (FlexElectionAllocationRequest allocation : allocations) {
          if (allocation == null) {
            throw new IllegalArgumentException("Election allocations cannot contain null");
          }
          allocation.validate();
        }
      }
    }
    public Map<String, JsonNode> resolvedEligibilityFacts() {
      return eligibilityFacts == null ? Map.of() : Map.copyOf(eligibilityFacts);
    }

    public List<FlexElectionAllocationRequest> resolvedAllocations() {
      return allocations == null ? List.of() : allocations;
    }
  }

  public record FlexElectionValidationView(
      String validationStatus, BigDecimal annualBasketAmount,
      BigDecimal electedAnnualAmount, BigDecimal residualAnnualAmount,
      String residualTreatment, List<String> blockers, List<String> warnings,
      String disclaimer) {}

  public static FlexElectionValidationView validateElection(
      FlexBenefitPlanView plan, FlexElectionValidationRequest request) {
    if (plan == null) {
      throw new IllegalArgumentException("Flex-benefit plan version is required");
    }
    request.validate();
    List<String> blockers = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    LocalDate date = request.electionDate();
    if (date.isBefore(plan.effectiveFrom())
        || (plan.effectiveTo() != null && !date.isBefore(plan.effectiveTo()))) {
      blockers.add("ELECTION_OUTSIDE_PLAN_EFFECTIVE_PERIOD");
    }
    boolean inRegularWindow =
        !date.isBefore(plan.electionWindowStart()) && date.isBefore(plan.electionWindowEnd());
    if (!inRegularWindow) {
      enforceOutsideWindowPolicy(plan, request, blockers);
    }
    Map<UUID, FlexBenefitOptionView> optionByComponent = new HashMap<>();
    for (FlexBenefitOptionView option : plan.options()) {
      optionByComponent.put(option.componentVersionId(), option);
    }
    List<FlexElectionAllocationRequest> allocations = request.resolvedAllocations();
    if (allocations.isEmpty()) {
      allocations = plan.options().stream()
          .filter(option -> option.defaultAnnualAmount().signum() > 0)
          .map(option -> new FlexElectionAllocationRequest(
              option.componentVersionId(), option.defaultAnnualAmount()))
          .toList();
      if (!allocations.isEmpty()) {
        warnings.add("DEFAULT_ELECTION_APPLIED");
      }
    }
    Set<UUID> seen = new HashSet<>();
    BigDecimal elected = BigDecimal.ZERO;
    for (FlexElectionAllocationRequest allocation : allocations) {
      if (!seen.add(allocation.componentVersionId())) {
        blockers.add("DUPLICATE_ELECTION_COMPONENT");
        continue;
      }
      FlexBenefitOptionView option = optionByComponent.get(allocation.componentVersionId());
      if (option == null) {
        blockers.add("COMPONENT_NOT_IN_FLEX_BENEFIT_PLAN");
        continue;
      }
      if (allocation.annualAmount().compareTo(option.minimumAnnualAmount()) < 0
          || (option.maximumAnnualAmount() != null
              && allocation.annualAmount().compareTo(option.maximumAnnualAmount()) > 0)) {
        blockers.add("OPTION_ALLOCATION_OUTSIDE_CONFIGURED_LIMITS");
      }
      elected = elected.add(allocation.annualAmount());
    }
    if (elected.compareTo(plan.annualBasketAmount()) > 0) {
      if (plan.allowTotalCompensationChange() && request.approvedCompensationAdjustment()) {
        warnings.add("APPROVED_TOTAL_COMPENSATION_ADJUSTMENT_REQUIRED_DOWNSTREAM");
      } else {
        blockers.add("ELECTION_EXCEEDS_APPROVED_ANNUAL_BASKET");
      }
    }
    BigDecimal residual = plan.annualBasketAmount().subtract(elected).max(BigDecimal.ZERO);
    if ("TAXABLE_FALLBACK".equals(plan.unusedBalanceRule()) && residual.signum() > 0) {
      warnings.add("UNUSED_BALANCE_REQUIRES_TAXABLE_FALLBACK_COMPONENT");
    }
    return new FlexElectionValidationView(
        blockers.isEmpty() ? "PASS" : "FAIL", plan.annualBasketAmount(), elected, residual,
        plan.unusedBalanceRule(), List.copyOf(blockers), List.copyOf(warnings), DISCLAIMER);
  }

  private static void enforceOutsideWindowPolicy(
      FlexBenefitPlanView plan, FlexElectionValidationRequest request, List<String> blockers) {
    LocalDate joining = request.joiningDate();
    if (joining != null && !joining.isAfter(request.electionDate())) {
      switch (plan.midYearJoiningRule()) {
        case "OPEN_SPECIAL_WINDOW" -> {
          if (plan.joiningElectionWindowDays() == null
              || request.electionDate().isAfter(joining.plusDays(plan.joiningElectionWindowDays()))) {
            blockers.add("MID_YEAR_JOINER_SPECIAL_WINDOW_CLOSED");
          }
        }
        case "DEFAULT_ELECTION" -> {
          if (!request.resolvedAllocations().isEmpty()) {
            blockers.add("MID_YEAR_JOINER_DEFAULT_ELECTION_REQUIRED");
          }
        }
        case "APPROVAL_REQUIRED" -> {
          if (!request.approvedPolicyException()) {
            blockers.add("MID_YEAR_JOINER_APPROVAL_REQUIRED");
          }
        }
        default -> blockers.add("MID_YEAR_JOINER_MUST_WAIT_FOR_NEXT_WINDOW");
      }
      return;
    }
    if (request.midYearChange()) {
      switch (plan.midYearChangeRule()) {
        case "QUALIFYING_EVENT_ONLY" -> {
          if (!request.qualifyingEvent()) {
            blockers.add("MID_YEAR_CHANGE_REQUIRES_QUALIFYING_EVENT");
          }
        }
        case "APPROVAL_REQUIRED" -> {
          if (!request.approvedPolicyException()) {
            blockers.add("MID_YEAR_CHANGE_REQUIRES_APPROVAL");
          }
        }
        default -> blockers.add("MID_YEAR_CHANGE_PROHIBITED");
      }
      return;
    }
    blockers.add("ELECTION_OUTSIDE_CONFIGURED_WINDOW");
  }
}
