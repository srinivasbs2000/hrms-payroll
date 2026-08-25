package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldWriteRequest;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeePayrollHoldRepository {
  private final JdbcTemplate jdbc;

  public EmployeePayrollHoldRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollHoldView create(
      UUID relationshipId, PayrollHoldWriteRequest request,
      String actor, Instant at) {
    Long affected = jdbc.queryForObject(
        "select employee_payroll.create_payroll_hold_version(?,?,?,?,?,?,?,?,?,?,?,?)",
        Long.class, TenantContext.require(), request.holdId(), request.versionId(),
        relationshipId, String.join(",", request.scopes()), request.reasonCode(),
        request.reason(), request.sourceReference(), request.effectiveFrom(),
        request.effectiveTo(), actor, Timestamp.from(at));
    requireOne(affected, "Payroll hold draft could not be created");
    return version(request.versionId());
  }

  public PayrollHoldView approve(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    Long affected = jdbc.queryForObject(
        "select employee_payroll.approve_payroll_hold_version(?,?,?,?,?,?)",
        Long.class, TenantContext.require(), versionId, expectedVersion,
        actor, evidenceRef, Timestamp.from(at));
    requireOne(affected, "Payroll hold approval state changed");
    return version(versionId);
  }

  public PayrollHoldView release(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    Long affected = jdbc.queryForObject(
        "select employee_payroll.release_payroll_hold_version(?,?,?,?,?,?)",
        Long.class, TenantContext.require(), versionId, expectedVersion,
        actor, evidenceRef, Timestamp.from(at));
    requireOne(affected, "Payroll hold release state changed");
    return version(versionId);
  }

  public PayrollHoldView version(UUID versionId) {
    return jdbc.query(
            HOLD_SELECT + " where version.tenant_id=? and version.id=?"
                + " group by hold.id,version.id",
            this::map, TenantContext.require(), versionId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll hold version was not found"));
  }

  public List<PayrollHoldView> holds(UUID relationshipId, LocalDate asOf) {
    String effective = asOf == null ? "" : " and version.effective_from<=?"
        + " and (version.effective_to is null or ?<version.effective_to)";
    String sql = HOLD_SELECT
        + " where hold.tenant_id=? and hold.payroll_relationship_id=?" + effective
        + " group by hold.id,version.id order by version.created_at desc,version.id";
    if (asOf == null) {
      return jdbc.query(sql, this::map, TenantContext.require(), relationshipId);
    }
    return jdbc.query(sql, this::map, TenantContext.require(), relationshipId, asOf, asOf);
  }

  private static final String HOLD_SELECT = """
      select hold.id hold_id,version.id version_id,hold.payroll_relationship_id,
             version.version_sequence,version.reason_code,version.reason,
             version.source_reference,version.effective_from,version.effective_to,
             version.lifecycle_status,version.approved_at,version.approved_by,
             version.released_at,version.released_by,version.version_no,
             coalesce(string_agg(scope.scope,',' order by scope.scope),'') scopes
        from employee_payroll.payroll_hold hold
        join employee_payroll.payroll_hold_version version
          on version.tenant_id=hold.tenant_id and version.payroll_hold_id=hold.id
        left join employee_payroll.payroll_hold_scope scope
          on scope.tenant_id=version.tenant_id
         and scope.payroll_hold_version_id=version.id
      """;

  private PayrollHoldView map(ResultSet rs, int row) throws SQLException {
    String scopes = rs.getString("scopes");
    List<String> values = scopes == null || scopes.isBlank()
        ? List.of() : Arrays.asList(scopes.split(","));
    return new PayrollHoldView(
        rs.getObject("hold_id", UUID.class), rs.getObject("version_id", UUID.class),
        rs.getObject("payroll_relationship_id", UUID.class),
        rs.getInt("version_sequence"), rs.getString("reason_code"),
        rs.getString("reason"), rs.getString("source_reference"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"), instant(rs, "approved_at"),
        rs.getString("approved_by"), instant(rs, "released_at"),
        rs.getString("released_by"), rs.getLong("version_no"), values);
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private void requireOne(Long value, String message) {
    if (value == null || value != 1L) {
      throw new ConflictException(message);
    }
  }
}
