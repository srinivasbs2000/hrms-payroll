package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.SalaryStructureWriteRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class SalaryStructureRepository {
  private static final String SELECT = """
      select identity.id identity_id,
             identity.code,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.name,
             version.currency::text currency,
             version.effective_from,
             version.effective_to,
             version.approval_status,
             version.supersedes_version_id,
             exists(
               select 1
               from compensation.salary_structure_version successor
               where successor.tenant_id = version.tenant_id
                 and successor.supersedes_version_id = version.id
             ) superseded,
             line.id line_id,
             line.component_version_id,
             line.sequence_no,
             line.target_amount,
             line.target_percentage,
             line.percentage_base_code::text percentage_base_code,
             line.effective_from line_effective_from,
             line.effective_to line_effective_to,
             component.code::text component_code,
             component.name component_name,
             component.component_type,
             component_version.formula_type component_formula_type
      from compensation.salary_structure identity
      join compensation.salary_structure_version version
        on version.tenant_id = identity.tenant_id
       and version.salary_structure_id = identity.id
      left join compensation.salary_structure_line line
        on line.tenant_id = version.tenant_id
       and line.salary_structure_version_id = version.id
      left join compensation.pay_component_version component_version
        on component_version.tenant_id = line.tenant_id
       and component_version.id = line.component_version_id
      left join compensation.pay_component component
        on component.tenant_id = component_version.tenant_id
       and component.id = component_version.component_id
      """;

  private final JdbcTemplate jdbc;

  public SalaryStructureRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SalaryStructureView create(
      SalaryStructureWriteRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.salary_structure(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
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

    return version(versionId);
  }

  public SalaryStructureView addVersion(
      UUID identityId,
      SalaryStructureWriteRequest request,
      UUID supersedes,
      String actor) {
    ensureIdentity(identityId);

    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from compensation.salary_structure_version
        where tenant_id=? and salary_structure_id=?
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

  public SalaryStructureView version(UUID versionId) {
    return query(
            SELECT
                + """
                   where version.tenant_id=? and version.id=?
                   order by line.sequence_no
                   """,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure version was not found"));
  }

  public List<SalaryStructureView> list(LocalDate asOf) {
    return query(
        SELECT
            + """
               where identity.tenant_id=?
                 and version.approval_status='APPROVED'
                 and version.effective_from<=?
                 and (
                   version.effective_to is null
                   or version.effective_to>?
                 )
                 and not exists (
                   select 1
                   from compensation.salary_structure_version successor
                   where successor.tenant_id=version.tenant_id
                     and successor.supersedes_version_id=version.id
                 )
               order by identity.code,line.sequence_no
               """,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public SalaryStructureView current(
      UUID identityId,
      LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved salary-structure version is effective on "
                + asOf));
  }

  public List<SalaryStructureView> history(UUID identityId) {
    ensureIdentity(identityId);

    return query(
        SELECT
            + """
               where identity.tenant_id=? and identity.id=?
               order by version.version_sequence,line.sequence_no
               """,
        TenantContext.require(),
        identityId);
  }

  public SalaryStructureView approve(
      UUID versionId,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.approve_salary_structure_version(
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
          "Salary-structure version is not an approvable "
              + "complete draft");
    }

    return version(versionId);
  }

  public SalaryStructureView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.end_date_salary_structure_version(
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
          "Salary-structure version changed, is in use or "
              + "cannot be end-dated at the requested date");
    }

    return version(versionId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from compensation.salary_structure
        where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);

    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Salary-structure identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      SalaryStructureWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.salary_structure_version(
          id,
          tenant_id,
          salary_structure_id,
          version_sequence,
          name,
          currency,
          effective_from,
          effective_to,
          approval_status,
          supersedes_version_id,
          created_by,
          updated_by
        ) values (?,?,?,?,?,?,?,?, 'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name().trim(),
        request.resolvedCurrency(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);

    for (SalaryStructureLineWriteRequest line : request.lines()) {
      insertLine(versionId, request, line, actor);
    }
  }

  private void insertLine(
      UUID versionId,
      SalaryStructureWriteRequest request,
      SalaryStructureLineWriteRequest line,
      String actor) {
    jdbc.update(
        """
        insert into compensation.salary_structure_line(
          id,
          tenant_id,
          salary_structure_version_id,
          component_version_id,
          sequence_no,
          target_amount,
          target_percentage,
          percentage_base_code,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        TenantContext.require(),
        versionId,
        line.componentVersionId(),
        line.sequenceNo(),
        line.targetAmount(),
        line.targetPercentage(),
        blankToNull(line.percentageBaseCode()),
        request.effectiveFrom(),
        request.effectiveTo(),
        actor,
        actor);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank()
        ? null
        : value.trim();
  }

  private List<SalaryStructureView> query(
      String sql,
      Object... arguments) {
    ResultSetExtractor<List<SalaryStructureView>> extractor =
        this::extract;

    return jdbc.query(
        sql,
        extractor,
        arguments);
  }

  private List<SalaryStructureView> extract(
      ResultSet result) throws SQLException {
    Map<UUID, MutableVersion> versions =
        new LinkedHashMap<>();

    while (result.next()) {
      UUID versionId =
          result.getObject("version_id", UUID.class);

      MutableVersion mutable = versions.get(versionId);
      if (mutable == null) {
        mutable = header(result);
        versions.put(versionId, mutable);
      }

      UUID lineId = result.getObject("line_id", UUID.class);
      if (lineId != null) {
        mutable.lines.add(line(result, lineId));
      }
    }

    return versions.values().stream()
        .map(MutableVersion::toView)
        .toList();
  }

  private MutableVersion header(
      ResultSet result) throws SQLException {
    return new MutableVersion(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("identity_status"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("currency"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject(
            "supersedes_version_id",
            UUID.class),
        result.getBoolean("superseded"));
  }

  private SalaryStructureLineView line(
      ResultSet result,
      UUID lineId) throws SQLException {
    return new SalaryStructureLineView(
        lineId,
        result.getObject(
            "component_version_id",
            UUID.class),
        result.getString("component_code"),
        result.getString("component_name"),
        result.getString("component_type"),
        result.getString("component_formula_type"),
        result.getInt("sequence_no"),
        result.getBigDecimal("target_amount"),
        result.getBigDecimal("target_percentage"),
        result.getString("percentage_base_code"),
        result.getObject(
            "line_effective_from",
            LocalDate.class),
        result.getObject(
            "line_effective_to",
            LocalDate.class));
  }

  private static final class MutableVersion {
    private final UUID identityId;
    private final String code;
    private final String identityStatus;
    private final UUID versionId;
    private final int versionSequence;
    private final long versionNo;
    private final String name;
    private final String currency;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String approvalStatus;
    private final UUID supersedesVersionId;
    private final boolean superseded;
    private final List<SalaryStructureLineView> lines =
        new ArrayList<>();

    private MutableVersion(
        UUID identityId,
        String code,
        String identityStatus,
        UUID versionId,
        int versionSequence,
        long versionNo,
        String name,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String approvalStatus,
        UUID supersedesVersionId,
        boolean superseded) {
      this.identityId = identityId;
      this.code = code;
      this.identityStatus = identityStatus;
      this.versionId = versionId;
      this.versionSequence = versionSequence;
      this.versionNo = versionNo;
      this.name = name;
      this.currency = currency;
      this.effectiveFrom = effectiveFrom;
      this.effectiveTo = effectiveTo;
      this.approvalStatus = approvalStatus;
      this.supersedesVersionId = supersedesVersionId;
      this.superseded = superseded;
    }

    private SalaryStructureView toView() {
      return new SalaryStructureView(
          identityId,
          code,
          identityStatus,
          versionId,
          versionSequence,
          versionNo,
          name,
          currency,
          effectiveFrom,
          effectiveTo,
          approvalStatus,
          supersedesVersionId,
          superseded,
          List.copyOf(lines));
    }
  }
}