package com.acme.hrms.payroll.compensation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollBaseView(
    UUID identityId,
    String code,
    String name,
    String lifecycleStatus,
    String ownershipScope,
    String countryCode,
    boolean protectedFlag,
    String confidentialityLevel,
    long identityVersionNo,
    LocalDate retirementEffectiveDate,
    String retirementReason,
    Instant retiredAt,
    String retiredBy,
    UUID versionId,
    int versionSequence,
    long versionNo,
    int catalogueSchemaVersion,
    String baseCategory,
    String aggregationMethod,
    String description,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}
