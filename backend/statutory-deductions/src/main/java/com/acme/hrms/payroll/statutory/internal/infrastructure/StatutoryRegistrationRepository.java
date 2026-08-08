package com.acme.hrms.payroll.statutory.internal.infrastructure;

import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import com.acme.hrms.payroll.statutory.RegistrationTypeVersionWriteRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationCreateRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationVersionWriteRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationView;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class StatutoryRegistrationRepository {
  private static final String SELECT =
      """
      select
        i.id identity_id,
        i.registration_type_id,
        i.reference_code,
        i.version_no identity_version_no,
        v.id version_id,
        v.version_sequence,
        v.version_no,
        v.registration_type_version_id,
        v.identifier_raw,
        v.identifier_normalized,
        v.owner_kind,
        coalesce(
          v.legal_entity_id,
          v.payroll_statutory_unit_id,
          v.establishment_id
        ) owner_id,
        v.payroll_jurisdiction_id,
        v.payroll_jurisdiction_version_id,
        v.parent_registration_id,
        v.parent_registration_version_id,
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
        v.authority_reference,
        v.suspended_at,
        v.suspended_by,
        v.suspension_reason,
        v.supersedes_version_id,
        exists (
          select 1
          from statutory.registration_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by
      from statutory.registration i
      join statutory.registration_version v
        on v.tenant_id=i.tenant_id
       and v.registration_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public StatutoryRegistrationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public StatutoryRegistrationView create(
      StatutoryRegistrationCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into statutory.registration(
            id,tenant_id,registration_type_id,reference_code,
            created_by,updated_by
          ) values (?,?,?,?,?,?)
          """,
          identityId,
          TenantContext.require(),
          request.registrationTypeId(),
          request.referenceCode(),
          actor,
          actor);
      insertVersion(
          versionId,
          identityId,
          1,
          null,
          request.version(),
          actor);
      return version(versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public StatutoryRegistrationView addVersion(
      UUID identityId,
      StatutoryRegistrationVersionWriteRequest request,
      String actor) {
    try {
      RegistrationIdentity identity = lockIdentity(identityId);
      if (!identity.registrationTypeId().equals(request.registrationTypeId())) {
        throw new IllegalArgumentException(
            "Registration identity type cannot change");
      }

      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from statutory.registration_version
              where tenant_id=? and registration_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from statutory.registration_version
              where tenant_id=? and registration_id=?
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
          next,
          supersedes,
          request,
          actor);
      return version(versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public StatutoryRegistrationView submit(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return transition(
        "select statutory.submit_registration_version(?,?,?,?,?)",
        versionId,
        expectedVersion,
        actor,
        now);
  }

  public StatutoryRegistrationView verify(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return transition(
        """
        select statutory.verify_registration_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        actor,
        now,
        evidenceRef);
  }

  public StatutoryRegistrationView requestApproval(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return transition(
        """
        select statutory.request_registration_approval(
          ?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        actor,
        now);
  }

  public StatutoryRegistrationView approve(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return transition(
        """
        select statutory.activate_registration_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        actor,
        now,
        evidenceRef);
  }

  public StatutoryRegistrationView reject(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      String evidenceRef,
      String authorityReference,
      Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select statutory.reject_registration_version(
                ?,?,?,?,?,?,?,?
              )
              """,
              Long.class,
              TenantContext.require(),
              versionId,
              expectedVersion,
              actor,
              reason,
              evidenceRef,
              authorityReference,
              Timestamp.from(now));
      requireChanged(changed);
      return version(versionId);
    } catch (ConflictException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public StatutoryRegistrationView suspend(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select statutory.suspend_registration_version(
                ?,?,?,?,?,?
              )
              """,
              Long.class,
              TenantContext.require(),
              versionId,
              expectedVersion,
              actor,
              reason,
              Timestamp.from(now));
      requireChanged(changed);
      return version(versionId);
    } catch (ConflictException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public StatutoryRegistrationView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::mapMasked,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Statutory registration version was not found"));
  }

  public StatutoryRegistrationView versionExact(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::mapExact,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Statutory registration version was not found"));
  }

  public List<StatutoryRegistrationView> list(LocalDate asOf) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=?
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
                 and v.lifecycle_status in ('ACTIVE','SUSPENDED')
               order by i.reference_code
               """,
        this::mapMasked,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public StatutoryRegistrationView current(
      UUID identityId,
      LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No active or suspended registration is effective on " + asOf));
  }

  public List<StatutoryRegistrationView> history(UUID identityId) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence
               """,
        this::mapMasked,
        TenantContext.require(),
        identityId);
  }

  private StatutoryRegistrationView transition(
      String sql,
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now,
      Object... extraBeforeTimestamp) {
    try {
      Object[] arguments =
          new Object[4 + extraBeforeTimestamp.length + 1];
      int index = 0;
      arguments[index++] = TenantContext.require();
      arguments[index++] = versionId;
      arguments[index++] = expectedVersion;
      arguments[index++] = actor;
      for (Object extra : extraBeforeTimestamp) {
        arguments[index++] = extra;
      }
      arguments[index] = Timestamp.from(now);

      Long changed =
          jdbc.queryForObject(
              sql,
              Long.class,
              arguments);
      requireChanged(changed);
      return version(versionId);
    } catch (ConflictException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  private void requireChanged(Long changed) {
    if (changed == null || changed != 1) {
      throw new ConflictException(
          "Registration version is stale or lifecycle transition is invalid");
    }
  }

  private RegistrationIdentity lockIdentity(UUID identityId) {
    UUID tenantId = TenantContext.require();
    Boolean locked =
        jdbc.queryForObject(
            "select statutory.lock_registration_identity(?,?)",
            Boolean.class,
            tenantId,
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Statutory registration identity was not found");
    }

    UUID registrationTypeId =
        jdbc.queryForObject(
            """
            select registration_type_id
            from statutory.registration
            where tenant_id=? and id=?
            """,
            UUID.class,
            tenantId,
            identityId);
    return new RegistrationIdentity(registrationTypeId);
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      StatutoryRegistrationVersionWriteRequest request,
      String actor) {
    IdentifierPolicy policy =
        identifierPolicy(
            request.registrationTypeId(),
            request.registrationTypeVersionId());
    String raw = request.identifier().trim();
    String normalized =
        "UPPER".equals(policy.casePolicy())
            ? raw.toUpperCase(Locale.ROOT)
            : raw;

    if (!RegistrationTypeVersionWriteRequest.IDENTIFIER_PATTERN_DIALECT
        .equals(policy.dialect())) {
      throw new IllegalStateException(
          "Unsupported registration identifier pattern dialect");
    }
    if (!RegistrationTypeVersionWriteRequest.matchesIdentifierPattern(
        policy.pattern(),
        normalized)) {
      throw new IllegalArgumentException(
          "Registration identifier does not match type metadata");
    }

    UUID legalEntityId =
        request.ownerKind() == RegistrationOwnerKind.LEGAL_ENTITY
            ? request.ownerId()
            : null;
    UUID unitId =
        request.ownerKind() == RegistrationOwnerKind.PAYROLL_STATUTORY_UNIT
            ? request.ownerId()
            : null;
    UUID establishmentId =
        request.ownerKind() == RegistrationOwnerKind.ESTABLISHMENT
            ? request.ownerId()
            : null;

    jdbc.update(
        """
        insert into statutory.registration_version(
          id,
          tenant_id,
          registration_id,
          registration_type_id,
          registration_type_version_id,
          version_sequence,
          identifier_raw,
          identifier_normalized,
          owner_kind,
          legal_entity_id,
          payroll_statutory_unit_id,
          establishment_id,
          payroll_jurisdiction_id,
          payroll_jurisdiction_version_id,
          parent_registration_id,
          parent_registration_version_id,
          effective_from,
          effective_to,
          lifecycle_status,
          supersedes_version_id,
          created_by,
          updated_by
        ) values (
          ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?
        )
        """,
        versionId,
        TenantContext.require(),
        identityId,
        request.registrationTypeId(),
        request.registrationTypeVersionId(),
        sequence,
        raw,
        normalized,
        request.ownerKind().name(),
        legalEntityId,
        unitId,
        establishmentId,
        request.payrollJurisdictionId(),
        request.payrollJurisdictionVersionId(),
        request.parentRegistrationId(),
        request.parentRegistrationVersionId(),
        Date.valueOf(request.effectiveFrom()),
        request.effectiveTo() == null
            ? null
            : Date.valueOf(request.effectiveTo()),
        supersedes,
        actor,
        actor);
  }

  private IdentifierPolicy identifierPolicy(
      UUID registrationTypeId,
      UUID registrationTypeVersionId) {
    return jdbc.query(
            """
            select
              identifier_case_policy,
              identifier_pattern,
              identifier_pattern_dialect
            from statutory.registration_type_version
            where tenant_id=?
              and registration_type_id=?
              and id=?
              and approval_status='APPROVED'
            """,
            (rs, row) ->
                new IdentifierPolicy(
                    rs.getString("identifier_case_policy"),
                    rs.getString("identifier_pattern"),
                    rs.getString("identifier_pattern_dialect")),
            TenantContext.require(),
            registrationTypeId,
            registrationTypeVersionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ConflictException(
                "Registration type version is not approved"));
  }

  private StatutoryRegistrationView mapMasked(
      ResultSet rs,
      int row) throws SQLException {
    return map(rs, true);
  }

  private StatutoryRegistrationView mapExact(
      ResultSet rs,
      int row) throws SQLException {
    return map(rs, false);
  }

  private StatutoryRegistrationView map(
      ResultSet rs,
      boolean maskIdentifier) throws SQLException {
    return new StatutoryRegistrationView(
        rs.getObject("identity_id", UUID.class),
        rs.getObject("registration_type_id", UUID.class),
        rs.getString("reference_code"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getObject("registration_type_version_id", UUID.class),
        identifierValue(rs.getString("identifier_raw"), maskIdentifier),
        identifierValue(rs.getString("identifier_normalized"), maskIdentifier),
        RegistrationOwnerKind.valueOf(rs.getString("owner_kind")),
        rs.getObject("owner_id", UUID.class),
        rs.getObject("payroll_jurisdiction_id", UUID.class),
        rs.getObject("payroll_jurisdiction_version_id", UUID.class),
        rs.getObject("parent_registration_id", UUID.class),
        rs.getObject("parent_registration_version_id", UUID.class),
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
        rs.getString("authority_reference"),
        instant(rs, "suspended_at"),
        rs.getString("suspended_by"),
        rs.getString("suspension_reason"),
        rs.getObject("supersedes_version_id", UUID.class),
        rs.getBoolean("superseded"),
        rs.getString("created_by"));
  }

  private String identifierValue(
      String value,
      boolean mask) {
    if (!mask || value == null || value.isBlank()) {
      return value;
    }
    if (value.length() <= 4) {
      return "****";
    }
    return "****" + value.substring(value.length() - 4);
  }

  private Instant instant(ResultSet rs, String column)
      throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private RuntimeException translate(DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    return switch (state) {
      case "42501" ->
          new AccessDeniedException(
              "Registration lifecycle operation is not permitted",
              exception);
      case "23503" ->
          new ConflictException(
              "Required registration dependency was not found",
              exception);
      case "23505", "23P01", "23514" ->
          new ConflictException(
              "Registration ownership, identifier, parent, lifecycle or effective dates conflict",
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

  private record RegistrationIdentity(UUID registrationTypeId) {}

  private record IdentifierPolicy(
      String casePolicy,
      String pattern,
      String dialect) {}
}
