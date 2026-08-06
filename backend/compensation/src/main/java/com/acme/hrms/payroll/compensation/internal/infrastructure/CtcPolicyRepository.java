package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.CtcPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.CtcPolicyTreatmentView;
import com.acme.hrms.payroll.compensation.CtcPolicyTreatmentWriteRequest;
import com.acme.hrms.payroll.compensation.CtcPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.CtcPolicyView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class CtcPolicyRepository {
  private static final String SELECT = """
      select i.id identity_id,
             i.code,
             i.lifecycle_status,
             i.version_no identity_version_no,
             i.retirement_effective_date,
             i.retirement_reason,
             i.retired_at,
             i.retired_by,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.name,
             v.currency::text currency,
             v.annualisation_method,
             v.tolerance_amount,
             v.residual_component_id,
             v.residual_component_version_id,
             residual.code::text residual_component_code,
             residual.name residual_component_name,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.supersedes_version_id,
             exists(
               select 1
               from compensation.ctc_policy_version successor
               where successor.tenant_id=v.tenant_id
                 and successor.supersedes_version_id=v.id
             ) superseded,
             t.id treatment_id,
             t.component_id,
             t.component_version_id,
             component.code::text component_code,
             component.name component_name,
             component_version.component_category,
             t.treatment_sequence,
             t.cost_view,
             t.treatment_type,
             t.fixed_value,
             t.target_percentage,
             t.payroll_base_id,
             t.payroll_base_version_id,
             payroll_base.code payroll_base_code,
             payroll_base.name payroll_base_name,
             t.effective_from treatment_effective_from,
             t.effective_to treatment_effective_to,
             t.version_no treatment_version_no
      from compensation.ctc_policy i
      join compensation.ctc_policy_version v
        on v.tenant_id=i.tenant_id
       and v.ctc_policy_id=i.id
      left join compensation.pay_component residual
        on residual.tenant_id=v.tenant_id
       and residual.id=v.residual_component_id
      left join compensation.ctc_policy_treatment t
        on t.tenant_id=v.tenant_id
       and t.ctc_policy_version_id=v.id
      left join compensation.pay_component_version component_version
        on component_version.tenant_id=t.tenant_id
       and component_version.id=t.component_version_id
      left join compensation.pay_component component
        on component.tenant_id=component_version.tenant_id
       and component.id=component_version.component_id
      left join compensation.payroll_base_version payroll_base_version
        on payroll_base_version.tenant_id=t.tenant_id
       and payroll_base_version.id=t.payroll_base_version_id
      left join compensation.payroll_base payroll_base
        on payroll_base.tenant_id=payroll_base_version.tenant_id
       and payroll_base.id=payroll_base_version.payroll_base_id
      """;

  private final JdbcTemplate jdbc;

  public CtcPolicyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CtcPolicyView create(
      CtcPolicyCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.ctc_policy(
          id,tenant_id,code,lifecycle_status,created_by,updated_by
        ) values (?,?,?,'PENDING_APPROVAL',?,?)
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
  }

  public CtcPolicyView addVersion(
      UUID identityId,
      CtcPolicyVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockIdentity(identityId);

    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from compensation.ctc_policy_version
        where tenant_id=? and ctc_policy_id=?
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

  public CtcPolicyView version(UUID versionId) {
    return query(
            SELECT
                + """
                   where v.tenant_id=? and v.id=?
                   order by t.treatment_sequence
                   """,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "CTC policy version was not found"));
  }

  public List<CtcPolicyView> list(LocalDate asOf) {
    return query(
        SELECT
            + """
               where i.tenant_id=?
                 and (i.lifecycle_status<>'RETIRED'
                      or i.retirement_effective_date>?)
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
               order by i.code,t.treatment_sequence
               """,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public CtcPolicyView current(
      UUID identityId,
      LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved CTC policy version is effective on " + asOf));
  }

  public List<CtcPolicyView> history(UUID identityId) {
    ensureIdentity(identityId);
    return query(
        SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence,t.treatment_sequence
               """,
        TenantContext.require(),
        identityId);
  }

  public CtcPolicyView latest(UUID identityId) {
    return history(identityId).stream()
        .max(Comparator.comparingInt(CtcPolicyView::versionSequence))
        .orElseThrow(() -> new ResourceNotFoundException(
            "CTC policy version was not found"));
  }

  public CtcPolicyView approve(
      UUID versionId,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_ctc_policy_version(?,?,?,?)",
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "CTC policy version is not an approvable complete draft; "
              + "the checker must differ from the maker");
    }
    return version(versionId);
  }

  public CtcPolicyView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.end_date_ctc_policy_version(
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
          "CTC policy version changed, is in use or cannot be end-dated "
              + "at the requested date");
    }
    return version(versionId);
  }

  public CtcPolicyView retire(
      UUID identityId,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.retire_ctc_policy(?,?,?,?,?,?,?)",
        Long.class,
        TenantContext.require(),
        identityId,
        Date.valueOf(effectiveDate),
        expectedVersion,
        reason,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      CtcPolicyView current = latest(identityId);
      if (!"RETIRED".equals(current.lifecycleStatus())) {
        throw new ConflictException(
            "CTC policy changed or has active/future approved dependencies");
      }
    }
    return latest(identityId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from compensation.ctc_policy
        where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "CTC policy identity was not found");
    }
  }

  private void lockIdentity(UUID identityId) {
    List<String> statuses = jdbc.query(
        """
        select lifecycle_status
        from compensation.ctc_policy
        where tenant_id=? and id=?
        for update
        """,
        (result, row) -> result.getString(1),
        TenantContext.require(),
        identityId);
    if (statuses.isEmpty()) {
      throw new ResourceNotFoundException(
          "CTC policy identity was not found");
    }
    if ("RETIRED".equals(statuses.get(0))) {
      throw new ConflictException(
          "Retired CTC policies cannot accept new versions");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      CtcPolicyVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.ctc_policy_version(
          id,tenant_id,ctc_policy_id,version_sequence,name,currency,
          annualisation_method,tolerance_amount,residual_component_id,
          residual_component_version_id,effective_from,effective_to,
          approval_status,supersedes_version_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name().trim(),
        request.resolvedCurrency(),
        request.annualisationMethod(),
        request.resolvedToleranceAmount(),
        request.residualComponentId(),
        request.residualComponentVersionId(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);

    for (CtcPolicyTreatmentWriteRequest treatment : request.treatments()) {
      insertTreatment(
          identityId,
          versionId,
          request,
          treatment,
          actor);
    }
  }

  private void insertTreatment(
      UUID identityId,
      UUID versionId,
      CtcPolicyVersionWriteRequest request,
      CtcPolicyTreatmentWriteRequest treatment,
      String actor) {
    jdbc.update(
        """
        insert into compensation.ctc_policy_treatment(
          id,tenant_id,ctc_policy_id,ctc_policy_version_id,
          component_id,component_version_id,treatment_sequence,cost_view,
          treatment_type,fixed_value,target_percentage,payroll_base_id,
          payroll_base_version_id,effective_from,effective_to,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        TenantContext.require(),
        identityId,
        versionId,
        treatment.componentId(),
        treatment.componentVersionId(),
        treatment.treatmentSequence(),
        treatment.costView(),
        treatment.treatmentType(),
        treatment.fixedValue(),
        treatment.targetPercentage(),
        treatment.payrollBaseId(),
        treatment.payrollBaseVersionId(),
        treatment.resolvedEffectiveFrom(request.effectiveFrom()),
        treatment.resolvedEffectiveTo(request.effectiveTo()),
        actor,
        actor);
  }

  private List<CtcPolicyView> query(
      String sql,
      Object... arguments) {
    ResultSetExtractor<List<CtcPolicyView>> extractor =
        this::extract;
    return jdbc.query(sql, extractor, arguments);
  }

  private List<CtcPolicyView> extract(
      ResultSet result) throws SQLException {
    Map<UUID, MutableVersion> versions =
        new LinkedHashMap<>();

    while (result.next()) {
      UUID versionId = result.getObject("version_id", UUID.class);
      MutableVersion mutable = versions.get(versionId);
      if (mutable == null) {
        mutable = header(result);
        versions.put(versionId, mutable);
      }
      UUID treatmentId =
          result.getObject("treatment_id", UUID.class);
      if (treatmentId != null) {
        mutable.treatments.add(treatment(result, treatmentId));
      }
    }
    return versions.values().stream()
        .map(MutableVersion::toView)
        .toList();
  }

  private MutableVersion header(
      ResultSet result) throws SQLException {
    Timestamp retiredAt = result.getTimestamp("retired_at");
    return new MutableVersion(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("lifecycle_status"),
        result.getLong("identity_version_no"),
        result.getObject("retirement_effective_date", LocalDate.class),
        result.getString("retirement_reason"),
        retiredAt == null ? null : retiredAt.toInstant(),
        result.getString("retired_by"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("currency"),
        result.getString("annualisation_method"),
        result.getBigDecimal("tolerance_amount"),
        result.getObject("residual_component_id", UUID.class),
        result.getObject("residual_component_version_id", UUID.class),
        result.getString("residual_component_code"),
        result.getString("residual_component_name"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private CtcPolicyTreatmentView treatment(
      ResultSet result,
      UUID treatmentId) throws SQLException {
    return new CtcPolicyTreatmentView(
        treatmentId,
        result.getObject("component_id", UUID.class),
        result.getObject("component_version_id", UUID.class),
        result.getString("component_code"),
        result.getString("component_name"),
        result.getString("component_category"),
        result.getInt("treatment_sequence"),
        result.getString("cost_view"),
        result.getString("treatment_type"),
        result.getBigDecimal("fixed_value"),
        result.getBigDecimal("target_percentage"),
        result.getObject("payroll_base_id", UUID.class),
        result.getObject("payroll_base_version_id", UUID.class),
        result.getString("payroll_base_code"),
        result.getString("payroll_base_name"),
        result.getObject("treatment_effective_from", LocalDate.class),
        result.getObject("treatment_effective_to", LocalDate.class),
        result.getLong("treatment_version_no"));
  }

  private static final class MutableVersion {
    private final UUID identityId;
    private final String code;
    private final String lifecycleStatus;
    private final long identityVersionNo;
    private final LocalDate retirementEffectiveDate;
    private final String retirementReason;
    private final Instant retiredAt;
    private final String retiredBy;
    private final UUID versionId;
    private final int versionSequence;
    private final long versionNo;
    private final String name;
    private final String currency;
    private final String annualisationMethod;
    private final java.math.BigDecimal toleranceAmount;
    private final UUID residualComponentId;
    private final UUID residualComponentVersionId;
    private final String residualComponentCode;
    private final String residualComponentName;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String approvalStatus;
    private final UUID supersedesVersionId;
    private final boolean superseded;
    private final List<CtcPolicyTreatmentView> treatments =
        new ArrayList<>();

    private MutableVersion(
        UUID identityId,
        String code,
        String lifecycleStatus,
        long identityVersionNo,
        LocalDate retirementEffectiveDate,
        String retirementReason,
        Instant retiredAt,
        String retiredBy,
        UUID versionId,
        int versionSequence,
        long versionNo,
        String name,
        String currency,
        String annualisationMethod,
        java.math.BigDecimal toleranceAmount,
        UUID residualComponentId,
        UUID residualComponentVersionId,
        String residualComponentCode,
        String residualComponentName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String approvalStatus,
        UUID supersedesVersionId,
        boolean superseded) {
      this.identityId = identityId;
      this.code = code;
      this.lifecycleStatus = lifecycleStatus;
      this.identityVersionNo = identityVersionNo;
      this.retirementEffectiveDate = retirementEffectiveDate;
      this.retirementReason = retirementReason;
      this.retiredAt = retiredAt;
      this.retiredBy = retiredBy;
      this.versionId = versionId;
      this.versionSequence = versionSequence;
      this.versionNo = versionNo;
      this.name = name;
      this.currency = currency;
      this.annualisationMethod = annualisationMethod;
      this.toleranceAmount = toleranceAmount;
      this.residualComponentId = residualComponentId;
      this.residualComponentVersionId = residualComponentVersionId;
      this.residualComponentCode = residualComponentCode;
      this.residualComponentName = residualComponentName;
      this.effectiveFrom = effectiveFrom;
      this.effectiveTo = effectiveTo;
      this.approvalStatus = approvalStatus;
      this.supersedesVersionId = supersedesVersionId;
      this.superseded = superseded;
    }

    private CtcPolicyView toView() {
      return new CtcPolicyView(
          identityId,
          code,
          lifecycleStatus,
          identityVersionNo,
          retirementEffectiveDate,
          retirementReason,
          retiredAt,
          retiredBy,
          versionId,
          versionSequence,
          versionNo,
          name,
          currency,
          annualisationMethod,
          toleranceAmount,
          residualComponentId,
          residualComponentVersionId,
          residualComponentCode,
          residualComponentName,
          effectiveFrom,
          effectiveTo,
          approvalStatus,
          supersedesVersionId,
          superseded,
          List.copyOf(treatments));
    }
  }
}
