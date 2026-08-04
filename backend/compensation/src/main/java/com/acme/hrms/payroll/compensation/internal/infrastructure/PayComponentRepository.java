package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.PayComponentCreateRequest;
import com.acme.hrms.payroll.compensation.PayComponentVersionWriteRequest;
import com.acme.hrms.payroll.compensation.PayComponentView;
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
             case when v.catalogue_schema_version=0
                  then 'LEGACY_INCOMPLETE' else 'COMPLETE' end classification_status,
             v.formula_type,
             v.formula_expression,
             v.fixed_amount,
             v.rounding_scale,
             v.component_category,
             v.component_subcategory,
             v.cash_impact,
             v.payee_type,
             v.payment_channel,
             v.settlement_timing,
             v.payslip_visibility,
             v.zero_value_visibility,
             v.negative_value_policy,
             v.frequency,
             v.value_nature,
             v.amount_representation,
             v.tax_treatment,
             v.payroll_timing,
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

  public PayComponentView create(PayComponentCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.pay_component(
          id, tenant_id, code, name, component_type, lifecycle_status,
          ownership_scope, country_code, protected_flag, confidentiality_level,
          created_by, updated_by
        ) values (?,?,?,?,?,'PENDING_APPROVAL',?,?,?,?,?,?)
        """,
        identityId,
        TenantContext.require(),
        request.code(),
        request.name().trim(),
        request.componentType(),
        request.resolvedOwnershipScope(),
        blankToNull(request.countryCode()),
        request.resolvedProtectedFlag(),
        request.resolvedConfidentialityLevel(),
        actor,
        actor);

    insertVersion(versionId, identityId, 1, null, request.version(), actor);
    return version(versionId);
  }

  public PayComponentView addVersion(
      UUID identityId,
      PayComponentVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockIdentity(identityId);

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
    return version(versionId);
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
                 and (i.lifecycle_status<>'RETIRED'
                      or i.retirement_effective_date>?)
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
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayComponentView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved pay-component version is effective on " + asOf));
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

  public PayComponentView latest(UUID identityId) {
    return history(identityId).stream()
        .max(java.util.Comparator.comparingInt(PayComponentView::versionSequence))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pay-component version was not found"));
  }

  public PayComponentView approve(UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_pay_component_version(?,?,?,?)",
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Pay-component version is not an approvable schema-1 draft; "
              + "the checker must differ from the maker");
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
        select compensation.end_date_pay_component_version(?,?,?,?,?,?)
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
          "Pay-component version changed or cannot be end-dated at the requested date");
    }
    return version(versionId);
  }

  public PayComponentView retire(
      UUID identityId,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.retire_pay_component(?,?,?,?,?,?,?)",
        Long.class,
        TenantContext.require(),
        identityId,
        Date.valueOf(effectiveDate),
        expectedVersion,
        reason,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      PayComponentView current = latest(identityId);
      if (!"RETIRED".equals(current.lifecycleStatus())) {
        throw new ConflictException(
            "Pay component changed or has active/future approved dependencies");
      }
    }
    return latest(identityId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.pay_component where tenant_id=? and id=?",
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Pay-component identity was not found");
    }
  }

  private void lockIdentity(UUID identityId) {
    List<String> statuses = jdbc.query(
        """
        select lifecycle_status
        from compensation.pay_component
        where tenant_id=? and id=?
        for update
        """,
        (result, row) -> result.getString(1),
        TenantContext.require(),
        identityId);
    if (statuses.isEmpty()) {
      throw new ResourceNotFoundException("Pay-component identity was not found");
    }
    if ("RETIRED".equals(statuses.get(0))) {
      throw new ConflictException("Retired pay components cannot accept new versions");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      PayComponentVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.pay_component_version(
          id, tenant_id, component_id, version_sequence,
          formula_type, formula_expression, fixed_amount, rounding_scale,
          catalogue_schema_version, component_category, component_subcategory,
          cash_impact, payee_type, payment_channel, settlement_timing,
          payslip_visibility, zero_value_visibility, negative_value_policy,
          frequency, value_nature, amount_representation, tax_treatment,
          payroll_timing, effective_from, effective_to, approval_status,
          supersedes_version_id, created_by, updated_by
        ) values (?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.formulaType(),
        blankToNull(request.formulaExpression()),
        request.fixedAmount(),
        request.resolvedRoundingScale(),
        request.componentCategory(),
        blankToNull(request.componentSubcategory()),
        request.cashImpact(),
        request.payeeType(),
        request.paymentChannel(),
        request.settlementTiming(),
        request.payslipVisibility(),
        request.zeroValueVisibility(),
        request.negativeValuePolicy(),
        request.frequency(),
        request.valueNature(),
        request.amountRepresentation(),
        request.taxTreatment(),
        request.payrollTiming(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private PayComponentView map(ResultSet result, int row) throws SQLException {
    Timestamp retiredAt = result.getTimestamp("retired_at");
    return new PayComponentView(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("name"),
        result.getString("component_type"),
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
        result.getString("classification_status"),
        result.getString("formula_type"),
        result.getString("formula_expression"),
        result.getBigDecimal("fixed_amount"),
        result.getInt("rounding_scale"),
        result.getString("component_category"),
        result.getString("component_subcategory"),
        result.getString("cash_impact"),
        result.getString("payee_type"),
        result.getString("payment_channel"),
        result.getString("settlement_timing"),
        result.getString("payslip_visibility"),
        result.getString("zero_value_visibility"),
        result.getString("negative_value_policy"),
        result.getString("frequency"),
        result.getString("value_nature"),
        result.getString("amount_representation"),
        result.getString("tax_treatment"),
        result.getString("payroll_timing"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }
}
