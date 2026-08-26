package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldEvidenceRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldWriteRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollHoldRepository;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollHoldService {
  private final EmployeePayrollHoldRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public EmployeePayrollHoldService(
      EmployeePayrollHoldRepository repository,
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

  public PayrollHoldView create(
      UUID relationshipId, String key, PayrollHoldWriteRequest request) {
    return commands.execute(
        "employee-payroll:hold:create:" + request.versionId(),
        key, request, PayrollHoldView.class,
        () -> repository.create(relationshipId, request, actor.require(), clock.instant()));
  }

  public PayrollHoldView approve(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, PayrollHoldEvidenceRequest request) {
    return commands.execute(
        "employee-payroll:hold:approve:" + versionId, key,
        Map.of("relationshipId", relationshipId, "versionId", versionId,
            "expectedVersion", expectedVersion, "evidenceRef", request.evidenceRef()),
        PayrollHoldView.class,
        () -> ensureRelationship(
            relationshipId,
            repository.approve(
                versionId, expectedVersion, actor.require(),
                request.evidenceRef(), clock.instant())));
  }

  public PayrollHoldView release(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, PayrollHoldEvidenceRequest request) {
    return commands.execute(
        "employee-payroll:hold:release:" + versionId, key,
        Map.of("relationshipId", relationshipId, "versionId", versionId,
            "expectedVersion", expectedVersion, "evidenceRef", request.evidenceRef()),
        PayrollHoldView.class,
        () -> ensureRelationship(
            relationshipId,
            repository.release(
                versionId, expectedVersion, actor.require(),
                request.evidenceRef(), clock.instant())));
  }

  public List<PayrollHoldView> holds(UUID relationshipId, LocalDate asOf) {
    return transactions.read(() -> repository.holds(relationshipId, asOf));
  }

  private PayrollHoldView ensureRelationship(UUID relationshipId, PayrollHoldView view) {
    if (!relationshipId.equals(view.payrollRelationshipId())) {
      throw new IllegalArgumentException("Payroll hold relationship mismatch");
    }
    return view;
  }
}
