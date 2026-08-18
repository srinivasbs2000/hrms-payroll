package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record PayGroupAssignmentWriteRequest(
    @NotNull UUID payrollAssignmentVersionId,
    @NotNull UUID payGroupVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    LocalDate impactAssessmentThrough) {

  public PayGroupAssignmentWriteRequest(
      UUID payrollAssignmentVersionId,
      UUID payGroupVersionId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    this(
        payrollAssignmentVersionId,
        payGroupVersionId,
        effectiveFrom,
        effectiveTo,
        null);
  }

  public void validate() {
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    if (impactAssessmentThrough != null
        && impactAssessmentThrough.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException(
          "impactAssessmentThrough must not precede effectiveFrom");
    }
  }

  public boolean completeImpactContract() {
    return impactAssessmentThrough != null;
  }
}
