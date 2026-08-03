package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.OrganisationKind;
import com.acme.hrms.payroll.organisation.OrganisationProblemException;
import com.acme.hrms.payroll.organisation.OrganisationView;
import com.acme.hrms.payroll.organisation.OrganisationWriteRequest;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrganisationRepository {
  private final JdbcTemplate jdbc;

  public OrganisationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OrganisationView create(
      OrganisationKind kind,
      OrganisationWriteRequest request,
      String actor) {
    Spec spec = Spec.of(kind);
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          "insert into organisation."
              + spec.identityTable
              + "(id,tenant_id,code,created_by,updated_by)"
              + " values (?,?,?,?,?)",
          identityId,
          TenantContext.require(),
          request.code(),
          actor,
          actor);
      insertVersion(
          spec,
          versionId,
          identityId,
          1,
          null,
          request,
          actor);
      return version(kind, versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public OrganisationView addVersion(
      OrganisationKind kind,
      UUID identityId,
      OrganisationWriteRequest request,
      UUID supersedes,
      String actor) {
    Spec spec = Spec.of(kind);
    try {
      Integer next =
          jdbc.queryForObject(
              "select organisation.allocate_version_sequence(?,?,?)",
              Integer.class,
              kind.name(),
              TenantContext.require(),
              identityId);
      if (next == null) {
        throw new IllegalStateException(
            "Organisation version sequence allocation returned no value");
      }
      UUID versionId = UUID.randomUUID();
      insertVersion(
          spec,
          versionId,
          identityId,
          next,
          supersedes,
          request,
          actor);
      return version(kind, versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public OrganisationView version(
      OrganisationKind kind, UUID versionId) {
    Spec spec = Spec.of(kind);
    return jdbc
        .query(
            spec.select + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Organisation version was not found"));
  }

  public OrganisationView latest(
      OrganisationKind kind, UUID identityId) {
    Spec spec = Spec.of(kind);
    return jdbc
        .query(
            spec.select
                + " where i.tenant_id=? and i.id=?"
                + " order by v.version_sequence desc limit 1",
            this::map,
            TenantContext.require(),
            identityId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Organisation identity was not found"));
  }

  public List<OrganisationView> list(
      OrganisationKind kind, LocalDate asOf) {
    Spec spec = Spec.of(kind);
    return jdbc.query(
        spec.select
            + " where i.tenant_id=?"
            + " and v.approval_status='APPROVED'"
            + " and v.effective_from<=?"
            + " and (v.effective_to is null or v.effective_to>?)"
            + " and not exists (select 1 from organisation."
            + spec.versionTable
            + " s where s.tenant_id=v.tenant_id"
            + " and s.supersedes_version_id=v.id)"
            + " order by i.code",
        this::map,
        TenantContext.require(),
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  public OrganisationView current(
      OrganisationKind kind,
      UUID identityId,
      LocalDate asOf) {
    return list(kind, asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No approved organisation version is effective on "
                        + asOf));
  }

  public List<OrganisationView> history(
      OrganisationKind kind, UUID identityId) {
    Spec spec = Spec.of(kind);
    return jdbc.query(
        spec.select
            + " where i.tenant_id=? and i.id=?"
            + " order by v.version_sequence",
        this::map,
        TenantContext.require(),
        identityId);
  }

  public OrganisationView approve(
      OrganisationKind kind,
      UUID versionId,
      String actor,
      Instant now) {
    try {
      Long affected =
          jdbc.queryForObject(
              "select organisation.approve_version(?,?,?,?,?)",
              Long.class,
              kind.name(),
              TenantContext.require(),
              versionId,
              actor,
              Timestamp.from(now));
      if (affected == null || affected != 1) {
        throw lifecycleConflict(
            "Version is not an approvable draft");
      }
      return version(kind, versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public OrganisationView endDate(
      OrganisationKind kind,
      UUID versionId,
      LocalDate effectiveTo,
      long expectedVersion,
      String actor,
      Instant now) {
    try {
      Long affected =
          jdbc.queryForObject(
              "select organisation.end_date_version(?,?,?,?,?,?,?)",
              Long.class,
              kind.name(),
              TenantContext.require(),
              versionId,
              Date.valueOf(effectiveTo),
              expectedVersion,
              actor,
              Timestamp.from(now));
      if (affected == null || affected != 1) {
        throw stale(
            "Version changed or cannot be end-dated at the requested date");
      }
      return version(kind, versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public OrganisationView retire(
      OrganisationKind kind,
      UUID identityId,
      LocalDate effectiveDate,
      long expectedIdentityVersion,
      String reason,
      String actor,
      Instant now) {
    try {
      UUID finalVersionId =
          jdbc.queryForObject(
              "select organisation.retire_identity(?,?,?,?,?,?,?,?)",
              UUID.class,
              kind.name(),
              TenantContext.require(),
              identityId,
              Date.valueOf(effectiveDate),
              expectedIdentityVersion,
              reason,
              actor,
              Timestamp.from(now));
      if (finalVersionId == null) {
        throw lifecycleConflict(
            "Organisation identity could not be retired");
      }
      return version(kind, finalVersionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  private void insertVersion(
      Spec spec,
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      OrganisationWriteRequest request,
      String actor) {
    switch (spec.kind) {
      case LEGAL_ENTITY ->
          jdbc.update(
              "insert into organisation.legal_entity_version"
                  + "(id,tenant_id,legal_entity_id,version_sequence,"
                  + "name,country_code,currency,effective_from,effective_to,"
                  + "approval_status,supersedes_version_id,created_by,updated_by)"
                  + " values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
              versionId,
              TenantContext.require(),
              identityId,
              sequence,
              request.name(),
              defaulted(request.countryCode(), "IN"),
              defaulted(request.currency(), "INR"),
              request.effectiveFrom(),
              request.effectiveTo(),
              supersedes,
              actor,
              actor);
      case PAYROLL_STATUTORY_UNIT ->
          jdbc.update(
              "insert into organisation.payroll_statutory_unit_version"
                  + "(id,tenant_id,payroll_statutory_unit_id,"
                  + "legal_entity_version_id,version_sequence,name,"
                  + "responsibility_scope,effective_from,effective_to,"
                  + "approval_status,supersedes_version_id,created_by,updated_by)"
                  + " values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
              versionId,
              TenantContext.require(),
              identityId,
              request.parentVersionId(),
              sequence,
              request.name(),
              defaulted(
                  request.responsibilityScope(),
                  "TAX_AND_STATUTORY"),
              request.effectiveFrom(),
              request.effectiveTo(),
              supersedes,
              actor,
              actor);
      case ESTABLISHMENT ->
          jdbc.update(
              "insert into organisation.establishment_version"
                  + "(id,tenant_id,establishment_id,"
                  + "payroll_statutory_unit_version_id,version_sequence,"
                  + "name,state_code,establishment_type,effective_from,"
                  + "effective_to,approval_status,supersedes_version_id,"
                  + "created_by,updated_by)"
                  + " values (?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
              versionId,
              TenantContext.require(),
              identityId,
              request.parentVersionId(),
              sequence,
              request.name(),
              request.stateCode(),
              defaulted(request.establishmentType(), "OTHER"),
              request.effectiveFrom(),
              request.effectiveTo(),
              supersedes,
              actor,
              actor);
    }
  }

  private OrganisationView map(
      ResultSet result, int row) throws SQLException {
    Timestamp retiredAt = result.getTimestamp("retired_at");
    return new OrganisationView(
        OrganisationKind.valueOf(result.getString("kind")),
        result.getObject("identity_id", UUID.class),
        result.getString("code"),
        result.getString("identity_status"),
        result.getLong("identity_version_no"),
        result.getObject(
            "retirement_effective_date", LocalDate.class),
        result.getString("retirement_reason"),
        retiredAt == null ? null : retiredAt.toInstant(),
        result.getString("retired_by"),
        result.getObject("version_id", UUID.class),
        result.getInt("version_sequence"),
        result.getLong("version_no"),
        result.getString("name"),
        result.getString("country_code"),
        result.getString("currency"),
        result.getString("state_code"),
        result.getObject("parent_version_id", UUID.class),
        result.getString("responsibility_scope"),
        result.getString("establishment_type"),
        result.getObject("effective_from", LocalDate.class),
        result.getObject("effective_to", LocalDate.class),
        result.getString("approval_status"),
        result.getObject("supersedes_version_id", UUID.class),
        result.getBoolean("superseded"),
        result.getString("version_created_by"),
        result.getString("approved_by"));
  }

  private RuntimeException translate(
      DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    String message =
        sql == null || sql.getMessage() == null
            ? ""
            : sql.getMessage().toLowerCase(Locale.ROOT);

    return switch (state) {
      case "23505" ->
          new OrganisationProblemException(
              HttpStatus.CONFLICT,
              "urn:problem:organisation:duplicate",
              "Organisation conflict",
              "An organisation identity or version already uses the requested unique value",
              exception);
      case "23P01" ->
          lifecycleConflict(
              "The approved effective range overlaps another approved version",
              exception);
      case "23503" ->
          lifecycleConflict(
              "The requested parent version does not exist in the current tenant",
              exception);
      case "23514" -> {
        if (message.contains("code_format")
            || message.contains("responsibility_scope")
            || message.contains("establishment_version_type")) {
          yield invalid(
              "An organisation code or classification value is invalid",
              exception);
        }
        yield lifecycleConflict(
            "The requested hierarchy or effective range violates an organisation dependency",
            exception);
      }
      case "P5A01" ->
          new OrganisationProblemException(
              HttpStatus.CONFLICT,
              "urn:problem:organisation:maker-checker",
              "Maker-checker conflict",
              "The creator of a version cannot approve the same version",
              exception);
      case "P5A02" -> retired(exception);
      case "P5A03" ->
          lifecycleConflict(
              "The organisation lifecycle transition is blocked",
              exception);
      case "P5A04" ->
          stale(
              "The organisation identity or version changed",
              exception);
      case "P5A05" ->
          new ResourceNotFoundException(
              "Organisation identity or version was not found");
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:forbidden",
              "Organisation operation forbidden",
              "The organisation operation is not permitted in the current tenant context",
              exception);
      default -> exception;
    };
  }

  private SQLException sqlException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sql) {
        return sql;
      }
      current = current.getCause();
    }
    return null;
  }

  private OrganisationProblemException invalid(
      String detail, Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "urn:problem:organisation:invalid",
        "Invalid organisation request",
        detail,
        cause);
  }

  private OrganisationProblemException lifecycleConflict(
      String detail) {
    return lifecycleConflict(detail, null);
  }

  private OrganisationProblemException lifecycleConflict(
      String detail, Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:lifecycle-conflict",
        "Organisation lifecycle conflict",
        detail,
        cause);
  }

  private OrganisationProblemException stale(
      String detail) {
    return stale(detail, null);
  }

  private OrganisationProblemException stale(
      String detail, Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:stale",
        "Organisation version conflict",
        detail,
        cause);
  }

  private OrganisationProblemException retired() {
    return retired(null);
  }

  private OrganisationProblemException retired(
      Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:retired",
        "Organisation identity is retired",
        "A retired organisation identity cannot accept lifecycle changes",
        cause);
  }

  private String defaulted(
      String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Spec(
      OrganisationKind kind,
      String identityTable,
      String versionTable,
      String identityFk,
      String select) {
    static Spec of(OrganisationKind kind) {
      String identity =
          switch (kind) {
            case LEGAL_ENTITY -> "legal_entity";
            case PAYROLL_STATUTORY_UNIT ->
                "payroll_statutory_unit";
            case ESTABLISHMENT -> "establishment";
          };
      String version = identity + "_version";
      String identityFk = identity + "_id";
      String extras =
          switch (kind) {
            case LEGAL_ENTITY ->
                "v.country_code::text country_code,"
                    + "v.currency::text currency,"
                    + "null::text state_code,"
                    + "null::uuid parent_version_id,"
                    + "null::text responsibility_scope,"
                    + "null::text establishment_type";
            case PAYROLL_STATUTORY_UNIT ->
                "null::text country_code,"
                    + "null::text currency,"
                    + "null::text state_code,"
                    + "v.legal_entity_version_id parent_version_id,"
                    + "v.responsibility_scope::text responsibility_scope,"
                    + "null::text establishment_type";
            case ESTABLISHMENT ->
                "null::text country_code,"
                    + "null::text currency,"
                    + "v.state_code::text state_code,"
                    + "v.payroll_statutory_unit_version_id parent_version_id,"
                    + "null::text responsibility_scope,"
                    + "v.establishment_type::text establishment_type";
          };
      String select =
          "select '"
              + kind.name()
              + "' kind,"
              + "i.id identity_id,"
              + "i.code,"
              + "i.status identity_status,"
              + "i.version_no identity_version_no,"
              + "i.retirement_effective_date,"
              + "i.retirement_reason,"
              + "i.retired_at,"
              + "i.retired_by,"
              + "v.id version_id,"
              + "v.version_sequence,"
              + "v.version_no,"
              + "v.name,"
              + extras
              + ",v.effective_from,"
              + "v.effective_to,"
              + "v.approval_status,"
              + "v.supersedes_version_id,"
              + "exists(select 1 from organisation."
              + version
              + " successor where successor.tenant_id=v.tenant_id"
              + " and successor.supersedes_version_id=v.id) superseded,"
              + "v.created_by version_created_by,"
              + "v.approved_by "
              + "from organisation."
              + identity
              + " i join organisation."
              + version
              + " v on v.tenant_id=i.tenant_id and v."
              + identityFk
              + "=i.id";
      return new Spec(
          kind, identity, version, identityFk, select);
    }
  }
}
