package com.acme.hrms.payroll.employeepayroll.internal.infrastructure;

import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EmployeeBankAccountView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionLineRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionLineView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentReadinessFindingView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PayrollIdentifierView;
import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCrypto;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeIdentityPaymentRepository {
  private final JdbcTemplate jdbc;

  public EmployeeIdentityPaymentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollIdentifierView createIdentifier(
      UUID relationshipId,
      UUID identityId,
      String schemeCode,
      EmployeeSensitiveCrypto.EncryptedValue encrypted,
      String sourceAuthority,
      String sourceReference,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String actor,
      Instant at) {
    UUID tenant = TenantContext.require();
    UUID stableId = identityId == null ? UUID.randomUUID() : identityId;
    if (identityId == null) {
      jdbc.update(
          """
          insert into employee_payroll.payroll_identifier(
            id, tenant_id, payroll_relationship_id, scheme_code,
            created_at, created_by, updated_at, updated_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          stableId, tenant, relationshipId, upper(schemeCode),
          Timestamp.from(at), actor, Timestamp.from(at), actor);
    } else {
      lockIdentity(
          """
          select id from employee_payroll.payroll_identifier
           where tenant_id=? and id=? and payroll_relationship_id=?
           for update
          """,
          "Payroll identifier identity not found for relationship",
          tenant, stableId, relationshipId);
    }

    int sequence =
        nextSequence(
            """
            select coalesce(max(version_sequence),0)+1
              from employee_payroll.payroll_identifier_version
             where tenant_id=? and payroll_identifier_id=?
            """,
            tenant, stableId);
    UUID predecessor =
        previousVersion(
            """
            select id from employee_payroll.payroll_identifier_version
             where tenant_id=? and payroll_identifier_id=?
             order by version_sequence desc limit 1
            """,
            tenant, stableId);

    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.payroll_identifier_version(
          id, tenant_id, payroll_identifier_id, payroll_relationship_id,
          scheme_code, version_sequence, identifier_ciphertext, identifier_iv,
          encryption_key_version, identifier_fingerprint, masked_identifier,
          source_authority, source_reference, effective_from, effective_to,
          supersedes_version_id, created_at, created_by, updated_at, updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        versionId, tenant, stableId, relationshipId, upper(schemeCode), sequence,
        encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion(),
        encrypted.fingerprint(), encrypted.maskedValue(),
        blankToNull(sourceAuthority), blankToNull(sourceReference),
        effectiveFrom, effectiveTo, predecessor,
        Timestamp.from(at), actor, Timestamp.from(at), actor);
    return identifierVersion(versionId);
  }

  public List<PayrollIdentifierView> identifiers(UUID relationshipId) {
    return jdbc.query(
        IDENTIFIER_SELECT
            + " where identity.tenant_id=? and identity.payroll_relationship_id=?"
            + " order by identity.scheme_code, version.version_sequence desc",
        this::mapIdentifier,
        TenantContext.require(), relationshipId);
  }

  public PayrollIdentifierView identifierVersion(UUID versionId) {
    return one(
        IDENTIFIER_SELECT + " where version.tenant_id=? and version.id=?",
        this::mapIdentifier,
        "Payroll identifier version not found",
        TenantContext.require(), versionId);
  }

  public IdentifierSecret identifierSecret(UUID versionId) {
    return one(
        """
        select identity.id identity_id,
               identity.payroll_relationship_id,
               version.id version_id,
               identity.scheme_code,
               version.identifier_ciphertext,
               version.identifier_iv,
               version.encryption_key_version,
               version.masked_identifier,
               version.effective_from,
               version.effective_to
          from employee_payroll.payroll_identifier_version version
          join employee_payroll.payroll_identifier identity
            on identity.tenant_id=version.tenant_id
           and identity.id=version.payroll_identifier_id
         where version.tenant_id=? and version.id=?
        """,
        (rs, rowNum) ->
            new IdentifierSecret(
                uuid(rs, "identity_id"),
                uuid(rs, "payroll_relationship_id"),
                uuid(rs, "version_id"),
                rs.getString("scheme_code"),
                rs.getBytes("identifier_ciphertext"),
                rs.getBytes("identifier_iv"),
                rs.getString("encryption_key_version"),
                rs.getString("masked_identifier"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class)),
        "Payroll identifier version not found",
        TenantContext.require(), versionId);
  }

  public PayrollIdentifierView verifyIdentifier(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.verify_payroll_identifier_version(?,?,?,?,?,?)",
        "Payroll identifier verification state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return identifierVersion(versionId);
  }

  public PayrollIdentifierView approveIdentifier(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.activate_payroll_identifier_version(?,?,?,?,?,?)",
        "Payroll identifier approval state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return identifierVersion(versionId);
  }

  public IdentityMismatchView createMismatch(
      UUID relationshipId, String affectedField, String sourceKind,
      String sourceAuthority, String sourceReference,
      String authoritativeFingerprint, String observedFingerprint,
      String classification, String paymentImpact, String correctionOwner,
      String actor, Instant at) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.identity_mismatch_case(
          id, tenant_id, payroll_relationship_id, affected_field, source_kind,
          source_authority, source_reference, authoritative_fingerprint,
          observed_fingerprint, classification, payment_impact,
          correction_owner, detected_at, created_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id, TenantContext.require(), relationshipId, upper(affectedField),
        upper(sourceKind), blankToNull(sourceAuthority),
        blankToNull(sourceReference), authoritativeFingerprint,
        observedFingerprint, upper(classification), upper(paymentImpact),
        correctionOwner.strip(), Timestamp.from(at), actor);
    return mismatch(id);
  }

  public List<IdentityMismatchView> mismatches(UUID relationshipId) {
    return jdbc.query(
        MISMATCH_SELECT
            + " where tenant_id=? and payroll_relationship_id=?"
            + " order by detected_at desc, id",
        this::mapMismatch,
        TenantContext.require(), relationshipId);
  }

  public IdentityMismatchView mismatch(UUID id) {
    return one(
        MISMATCH_SELECT + " where tenant_id=? and id=?",
        this::mapMismatch,
        "Identity mismatch case not found",
        TenantContext.require(), id);
  }

  public IdentityMismatchView resolveMismatch(
      UUID id, long expectedVersion, String resolution, String reason,
      String evidenceRef, String actor, Instant at) {
    affected(
        "select employee_payroll.resolve_identity_mismatch(?,?,?,?,?,?,?,?)",
        "Identity mismatch resolution state changed",
        TenantContext.require(), id, expectedVersion, upper(resolution),
        reason.strip(), evidenceRef.strip(), actor, Timestamp.from(at));
    return mismatch(id);
  }

  public EmployeeBankAccountView createBankAccount(
      UUID relationshipId, UUID identityId, String code, String bankName,
      String branchName, String routingCode, String holderFingerprint,
      String maskedHolder, String currency,
      EmployeeSensitiveCrypto.EncryptedValue encrypted,
      LocalDate effectiveFrom, LocalDate effectiveTo,
      String actor, Instant at) {
    UUID tenant = TenantContext.require();
    UUID stableId = identityId == null ? UUID.randomUUID() : identityId;
    if (identityId == null) {
      jdbc.update(
          """
          insert into employee_payroll.employee_bank_account(
            id, tenant_id, payroll_relationship_id, code,
            created_at, created_by, updated_at, updated_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          stableId, tenant, relationshipId, upper(code),
          Timestamp.from(at), actor, Timestamp.from(at), actor);
    } else {
      lockIdentity(
          """
          select id from employee_payroll.employee_bank_account
           where tenant_id=? and id=? and payroll_relationship_id=?
           for update
          """,
          "Employee bank-account identity not found for relationship",
          tenant, stableId, relationshipId);
    }

    int sequence =
        nextSequence(
            """
            select coalesce(max(version_sequence),0)+1
              from employee_payroll.employee_bank_account_version
             where tenant_id=? and employee_bank_account_id=?
            """,
            tenant, stableId);
    UUID predecessor =
        previousVersion(
            """
            select id from employee_payroll.employee_bank_account_version
             where tenant_id=? and employee_bank_account_id=?
             order by version_sequence desc limit 1
            """,
            tenant, stableId);

    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.employee_bank_account_version(
          id, tenant_id, employee_bank_account_id, payroll_relationship_id,
          version_sequence, bank_name, branch_name, routing_code,
          account_holder_fingerprint, masked_account_holder_name,
          currency_code, account_number_ciphertext, account_number_iv,
          encryption_key_version, account_number_fingerprint,
          account_number_last4, effective_from, effective_to,
          supersedes_version_id, created_at, created_by, updated_at, updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        versionId, tenant, stableId, relationshipId, sequence,
        bankName.strip(), blankToNull(branchName), blankToNull(routingCode),
        holderFingerprint, maskedHolder, upper(currency),
        encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion(),
        encrypted.fingerprint(), encrypted.last4(), effectiveFrom, effectiveTo,
        predecessor, Timestamp.from(at), actor, Timestamp.from(at), actor);
    return bankVersion(versionId);
  }

  public List<EmployeeBankAccountView> bankAccounts(UUID relationshipId) {
    return jdbc.query(
        BANK_SELECT
            + " where identity.tenant_id=? and identity.payroll_relationship_id=?"
            + " order by identity.code, version.version_sequence desc",
        this::mapBank,
        TenantContext.require(), relationshipId);
  }

  public EmployeeBankAccountView bankVersion(UUID versionId) {
    return one(
        BANK_SELECT + " where version.tenant_id=? and version.id=?",
        this::mapBank,
        "Employee bank-account version not found",
        TenantContext.require(), versionId);
  }

  public BankSecret bankSecret(UUID versionId) {
    return one(
        """
        select identity.id identity_id,
               identity.payroll_relationship_id,
               version.id version_id,
               identity.code,
               version.account_number_ciphertext,
               version.account_number_iv,
               version.encryption_key_version,
               version.account_number_last4,
               version.effective_from,
               version.effective_to
          from employee_payroll.employee_bank_account_version version
          join employee_payroll.employee_bank_account identity
            on identity.tenant_id=version.tenant_id
           and identity.id=version.employee_bank_account_id
         where version.tenant_id=? and version.id=?
        """,
        (rs, rowNum) ->
            new BankSecret(
                uuid(rs, "identity_id"),
                uuid(rs, "payroll_relationship_id"),
                uuid(rs, "version_id"),
                rs.getString("code"),
                rs.getBytes("account_number_ciphertext"),
                rs.getBytes("account_number_iv"),
                rs.getString("encryption_key_version"),
                rs.getString("account_number_last4"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class)),
        "Employee bank-account version not found",
        TenantContext.require(), versionId);
  }

  public EmployeeBankAccountView verifyBank(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.verify_employee_bank_account_version(?,?,?,?,?,?)",
        "Employee bank verification state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return bankVersion(versionId);
  }

  public EmployeeBankAccountView reviewBankImpact(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.review_employee_bank_account_impact(?,?,?,?,?,?)",
        "Employee bank impact-review state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return bankVersion(versionId);
  }

  public EmployeeBankAccountView approveBank(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.activate_employee_bank_account_version(?,?,?,?,?,?)",
        "Employee bank approval state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return bankVersion(versionId);
  }

  public EmployeeBankAccountView suspendBank(
      UUID versionId, long expectedVersion, String actor,
      String reason, Instant at) {
    affected(
        "select employee_payroll.suspend_employee_bank_account_version(?,?,?,?,?,?)",
        "Employee bank suspension state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        reason, Timestamp.from(at));
    return bankVersion(versionId);
  }

  public PaymentInstructionView createInstruction(
      UUID relationshipId, UUID identityId, String code, String currency,
      String allocationMode, LocalDate effectiveFrom, LocalDate effectiveTo,
      List<PaymentInstructionLineRequest> lines, String actor, Instant at) {
    UUID tenant = TenantContext.require();
    UUID stableId = identityId == null ? UUID.randomUUID() : identityId;
    if (identityId == null) {
      jdbc.update(
          """
          insert into employee_payroll.payment_instruction_set(
            id, tenant_id, payroll_relationship_id, code,
            created_at, created_by, updated_at, updated_by
          ) values (?,?,?,?,?,?,?,?)
          """,
          stableId, tenant, relationshipId, upper(code),
          Timestamp.from(at), actor, Timestamp.from(at), actor);
    } else {
      lockIdentity(
          """
          select id from employee_payroll.payment_instruction_set
           where tenant_id=? and id=? and payroll_relationship_id=?
           for update
          """,
          "Payment-instruction identity not found for relationship",
          tenant, stableId, relationshipId);
    }

    int sequence =
        nextSequence(
            """
            select coalesce(max(version_sequence),0)+1
              from employee_payroll.payment_instruction_set_version
             where tenant_id=? and payment_instruction_set_id=?
            """,
            tenant, stableId);
    UUID predecessor =
        previousVersion(
            """
            select id from employee_payroll.payment_instruction_set_version
             where tenant_id=? and payment_instruction_set_id=?
             order by version_sequence desc limit 1
            """,
            tenant, stableId);

    UUID versionId = UUID.randomUUID();
    jdbc.update(
        """
        insert into employee_payroll.payment_instruction_set_version(
          id, tenant_id, payment_instruction_set_id, payroll_relationship_id,
          version_sequence, currency_code, allocation_mode, effective_from,
          effective_to, supersedes_version_id, created_at, created_by,
          updated_at, updated_by
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        versionId, tenant, stableId, relationshipId, sequence,
        upper(currency), upper(allocationMode), effectiveFrom, effectiveTo,
        predecessor, Timestamp.from(at), actor, Timestamp.from(at), actor);

    for (PaymentInstructionLineRequest line : lines) {
      jdbc.update(
          """
          insert into employee_payroll.payment_instruction_line(
            id, tenant_id, payment_instruction_set_version_id,
            payroll_relationship_id, line_sequence,
            employee_bank_account_version_id, line_type, percentage,
            fixed_amount, created_at, created_by
          ) values (?,?,?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(), tenant, versionId, relationshipId,
          line.lineSequence(), line.employeeBankAccountVersionId(),
          upper(line.lineType()), line.percentage(), line.fixedAmount(),
          Timestamp.from(at), actor);
    }
    return instructionVersion(versionId);
  }

  public List<PaymentInstructionView> instructions(UUID relationshipId) {
    List<PaymentInstructionView> versions =
        jdbc.query(
            INSTRUCTION_SELECT
                + " where identity.tenant_id=? and identity.payroll_relationship_id=?"
                + " order by identity.code, version.version_sequence desc",
            this::mapInstructionWithoutLines,
            TenantContext.require(), relationshipId);
    return versions.stream()
        .map(view -> withLines(view, instructionLines(view.versionId())))
        .toList();
  }

  public PaymentInstructionView instructionVersion(UUID versionId) {
    PaymentInstructionView view =
        one(
            INSTRUCTION_SELECT + " where version.tenant_id=? and version.id=?",
            this::mapInstructionWithoutLines,
            "Payment instruction version not found",
            TenantContext.require(), versionId);
    return withLines(view, instructionLines(versionId));
  }

  public PaymentInstructionView reviewInstructionImpact(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.review_payment_instruction_impact(?,?,?,?,?,?)",
        "Payment instruction impact-review state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return instructionVersion(versionId);
  }

  public PaymentInstructionView approveInstruction(
      UUID versionId, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.activate_payment_instruction_version(?,?,?,?,?,?)",
        "Payment instruction approval state changed",
        TenantContext.require(), versionId, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return instructionVersion(versionId);
  }

  public PaymentRestrictionView createRestriction(
      UUID relationshipId, String kind, String sourceReference,
      String reasonCode, String evidenceRef, LocalDate effectiveFrom,
      LocalDate effectiveTo, String actor, Instant at) {
    UUID tenant = TenantContext.require();
    UUID id = UUID.randomUUID();
    affected(
        """
        select employee_payroll.create_payment_restriction(
          ?,?,?,?,?,?,?,?,?,?,?
        )
        """,
        "Payment restriction creation failed",
        tenant, id, relationshipId, upper(kind), sourceReference.strip(),
        upper(reasonCode), evidenceRef.strip(), effectiveFrom, effectiveTo,
        actor, Timestamp.from(at));
    return restriction(id);
  }

  public List<PaymentRestrictionView> restrictions(UUID relationshipId) {
    return jdbc.query(
        RESTRICTION_SELECT
            + " where restriction.tenant_id=? and restriction.payroll_relationship_id=?"
            + " order by restriction.created_at desc",
        this::mapRestriction,
        TenantContext.require(), relationshipId);
  }

  public PaymentRestrictionView restriction(UUID id) {
    return one(
        RESTRICTION_SELECT + " where restriction.tenant_id=? and restriction.id=?",
        this::mapRestriction,
        "Payment restriction not found",
        TenantContext.require(), id);
  }

  public PaymentRestrictionView clearRestriction(
      UUID id, long expectedVersion, String actor,
      String evidenceRef, Instant at) {
    affected(
        "select employee_payroll.clear_payment_restriction(?,?,?,?,?,?)",
        "Payment restriction state changed",
        TenantContext.require(), id, expectedVersion, actor,
        evidenceRef, Timestamp.from(at));
    return restriction(id);
  }

  public List<PaymentReadinessFindingView> readinessFindings(
      UUID relationshipId, String currency, LocalDate asOf) {
    return jdbc.query(
        """
        select severity, finding_code, detail
          from employee_payroll.payment_readiness_findings(?,?,?,?)
        """,
        (rs, rowNum) ->
            new PaymentReadinessFindingView(
                rs.getString("severity"),
                rs.getString("finding_code"),
                rs.getString("detail")),
        TenantContext.require(), relationshipId, upper(currency), asOf);
  }

  private List<PaymentInstructionLineView> instructionLines(UUID versionId) {
    return jdbc.query(
        """
        select id, line_sequence, employee_bank_account_version_id,
               line_type, percentage, fixed_amount
          from employee_payroll.payment_instruction_line
         where tenant_id=? and payment_instruction_set_version_id=?
         order by line_sequence
        """,
        (rs, rowNum) ->
            new PaymentInstructionLineView(
                uuid(rs, "id"),
                rs.getInt("line_sequence"),
                uuid(rs, "employee_bank_account_version_id"),
                rs.getString("line_type"),
                rs.getBigDecimal("percentage"),
                rs.getBigDecimal("fixed_amount")),
        TenantContext.require(), versionId);
  }

  private PaymentInstructionView withLines(
      PaymentInstructionView view, List<PaymentInstructionLineView> lines) {
    return new PaymentInstructionView(
        view.identityId(), view.payrollRelationshipId(), view.code(),
        view.identityStatus(), view.versionId(), view.versionSequence(),
        view.versionNo(), view.currencyCode(), view.allocationMode(),
        view.effectiveFrom(), view.effectiveTo(), view.lifecycleStatus(),
        view.impactReviewedAt(), view.impactReviewedBy(),
        view.impactReviewEvidenceRef(), view.approvedAt(), view.approvedBy(),
        view.approvalEvidenceRef(), view.supersedesVersionId(), lines);
  }

  private PayrollIdentifierView mapIdentifier(ResultSet rs, int rowNum)
      throws SQLException {
    return new PayrollIdentifierView(
        uuid(rs, "identity_id"), uuid(rs, "payroll_relationship_id"),
        rs.getString("scheme_code"), rs.getString("identity_status"),
        uuid(rs, "version_id"), rs.getInt("version_sequence"),
        rs.getLong("version_no"), rs.getString("masked_identifier"),
        rs.getString("source_authority"), rs.getString("source_reference"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"),
        rs.getString("verification_evidence_ref"), instant(rs, "verified_at"),
        rs.getString("verified_by"), instant(rs, "approved_at"),
        rs.getString("approved_by"), rs.getString("approval_evidence_ref"),
        nullableUuid(rs, "supersedes_version_id"));
  }

  private IdentityMismatchView mapMismatch(ResultSet rs, int rowNum)
      throws SQLException {
    return new IdentityMismatchView(
        uuid(rs, "id"), uuid(rs, "payroll_relationship_id"),
        rs.getLong("version_no"), rs.getString("affected_field"),
        rs.getString("source_kind"), rs.getString("source_authority"),
        rs.getString("source_reference"), rs.getString("classification"),
        rs.getString("payment_impact"), rs.getString("correction_owner"),
        rs.getString("status"), instant(rs, "detected_at"),
        instant(rs, "resolved_at"), rs.getString("resolved_by"));
  }

  private EmployeeBankAccountView mapBank(ResultSet rs, int rowNum)
      throws SQLException {
    return new EmployeeBankAccountView(
        uuid(rs, "identity_id"), uuid(rs, "payroll_relationship_id"),
        rs.getString("code"), rs.getString("identity_status"),
        uuid(rs, "version_id"), rs.getInt("version_sequence"),
        rs.getLong("version_no"), rs.getString("bank_name"),
        rs.getString("branch_name"), rs.getString("routing_code"),
        rs.getString("masked_account_holder_name"),
        rs.getString("currency_code"),
        "****" + rs.getString("account_number_last4"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"),
        rs.getString("verification_evidence_ref"), instant(rs, "verified_at"),
        rs.getString("verified_by"), instant(rs, "impact_reviewed_at"),
        rs.getString("impact_reviewed_by"),
        rs.getString("impact_review_evidence_ref"), instant(rs, "approved_at"),
        rs.getString("approved_by"), rs.getString("approval_evidence_ref"),
        instant(rs, "suspended_at"), rs.getString("suspended_by"),
        rs.getString("suspension_reason"),
        nullableUuid(rs, "supersedes_version_id"));
  }

  private PaymentInstructionView mapInstructionWithoutLines(
      ResultSet rs, int rowNum) throws SQLException {
    return new PaymentInstructionView(
        uuid(rs, "identity_id"), uuid(rs, "payroll_relationship_id"),
        rs.getString("code"), rs.getString("identity_status"),
        uuid(rs, "version_id"), rs.getInt("version_sequence"),
        rs.getLong("version_no"), rs.getString("currency_code"),
        rs.getString("allocation_mode"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("lifecycle_status"), instant(rs, "impact_reviewed_at"),
        rs.getString("impact_reviewed_by"),
        rs.getString("impact_review_evidence_ref"), instant(rs, "approved_at"),
        rs.getString("approved_by"), rs.getString("approval_evidence_ref"),
        nullableUuid(rs, "supersedes_version_id"), List.of());
  }

  private PaymentRestrictionView mapRestriction(ResultSet rs, int rowNum)
      throws SQLException {
    return new PaymentRestrictionView(
        uuid(rs, "id"), uuid(rs, "payroll_relationship_id"),
        rs.getLong("version_no"), rs.getString("restriction_kind"),
        rs.getString("source_reference"), rs.getString("reason_code"),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("effective_to", LocalDate.class),
        rs.getString("current_state"), instant(rs, "created_at"),
        rs.getString("created_by"), rs.getString("latest_event_type"),
        instant(rs, "latest_event_at"), rs.getString("latest_event_actor"));
  }

  private void affected(String sql, String message, Object... args) {
    Long affected = jdbc.queryForObject(sql, Long.class, args);
    if (affected == null || affected != 1L) {
      throw new ConflictException(message);
    }
  }

  private int nextSequence(String sql, Object... args) {
    Integer value = jdbc.queryForObject(sql, Integer.class, args);
    if (value == null || value < 1) {
      throw new IllegalStateException("Unable to allocate version sequence");
    }
    return value;
  }

  private UUID previousVersion(String sql, Object... args) {
    List<UUID> values = jdbc.query(sql, (rs, rowNum) -> uuid(rs, "id"), args);
    return values.isEmpty() ? null : values.get(0);
  }

  private void lockIdentity(String sql, String message, Object... args) {
    List<UUID> values = jdbc.query(sql, (rs, rowNum) -> uuid(rs, "id"), args);
    if (values.size() != 1) {
      throw new ResourceNotFoundException(message);
    }
  }

  private <T> T one(
      String sql, org.springframework.jdbc.core.RowMapper<T> mapper,
      String message, Object... args) {
    try {
      return jdbc.queryForObject(sql, mapper, args);
    } catch (EmptyResultDataAccessException exception) {
      throw new ResourceNotFoundException(message);
    }
  }

  private static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  private static UUID nullableUuid(ResultSet rs, String column)
      throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  private static Instant instant(ResultSet rs, String column)
      throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static String upper(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public record IdentifierSecret(
      UUID identityId, UUID payrollRelationshipId, UUID versionId,
      String schemeCode, byte[] ciphertext, byte[] iv,
      String encryptionKeyVersion, String maskedValue,
      LocalDate effectiveFrom, LocalDate effectiveTo) {}

  public record BankSecret(
      UUID identityId, UUID payrollRelationshipId, UUID versionId,
      String code, byte[] ciphertext, byte[] iv,
      String encryptionKeyVersion, String last4,
      LocalDate effectiveFrom, LocalDate effectiveTo) {}

  private static final String IDENTIFIER_SELECT = """
      select identity.id identity_id,
             identity.payroll_relationship_id,
             identity.scheme_code,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.masked_identifier,
             version.source_authority,
             version.source_reference,
             version.effective_from,
             version.effective_to,
             version.lifecycle_status,
             version.verification_evidence_ref,
             version.verified_at,
             version.verified_by,
             version.approved_at,
             version.approved_by,
             version.approval_evidence_ref,
             version.supersedes_version_id
        from employee_payroll.payroll_identifier identity
        join employee_payroll.payroll_identifier_version version
          on version.tenant_id=identity.tenant_id
         and version.payroll_identifier_id=identity.id
      """;

  private static final String MISMATCH_SELECT = """
      select id, payroll_relationship_id, version_no, affected_field,
             source_kind, source_authority, source_reference, classification,
             payment_impact, correction_owner, status, detected_at,
             resolved_at, resolved_by
        from employee_payroll.identity_mismatch_case
      """;

  private static final String BANK_SELECT = """
      select identity.id identity_id,
             identity.payroll_relationship_id,
             identity.code,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.bank_name,
             version.branch_name,
             version.routing_code,
             version.masked_account_holder_name,
             version.currency_code,
             version.account_number_last4,
             version.effective_from,
             version.effective_to,
             version.lifecycle_status,
             version.verification_evidence_ref,
             version.verified_at,
             version.verified_by,
             version.impact_reviewed_at,
             version.impact_reviewed_by,
             version.impact_review_evidence_ref,
             version.approved_at,
             version.approved_by,
             version.approval_evidence_ref,
             version.suspended_at,
             version.suspended_by,
             version.suspension_reason,
             version.supersedes_version_id
        from employee_payroll.employee_bank_account identity
        join employee_payroll.employee_bank_account_version version
          on version.tenant_id=identity.tenant_id
         and version.employee_bank_account_id=identity.id
      """;

  private static final String INSTRUCTION_SELECT = """
      select identity.id identity_id,
             identity.payroll_relationship_id,
             identity.code,
             identity.status identity_status,
             version.id version_id,
             version.version_sequence,
             version.version_no,
             version.currency_code,
             version.allocation_mode,
             version.effective_from,
             version.effective_to,
             version.lifecycle_status,
             version.impact_reviewed_at,
             version.impact_reviewed_by,
             version.impact_review_evidence_ref,
             version.approved_at,
             version.approved_by,
             version.approval_evidence_ref,
             version.supersedes_version_id
        from employee_payroll.payment_instruction_set identity
        join employee_payroll.payment_instruction_set_version version
          on version.tenant_id=identity.tenant_id
         and version.payment_instruction_set_id=identity.id
      """;

  private static final String RESTRICTION_SELECT = """
      select restriction.id,
             restriction.payroll_relationship_id,
             restriction.version_no,
             restriction.restriction_kind,
             restriction.source_reference,
             restriction.reason_code,
             restriction.effective_from,
             restriction.effective_to,
             restriction.current_state,
             restriction.created_at,
             restriction.created_by,
             latest.event_type latest_event_type,
             latest.occurred_at latest_event_at,
             latest.actor latest_event_actor
        from employee_payroll.payment_restriction restriction
        join lateral (
          select event.event_type, event.occurred_at, event.actor
            from employee_payroll.payment_restriction_event event
           where event.tenant_id=restriction.tenant_id
             and event.payment_restriction_id=restriction.id
           order by event.event_sequence desc
           limit 1
        ) latest on true
      """;
}
