package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayGroupAssignmentImpactView(
    UUID payPeriodId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    String reasonCode) {}
