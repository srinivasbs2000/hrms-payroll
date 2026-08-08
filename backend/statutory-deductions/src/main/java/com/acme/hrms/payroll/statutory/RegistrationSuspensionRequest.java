package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationSuspensionRequest(
    @NotBlank @Size(max = 500) String reason) {}
