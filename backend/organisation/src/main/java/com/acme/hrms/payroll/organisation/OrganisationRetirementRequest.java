package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OrganisationRetirementRequest(
    @NotNull LocalDate effectiveDate,
    @NotBlank @Size(max = 500) String reason) {}
