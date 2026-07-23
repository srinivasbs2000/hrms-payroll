package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.PayrollAssignmentView;
import com.acme.hrms.payroll.employeepayroll.PayrollAssignmentWriteRequest;
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
public class PayrollAssignmentService {
  public static final String OBJECT_TYPE = "PAYROLL_ASSIGNMENT";

  private final EmployeePayrollRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public PayrollAssignmentService(
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

  public PayrollAssignmentView create(
      String key, PayrollAssignmentWriteRequest request) {
    request.validate(true);
    return commands.execute(
        "employee-payroll:assignment:create",
        key,
        request,
        PayrollAssignmentView.class,
        () -> {
          PayrollAssignmentView created =
              repository.createAssignment(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public PayrollAssignmentView addVersion(
      UUID identityId,
      String key,
      PayrollAssignmentWriteRequest request) {
    request.validate(false);
    return commands.execute(
        "employee-payroll:assignment:version-create:" + identityId,
        key,
        request,
        PayrollAssignmentView.class,
        () -> {
          PayrollAssignmentView created =
              repository.addAssignmentVersion(
                  identityId, request, null, actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public PayrollAssignmentView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      PayrollAssignmentWriteRequest request) {
    request.validate(false);
    return commands.execute(
        "employee-payroll:assignment:version-correct:" + versionId,
        key,
        request,
        PayrollAssignmentView.class,
        () -> {
          PayrollAssignmentView previous =
              repository.assignmentVersion(versionId);
          requireIdentity(previous, identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.assignmentStart().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft "
                    + "payroll assignment version can be corrected");
          }
          PayrollAssignmentView corrected =
              repository.addAssignmentVersion(
                  identityId, request, versionId, actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public PayrollAssignmentView approve(
      UUID identityId, UUID versionId, String key) {
    return commands.execute(
        "employee-payroll:assignment:approve:"
            + identityId
            + ":"
            + versionId,
        key,
        Map.of("versionId", versionId),
        PayrollAssignmentView.class,
        () -> {
          PayrollAssignmentView before =
              repository.assignmentVersion(versionId);
          requireIdentity(before, identityId);
          PayrollAssignmentView approved =
              repository.approveAssignment(
                  versionId, actor.require(), clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public PayrollAssignmentView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate assignmentEnd,
      long expectedVersion) {
    return commands.execute(
        "employee-payroll:assignment:end-date:" + versionId,
        key,
        Map.of(
            "assignmentEnd", assignmentEnd,
            "expectedVersion", expectedVersion),
        PayrollAssignmentView.class,
        () -> {
          PayrollAssignmentView before =
              repository.assignmentVersion(versionId);
          requireIdentity(before, identityId);
          PayrollAssignmentView ended =
              repository.endDateAssignment(
                  versionId,
                  assignmentEnd,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public List<PayrollAssignmentView> list(
      UUID relationshipId, LocalDate asOf) {
    return transactions.read(
        () -> repository.assignments(
            relationshipId, effectiveDate(asOf)));
  }

  public PayrollAssignmentView current(
      UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.currentAssignment(
            identityId, effectiveDate(asOf)));
  }

  public List<PayrollAssignmentView> history(UUID identityId) {
    return transactions.read(
        () -> repository.assignmentHistory(identityId));
  }

  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private void record(
      String action,
      PayrollAssignmentView after,
      PayrollAssignmentView before) {
    recorder.record(
        action,
        OBJECT_TYPE,
        "PayrollAssignment" + action,
        after.identityId(),
        after.versionSequence(),
        state(before),
        state(after),
        Map.of("versionId", after.versionId()));
  }

  private Map<String, Object> state(PayrollAssignmentView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("payrollRelationshipId", view.payrollRelationshipId());
    state.put("assignmentNumber", view.assignmentNumber());
    state.put(
        "payrollRelationshipVersionId",
        view.payrollRelationshipVersionId());
    state.put("establishmentVersionId", view.establishmentVersionId());
    state.put("assignmentStart", view.assignmentStart());
    state.put("assignmentEnd", view.assignmentEnd());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private void requireIdentity(
      PayrollAssignmentView view, UUID identityId) {
    if (!view.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to payroll assignment identity");
    }
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }
}
