package com.acme.hrms.payroll.statutory.internal.infrastructure;

import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import com.acme.hrms.payroll.statutory.RegistrationReadinessRequest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationReadinessRepository {
  private final JdbcTemplate jdbc;

  public RegistrationReadinessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Candidate> effectiveCandidates(
      RegistrationReadinessRequest request) {
    return jdbc.query(
        """
        select
          version.id,
          version.lifecycle_status,
          version.effective_to,
          version.parent_registration_version_id,
          parent.lifecycle_status parent_status,
          parent.effective_from parent_from,
          parent.effective_to parent_to
        from statutory.registration_version version
        left join statutory.registration_version parent
          on parent.tenant_id=version.tenant_id
         and parent.id=version.parent_registration_version_id
        where version.tenant_id=?
          and version.registration_type_id=?
          and version.owner_kind=?
          and version.owner_key=?
          and version.payroll_jurisdiction_id=?
          and version.effective_from<=?
          and (version.effective_to is null or version.effective_to>?)
          and version.lifecycle_status in ('ACTIVE','SUSPENDED')
        order by version.id
        """,
        (rs, row) ->
            new Candidate(
                rs.getObject("id", UUID.class),
                rs.getString("lifecycle_status"),
                rs.getObject("effective_to", LocalDate.class),
                rs.getObject("parent_registration_version_id", UUID.class),
                rs.getString("parent_status"),
                rs.getObject("parent_from", LocalDate.class),
                rs.getObject("parent_to", LocalDate.class)),
        TenantContext.require(),
        request.registrationTypeId(),
        request.ownerKind().name(),
        ownerKey(request.ownerKind(), request.ownerId()),
        request.payrollJurisdictionId(),
        Date.valueOf(request.asOf()),
        Date.valueOf(request.asOf()));
  }

  public boolean renewalDraftExists(
      RegistrationReadinessRequest request) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
            from statutory.registration_version version
            where version.tenant_id=?
              and version.registration_type_id=?
              and version.owner_kind=?
              and version.owner_key=?
              and version.payroll_jurisdiction_id=?
              and version.lifecycle_status in (
                'DRAFT',
                'PENDING_VERIFICATION',
                'VERIFIED',
                'APPROVAL_PENDING'
              )
              and version.effective_from>?
            """,
            Integer.class,
            TenantContext.require(),
            request.registrationTypeId(),
            request.ownerKind().name(),
            ownerKey(request.ownerKind(), request.ownerId()),
            request.payrollJurisdictionId(),
            Date.valueOf(request.asOf()));
    return count != null && count > 0;
  }

  public boolean expiredRegistrationExists(
      RegistrationReadinessRequest request) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
            from statutory.registration_version version
            where version.tenant_id=?
              and version.registration_type_id=?
              and version.owner_kind=?
              and version.owner_key=?
              and version.payroll_jurisdiction_id=?
              and (
                version.lifecycle_status='EXPIRED'
                or (
                  version.lifecycle_status='ACTIVE'
                  and version.effective_to is not null
                  and version.effective_to<=?
                )
              )
            """,
            Integer.class,
            TenantContext.require(),
            request.registrationTypeId(),
            request.ownerKind().name(),
            ownerKey(request.ownerKind(), request.ownerId()),
            request.payrollJurisdictionId(),
            Date.valueOf(request.asOf()));
    return count != null && count > 0;
  }

  private String ownerKey(
      RegistrationOwnerKind kind,
      UUID ownerId) {
    return kind.name() + ":" + ownerId;
  }

  public record Candidate(
      UUID versionId,
      String status,
      LocalDate effectiveTo,
      UUID parentVersionId,
      String parentStatus,
      LocalDate parentFrom,
      LocalDate parentTo) {}
}
