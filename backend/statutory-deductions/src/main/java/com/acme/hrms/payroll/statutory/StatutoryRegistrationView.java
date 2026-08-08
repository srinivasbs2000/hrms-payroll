package com.acme.hrms.payroll.statutory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatutoryRegistrationView(
    UUID identityId,
    UUID registrationTypeId,
    String referenceCode,
    long identityVersionNo,
    UUID versionId,
    int versionSequence,
    long versionNo,
    UUID registrationTypeVersionId,
    String identifier,
    String identifierNormalized,
    RegistrationOwnerKind ownerKind,
    UUID ownerId,
    UUID payrollJurisdictionId,
    UUID payrollJurisdictionVersionId,
    UUID parentRegistrationId,
    UUID parentRegistrationVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String lifecycleStatus,
    String verificationEvidenceRef,
    Instant verifiedAt,
    String verifiedBy,
    Instant approvedAt,
    String approvedBy,
    String approvalEvidenceRef,
    Instant rejectedAt,
    String rejectedBy,
    String rejectionReason,
    String rejectionEvidenceRef,
    String authorityReference,
    Instant suspendedAt,
    String suspendedBy,
    String suspensionReason,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy) {}
