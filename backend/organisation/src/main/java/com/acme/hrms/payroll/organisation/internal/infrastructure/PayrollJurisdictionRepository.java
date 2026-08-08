package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.OrganisationProblemException;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionCreateRequest;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionVersionWriteRequest;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionView;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollJurisdictionRepository {
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
        v.country_code,
        v.level_code,
        v.level_rank,
        v.parent_jurisdiction_id,
        v.parent_jurisdiction_version_id,
        v.effective_from,
        v.effective_to,
        v.approval_status,
        v.supersedes_version_id,
        exists (
          select 1
          from organisation.payroll_jurisdiction_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by,
        v.approved_at,
        v.approved_by
      from organisation.payroll_jurisdiction i
      join organisation.payroll_jurisdiction_version v
        on v.tenant_id=i.tenant_id
       and v.payroll_jurisdiction_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public PayrollJurisdictionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollJurisdictionView create(
      PayrollJurisdictionCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into organisation.payroll_jurisdiction(
            id,tenant_id,code,created_by,updated_by
          ) values (?,?,?,?,?)
          """,
          identityId,
          TenantContext.require(),
          request.code(),
          actor,
          actor);
      insertVersion(versionId, identityId, 1, null, request.version(), actor);
      return version(versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public PayrollJurisdictionView addVersion(
      UUID identityId,
      PayrollJurisdictionVersionWriteRequest request,
      String actor) {
    try {
      lockIdentity(identityId);
      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from organisation.payroll_jurisdiction_version
              where tenant_id=? and payroll_jurisdiction_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from organisation.payroll_jurisdiction_version
              where tenant_id=? and payroll_jurisdiction_id=?
              order by version_sequence desc
              limit 1
              """,
              UUID.class,
              TenantContext.require(),
              identityId);
      UUID versionId = UUID.randomUUID();
      insertVersion(versionId, identityId, next, supersedes, request, actor);
      return version(versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public PayrollJurisdictionView approve(
      UUID versionId, long expectedVersion, String actor, Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select organisation.approve_payroll_jurisdiction_version(
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
        throw conflict("Jurisdiction version is stale or not approvable");
      }
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public PayrollJurisdictionView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Payroll jurisdiction version was not found"));
  }

  public List<PayrollJurisdictionView> list(LocalDate asOf) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=?
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

  public PayrollJurisdictionView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No approved payroll jurisdiction is effective on " + asOf));
  }

  private void lockIdentity(UUID identityId) {
    Boolean locked =
        jdbc.queryForObject(
            "select organisation.lock_payroll_jurisdiction_identity(?,?)",
            Boolean.class,
            TenantContext.require(),
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Payroll jurisdiction identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      PayrollJurisdictionVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into organisation.payroll_jurisdiction_version(
          id,tenant_id,payroll_jurisdiction_id,version_sequence,
          name,country_code,level_code,level_rank,
          parent_jurisdiction_id,parent_jurisdiction_version_id,
          effective_from,effective_to,approval_status,
          supersedes_version_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name(),
        request.countryCode(),
        request.levelCode(),
        request.levelRank(),
        request.parentJurisdictionId(),
        request.parentJurisdictionVersionId(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private PayrollJurisdictionView map(ResultSet rs, int row)
      throws SQLException {
    Timestamp approvedAt = rs.getTimestamp("approved_at");
    return new PayrollJurisdictionView(
        rs.getObject("identity_id", UUID.class),
        rs.getString("code"),
        rs.getString("identity_status"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getString("name"),
        rs.getString("country_code"),
        rs.getString("level_code"),
        rs.getInt("level_rank"),
        rs.getObject("parent_jurisdiction_id", UUID.class),
        rs.getObject("parent_jurisdiction_version_id", UUID.class),
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
    String message =
        sql == null || sql.getMessage() == null
            ? ""
            : sql.getMessage().toLowerCase(Locale.ROOT);
    return switch (state) {
      case "23505" ->
          new OrganisationProblemException(
              HttpStatus.CONFLICT,
              "urn:problem:organisation:jurisdiction-duplicate",
              "Payroll jurisdiction conflict",
              "The jurisdiction code or version chain conflicts with existing data",
              exception);
      case "23P01" ->
          conflict("Approved jurisdiction effective ranges cannot overlap", exception);
      case "23503" ->
          conflict("The requested jurisdiction parent does not exist", exception);
      case "23514" ->
          conflict(
              message.contains("cycle")
                  ? "Payroll jurisdiction hierarchy cannot contain cycles"
                  : "The jurisdiction hierarchy or effective dates are invalid",
              exception);
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:jurisdiction-forbidden",
              "Jurisdiction operation forbidden",
              "The jurisdiction operation is not permitted",
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

  private OrganisationProblemException conflict(String detail, Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:jurisdiction-conflict",
        "Payroll jurisdiction conflict",
        detail,
        cause);
  }
}
