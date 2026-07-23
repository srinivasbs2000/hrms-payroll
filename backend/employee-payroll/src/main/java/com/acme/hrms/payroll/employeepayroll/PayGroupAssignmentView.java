package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayGroupAssignmentView(
    UUID id,
    UUID payrollAssignmentVersionId,
    UUID payGroupVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesAssignmentId,
    boolean superseded,
    long versionNo) {}
