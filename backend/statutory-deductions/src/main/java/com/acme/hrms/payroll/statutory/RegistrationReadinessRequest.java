package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RegistrationReadinessRequest(
    @NotNull UUID registrationTypeId,
    @NotNull RegistrationOwnerKind ownerKind,
    @NotNull UUID ownerId,
    @NotNull UUID payrollJurisdictionId,
    @NotNull LocalDate asOf,
    @Min(0) @Max(365) int warningHorizonDays) {}
