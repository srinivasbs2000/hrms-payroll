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
    LocalDate relationshipStart,
    LocalDate relationshipEnd,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}
