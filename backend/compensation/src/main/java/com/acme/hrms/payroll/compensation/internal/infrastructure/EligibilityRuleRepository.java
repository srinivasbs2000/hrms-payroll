package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.EligibilityCriterionView;
import com.acme.hrms.payroll.compensation.EligibilityCriterionWriteRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleCreateRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleVersionWriteRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EligibilityRuleRepository {
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
             v.result_when_matched,
             v.result_when_not_matched,
             v.effective_from,
             v.effective_to,
             v.approval_status,
             v.supersedes_version_id,
             exists(
               select 1
               from compensation.eligibility_rule_version successor
               where successor.tenant_id=v.tenant_id
                 and successor.supersedes_version_id=v.id
             ) superseded,
             c.id criterion_id,
             c.criterion_sequence,
             c.fact_key,
             c.fact_type,
             c.comparison_operator,
             c.value_json::text criterion_value_json,
             c.version_no criterion_version_no
      from compensation.eligibility_rule i
      join compensation.eligibility_rule_version v
        on v.tenant_id=i.tenant_id
       and v.eligibility_rule_id=i.id
      left join compensation.eligibility_rule_criterion c
        on c.tenant_id=v.tenant_id
       and c.eligibility_rule_version_id=v.id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public EligibilityRuleRepository(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public EligibilityRuleView create(
      EligibilityRuleCreateRequest request,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    jdbc.update(
        """
        insert into compensation.eligibility_rule(
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

  public EligibilityRuleView addVersion(
      UUID identityId,
      EligibilityRuleVersionWriteRequest request,
      UUID supersedes,
      String actor) {
    lockIdentity(identityId);

    Integer next = jdbc.queryForObject(
        """
        select coalesce(max(version_sequence),0)+1
        from compensation.eligibility_rule_version
        where tenant_id=? and eligibility_rule_id=?
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

  public EligibilityRuleView version(UUID versionId) {
    return query(
            SELECT
                + """
                   where v.tenant_id=? and v.id=?
                   order by c.criterion_sequence
                   """,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Eligibility-rule version was not found"));
  }

  public List<EligibilityRuleView> list(LocalDate asOf) {
    return query(
        SELECT
            + """
               where i.tenant_id=?
                 and (i.lifecycle_status<>'RETIRED'
                      or i.retirement_effective_date>?)
                 and v.approval_status='APPROVED'
                 and v.effective_from<=?
                 and (v.effective_to is null or v.effective_to>?)
               order by i.code,c.criterion_sequence
               """,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public EligibilityRuleView current(
      UUID identityId,
      LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No approved eligibility-rule version is effective on "
                + asOf));
  }

  public List<EligibilityRuleView> history(UUID identityId) {
    ensureIdentity(identityId);
    return query(
        SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence,c.criterion_sequence
               """,
        TenantContext.require(),
        identityId);
  }

  public EligibilityRuleView latest(UUID identityId) {
    return history(identityId).stream()
        .max(Comparator.comparingInt(
            EligibilityRuleView::versionSequence))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Eligibility-rule version was not found"));
  }

  public EligibilityRuleView approve(
      UUID versionId,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.approve_eligibility_rule_version(
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
          "Eligibility-rule version is not an approvable complete draft; "
              + "the checker must differ from the maker");
    }
    return version(versionId);
  }

  public EligibilityRuleView endDate(
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.end_date_eligibility_rule_version(
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
          "Eligibility-rule version changed, is in use or cannot be "
              + "end-dated at the requested date");
    }
    return version(versionId);
  }

  public EligibilityRuleView retire(
      UUID identityId,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason,
      String actor,
      Instant now) {
    Long affected = jdbc.queryForObject(
        """
        select compensation.retire_eligibility_rule(
          ?,?,?,?,?,?,?
        )
        """,
        Long.class,
        TenantContext.require(),
        identityId,
        Date.valueOf(effectiveDate),
        expectedVersion,
        reason,
        actor,
        Timestamp.from(now));

    if (affected == null || affected != 1) {
      EligibilityRuleView current = latest(identityId);
      if (!"RETIRED".equals(current.lifecycleStatus())) {
        throw new ConflictException(
            "Eligibility rule changed or has active/future "
                + "approved dependencies");
      }
    }
    return latest(identityId);
  }

  private void ensureIdentity(UUID identityId) {
    Integer count = jdbc.queryForObject(
        """
        select count(*)
        from compensation.eligibility_rule
        where tenant_id=? and id=?
        """,
        Integer.class,
        TenantContext.require(),
        identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Eligibility-rule identity was not found");
    }
  }

  private void lockIdentity(UUID identityId) {
    List<String> statuses = jdbc.query(
        """
        select lifecycle_status
        from compensation.eligibility_rule
        where tenant_id=? and id=?
        for update
        """,
        (result, row) -> result.getString(1),
        TenantContext.require(),
        identityId);
    if (statuses.isEmpty()) {
      throw new ResourceNotFoundException(
          "Eligibility-rule identity was not found");
    }
    if ("RETIRED".equals(statuses.get(0))) {
      throw new ConflictException(
          "Retired eligibility rules cannot accept new versions");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      EligibilityRuleVersionWriteRequest request,
      String actor) {
    jdbc.update(
        """
        insert into compensation.eligibility_rule_version(
          id,tenant_id,eligibility_rule_id,version_sequence,name,
          result_when_matched,result_when_not_matched,effective_from,
          effective_to,approval_status,supersedes_version_id,
          created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)
        """,
        versionId,
        TenantContext.require(),
        identityId,
        sequence,
        request.name().trim(),
        request.resultWhenMatched(),
        request.resultWhenNotMatched(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);

    for (EligibilityCriterionWriteRequest criterion
        : request.criteria()) {
      insertCriterion(
          identityId,
          versionId,
          criterion,
          actor);
    }
  }

  private void insertCriterion(
      UUID identityId,
      UUID versionId,
      EligibilityCriterionWriteRequest criterion,
      String actor) {
    jdbc.update(
        """
        insert into compensation.eligibility_rule_criterion(
          id,tenant_id,eligibility_rule_id,eligibility_rule_version_id,
          criterion_sequence,fact_key,fact_type,comparison_operator,
          value_json,created_by,updated_by
        ) values (?,?,?,?,?,?,?,?,cast(? as jsonb),?,?)
        """,
        UUID.randomUUID(),
        TenantContext.require(),
        identityId,
        versionId,
        criterion.criterionSequence(),
        criterion.factKey(),
        criterion.factType(),
        criterion.comparisonOperator(),
        criterion.value().toString(),
        actor,
        actor);
  }

  private List<EligibilityRuleView> query(
      String sql,
      Object... arguments) {
    ResultSetExtractor<List<EligibilityRuleView>> extractor =
        this::extract;
    return jdbc.query(sql, extractor, arguments);
  }

  private List<EligibilityRuleView> extract(
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

      UUID criterionId =
          result.getObject("criterion_id", UUID.class);
      if (criterionId != null) {
        mutable.criteria.add(criterion(result, criterionId));
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
        result.getObject(
            "retirement_effective_date",
            LocalDate.class),
        result.getString("retirement_reason"),
        retiredAt == null ? null : retiredAt.toInstant(),
        result.getString("retired_by"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("result_when_matched"),
        result.getString("result_when_not_matched"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"));
  }

  private EligibilityCriterionView criterion(
      ResultSet result,
      UUID criterionId) throws SQLException {
    return new EligibilityCriterionView(
        criterionId,
        result.getInt("criterion_sequence"),
        result.getString("fact_key"),
        result.getString("fact_type"),
        result.getString("comparison_operator"),
        parseValue(result.getString("criterion_value_json")),
        result.getLong("criterion_version_no"));
  }

  private JsonNode parseValue(String value) throws SQLException {
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "Persisted eligibility criterion JSON is invalid",
          exception);
    }
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
    private final String resultWhenMatched;
    private final String resultWhenNotMatched;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String approvalStatus;
    private final UUID supersedesVersionId;
    private final boolean superseded;
    private final List<EligibilityCriterionView> criteria =
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
        String resultWhenMatched,
        String resultWhenNotMatched,
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
      this.resultWhenMatched = resultWhenMatched;
      this.resultWhenNotMatched = resultWhenNotMatched;
      this.effectiveFrom = effectiveFrom;
      this.effectiveTo = effectiveTo;
      this.approvalStatus = approvalStatus;
      this.supersedesVersionId = supersedesVersionId;
      this.superseded = superseded;
    }

    private EligibilityRuleView toView() {
      return new EligibilityRuleView(
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
          resultWhenMatched,
          resultWhenNotMatched,
          effectiveFrom,
          effectiveTo,
          approvalStatus,
          supersedesVersionId,
          superseded,
          List.copyOf(criteria));
    }
  }
}
