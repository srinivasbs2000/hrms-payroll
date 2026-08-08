package com.acme.hrms.payroll.organisation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollJurisdictionView(
    UUID identityId,
    String code,
    String identityStatus,
    long identityVersionNo,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String countryCode,
    String levelCode,
    int levelRank,
    UUID parentJurisdictionId,
    UUID parentJurisdictionVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy,
    Instant approvedAt,
    String approvedBy) {}
