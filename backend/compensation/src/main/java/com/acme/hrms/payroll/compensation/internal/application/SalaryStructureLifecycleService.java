package com.acme.hrms.payroll.compensation.internal.application;
import com.acme.hrms.payroll.compensation.SalaryStructureEventContract;

import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleCommentRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleView;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.RejectionRequest;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureLifecycleRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class SalaryStructureLifecycleService {
  private static final String OBJECT_TYPE = "SALARY_STRUCTURE";

  private final SalaryStructureLifecycleRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public SalaryStructureLifecycleService(
      SalaryStructureLifecycleRepository repository,
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

  public LifecycleView lifecycle(UUID identityId, UUID versionId) {
    return transactions.read(() -> repository.lifecycle(identityId, versionId));
  }

  public LifecycleView submit(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      LifecycleCommentRequest request) {
    request.validate();
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("versionId", versionId);
    command.put("expectedVersion", expectedVersion);
    command.put("comment", request.comment());
    return idempotent(
        "salary-structure:submit:" + versionId,
        key,
        command,
        LifecycleView.class,
        () -> {
          LifecycleView result = repository.submit(
              identityId,
              versionId,
              expectedVersion,
              clean(request.comment()),
              actor.require(),
              clock.instant());
          record("VERSION_SUBMITTED", identityId, result);
          return result;
        });
  }

  public LifecycleView reject(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      RejectionRequest request) {
    request.validate();
    Map<String, Object> command = Map.of(
        "versionId", versionId,
        "expectedVersion", expectedVersion,
        "reason", request.reason().trim());
    return idempotent(
        "salary-structure:reject:" + versionId,
        key,
        command,
        LifecycleView.class,
        () -> {
          LifecycleView result = repository.reject(
              identityId,
              versionId,
              expectedVersion,
              request.reason().trim(),
              actor.require(),
              clock.instant());
          record("VERSION_REJECTED", identityId, result);
          return result;
        });
  }

  public LifecycleView publish(
      UUID identityId,
      UUID versionId,
      String key,
      long expectedVersion,
      LifecycleCommentRequest request) {
    request.validate();
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("versionId", versionId);
    command.put("expectedVersion", expectedVersion);
    command.put("comment", request.comment());
    return idempotent(
        "salary-structure:publish:" + versionId,
        key,
        command,
        LifecycleView.class,
        () -> {
          LifecycleView result = repository.publish(
              identityId,
              versionId,
              expectedVersion,
              clean(request.comment()),
              actor.require(),
              clock.instant());
          record("VERSION_PUBLISHED", identityId, result);
          return result;
        });
  }

  private void record(
      String action,
      UUID identityId,
      LifecycleView result) {
    String principal = actor.require();
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("versionId", result.versionId());
    state.put("versionNo", result.versionNo());
    state.put("workflowStatus", result.workflowStatus());
    state.put("approvalStatus", result.approvalStatus());
    state.put("validationFingerprint", result.validationFingerprint());
    state.put("statutoryBindingRevision", result.statutoryBindingRevision());
    audit.append(
        action,
        OBJECT_TYPE,
        identityId,
        null,
        state,
        Map.of("source", "P5-SSC-01-E04-010"),
        principal);

    String eventType = SalaryStructureEventContract.eventType(action);
    var event = events.create(
        eventType,
        SalaryStructureEventContract.SCHEMA_VERSION,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        identityId,
        result.versionNo(),
        SalaryStructureEventContract.validatePayload(
            eventType,
            state));
    outbox.append(event);
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> type,
      Supplier<T> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException(
              "Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException("Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(saved.get().body(), type);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored idempotent response is invalid", exception);
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
            "Idempotency-Key is already in use", exception);
      }
      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }
}
