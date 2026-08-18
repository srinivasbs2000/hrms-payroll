package com.acme.hrms.payroll.employeepayroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeComponentOverrideView(
    UUID id,
    UUID payrollAssignmentVersionId,
    UUID salaryAssignmentId,
    UUID salaryStructureLineId,
    UUID componentVersionId,
    String overrideKind,
    BigDecimal overrideValue,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesOverrideId,
    boolean superseded,
    long versionNo) {}
