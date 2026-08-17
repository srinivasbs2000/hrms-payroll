package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitOptionView;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitOptionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanCreateRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class FlexBenefitPlanRepository {
  private static final String SELECT = """
      select p.id identity_id,
             p.code,
             p.lifecycle_status,
             p.version_no identity_version_no,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.name,
             v.currency::text currency,
             v.supplemental_plan_id,
             v.supplemental_plan_version_id,
             sp.code supplemental_plan_code,
             spv.name supplemental_plan_name,
             spv.version_sequence supplemental_plan_version_sequence,
             erv.eligibility_rule_id,
             v.eligibility_rule_version_id,
             er.code eligibility_rule_code,
             v.annual_basket_amount,
             v.election_window_start,
             v.election_window_end,
             v.mid_year_joining_rule,
             v.joining_election_window_days,
             v.mid_year_change_rule,
             v.unused_balance_rule,
             v.carry_forward_limit,
             v.taxable_fallback_component_version_id,
             v.encashment_component_version_id,
             v.final_settlement_rule,
             v.retro_correction_rule,
             v.allow_total_compensation_change,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.approved_at,
             v.approved_by,
             v.supersedes_version_id,
             exists(
               select 1
                 from compensation.flex_benefit_plan_version successor
                where successor.tenant_id=v.tenant_id
                  and successor.supersedes_version_id=v.id
             ) superseded,
             o.id option_id,
             o.component_id,
             o.component_version_id,
             pc.code::text component_code,
             pc.name component_name,
             o.option_sequence,
             o.minimum_annual_amount,
             o.maximum_annual_amount,
             o.default_annual_amount,
             o.proof_required,
             o.version_no option_version_no
        from compensation.flex_benefit_plan p
        join compensation.flex_benefit_plan_version v
          on v.tenant_id=p.tenant_id
         and v.flex_benefit_plan_id=p.id
        join compensation.salary_supplemental_plan sp
          on sp.tenant_id=v.tenant_id
         and sp.id=v.supplemental_plan_id
        join compensation.salary_supplemental_plan_version spv
          on spv.tenant_id=v.tenant_id
         and spv.id=v.supplemental_plan_version_id
        left join compensation.eligibility_rule_version erv
          on erv.tenant_id=v.tenant_id
         and erv.id=v.eligibility_rule_version_id
        left join compensation.eligibility_rule er
          on er.tenant_id=erv.tenant_id
         and er.id=erv.eligibility_rule_id
        left join compensation.flex_benefit_option o
          on o.tenant_id=v.tenant_id
         and o.flex_benefit_plan_version_id=v.id
        left join compensation.pay_component pc
          on pc.tenant_id=o.tenant_id
         and pc.id=o.component_id
      """;

  private final JdbcTemplate jdbc;

  public FlexBenefitPlanRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public FlexBenefitPlanView create(FlexBenefitPlanCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.flex_benefit_plan(
          id,tenant_id,code,lifecycle_status,created_by,updated_by
        ) values (?,?,?,'PENDING_APPROVAL',?,?)
        """,
        identityId, TenantContext.require(), request.code(), actor, actor);
    insertVersion(versionId, identityId, 1, null, request.version(), actor);
    return version(versionId);
  }

  public FlexBenefitPlanView addVersion(
      UUID identityId, FlexBenefitPlanVersionWriteRequest request,
      UUID supersedes, String actor) {
    lockIdentity(identityId);
    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
          from compensation.flex_benefit_plan_version
         where tenant_id=? and flex_benefit_plan_id=?
        """,
        Integer.class, TenantContext.require(), identityId);
    UUID versionId = UUID.randomUUID();
    insertVersion(versionId, identityId, next == null ? 1 : next, supersedes, request, actor);
    return version(versionId);
  }

  public FlexBenefitPlanView version(UUID versionId) {
    return query(
            SELECT + """
               where v.tenant_id=? and v.id=?
               order by o.option_sequence
               """,
            TenantContext.require(), versionId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Flex-benefit plan version was not found"));
  }

  public List<FlexBenefitPlanView> list(LocalDate asOf) {
    return query(
        SELECT + """
           where p.tenant_id=?
             and p.lifecycle_status<>'RETIRED'
             and v.approval_status='APPROVED'
             and v.effective_from<=?
             and (v.effective_to is null or v.effective_to>?)
           order by p.code,o.option_sequence
           """,
        TenantContext.require(), asOf, asOf);
  }

  public FlexBenefitPlanView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream().filter(view -> view.identityId().equals(identityId)).findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved flex-benefit plan version is effective on " + asOf));
  }

  public List<FlexBenefitPlanView> history(UUID identityId) {
    ensureIdentity(identityId);
    return query(
        SELECT + """
           where p.tenant_id=? and p.id=?
           order by v.version_sequence,o.option_sequence
           """,
        TenantContext.require(), identityId);
  }

  public FlexBenefitPlanView approve(UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_flex_benefit_plan_version(?,?,?,?)",
        Long.class, TenantContext.require(), versionId, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Flex-benefit plan is not an approvable complete draft; the checker must differ "
              + "from the maker and pinned benefit/component/eligibility versions must be approved");
    }
    return version(versionId);
  }

  private void lockIdentity(UUID identityId) {
    List<String> statuses = jdbc.query(
        "select compensation.lock_flex_benefit_plan(?,?)",
        (result, row) -> result.getString(1), TenantContext.require(), identityId);
    if (statuses.isEmpty() || statuses.get(0) == null) {
      throw new ResourceNotFoundException("Flex-benefit plan identity was not found");
    }
    if ("RETIRED".equals(statuses.get(0))) {
      throw new ConflictException("Retired flex-benefit plans cannot accept new versions");
    }
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.flex_benefit_plan where tenant_id=? and id=?",
        Integer.class, TenantContext.require(), identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Flex-benefit plan identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId, UUID identityId, int sequence, UUID supersedes,
      FlexBenefitPlanVersionWriteRequest request, String actor) {
    int inserted = jdbc.update(
        """
        insert into compensation.flex_benefit_plan_version(
          id,tenant_id,flex_benefit_plan_id,version_sequence,name,currency,
          supplemental_plan_id,supplemental_plan_version_id,eligibility_rule_version_id,
          annual_basket_amount,election_window_start,election_window_end,
          mid_year_joining_rule,joining_election_window_days,mid_year_change_rule,
          unused_balance_rule,carry_forward_limit,taxable_fallback_component_version_id,
          encashment_component_version_id,final_settlement_rule,retro_correction_rule,
          allow_total_compensation_change,effective_from,effective_to,approval_status,
          supersedes_version_id,created_by,updated_by
        )
        select ?,?,?,?,?,'INR',spv.supplemental_plan_id,?,?,
               ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?
          from compensation.salary_supplemental_plan_version spv
         where spv.tenant_id=? and spv.id=? and spv.plan_type='BENEFIT'
        """,
        versionId, TenantContext.require(), identityId, sequence, request.name().trim(),
        request.supplementalPlanVersionId(), request.eligibilityRuleVersionId(),
        request.annualBasketAmount(), request.electionWindowStart(), request.electionWindowEnd(),
        request.midYearJoiningRule(), request.joiningElectionWindowDays(), request.midYearChangeRule(),
        request.unusedBalanceRule(), request.carryForwardLimit(),
        request.taxableFallbackComponentVersionId(), request.encashmentComponentVersionId(),
        request.finalSettlementRule(), request.retroCorrectionRule(),
        request.allowTotalCompensationChange(), request.effectiveFrom(), request.effectiveTo(),
        supersedes, actor, actor, TenantContext.require(), request.supplementalPlanVersionId());
    if (inserted != 1) {
      throw new ResourceNotFoundException("Referenced BENEFIT supplemental-plan version was not found");
    }
    for (FlexBenefitOptionWriteRequest option : request.options()) {
      insertOption(identityId, versionId, request, option, actor);
    }
  }

  private void insertOption(
      UUID identityId, UUID versionId, FlexBenefitPlanVersionWriteRequest request,
      FlexBenefitOptionWriteRequest option, String actor) {
    int inserted = jdbc.update(
        """
        insert into compensation.flex_benefit_option(
          id,tenant_id,flex_benefit_plan_id,flex_benefit_plan_version_id,
          component_id,component_version_id,option_sequence,minimum_annual_amount,
          maximum_annual_amount,default_annual_amount,proof_required,created_by,updated_by
        )
        select ?,?,?,?,pcv.component_id,pcv.id,?,?,?,?,?,?,?
          from compensation.pay_component_version pcv
          join compensation.salary_supplemental_plan_line spl
            on spl.tenant_id=pcv.tenant_id
           and spl.component_version_id=pcv.id
           and spl.supplemental_plan_version_id=?
         where pcv.tenant_id=? and pcv.id=?
        """,
        UUID.randomUUID(), TenantContext.require(), identityId, versionId,
        option.optionSequence(), option.resolvedMinimumAnnualAmount(), option.maximumAnnualAmount(),
        option.resolvedDefaultAnnualAmount(), option.proofRequired(), actor, actor,
        request.supplementalPlanVersionId(), TenantContext.require(), option.componentVersionId());
    if (inserted != 1) {
      throw new ResourceNotFoundException(
          "Flex-benefit option must reference an exact component version from the selected BENEFIT supplemental-plan version");
    }
  }

  private List<FlexBenefitPlanView> query(String sql, Object... arguments) {
    ResultSetExtractor<List<FlexBenefitPlanView>> extractor = this::extract;
    return jdbc.query(sql, extractor, arguments);
  }

  private List<FlexBenefitPlanView> extract(ResultSet result) throws SQLException {
    Map<UUID, MutableVersion> versions = new LinkedHashMap<>();
    while (result.next()) {
      UUID versionId = result.getObject("version_id", UUID.class);
      MutableVersion mutable = versions.get(versionId);
      if (mutable == null) {
        mutable = header(result);
        versions.put(versionId, mutable);
      }
      UUID optionId = result.getObject("option_id", UUID.class);
      if (optionId != null) mutable.options.add(option(result, optionId));
    }
    return versions.values().stream().map(MutableVersion::toView).toList();
  }

  private MutableVersion header(ResultSet r) throws SQLException {
    Timestamp approvedAt = r.getTimestamp("approved_at");
    return new MutableVersion(
        r.getObject("identity_id", UUID.class), r.getString("code"), r.getString("lifecycle_status"),
        r.getLong("identity_version_no"), r.getObject("version_id", UUID.class),
        r.getInt("version_sequence"), r.getLong("version_no"), r.getString("name"), r.getString("currency"),
        r.getObject("supplemental_plan_id", UUID.class), r.getObject("supplemental_plan_version_id", UUID.class),
        r.getString("supplemental_plan_code"), r.getString("supplemental_plan_name"),
        r.getInt("supplemental_plan_version_sequence"), r.getObject("eligibility_rule_id", UUID.class),
        r.getObject("eligibility_rule_version_id", UUID.class),
        r.getString("eligibility_rule_code"), r.getBigDecimal("annual_basket_amount"),
        r.getObject("election_window_start", LocalDate.class), r.getObject("election_window_end", LocalDate.class),
        r.getString("mid_year_joining_rule"), (Integer) r.getObject("joining_election_window_days"),
        r.getString("mid_year_change_rule"), r.getString("unused_balance_rule"), r.getBigDecimal("carry_forward_limit"),
        r.getObject("taxable_fallback_component_version_id", UUID.class),
        r.getObject("encashment_component_version_id", UUID.class), r.getString("final_settlement_rule"),
        r.getString("retro_correction_rule"), r.getBoolean("allow_total_compensation_change"),
        r.getObject("effective_from", LocalDate.class), r.getObject("effective_to", LocalDate.class),
        r.getString("approval_status"), approvedAt == null ? null : approvedAt.toInstant(),
        r.getString("approved_by"), r.getObject("supersedes_version_id", UUID.class), r.getBoolean("superseded"));
  }

  private FlexBenefitOptionView option(ResultSet r, UUID optionId) throws SQLException {
    return new FlexBenefitOptionView(
        optionId, r.getObject("component_id", UUID.class), r.getObject("component_version_id", UUID.class),
        r.getString("component_code"), r.getString("component_name"), r.getInt("option_sequence"),
        r.getBigDecimal("minimum_annual_amount"), r.getBigDecimal("maximum_annual_amount"),
        r.getBigDecimal("default_annual_amount"), r.getBoolean("proof_required"), r.getLong("option_version_no"));
  }

  private static final class MutableVersion {
    private final UUID identityId; private final String code; private final String lifecycleStatus;
    private final long identityVersionNo; private final UUID versionId; private final int versionSequence;
    private final long versionNo; private final String name; private final String currency;
    private final UUID supplementalPlanId; private final UUID supplementalPlanVersionId;
    private final String supplementalPlanCode; private final String supplementalPlanName;
    private final int supplementalPlanVersionSequence; private final UUID eligibilityRuleId; private final UUID eligibilityRuleVersionId;
    private final String eligibilityRuleCode; private final java.math.BigDecimal annualBasketAmount;
    private final LocalDate electionWindowStart; private final LocalDate electionWindowEnd;
    private final String midYearJoiningRule; private final Integer joiningElectionWindowDays;
    private final String midYearChangeRule; private final String unusedBalanceRule;
    private final java.math.BigDecimal carryForwardLimit; private final UUID taxableFallbackComponentVersionId;
    private final UUID encashmentComponentVersionId; private final String finalSettlementRule;
    private final String retroCorrectionRule; private final boolean allowTotalCompensationChange;
    private final LocalDate effectiveFrom; private final LocalDate effectiveTo; private final String approvalStatus;
    private final Instant approvedAt; private final String approvedBy; private final UUID supersedesVersionId;
    private final boolean superseded; private final List<FlexBenefitOptionView> options = new ArrayList<>();

    private MutableVersion(
        UUID identityId, String code, String lifecycleStatus, long identityVersionNo,
        UUID versionId, int versionSequence, long versionNo, String name, String currency,
        UUID supplementalPlanId, UUID supplementalPlanVersionId, String supplementalPlanCode,
        String supplementalPlanName, int supplementalPlanVersionSequence, UUID eligibilityRuleId, UUID eligibilityRuleVersionId,
        String eligibilityRuleCode, java.math.BigDecimal annualBasketAmount, LocalDate electionWindowStart,
        LocalDate electionWindowEnd, String midYearJoiningRule, Integer joiningElectionWindowDays,
        String midYearChangeRule, String unusedBalanceRule, java.math.BigDecimal carryForwardLimit,
        UUID taxableFallbackComponentVersionId, UUID encashmentComponentVersionId,
        String finalSettlementRule, String retroCorrectionRule, boolean allowTotalCompensationChange,
        LocalDate effectiveFrom, LocalDate effectiveTo, String approvalStatus, Instant approvedAt,
        String approvedBy, UUID supersedesVersionId, boolean superseded) {
      this.identityId=identityId;this.code=code;this.lifecycleStatus=lifecycleStatus;
      this.identityVersionNo=identityVersionNo;this.versionId=versionId;this.versionSequence=versionSequence;
      this.versionNo=versionNo;this.name=name;this.currency=currency;this.supplementalPlanId=supplementalPlanId;
      this.supplementalPlanVersionId=supplementalPlanVersionId;this.supplementalPlanCode=supplementalPlanCode;
      this.supplementalPlanName=supplementalPlanName;this.supplementalPlanVersionSequence=supplementalPlanVersionSequence;
      this.eligibilityRuleId=eligibilityRuleId;this.eligibilityRuleVersionId=eligibilityRuleVersionId;this.eligibilityRuleCode=eligibilityRuleCode;
      this.annualBasketAmount=annualBasketAmount;this.electionWindowStart=electionWindowStart;
      this.electionWindowEnd=electionWindowEnd;this.midYearJoiningRule=midYearJoiningRule;
      this.joiningElectionWindowDays=joiningElectionWindowDays;this.midYearChangeRule=midYearChangeRule;
      this.unusedBalanceRule=unusedBalanceRule;this.carryForwardLimit=carryForwardLimit;
      this.taxableFallbackComponentVersionId=taxableFallbackComponentVersionId;
      this.encashmentComponentVersionId=encashmentComponentVersionId;this.finalSettlementRule=finalSettlementRule;
      this.retroCorrectionRule=retroCorrectionRule;this.allowTotalCompensationChange=allowTotalCompensationChange;
      this.effectiveFrom=effectiveFrom;this.effectiveTo=effectiveTo;this.approvalStatus=approvalStatus;
      this.approvedAt=approvedAt;this.approvedBy=approvedBy;this.supersedesVersionId=supersedesVersionId;
      this.superseded=superseded;
    }
    private FlexBenefitPlanView toView() {
      return new FlexBenefitPlanView(
          identityId,code,lifecycleStatus,identityVersionNo,versionId,versionSequence,versionNo,name,currency,
          supplementalPlanId,supplementalPlanVersionId,supplementalPlanCode,supplementalPlanName,
          supplementalPlanVersionSequence,eligibilityRuleId,eligibilityRuleVersionId,eligibilityRuleCode,annualBasketAmount,
          electionWindowStart,electionWindowEnd,midYearJoiningRule,joiningElectionWindowDays,midYearChangeRule,
          unusedBalanceRule,carryForwardLimit,taxableFallbackComponentVersionId,encashmentComponentVersionId,
          finalSettlementRule,retroCorrectionRule,allowTotalCompensationChange,effectiveFrom,effectiveTo,
          approvalStatus,approvedAt,approvedBy,supersedesVersionId,superseded,List.copyOf(options));
    }
  }
}
