package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DependencyView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalaryStructureDesignImpactRepository {
  private final JdbcTemplate jdbc;

  public SalaryStructureDesignImpactRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Evidence evidence(UUID versionId) {
    return jdbc.query(
            """
            select version.workflow_status,
                   coalesce(state.binding_revision,0) statutory_binding_revision,
                   (
                     select evaluation.evidence_hash
                       from compensation.salary_structure_statutory_evaluation evaluation
                      where evaluation.tenant_id=version.tenant_id
                        and evaluation.salary_structure_version_id=version.id
                        and evaluation.statutory_binding_revision=
                            coalesce(state.binding_revision,0)
                      order by evaluation.created_at desc,evaluation.id desc
                      limit 1
                   ) statutory_evidence_hash
              from compensation.salary_structure_version version
              left join compensation.salary_structure_statutory_state state
                on state.tenant_id=version.tenant_id
               and state.salary_structure_version_id=version.id
             where version.tenant_id=?
               and version.id=?
            """,
            (result, row) -> new Evidence(
                result.getString("workflow_status"),
                result.getLong("statutory_binding_revision"),
                result.getString("statutory_evidence_hash")),
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure design evidence was not found"));
  }

  public List<DependencyView> dependencies(SalaryStructureView structure) {
    Map<String, DependencyView> unique = new LinkedHashMap<>();

    add(unique, direct(
        "CTC_POLICY",
        structure.ctcPolicyVersionId(),
        "CTC_POLICY"));
    if (structure.eligibilityRuleVersionId() != null) {
      add(unique, direct(
          "ELIGIBILITY_RULE",
          structure.eligibilityRuleVersionId(),
          "ELIGIBILITY_RULE"));
    }
    add(unique, direct(
        "PAY_COMPONENT",
        structure.residualComponentVersionId(),
        "RESIDUAL_COMPONENT"));

    structure.lines().forEach(line -> add(
        unique,
        new DependencyView(
            "PAY_COMPONENT",
            line.componentId(),
            line.componentVersionId(),
            line.componentCode(),
            "STRUCTURE_LINE_" + line.sequenceNo(),
            null)));

    jdbc.query(
        """
        select binding.supplemental_plan_id object_id,
               binding.supplemental_plan_version_id version_id,
               plan.code::text code,
               version.plan_type,
               version.approval_status
          from compensation.salary_structure_supplemental_plan_binding binding
          join compensation.salary_supplemental_plan_version version
            on version.tenant_id=binding.tenant_id
           and version.id=binding.supplemental_plan_version_id
           and version.supplemental_plan_id=binding.supplemental_plan_id
          join compensation.salary_supplemental_plan plan
            on plan.tenant_id=version.tenant_id
           and plan.id=version.supplemental_plan_id
         where binding.tenant_id=?
           and binding.salary_structure_version_id=?
         order by binding.sequence_no
        """,
        result -> {
          add(unique, dependency(
              result,
              "SUPPLEMENTAL_PLAN",
              "SUPPLEMENTAL_" + result.getString("plan_type"),
              "approval_status"));
        },
        TenantContext.require(),
        structure.versionId());

    jdbc.query(
        """
        select distinct plan.id object_id,
               version.id version_id,
               plan.code::text code,
               version.approval_status
          from compensation.flex_benefit_plan_version version
          join compensation.flex_benefit_plan plan
            on plan.tenant_id=version.tenant_id
           and plan.id=version.flex_benefit_plan_id
          join compensation.salary_structure_supplemental_plan_binding binding
            on binding.tenant_id=version.tenant_id
           and binding.supplemental_plan_version_id=
               version.supplemental_plan_version_id
         where binding.tenant_id=?
           and binding.salary_structure_version_id=?
         order by plan.code,version.id
        """,
        result -> {
          add(unique, dependency(
              result,
              "FLEX_BENEFIT_PLAN",
              "FLEX_BENEFIT_POLICY",
              "approval_status"));
        },
        TenantContext.require(),
        structure.versionId());

    jdbc.query(
        """
        select statutory_rule_id object_id,
               statutory_rule_version_id version_id,
               binding_purpose,
               enforcement_level,
               status
          from compensation.salary_structure_statutory_binding
         where tenant_id=?
           and salary_structure_version_id=?
         order by binding_purpose,statutory_rule_version_id
        """,
        result -> {
          add(
              unique,
              new DependencyView(
                  "STATUTORY_RULE",
                  result.getObject("object_id", UUID.class),
                  result.getObject("version_id", UUID.class),
                  result.getString("binding_purpose"),
                  result.getString("binding_purpose"),
                  result.getString("enforcement_level")
                      + ":" + result.getString("status")));
        },
        TenantContext.require(),
        structure.versionId());

    List<DependencyView> result = new ArrayList<>(unique.values());
    result.sort(
        Comparator.comparing(DependencyView::dependencyType)
            .thenComparing(item -> item.objectId().toString())
            .thenComparing(item -> item.versionId().toString())
            .thenComparing(DependencyView::role));
    return List.copyOf(result);
  }

  private DependencyView direct(
      String type,
      UUID versionId,
      String role) {
    return new DependencyView(
        type,
        versionId,
        versionId,
        null,
        role,
        null);
  }

  private DependencyView dependency(
      ResultSet result,
      String type,
      String role,
      String statusColumn) throws SQLException {
    return new DependencyView(
        type,
        result.getObject("object_id", UUID.class),
        result.getObject("version_id", UUID.class),
        result.getString("code"),
        role,
        result.getString(statusColumn));
  }

  private void add(
      Map<String, DependencyView> unique,
      DependencyView dependency) {
    if (dependency.objectId() == null || dependency.versionId() == null) {
      return;
    }
    String key = dependency.dependencyType()
        + "|" + dependency.objectId()
        + "|" + dependency.versionId()
        + "|" + dependency.role();
    unique.putIfAbsent(key, dependency);
  }

  public record Evidence(
      String workflowStatus,
      long statutoryBindingRevision,
      String statutoryEvidenceHash) {}
}
