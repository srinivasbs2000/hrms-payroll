package com.acme.hrms.payroll.statutory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrationReadinessView(
    UUID registrationTypeId,
    RegistrationOwnerKind ownerKind,
    UUID ownerId,
    UUID payrollJurisdictionId,
    LocalDate asOf,
    boolean ready,
    UUID registrationVersionId,
    List<RegistrationReadinessFindingView> findings) {}
