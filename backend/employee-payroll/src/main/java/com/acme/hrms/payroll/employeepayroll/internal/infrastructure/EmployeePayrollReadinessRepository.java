package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessFindingView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyWriteRequest;
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
public class EmployeePayrollReadinessRepository {
  private final JdbcTemplate jdbc;

  public EmployeePayrollReadinessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReadinessFindingView> findings(
      UUID relationshipId, String currencyCode, LocalDate asOf) {
    return jdbc.query(
        "select * from employee_payroll.payroll_readiness_findings(?,?,?,?)",
        this::mapFinding, TenantContext.require(), relationshipId,
        currencyCode, asOf);
  }

  public ReadinessPolicyView createPolicy(
      ReadinessPolicyWriteRequest request, String actor, Instant at) {
    Long affected = jdbc.queryForObject(
        "select employee_payroll.create_payroll_readiness_policy_version(?,?,?,?,?,?,?,?,?,?,?)",
        Long.class, TenantContext.require(), request.versionId(),
        request.dimension(), request.applicability(), request.severity(),
        request.evidenceRef(), request.reason(), request.effectiveFrom(),
        request.effectiveTo(), actor, Timestamp.from(at));
    if (affected == null || affected != 1L) {
      throw new ConflictException("Readiness policy version could not be created");
    }
    return policy(request.versionId());
  }

  public ReadinessPolicyView policy(UUID id) {
    return jdbc.query(
            POLICY_SELECT + " where tenant_id=? and id=?",
            this::mapPolicy, TenantContext.require(), id)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Readiness policy version was not found"));
  }

  public List<ReadinessPolicyView> policies(String dimension) {
    String sql = POLICY_SELECT + " where tenant_id=?"
        + (dimension == null || dimension.isBlank() ? "" : " and dimension=?")
        + " order by dimension,version_sequence desc";
    if (dimension == null || dimension.isBlank()) {
      return jdbc.query(sql, this::mapPolicy, TenantContext.require());
    }
    return jdbc.query(sql, this::mapPolicy, TenantContext.require(), dimension);
  }

  private static final String POLICY_SELECT = """
      select id,dimension,version_sequence,applicability,severity,evidence_ref,
             reason,effective_from,effective_to,supersedes_version_id,
             approved_at,approved_by
        from employee_payroll.payroll_readiness_policy_version
      """;

  private ReadinessFindingView mapFinding(ResultSet rs, int row) throws SQLException {
    return new ReadinessFindingView(
        rs.getString("dimension"), rs.getString("severity"),
        rs.getString("status"), rs.getString("finding_code"),
        rs.getString("detail"), rs.getString("source_kind"),
        rs.getString("source_reference"));
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private ReadinessPolicyView mapPolicy(ResultSet rs, int row) throws SQLException {
    return new ReadinessPolicyView(
        rs.getObject("id", UUID.class), rs.getString("dimension"),
        rs.getInt("version_sequence"), rs.getString("applicability"),
        rs.getString("severity"), rs.getString("evidence_ref"),
        rs.getString("reason"), rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getObject("supersedes_version_id", UUID.class),
        instant(rs, "approved_at"), rs.getString("approved_by"));
  }
}
