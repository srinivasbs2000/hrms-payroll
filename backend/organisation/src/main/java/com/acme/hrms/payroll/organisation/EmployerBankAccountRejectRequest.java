package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployerBankAccountRejectRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotBlank @Size(max = 240) String evidenceRef) {}
