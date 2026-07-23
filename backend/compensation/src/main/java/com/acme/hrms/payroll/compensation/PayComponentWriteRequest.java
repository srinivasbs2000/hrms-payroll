package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record PayComponentWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @Size(max = 160) String name,
    @Pattern(regexp = "^(EARNING|DEDUCTION|INFORMATION)$") String componentType,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$")
        String formulaType,
    @Size(max = 1000) String formulaExpression,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal fixedAmount,
    @Min(0) @Max(4) Integer roundingScale,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> COMPONENT_TYPES =
      Set.of("EARNING", "DEDUCTION", "INFORMATION");

  private static final Set<String> FORMULA_TYPES =
      Set.of("FIXED", "PERCENTAGE_OF_COMPONENT", "RESIDUAL");

  public void validate(boolean identityCreation) {
    if (identityCreation) {
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException(
            "code is required when creating a pay-component identity");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException(
            "name is required when creating a pay-component identity");
      }
      if (componentType == null || componentType.isBlank()) {
        throw new IllegalArgumentException(
            "componentType is required when creating a pay-component identity");
      }
    }

    if (componentType != null
        && !componentType.isBlank()
        && !COMPONENT_TYPES.contains(componentType)) {
      throw new IllegalArgumentException(
          "componentType must be EARNING, DEDUCTION or INFORMATION");
    }

    if (formulaType == null
        || formulaType.isBlank()
        || !FORMULA_TYPES.contains(formulaType)) {
      throw new IllegalArgumentException(
          "formulaType must be FIXED, PERCENTAGE_OF_COMPONENT or RESIDUAL");
    }

    if ("FIXED".equals(formulaType)) {
      if (fixedAmount == null || fixedAmount.signum() < 0) {
        throw new IllegalArgumentException(
            "FIXED formulaType requires a non-negative fixedAmount");
      }
      if (formulaExpression != null && !formulaExpression.isBlank()) {
        throw new IllegalArgumentException(
            "FIXED formulaType must not contain formulaExpression");
      }
    } else {
      if (fixedAmount != null) {
        throw new IllegalArgumentException(
            "Non-FIXED formulaType must not contain fixedAmount");
      }
      if (formulaExpression == null || formulaExpression.isBlank()) {
        throw new IllegalArgumentException(
            "Non-FIXED formulaType requires formulaExpression");
      }
    }

    if (roundingScale != null
        && (roundingScale < 0 || roundingScale > 4)) {
      throw new IllegalArgumentException(
          "roundingScale must be between 0 and 4");
    }

    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }

  public int resolvedRoundingScale() {
    return roundingScale == null ? 2 : roundingScale;
  }
}