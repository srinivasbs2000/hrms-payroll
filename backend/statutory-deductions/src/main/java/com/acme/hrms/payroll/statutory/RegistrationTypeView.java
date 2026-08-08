package com.acme.hrms.payroll.statutory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrationTypeView(
    UUID identityId,
    String code,
    String identityStatus,
    long identityVersionNo,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String obligationCode,
    String authorityCode,
    String jurisdictionLevelCode,
    String identifierPattern,
    String identifierPatternDialect,
    String identifierCasePolicy,
    boolean parentRequired,
    UUID parentRegistrationTypeId,
    List<RegistrationOwnerKind> ownerKinds,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy,
    Instant approvedAt,
    String approvedBy) {}
