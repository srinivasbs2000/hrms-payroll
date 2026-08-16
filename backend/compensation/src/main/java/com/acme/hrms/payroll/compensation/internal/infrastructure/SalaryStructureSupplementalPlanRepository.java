package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanCreateRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanLineWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanView;
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
public class SalaryStructureSupplementalPlanRepository {
  private static final String HEADER_SELECT = """
      select p.id identity_id,
             p.code,
             p.lifecycle_status,
             p.version_no identity_version_no,
             v.id version_id,
             v.version_sequence,
             v.version_no,
             v.name,
             v.plan_type,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.approved_at,
             v.approved_by,
             v.supersedes_version_id,
             exists(
               select 1
                 from compensation.salary_supplemental_plan_version successor
                where successor.tenant_id=v.tenant_id
                  and successor.supersedes_version_id=v.id
             ) superseded
        from compensation.salary_supplemental_plan p
        join compensation.salary_supplemental_plan_version v
          on v.tenant_id=p.tenant_id
         and v.supplemental_plan_id=p.id
      """;

  private final JdbcTemplate jdbc;

  public SalaryStructureSupplementalPlanRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SupplementalPlanView create(
      SupplementalPlanCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.salary_supplemental_plan(
          id,tenant_id,code,lifecycle_status,created_by,updated_by
        ) values (?,?,?,'PENDING_APPROVAL',?,?)
        """,
        identityId,
        TenantContext.require(),
        request.code(),
        actor,
        actor);

    insertVersion(
        identityId,
        versionId,
        1,
        null,
        request.version(),
        actor);
    return version(versionId);
  }

  public SupplementalPlanView addVersion(
      UUID identityId,
      SupplementalPlanVersionWriteRequest request,
      String actor) {
    String status = jdbc.queryForObject(
        "select compensation.lock_salary_supplemental_plan(?,?)",
        String.class,
        TenantContext.require(),
        identityId);
    if (status == null) {
      throw new ResourceNotFoundException(
          "Supplemental-plan identity was not found");
    }
    if ("RETIRED".equals(status)) {
      throw new ConflictException(
          "Retired supplemental plans cannot accept versions");
    }

    List<VersionPointer> pointers = jdbc.query(
        """
        select id, version_sequence
          from compensation.salary_supplemental_plan_version
         where tenant_id=? and supplemental_plan_id=?
         order by version_sequence desc
         limit 1
        """,
        (result, row) -> new VersionPointer(
            result.getObject("id", UUID.class),
            result.getInt("version_sequence")),
        TenantContext.require(),
        identityId);
    VersionPointer latest = pointers.stream()
        .findFirst()
        .orElseThrow(() -> new ConflictException(
            "Supplemental-plan identity has no version"));

    UUID versionId = UUID.randomUUID();
    insertVersion(
        identityId,
        versionId,
        latest.versionSequence() + 1,
        latest.versionId(),
        request,
        actor);
    return version(versionId);
  }

  public SupplementalPlanView approve(
      UUID versionId,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.approve_salary_supplemental_plan_version(
          ?,?,?,?
        )
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Supplemental-plan version is not an approvable complete draft; "
              + "the checker must differ from the maker");
    }
    return version(versionId);
  }

  public SupplementalPlanView version(UUID versionId) {
    List<PlanHeader> headers = jdbc.query(
        HEADER_SELECT
            + """
               where v.tenant_id=? and v.id=?
               """,
        (result, row) -> header(result),
        TenantContext.require(),
        versionId);

    PlanHeader header = headers.stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Supplemental-plan version was not found"));
    return header.toView(lines(versionId));
  }

  public List<SupplementalPlanView> list(LocalDate asOf) {
    return jdbc.query(
            HEADER_SELECT
                + """
                   where p.tenant_id=?
                     and p.lifecycle_status='ACTIVE'
                     and v.approval_status='APPROVED'
                     and v.effective_from<=?
                     and (v.effective_to is null or v.effective_to>?)
                   order by p.code
                   """,
            (result, row) -> header(result),
            TenantContext.require(),
            Date.valueOf(asOf),
            Date.valueOf(asOf))
        .stream()
        .map(header -> header.toView(lines(header.versionId())))
        .toList();
  }

  public SupplementalPlanView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved supplemental-plan version is effective on " + asOf));
  }

  public List<SupplementalPlanView> history(UUID identityId) {
    ensureIdentity(identityId);
    return jdbc.query(
            HEADER_SELECT
                + """
                   where p.tenant_id=? and p.id=?
                   order by v.version_sequence
                   """,
            (result, row) -> header(result),
            TenantContext.require(),
            identityId)
        .stream()
        .map(header -> header.toView(lines(header.versionId())))
        .toList();
  }

  public SupplementalPlanBindingView bind(
      UUID salaryStructureId,
      UUID salaryStructureVersionId,
      SupplementalPlanBindingWriteRequest request,
      String actor) {
    UUID bindingId = UUID.randomUUID();

    int inserted = jdbc.update(
        """
        insert into compensation.salary_structure_supplemental_plan_binding(
          id,tenant_id,salary_structure_id,salary_structure_version_id,
          supplemental_plan_id,supplemental_plan_version_id,sequence_no,
          effective_from,effective_to,created_by
        )
        select ?,?,?,?,v.supplemental_plan_id,v.id,?,?,?,?
          from compensation.salary_supplemental_plan_version v
         where v.tenant_id=? and v.id=?
        """,
        bindingId,
        TenantContext.require(),
        salaryStructureId,
        salaryStructureVersionId,
        request.sequenceNo(),
        request.effectiveFrom(),
        request.effectiveTo(),
        actor,
        TenantContext.require(),
        request.supplementalPlanVersionId());

    if (inserted != 1) {
      throw new ResourceNotFoundException(
          "Supplemental-plan version was not found");
    }
    return binding(bindingId);
  }

  public List<SupplementalPlanBindingView> bindings(
      UUID salaryStructureId,
      UUID salaryStructureVersionId) {
    return jdbc.query(
        """
        select b.id binding_id,
               b.salary_structure_id,
               b.salary_structure_version_id,
               b.supplemental_plan_id,
               b.supplemental_plan_version_id,
               p.code supplemental_plan_code,
               v.name supplemental_plan_name,
               v.plan_type,
               b.sequence_no,
               b.effective_from,
               b.effective_to,
               b.version_no
          from compensation.salary_structure_supplemental_plan_binding b
          join compensation.salary_supplemental_plan p
            on p.tenant_id=b.tenant_id
           and p.id=b.supplemental_plan_id
          join compensation.salary_supplemental_plan_version v
            on v.tenant_id=b.tenant_id
           and v.id=b.supplemental_plan_version_id
         where b.tenant_id=?
           and b.salary_structure_id=?
           and b.salary_structure_version_id=?
         order by b.sequence_no
        """,
        (result, row) -> binding(result),
        TenantContext.require(),
        salaryStructureId,
        salaryStructureVersionId);
  }

  private SupplementalPlanBindingView binding(UUID bindingId) {
    return jdbc.query(
            """
            select b.id binding_id,
                   b.salary_structure_id,
                   b.salary_structure_version_id,
                   b.supplemental_plan_id,
                   b.supplemental_plan_version_id,
                   p.code supplemental_plan_code,
                   v.name supplemental_plan_name,
                   v.plan_type,
                   b.sequence_no,
                   b.effective_from,
                   b.effective_to,
                   b.version_no
              from compensation.salary_structure_supplemental_plan_binding b
              join compensation.salary_supplemental_plan p
                on p.tenant_id=b.tenant_id
               and p.id=b.supplemental_plan_id
              join compensation.salary_supplemental_plan_version v
                on v.tenant_id=b.tenant_id
               and v.id=b.supplemental_plan_version_id
             where b.tenant_id=? and b.id=?
            """,
            (result, row) -> binding(result),
            TenantContext.require(),
            bindingId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure supplemental binding was not found"));
  }

  private void insertVersion(
      UUID identityId,
      UUID versionId,
      int sequence,
      UUID supersedes,
      SupplementalPlanVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.salary_supplemental_plan_version(
          id,tenant_id,supplemental_plan_id,version_sequence,name,plan_type,
          effective_from,effective_to,approval_status,supersedes_version_id,
          created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name().trim(),
        request.planType(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);

    for (SupplementalPlanLineWriteRequest line : request.lines()) {
      insertLine(identityId, versionId, request, line, actor);
    }
  }

  private void insertLine(
      UUID identityId,
      UUID versionId,
      SupplementalPlanVersionWriteRequest version,
      SupplementalPlanLineWriteRequest line,
      String actor) {
    int inserted = jdbc.update(
        """
        insert into compensation.salary_supplemental_plan_line(
          id,tenant_id,supplemental_plan_id,supplemental_plan_version_id,
          component_id,component_version_id,sequence_no,default_amount,
          default_percentage,minimum_amount,maximum_amount,
          employee_override_allowed,effective_from,effective_to,
          created_by,updated_by
        )
        select ?,?,?,?,component.component_id,component.id,?,?,?,?,?,?,?,?,?,?
          from compensation.pay_component_version component
         where component.tenant_id=? and component.id=?
        """,
        UUID.randomUUID(),
        TenantContext.require(),
        identityId,
        versionId,
        line.sequenceNo(),
        line.defaultAmount(),
        line.defaultPercentage(),
        line.minimumAmount(),
        line.maximumAmount(),
        line.employeeOverrideAllowed(),
        line.resolvedEffectiveFrom(version.effectiveFrom()),
        line.resolvedEffectiveTo(version.effectiveTo()),
        actor,
        actor,
        TenantContext.require(),
        line.componentVersionId());

    if (inserted != 1) {
      throw new ResourceNotFoundException(
          "Supplemental-plan component version was not found");
    }
  }

  private List<SupplementalPlanLineView> lines(UUID versionId) {
    return jdbc.query(
        """
        select line.id line_id,
               line.component_id,
               line.component_version_id,
               component.code::text component_code,
               component.name component_name,
               line.sequence_no,
               line.default_amount,
               line.default_percentage,
               line.minimum_amount,
               line.maximum_amount,
               line.employee_override_allowed,
               line.effective_from,
               line.effective_to,
               line.version_no
          from compensation.salary_supplemental_plan_line line
          join compensation.pay_component component
            on component.tenant_id=line.tenant_id
           and component.id=line.component_id
         where line.tenant_id=?
           and line.supplemental_plan_version_id=?
         order by line.sequence_no
        """,
        (result, row) -> new SupplementalPlanLineView(
            result.getObject("line_id", UUID.class),
            result.getObject("component_id", UUID.class),
            result.getObject("component_version_id", UUID.class),
            result.getString("component_code"),
            result.getString("component_name"),
            result.getInt("sequence_no"),
            result.getBigDecimal("default_amount"),
            result.getBigDecimal("default_percentage"),
            result.getBigDecimal("minimum_amount"),
            result.getBigDecimal("maximum_amount"),
            result.getBoolean("employee_override_allowed"),
            result.getObject("effective_from", LocalDate.class),
            result.getObject("effective_to", LocalDate.class),
            result.getLong("version_no")),
        TenantContext.require(),
        versionId);
  }

  private PlanHeader header(ResultSet result) throws SQLException {
    Timestamp approvedAt = result.getTimestamp("approved_at");
    return new PlanHeader(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("lifecycle_status"),
        result.getLong("identity_version_no"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("plan_type"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        approvedAt == null ? null : approvedAt.toInstant(),
        result.getString("approved_by"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private SupplementalPlanBindingView binding(ResultSet result)
      throws SQLException {
    return new SupplementalPlanBindingView(
        result.getObject("binding_id", UUID.class),
        result.getObject("salary_structure_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getObject("supplemental_plan_id", UUID.class),
        result.getObject("supplemental_plan_version_id", UUID.class),
        result.getString("supplemental_plan_code"),
        result.getString("supplemental_plan_name"),
        result.getString("plan_type"),
        result.getInt("sequence_no"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getLong("version_no"));
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
          from compensation.salary_supplemental_plan
         where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Supplemental-plan identity was not found");
    }
  }

  private record VersionPointer(UUID versionId, int versionSequence) {}

  private record PlanHeader(
      UUID identityId,
      String code,
      String lifecycleStatus,
      long identityVersionNo,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String name,
      String planType,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String approvalStatus,
      Instant approvedAt,
      String approvedBy,
      UUID supersedesVersionId,
      boolean superseded) {
    SupplementalPlanView toView(List<SupplementalPlanLineView> lines) {
      return new SupplementalPlanView(
          identityId,
          code,
          lifecycleStatus,
          identityVersionNo,
          versionId,
          versionSequence,
          versionNo,
          name,
          planType,
          effectiveFrom,
          effectiveTo,
          approvalStatus,
          approvedAt,
          approvedBy,
          supersedesVersionId,
          superseded,
          lines);
    }
  }
}
