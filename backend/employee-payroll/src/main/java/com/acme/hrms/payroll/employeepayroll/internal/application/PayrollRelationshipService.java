package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.PayrollRelationshipView;
import com.acme.hrms.payroll.employeepayroll.PayrollRelationshipWriteRequest;
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
public class PayrollRelationshipService {
  public static final String OBJECT_TYPE = "PAYROLL_RELATIONSHIP";

  private final EmployeePayrollRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public PayrollRelationshipService(
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

  public PayrollRelationshipView create(
      String key, PayrollRelationshipWriteRequest request) {
    request.validate(true);
    return commands.execute(
        "employee-payroll:relationship:create",
        key,
        request,
        PayrollRelationshipView.class,
        () -> {
          PayrollRelationshipView created =
              repository.createRelationship(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public PayrollRelationshipView addVersion(
      UUID identityId,
      String key,
      PayrollRelationshipWriteRequest request) {
    request.validate(false);
    return commands.execute(
        "employee-payroll:relationship:version-create:" + identityId,
        key,
        request,
        PayrollRelationshipView.class,
        () -> {
          PayrollRelationshipView created =
              repository.addRelationshipVersion(
                  identityId, request, null, actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public PayrollRelationshipView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      PayrollRelationshipWriteRequest request) {
    request.validate(false);
    return commands.execute(
        "employee-payroll:relationship:version-correct:" + versionId,
        key,
        request,
        PayrollRelationshipView.class,
        () -> {
          PayrollRelationshipView previous =
              repository.relationshipVersion(versionId);
          requireIdentity(previous, identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.relationshipStart()
                  .isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft "
                    + "payroll relationship version can be corrected");
          }
          PayrollRelationshipView corrected =
              repository.addRelationshipVersion(
                  identityId, request, versionId, actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public PayrollRelationshipView approve(
      UUID identityId, UUID versionId, String key) {
    return commands.execute(
        "employee-payroll:relationship:approve:"
            + identityId
            + ":"
            + versionId,
        key,
        Map.of("versionId", versionId),
        PayrollRelationshipView.class,
        () -> {
          PayrollRelationshipView before =
              repository.relationshipVersion(versionId);
          requireIdentity(before, identityId);
          PayrollRelationshipView approved =
              repository.approveRelationship(
                  versionId, actor.require(), clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public PayrollRelationshipView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate relationshipEnd,
      long expectedVersion) {
    return commands.execute(
        "employee-payroll:relationship:end-date:" + versionId,
        key,
        Map.of(
            "relationshipEnd", relationshipEnd,
            "expectedVersion", expectedVersion),
        PayrollRelationshipView.class,
        () -> {
          PayrollRelationshipView before =
              repository.relationshipVersion(versionId);
          requireIdentity(before, identityId);
          PayrollRelationshipView ended =
              repository.endDateRelationship(
                  versionId,
                  relationshipEnd,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public List<PayrollRelationshipView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.relationships(effectiveDate(asOf)));
  }

  public PayrollRelationshipView current(
      UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.currentRelationship(
            identityId, effectiveDate(asOf)));
  }

  public List<PayrollRelationshipView> history(UUID identityId) {
    return transactions.read(
        () -> repository.relationshipHistory(identityId));
  }

  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private void record(
      String action,
      PayrollRelationshipView after,
      PayrollRelationshipView before) {
    recorder.record(
        action,
        OBJECT_TYPE,
        "PayrollRelationship" + action,
        after.identityId(),
        after.versionSequence(),
        state(before),
        state(after),
        Map.of("versionId", after.versionId()));
  }

  private Map<String, Object> state(PayrollRelationshipView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("externalEmployeeId", view.externalEmployeeId());
    state.put("employeeNumber", view.employeeNumber());
    state.put("legalEntityVersionId", view.legalEntityVersionId());
    state.put("relationshipStart", view.relationshipStart());
    state.put("relationshipEnd", view.relationshipEnd());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private void requireIdentity(
      PayrollRelationshipView view, UUID identityId) {
    if (!view.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to payroll relationship identity");
    }
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }
}
