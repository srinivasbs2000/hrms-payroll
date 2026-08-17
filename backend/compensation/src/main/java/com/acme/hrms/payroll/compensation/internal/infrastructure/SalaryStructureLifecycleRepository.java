package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleView;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.WorkflowActionView;
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
import org.springframework.stereotype.Repository;

@Repository
public class SalaryStructureLifecycleRepository {
  private final JdbcTemplate jdbc;

  public SalaryStructureLifecycleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public LifecycleView lifecycle(UUID identityId, UUID versionId) {
    LifecycleView header = jdbc.query(
            """
            select identity.id identity_id,
                   version.id version_id,
                   version.version_no,
                   version.workflow_status,
                   version.approval_status,
                   version.submitted_at,
                   version.submitted_by,
                   version.approved_at,
                   version.approved_by,
                   version.published_at,
                   version.published_by,
                   version.configuration_hash,
                   version.validation_fingerprint,
                   coalesce(state.binding_revision,0) statutory_binding_revision,
                   version.workflow_status='PUBLISHED'
                     and version.effective_from<=current_date
                     and (version.effective_to is null or version.effective_to>current_date)
                     published_active
              from compensation.salary_structure identity
              join compensation.salary_structure_version version
                on version.tenant_id=identity.tenant_id
               and version.salary_structure_id=identity.id
              left join compensation.salary_structure_statutory_state state
                on state.tenant_id=version.tenant_id
               and state.salary_structure_version_id=version.id
             where identity.tenant_id=?
               and identity.id=?
               and version.id=?
            """,
            (result, row) -> mapHeader(result),
            TenantContext.require(),
            identityId,
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure lifecycle was not found"));

    List<WorkflowActionView> actions = jdbc.query(
        """
        select id,action_sequence,action_type,actor,occurred_at,comment,
               configuration_hash,validation_fingerprint,
               statutory_binding_revision,statutory_evidence_hash,
               structure_version_no,action_hash
          from compensation.salary_structure_workflow_action
         where tenant_id=?
           and salary_structure_version_id=?
         order by action_sequence
        """,
        this::mapAction,
        TenantContext.require(),
        versionId);

    return new LifecycleView(
        header.identityId(),
        header.versionId(),
        header.versionNo(),
        header.workflowStatus(),
        header.approvalStatus(),
        header.publishedActive(),
        header.submittedAt(),
        header.submittedBy(),
        header.approvedAt(),
        header.approvedBy(),
        header.publishedAt(),
        header.publishedBy(),
        header.configurationHash(),
        header.validationFingerprint(),
        header.statutoryBindingRevision(),
        actions);
  }

  public LifecycleView submit(
      UUID identityId,
      UUID versionId,
      long expectedVersion,
      String comment,
      String actor,
      Instant changedAt) {
    invoke(
        """
        select compensation.submit_salary_structure_version(
          ?,?,?,?,?,?,?
        )
        """,
        "Salary structure cannot be submitted with stale or incomplete evidence",
        TenantContext.require(),
        identityId,
        versionId,
        expectedVersion,
        comment,
        actor,
        Timestamp.from(changedAt));
    return lifecycle(identityId, versionId);
  }

  public LifecycleView reject(
      UUID identityId,
      UUID versionId,
      long expectedVersion,
      String reason,
      String actor,
      Instant changedAt) {
    invoke(
        """
        select compensation.reject_salary_structure_submission(
          ?,?,?,?,?,?,?
        )
        """,
        "Salary-structure submission changed or cannot be rejected",
        TenantContext.require(),
        identityId,
        versionId,
        expectedVersion,
        reason,
        actor,
        Timestamp.from(changedAt));
    return lifecycle(identityId, versionId);
  }

  public LifecycleView publish(
      UUID identityId,
      UUID versionId,
      long expectedVersion,
      String comment,
      String actor,
      Instant changedAt) {
    invoke(
        """
        select compensation.publish_salary_structure_version(
          ?,?,?,?,?,?,?
        )
        """,
        "Salary-structure approval changed or cannot be published",
        TenantContext.require(),
        identityId,
        versionId,
        expectedVersion,
        comment,
        actor,
        Timestamp.from(changedAt));
    return lifecycle(identityId, versionId);
  }

  private void invoke(String sql, String message, Object... arguments) {
    try {
      Long affected = jdbc.queryForObject(sql, Long.class, arguments);
      if (affected == null || affected != 1L) {
        throw new ConflictException(message);
      }
    } catch (DataAccessException exception) {
      throw new ConflictException(message, exception);
    }
  }

  private LifecycleView mapHeader(ResultSet result) throws SQLException {
    return new LifecycleView(
        result.getObject("identity_id", UUID.class),
        result.getObject("version_id", UUID.class),
        result.getLong("version_no"),
        result.getString("workflow_status"),
        result.getString("approval_status"),
        result.getBoolean("published_active"),
        instant(result, "submitted_at"),
        result.getString("submitted_by"),
        instant(result, "approved_at"),
        result.getString("approved_by"),
        instant(result, "published_at"),
        result.getString("published_by"),
        result.getString("configuration_hash"),
        result.getString("validation_fingerprint"),
        result.getLong("statutory_binding_revision"),
        List.of());
  }

  private WorkflowActionView mapAction(ResultSet result, int row)
      throws SQLException {
    return new WorkflowActionView(
        result.getObject("id", UUID.class),
        result.getInt("action_sequence"),
        result.getString("action_type"),
        result.getString("actor"),
        result.getTimestamp("occurred_at").toInstant(),
        result.getString("comment"),
        result.getString("configuration_hash"),
        result.getString("validation_fingerprint"),
        result.getLong("statutory_binding_revision"),
        result.getString("statutory_evidence_hash"),
        result.getLong("structure_version_no"),
        result.getString("action_hash"));
  }

  private Instant instant(ResultSet result, String column) throws SQLException {
    Timestamp value = result.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
