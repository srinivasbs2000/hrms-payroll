package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.ComponentBaseMembershipView;
import com.acme.hrms.payroll.compensation.ComponentBaseMembershipWriteRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseCreateRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseVersionWriteRequest;
import com.acme.hrms.payroll.compensation.PayrollBaseView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.PayrollBaseRepository;
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
public class PayrollBaseService {
  private static final String OBJECT_TYPE = "PAYROLL_BASE";

  private final PayrollBaseRepository repository;
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

  public PayrollBaseService(
      PayrollBaseRepository repository,
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

  public PayrollBaseView create(String key, PayrollBaseCreateRequest request) {
    request.validate();
    return idempotent(
        "payroll-base:create",
        key,
        request,
        PayrollBaseView.class,
        () -> {
          PayrollBaseView created = repository.create(request, actor.require());
          recordBase("CREATED", created, null);
          return created;
        });
  }

  public PayrollBaseView addVersion(
      UUID identityId,
      String key,
      PayrollBaseVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "payroll-base:version-create:" + identityId,
        key,
        request,
        PayrollBaseView.class,
        () -> {
          PayrollBaseView created =
              repository.addVersion(identityId, request, null, actor.require());
          recordBase("VERSION_CREATED", created, null);
          return created;
        });
  }

  public PayrollBaseView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      PayrollBaseVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "payroll-base:version-correct:" + versionId,
        key,
        request,
        PayrollBaseView.class,
        () -> {
          PayrollBaseView previous = repository.version(versionId);
          requireBaseIdentity(previous.identityId(), identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft payroll-base version can be corrected");
          }
          PayrollBaseView corrected = repository.addVersion(
              identityId, request, versionId, actor.require());
          recordBase("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public PayrollBaseView approve(UUID identityId, UUID versionId, String key) {
    return idempotent(
        "payroll-base:version-approve:" + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        PayrollBaseView.class,
        () -> {
          PayrollBaseView before = repository.version(versionId);
          requireBaseIdentity(before.identityId(), identityId);
          PayrollBaseView approved =
              repository.approve(versionId, actor.require(), clock.instant());
          recordBase("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public PayrollBaseView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "payroll-base:version-end-date:" + versionId,
        key,
        Map.of("effectiveTo", effectiveTo, "expectedVersion", expectedVersion),
        PayrollBaseView.class,
        () -> {
          PayrollBaseView before = repository.version(versionId);
          requireBaseIdentity(before.identityId(), identityId);
          PayrollBaseView ended = repository.endDate(
              versionId,
              effectiveTo,
              expectedVersion,
              actor.require(),
              clock.instant());
          recordBase("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public PayrollBaseView retire(
      UUID identityId,
      String key,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("retirement reason is required");
    }
    return idempotent(
        "payroll-base:retire:" + identityId,
        key,
        Map.of(
            "effectiveDate", effectiveDate,
            "expectedVersion", expectedVersion,
            "reason", reason),
        PayrollBaseView.class,
        () -> {
          PayrollBaseView before = repository.latest(identityId);
          PayrollBaseView retired = repository.retire(
              identityId,
              effectiveDate,
              expectedVersion,
              reason,
              actor.require(),
              clock.instant());
          recordBase("RETIRED", retired, before);
          return retired;
        });
  }

  public ComponentBaseMembershipView createMembership(
      UUID identityId,
      String key,
      ComponentBaseMembershipWriteRequest request) {
    request.validate();
    return idempotent(
        "payroll-base:membership-create:" + identityId,
        key,
        request,
        ComponentBaseMembershipView.class,
        () -> {
          ComponentBaseMembershipView created = repository.addMembership(
              identityId, request, null, actor.require());
          recordMembership("MEMBERSHIP_CREATED", created, null);
          return created;
        });
  }

  public ComponentBaseMembershipView correctMembership(
      UUID identityId,
      UUID membershipId,
      String key,
      ComponentBaseMembershipWriteRequest request) {
    request.validate();
    return idempotent(
        "payroll-base:membership-correct:" + membershipId,
        key,
        request,
        ComponentBaseMembershipView.class,
        () -> {
          ComponentBaseMembershipView previous = repository.membership(membershipId);
          requireBaseIdentity(previous.payrollBaseId(), identityId);
          if (!previous.componentId().equals(request.componentId())) {
            throw new IllegalArgumentException(
                "A membership correction cannot change component identity");
          }
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft membership can be corrected");
          }
          ComponentBaseMembershipView corrected = repository.addMembership(
              identityId, request, membershipId, actor.require());
          recordMembership("MEMBERSHIP_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public ComponentBaseMembershipView approveMembership(
      UUID identityId, UUID membershipId, String key) {
    return idempotent(
        "payroll-base:membership-approve:" + membershipId,
        key,
        Map.of("membershipId", membershipId),
        ComponentBaseMembershipView.class,
        () -> {
          ComponentBaseMembershipView before = repository.membership(membershipId);
          requireBaseIdentity(before.payrollBaseId(), identityId);
          ComponentBaseMembershipView approved = repository.approveMembership(
              membershipId, actor.require(), clock.instant());
          recordMembership("MEMBERSHIP_APPROVED", approved, before);
          return approved;
        });
  }

  public ComponentBaseMembershipView endDateMembership(
      UUID identityId,
      UUID membershipId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "payroll-base:membership-end-date:" + membershipId,
        key,
        Map.of("effectiveTo", effectiveTo, "expectedVersion", expectedVersion),
        ComponentBaseMembershipView.class,
        () -> {
          ComponentBaseMembershipView before = repository.membership(membershipId);
          requireBaseIdentity(before.payrollBaseId(), identityId);
          ComponentBaseMembershipView ended = repository.endDateMembership(
              membershipId,
              effectiveTo,
              expectedVersion,
              actor.require(),
              clock.instant());
          recordMembership("MEMBERSHIP_END_DATED", ended, before);
          return ended;
        });
  }

  public List<PayrollBaseView> list(LocalDate asOf) {
    return transactions.read(() -> repository.list(effectiveDate(asOf)));
  }

  public PayrollBaseView current(UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.current(identityId, effectiveDate(asOf)));
  }

  public List<PayrollBaseView> history(UUID identityId) {
    return transactions.read(() -> repository.history(identityId));
  }

  public List<ComponentBaseMembershipView> memberships(
      UUID identityId, boolean includeHistory, LocalDate asOf) {
    return transactions.read(() -> includeHistory
        ? repository.membershipHistory(identityId)
        : repository.memberships(identityId, effectiveDate(asOf)));
  }

  public List<AuditReader.AuditEventView> audit(UUID identityId) {
    return transactions.read(() -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private void recordBase(
      String action, PayrollBaseView after, PayrollBaseView before) {
    String principal = actor.require();
    audit.append(
        action,
        OBJECT_TYPE,
        after.identityId(),
        baseState(before),
        baseState(after),
        Map.of("versionId", after.versionId()),
        principal);
    outbox.append(events.create(
        "PayrollBase" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.identityId(),
        after.versionSequence(),
        baseState(after)));
  }

  private void recordMembership(
      String action,
      ComponentBaseMembershipView after,
      ComponentBaseMembershipView before) {
    String principal = actor.require();
    audit.append(
        action,
        OBJECT_TYPE,
        after.payrollBaseId(),
        membershipState(before),
        membershipState(after),
        Map.of("membershipId", after.membershipId()),
        principal);
    outbox.append(events.create(
        "PayrollBase" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.payrollBaseId(),
        after.membershipSequence(),
        membershipState(after)));
  }

  private Map<String, Object> baseState(PayrollBaseView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("ownershipScope", view.ownershipScope());
    state.put("countryCode", view.countryCode());
    state.put("protectedFlag", view.protectedFlag());
    state.put("confidentialityLevel", view.confidentialityLevel());
    state.put("baseCategory", view.baseCategory());
    state.put("aggregationMethod", view.aggregationMethod());
    state.put("description", view.description());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("retirementEffectiveDate", view.retirementEffectiveDate());
    state.put("retirementReason", view.retirementReason());
    return state;
  }

  private Map<String, Object> membershipState(ComponentBaseMembershipView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("membershipId", view.membershipId());
    state.put("payrollBaseId", view.payrollBaseId());
    state.put("payrollBaseVersionId", view.payrollBaseVersionId());
    state.put("componentId", view.componentId());
    state.put("componentVersionId", view.componentVersionId());
    state.put("membershipType", view.membershipType());
    state.put("inclusionPercent", view.inclusionPercent().toPlainString());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
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
          return objectMapper.readValue(saved.get().body(), responseType);
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
        throw new ConflictException("Idempotency-Key is already in use", exception);
      }
      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireBaseIdentity(UUID actual, UUID expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          "Resource does not belong to payroll-base identity");
    }
  }
}
