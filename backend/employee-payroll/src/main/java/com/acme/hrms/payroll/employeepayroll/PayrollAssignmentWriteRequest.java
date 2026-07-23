package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollAssignmentWriteRequest(
    UUID payrollRelationshipId,
    @Size(max = 60) String assignmentNumber,
    @NotNull UUID payrollRelationshipVersionId,
    @NotNull UUID establishmentVersionId,
    @NotNull LocalDate assignmentStart,
    LocalDate assignmentEnd) {

  public void validate(boolean identityCreation) {
    if (identityCreation && payrollRelationshipId == null) {
      throw new IllegalArgumentException(
          "payrollRelationshipId is required when creating an assignment");
    }
    if (identityCreation
        && (assignmentNumber == null || assignmentNumber.isBlank())) {
      throw new IllegalArgumentException(
          "assignmentNumber is required when creating an assignment");
    }
    if (assignmentEnd != null && !assignmentEnd.isAfter(assignmentStart)) {
      throw new IllegalArgumentException(
          "assignmentEnd must be after assignmentStart");
    }
  }
}
