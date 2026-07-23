package com.acme.hrms.payroll.employeepayroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryAssignmentView(
    UUID id,
    UUID payrollAssignmentVersionId,
    UUID salaryStructureVersionId,
    BigDecimal monthlyAmount,
    String currency,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesAssignmentId,
    boolean superseded,
    long versionNo) {}
