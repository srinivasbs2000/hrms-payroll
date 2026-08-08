package com.acme.hrms.payroll.statutory.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.statutory.RegistrationApprovalRequest;
import com.acme.hrms.payroll.statutory.RegistrationRejectionRequest;
import com.acme.hrms.payroll.statutory.RegistrationSuspensionRequest;
import com.acme.hrms.payroll.statutory.RegistrationVerificationRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationCreateRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationVersionWriteRequest;
import com.acme.hrms.payroll.statutory.StatutoryRegistrationView;
import com.acme.hrms.payroll.statutory.internal.infrastructure.StatutoryRegistrationRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class StatutoryRegistrationService {
  private final StatutoryRegistrationRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public StatutoryRegistrationService(
      StatutoryRegistrationRepository repository,
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

  public StatutoryRegistrationView create(
      String key,
      StatutoryRegistrationCreateRequest request) {
    request.validate();
    return idempotent(
        "statutory-registration:create",
        key,
        request,
        () -> {
          StatutoryRegistrationView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public StatutoryRegistrationView addVersion(
      UUID identityId,
      String key,
      StatutoryRegistrationVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "statutory-registration:version-create:" + identityId,
        key,
        request,
        () -> {
          StatutoryRegistrationView created =
              repository.addVersion(
                  identityId,
                  request,
                  actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public StatutoryRegistrationView submit(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return transition(
        "submit",
        identityId,
        versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () ->
            repository.submit(
                versionId,
                expectedVersion,
                actor.require(),
                clock.instant()),
        "SUBMITTED",
        null);
  }

  public StatutoryRegistrationView verify(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      RegistrationVerificationRequest request) {
    return transition(
        "verify",
        identityId,
        versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "evidenceRef",
            request.evidenceRef()),
        () ->
            repository.verify(
                versionId,
                expectedVersion,
                actor.require(),
                request.evidenceRef().trim(),
                clock.instant()),
        "VERIFIED",
        null);
  }

  public StatutoryRegistrationView requestApproval(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return transition(
        "approval-request",
        identityId,
        versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () ->
            repository.requestApproval(
                versionId,
                expectedVersion,
                actor.require(),
                clock.instant()),
        "APPROVAL_REQUESTED",
        null);
  }

  public StatutoryRegistrationView approve(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      RegistrationApprovalRequest request) {
    return transition(
        "approve",
        identityId,
        versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "evidenceRef",
            request.evidenceRef()),
        () ->
            repository.approve(
                versionId,
                expectedVersion,
                actor.require(),
                request.evidenceRef().trim(),
                clock.instant()),
        "ACTIVATED",
        "RegistrationActivated");
  }

  public StatutoryRegistrationView reject(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      RegistrationRejectionRequest request) {
    return transition(
        "reject",
        identityId,
        versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "reason",
            request.reason(),
            "evidenceRef",
            request.evidenceRef(),
            "authorityReference",
            request.authorityReference()),
        () ->
            repository.reject(
                versionId,
                expectedVersion,
                actor.require(),
                request.reason().trim(),
                request.evidenceRef().trim(),
                request.authorityReference().trim(),
                clock.instant()),
        "REJECTED",
        "RegistrationRejected");
  }

  public StatutoryRegistrationView suspend(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      RegistrationSuspensionRequest request) {
    return transition(
        "suspend",
        identityId,
        versionId,
        key,
        Map.of(
            "expectedVersion",
            expectedVersion,
            "reason",
            request.reason()),
        () -> {
          StatutoryRegistrationView current =
              repository.version(versionId);
          if (actor.require().equals(current.createdBy())) {
            throw new AccessDeniedException(
                "Registration maker cannot manually suspend the same registration version");
          }
          return repository.suspend(
              versionId,
              expectedVersion,
              actor.require(),
              request.reason().trim(),
              clock.instant());
        },
        "SUSPENDED",
        "RegistrationSuspended");
  }

  public List<StatutoryRegistrationView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public StatutoryRegistrationView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId,
            effectiveDate(asOf)));
  }

  public List<StatutoryRegistrationView> history(
      UUID identityId) {
    return transactions.read(
        () -> repository.history(identityId));
  }

  public StatutoryRegistrationView revealIdentifier(
      UUID identityId,
      UUID versionId) {
    return transactions.write(
        () -> {
          StatutoryRegistrationView exact =
              repository.versionExact(versionId);
          requireIdentity(exact.identityId(), identityId);
          audit.append(
              "IDENTIFIER_REVEALED",
              "STATUTORY_REGISTRATION",
              exact.identityId(),
              null,
              null,
              Map.of(
                  "versionId",
                  exact.versionId(),
                  "schemaVersion",
                  1),
              actor.require());
          return exact;
        });
  }

  private StatutoryRegistrationView transition(
      String operationSuffix,
      UUID identityId,
      UUID versionId,
      String key,
      Object request,
      Supplier<StatutoryRegistrationView> work,
      String auditAction,
      String eventType) {
    return idempotent(
        "statutory-registration:"
            + operationSuffix
            + ":"
            + versionId,
        key,
        request,
        () -> {
          StatutoryRegistrationView before =
              repository.version(versionId);
          requireIdentity(before.identityId(), identityId);
          StatutoryRegistrationView after = work.get();
          record(auditAction, after, before);
          if (eventType != null) {
            publish(eventType, after);
          }
          return after;
        });
  }

  private void record(
      String action,
      StatutoryRegistrationView after,
      StatutoryRegistrationView before) {
    audit.append(
        action,
        "STATUTORY_REGISTRATION",
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

  private void publish(
      String eventType,
      StatutoryRegistrationView view) {
    Map<String, Object> payload =
        new LinkedHashMap<>(state(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            eventType,
            1,
            TenantContext.require(),
            null,
            "STATUTORY_REGISTRATION",
            view.identityId(),
            view.versionSequence(),
            payload));
  }

  private Map<String, Object> state(
      StatutoryRegistrationView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("registrationTypeId", view.registrationTypeId());
    state.put("referenceCode", view.referenceCode());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put(
        "registrationTypeVersionId",
        view.registrationTypeVersionId());
    state.put("ownerKind", view.ownerKind());
    state.put("ownerId", view.ownerId());
    state.put(
        "payrollJurisdictionId",
        view.payrollJurisdictionId());
    state.put(
        "payrollJurisdictionVersionId",
        view.payrollJurisdictionVersionId());
    state.put(
        "parentRegistrationVersionId",
        view.parentRegistrationVersionId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("lifecycleStatus", view.lifecycleStatus());
    return state;
  }

  private StatutoryRegistrationView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<StatutoryRegistrationView> work) {
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
                  StatutoryRegistrationView.class);
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
          StatutoryRegistrationView response = work.get();
          idempotency.complete(operation, key, 200, response);
          return response;
        });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(UUID actual, UUID expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          "Version does not belong to statutory-registration identity");
    }
  }
}
