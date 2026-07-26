package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record StatutoryCorrectionCommand(
    @NotNull UUID statutoryResultId,
    @NotNull @DecimalString BigDecimal employeeAmountDelta,
    @NotNull @DecimalString BigDecimal employerAmountDelta,
    @NotBlank @Size(min = 8, max = 500) String reason) {}
