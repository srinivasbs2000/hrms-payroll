package com.acme.hrms.payroll.statutory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegistrationTypeCreateRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String code,
    @NotNull @Valid RegistrationTypeVersionWriteRequest version) {}
