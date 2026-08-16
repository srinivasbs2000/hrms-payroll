package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureValidationView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.SalaryStructureWriteRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
             version.structure_schema_version,
             version.structure_type,
             version.pay_frequency,
             version.confidentiality_level,
             version.ctc_policy_version_id,
             version.eligibility_rule_version_id,
             version.target_type,
             version.target_frequency,
             version.target_source_amount,
             version.target_annualization_factor,
             version.target_execution_mode,
             version.inclusive_payroll_base_version_id,
             version.exclusive_payroll_base_version_id,
             version.target_annual_amount,
             version.tolerance_amount,
             version.residual_component_version_id,
             version.configuration_hash,
             version.validation_fingerprint,
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
             line.line_schema_version,
             line.line_type,
             line.target_amount,
             line.target_percentage,
             line.percentage_base_code::text percentage_base_code,
             line.minimum_amount,
             line.maximum_amount,
             line.mandatory_flag,
             line.override_policy,
             line.ctc_display_order,
             line.payslip_display_order,
             line.effective_from line_effective_from,
             line.effective_to line_effective_to,
             component.id component_id,
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

  private static final String VALIDATION_SELECT = """
      select validation.id validation_id,
             validation.salary_structure_id identity_id,
             validation.salary_structure_version_id version_id,
             validation.ctc_policy_version_id,
             validation.eligibility_rule_version_id,
             validation.effective_date,
             validation.target_amount,
             validation.validation_status,
             validation.request_hash,
             validation.configuration_hash,
             validation.result_hash,
             validation.blocking_error_count,
             validation.warning_count,
             validation.summary_json::text summary_json,
             validation.created_at,
             validation.created_by,
             line.id validation_line_id,
             line.line_sequence,
             line.component_id,
             line.component_version_id,
             line.annual_amount,
             line.monthly_amount,
             line.classification,
             line.evidence_json::text evidence_json,
             component.code::text component_code,
             component.name component_name
        from compensation.salary_structure_validation validation
        left join compensation.salary_structure_validation_line line
          on line.tenant_id = validation.tenant_id
         and line.validation_id = validation.id
        left join compensation.pay_component component
          on component.tenant_id = line.tenant_id
         and component.id = line.component_id
      """;

  private static final String DISCLAIMER =
      "DESIGN-TIME SALARY-STRUCTURE SIMULATION — "
          + "NOT AN EMPLOYEE PAYROLL RESULT";

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public SalaryStructureRepository(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public SalaryStructureView create(
      SalaryStructureWriteRequest request,
      String configurationHash,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.salary_structure(
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
        configurationHash,
        actor);

    return version(versionId);
  }

  public SalaryStructureView addVersion(
      UUID identityId,
      SalaryStructureWriteRequest request,
      UUID supersedes,
      String configurationHash,
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
        configurationHash,
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
                 and (version.effective_to is null or version.effective_to>?)
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
            "No approved salary-structure version is effective on " + asOf));
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
        select compensation.approve_salary_structure_version(?,?,?,?)
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Salary-structure version is not an approvable complete draft");
    }
    return version(versionId);
  }

  public SalaryStructureView bindValidation(
      UUID versionId,
      UUID validationId,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.bind_salary_structure_validation(?,?,?,?,?,?)
        """,
        Long.class,
        TenantContext.require(),
        versionId,
        validationId,
        expectedVersion,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      throw new ConflictException(
          "Validation is stale, failing, incomplete or the structure changed");
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
        select compensation.end_date_salary_structure_version(?,?,?,?,?,?)
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
          "Salary-structure version changed, is in use or cannot be end-dated");
    }
    return version(versionId);
  }

  public SalaryStructureValidationView saveValidation(
      SalaryStructureValidationView validation,
      String actor) {
    jdbc.update(
        """
        insert into compensation.salary_structure_validation(
          id,tenant_id,salary_structure_id,salary_structure_version_id,
          ctc_policy_version_id,eligibility_rule_version_id,effective_date,
          target_amount,validation_status,request_hash,configuration_hash,
          result_hash,blocking_error_count,warning_count,summary_json,created_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)
        """,
        validation.validationId(),
        TenantContext.require(),
        validation.identityId(),
        validation.versionId(),
        validation.ctcPolicyVersionId(),
        validation.eligibilityRuleVersionId(),
        Date.valueOf(validation.effectiveDate()),
        validation.targetAmount(),
        validation.validationStatus(),
        validation.requestHash(),
        validation.configurationHash(),
        validation.resultHash(),
        validation.blockingErrorCount(),
        validation.warningCount(),
        json(validation.summary()),
        actor);

    for (SalaryStructureValidationLineView line : validation.lines()) {
      jdbc.update(
          """
          insert into compensation.salary_structure_validation_line(
            id,tenant_id,validation_id,line_sequence,component_id,
            component_version_id,annual_amount,monthly_amount,
            classification,evidence_json,created_by
          ) values (?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)
          """,
          line.id(),
          TenantContext.require(),
          validation.validationId(),
          line.lineSequence(),
          line.componentId(),
          line.componentVersionId(),
          line.annualAmount(),
          line.monthlyAmount(),
          line.classification(),
          json(line.evidence()),
          actor);
    }

    return validation(validation.validationId());
  }

  public Optional<SalaryStructureValidationView> findValidation(
      UUID versionId,
      String resultHash) {
    return validationQuery(
            VALIDATION_SELECT
                + """
                   where validation.tenant_id=?
                     and validation.salary_structure_version_id=?
                     and validation.result_hash=?
                   order by line.line_sequence
                   """,
            TenantContext.require(),
            versionId,
            resultHash)
        .stream()
        .findFirst();
  }

  public SalaryStructureValidationView validation(UUID validationId) {
    return validationQuery(
            VALIDATION_SELECT
                + """
                   where validation.tenant_id=? and validation.id=?
                   order by line.line_sequence
                   """,
            TenantContext.require(),
            validationId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Salary-structure validation was not found"));
  }

  public List<SalaryStructureValidationView> validations(UUID versionId) {
    return validationQuery(
        VALIDATION_SELECT
            + """
               where validation.tenant_id=?
                 and validation.salary_structure_version_id=?
               order by validation.created_at,validation.id,line.line_sequence
               """,
        TenantContext.require(),
        versionId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*) from compensation.salary_structure
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
      String configurationHash,
      String actor) {
    jdbc.update(
        """
        insert into compensation.salary_structure_version(
          id,tenant_id,salary_structure_id,version_sequence,name,currency,
          structure_schema_version,structure_type,pay_frequency,
          confidentiality_level,ctc_policy_version_id,
          eligibility_rule_version_id,target_type,target_source_amount,
          target_frequency,target_annualization_factor,target_execution_mode,
          inclusive_payroll_base_version_id,exclusive_payroll_base_version_id,
          target_annual_amount,tolerance_amount,residual_component_version_id,
          configuration_hash,effective_from,effective_to,approval_status,
          supersedes_version_id,created_by,updated_by
        ) values (
          ?,?,?,?,?,?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?
        )
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name().trim(),
        request.resolvedCurrency(),
        request.structureType(),
        request.payFrequency(),
        request.confidentialityLevel(),
        request.ctcPolicyVersionId(),
        request.eligibilityRuleVersionId(),
        request.targetType(),
        request.targetSourceAmount(),
        request.resolvedTargetFrequency(),
        request.resolvedTargetAnnualizationFactor(),
        request.targetExecutionMode(),
        request.inclusivePayrollBaseVersionId(),
        request.exclusivePayrollBaseVersionId(),
        request.resolvedTargetAnnualAmount(),
        request.toleranceAmount(),
        request.residualComponentVersionId(),
        configurationHash,
        Date.valueOf(request.effectiveFrom()),
        request.effectiveTo() == null
            ? null : Date.valueOf(request.effectiveTo()),
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
          id,tenant_id,salary_structure_version_id,component_version_id,
          sequence_no,line_schema_version,line_type,target_amount,
          target_percentage,percentage_base_code,minimum_amount,maximum_amount,
          mandatory_flag,override_policy,ctc_display_order,
          payslip_display_order,effective_from,effective_to,
          created_by,updated_by
        ) values (?,?,?,?,?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        TenantContext.require(),
        versionId,
        line.componentVersionId(),
        line.sequenceNo(),
        line.lineType(),
        line.targetAmount(),
        line.targetPercentage(),
        blankToNull(line.percentageBaseCode()),
        line.minimumAmount(),
        line.maximumAmount(),
        line.mandatory(),
        line.overridePolicy(),
        line.ctcDisplayOrder(),
        line.payslipDisplayOrder(),
        Date.valueOf(request.effectiveFrom()),
        request.effectiveTo() == null
            ? null : Date.valueOf(request.effectiveTo()),
        actor,
        actor);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private List<SalaryStructureView> query(
      String sql,
      Object... arguments) {
    ResultSetExtractor<List<SalaryStructureView>> extractor = this::extract;
    return jdbc.query(sql, extractor, arguments);
  }

  private List<SalaryStructureView> extract(
      ResultSet result) throws SQLException {
    Map<UUID, MutableVersion> versions = new LinkedHashMap<>();
    while (result.next()) {
      UUID versionId = result.getObject("version_id", UUID.class);
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

  private MutableVersion header(ResultSet result) throws SQLException {
    return new MutableVersion(
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("identity_status"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("currency"),
        result.getShort("structure_schema_version"),
        result.getString("structure_type"),
        result.getString("pay_frequency"),
        result.getString("confidentiality_level"),
        result.getObject("ctc_policy_version_id", UUID.class),
        result.getObject("eligibility_rule_version_id", UUID.class),
        result.getString("target_type"),
        result.getString("target_frequency"),
        result.getBigDecimal("target_source_amount"),
        result.getBigDecimal("target_annualization_factor"),
        result.getString("target_execution_mode"),
        result.getObject("inclusive_payroll_base_version_id", UUID.class),
        result.getObject("exclusive_payroll_base_version_id", UUID.class),
        result.getBigDecimal("target_annual_amount"),
        result.getBigDecimal("tolerance_amount"),
        result.getObject("residual_component_version_id", UUID.class),
        result.getString("configuration_hash"),
        result.getString("validation_fingerprint"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private SalaryStructureLineView line(
      ResultSet result,
      UUID lineId) throws SQLException {
    return new SalaryStructureLineView(
        lineId,
        result.getObject("component_id", UUID.class),
        result.getObject("component_version_id", UUID.class),
        result.getString("component_code"),
        result.getString("component_name"),
        result.getString("component_type"),
        result.getString("component_formula_type"),
        result.getInt("sequence_no"),
        result.getShort("line_schema_version"),
        result.getString("line_type"),
        result.getBigDecimal("target_amount"),
        result.getBigDecimal("target_percentage"),
        result.getString("percentage_base_code"),
        result.getBigDecimal("minimum_amount"),
        result.getBigDecimal("maximum_amount"),
        result.getBoolean("mandatory_flag"),
        result.getString("override_policy"),
        result.getInt("ctc_display_order"),
        result.getInt("payslip_display_order"),
        result.getObject("line_effective_from", LocalDate.class),
        result.getObject("line_effective_to", LocalDate.class));
  }

  private List<SalaryStructureValidationView> validationQuery(
      String sql,
      Object... arguments) {
    ResultSetExtractor<List<SalaryStructureValidationView>> extractor =
        this::extractValidations;
    return jdbc.query(sql, extractor, arguments);
  }

  private List<SalaryStructureValidationView> extractValidations(
      ResultSet result) throws SQLException {
    Map<UUID, MutableValidation> validations = new LinkedHashMap<>();
    while (result.next()) {
      UUID validationId = result.getObject("validation_id", UUID.class);
      MutableValidation mutable = validations.get(validationId);
      if (mutable == null) {
        Timestamp createdAt = result.getTimestamp("created_at");
        mutable = new MutableValidation(
            validationId,
            result.getObject("identity_id", UUID.class),
            result.getObject("version_id", UUID.class),
            result.getObject("ctc_policy_version_id", UUID.class),
            result.getObject("eligibility_rule_version_id", UUID.class),
            result.getObject("effective_date", LocalDate.class),
            result.getBigDecimal("target_amount"),
            result.getString("validation_status"),
            result.getString("request_hash"),
            result.getString("configuration_hash"),
            result.getString("result_hash"),
            result.getInt("blocking_error_count"),
            result.getInt("warning_count"),
            map(result.getString("summary_json")),
            createdAt == null ? null : createdAt.toInstant(),
            result.getString("created_by"));
        validations.put(validationId, mutable);
      }
      UUID lineId = result.getObject("validation_line_id", UUID.class);
      if (lineId != null) {
        mutable.lines.add(new SalaryStructureValidationLineView(
            lineId,
            result.getInt("line_sequence"),
            result.getObject("component_id", UUID.class),
            result.getObject("component_version_id", UUID.class),
            result.getString("component_code"),
            result.getString("component_name"),
            result.getBigDecimal("annual_amount"),
            result.getBigDecimal("monthly_amount"),
            result.getString("classification"),
            map(result.getString("evidence_json"))));
      }
    }
    return validations.values().stream()
        .map(MutableValidation::toView)
        .toList();
  }

  private String json(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "Validation evidence is not serializable", exception);
    }
  }

  private Map<String, Object> map(String value) throws SQLException {
    try {
      return objectMapper.readValue(
          value,
          new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "Persisted validation JSON is invalid", exception);
    }
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
    private final short structureSchemaVersion;
    private final String structureType;
    private final String payFrequency;
    private final String confidentialityLevel;
    private final UUID ctcPolicyVersionId;
    private final UUID eligibilityRuleVersionId;
    private final String targetType;
    private final String targetFrequency;
    private final java.math.BigDecimal targetSourceAmount;
    private final java.math.BigDecimal targetAnnualizationFactor;
    private final String targetExecutionMode;
    private final UUID inclusivePayrollBaseVersionId;
    private final UUID exclusivePayrollBaseVersionId;
    private final java.math.BigDecimal targetAnnualAmount;
    private final java.math.BigDecimal toleranceAmount;
    private final UUID residualComponentVersionId;
    private final String configurationHash;
    private final String validationFingerprint;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String approvalStatus;
    private final UUID supersedesVersionId;
    private final boolean superseded;
    private final List<SalaryStructureLineView> lines = new ArrayList<>();

    private MutableVersion(
        UUID identityId,
        String code,
        String identityStatus,
        UUID versionId,
        int versionSequence,
        long versionNo,
        String name,
        String currency,
        short structureSchemaVersion,
        String structureType,
        String payFrequency,
        String confidentialityLevel,
        UUID ctcPolicyVersionId,
        UUID eligibilityRuleVersionId,
        String targetType,
        String targetFrequency,
        java.math.BigDecimal targetSourceAmount,
        java.math.BigDecimal targetAnnualizationFactor,
        String targetExecutionMode,
        UUID inclusivePayrollBaseVersionId,
        UUID exclusivePayrollBaseVersionId,
        java.math.BigDecimal targetAnnualAmount,
        java.math.BigDecimal toleranceAmount,
        UUID residualComponentVersionId,
        String configurationHash,
        String validationFingerprint,
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
      this.structureSchemaVersion = structureSchemaVersion;
      this.structureType = structureType;
      this.payFrequency = payFrequency;
      this.confidentialityLevel = confidentialityLevel;
      this.ctcPolicyVersionId = ctcPolicyVersionId;
      this.eligibilityRuleVersionId = eligibilityRuleVersionId;
      this.targetType = targetType;
      this.targetFrequency = targetFrequency;
      this.targetSourceAmount = targetSourceAmount;
      this.targetAnnualizationFactor = targetAnnualizationFactor;
      this.targetExecutionMode = targetExecutionMode;
      this.inclusivePayrollBaseVersionId = inclusivePayrollBaseVersionId;
      this.exclusivePayrollBaseVersionId = exclusivePayrollBaseVersionId;
      this.targetAnnualAmount = targetAnnualAmount;
      this.toleranceAmount = toleranceAmount;
      this.residualComponentVersionId = residualComponentVersionId;
      this.configurationHash = configurationHash;
      this.validationFingerprint = validationFingerprint;
      this.effectiveFrom = effectiveFrom;
      this.effectiveTo = effectiveTo;
      this.approvalStatus = approvalStatus;
      this.supersedesVersionId = supersedesVersionId;
      this.superseded = superseded;
    }

    private SalaryStructureView toView() {
      return new SalaryStructureView(
          identityId,code,identityStatus,versionId,versionSequence,versionNo,
          name,currency,structureSchemaVersion,structureType,payFrequency,
          confidentialityLevel,ctcPolicyVersionId,eligibilityRuleVersionId,
          targetType,targetFrequency,targetSourceAmount,targetAnnualizationFactor,
          targetExecutionMode,inclusivePayrollBaseVersionId,
          exclusivePayrollBaseVersionId,targetAnnualAmount,toleranceAmount,
          residualComponentVersionId,configurationHash,validationFingerprint,
          effectiveFrom,effectiveTo,approvalStatus,supersedesVersionId,
          superseded,List.copyOf(lines));
    }
  }

  private static final class MutableValidation {
    private final UUID validationId;
    private final UUID identityId;
    private final UUID versionId;
    private final UUID ctcPolicyVersionId;
    private final UUID eligibilityRuleVersionId;
    private final LocalDate effectiveDate;
    private final java.math.BigDecimal targetAmount;
    private final String validationStatus;
    private final String requestHash;
    private final String configurationHash;
    private final String resultHash;
    private final int blockingErrorCount;
    private final int warningCount;
    private final Map<String, Object> summary;
    private final Instant createdAt;
    private final String createdBy;
    private final List<SalaryStructureValidationLineView> lines =
        new ArrayList<>();

    private MutableValidation(
        UUID validationId,
        UUID identityId,
        UUID versionId,
        UUID ctcPolicyVersionId,
        UUID eligibilityRuleVersionId,
        LocalDate effectiveDate,
        java.math.BigDecimal targetAmount,
        String validationStatus,
        String requestHash,
        String configurationHash,
        String resultHash,
        int blockingErrorCount,
        int warningCount,
        Map<String, Object> summary,
        Instant createdAt,
        String createdBy) {
      this.validationId = validationId;
      this.identityId = identityId;
      this.versionId = versionId;
      this.ctcPolicyVersionId = ctcPolicyVersionId;
      this.eligibilityRuleVersionId = eligibilityRuleVersionId;
      this.effectiveDate = effectiveDate;
      this.targetAmount = targetAmount;
      this.validationStatus = validationStatus;
      this.requestHash = requestHash;
      this.configurationHash = configurationHash;
      this.resultHash = resultHash;
      this.blockingErrorCount = blockingErrorCount;
      this.warningCount = warningCount;
      this.summary = summary;
      this.createdAt = createdAt;
      this.createdBy = createdBy;
    }

    private SalaryStructureValidationView toView() {
      return new SalaryStructureValidationView(
          validationId,identityId,versionId,ctcPolicyVersionId,
          eligibilityRuleVersionId,effectiveDate,targetAmount,
          validationStatus,requestHash,configurationHash,resultHash,
          blockingErrorCount,warningCount,summary,createdAt,createdBy,
          DISCLAIMER,List.copyOf(lines));
    }
  }
}
