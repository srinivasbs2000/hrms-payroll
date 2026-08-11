package com.acme.hrms.payroll.security.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.security.ApprovalAuthorityAssignmentCreateRequest;
import com.acme.hrms.payroll.security.ApprovalAuthorityAssignmentView;
import com.acme.hrms.payroll.security.ApprovalAuthorityStateRequest;
import com.acme.hrms.payroll.security.ApprovalDelegationCreateRequest;
import com.acme.hrms.payroll.security.ApprovalDelegationView;
import com.acme.hrms.payroll.security.ApprovalRole;
import com.acme.hrms.payroll.security.internal.infrastructure.ApprovalAuthorityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ApprovalAuthorityService {
  private final ApprovalAuthorityRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public ApprovalAuthorityService(
      ApprovalAuthorityRepository repository,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
    this.audit = audit;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public ApprovalAuthorityAssignmentView createAssignment(
      String key, ApprovalAuthorityAssignmentCreateRequest request) {
    request.validate();
    return idempotent("approval-authority:create", key, request,
        ApprovalAuthorityAssignmentView.class, 201, () -> {
          String principal = actor.require();
          ApprovalAuthorityAssignmentView created =
              repository.createAssignment(request, principal);
          audit.append("CREATED", "APPLICATION_APPROVAL_AUTHORITY", created.id(),
              null, assignmentState(created), Map.of("schemaVersion", 1), principal);
          return created;
        });
  }

  public List<ApprovalAuthorityAssignmentView> assignments() {
    return transactions.read(repository::assignments);
  }

  public ApprovalAuthorityAssignmentView suspend(
      UUID authorityId, String key, long expectedVersion,
      ApprovalAuthorityStateRequest request) {
    request.validate();
    Map<String, Object> payload = statePayload(expectedVersion, request.reason());
    return idempotent("approval-authority:suspend:" + authorityId, key, payload,
        ApprovalAuthorityAssignmentView.class, 200, () -> {
          String principal = actor.require();
          ApprovalAuthorityAssignmentView before = repository.assignment(authorityId);
          ApprovalAuthorityAssignmentView after = repository.suspend(
              authorityId, expectedVersion, principal, request.reason(), clock.instant());
          audit.append("SUSPENDED", "APPLICATION_APPROVAL_AUTHORITY", authorityId,
              assignmentState(before), assignmentState(after),
              Map.of("schemaVersion", 1), principal);
          return after;
        });
  }

  public ApprovalAuthorityAssignmentView retire(
      UUID authorityId, String key, long expectedVersion,
      ApprovalAuthorityStateRequest request) {
    request.validate();
    Map<String, Object> payload = statePayload(expectedVersion, request.reason());
    return idempotent("approval-authority:retire:" + authorityId, key, payload,
        ApprovalAuthorityAssignmentView.class, 200, () -> {
          String principal = actor.require();
          ApprovalAuthorityAssignmentView before = repository.assignment(authorityId);
          ApprovalAuthorityAssignmentView after = repository.retire(
              authorityId, expectedVersion, principal, request.reason(), clock.instant());
          audit.append("RETIRED", "APPLICATION_APPROVAL_AUTHORITY", authorityId,
              assignmentState(before), assignmentState(after),
              Map.of("schemaVersion", 1), principal);
          return after;
        });
  }

  public ApprovalDelegationView createDelegation(
      String key, ApprovalDelegationCreateRequest request) {
    request.validate();
    String principal = actor.require();
    return idempotent("approval-delegation:create:" + principal, key, request,
        ApprovalDelegationView.class, 201, () -> {
          ApprovalAuthorityAssignmentView source =
              repository.assignment(request.sourceAuthorityId());
          if (!"ACTIVE".equals(source.status())) {
            throw new ConflictException("Source approval authority must be active");
          }
          if (!source.actorId().equals(principal)) {
            throw new AccessDeniedException(
                "Only the source authority holder may delegate authority");
          }
          if (principal.equals(request.delegateActorId())) {
            throw new IllegalArgumentException("Self-delegation is not permitted");
          }
          if (request.effectiveFrom().isBefore(source.effectiveFrom())
              || (source.effectiveTo() != null
                  && request.effectiveTo().isAfter(source.effectiveTo()))) {
            throw new IllegalArgumentException(
                "Delegation period cannot exceed source authority period");
          }
          if (source.approvalRole() == ApprovalRole.FINAL_APPROVER
              && request.delegateActorId().startsWith("service:")) {
            throw new IllegalArgumentException(
                "Service identity cannot receive delegated final-approval authority");
          }
          ApprovalDelegationView created =
              repository.createDelegation(request, principal);
          audit.append("CREATED", "APPLICATION_APPROVAL_DELEGATION", created.id(),
              null, delegationState(created),
              Map.of("schemaVersion", 1, "sourceAuthorityId", source.id()), principal);
          return created;
        });
  }

  public List<ApprovalDelegationView> delegations() {
    return transactions.read(repository::delegations);
  }

  public ApprovalDelegationView revokeDelegation(
      UUID delegationId, String key, long expectedVersion,
      ApprovalAuthorityStateRequest request) {
    request.validate();
    Map<String, Object> payload = statePayload(expectedVersion, request.reason());
    return idempotent("approval-delegation:revoke:" + delegationId, key, payload,
        ApprovalDelegationView.class, 200, () -> {
          String principal = actor.require();
          ApprovalDelegationView before = repository.delegation(delegationId);
          if (!before.delegatorActorId().equals(principal)) {
            throw new AccessDeniedException("Only the delegator may revoke this delegation");
          }
          ApprovalDelegationView after = repository.revoke(
              delegationId, expectedVersion, principal, request.reason(), clock.instant());
          audit.append("REVOKED", "APPLICATION_APPROVAL_DELEGATION", delegationId,
              delegationState(before), delegationState(after),
              Map.of("schemaVersion", 1, "sourceAuthorityId", after.sourceAuthorityId()),
              principal);
          return after;
        });
  }

  private <T> T idempotent(
      String operation, String key, Object request, Class<T> type,
      int status, Supplier<T> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
    return transactions.write(() -> {
      String hash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(hash)) {
          throw new ConflictException(
              "Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException("Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(saved.get().body(), type);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Stored idempotent response is invalid", exception);
        }
      }
      idempotency.reserve(
          operation, key, hash, clock.instant().plus(Duration.ofHours(24)));
      T result = work.get();
      idempotency.complete(operation, key, status, result);
      return result;
    });
  }

  private Map<String, Object> statePayload(long version, String reason) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("expectedVersion", version);
    payload.put("reason", reason);
    return payload;
  }

  private Map<String, Object> assignmentState(ApprovalAuthorityAssignmentView view) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("ownerKind", view.ownerKind());
    state.put("ownerId", view.ownerId());
    state.put("approvalRole", view.approvalRole());
    state.put("domainCode", view.domainCode());
    state.put("actionCode", view.actionCode());
    state.put("actorId", view.actorId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("status", view.status());
    state.put("versionNo", view.versionNo());
    return state;
  }

  private Map<String, Object> delegationState(ApprovalDelegationView view) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("sourceAuthorityId", view.sourceAuthorityId());
    state.put("delegatorActorId", view.delegatorActorId());
    state.put("delegateActorId", view.delegateActorId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("status", view.status());
    state.put("versionNo", view.versionNo());
    return state;
  }
}
