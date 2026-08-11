package com.acme.hrms.payroll.security;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record ApprovalDelegationView(
    UUID id, UUID sourceAuthorityId, String delegatorActorId, String delegateActorId,
    LocalDate effectiveFrom, LocalDate effectiveTo, String status,
    Instant createdAt, String createdBy,
    Instant revokedAt, String revokedBy, String revocationReason, long versionNo) {}
