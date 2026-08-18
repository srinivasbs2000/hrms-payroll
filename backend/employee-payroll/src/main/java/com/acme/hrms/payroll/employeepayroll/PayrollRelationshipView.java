package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollRelationshipView(
    UUID identityId,
    String externalEmployeeId,
    String employeeNumber,
    String identityStatus,
    UUID versionId,
    int versionSequence,
    long versionNo,
    UUID legalEntityVersionId,
    UUID payrollStatutoryUnitVersionId,
    String aggregationBoundaryKey,
    String countryCode,
    String employerCurrency,
    LocalDate relationshipStart,
    LocalDate relationshipEnd,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {

  public PayrollRelationshipView(
      UUID identityId, String externalEmployeeId, String employeeNumber,
      String identityStatus, UUID versionId, int versionSequence, long versionNo,
      UUID legalEntityVersionId, LocalDate relationshipStart,
      LocalDate relationshipEnd, String approvalStatus,
      UUID supersedesVersionId, boolean superseded) {
    this(identityId, externalEmployeeId, employeeNumber, identityStatus, versionId,
        versionSequence, versionNo, legalEntityVersionId, null, null, null, null,
        relationshipStart, relationshipEnd, approvalStatus, supersedesVersionId,
        superseded);
  }
}
