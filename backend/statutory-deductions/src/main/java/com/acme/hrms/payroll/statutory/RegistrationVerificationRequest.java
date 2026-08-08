package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationVerificationRequest(
    @NotBlank @Size(max = 240) String evidenceRef) {}
