package com.acme.hrms.payroll.statutory.internal.infrastructure;

import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import com.acme.hrms.payroll.statutory.RegistrationTypeCreateRequest;
import com.acme.hrms.payroll.statutory.RegistrationTypeVersionWriteRequest;
import com.acme.hrms.payroll.statutory.RegistrationTypeView;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationTypeRepository {
  private static final String SELECT =
      """
      select
        i.id identity_id,
        i.code,
        i.status identity_status,
        i.version_no identity_version_no,
        v.id version_id,
        v.version_sequence,
        v.version_no,
        v.name,
        v.obligation_code,
        v.authority_code,
        v.jurisdiction_level_code,
        v.identifier_pattern,
        v.identifier_pattern_dialect,
        v.identifier_case_policy,
        v.parent_required,
        v.parent_registration_type_id,
        v.effective_from,
        v.effective_to,
        v.approval_status,
        v.supersedes_version_id,
        exists (
          select 1
          from statutory.registration_type_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by,
        v.approved_at,
        v.approved_by,
        coalesce(
          (
            select string_agg(owner_kind.owner_kind, ',' order by owner_kind.owner_kind)
            from statutory.registration_type_owner_kind owner_kind
            where owner_kind.tenant_id=v.tenant_id
              and owner_kind.registration_type_version_id=v.id
          ),
          ''
        ) owner_kinds
      from statutory.registration_type i
      join statutory.registration_type_version v
        on v.tenant_id=i.tenant_id
       and v.registration_type_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public RegistrationTypeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public RegistrationTypeView create(
      RegistrationTypeCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into statutory.registration_type(
            id,tenant_id,code,created_by,updated_by
          ) values (?,?,?,?,?)
          """,
          identityId,
          TenantContext.require(),
          request.code(),
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

  public RegistrationTypeView addVersion(
      UUID identityId,
      RegistrationTypeVersionWriteRequest request,
      String actor) {
    try {
      lockIdentity(identityId);
      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from statutory.registration_type_version
              where tenant_id=? and registration_type_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from statutory.registration_type_version
              where tenant_id=? and registration_type_id=?
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

  public RegistrationTypeView approve(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select statutory.approve_registration_type_version(
                ?,?,?,?,?
              )
              """,
              Long.class,
              TenantContext.require(),
              versionId,
              expectedVersion,
              actor,
              Timestamp.from(now));
      if (changed == null || changed != 1) {
        throw new ConflictException(
            "Registration-type version is stale or not approvable");
      }
      return version(versionId);
    } catch (ConflictException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public RegistrationTypeView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Registration-type version was not found"));
  }

  public List<RegistrationTypeView> list(LocalDate asOf) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=?
                 and i.status='ACTIVE'
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
               order by i.code
               """,
        this::map,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public RegistrationTypeView current(
      UUID identityId,
      LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No approved registration type is effective on " + asOf));
  }

  private void lockIdentity(UUID identityId) {
    Boolean locked =
        jdbc.queryForObject(
            "select statutory.lock_registration_type_identity(?,?)",
            Boolean.class,
            TenantContext.require(),
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Registration-type identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      RegistrationTypeVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into statutory.registration_type_version(
          id,tenant_id,registration_type_id,version_sequence,
          name,obligation_code,authority_code,jurisdiction_level_code,
          identifier_pattern,identifier_pattern_dialect,identifier_case_policy,
          parent_required,parent_registration_type_id,
          effective_from,effective_to,approval_status,
          supersedes_version_id,created_by,updated_by
        ) values (
          ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?
        )
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name(),
        request.obligationCode(),
        request.authorityCode(),
        request.jurisdictionLevelCode(),
        blankToNull(request.identifierPattern()),
        RegistrationTypeVersionWriteRequest.IDENTIFIER_PATTERN_DIALECT,
        request.identifierCasePolicy(),
        request.parentRequired(),
        request.parentRegistrationTypeId(),
        Date.valueOf(request.effectiveFrom()),
        request.effectiveTo() == null
            ? null
            : Date.valueOf(request.effectiveTo()),
        supersedes,
        actor,
        actor);

    for (RegistrationOwnerKind ownerKind : request.ownerKinds()) {
      jdbc.update(
          """
          insert into statutory.registration_type_owner_kind(
            tenant_id,
            registration_type_id,
            registration_type_version_id,
            owner_kind,
            created_by
          ) values (?,?,?,?,?)
          """,
          TenantContext.require(),
          identityId,
          versionId,
          ownerKind.name(),
          actor);
    }
  }

  private RegistrationTypeView map(
      ResultSet rs,
      int row) throws SQLException {
    Timestamp approvedAt = rs.getTimestamp("approved_at");
    String owners = rs.getString("owner_kinds");
    List<RegistrationOwnerKind> ownerKinds =
        owners == null || owners.isBlank()
            ? List.of()
            : Arrays.stream(owners.split(","))
                .map(RegistrationOwnerKind::valueOf)
                .toList();

    return new RegistrationTypeView(
        rs.getObject("identity_id", UUID.class),
        rs.getString("code"),
        rs.getString("identity_status"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getString("name"),
        rs.getString("obligation_code"),
        rs.getString("authority_code"),
        rs.getString("jurisdiction_level_code"),
        rs.getString("identifier_pattern"),
        rs.getString("identifier_pattern_dialect"),
        rs.getString("identifier_case_policy"),
        rs.getBoolean("parent_required"),
        rs.getObject("parent_registration_type_id", UUID.class),
        ownerKinds,
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("approval_status"),
        rs.getObject("supersedes_version_id", UUID.class),
        rs.getBoolean("superseded"),
        rs.getString("created_by"),
        approvedAt == null ? null : approvedAt.toInstant(),
        rs.getString("approved_by"));
  }

  private RuntimeException translate(DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    return switch (state) {
      case "42501" ->
          new AccessDeniedException(
              "Registration-type operation is not permitted",
              exception);
      case "23503" ->
          new ConflictException(
              "Required registration-type dependency was not found",
              exception);
      case "23505", "23P01", "23514" ->
          new ConflictException(
              "Registration-type lifecycle or effective dates conflict",
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

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
