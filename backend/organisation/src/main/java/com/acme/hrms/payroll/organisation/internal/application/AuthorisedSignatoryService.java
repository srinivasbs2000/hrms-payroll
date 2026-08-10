package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryCreateRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryEvidenceRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryRejectRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatorySuspendRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryVersionWriteRequest;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryView;
import com.acme.hrms.payroll.organisation.AuthorisedSignatoryView.ScopeView;
import com.acme.hrms.payroll.organisation.AuthorityEvaluationRequest;
import com.acme.hrms.payroll.organisation.AuthorityEvaluationView;
import com.acme.hrms.payroll.organisation.internal.infrastructure.AuthorisedSignatoryRepository;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
public class AuthorisedSignatoryService {
  private final AuthorisedSignatoryRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public AuthorisedSignatoryService(
      AuthorisedSignatoryRepository repository,
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

  public AuthorisedSignatoryView create(
      String key,
      AuthorisedSignatoryCreateRequest request) {
    request.validate();
    return idempotent(
        "authorised-signatory:create",
        key,
        request,
        () -> {
          AuthorisedSignatoryView created =
              repository.create(request, actor.require());
          audit("CREATED", created, null);
          return created;
        });
  }

  public AuthorisedSignatoryView addVersion(
      UUID identityId,
      String key,
      AuthorisedSignatoryVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "authorised-signatory:version-create:" + identityId,
        key,
        request,
        () -> {
          AuthorisedSignatoryView created =
              repository.addVersion(identityId, request, actor.require());
          audit("VERSION_CREATED", created, null);
          return created;
        });
  }

  public AuthorisedSignatoryView submit(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "authorised-signatory:submit:" + versionId,
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

  public AuthorisedSignatoryView verify(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      AuthorisedSignatoryEvidenceRequest request) {
    return idempotent(
        "authorised-signatory:verify:" + versionId,
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

  public AuthorisedSignatoryView requestApproval(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "authorised-signatory:request-approval:" + versionId,
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

  public AuthorisedSignatoryView approve(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      AuthorisedSignatoryEvidenceRequest request) {
    return idempotent(
        "authorised-signatory:approve:" + versionId,
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

  public AuthorisedSignatoryView reject(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      AuthorisedSignatoryRejectRequest request) {
    return idempotent(
        "authorised-signatory:reject:" + versionId,
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

  public AuthorisedSignatoryView suspend(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      AuthorisedSignatorySuspendRequest request) {
    return idempotent(
        "authorised-signatory:suspend:" + versionId,
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

  public List<AuthorisedSignatoryView> list(
      String ownerKind,
      UUID ownerId,
      LocalDate asOf) {
    validateFilters(ownerKind, ownerId);
    return transactions.read(
        () ->
            repository.list(effectiveDate(asOf)).stream()
                .filter(view -> ownerKind == null || ownerKind.equals(view.ownerKind()))
                .filter(view -> ownerId == null || ownerId.equals(ownerId(view)))
                .toList());
  }

  public AuthorisedSignatoryView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  public List<AuthorisedSignatoryView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }

  public AuthorityEvaluationView evaluateAuthority(
      AuthorityEvaluationRequest request) {
    request.validate();
    LocalDate date = effectiveDate(request.asOf());
    return transactions.read(
        () -> evaluateWithinTransaction(request, date));
  }

  AuthorityEvaluationView evaluateWithinTransaction(
      AuthorityEvaluationRequest request,
      LocalDate asOf) {
    request.validate();
    List<AuthorisedSignatoryView> ownerCandidates =
        repository.list(asOf).stream()
            .filter(view -> request.ownerKind().equals(view.ownerKind()))
            .filter(view -> request.ownerId().equals(ownerId(view)))
            .toList();

    if (ownerCandidates.isEmpty()) {
      return denied(request, asOf, "NO_ACTIVE_SIGNATORY");
    }

    List<Candidate> purposeCandidates =
        ownerCandidates.stream()
            .flatMap(
                view ->
                    view.scopes().stream()
                        .filter(
                            scope ->
                                request.purposeCode()
                                    .equals(scope.purposeCode()))
                        .map(scope -> new Candidate(view, scope)))
            .toList();

    if (purposeCandidates.isEmpty()) {
      return denied(request, asOf, "PURPOSE_NOT_AUTHORIZED");
    }

    List<Candidate> currencyCandidates =
        purposeCandidates.stream()
            .filter(
                candidate ->
                    currencyMatches(
                        candidate.scope(),
                        request.currencyCode()))
            .toList();

    if (currencyCandidates.isEmpty()) {
      return denied(request, asOf, "CURRENCY_MISMATCH");
    }

    if (request.amount() == null) {
      return currencyCandidates.stream()
          .filter(candidate -> candidate.scope().maximumAmount() == null)
          .findFirst()
          .map(candidate -> allowed(request, asOf, candidate))
          .orElseGet(
              () ->
                  denied(
                      request,
                      asOf,
                      "AMOUNT_REQUIRED_FOR_LIMITED_SCOPE"));
    }

    return currencyCandidates.stream()
        .filter(
            candidate ->
                candidate.scope().maximumAmount() == null
                    || request.amount().compareTo(
                            candidate.scope().maximumAmount())
                        <= 0)
        .findFirst()
        .map(candidate -> allowed(request, asOf, candidate))
        .orElseGet(
            () -> denied(request, asOf, "AMOUNT_EXCEEDS_LIMIT"));
  }

  private boolean currencyMatches(
      ScopeView scope,
      String requestedCurrency) {
    if (scope.currencyCode() == null) {
      return true;
    }
    return requestedCurrency != null
        && scope.currencyCode().equals(requestedCurrency);
  }

  private AuthorityEvaluationView allowed(
      AuthorityEvaluationRequest request,
      LocalDate asOf,
      Candidate candidate) {
    AuthorisedSignatoryView view = candidate.view();
    ScopeView scope = candidate.scope();
    return new AuthorityEvaluationView(
        true,
        "AUTHORIZED",
        request.ownerKind(),
        request.legalEntityId(),
        request.payrollStatutoryUnitId(),
        request.purposeCode(),
        request.currencyCode(),
        request.amount(),
        asOf,
        view.identityId(),
        view.versionId(),
        view.code(),
        view.fullName(),
        scope.currencyCode(),
        scope.maximumAmount());
  }

  private AuthorityEvaluationView denied(
      AuthorityEvaluationRequest request,
      LocalDate asOf,
      String reason) {
    return new AuthorityEvaluationView(
        false,
        reason,
        request.ownerKind(),
        request.legalEntityId(),
        request.payrollStatutoryUnitId(),
        request.purposeCode(),
        request.currencyCode(),
        request.amount(),
        asOf,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private AuthorisedSignatoryView approveTransition(
      UUID identityId,
      UUID versionId,
      long expectedVersion,
      AuthorisedSignatoryEvidenceRequest request) {
    AuthorisedSignatoryView before = repository.version(versionId);
    requireIdentity(before.identityId(), identityId);
    LocalDate today = LocalDate.now(clock);
    if (today.isBefore(before.effectiveFrom())
        || (before.effectiveTo() != null
            && !today.isBefore(before.effectiveTo()))) {
      throw new ConflictException(
          "Signatory version must be effective on the approval date");
    }

    AuthorisedSignatoryView after =
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

  private AuthorisedSignatoryView transition(
      UUID identityId,
      UUID versionId,
      String action,
      Supplier<AuthorisedSignatoryView> work,
      boolean publishEvent) {
    AuthorisedSignatoryView before = repository.version(versionId);
    requireIdentity(before.identityId(), identityId);
    AuthorisedSignatoryView after = work.get();
    audit(action, after, before);
    if (publishEvent) {
      event(action, after);
    }
    return after;
  }

  private void audit(
      String action,
      AuthorisedSignatoryView after,
      AuthorisedSignatoryView before) {
    audit.append(
        action,
        "AUTHORISED_SIGNATORY",
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
      AuthorisedSignatoryView view) {
    Map<String, Object> payload = new LinkedHashMap<>(state(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            "AuthorisedSignatoryVersion" + action,
            1,
            TenantContext.require(),
            null,
            "AUTHORISED_SIGNATORY",
            view.identityId(),
            view.versionSequence(),
            payload));
  }

  private Map<String, Object> state(AuthorisedSignatoryView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("code", view.code());
    state.put("ownerKind", view.ownerKind());
    state.put("legalEntityId", view.legalEntityId());
    state.put("payrollStatutoryUnitId", view.payrollStatutoryUnitId());
    state.put("identityStatus", view.identityStatus());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put("fullName", view.fullName());
    state.put("designation", view.designation());
    state.put("authorityReference", view.authorityReference());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("verificationEvidenceRef", view.verificationEvidenceRef());
    state.put("verifiedAt", view.verifiedAt());
    state.put("verifiedBy", view.verifiedBy());
    state.put("approvedAt", view.approvedAt());
    state.put("approvedBy", view.approvedBy());
    state.put("approvalEvidenceRef", view.approvalEvidenceRef());
    state.put("rejectedAt", view.rejectedAt());
    state.put("rejectedBy", view.rejectedBy());
    state.put("rejectionReason", view.rejectionReason());
    state.put("rejectionEvidenceRef", view.rejectionEvidenceRef());
    state.put("suspendedAt", view.suspendedAt());
    state.put("suspendedBy", view.suspendedBy());
    state.put("suspensionReason", view.suspensionReason());
    state.put("supersedesVersionId", view.supersedesVersionId());
    state.put(
        "scopes",
        view.scopes().stream()
            .map(
                scope ->
                    Map.<String, Object>of(
                        "purposeCode",
                        scope.purposeCode(),
                        "currencyCode",
                        scope.currencyCode() == null ? "*" : scope.currencyCode(),
                        "maximumAmount",
                        scope.maximumAmount() == null
                            ? "UNLIMITED"
                            : scope.maximumAmount()))
            .toList());
    return state;
  }

  private AuthorisedSignatoryView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<AuthorisedSignatoryView> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
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
                  AuthorisedSignatoryView.class);
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

          AuthorisedSignatoryView response = work.get();
          idempotency.complete(operation, key, 200, response);
          return response;
        });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void validateFilters(String ownerKind, UUID ownerId) {
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
  }

  private UUID ownerId(AuthorisedSignatoryView view) {
    return "LEGAL_ENTITY".equals(view.ownerKind())
        ? view.legalEntityId()
        : view.payrollStatutoryUnitId();
  }

  private void requireIdentity(UUID actual, UUID expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          "Version does not belong to authorised-signatory identity");
    }
  }

  private record Candidate(
      AuthorisedSignatoryView view,
      ScopeView scope) {}
}
