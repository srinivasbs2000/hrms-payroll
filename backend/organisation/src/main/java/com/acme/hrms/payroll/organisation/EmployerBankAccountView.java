package com.acme.hrms.payroll.organisation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployerBankAccountView(
    UUID identityId,
    String code,
    String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    String identityStatus,
    long identityVersionNo,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String bankName,
    String branchName,
    String routingCode,
    String accountHolderName,
    String currencyCode,
    String maskedAccountNumber,
    boolean defaultAccount,
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
    Instant suspendedAt,
    String suspendedBy,
    String suspensionReason,
    UUID supersedesVersionId,
    boolean superseded,
    String createdBy) {}
