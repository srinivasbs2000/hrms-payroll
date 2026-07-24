package com.acme.hrms.payroll.payrolloperations.internal.infrastructure;

import com.acme.hrms.payroll.payrolloperations.PayrollInputSealResult;
import com.acme.hrms.payroll.payrolloperations.PayrollInputSnapshotDetailView;
import com.acme.hrms.payroll.payrolloperations.PayrollInputSnapshotView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PayrollInputSnapshotRepository {
  private static final String SNAPSHOT_SELECT = """
      select snapshot.id,
             snapshot.payroll_cycle_id,
             snapshot.payroll_assignment_version_id,
             assignment_identity.assignment_number,
             snapshot.payroll_relationship_version_id,
             relationship_identity.employee_number,
             snapshot.population_resolution_id,
             snapshot.population_member_id,
             snapshot.population_decision_id,
             snapshot.employee_payroll_profile_id,
             snapshot.pay_group_assignment_id,
             snapshot.salary_assignment_id,
             snapshot.salary_structure_version_id,
             snapshot.payload_schema_version,
             snapshot.snapshot_hash,
             snapshot.snapshot_payload::text snapshot_payload,
             snapshot.sealed_at,
             snapshot.created_by sealed_by
      from payroll_ops.input_snapshot snapshot
      join employee_payroll.payroll_assignment_version assignment_version
        on assignment_version.tenant_id = snapshot.tenant_id
       and assignment_version.id = snapshot.payroll_assignment_version_id
      join employee_payroll.payroll_assignment assignment_identity
        on assignment_identity.tenant_id = assignment_version.tenant_id
       and assignment_identity.id = assignment_version.payroll_assignment_id
      join employee_payroll.payroll_relationship_version relationship_version
        on relationship_version.tenant_id = snapshot.tenant_id
       and relationship_version.id = snapshot.payroll_relationship_version_id
      join employee_payroll.payroll_relationship relationship_identity
        on relationship_identity.tenant_id = relationship_version.tenant_id
       and relationship_identity.id =
           relationship_version.payroll_relationship_id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public PayrollInputSnapshotRepository(
      JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public PayrollInputSealResult seal(
      UUID cycleId,
      long expectedVersion,
      String actor,
      Instant sealedAt) {
    try {
      return jdbc.query(
              """
              select snapshot_count,
                     combined_hash,
                     cycle_version_no
              from payroll_ops.seal_payroll_inputs(?,?,?,?,?)
              """,
              (result, row) -> new PayrollInputSealResult(
                  cycleId,
                  result.getInt("snapshot_count"),
                  result.getString("combined_hash"),
                  result.getLong("cycle_version_no"),
                  sealedAt),
              TenantContext.require(),
              cycleId,
              expectedVersion,
              actor,
              Timestamp.from(sealedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Input sealing returned no result"));
    } catch (DataAccessException exception) {
      throw translate("Payroll inputs could not be sealed", exception);
    }
  }

  public List<PayrollInputSnapshotView> list(UUID cycleId) {
    return jdbc.query(
        SNAPSHOT_SELECT
            + " where snapshot.tenant_id=?"
            + " and snapshot.payroll_cycle_id=?"
            + " order by relationship_identity.employee_number,"
            + " assignment_identity.assignment_number",
        this::mapSummary,
        TenantContext.require(),
        cycleId);
  }

  public PayrollInputSnapshotDetailView get(
      UUID cycleId, UUID snapshotId) {
    return jdbc.query(
            SNAPSHOT_SELECT
                + " where snapshot.tenant_id=?"
                + " and snapshot.payroll_cycle_id=?"
                + " and snapshot.id=?",
            this::mapDetail,
            TenantContext.require(),
            cycleId,
            snapshotId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll input snapshot was not found"));
  }

  private PayrollInputSnapshotView mapSummary(ResultSet result, int row)
      throws SQLException {
    return new PayrollInputSnapshotView(
        result.getObject("id", UUID.class),
        result.getObject("payroll_cycle_id", UUID.class),
        result.getObject("payroll_assignment_version_id", UUID.class),
        result.getString("assignment_number"),
        result.getString("employee_number"),
        result.getObject("population_resolution_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getShort("payload_schema_version"),
        result.getString("snapshot_hash"),
        result.getTimestamp("sealed_at").toInstant(),
        result.getString("sealed_by"));
  }

  private PayrollInputSnapshotDetailView mapDetail(
      ResultSet result, int row) throws SQLException {
    return new PayrollInputSnapshotDetailView(
        result.getObject("id", UUID.class),
        result.getObject("payroll_cycle_id", UUID.class),
        result.getObject("payroll_assignment_version_id", UUID.class),
        result.getString("assignment_number"),
        result.getObject("payroll_relationship_version_id", UUID.class),
        result.getString("employee_number"),
        result.getObject("population_resolution_id", UUID.class),
        result.getObject("population_member_id", UUID.class),
        result.getObject("population_decision_id", UUID.class),
        result.getObject("employee_payroll_profile_id", UUID.class),
        result.getObject("pay_group_assignment_id", UUID.class),
        result.getObject("salary_assignment_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getShort("payload_schema_version"),
        result.getString("snapshot_hash"),
        payload(result),
        result.getTimestamp("sealed_at").toInstant(),
        result.getString("sealed_by"));
  }

  private JsonNode payload(ResultSet result) throws SQLException {
    try {
      return objectMapper.readTree(result.getString("snapshot_payload"));
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "Stored payroll input snapshot payload is invalid", exception);
    }
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

  private String databaseMessage(
      String fallback, SQLException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return fallback;
    }
    int lineBreak = message.indexOf('\n');
    return lineBreak < 0 ? message : message.substring(0, lineBreak);
  }
}
