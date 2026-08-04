package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.ComponentBaseMembershipView;
import com.acme.hrms.payroll.compensation.ComponentBaseMembershipWriteRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseCreateRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseVersionWriteRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollBaseRepository {
  private static final String BASE_SELECT = """
      select i.id identity_id,
             i.code,
             i.name,
             i.lifecycle_status,
             i.ownership_scope,
             i.country_code,
             i.protected_flag,
             i.confidentiality_level,
             i.version_no identity_version_no,
             i.retirement_effective_date,
             i.retirement_reason,
             i.retired_at,
             i.retired_by,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.catalogue_schema_version,
             v.base_category,
             v.aggregation_method,
             v.description,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.supersedes_version_id,
             exists(
               select 1
               from compensation.payroll_base_version successor
               where successor.tenant_id=v.tenant_id
                 and successor.supersedes_version_id=v.id
             ) superseded
      from compensation.payroll_base i
      join compensation.payroll_base_version v
        on v.tenant_id=i.tenant_id
       and v.payroll_base_id=i.id
      """;

  private static final String MEMBERSHIP_SELECT = """
      select m.id membership_id,
             m.payroll_base_id,
             m.payroll_base_version_id,
             b.code payroll_base_code,
             bv.version_sequence payroll_base_version_sequence,
             m.component_id,
             m.component_version_id,
             c.code::text component_code,
             c.name component_name,
             cv.version_sequence component_version_sequence,
             m.membership_sequence,
             m.version_no,
             m.membership_type,
             m.inclusion_percent,
             m.effective_from,
             m.effective_to,
             m.approval_status,
             m.supersedes_membership_id,
             exists(
               select 1
               from compensation.component_base_membership successor
               where successor.tenant_id=m.tenant_id
                 and successor.supersedes_membership_id=m.id
             ) superseded
      from compensation.component_base_membership m
      join compensation.payroll_base b
        on b.tenant_id=m.tenant_id
       and b.id=m.payroll_base_id
      join compensation.payroll_base_version bv
        on bv.tenant_id=m.tenant_id
       and bv.id=m.payroll_base_version_id
       and bv.payroll_base_id=m.payroll_base_id
      join compensation.pay_component c
        on c.tenant_id=m.tenant_id
       and c.id=m.component_id
      join compensation.pay_component_version cv
        on cv.tenant_id=m.tenant_id
       and cv.id=m.component_version_id
       and cv.component_id=m.component_id
      """;

  private final JdbcTemplate jdbc;

  public PayrollBaseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollBaseView create(PayrollBaseCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.payroll_base(
          id, tenant_id, code, name, lifecycle_status, ownership_scope,
          country_code, protected_flag, confidentiality_level,
          created_by, updated_by
        ) values (?,?,?,?,'PENDING_APPROVAL',?,?,?,?,?,?)
        """,
        identityId,
        TenantContext.require(),
        request.code(),
        request.name().trim(),
        request.resolvedOwnershipScope(),
        blankToNull(request.countryCode()),
        request.resolvedProtectedFlag(),
        request.resolvedConfidentialityLevel(),
        actor,
        actor);

    insertVersion(versionId, identityId, 1, null, request.version(), actor);
    return version(versionId);
  }

  public PayrollBaseView addVersion(
      UUID identityId,
      PayrollBaseVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockBase(identityId);
    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from compensation.payroll_base_version
        where tenant_id=? and payroll_base_id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);
    UUID versionId = UUID.randomUUID();
    insertVersion(
        versionId,
        identityId,
        next == null ? 1 : next,
        supersedes,
        request,
        actor);
    return version(versionId);
  }

  public PayrollBaseView version(UUID versionId) {
    return jdbc.query(
            BASE_SELECT + " where v.tenant_id=? and v.id=?",
            this::mapBase,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll-base version was not found"));
  }

  public List<PayrollBaseView> list(LocalDate asOf) {
    return jdbc.query(
        BASE_SELECT
            + """
               where i.tenant_id=?
                 and (i.lifecycle_status<>'RETIRED'
                      or i.retirement_effective_date>?)
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
                 and not exists (
                   select 1
                   from compensation.payroll_base_version successor
                   where successor.tenant_id=v.tenant_id
                     and successor.supersedes_version_id=v.id
                 )
               order by i.code
               """,
        this::mapBase,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayrollBaseView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(item -> item.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved payroll-base version is effective on " + asOf));
  }

  public List<PayrollBaseView> history(UUID identityId) {
    ensureBase(identityId);
    return jdbc.query(
        BASE_SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence
               """,
        this::mapBase,
        TenantContext.require(),
        identityId);
  }

  public PayrollBaseView latest(UUID identityId) {
    return history(identityId).stream()
        .max(Comparator.comparingInt(PayrollBaseView::versionSequence))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll-base version was not found"));
  }

  public PayrollBaseView approve(UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_payroll_base_version(?,?,?,?)",
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Payroll-base version is not an approvable draft; "
              + "the checker must differ from the maker");
    }
    return version(versionId);
  }

  public PayrollBaseView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.end_date_payroll_base_version(?,?,?,?,?,?)",
        Long.class,
        TenantContext.require(),
        versionId,
        Date.valueOf(effectiveTo),
        expectedVersion,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Payroll-base version changed or cannot be end-dated at the requested date");
    }
    return version(versionId);
  }

  public PayrollBaseView retire(
      UUID identityId,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.retire_payroll_base(?,?,?,?,?,?,?)",
        Long.class,
        TenantContext.require(),
        identityId,
        Date.valueOf(effectiveDate),
        expectedVersion,
        reason,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      PayrollBaseView current = latest(identityId);
      if (!"RETIRED".equals(current.lifecycleStatus())) {
        throw new ConflictException(
            "Payroll base changed or has active/future approved dependencies");
      }
    }
    return latest(identityId);
  }

  public ComponentBaseMembershipView addMembership(
      UUID payrollBaseId,
      ComponentBaseMembershipWriteRequest request,
      UUID supersedes,
      String actor) {
    lockBase(payrollBaseId);
    ensureBaseVersion(payrollBaseId, request.payrollBaseVersionId());
    ensureComponentVersion(request.componentId(), request.componentVersionId());

    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(membership_sequence),0)+1
        from compensation.component_base_membership
        where tenant_id=? and payroll_base_id=? and component_id=?
        """,
        Integer.class,
        TenantContext.require(),
        payrollBaseId,
        request.componentId());

    UUID membershipId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_base_membership(
          id, tenant_id, payroll_base_id, payroll_base_version_id,
          component_id, component_version_id, membership_sequence,
          membership_type, inclusion_percent, effective_from, effective_to,
          approval_status, supersedes_membership_id, created_by, updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        membershipId,
        TenantContext.require(),
        payrollBaseId,
        request.payrollBaseVersionId(),
        request.componentId(),
        request.componentVersionId(),
        next == null ? 1 : next,
        request.membershipType(),
        request.inclusionPercent(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
    return membership(membershipId);
  }

  public ComponentBaseMembershipView membership(UUID membershipId) {
    return jdbc.query(
            MEMBERSHIP_SELECT + " where m.tenant_id=? and m.id=?",
            this::mapMembership,
            TenantContext.require(),
            membershipId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Component/base membership was not found"));
  }

  public List<ComponentBaseMembershipView> membershipHistory(UUID payrollBaseId) {
    ensureBase(payrollBaseId);
    return jdbc.query(
        MEMBERSHIP_SELECT
            + """
               where m.tenant_id=? and m.payroll_base_id=?
               order by c.code, m.membership_sequence
               """,
        this::mapMembership,
        TenantContext.require(),
        payrollBaseId);
  }

  public List<ComponentBaseMembershipView> memberships(
      UUID payrollBaseId, LocalDate asOf) {
    ensureBase(payrollBaseId);
    return jdbc.query(
        MEMBERSHIP_SELECT
            + """
               where m.tenant_id=?
                 and m.payroll_base_id=?
                 and m.approval_status='APPROVED'
                 and m.effective_from<=?
                 and (m.effective_to is null or m.effective_to>?)
                 and not exists (
                   select 1
                   from compensation.component_base_membership successor
                   where successor.tenant_id=m.tenant_id
                     and successor.supersedes_membership_id=m.id
                 )
               order by c.code
               """,
        this::mapMembership,
        TenantContext.require(),
        payrollBaseId,
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public ComponentBaseMembershipView approveMembership(
      UUID membershipId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_component_base_membership(?,?,?,?)",
        Long.class,
        TenantContext.require(),
        membershipId,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Membership is not approvable; maker-checker and approved exact versions are required");
    }
    return membership(membershipId);
  }

  public ComponentBaseMembershipView endDateMembership(
      UUID membershipId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.end_date_component_base_membership(?,?,?,?,?,?)",
        Long.class,
        TenantContext.require(),
        membershipId,
        Date.valueOf(effectiveTo),
        expectedVersion,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Membership changed or cannot be end-dated at the requested date");
    }
    return membership(membershipId);
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      PayrollBaseVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.payroll_base_version(
          id, tenant_id, payroll_base_id, version_sequence,
          catalogue_schema_version, base_category, aggregation_method,
          description, effective_from, effective_to, approval_status,
          supersedes_version_id, created_by, updated_by
        ) values (?,?,?,?,1,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.baseCategory(),
        request.aggregationMethod(),
        blankToNull(request.description()),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private void ensureBase(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.payroll_base where tenant_id=? and id=?",
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Payroll-base identity was not found");
    }
  }

  private void lockBase(UUID identityId) {
    UUID tenantId = TenantContext.require();
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        result -> null,
        tenantId + ":payroll-base:" + identityId);
    List<String> statuses = jdbc.query(
        """
        select lifecycle_status
        from compensation.payroll_base
        where tenant_id=? and id=?
        """,
        (result, row) -> result.getString(1),
        tenantId,
        identityId);
    if (statuses.isEmpty()) {
      throw new ResourceNotFoundException("Payroll-base identity was not found");
    }
    if ("RETIRED".equals(statuses.get(0))) {
      throw new ConflictException("Retired payroll bases cannot accept new versions");
    }
  }

  private void ensureBaseVersion(UUID identityId, UUID versionId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*) from compensation.payroll_base_version
        where tenant_id=? and payroll_base_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId,
        versionId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Payroll-base version does not belong to the identity");
    }
  }

  private void ensureComponentVersion(UUID componentId, UUID versionId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*) from compensation.pay_component_version
        where tenant_id=? and component_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        componentId,
        versionId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Pay-component version does not belong to the identity");
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private PayrollBaseView mapBase(ResultSet result, int row) throws SQLException {
    Timestamp retiredAt = result.getTimestamp("retired_at");
    return new PayrollBaseView(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("name"),
        result.getString("lifecycle_status"),
        result.getString("ownership_scope"),
        result.getString("country_code"),
        result.getBoolean("protected_flag"),
        result.getString("confidentiality_level"),
        result.getLong("identity_version_no"),
        result.getObject("retirement_effective_date", LocalDate.class),
        result.getString("retirement_reason"),
        retiredAt == null ? null : retiredAt.toInstant(),
        result.getString("retired_by"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getInt("catalogue_schema_version"),
        result.getString("base_category"),
        result.getString("aggregation_method"),
        result.getString("description"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private ComponentBaseMembershipView mapMembership(
      ResultSet result, int row) throws SQLException {
    return new ComponentBaseMembershipView(
        result.getObject("membership_id", UUID.class),
        result.getObject("payroll_base_id", UUID.class),
        result.getObject("payroll_base_version_id", UUID.class),
        result.getString("payroll_base_code"),
        result.getInt("payroll_base_version_sequence"),
        result.getObject("component_id", UUID.class),
        result.getObject("component_version_id", UUID.class),
        result.getString("component_code"),
        result.getString("component_name"),
        result.getInt("component_version_sequence"),
        result.getInt("membership_sequence"),
        result.getLong("version_no"),
        result.getString("membership_type"),
        result.getBigDecimal("inclusion_percent"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_membership_id", UUID.class),
        result.getBoolean("superseded"));
  }
}
