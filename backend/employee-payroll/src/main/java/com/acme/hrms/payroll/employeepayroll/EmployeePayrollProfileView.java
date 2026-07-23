package com.acme.hrms.payroll.employeepayroll;

import java.util.UUID;

public record EmployeePayrollProfileView(
    UUID id,
    UUID payrollRelationshipId,
    String employeeNumber,
    String currency,
    String payrollStatus,
    long versionNo) {}
