package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaDependencyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateCellRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateCellView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateDimensionRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateDimensionView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateLookupView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.StatutoryWageReferenceRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.StatutoryWageReferenceView;
import com.acme.hrms.payroll.compensation.PayComponentView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComponentCatalogueControlRepository {
  private static final String RATE_SELECT = """
      select i.id identity_id, i.code, i.name, i.lifecycle_status,
             i.version_no identity_version_no,
             v.id version_id, v.version_sequence, v.version_no,
             v.effective_from, v.effective_to, v.approval_status,
             v.supersedes_version_id,
             exists(select 1 from compensation.component_rate_table_version s
                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id) superseded
        from compensation.component_rate_table i
        join compensation.component_rate_table_version v
          on v.tenant_id=i.tenant_id and v.rate_table_id=i.id
      """;

  private static final String ROUNDING_SELECT = """
      select i.id identity_id, i.component_id, c.code::text component_code,
             i.lifecycle_status, i.version_no identity_version_no,
             v.id version_id, v.version_sequence, v.version_no,
             v.rounding_method, v.rounding_scale, v.rounding_stage,
             v.negative_treatment, v.effective_from, v.effective_to,
             v.approval_status, v.supersedes_version_id,
             exists(select 1 from compensation.component_rounding_policy_version s
                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id) superseded
        from compensation.component_rounding_policy i
        join compensation.pay_component c
          on c.tenant_id=i.tenant_id and c.id=i.component_id
        join compensation.component_rounding_policy_version v
          on v.tenant_id=i.tenant_id and v.policy_id=i.id
      """;

  private static final String PRORATION_SELECT = """
      select i.id identity_id, i.component_id, c.code::text component_code,
             i.event_type, i.lifecycle_status, i.version_no identity_version_no,
             v.id version_id, v.version_sequence, v.version_no,
             v.proration_method, v.proration_basis, v.effective_from, v.effective_to,
             v.approval_status, v.supersedes_version_id,
             exists(select 1 from compensation.component_proration_policy_version s
                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id) superseded
        from compensation.component_proration_policy i
        join compensation.pay_component c
          on c.tenant_id=i.tenant_id and c.id=i.component_id
        join compensation.component_proration_policy_version v
          on v.tenant_id=i.tenant_id and v.policy_id=i.id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ComponentCatalogueControlRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public boolean formulaExists(UUID componentVersionId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.component_formula_metadata where tenant_id=? and component_version_id=?",
        Integer.class,
        TenantContext.require(),
        componentVersionId);
    return count != null && count > 0;
  }

  public void persistFormula(
      PayComponentView component,
      String formulaType,
      String phase,
      String resultContract,
      String canonicalExpression,
      String fingerprint,
      List<DependencyTarget> dependencies,
      String actor) {
    UUID metadataId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_formula_metadata(
          id, tenant_id, component_id, component_version_id, formula_type,
          calculation_phase, result_contract, canonical_expression,
          formula_fingerprint, dependency_count, created_by
        ) values (?,?,?,?,?,?,?,?,?,?,?)
        """,
        metadataId,
        TenantContext.require(),
        component.identityId(),
        component.versionId(),
        formulaType,
        phase,
        resultContract,
        canonicalExpression,
        fingerprint,
        dependencies.size(),
        actor);

    int order = 1;
    for (DependencyTarget dependency : dependencies) {
      jdbc.update(
          """
          insert into compensation.component_formula_dependency(
            id, tenant_id, formula_metadata_id, component_id, component_version_id,
            dependency_component_id, dependency_component_version_id,
            dependency_code, dependency_order, dependency_phase, created_by
          ) values (?,?,?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          TenantContext.require(),
          metadataId,
          component.identityId(),
          component.versionId(),
          dependency.componentId(),
          dependency.componentVersionId(),
          dependency.code(),
          order++,
          dependency.phase(),
          actor);
    }
  }

  public DependencyTarget resolveApprovedDependency(String code, LocalDate asOf) {
    return jdbc.query(
            """
            select i.id component_id, v.id component_version_id, i.code::text code,
                   coalesce(m.calculation_phase,
                     case when v.formula_type='FIXED' then 'INPUT' else 'PRE_TAX' end) calculation_phase
              from compensation.pay_component i
              join compensation.pay_component_version v
                on v.tenant_id=i.tenant_id and v.component_id=i.id
              left join compensation.component_formula_metadata m
                on m.tenant_id=v.tenant_id and m.component_version_id=v.id
             where i.tenant_id=? and i.code=?
               and i.lifecycle_status='ACTIVE'
               and v.approval_status='APPROVED'
               and v.effective_from<=?
               and (v.effective_to is null or v.effective_to>?)
               and not exists (
                 select 1 from compensation.pay_component_version s
                  where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
             order by v.version_sequence desc
             limit 1
            """,
            (result, row) -> new DependencyTarget(
                result.getObject("component_id", UUID.class),
                result.getObject("component_version_id", UUID.class),
                result.getString("code"),
                result.getString("calculation_phase")),
            TenantContext.require(),
            code,
            Date.valueOf(asOf),
            Date.valueOf(asOf))
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "UNKNOWN_DEPENDENCY: no approved component version is effective for " + code));
  }

  public Map<String, Object> formulaEvidence(UUID componentVersionId) {
    Map<String, Object> evidence = jdbc.query(
            """
            select calculation_phase,result_contract,canonical_expression,formula_fingerprint,dependency_count
              from compensation.component_formula_metadata
             where tenant_id=? and component_version_id=?
            """,
            (result, row) -> {
              Map<String, Object> values = new LinkedHashMap<>();
              values.put("calculationPhase", result.getString("calculation_phase"));
              values.put("resultContract", result.getString("result_contract"));
              values.put("canonicalFormula", result.getString("canonical_expression"));
              values.put("canonicalFormulaFingerprint", result.getString("formula_fingerprint"));
              values.put("formulaDependencyCount", result.getInt("dependency_count"));
              return values;
            },
            TenantContext.require(),
            componentVersionId)
        .stream().findFirst().orElseGet(LinkedHashMap::new);

    Integer wageReferenceCount = jdbc.queryForObject(
        "select count(*) from compensation.component_statutory_wage_reference "
            + "where tenant_id=? and component_version_id=?",
        Integer.class, TenantContext.require(), componentVersionId);
    evidence.put("statutoryWageReferenceCount", wageReferenceCount == null ? 0 : wageReferenceCount);
    return evidence;
  }

  public void persistStatutoryWageReferences(
      PayComponentView component,
      List<StatutoryWageReferenceRequest> references,
      String actor) {
    for (StatutoryWageReferenceRequest reference : references) {
      String ruleSql = """
          select r.id statutory_rule_id, rv.id statutory_rule_version_id,
                 r.code statutory_rule_code, r.rule_category,
                 rv.effective_from rule_effective_from, rv.effective_to rule_effective_to
            from statutory.statutory_rule r
            join statutory.statutory_rule_version rv
              on rv.tenant_id=r.tenant_id and rv.statutory_rule_id=r.id
           where r.tenant_id=? and r.id=? and rv.id=?
             and r.status='ACTIVE' and rv.approval_status='APPROVED'
             and rv.effective_from<=?
             and %s
             and not exists (
               select 1 from statutory.statutory_rule_version successor
                where successor.tenant_id=rv.tenant_id
                  and successor.supersedes_version_id=rv.id)
          """.formatted(
              component.effectiveTo() == null
                  ? "rv.effective_to is null"
                  : "(rv.effective_to is null or ?<=rv.effective_to)");
      List<Object> ruleArguments = new ArrayList<>();
      ruleArguments.add(TenantContext.require());
      ruleArguments.add(reference.statutoryRuleId());
      ruleArguments.add(reference.statutoryRuleVersionId());
      ruleArguments.add(Date.valueOf(component.effectiveFrom()));
      if (component.effectiveTo() != null) {
        ruleArguments.add(Date.valueOf(component.effectiveTo()));
      }
      StatutoryWageReferenceView resolved = jdbc.query(
              ruleSql,
              (result, row) -> new StatutoryWageReferenceView(
                  component.identityId(), component.versionId(),
                  result.getObject("statutory_rule_id", UUID.class),
                  result.getObject("statutory_rule_version_id", UUID.class),
                  result.getString("statutory_rule_code"), result.getString("rule_category"),
                  result.getObject("rule_effective_from", LocalDate.class),
                  result.getObject("rule_effective_to", LocalDate.class)),
              ruleArguments.toArray())
          .stream().findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              "STATUTORY_WAGE_REFERENCE_INVALID: exact approved statutory rule version "
                  + "must cover the pay-component effective range"));

      jdbc.update(
          """
          insert into compensation.component_statutory_wage_reference(
            id,tenant_id,component_id,component_version_id,statutory_rule_id,
            statutory_rule_version_id,created_by)
          values (?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(), TenantContext.require(), component.identityId(),
          component.versionId(), resolved.statutoryRuleId(), resolved.statutoryRuleVersionId(), actor);
    }
  }

  public List<StatutoryWageReferenceView> statutoryWageReferences(UUID componentId) {
    ensureComponent(componentId);
    return jdbc.query(
        """
        select w.component_id,w.component_version_id,w.statutory_rule_id,
               w.statutory_rule_version_id,r.code statutory_rule_code,r.rule_category,
               rv.effective_from rule_effective_from,rv.effective_to rule_effective_to
          from compensation.component_statutory_wage_reference w
          join statutory.statutory_rule r
            on r.tenant_id=w.tenant_id and r.id=w.statutory_rule_id
          join statutory.statutory_rule_version rv
            on rv.tenant_id=w.tenant_id and rv.id=w.statutory_rule_version_id
           and rv.statutory_rule_id=w.statutory_rule_id
         where w.tenant_id=? and w.component_id=?
         order by w.created_at,r.code
        """,
        (result, row) -> new StatutoryWageReferenceView(
            result.getObject("component_id", UUID.class),
            result.getObject("component_version_id", UUID.class),
            result.getObject("statutory_rule_id", UUID.class),
            result.getObject("statutory_rule_version_id", UUID.class),
            result.getString("statutory_rule_code"), result.getString("rule_category"),
            result.getObject("rule_effective_from", LocalDate.class),
            result.getObject("rule_effective_to", LocalDate.class)),
        TenantContext.require(), componentId);
  }

  public List<FormulaDependencyView> dependencies(UUID componentId) {
    ensureComponent(componentId);
    return jdbc.query(
        """
        select m.component_id, m.component_version_id, c.code::text component_code,
               m.calculation_phase, d.dependency_component_id,
               d.dependency_component_version_id, d.dependency_code,
               d.dependency_phase, d.dependency_order, m.formula_fingerprint
          from compensation.component_formula_metadata m
          join compensation.pay_component c
            on c.tenant_id=m.tenant_id and c.id=m.component_id
          join compensation.component_formula_dependency d
            on d.tenant_id=m.tenant_id and d.formula_metadata_id=m.id
         where m.tenant_id=? and m.component_id=?
         order by m.created_at, d.dependency_order
        """,
        (result, row) -> new FormulaDependencyView(
            result.getObject("component_id", UUID.class),
            result.getObject("component_version_id", UUID.class),
            result.getString("component_code"),
            result.getString("calculation_phase"),
            result.getObject("dependency_component_id", UUID.class),
            result.getObject("dependency_component_version_id", UUID.class),
            result.getString("dependency_code"),
            result.getString("dependency_phase"),
            result.getObject("dependency_order") == null ? 0 : result.getInt("dependency_order"),
            result.getString("formula_fingerprint")),
        TenantContext.require(),
        componentId);
  }

  public List<PlanningRow> planningRows() {
    return jdbc.query(
        """
        select distinct on (i.code) i.id component_id, i.code::text code,
               v.id component_version_id, v.formula_type, v.formula_expression,
               coalesce(m.calculation_phase,
                 case when v.formula_type='FIXED' then 'INPUT' else 'PRE_TAX' end) calculation_phase
          from compensation.pay_component i
          join compensation.pay_component_version v
            on v.tenant_id=i.tenant_id and v.component_id=i.id
          left join compensation.component_formula_metadata m
            on m.tenant_id=v.tenant_id and m.component_version_id=v.id
         where i.tenant_id=? and i.lifecycle_status<>'RETIRED'
           and (m.id is not null or v.formula_type='FIXED')
           and not exists (
             select 1 from compensation.pay_component_version s
              where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
         order by i.code, v.version_sequence desc
        """,
        (result, row) -> new PlanningRow(
            result.getObject("component_id", UUID.class),
            result.getString("code"),
            result.getObject("component_version_id", UUID.class),
            result.getString("formula_type"),
            result.getString("formula_expression"),
            result.getString("calculation_phase")),
        TenantContext.require());
  }

  public RateTableView createRateTable(RateTableCreateRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_rate_table(
          id,tenant_id,code,name,lifecycle_status,created_by,updated_by)
        values (?,?,?,?,'PENDING_APPROVAL',?,?)
        """,
        identityId,
        TenantContext.require(),
        request.code(),
        request.name().trim(),
        actor,
        actor);
    return addRateTableVersion(identityId, request.version(), null, actor);
  }

  public RateTableView addRateTableVersion(
      UUID identityId,
      RateTableVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockRateTable(identityId);
    Integer next = jdbc.queryForObject(
        "select coalesce(max(version_sequence),0)+1 from compensation.component_rate_table_version where tenant_id=? and rate_table_id=?",
        Integer.class,
        TenantContext.require(),
        identityId);
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_rate_table_version(
          id,tenant_id,rate_table_id,version_sequence,effective_from,effective_to,
          approval_status,supersedes_version_id,created_by,updated_by)
        values (?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        next == null ? 1 : next,
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
    int sequence = 1;
    for (RateDimensionRequest dimension : request.dimensions()) {
      jdbc.update(
          """
          insert into compensation.component_rate_dimension(
            id,tenant_id,rate_table_version_id,dimension_sequence,code,name,data_type,created_by)
          values (?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(), TenantContext.require(), versionId, sequence++,
          dimension.code(), dimension.name().trim(), dimension.dataType(), actor);
    }
    sequence = 1;
    for (RateCellRequest cell : request.cells()) {
      jdbc.update(
          """
          insert into compensation.component_rate_cell(
            id,tenant_id,rate_table_version_id,cell_sequence,dimension_values,rate_value,created_by)
          values (?,?,?,?,cast(? as jsonb),?,?)
          """,
          UUID.randomUUID(), TenantContext.require(), versionId, sequence++,
          canonicalJson(cell.dimensionValues()), cell.rateValue(), actor);
    }
    return rateTableVersion(versionId);
  }

  public RateTableView rateTableVersion(UUID versionId) {
    RateTableView shell = jdbc.query(
            RATE_SELECT + " where v.tenant_id=? and v.id=?",
            this::mapRateShell,
            TenantContext.require(), versionId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Component rate-table version was not found"));
    return withRateChildren(shell);
  }

  public RateTableView currentRateTable(UUID identityId, LocalDate asOf) {
    return jdbc.query(
            RATE_SELECT
                + """
                  where i.tenant_id=? and i.id=? and i.lifecycle_status='ACTIVE'
                    and v.approval_status='APPROVED' and v.effective_from<=?
                    and (v.effective_to is null or v.effective_to>?)
                    and not exists (select 1 from compensation.component_rate_table_version s
                                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
                  order by v.version_sequence desc limit 1
                  """,
            this::mapRateShell,
            TenantContext.require(), identityId, Date.valueOf(asOf), Date.valueOf(asOf))
        .stream().findFirst().map(this::withRateChildren)
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved component rate-table version is effective on " + asOf));
  }

  public List<RateTableView> listRateTables(LocalDate asOf) {
    List<RateTableView> shells = jdbc.query(
        RATE_SELECT
            + """
              where i.tenant_id=? and i.lifecycle_status='ACTIVE'
                and v.approval_status='APPROVED' and v.effective_from<=?
                and (v.effective_to is null or v.effective_to>?)
                and not exists (select 1 from compensation.component_rate_table_version s
                                where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
              order by i.code
              """,
        this::mapRateShell,
        TenantContext.require(), Date.valueOf(asOf), Date.valueOf(asOf));
    return shells.stream().map(this::withRateChildren).toList();
  }

  public List<RateTableView> rateTableHistory(UUID identityId) {
    ensureRateTable(identityId);
    return jdbc.query(
            RATE_SELECT + " where i.tenant_id=? and i.id=? order by v.version_sequence",
            this::mapRateShell,
            TenantContext.require(), identityId)
        .stream().map(this::withRateChildren).toList();
  }

  public RateTableView approveRateTable(
      UUID versionId, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_component_rate_table_version(?,?,?,?,?)",
        Long.class,
        TenantContext.require(), versionId, expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Rate-table version changed or is not approvable; checker must differ from maker");
    }
    return rateTableVersion(versionId);
  }

  public RateTableView endDateRateTable(
      UUID versionId, LocalDate effectiveTo, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.end_date_component_rate_table_version(?,?,?,?,?,?)",
        Long.class, TenantContext.require(), versionId, Date.valueOf(effectiveTo),
        expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Rate-table version changed or cannot be end-dated at the requested date");
    }
    return rateTableVersion(versionId);
  }

  public RateLookupView lookupRate(UUID identityId, LocalDate asOf, Map<String, String> dimensions) {
    RateTableView version = currentRateTable(identityId, asOf);
    String json = canonicalJson(dimensions);
    RateCellView cell = jdbc.query(
            """
            select id,cell_sequence,dimension_values,rate_value
              from compensation.component_rate_cell
             where tenant_id=? and rate_table_version_id=? and dimension_values=cast(? as jsonb)
            """,
            this::mapCell,
            TenantContext.require(), version.versionId(), json)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("No rate-table cell matches the supplied dimensions"));
    return new RateLookupView(
        identityId, version.versionId(), cell.dimensionValues(), cell.rateValue(),
        version.effectiveFrom(), version.effectiveTo());
  }

  public RoundingPolicyView createRoundingPolicy(RoundingPolicyCreateRequest request, String actor) {
    ensureComponent(request.componentId());
    UUID identityId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_rounding_policy(
          id,tenant_id,component_id,lifecycle_status,created_by,updated_by)
        values (?,?,?,'PENDING_APPROVAL',?,?)
        """,
        identityId, TenantContext.require(), request.componentId(), actor, actor);
    return addRoundingPolicyVersion(identityId, request.version(), null, actor);
  }

  public RoundingPolicyView addRoundingPolicyVersion(
      UUID identityId,
      RoundingPolicyVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockRoundingPolicy(identityId);
    Integer next = jdbc.queryForObject(
        "select coalesce(max(version_sequence),0)+1 from compensation.component_rounding_policy_version where tenant_id=? and policy_id=?",
        Integer.class, TenantContext.require(), identityId);
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_rounding_policy_version(
          id,tenant_id,policy_id,version_sequence,rounding_method,rounding_scale,
          rounding_stage,negative_treatment,effective_from,effective_to,
          approval_status,supersedes_version_id,created_by,updated_by)
        values (?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId, TenantContext.require(), identityId, next == null ? 1 : next,
        request.method(), request.scale(), request.stage(), request.negativeTreatment(),
        request.effectiveFrom(), request.effectiveTo(), supersedes, actor, actor);
    return roundingVersion(versionId);
  }

  public RoundingPolicyView roundingVersion(UUID versionId) {
    return jdbc.query(
            ROUNDING_SELECT + " where v.tenant_id=? and v.id=?",
            this::mapRounding,
            TenantContext.require(), versionId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Component rounding-policy version was not found"));
  }

  public RoundingPolicyView currentRoundingPolicy(UUID identityId, LocalDate asOf) {
    return jdbc.query(
            ROUNDING_SELECT
                + """
                  where i.tenant_id=? and i.id=? and i.lifecycle_status='ACTIVE'
                    and v.approval_status='APPROVED' and v.effective_from<=?
                    and (v.effective_to is null or v.effective_to>?)
                    and not exists (select 1 from compensation.component_rounding_policy_version s
                                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
                  order by v.version_sequence desc limit 1
                  """,
            this::mapRounding,
            TenantContext.require(), identityId, Date.valueOf(asOf), Date.valueOf(asOf))
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved component rounding policy is effective on " + asOf));
  }

  public List<RoundingPolicyView> listRoundingPolicies(LocalDate asOf) {
    return jdbc.query(
        ROUNDING_SELECT
            + """
              where i.tenant_id=? and i.lifecycle_status='ACTIVE'
                and v.approval_status='APPROVED' and v.effective_from<=?
                and (v.effective_to is null or v.effective_to>?)
                and not exists (select 1 from compensation.component_rounding_policy_version s
                                where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
              order by c.code
              """,
        this::mapRounding,
        TenantContext.require(), Date.valueOf(asOf), Date.valueOf(asOf));
  }

  public List<RoundingPolicyView> roundingHistory(UUID identityId) {
    ensureRoundingPolicy(identityId);
    return jdbc.query(
        ROUNDING_SELECT + " where i.tenant_id=? and i.id=? order by v.version_sequence",
        this::mapRounding,
        TenantContext.require(), identityId);
  }

  public RoundingPolicyView approveRoundingPolicy(
      UUID versionId, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_component_rounding_policy_version(?,?,?,?,?)",
        Long.class,
        TenantContext.require(), versionId, expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Rounding-policy version changed or is not approvable; checker must differ from maker");
    }
    return roundingVersion(versionId);
  }

  public RoundingPolicyView endDateRoundingPolicy(
      UUID versionId, LocalDate effectiveTo, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.end_date_component_rounding_policy_version(?,?,?,?,?,?)",
        Long.class, TenantContext.require(), versionId, Date.valueOf(effectiveTo),
        expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Rounding-policy version changed or cannot be end-dated at the requested date");
    }
    return roundingVersion(versionId);
  }

  public ProrationPolicyView createProrationPolicy(ProrationPolicyCreateRequest request, String actor) {
    ensureComponent(request.componentId());
    UUID identityId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_proration_policy(
          id,tenant_id,component_id,event_type,lifecycle_status,created_by,updated_by)
        values (?,?,?,?,'PENDING_APPROVAL',?,?)
        """,
        identityId, TenantContext.require(), request.componentId(), request.eventType(), actor, actor);
    return addProrationPolicyVersion(identityId, request.version(), null, actor);
  }

  public ProrationPolicyView addProrationPolicyVersion(
      UUID identityId,
      ProrationPolicyVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockProrationPolicy(identityId);
    Integer next = jdbc.queryForObject(
        "select coalesce(max(version_sequence),0)+1 from compensation.component_proration_policy_version where tenant_id=? and policy_id=?",
        Integer.class, TenantContext.require(), identityId);
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into compensation.component_proration_policy_version(
          id,tenant_id,policy_id,version_sequence,proration_method,proration_basis,
          effective_from,effective_to,approval_status,supersedes_version_id,created_by,updated_by)
        values (?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId, TenantContext.require(), identityId, next == null ? 1 : next,
        request.method(), request.basis(), request.effectiveFrom(), request.effectiveTo(),
        supersedes, actor, actor);
    return prorationVersion(versionId);
  }

  public ProrationPolicyView prorationVersion(UUID versionId) {
    return jdbc.query(
            PRORATION_SELECT + " where v.tenant_id=? and v.id=?",
            this::mapProration,
            TenantContext.require(), versionId)
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Component proration-policy version was not found"));
  }

  public ProrationPolicyView currentProrationPolicy(UUID identityId, LocalDate asOf) {
    return jdbc.query(
            PRORATION_SELECT
                + """
                  where i.tenant_id=? and i.id=? and i.lifecycle_status='ACTIVE'
                    and v.approval_status='APPROVED' and v.effective_from<=?
                    and (v.effective_to is null or v.effective_to>?)
                    and not exists (select 1 from compensation.component_proration_policy_version s
                                    where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
                  order by v.version_sequence desc limit 1
                  """,
            this::mapProration,
            TenantContext.require(), identityId, Date.valueOf(asOf), Date.valueOf(asOf))
        .stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved component proration policy is effective on " + asOf));
  }

  public List<ProrationPolicyView> listProrationPolicies(LocalDate asOf) {
    return jdbc.query(
        PRORATION_SELECT
            + """
              where i.tenant_id=? and i.lifecycle_status='ACTIVE'
                and v.approval_status='APPROVED' and v.effective_from<=?
                and (v.effective_to is null or v.effective_to>?)
                and not exists (select 1 from compensation.component_proration_policy_version s
                                where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id)
              order by c.code,i.event_type
              """,
        this::mapProration,
        TenantContext.require(), Date.valueOf(asOf), Date.valueOf(asOf));
  }

  public List<ProrationPolicyView> prorationHistory(UUID identityId) {
    ensureProrationPolicy(identityId);
    return jdbc.query(
        PRORATION_SELECT + " where i.tenant_id=? and i.id=? order by v.version_sequence",
        this::mapProration,
        TenantContext.require(), identityId);
  }

  public ProrationPolicyView approveProrationPolicy(
      UUID versionId, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.approve_component_proration_policy_version(?,?,?,?,?)",
        Long.class,
        TenantContext.require(), versionId, expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Proration-policy version changed or is not approvable; checker must differ from maker");
    }
    return prorationVersion(versionId);
  }

  public ProrationPolicyView endDateProrationPolicy(
      UUID versionId, LocalDate effectiveTo, long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject(
        "select compensation.end_date_component_proration_policy_version(?,?,?,?,?,?)",
        Long.class, TenantContext.require(), versionId, Date.valueOf(effectiveTo),
        expectedVersion, actor, Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Proration-policy version changed or cannot be end-dated at the requested date");
    }
    return prorationVersion(versionId);
  }

  private RateTableView withRateChildren(RateTableView shell) {
    List<RateDimensionView> dimensions = jdbc.query(
        """
        select id,dimension_sequence,code,name,data_type
          from compensation.component_rate_dimension
         where tenant_id=? and rate_table_version_id=? order by dimension_sequence
        """,
        (result, row) -> new RateDimensionView(
            result.getObject("id", UUID.class), result.getInt("dimension_sequence"),
            result.getString("code"), result.getString("name"), result.getString("data_type")),
        TenantContext.require(), shell.versionId());
    List<RateCellView> cells = jdbc.query(
        """
        select id,cell_sequence,dimension_values,rate_value
          from compensation.component_rate_cell
         where tenant_id=? and rate_table_version_id=? order by cell_sequence
        """,
        this::mapCell,
        TenantContext.require(), shell.versionId());
    return new RateTableView(
        shell.identityId(), shell.code(), shell.name(), shell.lifecycleStatus(),
        shell.identityVersionNo(), shell.versionId(), shell.versionSequence(), shell.versionNo(),
        shell.effectiveFrom(), shell.effectiveTo(), shell.approvalStatus(),
        shell.supersedesVersionId(), shell.superseded(), dimensions, cells);
  }

  private RateTableView mapRateShell(ResultSet result, int row) throws SQLException {
    return new RateTableView(
        result.getObject("identity_id", UUID.class), result.getString("code"),
        result.getString("name"), result.getString("lifecycle_status"),
        result.getLong("identity_version_no"), result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"), result.getLong("version_no"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class), result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class), result.getBoolean("superseded"),
        List.of(), List.of());
  }

  private RateCellView mapCell(ResultSet result, int row) throws SQLException {
    return new RateCellView(
        result.getObject("id", UUID.class), result.getInt("cell_sequence"),
        parseStringMap(result.getString("dimension_values")), result.getBigDecimal("rate_value"));
  }

  private RoundingPolicyView mapRounding(ResultSet result, int row) throws SQLException {
    return new RoundingPolicyView(
        result.getObject("identity_id", UUID.class), result.getObject("component_id", UUID.class),
        result.getString("component_code"), result.getString("lifecycle_status"),
        result.getLong("identity_version_no"), result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"), result.getLong("version_no"),
        result.getString("rounding_method"), result.getInt("rounding_scale"),
        result.getString("rounding_stage"), result.getString("negative_treatment"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class), result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class), result.getBoolean("superseded"));
  }

  private ProrationPolicyView mapProration(ResultSet result, int row) throws SQLException {
    return new ProrationPolicyView(
        result.getObject("identity_id", UUID.class), result.getObject("component_id", UUID.class),
        result.getString("component_code"), result.getString("event_type"),
        result.getString("lifecycle_status"), result.getLong("identity_version_no"),
        result.getObject("version_id", UUID.class), result.getInt("version_sequence"),
        result.getLong("version_no"), result.getString("proration_method"),
        result.getString("proration_basis"), result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class), result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class), result.getBoolean("superseded"));
  }

  private void ensureComponent(UUID componentId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.pay_component where tenant_id=? and id=? and lifecycle_status<>'RETIRED'",
        Integer.class, TenantContext.require(), componentId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Pay-component identity was not found or is retired");
    }
  }

  private void ensureRateTable(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.component_rate_table where tenant_id=? and id=?",
        Integer.class, TenantContext.require(), identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Component rate-table identity was not found");
    }
  }

  private void ensureRoundingPolicy(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.component_rounding_policy where tenant_id=? and id=?",
        Integer.class, TenantContext.require(), identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Component rounding-policy identity was not found");
    }
  }

  private void ensureProrationPolicy(UUID identityId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from compensation.component_proration_policy where tenant_id=? and id=?",
        Integer.class, TenantContext.require(), identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Component proration-policy identity was not found");
    }
  }

  private void lockRateTable(UUID identityId) {
    List<String> values = jdbc.query(
        "select lifecycle_status from compensation.component_rate_table where tenant_id=? and id=?",
        (result, row) -> result.getString(1), TenantContext.require(), identityId);
    if (values.isEmpty()) {
      throw new ResourceNotFoundException("Component rate-table identity was not found");
    }
    if ("RETIRED".equals(values.get(0))) {
      throw new ConflictException("Retired component rate tables cannot accept new versions");
    }
  }

  private void lockRoundingPolicy(UUID identityId) {
    List<String> values = jdbc.query(
        "select lifecycle_status from compensation.component_rounding_policy where tenant_id=? and id=?",
        (result, row) -> result.getString(1), TenantContext.require(), identityId);
    if (values.isEmpty()) {
      throw new ResourceNotFoundException("Component rounding-policy identity was not found");
    }
  }

  private void lockProrationPolicy(UUID identityId) {
    List<String> values = jdbc.query(
        "select lifecycle_status from compensation.component_proration_policy where tenant_id=? and id=?",
        (result, row) -> result.getString(1), TenantContext.require(), identityId);
    if (values.isEmpty()) {
      throw new ResourceNotFoundException("Component proration-policy identity was not found");
    }
  }

  private String canonicalJson(Map<String, String> values) {
    if (values == null) {
      throw new IllegalArgumentException("dimension values are required");
    }
    try {
      return objectMapper.writeValueAsString(new TreeMap<>(values));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("dimension values cannot be serialized", exception);
    }
  }

  private Map<String, String> parseStringMap(String json) {
    if (json == null) {
      return Map.of();
    }
    try {
      Map<String, String> values = objectMapper.readValue(
          json, new TypeReference<Map<String, String>>() {});
      return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored rate-table dimensions are invalid", exception);
    }
  }

  public record DependencyTarget(
      UUID componentId, UUID componentVersionId, String code, String phase) {}

  public record PlanningRow(
      UUID componentId,
      String code,
      UUID componentVersionId,
      String formulaType,
      String formulaExpression,
      String calculationPhase) {}
}
