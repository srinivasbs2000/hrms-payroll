package com.acme.hrms.payroll.statutory.internal.application;

import com.acme.hrms.payroll.organisation.OrganisationApprovalScopeFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityRequirement;
import com.acme.hrms.payroll.security.ApprovalOwnerKind;
import com.acme.hrms.payroll.security.ApprovalRole;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
final class StatutoryApprovalAuthorityGate {
  private final ApprovalAuthorityFacade authority;
  private final OrganisationApprovalScopeFacade scopes;
  private final Clock clock;

  StatutoryApprovalAuthorityGate(
      ApprovalAuthorityFacade authority,
      OrganisationApprovalScopeFacade scopes,
      Clock clock) {
    this.authority = authority;
    this.scopes = scopes;
    this.clock = clock;
  }

  void requireRegistrationTransition(
      RegistrationOwnerKind ownerKind,
      UUID ownerId,
      LocalDate effectiveFrom,
      String operationSuffix) {
    ApprovalRole role;
    String action;
    switch (operationSuffix) {
      case "verify" -> {
        role = ApprovalRole.VERIFIER;
        action = "VERIFY";
      }
      case "approval-request" -> {
        role = ApprovalRole.VERIFIER;
        action = "REQUEST_APPROVAL";
      }
      case "approve" -> {
        role = ApprovalRole.FINAL_APPROVER;
        action = "APPROVE";
      }
      case "reject" -> {
        role = ApprovalRole.FINAL_APPROVER;
        action = "REJECT";
      }
      case "suspend" -> {
        role = ApprovalRole.FINAL_APPROVER;
        action = "SUSPEND";
      }
      default -> {
        return;
      }
    }

    ApprovalOwnerKind resolvedKind;
    UUID resolvedOwner;
    switch (ownerKind) {
      case LEGAL_ENTITY -> {
        resolvedKind = ApprovalOwnerKind.LEGAL_ENTITY;
        resolvedOwner = ownerId;
      }
      case PAYROLL_STATUTORY_UNIT -> {
        resolvedKind = ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT;
        resolvedOwner = ownerId;
      }
      case ESTABLISHMENT -> {
        resolvedKind = ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT;
        resolvedOwner =
            scopes.findForEstablishmentIdentity(ownerId, effectiveFrom)
                .orElseThrow(
                    () ->
                        new AccessDeniedException(
                            "No parent PSU approval scope exists for the registration establishment"))
                .payrollStatutoryUnitId();
      }
      default -> throw new AccessDeniedException("Unsupported registration owner kind");
    }

    if (resolvedOwner == null) {
      throw new AccessDeniedException("Registration owner is required for application approval authority");
    }
    authority.requireAuthority(
        new ApprovalAuthorityRequirement(
            resolvedKind,
            resolvedOwner,
            role,
            "STATUTORY_REGISTRATION",
            action,
            LocalDate.now(clock)));
  }
}
