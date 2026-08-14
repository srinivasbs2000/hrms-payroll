package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record PayGroupRoutingRuleWriteRequest(
    @NotNull UUID payGroupVersionId,
    @NotNull UUID payrollStatutoryUnitVersionId,
    UUID establishmentVersionId,
    @Positive Integer priority,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if (payGroupVersionId == null || payrollStatutoryUnitVersionId == null) {
      throw new IllegalArgumentException(
          "payGroupVersionId and payrollStatutoryUnitVersionId are required");
    }
    if (priority != null && priority <= 0) {
      throw new IllegalArgumentException("priority must be greater than zero");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }

  public int resolvedPriority() {
    return priority == null ? 100 : priority;
  }
}
