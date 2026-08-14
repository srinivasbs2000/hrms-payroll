package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.PayGroupCompatibilityIssueView;
import com.acme.hrms.payroll.compensation.PayGroupResolutionCheckpointView;
import com.acme.hrms.payroll.compensation.PayGroupResolutionView;
import com.acme.hrms.payroll.compensation.PayGroupRoutingReadinessView;
import com.acme.hrms.payroll.compensation.PayGroupRoutingRuleView;
import com.acme.hrms.payroll.compensation.PayGroupRoutingRuleWriteRequest;
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

  public List<PayGroupRoutingRuleView> routingRules(LocalDate asOf) {
    return jdbc.query(
        """
        select id,pay_group_version_id,payroll_statutory_unit_version_id,
               establishment_version_id,priority,effective_from,effective_to,
               status,version_no
        from organisation.pay_group_routing_rule
        where tenant_id=? and status='ACTIVE'
          and effective_from<=?
          and (effective_to is null or effective_to>?)
        order by establishment_version_id nulls last,priority,effective_from desc,id
        """,
        this::mapRoutingRule,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayGroupRoutingRuleView routingRule(UUID ruleId) {
    return jdbc.query(
            """
            select id,pay_group_version_id,payroll_statutory_unit_version_id,
                   establishment_version_id,priority,effective_from,effective_to,
                   status,version_no
            from organisation.pay_group_routing_rule
            where tenant_id=? and id=?
            """,
            this::mapRoutingRule,
            TenantContext.require(),
            ruleId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pay-group routing rule was not found"));
  }

  public PayGroupRoutingRuleView createRoutingRule(
      PayGroupRoutingRuleWriteRequest request, String actor) {
    UUID id = jdbc.queryForObject(
        """
        select organisation.create_pay_group_routing_rule(
          ?,?,?,?,?,?,?,?
        )
        """,
        UUID.class,
        TenantContext.require(),
        request.payGroupVersionId(),
        request.payrollStatutoryUnitVersionId(),
        request.establishmentVersionId(),
        request.resolvedPriority(),
        Date.valueOf(request.effectiveFrom()),
        request.effectiveTo() == null ? null : Date.valueOf(request.effectiveTo()),
        actor);
    if (id == null) {
      throw new IllegalStateException("Routing-rule creation returned no identifier");
    }
    return routingRule(id);
  }

  public PayGroupRoutingRuleView endDateRoutingRule(
      UUID ruleId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant changedAt) {
    Long affected = jdbc.queryForObject(
        """
        select organisation.end_date_pay_group_routing_rule(
          ?,?,?,?,?,?
        )
        """,
        Long.class,
        TenantContext.require(),
        ruleId,
        Date.valueOf(effectiveTo),
        expectedVersion,
        actor,
        Timestamp.from(changedAt));
    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Routing rule changed, is inactive, or cannot be shortened to the requested date");
    }
    return routingRule(ruleId);
  }

  public PayGroupRoutingReadinessView routingReadiness(
      UUID payrollAssignmentVersionId,
      UUID payGroupVersionId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    UUID tenantId = TenantContext.require();
    List<PayGroupCompatibilityIssueView> issues = jdbc.query(
        """
        select issue_code,issue_detail
        from organisation.pay_group_assignment_compatibility_issues(
          ?,?,?,?,?
        )
        order by issue_code
        """,
        (result, row) -> new PayGroupCompatibilityIssueView(
            result.getString("issue_code"), result.getString("issue_detail")),
        tenantId,
        payrollAssignmentVersionId,
        payGroupVersionId,
        Date.valueOf(effectiveFrom),
        effectiveTo == null ? null : Date.valueOf(effectiveTo));
    List<PayGroupResolutionCheckpointView> checkpoints = jdbc.query(
        """
        with candidate_dates(as_of) as (
          values (?::date)
          union
          select rule.effective_from
          from organisation.pay_group_routing_rule rule
          where rule.tenant_id=? and rule.status='ACTIVE'
            and rule.effective_from>? and rule.effective_from<?
          union
          select rule.effective_to
          from organisation.pay_group_routing_rule rule
          where rule.tenant_id=? and rule.status='ACTIVE'
            and rule.effective_to>? and rule.effective_to<?
          union
          select assignment.effective_from
          from employee_payroll.pay_group_assignment assignment
          where assignment.tenant_id=?
            and assignment.payroll_assignment_version_id=?
            and assignment.effective_from>? and assignment.effective_from<?
          union
          select assignment.effective_to
          from employee_payroll.pay_group_assignment assignment
          where assignment.tenant_id=?
            and assignment.payroll_assignment_version_id=?
            and assignment.effective_to>? and assignment.effective_to<?
        )
        select candidate.as_of,
               resolved.pay_group_version_id,
               resolved.resolution_source,
               resolved.routing_rule_id
        from candidate_dates candidate
        left join lateral organisation.resolve_pay_group_version_for_assignment(
          ?,?,candidate.as_of
        ) resolved on true
        order by candidate.as_of
        """,
        (result, row) -> {
          UUID resolvedPayGroup = result.getObject("pay_group_version_id", UUID.class);
          return new PayGroupResolutionCheckpointView(
              result.getObject("as_of", LocalDate.class),
              resolvedPayGroup,
              result.getString("resolution_source"),
              result.getObject("routing_rule_id", UUID.class),
              payGroupVersionId.equals(resolvedPayGroup));
        },
        Date.valueOf(effectiveFrom),
        tenantId, Date.valueOf(effectiveFrom), Date.valueOf(effectiveTo),
        tenantId, Date.valueOf(effectiveFrom), Date.valueOf(effectiveTo),
        tenantId, payrollAssignmentVersionId,
        Date.valueOf(effectiveFrom), Date.valueOf(effectiveTo),
        tenantId, payrollAssignmentVersionId,
        Date.valueOf(effectiveFrom), Date.valueOf(effectiveTo),
        tenantId, payrollAssignmentVersionId);
    PayGroupResolutionView resolutionAtEffectiveFrom = checkpoints.stream()
        .filter(checkpoint -> checkpoint.asOf().equals(effectiveFrom))
        .findFirst()
        .filter(checkpoint -> checkpoint.payGroupVersionId() != null)
        .map(checkpoint -> new PayGroupResolutionView(
            checkpoint.payGroupVersionId(),
            checkpoint.resolutionSource(),
            checkpoint.routingRuleId()))
        .orElse(null);
    boolean routingCoverageComplete = !checkpoints.isEmpty()
        && checkpoints.stream().allMatch(
            PayGroupResolutionCheckpointView::matchesRequestedPayGroup);
    RoutingContext context = routingContext(payGroupVersionId);
    return new PayGroupRoutingReadinessView(
        payrollAssignmentVersionId,
        payGroupVersionId,
        effectiveFrom,
        effectiveTo,
        context == null ? null : context.payrollStatutoryUnitVersionId(),
        context == null ? null : context.calendarId(),
        context == null ? null : context.calendarFrequency(),
        context == null ? null : context.calendarTimezone(),
        resolutionAtEffectiveFrom,
        issues.isEmpty(),
        routingCoverageComplete,
        routingCoverageComplete,
        issues.isEmpty() && routingCoverageComplete,
        List.copyOf(checkpoints),
        List.copyOf(issues));
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

  private PayGroupRoutingRuleView mapRoutingRule(
      ResultSet result, int row) throws SQLException {
    return new PayGroupRoutingRuleView(
        result.getObject("id", UUID.class),
        result.getObject("pay_group_version_id", UUID.class),
        result.getObject("payroll_statutory_unit_version_id", UUID.class),
        result.getObject("establishment_version_id", UUID.class),
        result.getInt("priority"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("status"),
        result.getLong("version_no"));
  }

  private RoutingContext routingContext(UUID payGroupVersionId) {
    return jdbc.query(
            """
            select group_version.payroll_statutory_unit_version_id,
                   group_version.calendar_id,
                   calendar.frequency,
                   calendar.timezone
            from organisation.pay_group_version group_version
            join organisation.payroll_calendar calendar
              on calendar.tenant_id=group_version.tenant_id
             and calendar.id=group_version.calendar_id
            where group_version.tenant_id=? and group_version.id=?
            """,
            (result, row) -> new RoutingContext(
                result.getObject("payroll_statutory_unit_version_id", UUID.class),
                result.getObject("calendar_id", UUID.class),
                result.getString("frequency"),
                result.getString("timezone")),
            TenantContext.require(),
            payGroupVersionId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private record RoutingContext(
      UUID payrollStatutoryUnitVersionId,
      UUID calendarId,
      String calendarFrequency,
      String calendarTimezone) {}
}
