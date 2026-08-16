package com.acme.hrms.payroll.compensation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SalaryStructureSupplementalPlanControls {
  private static final Set<String> PLAN_TYPES =
      Set.of("ALLOWANCE", "BENEFIT", "INCENTIVE");

  private SalaryStructureSupplementalPlanControls() {}

  public record SupplementalPlanCreateRequest(
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
      @NotNull @Valid SupplementalPlanVersionWriteRequest version) {
    public void validate() {
      if (code == null || !code.matches("^[A-Z][A-Z0-9_]{1,39}$")) {
        throw new IllegalArgumentException(
            "Supplemental-plan code must be canonical uppercase");
      }
      version.validate();
    }
  }

  public record SupplementalPlanVersionWriteRequest(
      @NotBlank String name,
      @NotBlank String planType,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      @NotEmpty List<@Valid SupplementalPlanLineWriteRequest> lines) {
    public void validate() {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Supplemental-plan name is required");
      }
      if (!PLAN_TYPES.contains(planType)) {
        throw new IllegalArgumentException(
            "planType must be ALLOWANCE, BENEFIT or INCENTIVE");
      }
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom is required");
      }
      if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
        throw new IllegalArgumentException(
            "effectiveTo must be after effectiveFrom");
      }
      if (lines == null || lines.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one supplemental-plan line is required");
      }
      if (lines.size() > 100) {
        throw new IllegalArgumentException(
            "Supplemental plans are limited to 100 lines");
      }

      Set<UUID> components = new HashSet<>();
      Set<Integer> sequences = new HashSet<>();
      for (SupplementalPlanLineWriteRequest line : lines) {
        line.validate(effectiveFrom, effectiveTo);
        if (!components.add(line.componentVersionId())) {
          throw new IllegalArgumentException(
              "Component versions cannot be duplicated in one plan version");
        }
        if (!sequences.add(line.sequenceNo())) {
          throw new IllegalArgumentException(
              "Supplemental-plan line sequences must be unique");
        }
      }
    }
  }

  public record SupplementalPlanLineWriteRequest(
      @NotNull UUID componentVersionId,
      int sequenceNo,
      BigDecimal defaultAmount,
      BigDecimal defaultPercentage,
      UUID percentageBaseComponentVersionId,
      BigDecimal minimumAmount,
      BigDecimal maximumAmount,
      boolean employeeOverrideAllowed,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate(
        LocalDate parentEffectiveFrom,
        LocalDate parentEffectiveTo) {
      if (componentVersionId == null) {
        throw new IllegalArgumentException("componentVersionId is required");
      }
      if (sequenceNo < 1) {
        throw new IllegalArgumentException("sequenceNo must be positive");
      }
      if (defaultAmount != null && defaultAmount.signum() < 0) {
        throw new IllegalArgumentException("defaultAmount cannot be negative");
      }
      if (defaultPercentage != null
          && (defaultPercentage.signum() <= 0
              || defaultPercentage.compareTo(BigDecimal.valueOf(100)) > 0)) {
        throw new IllegalArgumentException(
            "defaultPercentage must be greater than 0 and at most 100");
      }

      boolean fixed = defaultAmount != null;
      boolean percentage = defaultPercentage != null;
      if (fixed == percentage) {
        throw new IllegalArgumentException(
            "Exactly one of defaultAmount or defaultPercentage is required");
      }
      if (percentage && percentageBaseComponentVersionId == null) {
        throw new IllegalArgumentException(
            "percentageBaseComponentVersionId is required for percentage defaults");
      }
      if (fixed && percentageBaseComponentVersionId != null) {
        throw new IllegalArgumentException(
            "percentageBaseComponentVersionId is allowed only for percentage defaults");
      }
      if (componentVersionId.equals(percentageBaseComponentVersionId)) {
        throw new IllegalArgumentException(
            "A supplemental line cannot calculate from itself");
      }

      if (minimumAmount != null && minimumAmount.signum() < 0) {
        throw new IllegalArgumentException("minimumAmount cannot be negative");
      }
      if (maximumAmount != null && maximumAmount.signum() < 0) {
        throw new IllegalArgumentException("maximumAmount cannot be negative");
      }
      if (minimumAmount != null
          && maximumAmount != null
          && maximumAmount.compareTo(minimumAmount) < 0) {
        throw new IllegalArgumentException(
            "maximumAmount cannot be below minimumAmount");
      }

      LocalDate from =
          effectiveFrom == null ? parentEffectiveFrom : effectiveFrom;
      LocalDate to =
          effectiveTo == null ? parentEffectiveTo : effectiveTo;
      if (from.isBefore(parentEffectiveFrom)) {
        throw new IllegalArgumentException(
            "Line effectiveFrom cannot precede the plan");
      }
      if (to != null && !to.isAfter(from)) {
        throw new IllegalArgumentException(
            "Line effectiveTo must be after effectiveFrom");
      }
      if (parentEffectiveTo != null
          && (to == null || to.isAfter(parentEffectiveTo))) {
        throw new IllegalArgumentException(
            "Line effectiveTo cannot exceed the plan");
      }
    }

    public LocalDate resolvedEffectiveFrom(LocalDate parentFrom) {
      return effectiveFrom == null ? parentFrom : effectiveFrom;
    }

    public LocalDate resolvedEffectiveTo(LocalDate parentTo) {
      return effectiveTo == null ? parentTo : effectiveTo;
    }
  }

  public record SupplementalPlanLineView(
      UUID lineId,
      UUID componentId,
      UUID componentVersionId,
      String componentCode,
      String componentName,
      int sequenceNo,
      BigDecimal defaultAmount,
      BigDecimal defaultPercentage,
      UUID percentageBaseComponentVersionId,
      BigDecimal minimumAmount,
      BigDecimal maximumAmount,
      boolean employeeOverrideAllowed,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      long versionNo) {}

  public record SupplementalPlanView(
      UUID identityId,
      String code,
      String lifecycleStatus,
      long identityVersionNo,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String name,
      String planType,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String approvalStatus,
      Instant approvedAt,
      String approvedBy,
      UUID supersedesVersionId,
      boolean superseded,
      List<SupplementalPlanLineView> lines) {}

  public record SupplementalPlanBindingWriteRequest(
      @NotNull UUID supplementalPlanVersionId,
      int sequenceNo,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      if (supplementalPlanVersionId == null) {
        throw new IllegalArgumentException(
            "supplementalPlanVersionId is required");
      }
      if (sequenceNo < 1) {
        throw new IllegalArgumentException("sequenceNo must be positive");
      }
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom is required");
      }
      if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
        throw new IllegalArgumentException(
            "effectiveTo must be after effectiveFrom");
      }
    }
  }

  public record SupplementalPlanBindingView(
      UUID bindingId,
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      UUID supplementalPlanId,
      UUID supplementalPlanVersionId,
      String supplementalPlanCode,
      String supplementalPlanName,
      String planType,
      int sequenceNo,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      long versionNo,
      long compositionRevision) {}
}
