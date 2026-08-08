package com.acme.hrms.payroll.organisation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkLocationView(
    UUID identityId,
    String code,
    String identityStatus,
    long identityVersionNo,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    UUID establishmentVersionId,
    UUID payrollJurisdictionId,
    UUID payrollJurisdictionVersionId,
    String addressLine1,
    String addressLine2,
    String locality,
    String stateCode,
    String postalCode,
    String countryCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy,
    Instant approvedAt,
    String approvedBy) {}
