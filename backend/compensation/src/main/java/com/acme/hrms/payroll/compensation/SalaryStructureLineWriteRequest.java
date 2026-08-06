package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SalaryStructureLineWriteRequest(
    @NotNull UUID componentVersionId,
    @NotNull @Min(1) Integer sequenceNo,
    @NotBlank
        @Pattern(regexp = "^(FIXED|PERCENTAGE|RESIDUAL)$")
        String lineType,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal targetAmount,
    @DecimalMin(value = "0.000000", inclusive = false)
        @DecimalMax("100.000000")
        @Digits(integer = 3, fraction = 6)
        BigDecimal targetPercentage,
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$")
        String percentageBaseCode,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal minimumAmount,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal maximumAmount,
    @NotNull Boolean mandatory,
    @NotBlank
        @Pattern(regexp = "^(PROHIBITED|CONTROLLED|ALLOWED)$")
        String overridePolicy,
    @NotNull @Min(1) Integer ctcDisplayOrder,
    @NotNull @Min(1) Integer payslipDisplayOrder) {

  private static final Set<String> LINE_TYPES =
      Set.of("FIXED", "PERCENTAGE", "RESIDUAL");
  private static final Set<String> OVERRIDE_POLICIES =
      Set.of("PROHIBITED", "CONTROLLED", "ALLOWED");

  public void validate() {
    if (componentVersionId == null) {
      throw new IllegalArgumentException(
          "componentVersionId is required");
    }
    if (sequenceNo == null || sequenceNo < 1) {
      throw new IllegalArgumentException(
          "sequenceNo must be greater than zero");
    }
    if (!LINE_TYPES.contains(lineType)) {
      throw new IllegalArgumentException(
          "lineType contains an unsupported value");
    }
    if (!OVERRIDE_POLICIES.contains(overridePolicy)) {
      throw new IllegalArgumentException(
          "overridePolicy contains an unsupported value");
    }
    if (mandatory == null) {
      throw new IllegalArgumentException(
          "mandatory is required");
    }
    if (ctcDisplayOrder == null || ctcDisplayOrder < 1
        || payslipDisplayOrder == null || payslipDisplayOrder < 1) {
      throw new IllegalArgumentException(
          "Display orders must be greater than zero");
    }
    if (minimumAmount != null && minimumAmount.signum() < 0) {
      throw new IllegalArgumentException(
          "minimumAmount must not be negative");
    }
    if (maximumAmount != null && maximumAmount.signum() < 0) {
      throw new IllegalArgumentException(
          "maximumAmount must not be negative");
    }
    if (minimumAmount != null && maximumAmount != null
        && maximumAmount.compareTo(minimumAmount) < 0) {
      throw new IllegalArgumentException(
          "maximumAmount must be greater than or equal to minimumAmount");
    }

    boolean fixed = "FIXED".equals(lineType)
        && targetAmount != null
        && targetPercentage == null
        && blank(percentageBaseCode);
    boolean percentage = "PERCENTAGE".equals(lineType)
        && targetAmount == null
        && targetPercentage != null
        && targetPercentage.signum() > 0
        && targetPercentage.compareTo(
            new BigDecimal("100.000000")) <= 0
        && !blank(percentageBaseCode);
    boolean residual = "RESIDUAL".equals(lineType)
        && targetAmount == null
        && targetPercentage == null
        && blank(percentageBaseCode);

    if (!fixed && !percentage && !residual) {
      throw new IllegalArgumentException(
          "Line metadata must match FIXED, PERCENTAGE or RESIDUAL shape");
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown salary-structure line field: " + property);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
