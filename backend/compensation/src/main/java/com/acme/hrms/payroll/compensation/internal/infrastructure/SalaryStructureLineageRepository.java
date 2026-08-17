package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.AuditEvidenceView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.DomainEventEvidenceView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.StatutoryEvidenceView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.ValidationEvidenceView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.WorkflowEvidenceView;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalaryStructureLineageRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public SalaryStructureLineageRepository(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Snapshot snapshot(UUID identityId, UUID versionId) {
    Snapshot header = jdbc.query(
            """
            select identity.id identity_id,
                   version.id version_id,
                   version.version_no,
                   version.structure_schema_version,
                   version.workflow_status,
                   version.approval_status,
                   version.effective_from,
                   version.effective_to,
                   version.configuration_hash,
                   version.validation_fingerprint,
                   coalesce(state.binding_revision,0)
                     statutory_binding_revision,
                   (
                     select evaluation.evidence_hash
                       from compensation.salary_structure_statutory_evaluation
                            evaluation
                      where evaluation.tenant_id=version.tenant_id
                        and evaluation.salary_structure_version_id=version.id
                        and evaluation.statutory_binding_revision=
                            coalesce(state.binding_revision,0)
                      order by evaluation.created_at desc,evaluation.id desc
                      limit 1
                   ) current_statutory_evidence_hash
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
            (result, row) -> new Snapshot(
                result.getObject("identity_id", UUID.class),
                result.getObject("version_id", UUID.class),
                result.getLong("version_no"),
                result.getShort("structure_schema_version"),
                result.getString("workflow_status"),
                result.getString("approval_status"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class),
                result.getString("configuration_hash"),
                result.getString("validation_fingerprint"),
                result.getLong("statutory_binding_revision"),
                result.getString("current_statutory_evidence_hash"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            TenantContext.require(),
            identityId,
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure lineage version was not found"));

    return header.withEvidence(
        validations(versionId),
        statutoryEvaluations(versionId),
        workflowActions(versionId),
        auditEvents(identityId, versionId),
        domainEvents(identityId, versionId));
  }

  private List<ValidationEvidenceView> validations(UUID versionId) {
    return jdbc.query(
        """
        select id,
               validation_status,
               request_hash,
               configuration_hash,
               result_hash,
               blocking_error_count,
               warning_count,
               created_at,
               created_by
          from compensation.salary_structure_validation
         where tenant_id=?
           and salary_structure_version_id=?
         order by created_at,id
        """,
        (result, row) -> new ValidationEvidenceView(
            result.getObject("id", UUID.class),
            result.getString("validation_status"),
            result.getString("request_hash"),
            result.getString("configuration_hash"),
            result.getString("result_hash"),
            result.getInt("blocking_error_count"),
            result.getInt("warning_count"),
            result.getTimestamp("created_at").toInstant(),
            result.getString("created_by")),
        TenantContext.require(),
        versionId);
  }

  private List<StatutoryEvidenceView> statutoryEvaluations(UUID versionId) {
    return jdbc.query(
        """
        select id,
               validation_id,
               statutory_binding_revision,
               validation_status,
               blocking_issue_count,
               advisory_issue_count,
               evidence_hash,
               created_at,
               created_by
          from compensation.salary_structure_statutory_evaluation
         where tenant_id=?
           and salary_structure_version_id=?
         order by statutory_binding_revision,created_at,id
        """,
        (result, row) -> new StatutoryEvidenceView(
            result.getObject("id", UUID.class),
            result.getObject("validation_id", UUID.class),
            result.getLong("statutory_binding_revision"),
            result.getString("validation_status"),
            result.getInt("blocking_issue_count"),
            result.getInt("advisory_issue_count"),
            result.getString("evidence_hash"),
            result.getTimestamp("created_at").toInstant(),
            result.getString("created_by")),
        TenantContext.require(),
        versionId);
  }

  private List<WorkflowEvidenceView> workflowActions(UUID versionId) {
    return jdbc.query(
        """
        select id,
               action_sequence,
               action_type,
               actor,
               occurred_at,
               comment,
               configuration_hash,
               validation_fingerprint,
               statutory_binding_revision,
               statutory_evidence_hash,
               structure_version_no,
               action_hash
          from compensation.salary_structure_workflow_action
         where tenant_id=?
           and salary_structure_version_id=?
         order by action_sequence
        """,
        (result, row) -> new WorkflowEvidenceView(
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
            result.getString("action_hash")),
        TenantContext.require(),
        versionId);
  }

  private List<AuditEvidenceView> auditEvents(
      UUID identityId,
      UUID versionId) {
    String version = versionId.toString();
    return jdbc.query(
        """
        select id,
               occurred_at,
               actor,
               action,
               correlation_id,
               before_state,
               after_state,
               metadata
          from audit.audit_event
         where tenant_id=?
           and object_type='SALARY_STRUCTURE'
           and object_id=?
           and (
             coalesce(before_state->>'versionId','')=?
             or coalesce(after_state->>'versionId','')=?
             or coalesce(metadata->>'versionId','')=?
             or coalesce(before_state->>'salaryStructureVersionId','')=?
             or coalesce(after_state->>'salaryStructureVersionId','')=?
             or coalesce(metadata->>'salaryStructureVersionId','')=?
           )
         order by occurred_at,id
        """,
        (result, row) -> new AuditEvidenceView(
            result.getObject("id", UUID.class),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("actor"),
            result.getString("action"),
            result.getObject("correlation_id", UUID.class),
            json(result, "before_state"),
            json(result, "after_state"),
            json(result, "metadata")),
        TenantContext.require(),
        identityId,
        version,
        version,
        version,
        version,
        version,
        version);
  }

  private List<DomainEventEvidenceView> domainEvents(
      UUID identityId,
      UUID versionId) {
    String version = versionId.toString();
    return jdbc.query(
        """
        select id,
               event_type,
               event_version,
               occurred_at,
               correlation_id,
               causation_id,
               status,
               payload,
               headers
          from integration.outbox_event
         where tenant_id=?
           and aggregate_type='SALARY_STRUCTURE'
           and aggregate_id=?
           and (
             coalesce(payload->>'versionId','')=?
             or coalesce(payload->>'salaryStructureVersionId','')=?
           )
         order by occurred_at,id
        """,
        (result, row) -> new DomainEventEvidenceView(
            result.getObject("id", UUID.class),
            result.getString("event_type"),
            result.getInt("event_version"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getObject("correlation_id", UUID.class),
            result.getObject("causation_id", UUID.class),
            result.getString("status"),
            json(result, "payload"),
            json(result, "headers")),
        TenantContext.require(),
        identityId,
        version,
        version);
  }

  private JsonNode json(ResultSet result, String column) throws SQLException {
    String value = result.getString(column);
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "Invalid JSON evidence in column " + column,
          exception);
    }
  }

  public record Snapshot(
      UUID identityId,
      UUID versionId,
      long versionNo,
      short structureSchemaVersion,
      String workflowStatus,
      String approvalStatus,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String configurationHash,
      String validationFingerprint,
      long statutoryBindingRevision,
      String currentStatutoryEvidenceHash,
      List<ValidationEvidenceView> validations,
      List<StatutoryEvidenceView> statutoryEvaluations,
      List<WorkflowEvidenceView> workflowActions,
      List<AuditEvidenceView> auditEvents,
      List<DomainEventEvidenceView> domainEvents) {
    public Snapshot withEvidence(
        List<ValidationEvidenceView> validationEvidence,
        List<StatutoryEvidenceView> statutoryEvidence,
        List<WorkflowEvidenceView> workflowEvidence,
        List<AuditEvidenceView> auditEvidence,
        List<DomainEventEvidenceView> eventEvidence) {
      return new Snapshot(
          identityId,
          versionId,
          versionNo,
          structureSchemaVersion,
          workflowStatus,
          approvalStatus,
          effectiveFrom,
          effectiveTo,
          configurationHash,
          validationFingerprint,
          statutoryBindingRevision,
          currentStatutoryEvidenceHash,
          List.copyOf(validationEvidence),
          List.copyOf(statutoryEvidence),
          List.copyOf(workflowEvidence),
          List.copyOf(auditEvidence),
          List.copyOf(eventEvidence));
    }
  }
}
