package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.CompensationChangeImpactView;
import com.acme.hrms.payroll.employeepayroll.CompensationChangeView;
import com.acme.hrms.payroll.employeepayroll.CompensationChangeWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeComponentOverrideView;
import com.acme.hrms.payroll.employeepayroll.EmployeeComponentOverrideWriteRequest;
import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentImpactView;
import com.acme.hrms.payroll.employeepayroll.PayrollLifecycleLineageView;
import com.acme.hrms.payroll.employeepayroll.PayrollLifecycleLineageWriteRequest;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeePayrollBindingRepository {
  private final JdbcTemplate jdbc;

  public EmployeePayrollBindingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CompensationChangeView createCompensationChange(
      CompensationChangeWriteRequest request, String actor) {
    UUID id = UUID.randomUUID();
    UUID tenant = TenantContext.require();
    jdbc.update(
        """
        insert into employee_payroll.compensation_change_event(
          id,tenant_id,payroll_assignment_id,event_type,effective_date,
          source_event_id,reason,created_by,updated_by)
        values (?,?,?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        request.payrollAssignmentId(),
        request.eventType(),
        request.effectiveDate(),
        request.sourceEventId(),
        request.reason(),
        actor,
        actor);
    return compensationChange(id);
  }

  public CompensationChangeView compensationChange(UUID id) {
    return one(
        jdbc.query(
            """
            select e.id,e.payroll_assignment_id,e.event_type,e.effective_date,
                   e.source_event_id,e.reason,e.assessment_through,
                   e.impact_assessed_at,e.impact_assessed_by,
                   (select count(*) from employee_payroll.compensation_change_impact i
                     where i.tenant_id=e.tenant_id
                       and i.compensation_change_event_id=e.id) impacted_period_count,
                   e.approval_status,e.approved_at,e.approved_by,e.version_no
              from employee_payroll.compensation_change_event e
             where e.tenant_id=? and e.id=?
            """,
            this::mapCompensationChange,
            TenantContext.require(),
            id),
        "Compensation change not found");
  }

  public List<CompensationChangeView> compensationChanges(UUID assignmentId) {
    return jdbc.query(
        """
        select e.id,e.payroll_assignment_id,e.event_type,e.effective_date,
               e.source_event_id,e.reason,e.assessment_through,
               e.impact_assessed_at,e.impact_assessed_by,
               (select count(*) from employee_payroll.compensation_change_impact i
                 where i.tenant_id=e.tenant_id
                   and i.compensation_change_event_id=e.id) impacted_period_count,
               e.approval_status,e.approved_at,e.approved_by,e.version_no
          from employee_payroll.compensation_change_event e
         where e.tenant_id=? and e.payroll_assignment_id=?
         order by e.effective_date,e.created_at,e.id
        """,
        this::mapCompensationChange,
        TenantContext.require(),
        assignmentId);
  }

  public CompensationChangeView assessCompensationChange(
      UUID id, LocalDate through, String actor, Instant assessedAt) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.assess_compensation_change(?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            through,
            actor,
            Timestamp.from(assessedAt)),
        "Compensation change is not an assessable draft");
    return compensationChange(id);
  }

  public CompensationChangeView approveCompensationChange(
      UUID id, String actor, Instant approvedAt) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_compensation_change_event(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            actor,
            Timestamp.from(approvedAt)),
        "Compensation change is not approval-ready");
    return compensationChange(id);
  }

  public List<CompensationChangeImpactView> compensationChangeImpact(UUID id) {
    return jdbc.query(
        """
        select p.id,p.period_code,p.period_start,p.period_end,i.reason_code
          from employee_payroll.compensation_change_impact i
          join organisation.pay_period p
            on p.tenant_id=i.tenant_id and p.id=i.pay_period_id
         where i.tenant_id=? and i.compensation_change_event_id=?
         order by p.period_start,p.id
        """,
        (rs, n) -> new CompensationChangeImpactView(
            rs.getObject("id", UUID.class),
            rs.getString("period_code"),
            rs.getObject("period_start", LocalDate.class),
            rs.getObject("period_end", LocalDate.class),
            rs.getString("reason_code")),
        TenantContext.require(),
        id);
  }

  public EmployeeComponentOverrideView createOverride(
      EmployeeComponentOverrideWriteRequest request,
      UUID supersedes,
      String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.employee_component_override(
          id,tenant_id,payroll_assignment_version_id,salary_assignment_id,
          salary_structure_line_id,component_version_id,override_kind,
          override_value,effective_from,effective_to,supersedes_override_id,
          created_by,updated_by)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        TenantContext.require(),
        request.payrollAssignmentVersionId(),
        request.salaryAssignmentId(),
        request.salaryStructureLineId(),
        request.componentVersionId(),
        request.overrideKind(),
        request.overrideValue(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
    return componentOverride(id);
  }

  public EmployeeComponentOverrideView componentOverride(UUID id) {
    return one(
        jdbc.query(
            """
            select o.id,o.payroll_assignment_version_id,o.salary_assignment_id,
                   o.salary_structure_line_id,o.component_version_id,o.override_kind,
                   o.override_value,o.effective_from,o.effective_to,o.approval_status,
                   o.supersedes_override_id,
                   exists(select 1 from employee_payroll.employee_component_override s
                     where s.tenant_id=o.tenant_id and s.supersedes_override_id=o.id) superseded,
                   o.version_no
              from employee_payroll.employee_component_override o
             where o.tenant_id=? and o.id=?
            """,
            this::mapOverride,
            TenantContext.require(),
            id),
        "Employee component override not found");
  }

  public List<EmployeeComponentOverrideView> componentOverrides(UUID assignmentVersionId) {
    return jdbc.query(
        """
        select o.id,o.payroll_assignment_version_id,o.salary_assignment_id,
               o.salary_structure_line_id,o.component_version_id,o.override_kind,
               o.override_value,o.effective_from,o.effective_to,o.approval_status,
               o.supersedes_override_id,
               exists(select 1 from employee_payroll.employee_component_override s
                 where s.tenant_id=o.tenant_id and s.supersedes_override_id=o.id) superseded,
               o.version_no
          from employee_payroll.employee_component_override o
         where o.tenant_id=? and o.payroll_assignment_version_id=?
         order by o.effective_from,o.id
        """,
        this::mapOverride,
        TenantContext.require(),
        assignmentVersionId);
  }

  public EmployeeComponentOverrideView approveOverride(
      UUID id, String actor, Instant approvedAt) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_employee_component_override(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            actor,
            Timestamp.from(approvedAt)),
        "Employee component override is not approval-ready");
    return componentOverride(id);
  }

  public PayrollLifecycleLineageView createLineage(
      PayrollLifecycleLineageWriteRequest request, String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.payroll_lifecycle_lineage(
          id,tenant_id,event_type,relationship_decision,
          predecessor_relationship_id,successor_relationship_id,
          predecessor_assignment_id,successor_assignment_id,effective_date,
          reason,created_by,updated_by)
        values (?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        TenantContext.require(),
        request.eventType(),
        request.relationshipDecision(),
        request.predecessorRelationshipId(),
        request.successorRelationshipId(),
        request.predecessorAssignmentId(),
        request.successorAssignmentId(),
        request.effectiveDate(),
        request.reason(),
        actor,
        actor);
    return lineage(id);
  }

  public PayrollLifecycleLineageView lineage(UUID id) {
    return one(
        jdbc.query(
            """
            select id,event_type,relationship_decision,
                   predecessor_relationship_id,successor_relationship_id,
                   predecessor_assignment_id,successor_assignment_id,
                   effective_date,reason,approval_status,version_no
              from employee_payroll.payroll_lifecycle_lineage
             where tenant_id=? and id=?
            """,
            this::mapLineage,
            TenantContext.require(),
            id),
        "Payroll lifecycle lineage not found");
  }

  public List<PayrollLifecycleLineageView> lineageForRelationship(UUID relationshipId) {
    return jdbc.query(
        """
        select id,event_type,relationship_decision,
               predecessor_relationship_id,successor_relationship_id,
               predecessor_assignment_id,successor_assignment_id,
               effective_date,reason,approval_status,version_no
          from employee_payroll.payroll_lifecycle_lineage
         where tenant_id=? and
               (predecessor_relationship_id=? or successor_relationship_id=?)
         order by effective_date,id
        """,
        this::mapLineage,
        TenantContext.require(),
        relationshipId,
        relationshipId);
  }

  public PayrollLifecycleLineageView approveLineage(
      UUID id, String actor, Instant approvedAt) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_payroll_lifecycle_lineage(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            actor,
            Timestamp.from(approvedAt)),
        "Payroll lifecycle lineage is not approval-ready");
    return lineage(id);
  }

  public List<PayGroupAssignmentImpactView> payGroupImpact(UUID assignmentId) {
    return jdbc.query(
        """
        select p.id,p.period_code,p.period_start,p.period_end,i.reason_code
          from employee_payroll.pay_group_assignment_impact_period i
          join organisation.pay_period p
            on p.tenant_id=i.tenant_id and p.id=i.pay_period_id
         where i.tenant_id=? and i.pay_group_assignment_id=?
         order by p.period_start,p.id
        """,
        (rs, n) -> new PayGroupAssignmentImpactView(
            rs.getObject("id", UUID.class),
            rs.getString("period_code"),
            rs.getObject("period_start", LocalDate.class),
            rs.getObject("period_end", LocalDate.class),
            rs.getString("reason_code")),
        TenantContext.require(),
        assignmentId);
  }

  private CompensationChangeView mapCompensationChange(ResultSet rs, int row)
      throws SQLException {
    Timestamp assessed = rs.getTimestamp("impact_assessed_at");
    Timestamp approved = rs.getTimestamp("approved_at");
    return new CompensationChangeView(
        rs.getObject("id", UUID.class),
        rs.getObject("payroll_assignment_id", UUID.class),
        rs.getString("event_type"),
        rs.getObject("effective_date", LocalDate.class),
        rs.getObject("source_event_id", UUID.class),
        rs.getString("reason"),
        rs.getObject("assessment_through", LocalDate.class),
        assessed == null ? null : assessed.toInstant(),
        rs.getString("impact_assessed_by"),
        rs.getInt("impacted_period_count"),
        rs.getString("approval_status"),
        approved == null ? null : approved.toInstant(),
        rs.getString("approved_by"),
        rs.getLong("version_no"));
  }

  private EmployeeComponentOverrideView mapOverride(ResultSet rs, int row)
      throws SQLException {
    return new EmployeeComponentOverrideView(
        rs.getObject("id", UUID.class),
        rs.getObject("payroll_assignment_version_id", UUID.class),
        rs.getObject("salary_assignment_id", UUID.class),
        rs.getObject("salary_structure_line_id", UUID.class),
        rs.getObject("component_version_id", UUID.class),
        rs.getString("override_kind"),
        rs.getBigDecimal("override_value"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("approval_status"),
        rs.getObject("supersedes_override_id", UUID.class),
        rs.getBoolean("superseded"),
        rs.getLong("version_no"));
  }

  private PayrollLifecycleLineageView mapLineage(ResultSet rs, int row)
      throws SQLException {
    return new PayrollLifecycleLineageView(
        rs.getObject("id", UUID.class),
        rs.getString("event_type"),
        rs.getString("relationship_decision"),
        rs.getObject("predecessor_relationship_id", UUID.class),
        rs.getObject("successor_relationship_id", UUID.class),
        rs.getObject("predecessor_assignment_id", UUID.class),
        rs.getObject("successor_assignment_id", UUID.class),
        rs.getObject("effective_date", LocalDate.class),
        rs.getString("reason"),
        rs.getString("approval_status"),
        rs.getLong("version_no"));
  }

  private <T> T one(List<T> values, String message) {
    if (values.isEmpty()) {
      throw new ResourceNotFoundException(message);
    }
    return values.getFirst();
  }

  private void requireOne(Long affected, String message) {
    if (affected == null || affected != 1L) {
      throw new ConflictException(message);
    }
  }
}
