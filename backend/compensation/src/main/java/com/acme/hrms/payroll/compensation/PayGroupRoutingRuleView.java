package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayGroupRoutingRuleView(
    UUID id,
    UUID payGroupVersionId,
    UUID payrollStatutoryUnitVersionId,
    UUID establishmentVersionId,
    int priority,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String status,
    long versionNo) {}
