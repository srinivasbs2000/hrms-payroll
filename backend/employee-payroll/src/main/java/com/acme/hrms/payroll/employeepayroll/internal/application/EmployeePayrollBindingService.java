package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.CompensationChangeAssessmentRequest;
import com.acme.hrms.payroll.employeepayroll.CompensationChangeImpactView;
import com.acme.hrms.payroll.employeepayroll.CompensationChangeView;
import com.acme.hrms.payroll.employeepayroll.CompensationChangeWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeComponentOverrideView;
import com.acme.hrms.payroll.employeepayroll.EmployeeComponentOverrideWriteRequest;
import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentImpactView;
import com.acme.hrms.payroll.employeepayroll.PayrollLifecycleLineageView;
import com.acme.hrms.payroll.employeepayroll.PayrollLifecycleLineageWriteRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeePayrollBindingRepository;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeePayrollBindingService {
  public static final String COMPENSATION_CHANGE = "COMPENSATION_CHANGE";
  public static final String COMPONENT_OVERRIDE = "EMPLOYEE_COMPONENT_OVERRIDE";
  public static final String LIFECYCLE_LINEAGE = "PAYROLL_LIFECYCLE_LINEAGE";

  private final EmployeePayrollBindingRepository repository;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader audit;
  private final Clock clock;

  public EmployeePayrollBindingService(
      EmployeePayrollBindingRepository repository,
      EmployeePayrollCommandExecutor commands,
      EmployeePayrollEventRecorder recorder,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      AuditReader audit,
      Clock clock) {
    this.repository = repository;
    this.commands = commands;
    this.recorder = recorder;
    this.transactions = transactions;
    this.actor = actor;
    this.audit = audit;
    this.clock = clock;
  }

  public CompensationChangeView createCompensationChange(
      String key, CompensationChangeWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:compensation-change:create",
        key,
        request,
        CompensationChangeView.class,
        () -> {
          CompensationChangeView view = repository.createCompensationChange(request, actor.require());
          record("CREATED", COMPENSATION_CHANGE, "CompensationChangeCreated", view.id(), null, state(view));
          return view;
        });
  }

  public CompensationChangeView assessCompensationChange(
      UUID id, String key, CompensationChangeAssessmentRequest request) {
    return commands.execute(
        "employee-payroll:compensation-change:assess:" + id,
        key,
        request,
        CompensationChangeView.class,
        () -> {
          CompensationChangeView before = repository.compensationChange(id);
          CompensationChangeView after = repository.assessCompensationChange(
              id, request.assessmentThrough(), actor.require(), clock.instant());
          record("IMPACT_ASSESSED", COMPENSATION_CHANGE, "CompensationChangeImpactAssessed",
              id, state(before), state(after));
          return after;
        });
  }

  public CompensationChangeView approveCompensationChange(UUID id, String key) {
    return commands.execute(
        "employee-payroll:compensation-change:approve:" + id,
        key,
        Map.of("id", id),
        CompensationChangeView.class,
        () -> {
          CompensationChangeView before = repository.compensationChange(id);
          if (before.assessmentThrough() == null) {
            throw new ConflictException("Compensation change requires impact assessment before approval");
          }
          CompensationChangeView after = repository.approveCompensationChange(id, actor.require(), clock.instant());
          record("APPROVED", COMPENSATION_CHANGE, "CompensationChangeApproved",
              id, state(before), state(after));
          return after;
        });
  }

  public List<CompensationChangeView> compensationChanges(UUID assignmentId) {
    return transactions.read(() -> repository.compensationChanges(assignmentId));
  }

  public List<CompensationChangeImpactView> compensationChangeImpact(UUID id) {
    return transactions.read(() -> repository.compensationChangeImpact(id));
  }

  public EmployeeComponentOverrideView createOverride(
      String key, EmployeeComponentOverrideWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:component-override:create",
        key,
        request,
        EmployeeComponentOverrideView.class,
        () -> {
          EmployeeComponentOverrideView view = repository.createOverride(request, null, actor.require());
          record("CREATED", COMPONENT_OVERRIDE, "EmployeeComponentOverrideCreated",
              view.id(), null, state(view));
          return view;
        });
  }

  public EmployeeComponentOverrideView correctOverride(
      UUID id, String key, EmployeeComponentOverrideWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:component-override:correct:" + id,
        key,
        request,
        EmployeeComponentOverrideView.class,
        () -> {
          EmployeeComponentOverrideView before = repository.componentOverride(id);
          if (!"DRAFT".equals(before.approvalStatus()) || before.superseded()) {
            throw new ConflictException("Only a non-superseded draft override can be corrected");
          }
          if (!before.payrollAssignmentVersionId().equals(request.payrollAssignmentVersionId())
              || !before.salaryAssignmentId().equals(request.salaryAssignmentId())
              || !before.salaryStructureLineId().equals(request.salaryStructureLineId())
              || !before.componentVersionId().equals(request.componentVersionId())) {
            throw new IllegalArgumentException(
                "Override correction must retain assignment, salary, line and "
                    + "component lineage");
          }
          EmployeeComponentOverrideView after = repository.createOverride(request, id, actor.require());
          record("CORRECTED", COMPONENT_OVERRIDE, "EmployeeComponentOverrideCorrected",
              after.id(), state(before), state(after));
          return after;
        });
  }

  public EmployeeComponentOverrideView approveOverride(UUID id, String key) {
    return commands.execute(
        "employee-payroll:component-override:approve:" + id,
        key,
        Map.of("id", id),
        EmployeeComponentOverrideView.class,
        () -> {
          EmployeeComponentOverrideView before = repository.componentOverride(id);
          EmployeeComponentOverrideView after = repository.approveOverride(id, actor.require(), clock.instant());
          record("APPROVED", COMPONENT_OVERRIDE, "EmployeeComponentOverrideApproved",
              id, state(before), state(after));
          return after;
        });
  }

  public List<EmployeeComponentOverrideView> overrides(UUID assignmentVersionId) {
    return transactions.read(() -> repository.componentOverrides(assignmentVersionId));
  }

  public PayrollLifecycleLineageView createLineage(
      String key, PayrollLifecycleLineageWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:lifecycle-lineage:create",
        key,
        request,
        PayrollLifecycleLineageView.class,
        () -> {
          PayrollLifecycleLineageView view = repository.createLineage(request, actor.require());
          record("CREATED", LIFECYCLE_LINEAGE, "PayrollLifecycleLineageCreated",
              view.id(), null, state(view));
          return view;
        });
  }

  public PayrollLifecycleLineageView approveLineage(UUID id, String key) {
    return commands.execute(
        "employee-payroll:lifecycle-lineage:approve:" + id,
        key,
        Map.of("id", id),
        PayrollLifecycleLineageView.class,
        () -> {
          PayrollLifecycleLineageView before = repository.lineage(id);
          PayrollLifecycleLineageView after = repository.approveLineage(id, actor.require(), clock.instant());
          record("APPROVED", LIFECYCLE_LINEAGE, "PayrollLifecycleLineageApproved",
              id, state(before), state(after));
          return after;
        });
  }

  public List<PayrollLifecycleLineageView> lineage(UUID relationshipId) {
    return transactions.read(() -> repository.lineageForRelationship(relationshipId));
  }

  public List<PayGroupAssignmentImpactView> payGroupImpact(UUID id) {
    return transactions.read(() -> repository.payGroupImpact(id));
  }

  public List<AuditReader.AuditEventView> audit(String objectType, UUID id) {
    return transactions.read(() -> audit.forObject(objectType, id));
  }

  private void record(
      String action,
      String objectType,
      String eventType,
      UUID id,
      Map<String, Object> before,
      Map<String, Object> after) {
    long version = after != null && after.get("versionNo") instanceof Number number
        ? number.longValue() + 1
        : 1L;
    recorder.record(action, objectType, eventType, id, version, before, after, Map.of());
  }

  private Map<String, Object> state(Object view) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (view instanceof CompensationChangeView value) {
      result.put("id", value.id());
      result.put("payrollAssignmentId", value.payrollAssignmentId());
      result.put("eventType", value.eventType());
      result.put("effectiveDate", value.effectiveDate());
      result.put("sourceEventId", value.sourceEventId());
      result.put("assessmentThrough", value.assessmentThrough());
      result.put("impactedPeriodCount", value.impactedPeriodCount());
      result.put("approvalStatus", value.approvalStatus());
      result.put("versionNo", value.versionNo());
    } else if (view instanceof EmployeeComponentOverrideView value) {
      result.put("id", value.id());
      result.put("payrollAssignmentVersionId", value.payrollAssignmentVersionId());
      result.put("salaryAssignmentId", value.salaryAssignmentId());
      result.put("salaryStructureLineId", value.salaryStructureLineId());
      result.put("componentVersionId", value.componentVersionId());
      result.put("overrideKind", value.overrideKind());
      result.put("overrideValue", value.overrideValue());
      result.put("effectiveFrom", value.effectiveFrom());
      result.put("effectiveTo", value.effectiveTo());
      result.put("approvalStatus", value.approvalStatus());
      result.put("versionNo", value.versionNo());
    } else if (view instanceof PayrollLifecycleLineageView value) {
      result.put("id", value.id());
      result.put("eventType", value.eventType());
      result.put("relationshipDecision", value.relationshipDecision());
      result.put("predecessorRelationshipId", value.predecessorRelationshipId());
      result.put("successorRelationshipId", value.successorRelationshipId());
      result.put("predecessorAssignmentId", value.predecessorAssignmentId());
      result.put("successorAssignmentId", value.successorAssignmentId());
      result.put("effectiveDate", value.effectiveDate());
      result.put("approvalStatus", value.approvalStatus());
      result.put("versionNo", value.versionNo());
    }
    return result;
  }
}
