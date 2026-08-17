package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.CompatibilityEvaluationView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.CompatibilityIssueView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.RuleVersionOption;
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
public class SalaryStructureStatutoryCompatibilityRepository {
  private static final String BINDING_SELECT = """
      select b.id binding_id,
             b.salary_structure_version_id,
             b.statutory_rule_id,
             b.statutory_rule_version_id,
             rv.version_sequence statutory_rule_version_sequence,
             r.jurisdiction_code,
             r.authority_code,
             r.code rule_code,
             r.name rule_name,
             r.rule_category,
             b.binding_purpose,
             b.enforcement_level,
             b.component_version_id,
             c.period_basis,
             c.minimum_amount,
             rv.currency::text currency,
             b.status,
             b.version_no,
             b.created_at,
             b.created_by,
             b.retired_at,
             b.retired_by
        from compensation.salary_structure_statutory_binding b
        join statutory.statutory_rule r
          on r.tenant_id=b.tenant_id
         and r.id=b.statutory_rule_id
        join statutory.statutory_rule_version rv
          on rv.tenant_id=b.tenant_id
         and rv.id=b.statutory_rule_version_id
         and rv.statutory_rule_id=b.statutory_rule_id
        left join statutory.statutory_rule_design_constraint c
          on c.tenant_id=rv.tenant_id
         and c.statutory_rule_version_id=rv.id
         and c.constraint_kind='MINIMUM_WAGE'
      """;

  private final JdbcTemplate jdbc;

  public SalaryStructureStatutoryCompatibilityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void requireStructureVersion(UUID identityId, UUID versionId) {
    Integer matches = jdbc.queryForObject(
        """
        select count(*)
          from compensation.salary_structure_version
         where tenant_id=? and salary_structure_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId,
        versionId);
    if (matches == null || matches != 1) {
      throw new ResourceNotFoundException(
          "Salary-structure version was not found for the requested identity");
    }
  }

  public List<RuleVersionOption> ruleVersions(LocalDate asOf) {
    return jdbc.query(
        """
        select r.id statutory_rule_id,
               rv.id statutory_rule_version_id,
               rv.version_sequence,
               r.jurisdiction_code,
               r.authority_code,
               r.code rule_code,
               r.name rule_name,
               r.rule_category,
               rv.currency::text currency,
               rv.effective_from,
               rv.effective_to,
               c.constraint_kind,
               c.period_basis,
               c.minimum_amount
          from statutory.statutory_rule r
          join statutory.statutory_rule_version rv
            on rv.tenant_id=r.tenant_id
           and rv.statutory_rule_id=r.id
          left join statutory.statutory_rule_design_constraint c
            on c.tenant_id=rv.tenant_id
           and c.statutory_rule_version_id=rv.id
           and c.constraint_kind='MINIMUM_WAGE'
         where r.tenant_id=?
           and r.status='ACTIVE'
           and rv.approval_status='APPROVED'
           and rv.effective_from<=?
           and (rv.effective_to is null or rv.effective_to>?)
           and not exists (
             select 1
               from statutory.statutory_rule_version successor
              where successor.tenant_id=rv.tenant_id
                and successor.supersedes_version_id=rv.id
           )
         order by r.jurisdiction_code,r.authority_code,r.code,rv.version_sequence desc
        """,
        this::mapRuleVersion,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public List<BindingView> bindings(UUID salaryStructureVersionId) {
    return jdbc.query(
        BINDING_SELECT
            + """
               where b.tenant_id=? and b.salary_structure_version_id=?
               order by case when b.status='ACTIVE' then 0 else 1 end,
                        b.created_at desc,b.id
              """,
        this::mapBinding,
        TenantContext.require(),
        salaryStructureVersionId);
  }

  public BindingView binding(UUID bindingId) {
    return jdbc.query(
            BINDING_SELECT + " where b.tenant_id=? and b.id=?",
            this::mapBinding,
            TenantContext.require(),
            bindingId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure statutory binding was not found"));
  }

  public BindingView bind(
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      BindingRequest request,
      String actor,
      Instant changedAt) {
    UUID bindingId = jdbc.queryForObject(
        """
        select compensation.bind_salary_structure_statutory_rule(
          ?,?,?,?,?,?,?,?,?
        )
        """,
        UUID.class,
        TenantContext.require(),
        salaryStructureId,
        salaryStructureVersionId,
        request.statutoryRuleVersionId(),
        request.bindingPurpose(),
        request.enforcementLevel(),
        request.componentVersionId(),
        actor,
        Timestamp.from(changedAt));
    if (bindingId == null) {
      throw new ConflictException("Statutory binding command returned no identifier");
    }
    return binding(bindingId);
  }

  public BindingView retire(
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      UUID bindingId,
      long expectedVersion,
      String actor,
      Instant changedAt) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.retire_salary_structure_statutory_binding(
          ?,?,?,?,?,?,?
        )
        """,
        Long.class,
        TenantContext.require(),
        salaryStructureId,
        salaryStructureVersionId,
        bindingId,
        expectedVersion,
        actor,
        Timestamp.from(changedAt));
    if (affected == null || affected != 1L) {
      throw new ConflictException(
          "Statutory binding changed, is retired, or no longer belongs to this structure");
    }
    return binding(bindingId);
  }

  public CompatibilityEvaluationView evaluate(
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      UUID validationId,
      String actor,
      Instant evaluatedAt) {
    UUID evaluationId = jdbc.queryForObject(
        """
        select compensation.evaluate_salary_structure_statutory_compatibility(
          ?,?,?,?,?,?
        )
        """,
        UUID.class,
        TenantContext.require(),
        salaryStructureId,
        salaryStructureVersionId,
        validationId,
        actor,
        Timestamp.from(evaluatedAt));
    if (evaluationId == null) {
      throw new ConflictException(
          "Statutory compatibility evaluation returned no identifier");
    }
    return evaluation(evaluationId);
  }

  public List<CompatibilityEvaluationView> evaluations(
      UUID salaryStructureVersionId,
      UUID validationId) {
    return jdbc.query(
            """
            select id,validation_id,salary_structure_version_id,
                   statutory_binding_revision,validation_status,
                   blocking_issue_count,advisory_issue_count,evidence_hash,
                   created_at,created_by
              from compensation.salary_structure_statutory_evaluation
             where tenant_id=?
               and salary_structure_version_id=?
               and validation_id=?
             order by created_at desc,id desc
            """,
            (result, row) -> mapEvaluationHeader(result),
            TenantContext.require(),
            salaryStructureVersionId,
            validationId)
        .stream()
        .map(this::withIssues)
        .toList();
  }

  private CompatibilityEvaluationView evaluation(UUID evaluationId) {
    CompatibilityEvaluationView header = jdbc.query(
            """
            select id,validation_id,salary_structure_version_id,
                   statutory_binding_revision,validation_status,
                   blocking_issue_count,advisory_issue_count,evidence_hash,
                   created_at,created_by
              from compensation.salary_structure_statutory_evaluation
             where tenant_id=? and id=?
            """,
            (result, row) -> mapEvaluationHeader(result),
            TenantContext.require(),
            evaluationId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Statutory compatibility evaluation was not found"));
    return withIssues(header);
  }

  private CompatibilityEvaluationView withIssues(
      CompatibilityEvaluationView header) {
    List<CompatibilityIssueView> issues = jdbc.query(
        """
        select id,binding_id,issue_code,severity,statutory_rule_id,
               statutory_rule_version_id,component_version_id,period_basis,
               required_amount,actual_amount,issue_detail
          from compensation.salary_structure_statutory_issue
         where tenant_id=? and evaluation_id=?
         order by case severity when 'BLOCKING' then 0 else 1 end,
                  issue_code,id
        """,
        this::mapIssue,
        TenantContext.require(),
        header.evaluationId());
    return new CompatibilityEvaluationView(
        header.evaluationId(),
        header.validationId(),
        header.salaryStructureVersionId(),
        header.statutoryBindingRevision(),
        header.validationStatus(),
        header.blockingIssueCount(),
        header.advisoryIssueCount(),
        header.evidenceHash(),
        header.createdAt(),
        header.createdBy(),
        issues,
        "DESIGN-TIME STATUTORY COMPATIBILITY — NOT AN OFFICIAL PAYROLL OR LEGAL CALCULATION");
  }

  private RuleVersionOption mapRuleVersion(ResultSet result, int row)
      throws SQLException {
    return new RuleVersionOption(
        result.getObject("statutory_rule_id", UUID.class),
        result.getObject("statutory_rule_version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getString("jurisdiction_code"),
        result.getString("authority_code"),
        result.getString("rule_code"),
        result.getString("rule_name"),
        result.getString("rule_category"),
        result.getString("currency"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("constraint_kind"),
        result.getString("period_basis"),
        result.getBigDecimal("minimum_amount"));
  }

  private BindingView mapBinding(ResultSet result, int row)
      throws SQLException {
    return new BindingView(
        result.getObject("binding_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getObject("statutory_rule_id", UUID.class),
        result.getObject("statutory_rule_version_id", UUID.class),
        result.getInt("statutory_rule_version_sequence"),
        result.getString("jurisdiction_code"),
        result.getString("authority_code"),
        result.getString("rule_code"),
        result.getString("rule_name"),
        result.getString("rule_category"),
        result.getString("binding_purpose"),
        result.getString("enforcement_level"),
        result.getObject("component_version_id", UUID.class),
        result.getString("period_basis"),
        result.getBigDecimal("minimum_amount"),
        result.getString("currency"),
        result.getString("status"),
        result.getLong("version_no"),
        result.getTimestamp("created_at").toInstant(),
        result.getString("created_by"),
        instant(result, "retired_at"),
        result.getString("retired_by"));
  }

  private CompatibilityEvaluationView mapEvaluationHeader(ResultSet result)
      throws SQLException {
    return new CompatibilityEvaluationView(
        result.getObject("id", UUID.class),
        result.getObject("validation_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getLong("statutory_binding_revision"),
        result.getString("validation_status"),
        result.getInt("blocking_issue_count"),
        result.getInt("advisory_issue_count"),
        result.getString("evidence_hash"),
        result.getTimestamp("created_at").toInstant(),
        result.getString("created_by"),
        List.of(),
        "DESIGN-TIME STATUTORY COMPATIBILITY — NOT AN OFFICIAL PAYROLL OR LEGAL CALCULATION");
  }

  private CompatibilityIssueView mapIssue(ResultSet result, int row)
      throws SQLException {
    return new CompatibilityIssueView(
        result.getObject("id", UUID.class),
        result.getObject("binding_id", UUID.class),
        result.getString("issue_code"),
        result.getString("severity"),
        result.getObject("statutory_rule_id", UUID.class),
        result.getObject("statutory_rule_version_id", UUID.class),
        result.getObject("component_version_id", UUID.class),
        result.getString("period_basis"),
        result.getBigDecimal("required_amount"),
        result.getBigDecimal("actual_amount"),
        result.getString("issue_detail"));
  }

  private Instant instant(ResultSet result, String column) throws SQLException {
    Timestamp timestamp = result.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
