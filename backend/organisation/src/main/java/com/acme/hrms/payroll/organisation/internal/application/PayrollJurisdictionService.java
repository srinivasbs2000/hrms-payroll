package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionCreateRequest;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionVersionWriteRequest;
import com.acme.hrms.payroll.organisation.PayrollJurisdictionView;
import com.acme.hrms.payroll.organisation.internal.infrastructure.PayrollJurisdictionRepository;
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
public class PayrollJurisdictionService {
  private final PayrollJurisdictionRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public PayrollJurisdictionService(
      PayrollJurisdictionRepository repository,
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

  public PayrollJurisdictionView create(
      String key, PayrollJurisdictionCreateRequest request) {
    request.version().validate();
    return idempotent(
        "payroll-jurisdiction:create",
        key,
        request,
        () -> {
          PayrollJurisdictionView created =
              repository.create(request, actor.require());
          audit("CREATED", created, null);
          return created;
        });
  }

  public PayrollJurisdictionView addVersion(
      UUID identityId,
      String key,
      PayrollJurisdictionVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "payroll-jurisdiction:version-create:" + identityId,
        key,
        request,
        () -> {
          PayrollJurisdictionView created =
              repository.addVersion(identityId, request, actor.require());
          audit("VERSION_CREATED", created, null);
          return created;
        });
  }

  public PayrollJurisdictionView approve(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "payroll-jurisdiction:approve:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () -> {
          PayrollJurisdictionView before = repository.version(versionId);
          requireIdentity(before.identityId(), identityId);
          PayrollJurisdictionView approved =
              repository.approve(
                  versionId, expectedVersion, actor.require(), clock.instant());
          audit("VERSION_APPROVED", approved, before);
          event(approved);
          return approved;
        });
  }

  public List<PayrollJurisdictionView> list(LocalDate asOf) {
    return transactions.read(() -> repository.list(effectiveDate(asOf)));
  }

  public PayrollJurisdictionView current(UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  private void audit(
      String action,
      PayrollJurisdictionView after,
      PayrollJurisdictionView before) {
    audit.append(
        action,
        "PAYROLL_JURISDICTION",
        after.identityId(),
        state(before),
        state(after),
        Map.of("versionId", after.versionId(), "schemaVersion", 1),
        actor.require());
  }

  private void event(PayrollJurisdictionView view) {
    Map<String, Object> payload = new LinkedHashMap<>(state(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            "PayrollJurisdictionVersionApproved",
            1,
            TenantContext.require(),
            null,
            "PAYROLL_JURISDICTION",
            view.identityId(),
            view.versionSequence(),
            payload));
  }

  private Map<String, Object> state(PayrollJurisdictionView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("code", view.code());
    state.put("identityStatus", view.identityStatus());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put("name", view.name());
    state.put("countryCode", view.countryCode());
    state.put("levelCode", view.levelCode());
    state.put("levelRank", view.levelRank());
    state.put("parentJurisdictionId", view.parentJurisdictionId());
    state.put("parentJurisdictionVersionId", view.parentJurisdictionVersionId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private PayrollJurisdictionView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<PayrollJurisdictionView> work) {
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
                  saved.get().body(), PayrollJurisdictionView.class);
            } catch (JsonProcessingException exception) {
              throw new IllegalStateException(
                  "Stored idempotent response is invalid", exception);
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
                "Idempotency-Key is already in use", exception);
          }
          PayrollJurisdictionView response = work.get();
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
          "Version does not belong to jurisdiction identity");
    }
  }
}
