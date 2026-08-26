package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCaseView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCreateRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingEventView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingTransitionRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollOnboardingRepository;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollOnboardingService {
  private final EmployeePayrollOnboardingRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public EmployeePayrollOnboardingService(
      EmployeePayrollOnboardingRepository repository,
      EmployeePayrollCommandExecutor commands,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock) {
    this.repository = repository;
    this.commands = commands;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
  }

  public OnboardingCaseView create(
      UUID relationshipId, String key, OnboardingCreateRequest request) {
    return commands.execute(
        "employee-payroll:onboarding:create:" + relationshipId,
        key, request, OnboardingCaseView.class,
        () -> repository.create(
            request.caseId(), relationshipId, request.reason(),
            request.evidenceRef(), actor.require(), clock.instant()));
  }

  public OnboardingCaseView get(UUID relationshipId) {
    return transactions.read(() -> repository.forRelationship(relationshipId));
  }

  public List<OnboardingEventView> history(UUID relationshipId) {
    return transactions.read(() -> {
      OnboardingCaseView view = repository.forRelationship(relationshipId);
      return repository.history(view.id());
    });
  }

  public OnboardingCaseView transition(
      UUID relationshipId, String key, long expectedVersion,
      OnboardingTransitionRequest request) {
    return doTransition(relationshipId, key, expectedVersion, request, false);
  }

  public OnboardingCaseView approve(
      UUID relationshipId, String key, long expectedVersion,
      OnboardingTransitionRequest request) {
    return doTransition(relationshipId, key, expectedVersion, request, true);
  }

  private OnboardingCaseView doTransition(
      UUID relationshipId, String key, long expectedVersion,
      OnboardingTransitionRequest request, boolean independentApproval) {
    OnboardingCaseView current = get(relationshipId);
    return commands.execute(
        "employee-payroll:onboarding:transition:" + current.id(), key,
        Map.of(
            "expectedVersion", expectedVersion,
            "targetStatus", request.targetStatus(),
            "reason", request.reason(),
            "evidenceRef", request.evidenceRef(),
            "asOf", request.asOf() == null ? "" : request.asOf().toString(),
            "approvalAuthority", independentApproval),
        OnboardingCaseView.class,
        () -> repository.transition(
            current.id(), expectedVersion, request.targetStatus(),
            request.reason(), request.evidenceRef(), actor.require(),
            clock.instant(), request.asOf(), independentApproval));
  }
}
