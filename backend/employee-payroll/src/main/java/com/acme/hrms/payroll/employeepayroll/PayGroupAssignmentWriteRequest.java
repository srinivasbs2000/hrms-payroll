package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record PayGroupAssignmentWriteRequest(
    @NotNull UUID payrollAssignmentVersionId,
    @NotNull UUID payGroupVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }
}
