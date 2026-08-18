package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record EmployeeComponentOverrideWriteRequest(
    @NotNull UUID payrollAssignmentVersionId,
    @NotNull UUID salaryAssignmentId,
    @NotNull UUID salaryStructureLineId,
    @NotNull UUID componentVersionId,
    @NotBlank String overrideKind,
    @NotNull BigDecimal overrideValue,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {
  private static final Set<String> KINDS = Set.of("AMOUNT", "PERCENTAGE");

  public void validate() {
    if (!KINDS.contains(overrideKind)) {
      throw new IllegalArgumentException("overrideKind must be AMOUNT or PERCENTAGE");
    }
    if (overrideValue == null || overrideValue.signum() < 0) {
      throw new IllegalArgumentException("overrideValue must not be negative");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
}
