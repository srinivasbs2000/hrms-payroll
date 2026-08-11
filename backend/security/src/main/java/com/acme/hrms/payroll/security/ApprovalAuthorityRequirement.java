package com.acme.hrms.payroll.security;
import java.time.LocalDate;
import java.util.UUID;
public record ApprovalAuthorityRequirement(
    ApprovalOwnerKind ownerKind, UUID ownerId, ApprovalRole approvalRole,
    String domainCode, String actionCode, LocalDate decisionDate) {
  public void validate() {
    if (ownerKind == null || ownerId == null || approvalRole == null) {
      throw new IllegalArgumentException("ownerKind, ownerId and approvalRole are required");
    }
    ApprovalAuthorityAssignmentCreateRequest.validateCode("domainCode", domainCode);
    ApprovalAuthorityAssignmentCreateRequest.validateCode("actionCode", actionCode);
    if (decisionDate == null) throw new IllegalArgumentException("decisionDate is required");
  }
}
