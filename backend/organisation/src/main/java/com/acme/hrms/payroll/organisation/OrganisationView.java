package com.acme.hrms.payroll.organisation;

import java.time.LocalDate;
import java.util.UUID;

public record OrganisationView(
    OrganisationKind kind,
    UUID identityId,
    String code,
    String identityStatus,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String countryCode,
    String currency,
    String stateCode,
    UUID parentVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}
