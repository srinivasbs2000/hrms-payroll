package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.security.internal.infrastructure.ApprovalAuthorityRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalAuthorityFacade {
  private final ApprovalAuthorityRepository repository;
  private final AuthenticatedActor actor;
  private final TenantTransactionExecutor transactions;

  public ApprovalAuthorityFacade(
      ApprovalAuthorityRepository repository,
      AuthenticatedActor actor,
      TenantTransactionExecutor transactions) {
    this.repository = repository;
    this.actor = actor;
    this.transactions = transactions;
  }

  public ApprovalAuthorityDecision requireAuthority(ApprovalAuthorityRequirement requirement) {
    requirement.validate();
    String effectiveActor = actor.require();
    if (requirement.approvalRole() == ApprovalRole.FINAL_APPROVER
        && effectiveActor.startsWith("service:")) {
      throw new AccessDeniedException("Service identity cannot perform interactive final approval");
    }
    return transactions.read(() -> repository.resolve(effectiveActor, requirement)
        .orElseThrow(() -> new AccessDeniedException(
            "No effective approval authority for the requested owner scope")));
  }
}
