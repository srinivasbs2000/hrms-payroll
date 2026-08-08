package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.OrganisationProblemException;
import com.acme.hrms.payroll.organisation.WorkLocationCreateRequest;
import com.acme.hrms.payroll.organisation.WorkLocationVersionWriteRequest;
import com.acme.hrms.payroll.organisation.WorkLocationView;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkLocationRepository {
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
        v.establishment_version_id,
        v.payroll_jurisdiction_id,
        v.payroll_jurisdiction_version_id,
        v.address_line1,
        v.address_line2,
        v.locality,
        v.state_code,
        v.postal_code,
        v.country_code,
        v.effective_from,
        v.effective_to,
        v.approval_status,
        v.supersedes_version_id,
        exists (
          select 1
          from organisation.work_location_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by,
        v.approved_at,
        v.approved_by
      from organisation.work_location i
      join organisation.work_location_version v
        on v.tenant_id=i.tenant_id
       and v.work_location_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public WorkLocationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public WorkLocationView create(
      WorkLocationCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into organisation.work_location(
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

  public WorkLocationView addVersion(
      UUID identityId,
      WorkLocationVersionWriteRequest request,
      String actor) {
    try {
      lockIdentity(identityId);
      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from organisation.work_location_version
              where tenant_id=? and work_location_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from organisation.work_location_version
              where tenant_id=? and work_location_id=?
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

  public WorkLocationView approve(
      UUID versionId, long expectedVersion, String actor, Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select organisation.approve_work_location_version(
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
        throw conflict("Work-location version is stale or not approvable");
      }
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public WorkLocationView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Work-location version was not found"));
  }

  public List<WorkLocationView> list(LocalDate asOf) {
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

  public WorkLocationView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No approved work location is effective on " + asOf));
  }

  private void lockIdentity(UUID identityId) {
    Boolean locked =
        jdbc.queryForObject(
            "select organisation.lock_work_location_identity(?,?)",
            Boolean.class,
            TenantContext.require(),
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Work-location identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      WorkLocationVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into organisation.work_location_version(
          id,tenant_id,work_location_id,version_sequence,
          name,establishment_version_id,
          payroll_jurisdiction_id,payroll_jurisdiction_version_id,
          address_line1,address_line2,locality,state_code,postal_code,country_code,
          effective_from,effective_to,approval_status,
          supersedes_version_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name(),
        request.establishmentVersionId(),
        request.payrollJurisdictionId(),
        request.payrollJurisdictionVersionId(),
        request.addressLine1(),
        request.addressLine2(),
        request.locality(),
        request.stateCode(),
        request.postalCode(),
        request.countryCode(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private WorkLocationView map(ResultSet rs, int row)
      throws SQLException {
    Timestamp approvedAt = rs.getTimestamp("approved_at");
    return new WorkLocationView(
        rs.getObject("identity_id", UUID.class),
        rs.getString("code"),
        rs.getString("identity_status"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getString("name"),
        rs.getObject("establishment_version_id", UUID.class),
        rs.getObject("payroll_jurisdiction_id", UUID.class),
        rs.getObject("payroll_jurisdiction_version_id", UUID.class),
        rs.getString("address_line1"),
        rs.getString("address_line2"),
        rs.getString("locality"),
        rs.getString("state_code"),
        rs.getString("postal_code"),
        rs.getString("country_code"),
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
      case "23505" ->
          new OrganisationProblemException(
              HttpStatus.CONFLICT,
              "urn:problem:organisation:work-location-duplicate",
              "Work-location conflict",
              "The work-location code or version chain conflicts with existing data",
              exception);
      case "23P01" ->
          conflict("Approved work-location effective ranges cannot overlap", exception);
      case "23503" ->
          conflict(
              "The requested establishment or jurisdiction version does not exist",
              exception);
      case "23514" ->
          conflict(
              "The work-location hierarchy attribution or effective dates are invalid",
              exception);
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:work-location-forbidden",
              "Work-location operation forbidden",
              "The work-location operation is not permitted",
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
        "urn:problem:organisation:work-location-conflict",
        "Work-location conflict",
        detail,
        cause);
  }
}
