package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public record SalaryStructureLineWriteRequest(
    @NotNull UUID componentVersionId,
    @NotNull @Min(1) Integer sequenceNo,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal targetAmount,
    @DecimalMin(value = "0.000000", inclusive = false)
        @DecimalMax("100.000000")
        @Digits(integer = 3, fraction = 6)
        BigDecimal targetPercentage,
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$")
        String percentageBaseCode) {

  public void validate() {
    boolean fixed = targetAmount != null
        && targetPercentage == null
        && blank(percentageBaseCode);

    boolean percentage = targetAmount == null
        && targetPercentage != null
        && targetPercentage.signum() > 0
        && targetPercentage.compareTo(
            new BigDecimal("100.000000")) <= 0
        && !blank(percentageBaseCode);

    boolean residual = targetAmount == null
        && targetPercentage == null
        && blank(percentageBaseCode);

    if (!fixed && !percentage && !residual) {
      throw new IllegalArgumentException(
          "Each salary-structure line must be fixed, "
              + "percentage-based or residual");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}