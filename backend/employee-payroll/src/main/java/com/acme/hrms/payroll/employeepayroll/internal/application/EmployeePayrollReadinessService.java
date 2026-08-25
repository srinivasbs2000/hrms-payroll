package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessFindingView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessView;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollReadinessRepository;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollReadinessService {
  private final EmployeePayrollReadinessRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public EmployeePayrollReadinessService(
      EmployeePayrollReadinessRepository repository,
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

  public ReadinessView readiness(
      UUID relationshipId, String currencyCode, LocalDate asOf) {
    LocalDate effective = asOf == null ? LocalDate.now(clock) : asOf;
    return transactions.read(() -> {
      List<ReadinessFindingView> findings =
          repository.findings(relationshipId, currencyCode, effective);
      boolean ready = findings.stream().noneMatch(finding ->
          "BLOCKING".equals(finding.severity())
              && !"READY".equals(finding.status())
              && !"EXPLICIT_NOT_APPLICABLE".equals(finding.status()));
      return new ReadinessView(
          relationshipId, currencyCode, effective, ready, List.copyOf(findings));
    });
  }

  public ReadinessPolicyView createPolicy(
      String key, ReadinessPolicyWriteRequest request) {
    return commands.execute(
        "employee-payroll:readiness-policy:create:" + request.dimension(),
        key, request, ReadinessPolicyView.class,
        () -> repository.createPolicy(request, actor.require(), clock.instant()));
  }

  public List<ReadinessPolicyView> policies(String dimension) {
    return transactions.read(() -> repository.policies(dimension));
  }
}
