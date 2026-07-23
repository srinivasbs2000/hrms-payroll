package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.SalaryAssignmentView;
import com.acme.hrms.payroll.employeepayroll.SalaryAssignmentWriteRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollRepository;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalaryAssignmentService {
  public static final String OBJECT_TYPE = "SALARY_ASSIGNMENT";

  private final EmployeePayrollRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public SalaryAssignmentService(
      EmployeePayrollRepository repository,
      EmployeePayrollCommandExecutor commands,
      EmployeePayrollEventRecorder recorder,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      AuditReader auditReader,
      Clock clock) {
    this.repository = repository;
    this.commands = commands;
    this.recorder = recorder;
    this.transactions = transactions;
    this.actor = actor;
    this.auditReader = auditReader;
    this.clock = clock;
  }

  public SalaryAssignmentView create(
      String key, SalaryAssignmentWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:salary-assignment:create",
        key,
        request,
        SalaryAssignmentView.class,
        () -> {
          SalaryAssignmentView created =
              repository.createSalaryAssignment(
                  request, null, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public SalaryAssignmentView correctFuture(
      UUID assignmentId,
      String key,
      SalaryAssignmentWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:salary-assignment:correct:" + assignmentId,
        key,
        request,
        SalaryAssignmentView.class,
        () -> {
          SalaryAssignmentView previous =
              repository.salaryAssignment(assignmentId);
          requireFutureDraft(previous);
          if (!previous.payrollAssignmentVersionId()
              .equals(request.payrollAssignmentVersionId())) {
            throw new IllegalArgumentException(
                "A salary correction must retain its "
                    + "payroll assignment version");
          }
          SalaryAssignmentView corrected =
              repository.createSalaryAssignment(
                  request, assignmentId, actor.require());
          record("CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public SalaryAssignmentView approve(UUID assignmentId, String key) {
    return commands.execute(
        "employee-payroll:salary-assignment:approve:" + assignmentId,
        key,
        Map.of("assignmentId", assignmentId),
        SalaryAssignmentView.class,
        () -> {
          SalaryAssignmentView before =
              repository.salaryAssignment(assignmentId);
          SalaryAssignmentView approved =
              repository.approveSalaryAssignment(
                  assignmentId, actor.require(), clock.instant());
          record("APPROVED", approved, before);
          return approved;
        });
  }

  public SalaryAssignmentView endDate(
      UUID assignmentId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return commands.execute(
        "employee-payroll:salary-assignment:end-date:" + assignmentId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        SalaryAssignmentView.class,
        () -> {
          SalaryAssignmentView before =
              repository.salaryAssignment(assignmentId);
          SalaryAssignmentView ended =
              repository.endDateSalaryAssignment(
                  assignmentId,
                  effectiveTo,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("END_DATED", ended, before);
          return ended;
        });
  }

  public List<SalaryAssignmentView> list(UUID assignmentVersionId) {
    return transactions.read(
        () -> repository.salaryAssignments(assignmentVersionId));
  }

  public List<AuditReader.AuditEventView> audit(UUID assignmentId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, assignmentId));
  }

  private void requireFutureDraft(SalaryAssignmentView view) {
    if (!"DRAFT".equals(view.approvalStatus())
        || view.superseded()
        || !view.effectiveFrom().isAfter(LocalDate.now(clock))) {
      throw new ConflictException(
          "Only a non-superseded future draft "
              + "salary assignment can be corrected");
    }
  }

  private void record(
      String action,
      SalaryAssignmentView after,
      SalaryAssignmentView before) {
    recorder.record(
        action,
        OBJECT_TYPE,
        "SalaryAssignment" + action,
        after.id(),
        after.versionNo() + 1,
        state(before),
        state(after),
        Map.of(
            "payrollAssignmentVersionId",
            after.payrollAssignmentVersionId()));
  }

  private Map<String, Object> state(SalaryAssignmentView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put(
        "payrollAssignmentVersionId",
        view.payrollAssignmentVersionId());
    state.put(
        "salaryStructureVersionId",
        view.salaryStructureVersionId());
    state.put("monthlyAmount", view.monthlyAmount());
    state.put("currency", view.currency());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("supersedesAssignmentId", view.supersedesAssignmentId());
    return state;
  }
}
