package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.PayGroupView;
import com.acme.hrms.payroll.compensation.PayGroupWriteRequest;
import com.acme.hrms.payroll.platform.ConflictException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayGroupRepository {
  private static final String SELECT = """
      select i.id identity_id,
             i.code,
             i.status identity_status,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.name,
             v.payroll_statutory_unit_version_id,
             v.calendar_id,
             v.currency::text currency,
             v.proration_method,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.supersedes_version_id,
             exists(
               select 1
               from organisation.pay_group_version successor
               where successor.tenant_id = v.tenant_id
                 and successor.supersedes_version_id = v.id
             ) superseded
      from organisation.pay_group i
      join organisation.pay_group_version v
        on v.tenant_id = i.tenant_id
       and v.pay_group_id = i.id
      """;

  private final JdbcTemplate jdbc;

  public PayGroupRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayGroupView create(
      PayGroupWriteRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into organisation.pay_group(
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
        request,
        actor);

    return history(identityId).stream()
        .filter(view -> view.versionId().equals(versionId))
        .findFirst()
        .orElseThrow();
  }

  public PayGroupView addVersion(
      UUID identityId,
      PayGroupWriteRequest request,
      UUID supersedes,
      String actor) {
    ensureIdentity(identityId);
    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from organisation.pay_group_version
        where tenant_id=? and pay_group_id=?
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

    return history(identityId).stream()
        .filter(view -> view.versionId().equals(versionId))
        .findFirst()
        .orElseThrow();
  }

  public PayGroupView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pay-group version was not found"));
  }

  public List<PayGroupView> list(LocalDate asOf) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=?
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
                 and not exists (
                   select 1
                   from organisation.pay_group_version successor
                   where successor.tenant_id=v.tenant_id
                     and successor.supersedes_version_id=v.id
                 )
               order by i.code
               """,
        this::map,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayGroupView current(
      UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved pay-group version is effective on "
                + asOf));
  }

  public List<PayGroupView> history(UUID identityId) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence
               """,
        this::map,
        TenantContext.require(),
        identityId);
  }

  public PayGroupView approve(
      UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select organisation.approve_pay_group_version(?,?,?,?)
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Pay-group version is not an approvable draft");
    }
    return version(versionId);
  }

  public PayGroupView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select organisation.end_date_pay_group_version(
          ?,?,?,?,?,?
        )
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        Date.valueOf(effectiveTo),
        expectedVersion,
        actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Pay-group version changed or cannot be "
              + "end-dated at the requested date");
    }
    return version(versionId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from organisation.pay_group
        where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Pay-group identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      PayGroupWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into organisation.pay_group_version(
          id,
          tenant_id,
          pay_group_id,
          payroll_statutory_unit_version_id,
          calendar_id,
          version_sequence,
          name,
          currency,
          proration_method,
          effective_from,
          effective_to,
          approval_status,
          supersedes_version_id,
          created_by,
          updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        request.payrollStatutoryUnitVersionId(),
        request.calendarId(),
        sequence,
        request.name(),
        request.resolvedCurrency(),
        request.resolvedProrationMethod(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private PayGroupView map(
      ResultSet result, int row) throws SQLException {
    return new PayGroupView(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("identity_status"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getObject(
            "payroll_statutory_unit_version_id",
            UUID.class),
        result.getObject("calendar_id", UUID.class),
        result.getString("currency"),
        result.getString("proration_method"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject(
            "supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }
}
