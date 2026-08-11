package com.acme.hrms.payroll.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalAuthorityRequestTest {
  @Test
  void finalApproverCannotBeServiceIdentity() {
    var request = new ApprovalAuthorityAssignmentCreateRequest(
        ApprovalOwnerKind.LEGAL_ENTITY, UUID.randomUUID(), ApprovalRole.FINAL_APPROVER,
        "ORGANISATION_CONFIG", "APPROVE", "service:batch",
        LocalDate.of(2026, 1, 1), null);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("service identities");
  }

  @Test
  void delegationRequiresExplicitBoundedPeriod() {
    var request = new ApprovalDelegationCreateRequest(
        UUID.randomUUID(), "issuer|delegate",
        LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2));
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("after effectiveFrom");
  }

  @Test
  void requirementRejectsUnboundedCodeVocabulary() {
    var requirement = new ApprovalAuthorityRequirement(
        ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT, UUID.randomUUID(), ApprovalRole.VERIFIER,
        "bad-domain", "VERIFY", LocalDate.of(2026, 1, 2));
    assertThatThrownBy(requirement::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("domainCode");
  }
}
