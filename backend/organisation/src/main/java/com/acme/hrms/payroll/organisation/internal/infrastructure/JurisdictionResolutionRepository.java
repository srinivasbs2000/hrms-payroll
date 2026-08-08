package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.JurisdictionOverrideView;
import com.acme.hrms.payroll.organisation.JurisdictionOverrideWriteRequest;
import com.acme.hrms.payroll.organisation.OrganisationProblemException;
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
public class JurisdictionResolutionRepository {
  private final JdbcTemplate jdbc;

  public JurisdictionResolutionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public JurisdictionOverrideView createOverride(
      JurisdictionOverrideWriteRequest request,
      String actor) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into organisation.jurisdiction_resolution_override(
            id,
            tenant_id,
            target_kind,
            work_location_version_id,
            establishment_version_id,
            payroll_jurisdiction_id,
            payroll_jurisdiction_version_id,
            effective_from,
            effective_to,
            reason,
            approval_status,
            created_by,
            updated_by
          ) values (
            ?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?
          )
          """,
          id,
          TenantContext.require(),
          request.targetKind(),
          request.workLocationVersionId(),
          request.establishmentVersionId(),
          request.payrollJurisdictionId(),
          request.payrollJurisdictionVersionId(),
          Date.valueOf(request.effectiveFrom()),
          request.effectiveTo() == null
              ? null
              : Date.valueOf(request.effectiveTo()),
          request.reason(),
          actor,
          actor);
      return override(id);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public JurisdictionOverrideView approveOverride(
      UUID id,
      long expectedVersion,
      String actor,
      Instant now) {
    try {
      Long changed =
          jdbc.queryForObject(
              """
              select organisation.approve_jurisdiction_override(
                ?,?,?,?,?
              )
              """,
              Long.class,
              TenantContext.require(),
              id,
              expectedVersion,
              actor,
              Timestamp.from(now));
      if (changed == null || changed != 1) {
        throw conflict(
            "Jurisdiction override is stale or not approvable");
      }
      return override(id);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public JurisdictionOverrideView override(UUID id) {
    return jdbc.query(
            """
            select
              id,
              version_no,
              target_kind,
              work_location_version_id,
              establishment_version_id,
              payroll_jurisdiction_id,
              payroll_jurisdiction_version_id,
              effective_from,
              effective_to,
              reason,
              approval_status,
              created_by,
              approved_at,
              approved_by
            from organisation.jurisdiction_resolution_override
            where tenant_id=? and id=?
            """,
            this::mapOverride,
            TenantContext.require(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Jurisdiction override was not found"));
  }

  public WorkLocationFact workLocation(
      UUID versionId,
      LocalDate asOf) {
    return jdbc.query(
            """
            select
              v.id work_location_version_id,
              v.establishment_version_id,
              v.payroll_jurisdiction_id,
              v.payroll_jurisdiction_version_id
            from organisation.work_location_version v
            join organisation.work_location i
              on i.tenant_id=v.tenant_id
             and i.id=v.work_location_id
            join organisation.payroll_jurisdiction_version jv
              on jv.tenant_id=v.tenant_id
             and jv.id=v.payroll_jurisdiction_version_id
             and jv.payroll_jurisdiction_id=v.payroll_jurisdiction_id
            where v.tenant_id=?
              and v.id=?
              and i.status='ACTIVE'
              and v.approval_status='APPROVED'
              and v.effective_from<=?
              and (v.effective_to is null or v.effective_to>?)
              and jv.approval_status='APPROVED'
              and jv.effective_from<=?
              and (jv.effective_to is null or jv.effective_to>?)
              and not exists (
                select 1
                from organisation.work_location_version successor
                where successor.tenant_id=v.tenant_id
                  and successor.supersedes_version_id=v.id
              )
            """,
            (rs, row) ->
                new WorkLocationFact(
                    rs.getObject(
                        "work_location_version_id",
                        UUID.class),
                    rs.getObject(
                        "establishment_version_id",
                        UUID.class),
                    rs.getObject(
                        "payroll_jurisdiction_id",
                        UUID.class),
                    rs.getObject(
                        "payroll_jurisdiction_version_id",
                        UUID.class)),
            TenantContext.require(),
            versionId,
            Date.valueOf(asOf),
            Date.valueOf(asOf),
            Date.valueOf(asOf),
            Date.valueOf(asOf))
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<OverrideFact> activeOverrides(
      UUID workLocationVersionId,
      UUID establishmentVersionId,
      LocalDate asOf) {
    return jdbc.query(
        """
        select
          id,
          target_kind,
          payroll_jurisdiction_id,
          payroll_jurisdiction_version_id
        from organisation.jurisdiction_resolution_override
        where tenant_id=?
          and approval_status='APPROVED'
          and effective_from<=?
          and (effective_to is null or effective_to>?)
          and (
            (
              ? is not null
              and target_kind='WORK_LOCATION'
              and work_location_version_id=?
            )
            or
            (
              ? is not null
              and target_kind='ESTABLISHMENT'
              and establishment_version_id=?
            )
          )
        order by
          case target_kind
            when 'WORK_LOCATION' then 0
            else 1
          end,
          id
        """,
        (rs, row) ->
            new OverrideFact(
                rs.getObject("id", UUID.class),
                rs.getString("target_kind"),
                rs.getObject(
                    "payroll_jurisdiction_id",
                    UUID.class),
                rs.getObject(
                    "payroll_jurisdiction_version_id",
                    UUID.class)),
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        workLocationVersionId,
        workLocationVersionId,
        establishmentVersionId,
        establishmentVersionId);
  }

  public List<JurisdictionFact> establishmentFallback(
      UUID establishmentVersionId,
      LocalDate asOf) {
    if (establishmentVersionId == null) {
      return List.of();
    }
    return jdbc.query(
        """
        select
          jurisdiction.id payroll_jurisdiction_id,
          jurisdiction_version.id payroll_jurisdiction_version_id
        from organisation.establishment_version establishment_version
        join organisation.payroll_statutory_unit_version unit_version
          on unit_version.tenant_id=establishment_version.tenant_id
         and unit_version.id=
             establishment_version.payroll_statutory_unit_version_id
        join organisation.legal_entity_version legal_version
          on legal_version.tenant_id=unit_version.tenant_id
         and legal_version.id=unit_version.legal_entity_version_id
        join organisation.payroll_jurisdiction jurisdiction
          on jurisdiction.tenant_id=establishment_version.tenant_id
         and jurisdiction.status='ACTIVE'
        join organisation.payroll_jurisdiction_version jurisdiction_version
          on jurisdiction_version.tenant_id=jurisdiction.tenant_id
         and jurisdiction_version.payroll_jurisdiction_id=jurisdiction.id
        where establishment_version.tenant_id=?
          and establishment_version.id=?
          and establishment_version.approval_status='APPROVED'
          and establishment_version.effective_from<=?
          and (
            establishment_version.effective_to is null
            or establishment_version.effective_to>?
          )
          and unit_version.approval_status='APPROVED'
          and legal_version.approval_status='APPROVED'
          and jurisdiction_version.approval_status='APPROVED'
          and jurisdiction_version.effective_from<=?
          and (
            jurisdiction_version.effective_to is null
            or jurisdiction_version.effective_to>?
          )
          and not exists (
            select 1
            from organisation.payroll_jurisdiction_version successor
            where successor.tenant_id=jurisdiction_version.tenant_id
              and successor.supersedes_version_id=
                  jurisdiction_version.id
          )
          and (
            (
              establishment_version.state_code is not null
              and jurisdiction.code=
                  upper(establishment_version.state_code)
              and jurisdiction_version.level_code='STATE'
              and jurisdiction_version.country_code=
                  legal_version.country_code
            )
            or
            (
              establishment_version.state_code is null
              and jurisdiction.code=
                  upper(legal_version.country_code)
              and jurisdiction_version.level_rank=1
              and jurisdiction_version.country_code=
                  legal_version.country_code
            )
          )
        order by jurisdiction.id
        """,
        (rs, row) ->
            new JurisdictionFact(
                rs.getObject(
                    "payroll_jurisdiction_id",
                    UUID.class),
                rs.getObject(
                    "payroll_jurisdiction_version_id",
                    UUID.class)),
        TenantContext.require(),
        establishmentVersionId,
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public UUID insertEvidence(
      LocalDate asOf,
      UUID workLocationVersionId,
      UUID establishmentVersionId,
      UUID overrideId,
      UUID resolvedJurisdictionId,
      UUID resolvedJurisdictionVersionId,
      String source,
      String status,
      String inputFingerprint,
      String resultFingerprint,
      String findingCodesJson,
      String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into organisation.jurisdiction_resolution_evidence(
          id,
          tenant_id,
          as_of_date,
          work_location_version_id,
          establishment_version_id,
          override_id,
          resolved_jurisdiction_id,
          resolved_jurisdiction_version_id,
          resolution_source,
          resolution_status,
          input_fingerprint,
          result_fingerprint,
          finding_codes,
          created_by
        ) values (
          ?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?
        )
        """,
        id,
        TenantContext.require(),
        Date.valueOf(asOf),
        workLocationVersionId,
        establishmentVersionId,
        overrideId,
        resolvedJurisdictionId,
        resolvedJurisdictionVersionId,
        source,
        status,
        inputFingerprint,
        resultFingerprint,
        findingCodesJson,
        actor);
    return id;
  }

  private JurisdictionOverrideView mapOverride(
      ResultSet rs,
      int row) throws SQLException {
    Timestamp approvedAt = rs.getTimestamp("approved_at");
    return new JurisdictionOverrideView(
        rs.getObject("id", UUID.class),
        rs.getLong("version_no"),
        rs.getString("target_kind"),
        rs.getObject("work_location_version_id", UUID.class),
        rs.getObject("establishment_version_id", UUID.class),
        rs.getObject("payroll_jurisdiction_id", UUID.class),
        rs.getObject(
            "payroll_jurisdiction_version_id",
            UUID.class),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("reason"),
        rs.getString("approval_status"),
        rs.getString("created_by"),
        approvedAt == null ? null : approvedAt.toInstant(),
        rs.getString("approved_by"));
  }

  private RuntimeException translate(
      DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    return switch (state) {
      case "23P01" ->
          conflict(
              "Approved jurisdiction overrides cannot overlap",
              exception);
      case "23503" ->
          conflict(
              "The override target or jurisdiction does not exist",
              exception);
      case "23514" ->
          conflict(
              "The override target, jurisdiction or effective dates are invalid",
              exception);
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:jurisdiction-resolution-forbidden",
              "Jurisdiction resolution operation forbidden",
              "The jurisdiction resolution operation is not permitted",
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

  private OrganisationProblemException conflict(
      String detail) {
    return conflict(detail, null);
  }

  private OrganisationProblemException conflict(
      String detail,
      Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:jurisdiction-resolution-conflict",
        "Jurisdiction resolution conflict",
        detail,
        cause);
  }

  public record WorkLocationFact(
      UUID workLocationVersionId,
      UUID establishmentVersionId,
      UUID jurisdictionId,
      UUID jurisdictionVersionId) {}

  public record OverrideFact(
      UUID overrideId,
      String targetKind,
      UUID jurisdictionId,
      UUID jurisdictionVersionId) {}

  public record JurisdictionFact(
      UUID jurisdictionId,
      UUID jurisdictionVersionId) {}
}
