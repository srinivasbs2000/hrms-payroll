package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.PayComponentView;
import com.acme.hrms.payroll.compensation.PayComponentWriteRequest;
import com.acme.hrms.payroll.compensation.internal.infrastructure.PayComponentRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditReader;
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
public class PayComponentService {
  private static final String OBJECT_TYPE = "PAY_COMPONENT";

  private final PayComponentRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final AuditReader auditReader;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public PayComponentService(
      PayComponentRepository repository,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      AuditReader auditReader,
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
    this.auditReader = auditReader;
    this.events = events;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public PayComponentView create(
      String key, PayComponentWriteRequest request) {
    request.validate(true);
    return idempotent(
        "pay-component:create",
        key,
        request,
        () -> {
          PayComponentView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public PayComponentView addVersion(
      UUID identityId,
      String key,
      PayComponentWriteRequest request) {
    request.validate(false);
    return idempotent(
        "pay-component:version-create:" + identityId,
        key,
        request,
        () -> {
          PayComponentView created = repository.addVersion(
              identityId, request, null, actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public PayComponentView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      PayComponentWriteRequest request) {
    request.validate(false);
    return idempotent(
        "pay-component:version-correct:" + versionId,
        key,
        request,
        () -> {
          PayComponentView previous = repository.version(versionId);
          requireIdentity(previous, identityId);

          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom()
                  .isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft "
                    + "pay-component version can be corrected");
          }

          PayComponentView corrected = repository.addVersion(
              identityId,
              request,
              versionId,
              actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public PayComponentView approve(
      UUID identityId, UUID versionId, String key) {
    return idempotent(
        "pay-component:version-approve:"
            + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        () -> {
          PayComponentView before = repository.version(versionId);
          requireIdentity(before, identityId);
          PayComponentView approved = repository.approve(
              versionId, actor.require(), clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public PayComponentView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "pay-component:version-end-date:" + versionId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        () -> {
          PayComponentView before = repository.version(versionId);
          requireIdentity(before, identityId);
          PayComponentView ended = repository.endDate(
              versionId,
              effectiveTo,
              expectedVersion,
              actor.require(),
              clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public List<PayComponentView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public PayComponentView current(
      UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId, effectiveDate(asOf)));
  }

  public List<PayComponentView> history(UUID identityId) {
    return transactions.read(
        () -> repository.history(identityId));
  }

  public List<AuditReader.AuditEventView> audit(
      UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(
            OBJECT_TYPE, identityId));
  }

  private void record(
      String action,
      PayComponentView after,
      PayComponentView before) {
    String principal = actor.require();

    audit.append(
        action,
        OBJECT_TYPE,
        after.identityId(),
        state(before),
        state(after),
        Map.of("versionId", after.versionId()),
        principal);

    var event = events.create(
        "PayComponent" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.identityId(),
        after.versionSequence(),
        state(after));

    outbox.append(event);
  }

  private Map<String, Object> state(PayComponentView view) {
    if (view == null) {
      return null;
    }

    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("componentType", view.componentType());
    state.put("formulaType", view.formulaType());
    state.put("formulaExpression", view.formulaExpression());
    state.put("fixedAmount", view.fixedAmount());
    state.put("roundingScale", view.roundingScale());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private PayComponentView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<PayComponentView> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required");
    }

    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);

      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException(
              "Idempotency-Key was already used "
                  + "with a different request");
        }

        if (!saved.get().completed()) {
          throw new ConflictException(
              "Idempotent operation is still in progress");
        }

        try {
          return objectMapper.readValue(
              saved.get().body(), PayComponentView.class);
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
            requestHash,
            clock.instant().plus(Duration.ofHours(24)));
      } catch (IllegalStateException exception) {
        throw new ConflictException(
            "Idempotency-Key is already in use",
            exception);
      }

      PayComponentView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      PayComponentView version, UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to pay-component identity");
    }
  }
}