package com.acme.hrms.payroll.calculation.internal.application;

import com.acme.hrms.payroll.calculation.PayrollCalculationRequestView;
import com.acme.hrms.payroll.calculation.PayrollCalculationResult;
import com.acme.hrms.payroll.calculation.PayrollCalculationTraceView;
import com.acme.hrms.payroll.calculation.PayrollRecalculationRequest;
import com.acme.hrms.payroll.calculation.PayrollRecalculationResult;
import com.acme.hrms.payroll.calculation.PayrollResultDetailView;
import com.acme.hrms.payroll.calculation.PayrollResultSummaryView;
import com.acme.hrms.payroll.calculation.internal.infrastructure.PayrollCalculationRepository;
import com.acme.hrms.payroll.calculation.internal.infrastructure.PayrollCalculationRepository.CalculationCycleState;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PayrollCalculationService {
  private static final String OBJECT_TYPE = "PAYROLL_CYCLE";

  private final PayrollCalculationRepository repository;
  private final TenantTransactionExecutor transactions;
  private final CanonicalJsonHasher canonical;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public PayrollCalculationService(
      PayrollCalculationRepository repository,
      TenantTransactionExecutor transactions,
      CanonicalJsonHasher canonical,
      AuditWriter audit,
      DomainEventFactory events,
      OutboxWriter outbox,
      AuthenticatedActor actor,
      Clock clock) {
    this.repository = repository;
    this.transactions = transactions;
    this.canonical = canonical;
    this.audit = audit;
    this.events = events;
    this.outbox = outbox;
    this.actor = actor;
    this.clock = clock;
  }

  public PayrollCalculationResult calculate(
      UUID cycleId, String idempotencyKey, long expectedVersion) {
    requireIdempotencyKey(idempotencyKey);

    return transactions.write(() -> {
      CalculationCycleState before = repository.cycle(cycleId);
      String principal = actor.require();
      Instant calculatedAt = clock.instant();
      String requestHash = canonical.hash(Map.of(
          "cycleId", cycleId,
          "expectedVersion", expectedVersion));

      PayrollCalculationResult result = repository.calculate(
          cycleId,
          expectedVersion,
          idempotencyKey,
          requestHash,
          principal,
          calculatedAt);

      CalculationCycleState after = repository.cycle(cycleId);
      if (!Objects.equals(
          before.activeCalculationRequestId(),
          result.calculationRequestId())) {
        recordCalculation(before, after, result, principal);
      }
      return result;
    });
  }

  public PayrollRecalculationResult recalculate(
      UUID cycleId,
      String idempotencyKey,
      long expectedVersion,
      PayrollRecalculationRequest request) {
    requireIdempotencyKey(idempotencyKey);
    if (request == null || request.reason() == null) {
      throw new IllegalArgumentException("Recalculation reason is required");
    }
    String reason = request.reason().trim();
    if (reason.length() < 8 || reason.length() > 500) {
      throw new IllegalArgumentException(
          "Recalculation reason must contain between 8 and 500 characters");
    }

    return transactions.write(() -> {
      CalculationCycleState before = repository.cycle(cycleId);
      String principal = actor.require();
      Instant recalculatedAt = clock.instant();
      Map<String, Object> hashInput = new LinkedHashMap<>();
      hashInput.put("cycleId", cycleId);
      hashInput.put("expectedVersion", expectedVersion);
      hashInput.put("reason", reason);
      String requestHash = canonical.hash(hashInput);

      PayrollRecalculationResult result = repository.recalculate(
          cycleId,
          expectedVersion,
          idempotencyKey,
          requestHash,
          reason,
          principal,
          recalculatedAt);

      CalculationCycleState after = repository.cycle(cycleId);
      if (!Objects.equals(
          before.activeCalculationRequestId(),
          result.calculationRequestId())) {
        recordRecalculation(before, after, result, reason, principal);
      }
      return result;
    });
  }

  public List<PayrollCalculationRequestView> requests(UUID cycleId) {
    return transactions.read(() -> {
      repository.cycle(cycleId);
      return repository.requests(cycleId);
    });
  }

  public List<PayrollResultSummaryView> results(UUID cycleId) {
    return transactions.read(() -> {
      repository.cycle(cycleId);
      return repository.results(cycleId);
    });
  }

  public PayrollResultDetailView result(UUID cycleId, UUID resultId) {
    return transactions.read(() -> {
      repository.cycle(cycleId);
      return repository.result(cycleId, resultId);
    });
  }

  public List<PayrollCalculationTraceView> trace(
      UUID cycleId, UUID resultId) {
    return transactions.read(() -> {
      repository.cycle(cycleId);
      repository.requireResult(cycleId, resultId);
      return repository.trace(cycleId, resultId);
    });
  }

  private void recordCalculation(
      CalculationCycleState before,
      CalculationCycleState after,
      PayrollCalculationResult result,
      String principal) {
    Map<String, Object> metadata = Map.of(
        "calculationRequestId", result.calculationRequestId(),
        "resultCount", result.resultCount(),
        "grossTotal", result.grossTotal(),
        "deductionTotal", result.deductionTotal(),
        "netTotal", result.netTotal(),
        "resultSetHash", result.resultSetHash());

    audit.append(
        "CALCULATED",
        OBJECT_TYPE,
        after.id(),
        cycleState(before),
        cycleState(after),
        metadata,
        principal);

    outbox.append(events.create(
        "PayrollCalculated",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.id(),
        after.versionNo() + 1,
        cycleState(after)));
  }

  private void recordRecalculation(
      CalculationCycleState before,
      CalculationCycleState after,
      PayrollRecalculationResult result,
      String reason,
      String principal) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calculationRequestId", result.calculationRequestId());
    metadata.put("supersededRequestId", result.supersededRequestId());
    metadata.put("attemptNo", result.attemptNo());
    metadata.put("recalculationReason", reason);
    metadata.put("engineVersion", "STARTER_FIXED_V1");
    metadata.put("resultCount", result.resultCount());
    metadata.put("grossTotal", result.grossTotal());
    metadata.put("deductionTotal", result.deductionTotal());
    metadata.put("netTotal", result.netTotal());
    metadata.put("resultSetHash", result.resultSetHash());

    audit.append(
        "RECALCULATED",
        OBJECT_TYPE,
        after.id(),
        cycleState(before),
        cycleState(after),
        metadata,
        principal);

    Map<String, Object> payload = cycleState(after);
    payload.putAll(metadata);
    outbox.append(events.create(
        "PayrollRecalculated",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        after.id(),
        after.versionNo() + 1,
        payload));
  }

  private Map<String, Object> cycleState(CalculationCycleState cycle) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", cycle.id());
    state.put("status", cycle.status());
    state.put(
        "activeCalculationRequestId",
        cycle.activeCalculationRequestId());
    state.put("calculatedAt", cycle.calculatedAt());
    state.put("calculatedBy", cycle.calculatedBy());
    state.put("calculationResultCount", cycle.calculationResultCount());
    state.put(
        "calculationResultSetHash",
        cycle.calculationResultSetHash());
    state.put("grossTotal", cycle.grossTotal());
    state.put("deductionTotal", cycle.deductionTotal());
    state.put("netTotal", cycle.netTotal());
    state.put("controlTotal", cycle.controlTotal());
    state.put("versionNo", cycle.versionNo());
    return state;
  }

  private static void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
  }
}
