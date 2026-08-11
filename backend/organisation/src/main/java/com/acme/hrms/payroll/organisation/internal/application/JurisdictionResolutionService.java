package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.organisation.JurisdictionFindingView;
import com.acme.hrms.payroll.organisation.JurisdictionOverrideView;
import com.acme.hrms.payroll.organisation.JurisdictionOverrideWriteRequest;
import com.acme.hrms.payroll.organisation.JurisdictionResolutionRequest;
import com.acme.hrms.payroll.organisation.JurisdictionResolutionView;
import com.acme.hrms.payroll.organisation.internal.infrastructure.JurisdictionResolutionRepository;
import com.acme.hrms.payroll.organisation.internal.infrastructure.JurisdictionResolutionRepository.JurisdictionFact;
import com.acme.hrms.payroll.organisation.internal.infrastructure.JurisdictionResolutionRepository.OverrideFact;
import com.acme.hrms.payroll.organisation.internal.infrastructure.JurisdictionResolutionRepository.WorkLocationFact;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class JurisdictionResolutionService {
  private final JurisdictionResolutionRepository repository;
  private final OrganisationApprovalAuthorityGate approvalGate;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public JurisdictionResolutionService(
      JurisdictionResolutionRepository repository,
      OrganisationApprovalAuthorityGate approvalGate,
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
    this.approvalGate = approvalGate;
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

  public JurisdictionOverrideView createOverride(
      String key,
      JurisdictionOverrideWriteRequest request) {
    request.validate();
    return idempotent(
        "jurisdiction-override:create",
        key,
        request,
        JurisdictionOverrideView.class,
        () -> {
          JurisdictionOverrideView created =
              repository.createOverride(request, actor.require());
          recordOverride("CREATED", created, null);
          return created;
        });
  }

  public JurisdictionOverrideView approveOverride(
      UUID overrideId,
      String key,
      long expectedVersion) {
    return idempotent(
        "jurisdiction-override:approve:" + overrideId,
        key,
        Map.of("expectedVersion", expectedVersion),
        JurisdictionOverrideView.class,
        () -> {
          JurisdictionOverrideView before =
              repository.override(overrideId);
          approvalGate.requireJurisdictionOverrideApproval(
              before.establishmentVersionId(), before.workLocationVersionId());
          JurisdictionOverrideView approved =
              repository.approveOverride(
                  overrideId,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          recordOverride("APPROVED", approved, before);
          publishOverrideApproved(approved);
          return approved;
        });
  }

  public JurisdictionOverrideView override(UUID overrideId) {
    return transactions.read(
        () -> repository.override(overrideId));
  }

  public JurisdictionResolutionView preview(
      JurisdictionResolutionRequest request) {
    request.validate();
    return transactions.read(() -> decide(request));
  }

  public JurisdictionResolutionView resolve(
      String key,
      JurisdictionResolutionRequest request) {
    request.validate();
    return idempotent(
        "jurisdiction-resolution:record",
        key,
        request,
        JurisdictionResolutionView.class,
        () -> {
          JurisdictionResolutionView preview = decide(request);
          List<String> codes =
              preview.findings().stream()
                  .map(JurisdictionFindingView::code)
                  .toList();
          String findingCodesJson = json(codes);
          UUID evidenceId =
              repository.insertEvidence(
                  preview.asOf(),
                  preview.workLocationVersionId(),
                  preview.establishmentVersionId(),
                  preview.overrideId(),
                  preview.resolvedJurisdictionId(),
                  preview.resolvedJurisdictionVersionId(),
                  preview.resolutionSource(),
                  preview.resolutionStatus(),
                  preview.inputFingerprint(),
                  preview.resultFingerprint(),
                  findingCodesJson,
                  actor.require());

          JurisdictionResolutionView recorded =
              new JurisdictionResolutionView(
                  evidenceId,
                  preview.asOf(),
                  preview.workLocationVersionId(),
                  preview.establishmentVersionId(),
                  preview.overrideId(),
                  preview.resolvedJurisdictionId(),
                  preview.resolvedJurisdictionVersionId(),
                  preview.resolutionSource(),
                  preview.resolutionStatus(),
                  preview.inputFingerprint(),
                  preview.resultFingerprint(),
                  preview.findings());

          audit.append(
              "RESOLUTION_RECORDED",
              "JURISDICTION_RESOLUTION",
              evidenceId,
              null,
              resolutionState(recorded),
              Map.of("schemaVersion", 1),
              actor.require());

          return recorded;
        });
  }

  private JurisdictionResolutionView decide(
      JurisdictionResolutionRequest request) {
    String inputFingerprint =
        canonical.hash(
            Map.of(
                "asOf",
                request.asOf(),
                "workLocationVersionId",
                nullable(request.workLocationVersionId()),
                "establishmentVersionId",
                nullable(request.establishmentVersionId())));

    List<JurisdictionFindingView> findings =
        new ArrayList<>();

    WorkLocationFact workLocation = null;
    if (request.workLocationVersionId() != null) {
      workLocation =
          repository.workLocation(
              request.workLocationVersionId(),
              request.asOf());
      if (workLocation == null) {
        findings.add(
            blocker(
                "WORK_LOCATION_NOT_EFFECTIVE",
                "The supplied work-location version is not approved and effective"));
      }
    }

    UUID establishmentVersionId =
        request.establishmentVersionId();
    if (workLocation != null
        && workLocation.establishmentVersionId() != null) {
      if (establishmentVersionId != null
          && !establishmentVersionId.equals(
              workLocation.establishmentVersionId())) {
        findings.add(
            blocker(
                "ESTABLISHMENT_MISMATCH",
                "The supplied establishment does not match the work location"));
        return result(
            request,
            establishmentVersionId,
            null,
            null,
            null,
            "NONE",
            "CONFLICT",
            inputFingerprint,
            findings);
      }
      establishmentVersionId =
          workLocation.establishmentVersionId();
    }

    List<OverrideFact> overrides =
        repository.activeOverrides(
            request.workLocationVersionId(),
            establishmentVersionId,
            request.asOf());

    if (!overrides.isEmpty()) {
      UUID firstJurisdiction =
          overrides.get(0).jurisdictionId();
      boolean conflict =
          overrides.stream()
              .anyMatch(
                  candidate ->
                      !candidate.jurisdictionId()
                          .equals(firstJurisdiction));
      if (conflict) {
        findings.add(
            blocker(
                "OVERRIDE_CONFLICT",
                "Active explicit overrides resolve to different jurisdictions"));
        return result(
            request,
            establishmentVersionId,
            null,
            null,
            null,
            "NONE",
            "CONFLICT",
            inputFingerprint,
            findings);
      }

      OverrideFact selected = overrides.get(0);
      return result(
          request,
          establishmentVersionId,
          selected.overrideId(),
          selected.jurisdictionId(),
          selected.jurisdictionVersionId(),
          "EXPLICIT_OVERRIDE",
          "RESOLVED",
          inputFingerprint,
          findings);
    }

    List<JurisdictionFact> fallback =
        repository.establishmentFallback(
            establishmentVersionId,
            request.asOf());

    if (fallback.size() > 1) {
      findings.add(
          blocker(
              "ESTABLISHMENT_JURISDICTION_AMBIGUOUS",
              "The establishment resolves to more than one effective jurisdiction"));
      return result(
          request,
          establishmentVersionId,
          null,
          null,
          null,
          "NONE",
          "UNRESOLVED",
          inputFingerprint,
          findings);
    }

    JurisdictionFact fallbackJurisdiction =
        fallback.isEmpty() ? null : fallback.get(0);

    if (workLocation != null
        && fallbackJurisdiction != null
        && !workLocation.jurisdictionId().equals(
            fallbackJurisdiction.jurisdictionId())) {
      findings.add(
          blocker(
              "JURISDICTION_CONFLICT",
              "Work-location and establishment-derived jurisdictions disagree"));
      return result(
          request,
          establishmentVersionId,
          null,
          null,
          null,
          "NONE",
          "CONFLICT",
          inputFingerprint,
          findings);
    }

    if (workLocation != null) {
      return result(
          request,
          establishmentVersionId,
          null,
          workLocation.jurisdictionId(),
          workLocation.jurisdictionVersionId(),
          "WORK_LOCATION",
          "RESOLVED",
          inputFingerprint,
          findings);
    }

    if (fallbackJurisdiction != null) {
      findings.add(
          warning(
              "ESTABLISHMENT_FALLBACK_USED",
              "Jurisdiction was derived from approved establishment lineage"));
      return result(
          request,
          establishmentVersionId,
          null,
          fallbackJurisdiction.jurisdictionId(),
          fallbackJurisdiction.jurisdictionVersionId(),
          "ESTABLISHMENT_FALLBACK",
          "RESOLVED",
          inputFingerprint,
          findings);
    }

    findings.add(
        blocker(
            "JURISDICTION_UNRESOLVED",
            "No approved jurisdiction could be resolved from the supplied context"));
    return result(
        request,
        establishmentVersionId,
        null,
        null,
        null,
        "NONE",
        "UNRESOLVED",
        inputFingerprint,
        findings);
  }

  private JurisdictionResolutionView result(
      JurisdictionResolutionRequest request,
      UUID establishmentVersionId,
      UUID overrideId,
      UUID jurisdictionId,
      UUID jurisdictionVersionId,
      String source,
      String status,
      String inputFingerprint,
      List<JurisdictionFindingView> findings) {
    Map<String, Object> resultState = new LinkedHashMap<>();
    resultState.put("status", status);
    resultState.put("source", source);
    resultState.put("overrideId", overrideId);
    resultState.put("resolvedJurisdictionId", jurisdictionId);
    resultState.put(
        "resolvedJurisdictionVersionId",
        jurisdictionVersionId);
    resultState.put(
        "findingCodes",
        findings.stream()
            .map(JurisdictionFindingView::code)
            .toList());
    String resultFingerprint = canonical.hash(resultState);

    return new JurisdictionResolutionView(
        null,
        request.asOf(),
        request.workLocationVersionId(),
        establishmentVersionId,
        overrideId,
        jurisdictionId,
        jurisdictionVersionId,
        source,
        status,
        inputFingerprint,
        resultFingerprint,
        List.copyOf(findings));
  }

  private void recordOverride(
      String action,
      JurisdictionOverrideView after,
      JurisdictionOverrideView before) {
    audit.append(
        action,
        "JURISDICTION_OVERRIDE",
        after.id(),
        overrideState(before),
        overrideState(after),
        Map.of("schemaVersion", 1),
        actor.require());
  }

  private void publishOverrideApproved(
      JurisdictionOverrideView view) {
    Map<String, Object> payload =
        new LinkedHashMap<>(overrideState(view));
    payload.put("tenantId", TenantContext.require());
    payload.put("actor", actor.require());
    payload.put("schemaVersion", 1);
    outbox.append(
        events.create(
            "JurisdictionOverrideApproved",
            1,
            TenantContext.require(),
            null,
            "JURISDICTION_OVERRIDE",
            view.id(),
            view.versionNo(),
            payload));
  }

  private Map<String, Object> overrideState(
      JurisdictionOverrideView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("versionNo", view.versionNo());
    state.put("targetKind", view.targetKind());
    state.put(
        "workLocationVersionId",
        view.workLocationVersionId());
    state.put(
        "establishmentVersionId",
        view.establishmentVersionId());
    state.put(
        "payrollJurisdictionId",
        view.payrollJurisdictionId());
    state.put(
        "payrollJurisdictionVersionId",
        view.payrollJurisdictionVersionId());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("reason", view.reason());
    state.put("approvalStatus", view.approvalStatus());
    return state;
  }

  private Map<String, Object> resolutionState(
      JurisdictionResolutionView view) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("evidenceId", view.evidenceId());
    state.put("asOf", view.asOf());
    state.put(
        "workLocationVersionId",
        view.workLocationVersionId());
    state.put(
        "establishmentVersionId",
        view.establishmentVersionId());
    state.put("overrideId", view.overrideId());
    state.put(
        "resolvedJurisdictionId",
        view.resolvedJurisdictionId());
    state.put(
        "resolvedJurisdictionVersionId",
        view.resolvedJurisdictionVersionId());
    state.put("resolutionSource", view.resolutionSource());
    state.put("resolutionStatus", view.resolutionStatus());
    state.put("inputFingerprint", view.inputFingerprint());
    state.put("resultFingerprint", view.resultFingerprint());
    state.put(
        "findingCodes",
        view.findings().stream()
            .map(JurisdictionFindingView::code)
            .toList());
    return state;
  }

  private JurisdictionFindingView blocker(
      String code,
      String message) {
    return new JurisdictionFindingView(
        code,
        "BLOCKER",
        message);
  }

  private JurisdictionFindingView warning(
      String code,
      String message) {
    return new JurisdictionFindingView(
        code,
        "WARNING",
        message);
  }

  private Object nullable(Object value) {
    return value == null ? "" : value;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "Jurisdiction evidence is not serializable",
          exception);
    }
  }

  private <T> T idempotent(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<T> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required");
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
                  saved.get().body(),
                  responseType);
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

          T response = work.get();
          idempotency.complete(
              operation,
              key,
              200,
              response);
          return response;
        });
  }
}
