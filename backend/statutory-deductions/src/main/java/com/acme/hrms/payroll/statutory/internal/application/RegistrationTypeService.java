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
import com.acme.hrms.payroll.statutory.RegistrationTypeCreateRequest;
import com.acme.hrms.payroll.statutory.RegistrationTypeVersionWriteRequest;
import com.acme.hrms.payroll.statutory.RegistrationTypeView;
import com.acme.hrms.payroll.statutory.internal.infrastructure.RegistrationTypeRepository;
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
public class RegistrationTypeService {
  private final RegistrationTypeRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public RegistrationTypeService(
      RegistrationTypeRepository repository,
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

  public RegistrationTypeView create(
      String key,
      RegistrationTypeCreateRequest request) {
    request.version().validate();
    return idempotent(
        "registration-type:create",
        key,
        request,
        () -> {
          RegistrationTypeView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public RegistrationTypeView addVersion(
      UUID identityId,
      String key,
      RegistrationTypeVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "registration-type:version-create:" + identityId,
        key,
        request,
        () -> {
          RegistrationTypeView created =
              repository.addVersion(
                  identityId,
                  request,
                  actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public RegistrationTypeView approve(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion) {
    return idempotent(
        "registration-type:approve:" + versionId,
        key,
        Map.of("expectedVersion", expectedVersion),
        () -> {
          RegistrationTypeView before =
              repository.version(versionId);
          requireIdentity(before.identityId(), identityId);
          RegistrationTypeVersionWriteRequest.validateIdentifierPattern(
              before.identifierPattern());
          if (!RegistrationTypeVersionWriteRequest.IDENTIFIER_PATTERN_DIALECT
              .equals(before.identifierPatternDialect())) {
            throw new IllegalArgumentException(
                "Unsupported registration identifier pattern dialect");
          }
          RegistrationTypeView approved =
              repository.approve(
                  versionId,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record("VERSION_APPROVED", approved, before);
          publish(approved);
          return approved;
        });
  }

  public List<RegistrationTypeView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public RegistrationTypeView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId,
            effectiveDate(asOf)));
  }

  private void record(
      String action,
      RegistrationTypeView after,
      RegistrationTypeView before) {
    audit.append(
        action,
        "STATUTORY_REGISTRATION_TYPE",
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

  private void publish(RegistrationTypeView view) {
    Map<String, Object> payload =
        new LinkedHashMap<>(state(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            "RegistrationTypeVersionApproved",
            1,
            TenantContext.require(),
            null,
            "STATUTORY_REGISTRATION_TYPE",
            view.identityId(),
            view.versionSequence(),
            payload));
  }

  private Map<String, Object> state(
      RegistrationTypeView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("code", view.code());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put("name", view.name());
    state.put("obligationCode", view.obligationCode());
    state.put("authorityCode", view.authorityCode());
    state.put(
        "jurisdictionLevelCode",
        view.jurisdictionLevelCode());
    state.put(
        "identifierPatternDialect",
        view.identifierPatternDialect());
    state.put(
        "identifierCasePolicy",
        view.identifierCasePolicy());
    state.put("parentRequired", view.parentRequired());
    state.put(
        "parentRegistrationTypeId",
        view.parentRegistrationTypeId());
    state.put("ownerKinds", view.ownerKinds());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private RegistrationTypeView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<RegistrationTypeView> work) {
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
                  RegistrationTypeView.class);
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
          RegistrationTypeView response = work.get();
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
          "Version does not belong to registration-type identity");
    }
  }
}
