package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.AuthorisedSignatoryCreateRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryScopeRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryVersionWriteRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryView;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryView.ScopeView;
import com.acme.hrms.payroll.organisation.OrganisationProblemException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorisedSignatoryRepository {
  private static final String SELECT =
      """
      select
        i.id identity_id,
        i.code,
        i.owner_kind,
        i.legal_entity_id,
        i.payroll_statutory_unit_id,
        i.status identity_status,
        i.version_no identity_version_no,
        v.id version_id,
        v.version_sequence,
        v.version_no,
        v.full_name,
        v.designation,
        v.authority_reference,
        v.effective_from,
        v.effective_to,
        v.lifecycle_status,
        v.verification_evidence_ref,
        v.verified_at,
        v.verified_by,
        v.approved_at,
        v.approved_by,
        v.approval_evidence_ref,
        v.rejected_at,
        v.rejected_by,
        v.rejection_reason,
        v.rejection_evidence_ref,
        v.suspended_at,
        v.suspended_by,
        v.suspension_reason,
        v.supersedes_version_id,
        exists (
          select 1
          from organisation.authorised_signatory_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by
      from organisation.authorised_signatory i
      join organisation.authorised_signatory_version v
        on v.tenant_id=i.tenant_id
       and v.authorised_signatory_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public AuthorisedSignatoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AuthorisedSignatoryView create(
      AuthorisedSignatoryCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into organisation.authorised_signatory(
            id,tenant_id,code,owner_kind,
            legal_entity_id,payroll_statutory_unit_id,
            created_by,updated_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          identityId,
          TenantContext.require(),
          request.code(),
          request.ownerKind(),
          request.legalEntityId(),
          request.payrollStatutoryUnitId(),
          actor,
          actor);

      insertVersion(
          versionId,
          identityId,
          ownerKey(identityId),
          1,
          null,
          request.version(),
          actor);
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public AuthorisedSignatoryView addVersion(
      UUID identityId,
      AuthorisedSignatoryVersionWriteRequest request,
      String actor) {
    try {
      lockIdentity(identityId);
      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from organisation.authorised_signatory_version
              where tenant_id=? and authorised_signatory_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from organisation.authorised_signatory_version
              where tenant_id=? and authorised_signatory_id=?
              order by version_sequence desc
              limit 1
              """,
              UUID.class,
              TenantContext.require(),
              identityId);
      UUID versionId = UUID.randomUUID();
      insertVersion(
          versionId,
          identityId,
          ownerKey(identityId),
          next == null ? 1 : next,
          supersedes,
          request,
          actor);
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public AuthorisedSignatoryView submit(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return command(
        """
        select organisation.submit_authorised_signatory_version(
          ?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or not a draft",
        actor,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView verify(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.verify_authorised_signatory_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or not pending verification",
        actor,
        evidenceRef,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView requestApproval(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return command(
        """
        select organisation.request_authorised_signatory_approval(
          ?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or not verified",
        actor,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView approve(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.activate_authorised_signatory_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or not approval-pending",
        actor,
        evidenceRef,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView reject(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.reject_authorised_signatory_version(
          ?,?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or cannot be rejected",
        actor,
        reason,
        evidenceRef,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView suspend(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      Instant now) {
    return command(
        """
        select organisation.suspend_authorised_signatory_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Signatory version is stale or not active",
        actor,
        reason,
        Timestamp.from(now));
  }

  public AuthorisedSignatoryView version(UUID versionId) {
    AuthorisedSignatoryView base =
        jdbc.query(
                SELECT + " where v.tenant_id=? and v.id=?",
                this::mapBase,
                TenantContext.require(),
                versionId)
            .stream()
            .findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException(
                    "Authorised-signatory version was not found"));
    return withScopes(base);
  }

  public List<AuthorisedSignatoryView> history(UUID identityId) {
    requireIdentityExists(identityId);
    return jdbc.query(
            SELECT
                + """
                   where i.tenant_id=? and i.id=?
                   order by v.version_sequence desc
                   """,
            this::mapBase,
            TenantContext.require(),
            identityId)
        .stream()
        .map(this::withScopes)
        .toList();
  }

  public List<AuthorisedSignatoryView> list(LocalDate asOf) {
    List<AuthorisedSignatoryView> candidates =
        jdbc.query(
            SELECT
                + """
                   where i.tenant_id=?
                     and v.lifecycle_status in ('ACTIVE','SUPERSEDED')
                     and v.approved_at is not null
                     and v.approved_at::date<=?
                     and v.effective_from<=?
                     and (v.effective_to is null or v.effective_to>?)
                     and not exists (
                       select 1
                       from organisation.authorised_signatory_version successor
                       where successor.tenant_id=v.tenant_id
                         and successor.supersedes_version_id=v.id
                         and successor.approved_at is not null
                         and successor.approved_at::date<=?
                     )
                   order by i.code,v.version_sequence desc
                   """,
            this::mapBase,
            TenantContext.require(),
            Date.valueOf(asOf),
            Date.valueOf(asOf),
            Date.valueOf(asOf),
            Date.valueOf(asOf));

    Map<UUID, AuthorisedSignatoryView> resolved = new LinkedHashMap<>();
    for (AuthorisedSignatoryView candidate : candidates) {
      resolved.putIfAbsent(candidate.identityId(), candidate);
    }
    return resolved.values().stream().map(this::withScopes).toList();
  }

  public AuthorisedSignatoryView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No active authorised signatory is effective on " + asOf));
  }

  private AuthorisedSignatoryView command(
      String sql,
      UUID versionId,
      long expectedVersion,
      String conflictDetail,
      Object... commandArguments) {
    try {
      Object[] parameters = new Object[3 + commandArguments.length];
      parameters[0] = TenantContext.require();
      parameters[1] = versionId;
      parameters[2] = expectedVersion;
      System.arraycopy(
          commandArguments,
          0,
          parameters,
          3,
          commandArguments.length);

      Long changed = jdbc.queryForObject(sql, Long.class, parameters);
      if (changed == null || changed != 1) {
        throw conflict(conflictDetail);
      }
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      String ownerKey,
      int sequence,
      UUID supersedes,
      AuthorisedSignatoryVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into organisation.authorised_signatory_version(
          id,tenant_id,authorised_signatory_id,owner_key,version_sequence,
          full_name,designation,authority_reference,
          effective_from,effective_to,lifecycle_status,supersedes_version_id,
          created_by,updated_by
        ) values (
          ?,?,?,?,?,?,?,?,
          ?,?,'DRAFT',?,?,?
        )
        """,
        versionId,
        TenantContext.require(),
        identityId,
        ownerKey,
        sequence,
        request.fullName().trim(),
        blankToNull(request.designation()),
        request.authorityReference().trim(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);

    for (AuthorisedSignatoryScopeRequest scope : request.scopes()) {
      jdbc.update(
          """
          insert into organisation.authorised_signatory_scope(
            id,tenant_id,authorised_signatory_id,
            authorised_signatory_version_id,purpose_code,
            currency_code,maximum_amount,created_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          TenantContext.require(),
          identityId,
          versionId,
          scope.purposeCode(),
          blankToNull(scope.currencyCode()),
          scope.maximumAmount(),
          actor);
    }
  }

  private String ownerKey(UUID identityId) {
    String value =
        jdbc.queryForObject(
            """
            select owner_key
            from organisation.authorised_signatory
            where tenant_id=? and id=?
            """,
            String.class,
            TenantContext.require(),
            identityId);
    if (value == null) {
      throw new ResourceNotFoundException(
          "Authorised-signatory identity was not found");
    }
    return value;
  }

  private void requireIdentityExists(UUID identityId) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
            from organisation.authorised_signatory
            where tenant_id=? and id=?
            """,
            Integer.class,
            TenantContext.require(),
            identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Authorised-signatory identity was not found");
    }
  }

  private void lockIdentity(UUID identityId) {
    Boolean locked =
        jdbc.queryForObject(
            "select organisation.lock_authorised_signatory_identity(?,?)",
            Boolean.class,
            TenantContext.require(),
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Authorised-signatory identity was not found");
    }
  }

  private AuthorisedSignatoryView withScopes(AuthorisedSignatoryView view) {
    List<ScopeView> scopes =
        jdbc.query(
            """
            select id,purpose_code,currency_code,maximum_amount
            from organisation.authorised_signatory_scope
            where tenant_id=? and authorised_signatory_version_id=?
            order by purpose_code,currency_code nulls first
            """,
            (rs, row) ->
                new ScopeView(
                    rs.getObject("id", UUID.class),
                    rs.getString("purpose_code"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("maximum_amount")),
            TenantContext.require(),
            view.versionId());

    return new AuthorisedSignatoryView(
        view.identityId(),
        view.code(),
        view.ownerKind(),
        view.legalEntityId(),
        view.payrollStatutoryUnitId(),
        view.identityStatus(),
        view.identityVersionNo(),
        view.versionId(),
        view.versionSequence(),
        view.versionNo(),
        view.fullName(),
        view.designation(),
        view.authorityReference(),
        view.effectiveFrom(),
        view.effectiveTo(),
        view.lifecycleStatus(),
        view.verificationEvidenceRef(),
        view.verifiedAt(),
        view.verifiedBy(),
        view.approvedAt(),
        view.approvedBy(),
        view.approvalEvidenceRef(),
        view.rejectedAt(),
        view.rejectedBy(),
        view.rejectionReason(),
        view.rejectionEvidenceRef(),
        view.suspendedAt(),
        view.suspendedBy(),
        view.suspensionReason(),
        view.supersedesVersionId(),
        view.superseded(),
        view.createdBy(),
        scopes);
  }

  private AuthorisedSignatoryView mapBase(ResultSet rs, int row)
      throws SQLException {
    return new AuthorisedSignatoryView(
        rs.getObject("identity_id", UUID.class),
        rs.getString("code"),
        rs.getString("owner_kind"),
        rs.getObject("legal_entity_id", UUID.class),
        rs.getObject("payroll_statutory_unit_id", UUID.class),
        rs.getString("identity_status"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getString("full_name"),
        rs.getString("designation"),
        rs.getString("authority_reference"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"),
        rs.getString("verification_evidence_ref"),
        instant(rs, "verified_at"),
        rs.getString("verified_by"),
        instant(rs, "approved_at"),
        rs.getString("approved_by"),
        rs.getString("approval_evidence_ref"),
        instant(rs, "rejected_at"),
        rs.getString("rejected_by"),
        rs.getString("rejection_reason"),
        rs.getString("rejection_evidence_ref"),
        instant(rs, "suspended_at"),
        rs.getString("suspended_by"),
        rs.getString("suspension_reason"),
        rs.getObject("supersedes_version_id", UUID.class),
        rs.getBoolean("superseded"),
        rs.getString("created_by"),
        List.of());
  }

  private Instant instant(ResultSet rs, String column)
      throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private RuntimeException translate(DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    return switch (state) {
      case "23505" ->
          conflict(
              "The signatory code, version lineage, or authority scope conflicts with existing data",
              exception);
      case "23P01" ->
          conflict(
              "Active authorised-signatory effective ranges conflict",
              exception);
      case "23503" ->
          conflict(
              "The signatory owner or predecessor version does not exist",
              exception);
      case "23514" ->
          conflict(
              "The signatory owner, lifecycle, authority scope, or effective dates are invalid",
              exception);
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:authorised-signatory-forbidden",
              "Authorised-signatory operation forbidden",
              "The authorised-signatory operation is not permitted",
              exception);
      default -> exception;
    };
  }

  private SQLException sqlException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sql) {
        return sql;
      }
      current = current.getCause();
    }
    return null;
  }

  private OrganisationProblemException conflict(String detail) {
    return conflict(detail, null);
  }

  private OrganisationProblemException conflict(
      String detail,
      Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:authorised-signatory-conflict",
        "Authorised-signatory conflict",
        detail,
        cause);
  }
}
