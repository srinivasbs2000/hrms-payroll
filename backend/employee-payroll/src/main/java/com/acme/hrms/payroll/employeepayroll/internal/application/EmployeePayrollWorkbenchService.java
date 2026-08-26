package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCaseView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessFindingView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollWorkbenchModels.WorkbenchItemView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollWorkbenchModels.WorkbenchView;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollHoldRepository;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollOnboardingRepository;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollReadinessRepository;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollWorkbenchService {
  private final EmployeePayrollOnboardingRepository onboarding;
  private final EmployeePayrollReadinessRepository readiness;
  private final EmployeePayrollHoldRepository holds;
  private final TenantTransactionExecutor transactions;
  private final Clock clock;

  public EmployeePayrollWorkbenchService(
      EmployeePayrollOnboardingRepository onboarding,
      EmployeePayrollReadinessRepository readiness,
      EmployeePayrollHoldRepository holds,
      TenantTransactionExecutor transactions,
      Clock clock) {
    this.onboarding = onboarding;
    this.readiness = readiness;
    this.holds = holds;
    this.transactions = transactions;
    this.clock = clock;
  }

  public WorkbenchView view(
      String onboardingStatus, String holdScope, LocalDate asOf) {
    LocalDate effective = asOf == null ? LocalDate.now(clock) : asOf;
    return transactions.read(() -> {
      List<WorkbenchItemView> items = onboarding.cases(onboardingStatus).stream()
          .map(item -> build(item, effective))
          .filter(item -> holdScope == null || holdScope.isBlank()
              || item.activeHoldScopes().contains(holdScope))
          .toList();
      return new WorkbenchView(effective, items.size(), List.copyOf(items));
    });
  }

  private WorkbenchItemView build(OnboardingCaseView item, LocalDate asOf) {
    List<ReadinessFindingView> findings =
        readiness.findings(item.payrollRelationshipId(), null, asOf);
    long blocking = findings.stream()
        .filter(f -> "BLOCKING".equals(f.severity())
            && !"READY".equals(f.status())
            && !"EXPLICIT_NOT_APPLICABLE".equals(f.status()))
        .count();
    List<String> dimensions = findings.stream()
        .filter(f -> "BLOCKING".equals(f.severity())
            && !"READY".equals(f.status())
            && !"EXPLICIT_NOT_APPLICABLE".equals(f.status()))
        .map(ReadinessFindingView::dimension).distinct().sorted().toList();
    List<PayrollHoldView> active = holds.holds(item.payrollRelationshipId(), asOf)
        .stream().filter(h -> "ACTIVE".equals(h.lifecycleStatus())).toList();
    LinkedHashSet<String> scopes = new LinkedHashSet<>();
    active.stream().flatMap(h -> h.scopes().stream()).sorted().forEach(scopes::add);
    return new WorkbenchItemView(
        item.payrollRelationshipId(), item.id(), item.currentStatus(), asOf,
        blocking == 0, blocking, dimensions, active.size(), List.copyOf(scopes));
  }
}
