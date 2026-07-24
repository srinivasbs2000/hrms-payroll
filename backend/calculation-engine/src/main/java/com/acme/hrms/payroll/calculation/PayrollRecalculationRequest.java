package com.acme.hrms.payroll.calculation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayrollRecalculationRequest(
    @NotBlank
    @Size(min = 8, max = 500)
    String reason) {}
