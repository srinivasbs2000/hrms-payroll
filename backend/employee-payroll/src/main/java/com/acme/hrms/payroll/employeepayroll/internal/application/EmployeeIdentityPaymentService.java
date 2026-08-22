package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EmployeeBankAccountView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EmployeeBankAccountWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EvidenceRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchResolveRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.ImpactReviewRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentReadinessView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionClearRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PayrollIdentifierView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PayrollIdentifierWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.RevealRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.RevealView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.SuspendRequest;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeeIdentityPaymentRepository;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeeIdentityPaymentRepository.BankSecret;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeeIdentityPaymentRepository.IdentifierSecret;
import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCrypto;
import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCrypto.Domain;
import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCryptoProvider;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmployeeIdentityPaymentService {
  private final EmployeeIdentityPaymentRepository repository;
  private final EmployeeSensitiveCryptoProvider cryptoProvider;
  private final EmployeePayrollCommandExecutor commands;
  private final EmployeePayrollEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditWriter audit;
  private final Clock clock;

  public EmployeeIdentityPaymentService(
      EmployeeIdentityPaymentRepository repository,
      EmployeeSensitiveCryptoProvider cryptoProvider,
      EmployeePayrollCommandExecutor commands,
      EmployeePayrollEventRecorder recorder,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      AuditWriter audit,
      Clock clock) {
    this.repository = repository;
    this.cryptoProvider = cryptoProvider;
    this.commands = commands;
    this.recorder = recorder;
    this.transactions = transactions;
    this.actor = actor;
    this.audit = audit;
    this.clock = clock;
  }

  public PayrollIdentifierView writeIdentifier(
      UUID relationshipId, String key, PayrollIdentifierWriteRequest request) {
    request.validate();
    EmployeeSensitiveCrypto crypto = cryptoProvider.require();
    Map<String, Object> idempotency = new LinkedHashMap<>();
    idempotency.put("identityId", request.identityId());
    idempotency.put("schemeCode", request.schemeCode());
    idempotency.put("valueFingerprint",
        crypto.fingerprint(Domain.IDENTIFIER, request.value()));
    idempotency.put("sourceAuthority", request.sourceAuthority());
    idempotency.put("sourceReference", request.sourceReference());
    idempotency.put("effectiveFrom", request.effectiveFrom());
    idempotency.put("effectiveTo", request.effectiveTo());
    return commands.execute(
        "employee-payroll:identifier:write:" + relationshipId,
        key,
        idempotency,
        PayrollIdentifierView.class,
        () -> {
          EmployeeSensitiveCrypto.EncryptedValue encrypted =
              crypto.encrypt(Domain.IDENTIFIER, request.value());
          PayrollIdentifierView view =
              repository.createIdentifier(
                  relationshipId,
                  request.identityId(),
                  request.schemeCode(),
                  encrypted,
                  request.sourceAuthority(),
                  request.sourceReference(),
                  request.effectiveFrom(),
                  request.effectiveTo(),
                  actor.require(),
                  clock.instant());
          record("CREATED", "PAYROLL_IDENTIFIER",
              "PayrollIdentifierVersionCreated", view.identityId(),
              view.versionNo() + 1, null, identifierState(view),
              Map.of("versionId", view.versionId(), "schemaVersion", 1));
          return view;
        });
  }

  public List<PayrollIdentifierView> identifiers(UUID relationshipId) {
    return transactions.read(() -> repository.identifiers(relationshipId));
  }

  public PayrollIdentifierView verifyIdentifier(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, EvidenceRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:identifier:verify:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion,
            "evidenceRef", request.evidenceRef()),
        PayrollIdentifierView.class,
        () -> {
          PayrollIdentifierView before = repository.identifierVersion(versionId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          PayrollIdentifierView after =
              repository.verifyIdentifier(
                  versionId, expectedVersion, actor.require(),
                  request.evidenceRef(), clock.instant());
          record("VERIFIED", "PAYROLL_IDENTIFIER",
              "PayrollIdentifierVersionVerified", after.identityId(),
              after.versionNo() + 1, identifierState(before),
              identifierState(after),
              Map.of("versionId", versionId, "schemaVersion", 1));
          return after;
        });
  }

  public PayrollIdentifierView approveIdentifier(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, EvidenceRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:identifier:approve:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion,
            "evidenceRef", request.evidenceRef()),
        PayrollIdentifierView.class,
        () -> {
          PayrollIdentifierView before = repository.identifierVersion(versionId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          PayrollIdentifierView after =
              repository.approveIdentifier(
                  versionId, expectedVersion, actor.require(),
                  request.evidenceRef(), clock.instant());
          record("ACTIVATED", "PAYROLL_IDENTIFIER",
              "PayrollIdentifierVersionActivated", after.identityId(),
              after.versionNo() + 1, identifierState(before),
              identifierState(after),
              Map.of("versionId", versionId, "schemaVersion", 1));
          return after;
        });
  }

  public RevealView revealIdentifier(
      UUID relationshipId, UUID versionId, RevealRequest request) {
    request.validate();
    return transactions.write(() -> {
      IdentifierSecret secret = repository.identifierSecret(versionId);
      requireRelationship(secret.payrollRelationshipId(), relationshipId);
      String value =
          cryptoProvider.require().decrypt(
              Domain.IDENTIFIER,
              secret.ciphertext(),
              secret.iv(),
              secret.encryptionKeyVersion());
      audit.append(
          "IDENTIFIER_REVEALED",
          "PAYROLL_IDENTIFIER",
          secret.identityId(),
          null,
          null,
          revealMetadata(versionId, secret.maskedValue(), request.reason()),
          actor.require());
      return new RevealView(
          secret.identityId(), secret.versionId(), secret.schemeCode(), value,
          secret.effectiveFrom(), secret.effectiveTo());
    });
  }

  public IdentityMismatchView createMismatch(
      UUID relationshipId, String key, IdentityMismatchWriteRequest request) {
    request.validate();
    EmployeeSensitiveCrypto crypto = cryptoProvider.require();
    String authoritative =
        blank(request.authoritativeValue())
            ? null
            : crypto.fingerprint(
                Domain.IDENTITY_VALUE, request.authoritativeValue());
    String observed =
        blank(request.observedValue())
            ? null
            : crypto.fingerprint(
                Domain.IDENTITY_VALUE, request.observedValue());
    Map<String, Object> idempotency = mismatchIdempotency(request);
    idempotency.put("authoritativeFingerprint", authoritative);
    idempotency.put("observedFingerprint", observed);
    return commands.execute(
        "employee-payroll:identity-mismatch:create:" + relationshipId,
        key,
        idempotency,
        IdentityMismatchView.class,
        () -> {
          IdentityMismatchView view =
              repository.createMismatch(
                  relationshipId, request.affectedField(), request.sourceKind(),
                  request.sourceAuthority(), request.sourceReference(),
                  authoritative, observed, request.classification(),
                  request.paymentImpact(), request.correctionOwner(),
                  actor.require(), clock.instant());
          record("CREATED", "IDENTITY_MISMATCH",
              "EmployeeIdentityMismatchCreated", view.id(),
              view.versionNo() + 1, null, mismatchState(view),
              Map.of("schemaVersion", 1));
          return view;
        });
  }

  public List<IdentityMismatchView> mismatches(UUID relationshipId) {
    return transactions.read(() -> repository.mismatches(relationshipId));
  }

  public IdentityMismatchView resolveMismatch(
      UUID relationshipId, UUID caseId, String key,
      long expectedVersion, IdentityMismatchResolveRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:identity-mismatch:resolve:" + caseId,
        key,
        Map.of("expectedVersion", expectedVersion,
            "resolution", request.resolution(),
            "reason", request.reason(),
            "evidenceRef", request.evidenceRef()),
        IdentityMismatchView.class,
        () -> {
          IdentityMismatchView before = repository.mismatch(caseId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          IdentityMismatchView after =
              repository.resolveMismatch(
                  caseId, expectedVersion, request.resolution(),
                  request.reason(), request.evidenceRef(),
                  actor.require(), clock.instant());
          record("RESOLVED", "IDENTITY_MISMATCH",
              "EmployeeIdentityMismatchResolved", after.id(),
              after.versionNo() + 1, mismatchState(before),
              mismatchState(after), Map.of("schemaVersion", 1));
          return after;
        });
  }

  public EmployeeBankAccountView writeBankAccount(
      UUID relationshipId, String key, EmployeeBankAccountWriteRequest request) {
    request.validate();
    EmployeeSensitiveCrypto crypto = cryptoProvider.require();
    Map<String, Object> idempotency = bankIdempotency(request);
    idempotency.put("accountFingerprint",
        crypto.fingerprint(Domain.BANK_ACCOUNT, request.accountNumber()));
    idempotency.put("holderFingerprint",
        crypto.fingerprint(Domain.IDENTITY_VALUE, request.accountHolderName()));
    return commands.execute(
        "employee-payroll:bank-account:write:" + relationshipId,
        key,
        idempotency,
        EmployeeBankAccountView.class,
        () -> {
          EmployeeSensitiveCrypto.EncryptedValue encrypted =
              crypto.encrypt(Domain.BANK_ACCOUNT, request.accountNumber());
          EmployeeBankAccountView view =
              repository.createBankAccount(
                  relationshipId, request.identityId(), request.code(),
                  request.bankName(), request.branchName(), request.routingCode(),
                  crypto.fingerprint(
                      Domain.IDENTITY_VALUE, request.accountHolderName()),
                  crypto.maskName(request.accountHolderName()),
                  request.currencyCode(), encrypted,
                  request.effectiveFrom(), request.effectiveTo(),
                  actor.require(), clock.instant());
          record("CREATED", "EMPLOYEE_BANK_ACCOUNT",
              "EmployeeBankAccountVersionCreated", view.identityId(),
              view.versionNo() + 1, null, bankState(view),
              Map.of("versionId", view.versionId(), "schemaVersion", 1));
          return view;
        });
  }

  public List<EmployeeBankAccountView> bankAccounts(UUID relationshipId) {
    return transactions.read(() -> repository.bankAccounts(relationshipId));
  }

  public EmployeeBankAccountView verifyBank(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, EvidenceRequest request) {
    request.validate();
    return bankTransition(
        relationshipId, versionId, key, expectedVersion,
        "verify", "VERIFIED", "EmployeeBankAccountVersionVerified",
        request.evidenceRef(),
        () -> repository.verifyBank(
            versionId, expectedVersion, actor.require(),
            request.evidenceRef(), clock.instant()));
  }

  public EmployeeBankAccountView reviewBankImpact(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, ImpactReviewRequest request) {
    request.validate();
    return bankTransition(
        relationshipId, versionId, key, expectedVersion,
        "impact-review", "IMPACT_REVIEWED",
        "EmployeeBankAccountImpactReviewed",
        request.evidenceRef(),
        () -> repository.reviewBankImpact(
            versionId, expectedVersion, actor.require(),
            request.evidenceRef(), clock.instant()));
  }

  public EmployeeBankAccountView approveBank(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, EvidenceRequest request) {
    request.validate();
    return bankTransition(
        relationshipId, versionId, key, expectedVersion,
        "approve", "ACTIVATED", "EmployeeBankAccountVersionActivated",
        request.evidenceRef(),
        () -> repository.approveBank(
            versionId, expectedVersion, actor.require(),
            request.evidenceRef(), clock.instant()));
  }

  public EmployeeBankAccountView suspendBank(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, SuspendRequest request) {
    request.validate();
    return bankTransition(
        relationshipId, versionId, key, expectedVersion,
        "suspend", "SUSPENDED", "EmployeeBankAccountVersionSuspended",
        request.reason(),
        () -> repository.suspendBank(
            versionId, expectedVersion, actor.require(),
            request.reason(), clock.instant()));
  }

  public RevealView revealBank(
      UUID relationshipId, UUID versionId, RevealRequest request) {
    request.validate();
    return transactions.write(() -> {
      BankSecret secret = repository.bankSecret(versionId);
      requireRelationship(secret.payrollRelationshipId(), relationshipId);
      String value =
          cryptoProvider.require().decrypt(
              Domain.BANK_ACCOUNT,
              secret.ciphertext(),
              secret.iv(),
              secret.encryptionKeyVersion());
      audit.append(
          "ACCOUNT_NUMBER_REVEALED",
          "EMPLOYEE_BANK_ACCOUNT",
          secret.identityId(),
          null,
          null,
          revealMetadata(
              versionId, "****" + secret.last4(), request.reason()),
          actor.require());
      return new RevealView(
          secret.identityId(), secret.versionId(), "BANK_ACCOUNT", value,
          secret.effectiveFrom(), secret.effectiveTo());
    });
  }

  public PaymentInstructionView writeInstruction(
      UUID relationshipId, String key, PaymentInstructionWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:payment-instruction:write:" + relationshipId,
        key,
        request,
        PaymentInstructionView.class,
        () -> {
          PaymentInstructionView view =
              repository.createInstruction(
                  relationshipId, request.identityId(), request.code(),
                  request.currencyCode(), request.allocationMode(),
                  request.effectiveFrom(), request.effectiveTo(), request.lines(),
                  actor.require(), clock.instant());
          record("CREATED", "PAYMENT_INSTRUCTION",
              "EmployeePaymentInstructionVersionCreated", view.identityId(),
              view.versionNo() + 1, null, instructionState(view),
              Map.of("versionId", view.versionId(), "schemaVersion", 1));
          return view;
        });
  }

  public List<PaymentInstructionView> instructions(UUID relationshipId) {
    return transactions.read(() -> repository.instructions(relationshipId));
  }

  public PaymentInstructionView reviewInstructionImpact(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, ImpactReviewRequest request) {
    request.validate();
    return instructionTransition(
        relationshipId, versionId, key, expectedVersion,
        "impact-review", "IMPACT_REVIEWED",
        "EmployeePaymentInstructionImpactReviewed",
        request.evidenceRef(),
        () -> repository.reviewInstructionImpact(
            versionId, expectedVersion, actor.require(),
            request.evidenceRef(), clock.instant()));
  }

  public PaymentInstructionView approveInstruction(
      UUID relationshipId, UUID versionId, String key,
      long expectedVersion, EvidenceRequest request) {
    request.validate();
    return instructionTransition(
        relationshipId, versionId, key, expectedVersion,
        "approve", "ACTIVATED",
        "EmployeePaymentInstructionVersionActivated",
        request.evidenceRef(),
        () -> repository.approveInstruction(
            versionId, expectedVersion, actor.require(),
            request.evidenceRef(), clock.instant()));
  }

  public PaymentRestrictionView createRestriction(
      UUID relationshipId, String key, PaymentRestrictionWriteRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:payment-restriction:create:" + relationshipId,
        key,
        request,
        PaymentRestrictionView.class,
        () -> {
          PaymentRestrictionView view =
              repository.createRestriction(
                  relationshipId, request.restrictionKind(),
                  request.sourceReference(), request.reasonCode(),
                  request.evidenceRef(), request.effectiveFrom(),
                  request.effectiveTo(), actor.require(), clock.instant());
          record("IMPOSED", "PAYMENT_RESTRICTION",
              "EmployeePaymentRestrictionImposed", view.id(),
              view.versionNo() + 1, null, restrictionState(view),
              Map.of("schemaVersion", 1));
          return view;
        });
  }

  public List<PaymentRestrictionView> restrictions(UUID relationshipId) {
    return transactions.read(() -> repository.restrictions(relationshipId));
  }

  public PaymentRestrictionView clearRestriction(
      UUID relationshipId, UUID restrictionId, String key,
      long expectedVersion, PaymentRestrictionClearRequest request) {
    request.validate();
    return commands.execute(
        "employee-payroll:payment-restriction:clear:" + restrictionId,
        key,
        Map.of("expectedVersion", expectedVersion,
            "evidenceRef", request.evidenceRef()),
        PaymentRestrictionView.class,
        () -> {
          PaymentRestrictionView before = repository.restriction(restrictionId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          PaymentRestrictionView after =
              repository.clearRestriction(
                  restrictionId, expectedVersion, actor.require(),
                  request.evidenceRef(), clock.instant());
          record("CLEARED", "PAYMENT_RESTRICTION",
              "EmployeePaymentRestrictionCleared", after.id(),
              after.versionNo() + 1, restrictionState(before),
              restrictionState(after), Map.of("schemaVersion", 1));
          return after;
        });
  }

  public PaymentReadinessView readiness(
      UUID relationshipId, String currencyCode, LocalDate asOf) {
    String currency =
        currencyCode == null
            ? ""
            : currencyCode.trim().toUpperCase(Locale.ROOT);
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException(
          "currencyCode must be a three-letter ISO code");
    }
    LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
    return transactions.read(() -> {
      var findings =
          repository.readinessFindings(relationshipId, currency, date);
      boolean ready =
          findings.stream()
              .noneMatch(finding -> "BLOCKER".equals(finding.severity()));
      return new PaymentReadinessView(
          relationshipId, currency, date, ready, findings);
    });
  }

  private EmployeeBankAccountView bankTransition(
      UUID relationshipId, UUID versionId, String key, long expectedVersion,
      String operation, String action, String eventType, String evidence,
      java.util.function.Supplier<EmployeeBankAccountView> work) {
    return commands.execute(
        "employee-payroll:bank-account:" + operation + ":" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion, "evidence", evidence),
        EmployeeBankAccountView.class,
        () -> {
          EmployeeBankAccountView before = repository.bankVersion(versionId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          EmployeeBankAccountView after = work.get();
          record(action, "EMPLOYEE_BANK_ACCOUNT", eventType,
              after.identityId(), after.versionNo() + 1,
              bankState(before), bankState(after),
              Map.of("versionId", versionId, "schemaVersion", 1));
          return after;
        });
  }

  private PaymentInstructionView instructionTransition(
      UUID relationshipId, UUID versionId, String key, long expectedVersion,
      String operation, String action, String eventType, String evidence,
      java.util.function.Supplier<PaymentInstructionView> work) {
    return commands.execute(
        "employee-payroll:payment-instruction:" + operation + ":" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion, "evidence", evidence),
        PaymentInstructionView.class,
        () -> {
          PaymentInstructionView before = repository.instructionVersion(versionId);
          requireRelationship(before.payrollRelationshipId(), relationshipId);
          PaymentInstructionView after = work.get();
          record(action, "PAYMENT_INSTRUCTION", eventType,
              after.identityId(), after.versionNo() + 1,
              instructionState(before), instructionState(after),
              Map.of("versionId", versionId, "schemaVersion", 1));
          return after;
        });
  }

  private void record(
      String action, String objectType, String eventType, UUID objectId,
      long aggregateVersion, Map<String, Object> before,
      Map<String, Object> after, Map<String, Object> metadata) {
    recorder.record(
        action, objectType, eventType, objectId,
        Math.max(1, aggregateVersion), before, after, metadata);
  }

  private static void requireRelationship(UUID actual, UUID expected) {
    if (expected == null || !expected.equals(actual)) {
      throw new IllegalArgumentException(
          "Resource does not belong to the payroll relationship in the path");
    }
  }

  private static Map<String, Object> revealMetadata(
      UUID versionId, String masked, String reason) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("versionId", versionId);
    map.put("maskedValue", masked);
    map.put("reason", reason.strip());
    map.put("schemaVersion", 1);
    return map;
  }

  private static Map<String, Object> identifierState(PayrollIdentifierView v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("identityId", v.identityId());
    map.put("payrollRelationshipId", v.payrollRelationshipId());
    map.put("schemeCode", v.schemeCode());
    map.put("identityStatus", v.identityStatus());
    map.put("versionId", v.versionId());
    map.put("versionSequence", v.versionSequence());
    map.put("versionNo", v.versionNo());
    map.put("maskedValue", v.maskedValue());
    map.put("effectiveFrom", v.effectiveFrom());
    map.put("effectiveTo", v.effectiveTo());
    map.put("lifecycleStatus", v.lifecycleStatus());
    map.put("verifiedAt", v.verifiedAt());
    map.put("approvedAt", v.approvedAt());
    return map;
  }

  private static Map<String, Object> mismatchState(IdentityMismatchView v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", v.id());
    map.put("payrollRelationshipId", v.payrollRelationshipId());
    map.put("affectedField", v.affectedField());
    map.put("sourceKind", v.sourceKind());
    map.put("classification", v.classification());
    map.put("paymentImpact", v.paymentImpact());
    map.put("status", v.status());
    map.put("detectedAt", v.detectedAt());
    map.put("resolvedAt", v.resolvedAt());
    return map;
  }

  private static Map<String, Object> bankState(EmployeeBankAccountView v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("identityId", v.identityId());
    map.put("payrollRelationshipId", v.payrollRelationshipId());
    map.put("code", v.code());
    map.put("identityStatus", v.identityStatus());
    map.put("versionId", v.versionId());
    map.put("versionSequence", v.versionSequence());
    map.put("versionNo", v.versionNo());
    map.put("bankName", v.bankName());
    map.put("branchName", v.branchName());
    map.put("routingCode", v.routingCode());
    map.put("maskedAccountHolderName", v.maskedAccountHolderName());
    map.put("currencyCode", v.currencyCode());
    map.put("maskedAccountNumber", v.maskedAccountNumber());
    map.put("effectiveFrom", v.effectiveFrom());
    map.put("effectiveTo", v.effectiveTo());
    map.put("lifecycleStatus", v.lifecycleStatus());
    map.put("verifiedAt", v.verifiedAt());
    map.put("impactReviewedAt", v.impactReviewedAt());
    map.put("approvedAt", v.approvedAt());
    map.put("suspendedAt", v.suspendedAt());
    return map;
  }

  private static Map<String, Object> instructionState(PaymentInstructionView v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("identityId", v.identityId());
    map.put("payrollRelationshipId", v.payrollRelationshipId());
    map.put("code", v.code());
    map.put("identityStatus", v.identityStatus());
    map.put("versionId", v.versionId());
    map.put("versionSequence", v.versionSequence());
    map.put("versionNo", v.versionNo());
    map.put("currencyCode", v.currencyCode());
    map.put("allocationMode", v.allocationMode());
    map.put("effectiveFrom", v.effectiveFrom());
    map.put("effectiveTo", v.effectiveTo());
    map.put("lifecycleStatus", v.lifecycleStatus());
    map.put("impactReviewedAt", v.impactReviewedAt());
    map.put("approvedAt", v.approvedAt());
    map.put("lineCount", v.lines().size());
    return map;
  }

  private static Map<String, Object> restrictionState(PaymentRestrictionView v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", v.id());
    map.put("payrollRelationshipId", v.payrollRelationshipId());
    map.put("restrictionKind", v.restrictionKind());
    map.put("reasonCode", v.reasonCode());
    map.put("effectiveFrom", v.effectiveFrom());
    map.put("effectiveTo", v.effectiveTo());
    map.put("currentState", v.currentState());
    map.put("latestEventType", v.latestEventType());
    map.put("latestEventAt", v.latestEventAt());
    return map;
  }

  private static Map<String, Object> mismatchIdempotency(
      IdentityMismatchWriteRequest request) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("affectedField", request.affectedField());
    map.put("sourceKind", request.sourceKind());
    map.put("sourceAuthority", request.sourceAuthority());
    map.put("sourceReference", request.sourceReference());
    map.put("classification", request.classification());
    map.put("paymentImpact", request.paymentImpact());
    map.put("correctionOwner", request.correctionOwner());
    // Comparison plaintext is deliberately excluded from idempotency persistence.
    map.put("comparisonFingerprintSeeded", true);
    return map;
  }

  private static Map<String, Object> bankIdempotency(
      EmployeeBankAccountWriteRequest request) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("identityId", request.identityId());
    map.put("code", request.code());
    map.put("bankName", request.bankName());
    map.put("branchName", request.branchName());
    map.put("routingCode", request.routingCode());
    map.put("currencyCode", request.currencyCode());
    map.put("effectiveFrom", request.effectiveFrom());
    map.put("effectiveTo", request.effectiveTo());
    // Secret account/holder plaintext is never persisted in idempotency state.
    map.put("secretProvided", true);
    return map;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
