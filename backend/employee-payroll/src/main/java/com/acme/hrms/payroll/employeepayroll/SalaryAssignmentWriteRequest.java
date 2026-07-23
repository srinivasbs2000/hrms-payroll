package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryAssignmentWriteRequest(
    @NotNull UUID payrollAssignmentVersionId,
    @NotNull UUID salaryStructureVersionId,
    @NotNull @DecimalMin("0.00") BigDecimal monthlyAmount,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if (monthlyAmount.signum() < 0) {
      throw new IllegalArgumentException(
          "monthlyAmount must be zero or greater");
    }
    if (currency != null && !"INR".equals(currency)) {
      throw new IllegalArgumentException(
          "Only INR currency is supported for salary assignments");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }
}
