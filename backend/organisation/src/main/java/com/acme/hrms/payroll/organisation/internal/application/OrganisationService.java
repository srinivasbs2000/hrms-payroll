package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.organisation.OrganisationHierarchy;
import com.acme.hrms.payroll.organisation.OrganisationHierarchy.Node;
import com.acme.hrms.payroll.organisation.OrganisationKind;
import com.acme.hrms.payroll.organisation.OrganisationRetirementRequest;
import com.acme.hrms.payroll.organisation.OrganisationView;
import com.acme.hrms.payroll.organisation.OrganisationWriteRequest;
import com.acme.hrms.payroll.organisation.internal.infrastructure.OrganisationRepository;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.CorrelationContext;
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
public class OrganisationService {
  private final OrganisationRepository repository;
  private final OrganisationApprovalAuthorityGate approvalGate;
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

  public OrganisationService(
      OrganisationRepository repository,
      OrganisationApprovalAuthorityGate approvalGate,
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
    this.approvalGate = approvalGate;
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

  public OrganisationView create(
      OrganisationKind kind,
      String key,
      OrganisationWriteRequest request) {
    request.validateFor(kind, true);
    return idempotent(
        "create:" + kind,
        key,
        request,
        () -> {
          OrganisationView created =
              repository.create(kind, request, actor.require());
          record(kind, "CREATED", created, null);
          return created;
        });
  }

  public OrganisationView addVersion(
      OrganisationKind kind,
      UUID identityId,
      String key,
      OrganisationWriteRequest request) {
    request.validateFor(kind, false);
    return idempotent(
        "version-create:" + kind + ":" + identityId,
        key,
        request,
        () -> {
          OrganisationView created =
              repository.addVersion(
                  kind, identityId, request, null, actor.require());
          record(kind, "VERSION_CREATED", created, null);
          return created;
        });
  }

  public OrganisationView correctFuture(
      OrganisationKind kind,
      UUID identityId,
      UUID versionId,
      String key,
      OrganisationWriteRequest request) {
    request.validateFor(kind, false);
    return idempotent(
        "version-correct:" + kind + ":" + versionId,
        key,
        request,
        () -> {
          OrganisationView previous = repository.version(kind, versionId);
          if (!previous.identityId().equals(identityId)
              || !"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft version can be corrected");
          }
          OrganisationView corrected =
              repository.addVersion(
                  kind,
                  identityId,
                  request,
                  versionId,
                  actor.require());
          record(kind, "VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public OrganisationView approve(
      OrganisationKind kind,
      UUID identityId,
      UUID versionId,
      String key) {
    return idempotent(
        "version-approve:" + kind + ":" + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        () -> {
          OrganisationView before = repository.version(kind, versionId);
          requireIdentity(before, identityId);
          approvalGate.requireOrganisationApproval(
              kind, before.identityId(), before.versionId());
          OrganisationView approved =
              repository.approve(
                  kind, versionId, actor.require(), clock.instant());
          record(kind, "VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public OrganisationView endDate(
      OrganisationKind kind,
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    return idempotent(
        "version-end-date:" + kind + ":" + versionId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        () -> {
          OrganisationView before = repository.version(kind, versionId);
          requireIdentity(before, identityId);
          OrganisationView ended =
              repository.endDate(
                  kind,
                  versionId,
                  effectiveTo,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          record(kind, "VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public OrganisationView retire(
      OrganisationKind kind,
      UUID identityId,
      String key,
      OrganisationRetirementRequest request,
      long expectedIdentityVersion) {
    return idempotent(
        "retire:" + kind + ":" + identityId,
        key,
        Map.of(
            "effectiveDate", request.effectiveDate(),
            "reason", request.reason(),
            "expectedIdentityVersion", expectedIdentityVersion),
        () -> {
          OrganisationView before = repository.latest(kind, identityId);
          OrganisationView retired =
              repository.retire(
                  kind,
                  identityId,
                  request.effectiveDate(),
                  expectedIdentityVersion,
                  request.reason(),
                  actor.require(),
                  clock.instant());
          record(kind, "RETIRED", retired, before);
          return retired;
        });
  }

  public List<OrganisationView> list(
      OrganisationKind kind, LocalDate asOf) {
    return transactions.read(
        () -> repository.list(kind, effectiveDate(asOf)));
  }

  public OrganisationView current(
      OrganisationKind kind, UUID identityId, LocalDate asOf) {
    return transactions.read(
        () -> repository.current(kind, identityId, effectiveDate(asOf)));
  }

  public List<OrganisationView> history(
      OrganisationKind kind, UUID identityId) {
    return transactions.read(() -> repository.history(kind, identityId));
  }

  public OrganisationHierarchy hierarchy(LocalDate asOf) {
    LocalDate effective = effectiveDate(asOf);
    return transactions.read(
        () -> {
          List<OrganisationView> legal =
              repository.list(OrganisationKind.LEGAL_ENTITY, effective);
          List<OrganisationView> units =
              repository.list(
                  OrganisationKind.PAYROLL_STATUTORY_UNIT, effective);
          List<OrganisationView> establishments =
              repository.list(
                  OrganisationKind.ESTABLISHMENT, effective);
          List<Node> roots =
              legal.stream()
                  .map(
                      legalEntity ->
                          new Node(
                              legalEntity,
                              units.stream()
                                  .filter(
                                      unit ->
                                          legalEntity
                                              .versionId()
                                              .equals(
                                                  unit.parentVersionId()))
                                  .map(
                                      unit ->
                                          new Node(
                                              unit,
                                              establishments.stream()
                                                  .filter(
                                                      establishment ->
                                                          unit.versionId()
                                                              .equals(
                                                                  establishment
                                                                      .parentVersionId()))
                                                  .map(
                                                      establishment ->
                                                          new Node(
                                                              establishment,
                                                              List.of()))
                                                  .toList()))
                                  .toList()))
                  .toList();
          return new OrganisationHierarchy(effective, roots);
        });
  }

  public List<AuditReader.AuditEventView> audit(
      OrganisationKind kind, UUID objectId) {
    return transactions.read(
        () -> auditReader.forObject(kind.name(), objectId));
  }

  private void record(
      OrganisationKind kind,
      String action,
      OrganisationView after,
      OrganisationView before) {
    String principal = actor.require();
    String eventType = eventType(kind, action);
    Map<String, Object> afterState = state(after);
    audit.append(
        action,
        kind.name(),
        after.identityId(),
        state(before),
        afterState,
        Map.of(
            "versionId", after.versionId(),
            "eventType", eventType,
            "schemaVersion", 1),
        principal);

    Map<String, Object> payload = new LinkedHashMap<>(afterState);
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", principal);
    payload.put("correlationId", CorrelationContext.require());
    payload.put("schemaVersion", 1);

    var event =
        events.create(
            eventType,
            1,
            TenantContext.require(),
            null,
            kind.name(),
            after.identityId(),
            after.versionSequence(),
            payload);
    outbox.append(event);
  }

  private String eventType(OrganisationKind kind, String action) {
    String prefix =
        switch (kind) {
          case LEGAL_ENTITY -> "LegalEntity";
          case PAYROLL_STATUTORY_UNIT -> "PayrollStatutoryUnit";
          case ESTABLISHMENT -> "Establishment";
        };
    String suffix =
        switch (action) {
          case "CREATED" -> "Created";
          case "VERSION_CREATED" -> "VersionCreated";
          case "VERSION_CORRECTED" -> "VersionCorrected";
          case "VERSION_APPROVED" -> "VersionApproved";
          case "VERSION_END_DATED" -> "VersionEndDated";
          case "RETIRED" -> "Retired";
          default ->
              throw new IllegalArgumentException(
                  "Unsupported organisation event action");
        };
    return prefix + suffix;
  }

  private Map<String, Object> state(OrganisationView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("kind", view.kind());
    state.put("identityId", view.identityId());
    state.put("identityVersionNo", view.identityVersionNo());
    state.put("identityStatus", view.identityStatus());
    state.put(
        "retirementEffectiveDate", view.retirementEffectiveDate());
    state.put("retirementReason", view.retirementReason());
    state.put("retiredAt", view.retiredAt());
    state.put("retiredBy", view.retiredBy());
    state.put("versionId", view.versionId());
    state.put("versionSequence", view.versionSequence());
    state.put("versionNo", view.versionNo());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("countryCode", view.countryCode());
    state.put("currency", view.currency());
    state.put("stateCode", view.stateCode());
    state.put("parentVersionId", view.parentVersionId());
    state.put("responsibilityScope", view.responsibilityScope());
    state.put("establishmentType", view.establishmentType());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("supersedesVersionId", view.supersedesVersionId());
    state.put("superseded", view.superseded());
    state.put("createdBy", view.createdBy());
    state.put("approvedBy", view.approvedBy());
    return state;
  }

  private OrganisationView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<OrganisationView> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
    return transactions.write(
        () -> {
          String requestHash = canonical.hash(request);
          var saved = idempotency.find(operation, key);
          if (saved.isPresent()) {
            if (!saved.get().requestHash().equals(requestHash)) {
              throw new ConflictException(
                  "Idempotency-Key was already used with a different request");
            }
            if (!saved.get().completed()) {
              throw new ConflictException(
                  "Idempotent operation is still in progress");
            }
            try {
              return objectMapper.readValue(
                  saved.get().body(), OrganisationView.class);
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
          OrganisationView response = work.get();
          idempotency.complete(operation, key, 200, response);
          return response;
        });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      OrganisationView version, UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to identity");
    }
  }
}
