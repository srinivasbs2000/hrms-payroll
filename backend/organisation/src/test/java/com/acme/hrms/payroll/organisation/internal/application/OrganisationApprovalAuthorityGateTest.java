package com.acme.hrms.payroll.organisation.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.hrms.payroll.organisation.OrganisationApprovalScopeFacade;
import com.acme.hrms.payroll.organisation.OrganisationKind;
import com.acme.hrms.payroll.security.ApprovalAuthorityFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityRequirement;
import com.acme.hrms.payroll.security.ApprovalOwnerKind;
import com.acme.hrms.payroll.security.ApprovalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OrganisationApprovalAuthorityGateTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void mapsDirectAndEstablishmentOrganisationApprovalScopes() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    OrganisationApprovalAuthorityGate gate =
        new OrganisationApprovalAuthorityGate(authority, scopes, CLOCK);
    UUID legalEntity = UUID.randomUUID();
    UUID establishment = UUID.randomUUID();
    UUID establishmentVersion = UUID.randomUUID();
    UUID psu = UUID.randomUUID();

    gate.requireOrganisationApproval(
        OrganisationKind.LEGAL_ENTITY, legalEntity, UUID.randomUUID());

    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.LEGAL_ENTITY,
                legalEntity,
                ApprovalRole.FINAL_APPROVER,
                "ORGANISATION_CONFIG",
                "APPROVE",
                java.time.LocalDate.of(2026, 8, 11)));

    when(scopes.findForEstablishmentVersion(establishmentVersion))
        .thenReturn(
            Optional.of(
                new OrganisationApprovalScopeFacade.ApprovalScope(psu)));

    gate.requireOrganisationApproval(
        OrganisationKind.ESTABLISHMENT, establishment, establishmentVersion);

    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
                psu,
                ApprovalRole.FINAL_APPROVER,
                "ORGANISATION_CONFIG",
                "APPROVE",
                java.time.LocalDate.of(2026, 8, 11)));
  }

  @Test
  void ownerlessWorkLocationRemainsOutsideFad() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    OrganisationApprovalAuthorityGate gate =
        new OrganisationApprovalAuthorityGate(authority, scopes, CLOCK);
    UUID workLocationVersion = UUID.randomUUID();
    when(scopes.findForWorkLocationVersion(workLocationVersion))
        .thenReturn(Optional.empty());

    gate.requireWorkLocationApproval(workLocationVersion);

    verifyNoInteractions(authority);
  }

  @Test
  void mapsBankVerifierAndSignatoryFinalApproverFromPersistedOwners() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    OrganisationApprovalAuthorityGate gate =
        new OrganisationApprovalAuthorityGate(authority, scopes, CLOCK);
    UUID legalEntity = UUID.randomUUID();
    UUID psu = UUID.randomUUID();

    gate.requireBankTransition(
        "LEGAL_ENTITY", legalEntity, null, "VERIFIED");
    gate.requireSignatoryApproval(
        "PAYROLL_STATUTORY_UNIT", null, psu);

    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.LEGAL_ENTITY,
                legalEntity,
                ApprovalRole.VERIFIER,
                "EMPLOYER_BANK_ACCOUNT",
                "VERIFY",
                java.time.LocalDate.of(2026, 8, 11)));
    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
                psu,
                ApprovalRole.FINAL_APPROVER,
                "AUTHORISED_SIGNATORY",
                "APPROVE",
                java.time.LocalDate.of(2026, 8, 11)));
  }

  @Test
  void denialFromSharedFacadePropagates() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    OrganisationApprovalAuthorityGate gate =
        new OrganisationApprovalAuthorityGate(authority, scopes, CLOCK);
    doThrow(new AccessDeniedException("denied"))
        .when(authority)
        .requireAuthority(any(ApprovalAuthorityRequirement.class));

    assertThatThrownBy(
            () ->
                gate.requireOrganisationApproval(
                    OrganisationKind.LEGAL_ENTITY,
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("denied");
  }
}
