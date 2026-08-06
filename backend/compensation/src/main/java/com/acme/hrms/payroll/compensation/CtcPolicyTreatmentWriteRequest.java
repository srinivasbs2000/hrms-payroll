package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CtcPolicyTreatmentWriteRequest(
    @NotNull UUID componentId,
    @NotNull UUID componentVersionId,
    @NotNull @Min(1) Integer treatmentSequence,
    @NotBlank @Pattern(regexp = "^(OFFERED|TARGET|ACCRUED|ACTUAL_EMPLOYER_COST)$")
        String costView,
    @NotBlank
        @Pattern(
            regexp =
                "^(FIXED_VALUE|TARGET_VALUE|ACTUAL_VALUE|PROVISION|"
                    + "EMPLOYER_CONTRIBUTION|BENEFIT_PREMIUM|EXCLUDE|INFORMATIONAL)$")
        String treatmentType,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal fixedValue,
    @DecimalMin("0.00000000") @Digits(integer = 3, fraction = 8)
        BigDecimal targetPercentage,
    UUID payrollBaseId,
    UUID payrollBaseVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> COST_VIEWS =
      Set.of("OFFERED", "TARGET", "ACCRUED", "ACTUAL_EMPLOYER_COST");
  private static final Set<String> TREATMENT_TYPES =
      Set.of(
          "FIXED_VALUE",
          "TARGET_VALUE",
          "ACTUAL_VALUE",
          "PROVISION",
          "EMPLOYER_CONTRIBUTION",
          "BENEFIT_PREMIUM",
          "EXCLUDE",
          "INFORMATIONAL");

  public void validate(LocalDate parentFrom, LocalDate parentTo) {
    if (componentId == null || componentVersionId == null) {
      throw new IllegalArgumentException(
          "componentId and componentVersionId are required");
    }
    if (treatmentSequence == null || treatmentSequence < 1) {
      throw new IllegalArgumentException(
          "treatmentSequence must be greater than zero");
    }
    requireMember(COST_VIEWS, costView, "costView");
    requireMember(TREATMENT_TYPES, treatmentType, "treatmentType");

    if ("FIXED_VALUE".equals(treatmentType)) {
      if (fixedValue == null || fixedValue.signum() < 0) {
        throw new IllegalArgumentException(
            "FIXED_VALUE treatment requires a non-negative fixedValue");
      }
      if (targetPercentage != null) {
        throw new IllegalArgumentException(
            "FIXED_VALUE treatment must not contain targetPercentage");
      }
    } else if ("TARGET_VALUE".equals(treatmentType)) {
      if (targetPercentage == null
          || targetPercentage.signum() <= 0
          || targetPercentage.compareTo(new BigDecimal("100")) > 0) {
        throw new IllegalArgumentException(
            "TARGET_VALUE treatment requires targetPercentage greater than zero and at most 100");
      }
      if (fixedValue != null) {
        throw new IllegalArgumentException(
            "TARGET_VALUE treatment must not contain fixedValue");
      }
    } else if (fixedValue != null || targetPercentage != null) {
      throw new IllegalArgumentException(
          "Only FIXED_VALUE and TARGET_VALUE treatments may contain numeric values");
    }

    if ((payrollBaseId == null) != (payrollBaseVersionId == null)) {
      throw new IllegalArgumentException(
          "payrollBaseId and payrollBaseVersionId must be supplied together");
    }
    if (parentFrom == null) {
      throw new IllegalArgumentException("parent effectiveFrom is required");
    }

    LocalDate from = resolvedEffectiveFrom(parentFrom);
    LocalDate to = resolvedEffectiveTo(parentTo);
    if (from.isBefore(parentFrom)) {
      throw new IllegalArgumentException(
          "Treatment effectiveFrom must be contained by the policy version");
    }
    if (to != null && !to.isAfter(from)) {
      throw new IllegalArgumentException(
          "Treatment effectiveTo must be after effectiveFrom");
    }
    if (parentTo != null && (to == null || to.isAfter(parentTo))) {
      throw new IllegalArgumentException(
          "Treatment effectiveTo must be contained by the policy version");
    }
  }

  public LocalDate resolvedEffectiveFrom(LocalDate parentFrom) {
    return effectiveFrom == null ? parentFrom : effectiveFrom;
  }

  public LocalDate resolvedEffectiveTo(LocalDate parentTo) {
    return effectiveTo == null ? parentTo : effectiveTo;
  }

  private static void requireMember(
      Set<String> values,
      String value,
      String field) {
    if (value == null || value.isBlank() || !values.contains(value)) {
      throw new IllegalArgumentException(
          field + " contains an unsupported value");
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown request field: " + property);
  }
}
