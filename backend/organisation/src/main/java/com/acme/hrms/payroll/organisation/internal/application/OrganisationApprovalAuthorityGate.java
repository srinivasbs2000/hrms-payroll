package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.organisation.OrganisationApprovalScopeFacade;
import com.acme.hrms.payroll.organisation.OrganisationKind;
import com.acme.hrms.payroll.security.ApprovalAuthorityFacade;
import com.acme.hrms.payroll.security.ApprovalAuthorityRequirement;
import com.acme.hrms.payroll.security.ApprovalOwnerKind;
import com.acme.hrms.payroll.security.ApprovalRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
final class OrganisationApprovalAuthorityGate {
  private final ApprovalAuthorityFacade authority;
  private final OrganisationApprovalScopeFacade scopes;
  private final Clock clock;

  OrganisationApprovalAuthorityGate(
      ApprovalAuthorityFacade authority,
      OrganisationApprovalScopeFacade scopes,
      Clock clock) {
    this.authority = authority;
    this.scopes = scopes;
    this.clock = clock;
  }

  void requireOrganisationApproval(
      OrganisationKind kind, UUID identityId, UUID versionId) {
    switch (kind) {
      case LEGAL_ENTITY ->
          require(
              ApprovalOwnerKind.LEGAL_ENTITY,
              identityId,
              ApprovalRole.FINAL_APPROVER,
              "ORGANISATION_CONFIG",
              "APPROVE");
      case PAYROLL_STATUTORY_UNIT ->
          require(
              ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
              identityId,
              ApprovalRole.FINAL_APPROVER,
              "ORGANISATION_CONFIG",
              "APPROVE");
      case ESTABLISHMENT ->
          requirePsu(
              requireScope(
                  scopes.findForEstablishmentVersion(versionId),
                  "No parent PSU approval scope exists for the establishment"),
              ApprovalRole.FINAL_APPROVER,
              "ORGANISATION_CONFIG",
              "APPROVE");
    }
  }

  void requireWorkLocationApproval(UUID workLocationVersionId) {
    scopes.findForWorkLocationVersion(workLocationVersionId)
        .ifPresent(
            scope ->
                requirePsu(
                    scope,
                    ApprovalRole.FINAL_APPROVER,
                    "WORK_LOCATION",
                    "APPROVE"));
  }

  void requireJurisdictionOverrideApproval(
      UUID establishmentVersionId, UUID workLocationVersionId) {
    Optional<OrganisationApprovalScopeFacade.ApprovalScope> scope =
        establishmentVersionId != null
            ? scopes.findForEstablishmentVersion(establishmentVersionId)
            : scopes.findForWorkLocationVersion(workLocationVersionId);
    scope.ifPresent(
        value ->
            requirePsu(
                value,
                ApprovalRole.FINAL_APPROVER,
                "JURISDICTION_OVERRIDE",
                "APPROVE"));
  }

  void requireBankTransition(
      String ownerKind,
      UUID legalEntityId,
      UUID payrollStatutoryUnitId,
      String lifecycleAction) {
    switch (lifecycleAction) {
      case "VERIFIED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.VERIFIER,
              "EMPLOYER_BANK_ACCOUNT",
              "VERIFY");
      case "APPROVAL_REQUESTED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.VERIFIER,
              "EMPLOYER_BANK_ACCOUNT",
              "REQUEST_APPROVAL");
      case "REJECTED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.FINAL_APPROVER,
              "EMPLOYER_BANK_ACCOUNT",
              "REJECT");
      case "SUSPENDED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.FINAL_APPROVER,
              "EMPLOYER_BANK_ACCOUNT",
              "SUSPEND");
      default -> {
        // Submission and non-approval lifecycle actions remain domain-only.
      }
    }
  }

  void requireBankApproval(
      String ownerKind, UUID legalEntityId, UUID payrollStatutoryUnitId) {
    requireOwned(
        ownerKind,
        legalEntityId,
        payrollStatutoryUnitId,
        ApprovalRole.FINAL_APPROVER,
        "EMPLOYER_BANK_ACCOUNT",
        "APPROVE");
  }

  void requireSignatoryTransition(
      String ownerKind,
      UUID legalEntityId,
      UUID payrollStatutoryUnitId,
      String lifecycleAction) {
    switch (lifecycleAction) {
      case "VERIFIED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.VERIFIER,
              "AUTHORISED_SIGNATORY",
              "VERIFY");
      case "APPROVAL_REQUESTED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.VERIFIER,
              "AUTHORISED_SIGNATORY",
              "REQUEST_APPROVAL");
      case "REJECTED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.FINAL_APPROVER,
              "AUTHORISED_SIGNATORY",
              "REJECT");
      case "SUSPENDED" ->
          requireOwned(
              ownerKind,
              legalEntityId,
              payrollStatutoryUnitId,
              ApprovalRole.FINAL_APPROVER,
              "AUTHORISED_SIGNATORY",
              "SUSPEND");
      default -> {
        // Submission and non-approval lifecycle actions remain domain-only.
      }
    }
  }

  void requireSignatoryApproval(
      String ownerKind, UUID legalEntityId, UUID payrollStatutoryUnitId) {
    requireOwned(
        ownerKind,
        legalEntityId,
        payrollStatutoryUnitId,
        ApprovalRole.FINAL_APPROVER,
        "AUTHORISED_SIGNATORY",
        "APPROVE");
  }

  private void requireOwned(
      String ownerKind,
      UUID legalEntityId,
      UUID payrollStatutoryUnitId,
      ApprovalRole role,
      String domain,
      String action) {
    if ("LEGAL_ENTITY".equals(ownerKind)) {
      require(
          ApprovalOwnerKind.LEGAL_ENTITY,
          requireId(legalEntityId, "legalEntityId"),
          role,
          domain,
          action);
      return;
    }
    if ("PAYROLL_STATUTORY_UNIT".equals(ownerKind)) {
      require(
          ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
          requireId(payrollStatutoryUnitId, "payrollStatutoryUnitId"),
          role,
          domain,
          action);
      return;
    }
    throw new AccessDeniedException("Unsupported owner kind for application approval authority");
  }

  private void requirePsu(
      OrganisationApprovalScopeFacade.ApprovalScope scope,
      ApprovalRole role,
      String domain,
      String action) {
    require(
        ApprovalOwnerKind.PAYROLL_STATUTORY_UNIT,
        scope.payrollStatutoryUnitId(),
        role,
        domain,
        action);
  }

  private void require(
      ApprovalOwnerKind ownerKind,
      UUID ownerId,
      ApprovalRole role,
      String domain,
      String action) {
    authority.requireAuthority(
        new ApprovalAuthorityRequirement(
            ownerKind,
            ownerId,
            role,
            domain,
            action,
            LocalDate.now(clock)));
  }

  private OrganisationApprovalScopeFacade.ApprovalScope requireScope(
      Optional<OrganisationApprovalScopeFacade.ApprovalScope> scope,
      String message) {
    return scope.orElseThrow(() -> new AccessDeniedException(message));
  }

  private UUID requireId(UUID id, String name) {
    if (id == null) {
      throw new AccessDeniedException(name + " is required for application approval authority");
    }
    return id;
  }
}
