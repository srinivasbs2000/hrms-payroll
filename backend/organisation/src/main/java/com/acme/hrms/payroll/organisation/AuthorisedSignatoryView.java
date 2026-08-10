package com.acme.hrms.payroll.organisation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AuthorisedSignatoryView(
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
    String fullName,
    String designation,
    String authorityReference,
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
    String createdBy,
    List<ScopeView> scopes) {

  public AuthorisedSignatoryView {
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }

  public record ScopeView(
      UUID scopeId,
      String purposeCode,
      String currencyCode,
      BigDecimal maximumAmount) {}
}
