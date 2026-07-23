package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record EmployeePayrollProfileWriteRequest(
    @NotNull UUID payrollRelationshipId,
    @Pattern(regexp = "^[A-Z]{3}$") String currency) {

  public void validate() {
    if (currency != null && !"INR".equals(currency)) {
      throw new IllegalArgumentException(
          "Only INR currency is supported for employee payroll profiles");
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }
}
