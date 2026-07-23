package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentView;
import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentWriteRequest;
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
public class PayGroupAssignmentService {
  public static final String OBJECT_TYPE = "PAY_GROUP_ASSIGNMENT";

  private final EmployeePayrollRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public PayGroupAssignmentService(
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

  public PayGroupAssignmentView create(
      String key, PayGroupAssignmentWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:pay-group-assignment:create",
        key,
        request,
        PayGroupAssignmentView.class,
        () -> {
          PayGroupAssignmentView created =
              repository.createPayGroupAssignment(
                  request, null, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public PayGroupAssignmentView correctFuture(
      UUID assignmentId,
      String key,
      PayGroupAssignmentWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:pay-group-assignment:correct:" + assignmentId,
        key,
        request,
        PayGroupAssignmentView.class,
        () -> {
          PayGroupAssignmentView previous =
              repository.payGroupAssignment(assignmentId);
          requireFutureDraft(previous);
          if (!previous.payrollAssignmentVersionId()
              .equals(request.payrollAssignmentVersionId())) {
            throw new IllegalArgumentException(
                "A pay-group correction must retain its "
                    + "payroll assignment version");
          }
          PayGroupAssignmentView corrected =
              repository.createPayGroupAssignment(
                  request, assignmentId, actor.require());
          record("CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public PayGroupAssignmentView approve(UUID assignmentId, String key) {
    return commands.execute(
        "employee-payroll:pay-group-assignment:approve:" + assignmentId,
        key,
        Map.of("assignmentId", assignmentId),
        PayGroupAssignmentView.class,
        () -> {
          PayGroupAssignmentView before =
              repository.payGroupAssignment(assignmentId);
          PayGroupAssignmentView approved =
              repository.approvePayGroupAssignment(
                  assignmentId, actor.require(), clock.instant());
          record("APPROVED", approved, before);
          return approved;
        });
  }

  public PayGroupAssignmentView endDate(
      UUID assignmentId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return commands.execute(
        "employee-payroll:pay-group-assignment:end-date:" + assignmentId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        PayGroupAssignmentView.class,
        () -> {
          PayGroupAssignmentView before =
              repository.payGroupAssignment(assignmentId);
          PayGroupAssignmentView ended =
              repository.endDatePayGroupAssignment(
                  assignmentId,
                  effectiveTo,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("END_DATED", ended, before);
          return ended;
        });
  }

  public List<PayGroupAssignmentView> list(UUID assignmentVersionId) {
    return transactions.read(
        () -> repository.payGroupAssignments(assignmentVersionId));
  }

  public List<AuditReader.AuditEventView> audit(UUID assignmentId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, assignmentId));
  }

  private void requireFutureDraft(PayGroupAssignmentView view) {
    if (!"DRAFT".equals(view.approvalStatus())
        || view.superseded()
        || !view.effectiveFrom().isAfter(LocalDate.now(clock))) {
      throw new ConflictException(
          "Only a non-superseded future draft "
              + "pay-group assignment can be corrected");
    }
  }

  private void record(
      String action,
      PayGroupAssignmentView after,
      PayGroupAssignmentView before) {
    recorder.record(
        action,
        OBJECT_TYPE,
        "PayGroupAssignment" + action,
        after.id(),
        after.versionNo() + 1,
        state(before),
        state(after),
        Map.of(
            "payrollAssignmentVersionId",
            after.payrollAssignmentVersionId()));
  }

  private Map<String, Object> state(PayGroupAssignmentView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put(
        "payrollAssignmentVersionId",
        view.payrollAssignmentVersionId());
    state.put("payGroupVersionId", view.payGroupVersionId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("supersedesAssignmentId", view.supersedesAssignmentId());
    return state;
  }
}
