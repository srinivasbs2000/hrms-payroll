package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.OrganisationKind;
import com.acme.hrms.payroll.organisation.OrganisationView;
import com.acme.hrms.payroll.organisation.OrganisationWriteRequest;
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
public class OrganisationRepository {
  private final JdbcTemplate jdbc;

  public OrganisationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OrganisationView create(OrganisationKind kind, OrganisationWriteRequest request, String actor) {
    Spec spec = Spec.of(kind);
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    jdbc.update("insert into organisation." + spec.identityTable
            + "(id,tenant_id,code,created_by,updated_by) values (?,?,?,?,?)",
        identityId, TenantContext.require(), request.code(), actor, actor);
    insertVersion(spec, versionId, identityId, 1, null, request, actor);
    return history(kind, identityId).stream().filter(v -> v.versionId().equals(versionId)).findFirst().orElseThrow();
  }

  public OrganisationView addVersion(OrganisationKind kind, UUID identityId,
                                     OrganisationWriteRequest request, UUID supersedes, String actor) {
    Spec spec = Spec.of(kind);
    ensureIdentity(spec, identityId);
    Integer next = jdbc.queryForObject("select coalesce(max(version_sequence),0)+1 from organisation."
        + spec.versionTable + " where tenant_id=? and " + spec.identityFk + "=?", Integer.class,
        TenantContext.require(), identityId);
    UUID versionId = UUID.randomUUID();
    insertVersion(spec, versionId, identityId, next == null ? 1 : next, supersedes, request, actor);
    return history(kind, identityId).stream().filter(v -> v.versionId().equals(versionId)).findFirst().orElseThrow();
  }

  public OrganisationView version(OrganisationKind kind, UUID versionId) {
    Spec spec = Spec.of(kind);
    return jdbc.query(spec.select + " where v.tenant_id=? and v.id=?", this::map,
        TenantContext.require(), versionId).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Organisation version was not found"));
  }

  public List<OrganisationView> list(OrganisationKind kind, LocalDate asOf) {
    Spec spec = Spec.of(kind);
    return jdbc.query(spec.select + " where i.tenant_id=? and v.approval_status='APPROVED' "
            + "and v.effective_from<=? and (v.effective_to is null or v.effective_to>?) "
            + "and not exists (select 1 from organisation." + spec.versionTable
            + " s where s.tenant_id=v.tenant_id and s.supersedes_version_id=v.id) order by i.code",
        this::map, TenantContext.require(), Date.valueOf(asOf), Date.valueOf(asOf));
  }

  public OrganisationView current(OrganisationKind kind, UUID identityId, LocalDate asOf) {
    return list(kind, asOf).stream().filter(view -> view.identityId().equals(identityId)).findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("No approved organisation version is effective on " + asOf));
  }

  public List<OrganisationView> history(OrganisationKind kind, UUID identityId) {
    Spec spec = Spec.of(kind);
    return jdbc.query(spec.select + " where i.tenant_id=? and i.id=? order by v.version_sequence",
        this::map, TenantContext.require(), identityId);
  }

  public OrganisationView approve(OrganisationKind kind, UUID versionId, String actor, Instant now) {
    Long affected = jdbc.queryForObject("select organisation.approve_version(?,?,?,?,?)", Long.class,
        kind.name(), TenantContext.require(), versionId, actor, Timestamp.from(now));
    if (affected == null || affected != 1) throw new ConflictException("Version is not an approvable draft");
    return version(kind, versionId);
  }

  public OrganisationView endDate(OrganisationKind kind, UUID versionId, LocalDate effectiveTo,
                                  long expectedVersion, String actor, Instant now) {
    Long affected = jdbc.queryForObject("select organisation.end_date_version(?,?,?,?,?,?,?)", Long.class,
        kind.name(), TenantContext.require(), versionId, Date.valueOf(effectiveTo), expectedVersion, actor,
        Timestamp.from(now));
    if (affected == null || affected != 1) {
      throw new ConflictException("Version changed or cannot be end-dated at the requested date");
    }
    return version(kind, versionId);
  }

  private void ensureIdentity(Spec spec, UUID identityId) {
    Integer count = jdbc.queryForObject("select count(*) from organisation." + spec.identityTable
        + " where tenant_id=? and id=?", Integer.class, TenantContext.require(), identityId);
    if (count == null || count == 0) throw new ResourceNotFoundException("Organisation identity was not found");
  }

  private void insertVersion(Spec spec, UUID versionId, UUID identityId, int sequence,
                             UUID supersedes, OrganisationWriteRequest request, String actor) {
    switch (spec.kind) {
      case LEGAL_ENTITY -> jdbc.update("insert into organisation.legal_entity_version"
              + "(id,tenant_id,legal_entity_id,version_sequence,name,country_code,currency,effective_from,effective_to,approval_status,supersedes_version_id,created_by,updated_by) "
              + "values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
          versionId, TenantContext.require(), identityId, sequence, request.name(),
          defaulted(request.countryCode(), "IN"), defaulted(request.currency(), "INR"),
          request.effectiveFrom(), request.effectiveTo(), supersedes, actor, actor);
      case PAYROLL_STATUTORY_UNIT -> jdbc.update("insert into organisation.payroll_statutory_unit_version"
              + "(id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,version_sequence,name,effective_from,effective_to,approval_status,supersedes_version_id,created_by,updated_by) "
              + "values (?,?,?,?,?,?,?,?, 'DRAFT',?,?,?)",
          versionId, TenantContext.require(), identityId, request.parentVersionId(), sequence,
          request.name(), request.effectiveFrom(), request.effectiveTo(), supersedes, actor, actor);
      case ESTABLISHMENT -> jdbc.update("insert into organisation.establishment_version"
              + "(id,tenant_id,establishment_id,payroll_statutory_unit_version_id,version_sequence,name,state_code,effective_from,effective_to,approval_status,supersedes_version_id,created_by,updated_by) "
              + "values (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
          versionId, TenantContext.require(), identityId, request.parentVersionId(), sequence,
          request.name(), request.stateCode(), request.effectiveFrom(), request.effectiveTo(), supersedes,
          actor, actor);
    }
  }

  private OrganisationView map(ResultSet rs, int row) throws SQLException {
    return new OrganisationView(OrganisationKind.valueOf(rs.getString("kind")),
        rs.getObject("identity_id", UUID.class), rs.getString("code"), rs.getString("identity_status"),
        rs.getObject("version_id", UUID.class), rs.getInt("version_sequence"), rs.getLong("version_no"),
        rs.getString("name"), rs.getString("country_code"), rs.getString("currency"),
        rs.getString("state_code"), rs.getObject("parent_version_id", UUID.class),
        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
        rs.getString("approval_status"), rs.getObject("supersedes_version_id", UUID.class),
        rs.getBoolean("superseded"));
  }

  private String defaulted(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Spec(OrganisationKind kind, String identityTable, String versionTable,
                      String identityFk, String select) {
    static Spec of(OrganisationKind kind) {
      String identity = switch (kind) {
        case LEGAL_ENTITY -> "legal_entity";
        case PAYROLL_STATUTORY_UNIT -> "payroll_statutory_unit";
        case ESTABLISHMENT -> "establishment";
      };
      String version = identity + "_version";
      String identityFk = identity + "_id";
      String extras = switch (kind) {
        case LEGAL_ENTITY -> "v.country_code::text country_code,v.currency::text currency,null::text state_code,null::uuid parent_version_id";
        case PAYROLL_STATUTORY_UNIT -> "null::text country_code,null::text currency,null::text state_code,v.legal_entity_version_id parent_version_id";
        case ESTABLISHMENT -> "null::text country_code,null::text currency,v.state_code::text state_code,v.payroll_statutory_unit_version_id parent_version_id";
      };
      String select = "select '" + kind.name() + "' kind,i.id identity_id,i.code,i.status identity_status,"
          + "v.id version_id,v.version_sequence,v.version_no,v.name," + extras + ",v.effective_from,v.effective_to,"
          + "v.approval_status,v.supersedes_version_id,exists(select 1 from organisation." + version
          + " successor where successor.tenant_id=v.tenant_id and successor.supersedes_version_id=v.id) superseded "
          + "from organisation." + identity + " i join organisation." + version + " v on v.tenant_id=i.tenant_id and v."
          + identityFk + "=i.id";
      return new Spec(kind, identity, version, identityFk, select);
    }
  }
}
