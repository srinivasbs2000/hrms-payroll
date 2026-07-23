package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollProfileStatusRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollProfileView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollProfileWriteRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollRepository;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollProfileService {
  public static final String OBJECT_TYPE = "EMPLOYEE_PAYROLL_PROFILE";

  private final EmployeePayrollRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public EmployeePayrollProfileService(
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

  public EmployeePayrollProfileView create(
      String key, EmployeePayrollProfileWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:profile:create",
        key,
        request,
        EmployeePayrollProfileView.class,
        () -> {
          EmployeePayrollProfileView created =
              repository.createProfile(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public EmployeePayrollProfileView get(UUID profileId) {
    return transactions.read(() -> repository.profile(profileId));
  }

  public EmployeePayrollProfileView forRelationship(UUID relationshipId) {
    return transactions.read(
        () -> repository.profileForRelationship(relationshipId));
  }

  public EmployeePayrollProfileView updateStatus(
      UUID profileId,
      String key,
      EmployeePayrollProfileStatusRequest request,
      long expectedVersion) {
    request.validate();
    return commands.execute(
        "employee-payroll:profile:status:" + profileId,
        key,
        Map.of(
            "payrollStatus", request.payrollStatus(),
            "expectedVersion", expectedVersion),
        EmployeePayrollProfileView.class,
        () -> {
          EmployeePayrollProfileView before = repository.profile(profileId);
          EmployeePayrollProfileView updated =
              repository.updateProfileStatus(
                  profileId,
                  request.payrollStatus(),
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("STATUS_UPDATED", updated, before);
          return updated;
        });
  }

  public List<AuditReader.AuditEventView> audit(UUID profileId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, profileId));
  }

  private void record(
      String action,
      EmployeePayrollProfileView after,
      EmployeePayrollProfileView before) {
    recorder.record(
        action,
        OBJECT_TYPE,
        "EmployeePayrollProfile" + action,
        after.id(),
        after.versionNo() + 1,
        state(before),
        state(after),
        Map.of("payrollRelationshipId", after.payrollRelationshipId()));
  }

  private Map<String, Object> state(EmployeePayrollProfileView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("payrollRelationshipId", view.payrollRelationshipId());
    state.put("employeeNumber", view.employeeNumber());
    state.put("currency", view.currency());
    state.put("payrollStatus", view.payrollStatus());
    state.put("versionNo", view.versionNo());
    return state;
  }
}
