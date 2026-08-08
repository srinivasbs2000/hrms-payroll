package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRejectionRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotBlank @Size(max = 240) String evidenceRef,
    @NotBlank @Size(max = 240) String authorityReference) {}
