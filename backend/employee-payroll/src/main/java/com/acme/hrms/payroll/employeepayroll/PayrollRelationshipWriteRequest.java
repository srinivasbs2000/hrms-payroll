package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollRelationshipWriteRequest(
    @Size(max = 100) String externalEmployeeId,
    @Size(max = 60) String employeeNumber,
    @NotNull UUID legalEntityVersionId,
    @NotNull LocalDate relationshipStart,
    LocalDate relationshipEnd) {

  public void validate(boolean identityCreation) {
    if (identityCreation
        && (externalEmployeeId == null || externalEmployeeId.isBlank())) {
      throw new IllegalArgumentException(
          "externalEmployeeId is required when creating a relationship");
    }
    if (identityCreation
        && (employeeNumber == null || employeeNumber.isBlank())) {
      throw new IllegalArgumentException(
          "employeeNumber is required when creating a relationship");
    }
    if (relationshipEnd != null
        && !relationshipEnd.isAfter(relationshipStart)) {
      throw new IllegalArgumentException(
          "relationshipEnd must be after relationshipStart");
    }
  }
}
