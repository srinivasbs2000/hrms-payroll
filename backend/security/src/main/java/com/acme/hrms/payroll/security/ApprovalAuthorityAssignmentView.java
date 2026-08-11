package com.acme.hrms.payroll.security;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record ApprovalAuthorityAssignmentView(
    UUID id, ApprovalOwnerKind ownerKind, UUID ownerId, ApprovalRole approvalRole,
    String domainCode, String actionCode, String actorId,
    LocalDate effectiveFrom, LocalDate effectiveTo, String status,
    Instant createdAt, String createdBy,
    Instant suspendedAt, String suspendedBy, String suspensionReason,
    Instant retiredAt, String retiredBy, String retirementReason, long versionNo) {}
