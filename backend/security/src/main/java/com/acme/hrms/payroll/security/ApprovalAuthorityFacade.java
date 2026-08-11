package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.security.internal.application.ApprovalPrincipalClassifier;
import java.util.LinkedHashMap;
import java.util.Map;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.security.internal.infrastructure.ApprovalAuthorityRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalAuthorityFacade {
  private final ApprovalAuthorityRepository repository;
  private final AuthenticatedActor actor;
  private final TenantTransactionExecutor transactions;
  private final ApprovalPrincipalClassifier principalClassifier;
  private final AuditWriter audit;

  public ApprovalAuthorityFacade(
      ApprovalAuthorityRepository repository,
      AuthenticatedActor actor,
      TenantTransactionExecutor transactions,
      ApprovalPrincipalClassifier principalClassifier,
      AuditWriter audit) {
    this.repository = repository;
    this.actor = actor;
    this.transactions = transactions;
    this.principalClassifier = principalClassifier;
    this.audit = audit;
  }

  public ApprovalAuthorityDecision requireAuthority(ApprovalAuthorityRequirement requirement) {
    requirement.validate();
    String effectiveActor = actor.require();
    if (requirement.approvalRole() == ApprovalRole.FINAL_APPROVER
        && principalClassifier.isServiceIdentity(effectiveActor)) {
      throw new AccessDeniedException("Service identity cannot perform interactive final approval");
    }
    return transactions.write(() -> {
      ApprovalAuthorityDecision decision =
          repository.resolve(effectiveActor, requirement)
              .orElseThrow(() -> new AccessDeniedException(
                  "No effective approval authority for the requested owner scope"));
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("authorityId", decision.authorityId());
      evidence.put("delegationId", decision.delegationId());
      evidence.put("delegated", decision.delegated());
      evidence.put("ownerKind", decision.ownerKind());
      evidence.put("ownerId", decision.ownerId());
      evidence.put("approvalRole", decision.approvalRole());
      evidence.put("domainCode", decision.domainCode());
      evidence.put("actionCode", decision.actionCode());
      evidence.put("decisionDate", decision.decisionDate());
      evidence.put("sourceActorId", decision.sourceActorId());
      evidence.put("effectiveActorId", decision.effectiveActorId());
      audit.append(
          "AUTHORIZED",
          "APPLICATION_APPROVAL_DECISION",
          decision.authorityId(),
          null,
          evidence,
          Map.of("schemaVersion", 1),
          effectiveActor);
      return decision;
    });
  }
}
