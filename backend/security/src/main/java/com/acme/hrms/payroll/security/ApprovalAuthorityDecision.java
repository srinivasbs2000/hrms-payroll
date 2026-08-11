package com.acme.hrms.payroll.security;
import java.time.LocalDate;
import java.util.UUID;
public record ApprovalAuthorityDecision(
    UUID authorityId, UUID delegationId,
    ApprovalOwnerKind ownerKind, UUID ownerId, ApprovalRole approvalRole,
    String domainCode, String actionCode, LocalDate decisionDate,
    String sourceActorId, String effectiveActorId) {
  public boolean delegated() { return delegationId != null; }
}
