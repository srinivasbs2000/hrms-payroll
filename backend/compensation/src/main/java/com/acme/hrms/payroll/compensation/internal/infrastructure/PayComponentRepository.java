package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.PayComponentView;
import com.acme.hrms.payroll.compensation.PayComponentWriteRequest;
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
public class PayComponentRepository {
  private static final String SELECT = """
      select i.id identity_id,
             i.code::text code,
             i.name,
             i.component_type,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.formula_type,
             v.formula_expression,
             v.fixed_amount,
             v.rounding_scale,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.supersedes_version_id,
             exists(
               select 1
               from compensation.pay_component_version successor
               where successor.tenant_id = v.tenant_id
                 and successor.supersedes_version_id = v.id
             ) superseded
      from compensation.pay_component i
      join compensation.pay_component_version v
        on v.tenant_id = i.tenant_id
       and v.component_id = i.id
      """;

  private final JdbcTemplate jdbc;

  public PayComponentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayComponentView create(
      PayComponentWriteRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.pay_component(
          id,
          tenant_id,
          code,
          name,
          component_type,
          created_by,
          updated_by
        ) values (?,?,?,?,?,?,?)
        """,
        identityId,
        TenantContext.require(),
        request.code(),
        request.name().trim(),
        request.componentType(),
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

  public PayComponentView addVersion(
      UUID identityId,
      PayComponentWriteRequest request,
      UUID supersedes,
      String actor) {
    ensureIdentity(identityId);

    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from compensation.pay_component_version
        where tenant_id=? and component_id=?
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

  public PayComponentView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pay-component version was not found"));
  }

  public List<PayComponentView> list(LocalDate asOf) {
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=?
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
                 and not exists (
                   select 1
                   from compensation.pay_component_version successor
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

  public PayComponentView current(
      UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved pay-component version is effective on "
                + asOf));
  }

  public List<PayComponentView> history(UUID identityId) {
    ensureIdentity(identityId);
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

  public PayComponentView approve(
      UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.approve_pay_component_version(?,?,?,?)
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Pay-component version is not an approvable draft");
    }

    return version(versionId);
  }

  public PayComponentView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.end_date_pay_component_version(
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
          "Pay-component version changed or cannot be "
              + "end-dated at the requested date");
    }

    return version(versionId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from compensation.pay_component
        where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);

    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Pay-component identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      PayComponentWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.pay_component_version(
          id,
          tenant_id,
          component_id,
          version_sequence,
          formula_type,
          formula_expression,
          fixed_amount,
          rounding_scale,
          effective_from,
          effective_to,
          approval_status,
          supersedes_version_id,
          created_by,
          updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.formulaType(),
        blankToNull(request.formulaExpression()),
        request.fixedAmount(),
        request.resolvedRoundingScale(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private PayComponentView map(
      ResultSet result, int row) throws SQLException {
    return new PayComponentView(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("name"),
        result.getString("component_type"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("formula_type"),
        result.getString("formula_expression"),
        result.getBigDecimal("fixed_amount"),
        result.getInt("rounding_scale"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject(
            "supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }
}