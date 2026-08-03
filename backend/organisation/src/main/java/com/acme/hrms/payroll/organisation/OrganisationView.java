package com.acme.hrms.payroll.organisation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrganisationView(
    OrganisationKind kind,
    UUID identityId,
    String code,
    String identityStatus,
    long identityVersionNo,
    LocalDate retirementEffectiveDate,
    String retirementReason,
    Instant retiredAt,
    String retiredBy,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String countryCode,
    String currency,
    String stateCode,
    UUID parentVersionId,
    String responsibilityScope,
    String establishmentType,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy,
    String approvedBy) {}
