package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCaseView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingEventView;
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
public class EmployeePayrollOnboardingRepository {
  private final JdbcTemplate jdbc;

  public EmployeePayrollOnboardingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OnboardingCaseView create(
      UUID caseId, UUID relationshipId, String reason, String evidenceRef,
      String actor, Instant at) {
    affected(
        "select employee_payroll.create_payroll_onboarding_case(?,?,?,?,?,?,?)",
        "Payroll onboarding case could not be created",
        TenantContext.require(), caseId, relationshipId, reason, evidenceRef,
        actor, Timestamp.from(at));
    return get(caseId);
  }

  public OnboardingCaseView transition(
      UUID caseId, long expectedVersion, String targetStatus, String reason,
      String evidenceRef, String actor, Instant at, LocalDate asOf,
      boolean independentApproval) {
    affected(
        "select employee_payroll.transition_payroll_onboarding(?,?,?,?,?,?,?,?,?,?)",
        "Payroll onboarding state changed",
        TenantContext.require(), caseId, expectedVersion, targetStatus,
        reason, evidenceRef, actor, Timestamp.from(at), asOf,
        independentApproval);
    return get(caseId);
  }

  public OnboardingCaseView get(UUID caseId) {
    return jdbc.query(
            """
            select id,payroll_relationship_id,current_status,created_at,created_by,
                   updated_at,updated_by,version_no
              from employee_payroll.payroll_onboarding_case
             where tenant_id=? and id=?
            """,
            this::mapCase, TenantContext.require(), caseId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll onboarding case was not found"));
  }

  public OnboardingCaseView forRelationship(UUID relationshipId) {
    return jdbc.query(
            """
            select id,payroll_relationship_id,current_status,created_at,created_by,
                   updated_at,updated_by,version_no
              from employee_payroll.payroll_onboarding_case
             where tenant_id=? and payroll_relationship_id=?
            """,
            this::mapCase, TenantContext.require(), relationshipId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll onboarding case was not found"));
  }

  public List<OnboardingCaseView> cases(String status) {
    String sql = """
        select id,payroll_relationship_id,current_status,created_at,created_by,
               updated_at,updated_by,version_no
          from employee_payroll.payroll_onboarding_case
         where tenant_id=?
        """ + (status == null || status.isBlank() ? "" : " and current_status=?")
        + " order by updated_at desc,id";
    if (status == null || status.isBlank()) {
      return jdbc.query(sql, this::mapCase, TenantContext.require());
    }
    return jdbc.query(sql, this::mapCase, TenantContext.require(), status);
  }

  public List<OnboardingEventView> history(UUID caseId) {
    get(caseId);
    return jdbc.query(
        """
        select id,onboarding_case_id,payroll_relationship_id,event_sequence,
               from_status,to_status,reason,evidence_ref,occurred_at,actor
          from employee_payroll.payroll_onboarding_event
         where tenant_id=? and onboarding_case_id=?
         order by event_sequence
        """,
        this::mapEvent, TenantContext.require(), caseId);
  }

  private OnboardingCaseView mapCase(ResultSet rs, int row) throws SQLException {
    return new OnboardingCaseView(
        rs.getObject("id", UUID.class),
        rs.getObject("payroll_relationship_id", UUID.class),
        rs.getString("current_status"),
        instant(rs, "created_at"),
        rs.getString("created_by"),
        instant(rs, "updated_at"),
        rs.getString("updated_by"),
        rs.getLong("version_no"));
  }

  private OnboardingEventView mapEvent(ResultSet rs, int row) throws SQLException {
    return new OnboardingEventView(
        rs.getObject("id", UUID.class),
        rs.getObject("onboarding_case_id", UUID.class),
        rs.getObject("payroll_relationship_id", UUID.class),
        rs.getInt("event_sequence"),
        rs.getString("from_status"), rs.getString("to_status"),
        rs.getString("reason"), rs.getString("evidence_ref"),
        instant(rs, "occurred_at"), rs.getString("actor"));
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private void affected(String sql, String message, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    if (value == null || value != 1L) {
      throw new ConflictException(message);
    }
  }
}
