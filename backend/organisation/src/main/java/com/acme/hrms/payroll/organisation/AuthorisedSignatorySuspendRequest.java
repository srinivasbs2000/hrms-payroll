package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorisedSignatorySuspendRequest(
    @NotBlank @Size(max = 500) String reason) {}
