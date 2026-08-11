package com.acme.hrms.payroll.statutory.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.hrms.payroll.organisation.OrganisationApprovalScopeFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityRequirement;
import com.acme.hrms.payroll.security.ApprovalOwnerKind;
import com.acme.hrms.payroll.security.ApprovalRole;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StatutoryApprovalAuthorityGateTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void mapsDirectAndEstablishmentRegistrationScopes() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    StatutoryApprovalAuthorityGate gate =
        new StatutoryApprovalAuthorityGate(authority, scopes, CLOCK);
    UUID legalEntity = UUID.randomUUID();
    UUID establishment = UUID.randomUUID();
    UUID psu = UUID.randomUUID();
    LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);

    gate.requireRegistrationTransition(
        RegistrationOwnerKind.LEGAL_ENTITY,
        legalEntity,
        effectiveFrom,
        "verify");

    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.LEGAL_ENTITY,
                legalEntity,
                ApprovalRole.VERIFIER,
                "STATUTORY_REGISTRATION",
                "VERIFY",
                LocalDate.of(2026, 8, 11)));

    when(scopes.findForEstablishmentIdentity(establishment, effectiveFrom))
        .thenReturn(
            Optional.of(
                new OrganisationApprovalScopeFacade.ApprovalScope(psu)));

    gate.requireRegistrationTransition(
        RegistrationOwnerKind.ESTABLISHMENT,
        establishment,
        effectiveFrom,
        "approve");

    verify(authority)
        .requireAuthority(
            new ApprovalAuthorityRequirement(
                ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
                psu,
                ApprovalRole.FINAL_APPROVER,
                "STATUTORY_REGISTRATION",
                "APPROVE",
                LocalDate.of(2026, 8, 11)));
  }

  @Test
  void missingEstablishmentScopeIsDenied() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    StatutoryApprovalAuthorityGate gate =
        new StatutoryApprovalAuthorityGate(authority, scopes, CLOCK);
    UUID establishment = UUID.randomUUID();
    LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);
    when(scopes.findForEstablishmentIdentity(establishment, effectiveFrom))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                gate.requireRegistrationTransition(
                    RegistrationOwnerKind.ESTABLISHMENT,
                    establishment,
                    effectiveFrom,
                    "approve"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("No parent PSU approval scope");
  }

  @Test
  void sharedFacadeDenialPropagates() {
    ApprovalAuthorityFacade authority = mock(ApprovalAuthorityFacade.class);
    OrganisationApprovalScopeFacade scopes = mock(OrganisationApprovalScopeFacade.class);
    StatutoryApprovalAuthorityGate gate =
        new StatutoryApprovalAuthorityGate(authority, scopes, CLOCK);
    doThrow(new AccessDeniedException("denied"))
        .when(authority)
        .requireAuthority(any(ApprovalAuthorityRequirement.class));

    assertThatThrownBy(
            () ->
                gate.requireRegistrationTransition(
                    RegistrationOwnerKind.PAYROLL_STATUTORY_UNIT,
                    UUID.randomUUID(),
                    LocalDate.of(2026, 1, 1),
                    "suspend"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("denied");
  }
}
