package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollAssignmentView(
    UUID identityId,
    UUID payrollRelationshipId,
    String assignmentNumber,
    String identityStatus,
    UUID versionId,
    int versionSequence,
    long versionNo,
    UUID payrollRelationshipVersionId,
    UUID establishmentVersionId,
    LocalDate assignmentStart,
    LocalDate assignmentEnd,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}
