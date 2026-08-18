package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollRelationshipWriteRequest(
    String externalEmployeeId,
    String employeeNumber,
    @NotNull UUID legalEntityVersionId,
    UUID payrollStatutoryUnitVersionId,
    String aggregationBoundaryKey,
    @NotNull LocalDate relationshipStart,
    LocalDate relationshipEnd) {

  public PayrollRelationshipWriteRequest(
      String externalEmployeeId,
      String employeeNumber,
      UUID legalEntityVersionId,
      LocalDate relationshipStart,
      LocalDate relationshipEnd) {
    this(
        externalEmployeeId,
        employeeNumber,
        legalEntityVersionId,
        null,
        null,
        relationshipStart,
        relationshipEnd);
  }

  public void validate(boolean creatingIdentity) {
    if (creatingIdentity) {
      validateForCreate();
    } else {
      validateVersion();
    }
  }

  public void validateForCreate() {
    if (externalEmployeeId == null || externalEmployeeId.isBlank()) {
      throw new IllegalArgumentException("externalEmployeeId is required");
    }
    if (employeeNumber == null || employeeNumber.isBlank()) {
      throw new IllegalArgumentException("employeeNumber is required");
    }
    validateVersion();
  }

  public void validateVersion() {
    if (legalEntityVersionId == null) {
      throw new IllegalArgumentException("legalEntityVersionId is required");
    }
    if (relationshipStart == null) {
      throw new IllegalArgumentException("relationshipStart is required");
    }
    if (relationshipEnd != null
        && !relationshipEnd.isAfter(relationshipStart)) {
      throw new IllegalArgumentException(
          "relationshipEnd must be after relationshipStart");
    }
    if ((payrollStatutoryUnitVersionId == null)
        != (aggregationBoundaryKey == null || aggregationBoundaryKey.isBlank())) {
      throw new IllegalArgumentException(
          "payrollStatutoryUnitVersionId and aggregationBoundaryKey must be supplied together");
    }
  }

  public boolean completeBoundary() {
    return payrollStatutoryUnitVersionId != null
        && aggregationBoundaryKey != null
        && !aggregationBoundaryKey.isBlank();
  }
}
