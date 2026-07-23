package com.acme.hrms.payroll.payrolloperations.internal.infrastructure;

import com.acme.hrms.payroll.payrolloperations.PayrollCycleCreateRequest;
import com.acme.hrms.payroll.payrolloperations.PayrollCycleView;
import com.acme.hrms.payroll.payrolloperations.PopulationDecisionView;
import com.acme.hrms.payroll.payrolloperations.PopulationMemberView;
import com.acme.hrms.payroll.payrolloperations.PopulationResolutionResult;
import com.acme.hrms.payroll.payrolloperations.PopulationResolutionView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollOperationsRepository {
  private static final String CYCLE_SELECT = """
      select cycle.id,
             cycle.pay_group_id pay_group_version_id,
             group_identity.code pay_group_code,
             group_version.name pay_group_name,
             cycle.pay_period_id,
             period.period_code,
             period.period_start,
             period.period_end,
             period.payment_date,
             cycle.cycle_type,
             cycle.status::text status,
             cycle.active_population_resolution_id,
             cycle.control_total,
             cycle.version_no
      from payroll_ops.payroll_cycle cycle
      join organisation.pay_group_version group_version
        on group_version.tenant_id = cycle.tenant_id
       and group_version.id = cycle.pay_group_id
      join organisation.pay_group group_identity
        on group_identity.tenant_id = group_version.tenant_id
       and group_identity.id = group_version.pay_group_id
      join organisation.pay_period period
        on period.tenant_id = cycle.tenant_id
       and period.id = cycle.pay_period_id
      """;

  private final JdbcTemplate jdbc;

  public PayrollOperationsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollCycleView createCycle(
      PayrollCycleCreateRequest request, String actor, Instant createdAt) {
    try {
      UUID cycleId = jdbc.queryForObject(
          "select payroll_ops.create_regular_payroll_cycle(?,?,?,?,?)",
          UUID.class,
          TenantContext.require(),
          request.payGroupVersionId(),
          request.payPeriodId(),
          actor,
          Timestamp.from(createdAt));
      if (cycleId == null) {
        throw new IllegalStateException(
            "Controlled payroll-cycle creation returned no identifier");
      }
      return cycle(cycleId);
    } catch (DataAccessException exception) {
      throw translate("Payroll cycle could not be created", exception);
    }
  }

  public PopulationResolutionResult resolvePopulation(
      UUID cycleId,
      long expectedVersion,
      String actor,
      Instant resolvedAt) {
    try {
      return jdbc.query(
              """
              select resolution_id,
                     attempt_no,
                     included_count,
                     excluded_count,
                     cycle_version_no
              from payroll_ops.resolve_payroll_population(?,?,?,?,?)
              """,
              (result, row) -> new PopulationResolutionResult(
                  result.getObject("resolution_id", UUID.class),
                  cycleId,
                  result.getInt("attempt_no"),
                  result.getInt("included_count"),
                  result.getInt("excluded_count"),
                  result.getLong("cycle_version_no")),
              TenantContext.require(),
              cycleId,
              expectedVersion,
              actor,
              Timestamp.from(resolvedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Population resolution returned no result"));
    } catch (DataAccessException exception) {
      throw translate("Payroll population could not be resolved", exception);
    }
  }

  public PayrollCycleView cycle(UUID cycleId) {
    return jdbc.query(
            CYCLE_SELECT + " where cycle.tenant_id=? and cycle.id=?",
            this::mapCycle,
            TenantContext.require(),
            cycleId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll cycle was not found"));
  }

  public List<PayrollCycleView> cycles() {
    return jdbc.query(
        CYCLE_SELECT
            + " where cycle.tenant_id=?"
            + " order by period.period_start desc, cycle.created_at desc",
        this::mapCycle,
        TenantContext.require());
  }

  public List<PopulationMemberView> population(UUID cycleId) {
    cycle(cycleId);
    return jdbc.query(
        """
        select member.id,
               member.payroll_cycle_id,
               member.population_resolution_id,
               member.payroll_assignment_version_id,
               assignment_identity.assignment_number,
               member.payroll_relationship_version_id,
               relationship_identity.employee_number,
               member.employee_payroll_profile_id,
               member.pay_group_assignment_id,
               member.salary_assignment_id,
               member.inclusion_reason,
               member.status
        from payroll_ops.population_member member
        join employee_payroll.payroll_assignment_version assignment_version
          on assignment_version.tenant_id = member.tenant_id
         and assignment_version.id = member.payroll_assignment_version_id
        join employee_payroll.payroll_assignment assignment_identity
          on assignment_identity.tenant_id = assignment_version.tenant_id
         and assignment_identity.id = assignment_version.payroll_assignment_id
        join employee_payroll.payroll_relationship_version relationship_version
          on relationship_version.tenant_id = member.tenant_id
         and relationship_version.id = member.payroll_relationship_version_id
        join employee_payroll.payroll_relationship relationship_identity
          on relationship_identity.tenant_id = relationship_version.tenant_id
         and relationship_identity.id =
             relationship_version.payroll_relationship_id
        where member.tenant_id=?
          and member.payroll_cycle_id=?
        order by relationship_identity.employee_number,
                 assignment_identity.assignment_number
        """,
        (result, row) -> new PopulationMemberView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("population_resolution_id", UUID.class),
            result.getObject("payroll_assignment_version_id", UUID.class),
            result.getString("assignment_number"),
            result.getObject("payroll_relationship_version_id", UUID.class),
            result.getString("employee_number"),
            result.getObject("employee_payroll_profile_id", UUID.class),
            result.getObject("pay_group_assignment_id", UUID.class),
            result.getObject("salary_assignment_id", UUID.class),
            result.getString("inclusion_reason"),
            result.getString("status")),
        TenantContext.require(),
        cycleId);
  }

  public List<PopulationResolutionView> resolutions(UUID cycleId) {
    cycle(cycleId);
    return jdbc.query(
        """
        select id,
               payroll_cycle_id,
               attempt_no,
               status,
               included_count,
               excluded_count,
               resolved_at,
               resolved_by,
               version_no
        from payroll_ops.population_resolution
        where tenant_id=?
          and payroll_cycle_id=?
        order by attempt_no desc
        """,
        (result, row) -> new PopulationResolutionView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getInt("attempt_no"),
            result.getString("status"),
            result.getInt("included_count"),
            result.getInt("excluded_count"),
            result.getTimestamp("resolved_at").toInstant(),
            result.getString("resolved_by"),
            result.getLong("version_no")),
        TenantContext.require(),
        cycleId);
  }

  public List<PopulationDecisionView> decisions(
      UUID cycleId, UUID resolutionId) {
    PayrollCycleView cycle = cycle(cycleId);
    UUID selected = resolutionId == null
        ? cycle.activePopulationResolutionId()
        : resolutionId;
    if (selected == null) {
      return List.of();
    }

    return jdbc.query(
        """
        select decision.id,
               decision.population_resolution_id,
               decision.payroll_cycle_id,
               decision.payroll_assignment_version_id,
               assignment_identity.assignment_number,
               decision.payroll_relationship_version_id,
               relationship_identity.employee_number,
               decision.employee_payroll_profile_id,
               decision.pay_group_assignment_id,
               decision.salary_assignment_id,
               decision.salary_structure_version_id,
               decision.decision,
               decision.reason_code,
               decision.reason_detail
        from payroll_ops.population_decision decision
        join employee_payroll.payroll_assignment_version assignment_version
          on assignment_version.tenant_id = decision.tenant_id
         and assignment_version.id =
             decision.payroll_assignment_version_id
        join employee_payroll.payroll_assignment assignment_identity
          on assignment_identity.tenant_id = assignment_version.tenant_id
         and assignment_identity.id = assignment_version.payroll_assignment_id
        join employee_payroll.payroll_relationship_version relationship_version
          on relationship_version.tenant_id = decision.tenant_id
         and relationship_version.id =
             decision.payroll_relationship_version_id
        join employee_payroll.payroll_relationship relationship_identity
          on relationship_identity.tenant_id = relationship_version.tenant_id
         and relationship_identity.id =
             relationship_version.payroll_relationship_id
        where decision.tenant_id=?
          and decision.payroll_cycle_id=?
          and decision.population_resolution_id=?
        order by decision.decision,
                 relationship_identity.employee_number,
                 assignment_identity.assignment_number
        """,
        (result, row) -> new PopulationDecisionView(
            result.getObject("id", UUID.class),
            result.getObject("population_resolution_id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("payroll_assignment_version_id", UUID.class),
            result.getString("assignment_number"),
            result.getObject("payroll_relationship_version_id", UUID.class),
            result.getString("employee_number"),
            result.getObject("employee_payroll_profile_id", UUID.class),
            result.getObject("pay_group_assignment_id", UUID.class),
            result.getObject("salary_assignment_id", UUID.class),
            result.getObject("salary_structure_version_id", UUID.class),
            result.getString("decision"),
            result.getString("reason_code"),
            result.getString("reason_detail")),
        TenantContext.require(),
        cycleId,
        selected);
  }

  private PayrollCycleView mapCycle(ResultSet result, int row)
      throws SQLException {
    return new PayrollCycleView(
        result.getObject("id", UUID.class),
        result.getObject("pay_group_version_id", UUID.class),
        result.getString("pay_group_code"),
        result.getString("pay_group_name"),
        result.getObject("pay_period_id", UUID.class),
        result.getString("period_code"),
        result.getObject("period_start", java.time.LocalDate.class),
        result.getObject("period_end", java.time.LocalDate.class),
        result.getObject("payment_date", java.time.LocalDate.class),
        result.getString("cycle_type"),
        result.getString("status"),
        result.getObject("active_population_resolution_id", UUID.class),
        result.getBigDecimal("control_total"),
        result.getLong("version_no"));
  }

  private RuntimeException translate(
      String operation, DataAccessException exception) {
    SQLException sql = sqlException(exception);
    if (sql == null || sql.getSQLState() == null) {
      return exception;
    }

    String message = databaseMessage(operation, sql);
    return switch (sql.getSQLState()) {
      case "23505", "40001" -> new ConflictException(message, exception);
      case "23503" -> new ResourceNotFoundException(message);
      case "23514" -> new IllegalArgumentException(message, exception);
      case "42501" -> new AccessDeniedException(message, exception);
      default -> exception;
    };
  }

  private SQLException sqlException(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof SQLException sql) {
        return sql;
      }
      current = current.getCause();
    }
    return null;
  }

  private String databaseMessage(String fallback, SQLException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return fallback;
    }
    int lineBreak = message.indexOf('\n');
    return lineBreak < 0 ? message : message.substring(0, lineBreak);
  }
}
