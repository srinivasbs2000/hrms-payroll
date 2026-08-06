package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.EligibilityCriterionView;
import com.acme.hrms.payroll.compensation.EligibilityCriterionWriteRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleView.CriterionEvaluationView;
import com.acme.hrms.payroll.compensation.EligibilityRuleView.EvaluationView;
import com.acme.hrms.payroll.compensation.EligibilityRuleCreateRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleVersionWriteRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.EligibilityRuleRepository;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class EligibilityRuleService {
  private static final String OBJECT_TYPE = "ELIGIBILITY_RULE";
  private static final String DISCLAIMER =
      "DESIGN-TIME ELIGIBILITY EVALUATION — "
          + "NOT AN EMPLOYEE ELIGIBILITY DECISION";

  private final EligibilityRuleRepository repository;
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

  public EligibilityRuleService(
      EligibilityRuleRepository repository,
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

  public EligibilityRuleView create(
      String key,
      EligibilityRuleCreateRequest request) {
    request.validate();
    return idempotent(
        "eligibility-rule:create",
        key,
        request,
        () -> {
          EligibilityRuleView created =
              repository.create(request, actor.require());
          record("CREATED", created, null);
          return created;
        });
  }

  public EligibilityRuleView addVersion(
      UUID identityId,
      String key,
      EligibilityRuleVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "eligibility-rule:version-create:" + identityId,
        key,
        request,
        () -> {
          EligibilityRuleView created = repository.addVersion(
              identityId,
              request,
              null,
              actor.require());
          record("VERSION_CREATED", created, null);
          return created;
        });
  }

  public EligibilityRuleView correctFuture(
      UUID identityId,
      UUID versionId,
      String key,
      EligibilityRuleVersionWriteRequest request) {
    request.validate();
    return idempotent(
        "eligibility-rule:version-correct:" + versionId,
        key,
        request,
        () -> {
          EligibilityRuleView previous =
              repository.version(versionId);
          requireIdentity(previous, identityId);
          if (!"DRAFT".equals(previous.approvalStatus())
              || previous.superseded()
              || !previous.effectiveFrom()
                  .isAfter(LocalDate.now(clock))) {
            throw new ConflictException(
                "Only a non-superseded future draft eligibility-rule "
                    + "version can be corrected");
          }
          EligibilityRuleView corrected = repository.addVersion(
              identityId,
              request,
              versionId,
              actor.require());
          record("VERSION_CORRECTED", corrected, previous);
          return corrected;
        });
  }

  public EligibilityRuleView approve(
      UUID identityId,
      UUID versionId,
      String key) {
    return idempotent(
        "eligibility-rule:version-approve:"
            + identityId + ":" + versionId,
        key,
        Map.of("versionId", versionId),
        () -> {
          EligibilityRuleView before =
              repository.version(versionId);
          requireIdentity(before, identityId);
          EligibilityRuleView approved = repository.approve(
              versionId,
              actor.require(),
              clock.instant());
          record("VERSION_APPROVED", approved, before);
          return approved;
        });
  }

  public EligibilityRuleView endDate(
      UUID identityId,
      UUID versionId,
      String key,
      LocalDate effectiveTo,
      long expectedVersion) {
    if (effectiveTo == null) {
      throw new IllegalArgumentException(
          "effectiveTo is required");
    }
    return idempotent(
        "eligibility-rule:version-end-date:" + versionId,
        key,
        Map.of(
            "effectiveTo", effectiveTo,
            "expectedVersion", expectedVersion),
        () -> {
          EligibilityRuleView before =
              repository.version(versionId);
          requireIdentity(before, identityId);
          EligibilityRuleView ended = repository.endDate(
              versionId,
              effectiveTo,
              expectedVersion,
              actor.require(),
              clock.instant());
          record("VERSION_END_DATED", ended, before);
          return ended;
        });
  }

  public EligibilityRuleView retire(
      UUID identityId,
      String key,
      LocalDate effectiveDate,
      long expectedVersion,
      String reason) {
    if (effectiveDate == null) {
      throw new IllegalArgumentException(
          "retirement effective date is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException(
          "retirement reason is required");
    }
    return idempotent(
        "eligibility-rule:retire:" + identityId,
        key,
        Map.of(
            "effectiveDate", effectiveDate,
            "expectedVersion", expectedVersion,
            "reason", reason),
        () -> {
          EligibilityRuleView before =
              repository.latest(identityId);
          EligibilityRuleView retired = repository.retire(
              identityId,
              effectiveDate,
              expectedVersion,
              reason,
              actor.require(),
              clock.instant());
          record("RETIRED", retired, before);
          return retired;
        });
  }

  public List<EligibilityRuleView> list(LocalDate asOf) {
    return transactions.read(
        () -> repository.list(effectiveDate(asOf)));
  }

  public EligibilityRuleView current(
      UUID identityId,
      LocalDate asOf) {
    return transactions.read(
        () -> repository.current(
            identityId,
            effectiveDate(asOf)));
  }

  public List<EligibilityRuleView> history(UUID identityId) {
    return transactions.read(
        () -> repository.history(identityId));
  }

  public EvaluationView evaluate(
      UUID identityId,
      UUID versionId,
      Map<String, JsonNode> facts) {
    return transactions.read(() -> {
      EligibilityRuleView rule = repository.version(versionId);
      requireIdentity(rule, identityId);
      return evaluate(rule, facts);
    });
  }

  public List<AuditReader.AuditEventView> audit(
      UUID identityId) {
    return transactions.read(
        () -> auditReader.forObject(OBJECT_TYPE, identityId));
  }

  private EvaluationView evaluate(
      EligibilityRuleView rule,
      Map<String, JsonNode> suppliedFacts) {
    List<EligibilityCriterionView> criteria =
        rule.criteria().stream()
            .sorted(Comparator.comparingInt(
                EligibilityCriterionView::criterionSequence))
            .toList();

    Set<String> requiredFacts = new TreeSet<>();
    for (EligibilityCriterionView criterion : criteria) {
      requiredFacts.add(criterion.factKey());
    }

    if (suppliedFacts == null || suppliedFacts.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one synthetic eligibility fact is required");
    }

    Map<String, JsonNode> canonicalFacts = new TreeMap<>();
    for (Map.Entry<String, JsonNode> entry
        : suppliedFacts.entrySet()) {
      String key = entry.getKey();
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException(
            "Eligibility fact keys must be non-blank");
      }
      EligibilityCriterionWriteRequest.validateSuppliedFact(
          key,
          entry.getValue());
      canonicalFacts.put(key, entry.getValue().deepCopy());
    }

    if (!requiredFacts.equals(canonicalFacts.keySet())) {
      throw new IllegalArgumentException(
          "Supplied facts must contain exactly the rule fact keys. "
              + "Required=" + requiredFacts
              + "; supplied=" + canonicalFacts.keySet());
    }

    List<CriterionEvaluationView> evaluations =
        new ArrayList<>();
    boolean allMatched = true;
    for (EligibilityCriterionView criterion : criteria) {
      JsonNode actual = canonicalFacts.get(criterion.factKey());
      boolean matched = matches(criterion, actual);
      allMatched = allMatched && matched;
      evaluations.add(new CriterionEvaluationView(
          criterion.criterionSequence(),
          criterion.factKey(),
          criterion.factType(),
          criterion.comparisonOperator(),
          criterion.value().deepCopy(),
          actual.deepCopy(),
          matched));
    }

    String result = allMatched
        ? rule.resultWhenMatched()
        : rule.resultWhenNotMatched();
    String configurationHash = configurationHash(rule);
    String factsHash = canonical.hash(canonicalFacts);

    Map<String, Object> hashInput = new LinkedHashMap<>();
    hashInput.put("versionId", rule.versionId());
    hashInput.put("configurationHash", configurationHash);
    hashInput.put("factsHash", factsHash);
    hashInput.put("matched", allMatched);
    hashInput.put("result", result);
    hashInput.put(
        "criteria",
        evaluations.stream()
            .map(this::evaluationHashState)
            .toList());
    String evaluationHash = canonical.hash(hashInput);

    return new EvaluationView(
        rule.identityId(),
        rule.versionId(),
        result,
        allMatched,
        configurationHash,
        factsHash,
        evaluationHash,
        DISCLAIMER,
        List.copyOf(evaluations));
  }

  private boolean matches(
      EligibilityCriterionView criterion,
      JsonNode actual) {
    String operator = criterion.comparisonOperator();
    JsonNode expected = criterion.value();

    return switch (operator) {
      case "EQ" ->
          compare(criterion.factType(), actual, expected) == 0;
      case "NE" ->
          compare(criterion.factType(), actual, expected) != 0;
      case "IN" -> contains(
          criterion.factType(),
          expected,
          actual);
      case "NOT_IN" -> !contains(
          criterion.factType(),
          expected,
          actual);
      case "GTE" ->
          compare(criterion.factType(), actual, expected) >= 0;
      case "LTE" ->
          compare(criterion.factType(), actual, expected) <= 0;
      default -> throw new IllegalStateException(
          "Unsupported persisted comparison operator: " + operator);
    };
  }

  private boolean contains(
      String factType,
      JsonNode expectedArray,
      JsonNode actual) {
    if (!expectedArray.isArray()) {
      throw new IllegalStateException(
          "Persisted IN/NOT_IN criterion value is not an array");
    }
    for (JsonNode candidate : expectedArray) {
      if (compare(factType, actual, candidate) == 0) {
        return true;
      }
    }
    return false;
  }

  private int compare(
      String factType,
      JsonNode left,
      JsonNode right) {
    return switch (factType) {
      case "TEXT" ->
          left.textValue().compareTo(right.textValue());
      case "NUMBER" -> decimal(left).compareTo(decimal(right));
      case "DATE" -> LocalDate.parse(left.textValue())
          .compareTo(LocalDate.parse(right.textValue()));
      case "UUID" -> UUID.fromString(left.textValue())
          .compareTo(UUID.fromString(right.textValue()));
      default -> throw new IllegalStateException(
          "Unsupported persisted fact type: " + factType);
    };
  }

  private BigDecimal decimal(JsonNode value) {
    return value.decimalValue().stripTrailingZeros();
  }

  private Map<String, Object> evaluationHashState(
      CriterionEvaluationView evaluation) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put(
        "criterionSequence",
        evaluation.criterionSequence());
    state.put("factKey", evaluation.factKey());
    state.put("matched", evaluation.matched());
    return state;
  }

  private void record(
      String action,
      EligibilityRuleView after,
      EligibilityRuleView before) {
    String principal = actor.require();
    Map<String, Object> beforeState = summary(before);
    Map<String, Object> afterState = summary(after);

    audit.append(
        action,
        OBJECT_TYPE,
        after.identityId(),
        beforeState,
        afterState,
        Map.of(
            "versionId", after.versionId(),
            "configurationHash", configurationHash(after)),
        principal);

    var event = events.create(
        "EligibilityRule" + action,
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.identityId(),
        after.versionSequence(),
        afterState);
    outbox.append(event);
  }

  private Map<String, Object> summary(
      EligibilityRuleView view) {
    if (view == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("identityId", view.identityId());
    state.put("versionId", view.versionId());
    state.put("code", view.code());
    state.put("lifecycleStatus", view.lifecycleStatus());
    state.put("versionSequence", view.versionSequence());
    state.put("name", view.name());
    state.put(
        "resultWhenMatched",
        view.resultWhenMatched());
    state.put(
        "resultWhenNotMatched",
        view.resultWhenNotMatched());
    state.put("effectiveFrom", view.effectiveFrom());
    state.put("effectiveTo", view.effectiveTo());
    state.put("approvalStatus", view.approvalStatus());
    state.put("criterionCount", view.criteria().size());
    state.put(
        "criterionShape",
        view.criteria().stream()
            .sorted(Comparator.comparingInt(
                EligibilityCriterionView::criterionSequence))
            .map(this::criterionSummary)
            .toList());
    state.put("configurationHash", configurationHash(view));
    state.put(
        "retirementEffectiveDate",
        view.retirementEffectiveDate());
    return state;
  }

  private Map<String, Object> criterionSummary(
      EligibilityCriterionView criterion) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put(
        "criterionSequence",
        criterion.criterionSequence());
    state.put("factKey", criterion.factKey());
    state.put("factType", criterion.factType());
    state.put(
        "comparisonOperator",
        criterion.comparisonOperator());
    return state;
  }

  private String configurationHash(
      EligibilityRuleView view) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("versionId", view.versionId());
    configuration.put("name", view.name());
    configuration.put(
        "resultWhenMatched",
        view.resultWhenMatched());
    configuration.put(
        "resultWhenNotMatched",
        view.resultWhenNotMatched());
    configuration.put("effectiveFrom", view.effectiveFrom());
    configuration.put("effectiveTo", view.effectiveTo());
    configuration.put(
        "criteria",
        view.criteria().stream()
            .sorted(Comparator.comparingInt(
                EligibilityCriterionView::criterionSequence))
            .map(this::criterionConfiguration)
            .toList());
    return canonical.hash(configuration);
  }

  private Map<String, Object> criterionConfiguration(
      EligibilityCriterionView criterion) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put(
        "criterionSequence",
        criterion.criterionSequence());
    configuration.put("factKey", criterion.factKey());
    configuration.put("factType", criterion.factType());
    configuration.put(
        "comparisonOperator",
        criterion.comparisonOperator());
    configuration.put("value", criterion.value());
    return configuration;
  }

  private EligibilityRuleView idempotent(
      String operation,
      String key,
      Object request,
      Supplier<EligibilityRuleView> work) {
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
              saved.get().body(),
              EligibilityRuleView.class);
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

      EligibilityRuleView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private LocalDate effectiveDate(LocalDate asOf) {
    return asOf == null ? LocalDate.now(clock) : asOf;
  }

  private void requireIdentity(
      EligibilityRuleView version,
      UUID identityId) {
    if (!version.identityId().equals(identityId)) {
      throw new IllegalArgumentException(
          "Version does not belong to eligibility-rule identity");
    }
  }
}
