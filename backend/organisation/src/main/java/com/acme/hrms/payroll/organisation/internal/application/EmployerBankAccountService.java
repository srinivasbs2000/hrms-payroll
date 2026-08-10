package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.organisation.EmployerBankAccountCreateRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountEvidenceRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountRejectRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountRevealRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountRevealView;
import com.acme.hrms.payroll.organisation.EmployerBankAccountSuspendRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountVersionWriteRequest;
import com.acme.hrms.payroll.organisation.EmployerBankAccountView;
import com.acme.hrms.payroll.organisation.internal.infrastructure.EmployerBankAccountRepository;
import com.acme.hrms.payroll.organisation.internal.infrastructure.EmployerBankAccountRepository.BankSecret;
import com.acme.hrms.payroll.organisation.internal.security.BankAccountCrypto;
import com.acme.hrms.payroll.organisation.internal.security.BankAccountCryptoProvider;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class EmployerBankAccountService {
  private final EmployerBankAccountRepository repository;
  private final BankAccountCryptoProvider cryptoProvider;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public EmployerBankAccountService(
      EmployerBankAccountRepository repository,
      BankAccountCryptoProvider cryptoProvider,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      DomainEventFactory events,
      OutboxWriter outbox,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.cryptoProvider = cryptoProvider;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
    this.audit = audit;
    this.events = events;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public EmployerBankAccountView create(
      String key,
      EmployerBankAccountCreateRequest request) {
    request.validate();
    return idempotent(
        "employer-bank-account:create",
        key,
        request,
        () -> {
          BankAccountCrypto.EncryptedValue encrypted =
              cryptoProvider.require().encrypt(request.version().accountNumber());
          EmployerBankAccountView created =
              repository.create(request, encrypted, actor.require());
          audit("CREATED", created, null);
          return created;
        });
  }

  public EmployerBankAccountView addVersion(
      UUID identityId,
      String key,
      EmployerBankAccountVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "employer-bank-account:version-create:" + identityId,
        key,
        request,
        () -> {
          BankAccountCrypto.EncryptedValue encrypted =
              cryptoProvider.require().encrypt(request.accountNumber());
          EmployerBankAccountView created =
              repository.addVersion(
                  identityId,
                  request,
                  encrypted,
                  actor.require());
          audit("VERSION_CREATED", created, null);
          return created;
        });
  }

  public EmployerBankAccountView submit(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "employer-bank-account:submit:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () ->
            transition(
                identityId,
                versionId,
                "SUBMITTED_FOR_VERIFICATION",
                () ->
                    repository.submit(
                        versionId,
                        expectedVersion,
                        actor.require(),
                        clock.instant()),
                false));
  }

  public EmployerBankAccountView verify(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      EmployerBankAccountEvidenceRequest request) {
    return idempotent(
        "employer-bank-account:verify:" + versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "evidenceRef",
            request.evidenceRef()),
        () ->
            transition(
                identityId,
                versionId,
                "VERIFIED",
                () ->
                    repository.verify(
                        versionId,
                        expectedVersion,
                        actor.require(),
                        request.evidenceRef(),
                        clock.instant()),
                false));
  }

  public EmployerBankAccountView requestApproval(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "employer-bank-account:request-approval:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () ->
            transition(
                identityId,
                versionId,
                "APPROVAL_REQUESTED",
                () ->
                    repository.requestApproval(
                        versionId,
                        expectedVersion,
                        actor.require(),
                        clock.instant()),
                false));
  }

  public EmployerBankAccountView approve(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      EmployerBankAccountEvidenceRequest request) {
    return idempotent(
        "employer-bank-account:approve:" + versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "evidenceRef",
            request.evidenceRef()),
        () ->
            approveTransition(
                identityId,
                versionId,
                expectedVersion,
                request));
  }

  public EmployerBankAccountView reject(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      EmployerBankAccountRejectRequest request) {
    return idempotent(
        "employer-bank-account:reject:" + versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "reason",
            request.reason(),
            "evidenceRef",
            request.evidenceRef()),
        () ->
            transition(
                identityId,
                versionId,
                "REJECTED",
                () ->
                    repository.reject(
                        versionId,
                        expectedVersion,
                        actor.require(),
                        request.reason(),
                        request.evidenceRef(),
                        clock.instant()),
                false));
  }

  public EmployerBankAccountView suspend(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      EmployerBankAccountSuspendRequest request) {
    return idempotent(
        "employer-bank-account:suspend:" + versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "reason",
            request.reason()),
        () ->
            transition(
                identityId,
                versionId,
                "SUSPENDED",
                () ->
                    repository.suspend(
                        versionId,
                        expectedVersion,
                        actor.require(),
                        request.reason(),
                        clock.instant()),
                true));
  }

  public List<EmployerBankAccountView> list(
      String ownerKind,
      UUID ownerId,
      String currencyCode,
      LocalDate asOf) {
    validateFilters(ownerKind, ownerId, currencyCode);
    return transactions.read(
        () ->
            repository.list(effectiveDate(asOf)).stream()
                .filter(
                    view ->
                        ownerKind == null
                            || ownerKind.equals(view.ownerKind()))
                .filter(
                    view ->
                        ownerId == null
                            || ownerId.equals(ownerId(view)))
                .filter(
                    view ->
                        currencyCode == null
                            || currencyCode.equals(view.currencyCode()))
                .toList());
  }

  public EmployerBankAccountView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  public List<EmployerBankAccountView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }

  public EmployerBankAccountRevealView reveal(
      UUID identityId,
      UUID versionId,
      EmployerBankAccountRevealRequest request) {
    return transactions.write(
        () -> {
          BankSecret secret = repository.secret(versionId);
          requireIdentity(secret.identityId(), identityId);
          String accountNumber =
              cryptoProvider
                  .require()
                  .decrypt(
                      secret.ciphertext(),
                      secret.iv(),
                      secret.encryptionKeyVersion());

          audit.append(
              "ACCOUNT_NUMBER_REVEALED",
              "EMPLOYER_BANK_ACCOUNT",
              identityId,
              null,
              null,
              Map.of(
                  "versionId",
                  versionId,
                  "maskedAccountNumber",
                  secret.maskedAccountNumber(),
                  "reason",
                  request.reason(),
                  "schemaVersion",
                  1),
              actor.require());

          return new EmployerBankAccountRevealView(
              secret.identityId(),
              secret.versionId(),
              secret.code(),
              secret.ownerKind(),
              secret.legalEntityId(),
              secret.payrollStatutoryUnitId(),
              secret.bankName(),
              secret.branchName(),
              secret.routingCode(),
              secret.accountHolderName(),
              secret.currencyCode(),
              accountNumber,
              secret.effectiveFrom(),
              secret.effectiveTo());
        });
  }

  private EmployerBankAccountView approveTransition(
      UUID identityId,
      UUID versionId,
      long expectedVersion,
      EmployerBankAccountEvidenceRequest request) {
    EmployerBankAccountView before = repository.version(versionId);
    requireIdentity(before.identityId(), identityId);
    LocalDate today = LocalDate.now(clock);
    if (today.isBefore(before.effectiveFrom())
        || (before.effectiveTo() != null
            && !today.isBefore(before.effectiveTo()))) {
      throw new ConflictException(
          "Bank-account version must be effective on the approval date");
    }

    EmployerBankAccountView after =
        repository.approve(
            versionId,
            expectedVersion,
            actor.require(),
            request.evidenceRef(),
            clock.instant());
    audit("ACTIVATED", after, before);
    event("ACTIVATED", after);
    return after;
  }

  private EmployerBankAccountView transition(
      UUID identityId,
      UUID versionId,
      String action,
      Supplier<EmployerBankAccountView> work,
      boolean publishEvent) {
    EmployerBankAccountView before = repository.version(versionId);
    requireIdentity(before.identityId(), identityId);
    EmployerBankAccountView after = work.get();
    audit(action, after, before);
    if (publishEvent) {
      event(action, after);
    }
    return after;
  }

  private void audit(
      String action,
      EmployerBankAccountView after,
      EmployerBankAccountView before) {
    audit.append(
        action,
        "EMPLOYER_BANK_ACCOUNT",
        after.identityId(),
        state(before),
        state(after),
        Map.of(
            "versionId",
            after.versionId(),
            "schemaVersion",
            1),
        actor.require());
  }

  private void event(
      String action,
      EmployerBankAccountView view) {
    Map<String, Object> payload = new LinkedHashMap<>(state(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            "EmployerBankAccountVersion" + action,
            1,
            TenantContext.require(),
            null,
            "EMPLOYER_BANK_ACCOUNT",
            view.identityId(),
            view.versionSequence(),
            payload));
  }

  private Map<String, Object> state(EmployerBankAccountView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("code", view.code());
    state.put("ownerKind", view.ownerKind());
    state.put("legalEntityId", view.legalEntityId());
    state.put(
        "payrollStatutoryUnitId",
        view.payrollStatutoryUnitId());
    state.put("identityStatus", view.identityStatus());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put("bankName", view.bankName());
    state.put("branchName", view.branchName());
    state.put("routingCode", view.routingCode());
    state.put("accountHolderName", view.accountHolderName());
    state.put("currencyCode", view.currencyCode());
    state.put("maskedAccountNumber", view.maskedAccountNumber());
    state.put("defaultAccount", view.defaultAccount());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put(
        "verificationEvidenceRef",
        view.verificationEvidenceRef());
    state.put("verifiedAt", view.verifiedAt());
    state.put("verifiedBy", view.verifiedBy());
    state.put("approvedAt", view.approvedAt());
    state.put("approvedBy", view.approvedBy());
    state.put(
        "approvalEvidenceRef",
        view.approvalEvidenceRef());
    state.put("rejectedAt", view.rejectedAt());
    state.put("rejectedBy", view.rejectedBy());
    state.put("rejectionReason", view.rejectionReason());
    state.put(
        "rejectionEvidenceRef",
        view.rejectionEvidenceRef());
    state.put("suspendedAt", view.suspendedAt());
    state.put("suspendedBy", view.suspendedBy());
    state.put("suspensionReason", view.suspensionReason());
    state.put(
        "supersedesVersionId",
        view.supersedesVersionId());
    return state;
  }

  private EmployerBankAccountView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<EmployerBankAccountView> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required");
    }
    return transactions.write(
        () -> {
          String hash = canonical.hash(request);
          var saved = idempotency.find(operation, key);
          if (saved.isPresent()) {
            if (!saved.get().requestHash().equals(hash)) {
              throw new ConflictException(
                  "Idempotency-Key was already used with a different request");
            }
            if (!saved.get().completed()) {
              throw new ConflictException(
                  "Idempotent operation is still in progress");
            }
            try {
              return objectMapper.readValue(
                  saved.get().body(),
                  EmployerBankAccountView.class);
            } catch (JsonProcessingException exception) {
              throw new IllegalStateException(
                  "Stored idempotent response is invalid",
                  exception);
            }
          }

          try {
            idempotency.reserve(
                operation,
                key,
                hash,
                clock.instant().plus(Duration.ofHours(24)));
          } catch (IllegalStateException exception) {
            throw new ConflictException(
                "Idempotency-Key is already in use",
                exception);
          }

          EmployerBankAccountView response = work.get();
          idempotency.complete(operation, key, 200, response);
          return response;
        });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void validateFilters(
      String ownerKind,
      UUID ownerId,
      String currencyCode) {
    if (ownerKind != null
        && !ownerKind.equals("LEGAL_ENTITY")
        && !ownerKind.equals("PAYROLL_STATUTORY_UNIT")) {
      throw new IllegalArgumentException(
          "ownerKind must be LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT");
    }
    if (ownerId != null && ownerKind == null) {
      throw new IllegalArgumentException(
          "ownerKind is required when ownerId is supplied");
    }
    if (currencyCode != null
        && !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException(
          "currencyCode must be a three-letter uppercase code");
    }
  }

  private UUID ownerId(EmployerBankAccountView view) {
    return "LEGAL_ENTITY".equals(view.ownerKind())
        ? view.legalEntityId()
        : view.payrollStatutoryUnitId();
  }

  private void requireIdentity(UUID actual, UUID expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          "Version does not belong to employer bank-account identity");
    }
  }
}
