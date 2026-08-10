package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.organisation.EmployerBankAccountCreateRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountVersionWriteRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountView;
import com.acme.hrms.payroll.organisation.OrganisationProblemException;
import com.acme.hrms.payroll.organisation.internal.security.BankAccountCrypto;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployerBankAccountRepository {
  private static final String SELECT =
      """
      select
        i.id identity_id,
        i.code,
        i.owner_kind,
        i.legal_entity_id,
        i.payroll_statutory_unit_id,
        i.status identity_status,
        i.version_no identity_version_no,
        v.id version_id,
        v.version_sequence,
        v.version_no,
        v.bank_name,
        v.branch_name,
        v.routing_code,
        v.account_holder_name,
        v.currency_code,
        v.account_number_last4,
        v.is_default,
        v.effective_from,
        v.effective_to,
        v.lifecycle_status,
        v.verification_evidence_ref,
        v.verified_at,
        v.verified_by,
        v.approved_at,
        v.approved_by,
        v.approval_evidence_ref,
        v.rejected_at,
        v.rejected_by,
        v.rejection_reason,
        v.rejection_evidence_ref,
        v.suspended_at,
        v.suspended_by,
        v.suspension_reason,
        v.supersedes_version_id,
        exists (
          select 1
          from organisation.employer_bank_account_version successor
          where successor.tenant_id=v.tenant_id
            and successor.supersedes_version_id=v.id
        ) superseded,
        v.created_by
      from organisation.employer_bank_account i
      join organisation.employer_bank_account_version v
        on v.tenant_id=i.tenant_id
       and v.employer_bank_account_id=i.id
      """;

  private final JdbcTemplate jdbc;

  public EmployerBankAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public EmployerBankAccountView create(
      EmployerBankAccountCreateRequest request,
      BankAccountCrypto.EncryptedValue encrypted,
      String actor) {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          insert into organisation.employer_bank_account(
            id,tenant_id,code,owner_kind,
            legal_entity_id,payroll_statutory_unit_id,
            created_by,updated_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          identityId,
          TenantContext.require(),
          request.code(),
          request.ownerKind(),
          request.legalEntityId(),
          request.payrollStatutoryUnitId(),
          actor,
          actor);

      String ownerKey = ownerKey(identityId);
      insertVersion(
          versionId,
          identityId,
          ownerKey,
          1,
          null,
          request.version(),
          encrypted,
          actor);
      return version(versionId);
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public EmployerBankAccountView addVersion(
      UUID identityId,
      EmployerBankAccountVersionWriteRequest request,
      BankAccountCrypto.EncryptedValue encrypted,
      String actor) {
    try {
      lockIdentity(identityId);
      Integer next =
          jdbc.queryForObject(
              """
              select coalesce(max(version_sequence),0)+1
              from organisation.employer_bank_account_version
              where tenant_id=? and employer_bank_account_id=?
              """,
              Integer.class,
              TenantContext.require(),
              identityId);
      UUID supersedes =
          jdbc.queryForObject(
              """
              select id
              from organisation.employer_bank_account_version
              where tenant_id=? and employer_bank_account_id=?
              order by version_sequence desc
              limit 1
              """,
              UUID.class,
              TenantContext.require(),
              identityId);
      UUID versionId = UUID.randomUUID();
      insertVersion(
          versionId,
          identityId,
          ownerKey(identityId),
          next == null ? 1 : next,
          supersedes,
          request,
          encrypted,
          actor);
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  public EmployerBankAccountView submit(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return command(
        """
        select organisation.submit_employer_bank_account_version(
          ?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or not a draft",
        actor,
        Timestamp.from(now));
  }

  public EmployerBankAccountView verify(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.verify_employer_bank_account_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or not pending verification",
        actor,
        evidenceRef,
        Timestamp.from(now));
  }

  public EmployerBankAccountView requestApproval(
      UUID versionId,
      long expectedVersion,
      String actor,
      Instant now) {
    return command(
        """
        select organisation.request_employer_bank_account_approval(
          ?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or not verified",
        actor,
        Timestamp.from(now));
  }

  public EmployerBankAccountView approve(
      UUID versionId,
      long expectedVersion,
      String actor,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.activate_employer_bank_account_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or not approval-pending",
        actor,
        evidenceRef,
        Timestamp.from(now));
  }

  public EmployerBankAccountView reject(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      String evidenceRef,
      Instant now) {
    return command(
        """
        select organisation.reject_employer_bank_account_version(
          ?,?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or cannot be rejected",
        actor,
        reason,
        evidenceRef,
        Timestamp.from(now));
  }

  public EmployerBankAccountView suspend(
      UUID versionId,
      long expectedVersion,
      String actor,
      String reason,
      Instant now) {
    return command(
        """
        select organisation.suspend_employer_bank_account_version(
          ?,?,?,?,?,?
        )
        """,
        versionId,
        expectedVersion,
        "Bank-account version is stale or not active",
        actor,
        reason,
        Timestamp.from(now));
  }

  public EmployerBankAccountView version(UUID versionId) {
    return jdbc.query(
            SELECT + " where v.tenant_id=? and v.id=?",
            this::map,
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Employer bank-account version was not found"));
  }

  public List<EmployerBankAccountView> history(UUID identityId) {
    requireIdentityExists(identityId);
    return jdbc.query(
        SELECT
            + """
               where i.tenant_id=? and i.id=?
               order by v.version_sequence desc
               """,
        this::map,
        TenantContext.require(),
        identityId);
  }

  public List<EmployerBankAccountView> list(LocalDate asOf) {
    List<EmployerBankAccountView> candidates =
        jdbc.query(
            SELECT
                + """
                   where i.tenant_id=?
                     and v.lifecycle_status in ('ACTIVE','SUPERSEDED')
                     and v.effective_from<=?
                     and (v.effective_to is null or v.effective_to>?)
                   order by i.code,v.version_sequence desc
                   """,
            this::map,
            TenantContext.require(),
            Date.valueOf(asOf),
            Date.valueOf(asOf));

    Map<UUID, EmployerBankAccountView> resolved = new LinkedHashMap<>();
    for (EmployerBankAccountView candidate : candidates) {
      resolved.putIfAbsent(candidate.identityId(), candidate);
    }
    return List.copyOf(resolved.values());
  }

  public EmployerBankAccountView current(UUID identityId, LocalDate asOf) {
    return list(asOf).stream()
        .filter(view -> view.identityId().equals(identityId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "No active employer bank account is effective on " + asOf));
  }

  public BankSecret secret(UUID versionId) {
    return jdbc.query(
            """
            select
              i.id identity_id,
              i.code,
              i.owner_kind,
              i.legal_entity_id,
              i.payroll_statutory_unit_id,
              v.id version_id,
              v.bank_name,
              v.branch_name,
              v.routing_code,
              v.account_holder_name,
              v.currency_code,
              v.account_number_last4,
              v.account_number_ciphertext,
              v.account_number_iv,
              v.encryption_key_version,
              v.effective_from,
              v.effective_to
            from organisation.employer_bank_account i
            join organisation.employer_bank_account_version v
              on v.tenant_id=i.tenant_id
             and v.employer_bank_account_id=i.id
            where v.tenant_id=? and v.id=?
            """,
            (rs, row) ->
                new BankSecret(
                    rs.getObject("identity_id", UUID.class),
                    rs.getObject("version_id", UUID.class),
                    rs.getString("code"),
                    rs.getString("owner_kind"),
                    rs.getObject("legal_entity_id", UUID.class),
                    rs.getObject("payroll_statutory_unit_id", UUID.class),
                    rs.getString("bank_name"),
                    rs.getString("branch_name"),
                    rs.getString("routing_code"),
                    rs.getString("account_holder_name"),
                    rs.getString("currency_code"),
                    "****" + rs.getString("account_number_last4"),
                    rs.getBytes("account_number_ciphertext"),
                    rs.getBytes("account_number_iv"),
                    rs.getString("encryption_key_version"),
                    rs.getObject("effective_from", LocalDate.class),
                    rs.getObject("effective_to", LocalDate.class)),
            TenantContext.require(),
            versionId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "Employer bank-account version was not found"));
  }

  private EmployerBankAccountView command(
      String sql,
      UUID versionId,
      long expectedVersion,
      String conflictDetail,
      Object... commandArguments) {
    try {
      Object[] parameters = new Object[3 + commandArguments.length];
      parameters[0] = TenantContext.require();
      parameters[1] = versionId;
      parameters[2] = expectedVersion;
      System.arraycopy(
          commandArguments,
          0,
          parameters,
          3,
          commandArguments.length);

      Long changed =
          jdbc.queryForObject(
              sql,
              Long.class,
              parameters);
      if (changed == null || changed != 1) {
        throw conflict(conflictDetail);
      }
      return version(versionId);
    } catch (OrganisationProblemException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw translate(exception);
    }
  }

  private String ownerKey(UUID identityId) {
    String value =
        jdbc.queryForObject(
            """
            select owner_key
            from organisation.employer_bank_account
            where tenant_id=? and id=?
            """,
            String.class,
            TenantContext.require(),
            identityId);
    if (value == null) {
      throw new ResourceNotFoundException(
          "Employer bank-account identity was not found");
    }
    return value;
  }

  private void requireIdentityExists(UUID identityId) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*)
            from organisation.employer_bank_account
            where tenant_id=? and id=?
            """,
            Integer.class,
            TenantContext.require(),
            identityId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException(
          "Employer bank-account identity was not found");
    }
  }

  private void lockIdentity(UUID identityId) {
    Boolean locked =
        jdbc.queryForObject(
            "select organisation.lock_employer_bank_account_identity(?,?)",
            Boolean.class,
            TenantContext.require(),
            identityId);
    if (!Boolean.TRUE.equals(locked)) {
      throw new ResourceNotFoundException(
          "Employer bank-account identity was not found");
    }
  }

  private void insertVersion(
      UUID versionId,
      UUID identityId,
      String ownerKey,
      int sequence,
      UUID supersedes,
      EmployerBankAccountVersionWriteRequest request,
      BankAccountCrypto.EncryptedValue encrypted,
      String actor) {
    jdbc.update(
        """
        insert into organisation.employer_bank_account_version(
          id,tenant_id,employer_bank_account_id,owner_key,version_sequence,
          bank_name,branch_name,routing_code,account_holder_name,currency_code,
          account_number_ciphertext,account_number_iv,encryption_key_version,
          account_number_fingerprint,account_number_last4,is_default,
          effective_from,effective_to,lifecycle_status,supersedes_version_id,
          created_by,updated_by
        ) values (
          ?,?,?,?,?,?,?,?,?,?,
          ?,?,?,?,?,?,
          ?,?,'DRAFT',?,?,?
        )
        """,
        versionId,
        TenantContext.require(),
        identityId,
        ownerKey,
        sequence,
        request.bankName(),
        blankToNull(request.branchName()),
        blankToNull(request.routingCode()),
        request.accountHolderName(),
        request.currencyCode(),
        encrypted.ciphertext(),
        encrypted.iv(),
        encrypted.keyVersion(),
        encrypted.fingerprintHex(),
        encrypted.lastFour(),
        request.defaultAccount(),
        request.effectiveFrom(),
        request.effectiveTo(),
        supersedes,
        actor,
        actor);
  }

  private EmployerBankAccountView map(ResultSet rs, int row)
      throws SQLException {
    return new EmployerBankAccountView(
        rs.getObject("identity_id", UUID.class),
        rs.getString("code"),
        rs.getString("owner_kind"),
        rs.getObject("legal_entity_id", UUID.class),
        rs.getObject("payroll_statutory_unit_id", UUID.class),
        rs.getString("identity_status"),
        rs.getLong("identity_version_no"),
        rs.getObject("version_id", UUID.class),
        rs.getInt("version_sequence"),
        rs.getLong("version_no"),
        rs.getString("bank_name"),
        rs.getString("branch_name"),
        rs.getString("routing_code"),
        rs.getString("account_holder_name"),
        rs.getString("currency_code"),
        "****" + rs.getString("account_number_last4"),
        rs.getBoolean("is_default"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"),
        rs.getString("verification_evidence_ref"),
        instant(rs, "verified_at"),
        rs.getString("verified_by"),
        instant(rs, "approved_at"),
        rs.getString("approved_by"),
        rs.getString("approval_evidence_ref"),
        instant(rs, "rejected_at"),
        rs.getString("rejected_by"),
        rs.getString("rejection_reason"),
        rs.getString("rejection_evidence_ref"),
        instant(rs, "suspended_at"),
        rs.getString("suspended_by"),
        rs.getString("suspension_reason"),
        rs.getObject("supersedes_version_id", UUID.class),
        rs.getBoolean("superseded"),
        rs.getString("created_by"));
  }

  private Instant instant(ResultSet rs, String column)
      throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private RuntimeException translate(DataAccessException exception) {
    SQLException sql = sqlException(exception);
    String state = sql == null ? "" : sql.getSQLState();
    return switch (state) {
      case "23505" ->
          conflict(
              "The bank-account code or version lineage conflicts with existing data",
              exception);
      case "23P01" ->
          conflict(
              "Active bank-account, default-account, or account fingerprint effective ranges conflict",
              exception);
      case "23503" ->
          conflict(
              "The bank-account owner or predecessor version does not exist",
              exception);
      case "23514" ->
          conflict(
              "The bank-account owner, lifecycle, account data, or effective dates are invalid",
              exception);
      case "42501" ->
          new OrganisationProblemException(
              HttpStatus.FORBIDDEN,
              "urn:problem:organisation:employer-bank-account-forbidden",
              "Employer bank-account operation forbidden",
              "The employer bank-account operation is not permitted",
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

  private OrganisationProblemException conflict(String detail) {
    return conflict(detail, null);
  }

  private OrganisationProblemException conflict(
      String detail,
      Throwable cause) {
    return new OrganisationProblemException(
        HttpStatus.CONFLICT,
        "urn:problem:organisation:employer-bank-account-conflict",
        "Employer bank-account conflict",
        detail,
        cause);
  }

  public record BankSecret(
      UUID identityId,
      UUID versionId,
      String code,
      String ownerKind,
      UUID legalEntityId,
      UUID payrollStatutoryUnitId,
      String bankName,
      String branchName,
      String routingCode,
      String accountHolderName,
      String currencyCode,
      String maskedAccountNumber,
      byte[] ciphertext,
      byte[] iv,
      String encryptionKeyVersion,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public BankSecret {
      ciphertext = ciphertext.clone();
      iv = iv.clone();
    }

    @Override
    public byte[] ciphertext() {
      return ciphertext.clone();
    }

    @Override
    public byte[] iv() {
      return iv.clone();
    }
  }
}
