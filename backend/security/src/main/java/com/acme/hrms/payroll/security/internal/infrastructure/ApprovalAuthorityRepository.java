package com.acme.hrms.payroll.security.internal.infrastructure;

import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.security.ApprovalAuthorityAssignmentCreateRequest;
import com.acme.hrms.payroll.security.ApprovalAuthorityAssignmentView;
import com.acme.hrms.payroll.security.ApprovalAuthorityDecision;
import com.acme.hrms.payroll.security.ApprovalAuthorityRequirement;
import com.acme.hrms.payroll.security.ApprovalDelegationCreateRequest;
import com.acme.hrms.payroll.security.ApprovalDelegationView;
import com.acme.hrms.payroll.security.ApprovalOwnerKind;
import com.acme.hrms.payroll.security.ApprovalRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalAuthorityRepository {
  private final JdbcTemplate jdbc;
  public ApprovalAuthorityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public ApprovalAuthorityAssignmentView createAssignment(
      ApprovalAuthorityAssignmentCreateRequest request, String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO security.approval_authority_assignment(
          id, tenant_id, owner_kind, owner_id, approval_role, domain_code, action_code,
          actor_id, effective_from, effective_to, created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id, TenantContext.require(), request.ownerKind().name(), request.ownerId(),
        request.approvalRole().name(), request.domainCode(), request.actionCode(),
        request.actorId(), request.effectiveFrom(), request.effectiveTo(), actor, actor);
    return assignment(id);
  }

  public List<ApprovalAuthorityAssignmentView> assignments() {
    return jdbc.query("""
        SELECT * FROM security.approval_authority_assignment
        WHERE tenant_id = ?
        ORDER BY owner_kind, owner_id, approval_role, domain_code, action_code,
                 actor_id, effective_from, id
        """, ASSIGNMENT_MAPPER, TenantContext.require());
  }

  public ApprovalAuthorityAssignmentView assignment(UUID authorityId) {
    List<ApprovalAuthorityAssignmentView> rows = jdbc.query("""
        SELECT * FROM security.approval_authority_assignment
        WHERE tenant_id = ? AND id = ?
        """, ASSIGNMENT_MAPPER, TenantContext.require(), authorityId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Approval authority does not exist");
    return rows.getFirst();
  }

  public ApprovalAuthorityAssignmentView suspend(
      UUID authorityId, long expectedVersion, String actor, String reason, Instant changedAt) {
    long affected = scalarLong("""
        SELECT security.suspend_approval_authority(?, ?, ?, ?, ?, ?)
        """, TenantContext.require(), authorityId, expectedVersion, actor, reason, changedAt);
    if (affected != 1) {
      throw new ConflictException("Approval authority is not active at the expected version");
    }
    return assignment(authorityId);
  }

  public ApprovalAuthorityAssignmentView retire(
      UUID authorityId, long expectedVersion, String actor, String reason, Instant changedAt) {
    long affected = scalarLong("""
        SELECT security.retire_approval_authority(?, ?, ?, ?, ?, ?)
        """, TenantContext.require(), authorityId, expectedVersion, actor, reason, changedAt);
    if (affected != 1) {
      throw new ConflictException("Approval authority cannot retire at the expected version");
    }
    return assignment(authorityId);
  }

  public ApprovalDelegationView createDelegation(
      ApprovalDelegationCreateRequest request, String delegator) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO security.approval_delegation(
          id, tenant_id, source_authority_id, delegator_actor_id, delegate_actor_id,
          effective_from, effective_to, created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id, TenantContext.require(), request.sourceAuthorityId(), delegator,
        request.delegateActorId(), request.effectiveFrom(), request.effectiveTo(),
        delegator, delegator);
    return delegation(id);
  }

  public List<ApprovalDelegationView> delegations() {
    return jdbc.query("""
        SELECT * FROM security.approval_delegation
        WHERE tenant_id = ?
        ORDER BY source_authority_id, effective_from, delegate_actor_id, id
        """, DELEGATION_MAPPER, TenantContext.require());
  }

  public ApprovalDelegationView delegation(UUID delegationId) {
    List<ApprovalDelegationView> rows = jdbc.query("""
        SELECT * FROM security.approval_delegation
        WHERE tenant_id = ? AND id = ?
        """, DELEGATION_MAPPER, TenantContext.require(), delegationId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Approval delegation does not exist");
    return rows.getFirst();
  }

  public ApprovalDelegationView revoke(
      UUID delegationId, long expectedVersion, String actor, String reason, Instant changedAt) {
    long affected = scalarLong("""
        SELECT security.revoke_approval_delegation(?, ?, ?, ?, ?, ?)
        """, TenantContext.require(), delegationId, expectedVersion, actor, reason, changedAt);
    if (affected != 1) {
      throw new ConflictException(
          "Approval delegation cannot be revoked by this actor at the expected version");
    }
    return delegation(delegationId);
  }

  public Optional<ApprovalAuthorityDecision> resolve(
      String effectiveActor, ApprovalAuthorityRequirement requirement) {
    List<ApprovalAuthorityDecision> rows = jdbc.query("""
        SELECT authority_id, delegation_id, source_actor_id, effective_actor_id
        FROM security.resolve_approval_authority(?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (result, rowNum) -> new ApprovalAuthorityDecision(
            result.getObject("authority_id", UUID.class),
            result.getObject("delegation_id", UUID.class),
            requirement.ownerKind(), requirement.ownerId(), requirement.approvalRole(),
            requirement.domainCode(), requirement.actionCode(), requirement.decisionDate(),
            result.getString("source_actor_id"), result.getString("effective_actor_id")),
        TenantContext.require(), effectiveActor, requirement.ownerKind().name(),
        requirement.ownerId(), requirement.approvalRole().name(), requirement.domainCode(),
        requirement.actionCode(), requirement.decisionDate());
    return rows.stream().findFirst();
  }

  private long scalarLong(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private static final RowMapper<ApprovalAuthorityAssignmentView> ASSIGNMENT_MAPPER =
      (result, rowNum) -> new ApprovalAuthorityAssignmentView(
          result.getObject("id", UUID.class),
          ApprovalOwnerKind.valueOf(result.getString("owner_kind")),
          result.getObject("owner_id", UUID.class),
          ApprovalRole.valueOf(result.getString("approval_role")),
          result.getString("domain_code"), result.getString("action_code"),
          result.getString("actor_id"),
          result.getObject("effective_from", LocalDate.class),
          result.getObject("effective_to", LocalDate.class),
          result.getString("status"), instant(result, "created_at"),
          result.getString("created_by"), instant(result, "suspended_at"),
          result.getString("suspended_by"), result.getString("suspension_reason"),
          instant(result, "retired_at"), result.getString("retired_by"),
          result.getString("retirement_reason"), result.getLong("version_no"));

  private static final RowMapper<ApprovalDelegationView> DELEGATION_MAPPER =
      (result, rowNum) -> new ApprovalDelegationView(
          result.getObject("id", UUID.class),
          result.getObject("source_authority_id", UUID.class),
          result.getString("delegator_actor_id"), result.getString("delegate_actor_id"),
          result.getObject("effective_from", LocalDate.class),
          result.getObject("effective_to", LocalDate.class),
          result.getString("status"), instant(result, "created_at"),
          result.getString("created_by"), instant(result, "revoked_at"),
          result.getString("revoked_by"), result.getString("revocation_reason"),
          result.getLong("version_no"));

  private static Instant instant(ResultSet result, String column) throws SQLException {
    var timestamp = result.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
