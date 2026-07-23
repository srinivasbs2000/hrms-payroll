package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollProfileView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollProfileWriteRequest;
import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentView;
import com.acme.hrms.payroll.employeepayroll.PayGroupAssignmentWriteRequest;
import com.acme.hrms.payroll.employeepayroll.PayrollAssignmentView;
import com.acme.hrms.payroll.employeepayroll.PayrollAssignmentWriteRequest;
import com.acme.hrms.payroll.employeepayroll.PayrollRelationshipView;
import com.acme.hrms.payroll.employeepayroll.PayrollRelationshipWriteRequest;
import com.acme.hrms.payroll.employeepayroll.SalaryAssignmentView;
import com.acme.hrms.payroll.employeepayroll.SalaryAssignmentWriteRequest;
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
public class EmployeePayrollRepository {
  private static final String RELATIONSHIP_SELECT = """
      select identity.id identity_id,
             identity.external_employee_id,
             identity.employee_number,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.legal_entity_version_id,
             version.relationship_start,
             version.relationship_end,
             version.approval_status,
             version.supersedes_version_id,
             exists (
               select 1
               from employee_payroll.payroll_relationship_version successor
               where successor.tenant_id = version.tenant_id
                 and successor.supersedes_version_id = version.id
             ) superseded
      from employee_payroll.payroll_relationship identity
      join employee_payroll.payroll_relationship_version version
        on version.tenant_id = identity.tenant_id
       and version.payroll_relationship_id = identity.id
      """;

  private static final String ASSIGNMENT_SELECT = """
      select identity.id identity_id,
             identity.payroll_relationship_id,
             identity.assignment_number,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.payroll_relationship_version_id,
             version.establishment_version_id,
             version.assignment_start,
             version.assignment_end,
             version.approval_status,
             version.supersedes_version_id,
             exists (
               select 1
               from employee_payroll.payroll_assignment_version successor
               where successor.tenant_id = version.tenant_id
                 and successor.supersedes_version_id = version.id
             ) superseded
      from employee_payroll.payroll_assignment identity
      join employee_payroll.payroll_assignment_version version
        on version.tenant_id = identity.tenant_id
       and version.payroll_assignment_id = identity.id
      """;

  private static final String PROFILE_SELECT = """
      select profile.id,
             profile.payroll_relationship_id,
             relationship.employee_number,
             profile.currency::text currency,
             profile.payroll_status,
             profile.version_no
      from employee_payroll.employee_payroll_profile profile
      join employee_payroll.payroll_relationship relationship
        on relationship.tenant_id = profile.tenant_id
       and relationship.id = profile.payroll_relationship_id
      """;

  private static final String GROUP_ASSIGNMENT_SELECT = """
      select assignment.id,
             assignment.payroll_assignment_version_id,
             assignment.pay_group_version_id,
             assignment.effective_from,
             assignment.effective_to,
             assignment.approval_status,
             assignment.supersedes_assignment_id,
             exists (
               select 1
               from employee_payroll.pay_group_assignment successor
               where successor.tenant_id = assignment.tenant_id
                 and successor.supersedes_assignment_id = assignment.id
             ) superseded,
             assignment.version_no
      from employee_payroll.pay_group_assignment assignment
      """;

  private static final String SALARY_ASSIGNMENT_SELECT = """
      select assignment.id,
             assignment.payroll_assignment_version_id,
             assignment.salary_structure_version_id,
             assignment.monthly_amount,
             assignment.currency::text currency,
             assignment.effective_from,
             assignment.effective_to,
             assignment.approval_status,
             assignment.supersedes_assignment_id,
             exists (
               select 1
               from employee_payroll.salary_assignment successor
               where successor.tenant_id = assignment.tenant_id
                 and successor.supersedes_assignment_id = assignment.id
             ) superseded,
             assignment.version_no
      from employee_payroll.salary_assignment assignment
      """;

  private final JdbcTemplate jdbc;

  public EmployeePayrollRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollRelationshipView createRelationship(
      PayrollRelationshipWriteRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,created_by,updated_by
        ) values (?,?,?,?,?,?)
        """,
        identityId,
        TenantContext.require(),
        request.externalEmployeeId(),
        request.employeeNumber(),
        actor,
        actor);
    insertRelationshipVersion(
        versionId, identityId, 1, null, request, actor);
    return relationshipVersion(versionId);
  }

  public PayrollRelationshipView addRelationshipVersion(
      UUID identityId,
      PayrollRelationshipWriteRequest request,
      UUID supersedesVersionId,
      String actor) {
    ensureRelationship(identityId);
    int sequence = nextSequence(
        "employee_payroll.payroll_relationship_version",
        "payroll_relationship_id",
        identityId);
    UUID versionId = UUID.randomUUID();
    insertRelationshipVersion(
        versionId,
        identityId,
        sequence,
        supersedesVersionId,
        request,
        actor);
    return relationshipVersion(versionId);
  }

  public PayrollRelationshipView relationshipVersion(UUID versionId) {
    return jdbc.query(
            RELATIONSHIP_SELECT
                + " where version.tenant_id=? and version.id=?",
            this::mapRelationship,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll relationship version was not found"));
  }

  public List<PayrollRelationshipView> relationships(LocalDate asOf) {
    return jdbc.query(
        RELATIONSHIP_SELECT
            + """
               where identity.tenant_id=?
                 and version.approval_status='APPROVED'
                 and version.relationship_start<=?
                 and (
                   version.relationship_end is null
                   or version.relationship_end>?
                 )
                 and not exists (
                   select 1
                   from employee_payroll.payroll_relationship_version successor
                   where successor.tenant_id=version.tenant_id
                     and successor.supersedes_version_id=version.id
                 )
               order by identity.employee_number
               """,
        this::mapRelationship,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayrollRelationshipView currentRelationship(
      UUID identityId, LocalDate asOf) {
    return relationships(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved payroll relationship is effective on " + asOf));
  }

  public List<PayrollRelationshipView> relationshipHistory(UUID identityId) {
    ensureRelationship(identityId);
    return jdbc.query(
        RELATIONSHIP_SELECT
            + " where identity.tenant_id=? and identity.id=?"
            + " order by version.version_sequence",
        this::mapRelationship,
        TenantContext.require(),
        identityId);
  }

  public PayrollRelationshipView approveRelationship(
      UUID versionId, String actor, Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_payroll_relationship_version(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            versionId,
            actor,
            Timestamp.from(now)),
        "Payroll relationship version is not an approvable draft");
    return relationshipVersion(versionId);
  }

  public PayrollRelationshipView endDateRelationship(
      UUID versionId,
      LocalDate relationshipEnd,
      long expectedVersion,
      String actor,
      Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.end_date_payroll_relationship_version(?,?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            versionId,
            Date.valueOf(relationshipEnd),
            expectedVersion,
            actor,
            Timestamp.from(now)),
        "Payroll relationship changed or cannot be end-dated");
    return relationshipVersion(versionId);
  }

  public PayrollAssignmentView createAssignment(
      PayrollAssignmentWriteRequest request, String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,assignment_number,
          created_by,updated_by
        ) values (?,?,?,?,?,?)
        """,
        identityId,
        TenantContext.require(),
        request.payrollRelationshipId(),
        request.assignmentNumber(),
        actor,
        actor);
    insertAssignmentVersion(versionId, identityId, 1, null, request, actor);
    return assignmentVersion(versionId);
  }

  public PayrollAssignmentView addAssignmentVersion(
      UUID identityId,
      PayrollAssignmentWriteRequest request,
      UUID supersedesVersionId,
      String actor) {
    ensureAssignment(identityId);
    int sequence = nextSequence(
        "employee_payroll.payroll_assignment_version",
        "payroll_assignment_id",
        identityId);
    UUID versionId = UUID.randomUUID();
    insertAssignmentVersion(
        versionId,
        identityId,
        sequence,
        supersedesVersionId,
        request,
        actor);
    return assignmentVersion(versionId);
  }

  public PayrollAssignmentView assignmentVersion(UUID versionId) {
    return jdbc.query(
            ASSIGNMENT_SELECT
                + " where version.tenant_id=? and version.id=?",
            this::mapAssignment,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll assignment version was not found"));
  }

  public List<PayrollAssignmentView> assignments(
      UUID relationshipId, LocalDate asOf) {
    return jdbc.query(
        ASSIGNMENT_SELECT
            + """
               where identity.tenant_id=?
                 and identity.payroll_relationship_id=?
                 and version.approval_status='APPROVED'
                 and version.assignment_start<=?
                 and (
                   version.assignment_end is null
                   or version.assignment_end>?
                 )
                 and not exists (
                   select 1
                   from employee_payroll.payroll_assignment_version successor
                   where successor.tenant_id=version.tenant_id
                     and successor.supersedes_version_id=version.id
                 )
               order by identity.assignment_number
               """,
        this::mapAssignment,
        TenantContext.require(),
        relationshipId,
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public PayrollAssignmentView currentAssignment(
      UUID identityId, LocalDate asOf) {
    ensureAssignment(identityId);
    return jdbc.query(
            ASSIGNMENT_SELECT
                + """
                   where identity.tenant_id=?
                     and identity.id=?
                     and version.approval_status='APPROVED'
                     and version.assignment_start<=?
                     and (
                       version.assignment_end is null
                       or version.assignment_end>?
                     )
                     and not exists (
                       select 1
                       from employee_payroll.payroll_assignment_version successor
                       where successor.tenant_id=version.tenant_id
                         and successor.supersedes_version_id=version.id
                     )
                   """,
            this::mapAssignment,
            TenantContext.require(),
            identityId,
            Date.valueOf(asOf),
            Date.valueOf(asOf))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved payroll assignment is effective on " + asOf));
  }

  public List<PayrollAssignmentView> assignmentHistory(UUID identityId) {
    ensureAssignment(identityId);
    return jdbc.query(
        ASSIGNMENT_SELECT
            + " where identity.tenant_id=? and identity.id=?"
            + " order by version.version_sequence",
        this::mapAssignment,
        TenantContext.require(),
        identityId);
  }

  public PayrollAssignmentView approveAssignment(
      UUID versionId, String actor, Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_payroll_assignment_version(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            versionId,
            actor,
            Timestamp.from(now)),
        "Payroll assignment version is not an approvable draft");
    return assignmentVersion(versionId);
  }

  public PayrollAssignmentView endDateAssignment(
      UUID versionId,
      LocalDate assignmentEnd,
      long expectedVersion,
      String actor,
      Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.end_date_payroll_assignment_version(?,?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            versionId,
            Date.valueOf(assignmentEnd),
            expectedVersion,
            actor,
            Timestamp.from(now)),
        "Payroll assignment changed or cannot be end-dated");
    return assignmentVersion(versionId);
  }

  public EmployeePayrollProfileView createProfile(
      EmployeePayrollProfileWriteRequest request, String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.employee_payroll_profile(
          id,tenant_id,payroll_relationship_id,currency,payroll_status,
          created_by,updated_by
        ) values (?,?,?,?, 'INCOMPLETE',?,?)
        """,
        id,
        TenantContext.require(),
        request.payrollRelationshipId(),
        request.resolvedCurrency(),
        actor,
        actor);
    return profile(id);
  }

  public EmployeePayrollProfileView profile(UUID profileId) {
    return jdbc.query(
            PROFILE_SELECT + " where profile.tenant_id=? and profile.id=?",
            this::mapProfile,
            TenantContext.require(),
            profileId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Employee payroll profile was not found"));
  }

  public EmployeePayrollProfileView profileForRelationship(
      UUID relationshipId) {
    return jdbc.query(
            PROFILE_SELECT
                + " where profile.tenant_id=?"
                + " and profile.payroll_relationship_id=?",
            this::mapProfile,
            TenantContext.require(),
            relationshipId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Employee payroll profile was not found"));
  }

  public EmployeePayrollProfileView updateProfileStatus(
      UUID profileId,
      String status,
      long expectedVersion,
      String actor,
      Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.update_employee_payroll_profile_status(?,?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            profileId,
            status,
            expectedVersion,
            actor,
            Timestamp.from(now)),
        "Employee payroll profile changed or status transition is invalid");
    return profile(profileId);
  }

  public PayGroupAssignmentView createPayGroupAssignment(
      PayGroupAssignmentWriteRequest request,
      UUID supersedesAssignmentId,
      String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.pay_group_assignment(
          id,tenant_id,payroll_assignment_version_id,pay_group_version_id,
          effective_from,effective_to,approval_status,
          supersedes_assignment_id,created_by,updated_by
        ) values (?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        id,
        TenantContext.require(),
        request.payrollAssignmentVersionId(),
        request.payGroupVersionId(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedesAssignmentId,
        actor,
        actor);
    return payGroupAssignment(id);
  }

  public PayGroupAssignmentView payGroupAssignment(UUID id) {
    return jdbc.query(
            GROUP_ASSIGNMENT_SELECT
                + " where assignment.tenant_id=? and assignment.id=?",
            this::mapPayGroupAssignment,
            TenantContext.require(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Pay-group assignment was not found"));
  }

  public List<PayGroupAssignmentView> payGroupAssignments(
      UUID assignmentVersionId) {
    return jdbc.query(
        GROUP_ASSIGNMENT_SELECT
            + " where assignment.tenant_id=?"
            + " and assignment.payroll_assignment_version_id=?"
            + " order by assignment.effective_from,assignment.id",
        this::mapPayGroupAssignment,
        TenantContext.require(),
        assignmentVersionId);
  }

  public PayGroupAssignmentView approvePayGroupAssignment(
      UUID id, String actor, Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_pay_group_assignment(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            actor,
            Timestamp.from(now)),
        "Pay-group assignment is not an approvable draft");
    return payGroupAssignment(id);
  }

  public PayGroupAssignmentView endDatePayGroupAssignment(
      UUID id,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.end_date_pay_group_assignment(?,?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            Date.valueOf(effectiveTo),
            expectedVersion,
            actor,
            Timestamp.from(now)),
        "Pay-group assignment changed or cannot be end-dated");
    return payGroupAssignment(id);
  }

  public SalaryAssignmentView createSalaryAssignment(
      SalaryAssignmentWriteRequest request,
      UUID supersedesAssignmentId,
      String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.salary_assignment(
          id,tenant_id,payroll_assignment_version_id,
          salary_structure_version_id,monthly_amount,currency,
          effective_from,effective_to,approval_status,
          supersedes_assignment_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        id,
        TenantContext.require(),
        request.payrollAssignmentVersionId(),
        request.salaryStructureVersionId(),
        request.monthlyAmount(),
        request.resolvedCurrency(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedesAssignmentId,
        actor,
        actor);
    return salaryAssignment(id);
  }

  public SalaryAssignmentView salaryAssignment(UUID id) {
    return jdbc.query(
            SALARY_ASSIGNMENT_SELECT
                + " where assignment.tenant_id=? and assignment.id=?",
            this::mapSalaryAssignment,
            TenantContext.require(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary assignment was not found"));
  }

  public List<SalaryAssignmentView> salaryAssignments(
      UUID assignmentVersionId) {
    return jdbc.query(
        SALARY_ASSIGNMENT_SELECT
            + " where assignment.tenant_id=?"
            + " and assignment.payroll_assignment_version_id=?"
            + " order by assignment.effective_from,assignment.id",
        this::mapSalaryAssignment,
        TenantContext.require(),
        assignmentVersionId);
  }

  public SalaryAssignmentView approveSalaryAssignment(
      UUID id, String actor, Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.approve_salary_assignment(?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            actor,
            Timestamp.from(now)),
        "Salary assignment is not an approvable draft");
    return salaryAssignment(id);
  }

  public SalaryAssignmentView endDateSalaryAssignment(
      UUID id,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    requireOne(
        jdbc.queryForObject(
            "select employee_payroll.end_date_salary_assignment(?,?,?,?,?,?)",
            Long.class,
            TenantContext.require(),
            id,
            Date.valueOf(effectiveTo),
            expectedVersion,
            actor,
            Timestamp.from(now)),
        "Salary assignment changed or cannot be end-dated");
    return salaryAssignment(id);
  }

  private void insertRelationshipVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedesVersionId,
      PayrollRelationshipWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into employee_payroll.payroll_relationship_version(
          id,tenant_id,payroll_relationship_id,legal_entity_version_id,
          version_sequence,relationship_start,relationship_end,
          approval_status,supersedes_version_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        request.legalEntityVersionId(),
        sequence,
        request.relationshipStart(),
        request.relationshipEnd(),
        supersedesVersionId,
        actor,
        actor);
  }

  private void insertAssignmentVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedesVersionId,
      PayrollAssignmentWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,
          payroll_relationship_version_id,establishment_version_id,
          version_sequence,assignment_start,assignment_end,
          approval_status,supersedes_version_id,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        request.payrollRelationshipVersionId(),
        request.establishmentVersionId(),
        sequence,
        request.assignmentStart(),
        request.assignmentEnd(),
        supersedesVersionId,
        actor,
        actor);
  }

  private int nextSequence(
      String tableName, String identityColumn, UUID identityId) {
    Integer next = jdbc.queryForObject(
        "select coalesce(max(version_sequence),0)+1 from "
            + tableName
            + " where tenant_id=? and "
            + identityColumn
            + "=?",
        Integer.class,
        TenantContext.require(),
        identityId);
    return next == null ? 1 : next;
  }

  private void ensureRelationship(UUID identityId) {
    ensureIdentity(
        "employee_payroll.payroll_relationship",
        identityId,
        "Payroll relationship identity was not found");
  }

  private void ensureAssignment(UUID identityId) {
    ensureIdentity(
        "employee_payroll.payroll_assignment",
        identityId,
        "Payroll assignment identity was not found");
  }

  private void ensureIdentity(
      String tableName, UUID identityId, String message) {
    Integer count = jdbc.queryForObject(
        "select count(*) from " + tableName + " where tenant_id=? and id=?",
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(message);
    }
  }

  private void requireOne(Long affected, String message) {
    if (affected == null || affected != 1) {
      throw new ConflictException(message);
    }
  }

  private PayrollRelationshipView mapRelationship(
      ResultSet result, int row) throws SQLException {
    return new PayrollRelationshipView(
        result.getObject("identity_id", UUID.class),
        result.getString("external_employee_id"),
        result.getString("employee_number"),
        result.getString("identity_status"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getObject("legal_entity_version_id", UUID.class),
        result.getObject("relationship_start", LocalDate.class),
        result.getObject("relationship_end", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private PayrollAssignmentView mapAssignment(
      ResultSet result, int row) throws SQLException {
    return new PayrollAssignmentView(
        result.getObject("identity_id", UUID.class),
        result.getObject("payroll_relationship_id", UUID.class),
        result.getString("assignment_number"),
        result.getString("identity_status"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getObject("payroll_relationship_version_id", UUID.class),
        result.getObject("establishment_version_id", UUID.class),
        result.getObject("assignment_start", LocalDate.class),
        result.getObject("assignment_end", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private EmployeePayrollProfileView mapProfile(
      ResultSet result, int row) throws SQLException {
    return new EmployeePayrollProfileView(
        result.getObject("id", UUID.class),
        result.getObject("payroll_relationship_id", UUID.class),
        result.getString("employee_number"),
        result.getString("currency"),
        result.getString("payroll_status"),
        result.getLong("version_no"));
  }

  private PayGroupAssignmentView mapPayGroupAssignment(
      ResultSet result, int row) throws SQLException {
    return new PayGroupAssignmentView(
        result.getObject("id", UUID.class),
        result.getObject("payroll_assignment_version_id", UUID.class),
        result.getObject("pay_group_version_id", UUID.class),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_assignment_id", UUID.class),
        result.getBoolean("superseded"),
        result.getLong("version_no"));
  }

  private SalaryAssignmentView mapSalaryAssignment(
      ResultSet result, int row) throws SQLException {
    return new SalaryAssignmentView(
        result.getObject("id", UUID.class),
        result.getObject("payroll_assignment_version_id", UUID.class),
        result.getObject("salary_structure_version_id", UUID.class),
        result.getBigDecimal("monthly_amount"),
        result.getString("currency"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_assignment_id", UUID.class),
        result.getBoolean("superseded"),
        result.getLong("version_no"));
  }
}
