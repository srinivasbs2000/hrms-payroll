package com.acme.hrms.payroll.payrolloperations;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PayrollCycleCreateRequest(
    @NotNull UUID payGroupVersionId,
    @NotNull UUID payPeriodId) {}
