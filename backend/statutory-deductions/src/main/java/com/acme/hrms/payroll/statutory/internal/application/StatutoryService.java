package com.acme.hrms.payroll.statutory.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.statutory.StatutoryBalanceSnapshotView;
import com.acme.hrms.payroll.statutory.StatutoryCorrectionCommand;
import com.acme.hrms.payroll.statutory.StatutoryCorrectionExecution;
import com.acme.hrms.payroll.statutory.StatutoryEvaluationCommand;
import com.acme.hrms.payroll.statutory.StatutoryEvaluationExecution;
import com.acme.hrms.payroll.statutory.StatutoryEvaluationRequestView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerBatchView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerEntryView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerPostingCommand;
import com.acme.hrms.payroll.statutory.StatutoryLedgerPostingExecution;
import com.acme.hrms.payroll.statutory.StatutoryReconciliationView;
import com.acme.hrms.payroll.statutory.StatutoryRemittanceSummaryView;
import com.acme.hrms.payroll.statutory.StatutoryResultView;
import com.acme.hrms.payroll.statutory.internal.infrastructure.StatutoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StatutoryService {
  private static final String EVALUATION_OBJECT = "STATUTORY_EVALUATION";
  private static final String LEDGER_OBJECT = "STATUTORY_LEDGER_BATCH";

  private final StatutoryRepository repository;
  private final TenantTransactionExecutor transactions;
  private final CanonicalJsonHasher canonical;
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public StatutoryService(
      StatutoryRepository repository,
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

  public StatutoryEvaluationExecution evaluate(
      UUID cycleId,
      String idempotencyKey,
      long expectedVersion,
      StatutoryEvaluationCommand command) {
    requireKey(idempotencyKey);
    if (command == null || command.calculationRequestId() == null) {
      throw new IllegalArgumentException("Calculation request is required");
    }

    return transactions.write(() -> {
      String principal = actor.require();
      Instant at = clock.instant();
      Map<String, Object> input = new LinkedHashMap<>();
      input.put("cycleId", cycleId);
      input.put("calculationRequestId", command.calculationRequestId());
      input.put("expectedVersion", expectedVersion);
      String requestHash = canonical.hash(input);

      StatutoryEvaluationExecution result = repository.evaluate(
          cycleId,
          command.calculationRequestId(),
          expectedVersion,
          idempotencyKey.trim(),
          requestHash,
          principal,
          at);
      emitOnce(
          "StatutoryEvaluated",
          "EVALUATED",
          EVALUATION_OBJECT,
          result.evaluationRequestId(),
          1,
          evaluationState(result),
          principal);
      return result;
    });
  }

  public StatutoryLedgerPostingExecution post(
      UUID cycleId,
      String idempotencyKey,
      long expectedVersion,
      StatutoryLedgerPostingCommand command) {
    requireKey(idempotencyKey);
    if (command == null || command.evaluationRequestId() == null) {
      throw new IllegalArgumentException("Evaluation request is required");
    }

    return transactions.write(() -> {
      String principal = actor.require();
      Instant at = clock.instant();
      Map<String, Object> input = new LinkedHashMap<>();
      input.put("cycleId", cycleId);
      input.put("evaluationRequestId", command.evaluationRequestId());
      input.put("expectedVersion", expectedVersion);
      String requestHash = canonical.hash(input);

      StatutoryLedgerPostingExecution result = repository.post(
          cycleId,
          command.evaluationRequestId(),
          expectedVersion,
          idempotencyKey.trim(),
          requestHash,
          principal,
          at);
      emitOnce(
          "StatutoryLedgerPosted",
          "POSTED",
          LEDGER_OBJECT,
          result.ledgerBatchId(),
          result.attemptNo(),
          postingState(result),
          principal);
      return result;
    });
  }

  public StatutoryCorrectionExecution correct(
      UUID cycleId,
      String idempotencyKey,
      long expectedVersion,
      StatutoryCorrectionCommand command) {
    requireKey(idempotencyKey);
    if (command == null || command.statutoryResultId() == null) {
      throw new IllegalArgumentException("Statutory result is required");
    }
    BigDecimal employeeDelta = command.employeeAmountDelta();
    BigDecimal employerDelta = command.employerAmountDelta();
    if (employeeDelta == null || employerDelta == null
        || (employeeDelta.signum() == 0 && employerDelta.signum() == 0)) {
      throw new IllegalArgumentException(
          "Correction requires at least one non-zero signed delta");
    }
    String reason = command.reason() == null ? "" : command.reason().trim();
    if (reason.length() < 8 || reason.length() > 500) {
      throw new IllegalArgumentException(
          "Correction reason must contain between 8 and 500 characters");
    }

    return transactions.write(() -> {
      String principal = actor.require();
      Instant at = clock.instant();
      Map<String, Object> input = new LinkedHashMap<>();
      input.put("cycleId", cycleId);
      input.put("statutoryResultId", command.statutoryResultId());
      input.put("employeeAmountDelta", employeeDelta);
      input.put("employerAmountDelta", employerDelta);
      input.put("reason", reason);
      input.put("expectedVersion", expectedVersion);
      String requestHash = canonical.hash(input);

      StatutoryCorrectionExecution result = repository.correct(
          cycleId,
          command.statutoryResultId(),
          employeeDelta,
          employerDelta,
          reason,
          expectedVersion,
          idempotencyKey.trim(),
          requestHash,
          principal,
          at);
      emitOnce(
          "StatutoryLedgerCorrected",
          "CORRECTED",
          LEDGER_OBJECT,
          result.ledgerBatchId(),
          result.attemptNo(),
          correctionState(result, reason),
          principal);
      return result;
    });
  }

  public List<StatutoryEvaluationRequestView> evaluations(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.evaluations(cycleId);
    });
  }

  public List<StatutoryResultView> results(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.results(cycleId);
    });
  }

  public List<StatutoryLedgerBatchView> ledgerBatches(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.ledgerBatches(cycleId);
    });
  }

  public List<StatutoryLedgerEntryView> ledgerEntries(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.ledgerEntries(cycleId);
    });
  }

  public List<StatutoryBalanceSnapshotView> balances(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.balances(cycleId);
    });
  }

  public List<StatutoryReconciliationView> reconciliations(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.reconciliations(cycleId);
    });
  }

  public List<StatutoryRemittanceSummaryView> remittances(UUID cycleId) {
    return transactions.read(() -> {
      repository.requireCycle(cycleId);
      return repository.remittances(cycleId);
    });
  }

  private void emitOnce(
      String eventType,
      String action,
      String objectType,
      UUID objectId,
      long sequence,
      Map<String, Object> state,
      String principal) {
    if (repository.eventExists(objectType, objectId, eventType)) {
      return;
    }
    audit.append(
        action,
        objectType,
        objectId,
        null,
        state,
        Map.of("eventType", eventType),
        principal);
    outbox.append(events.create(
        eventType,
        1,
        TenantContext.require(),
        null,
        objectType,
        objectId,
        sequence,
        state));
  }

  private Map<String, Object> evaluationState(
      StatutoryEvaluationExecution result) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("cycleId", result.cycleId());
    state.put("calculationRequestId", result.calculationRequestId());
    state.put("evaluationRequestId", result.evaluationRequestId());
    state.put("payrollResultCount", result.payrollResultCount());
    state.put("statutoryResultCount", result.statutoryResultCount());
    state.put("employeeTotal", result.employeeTotal());
    state.put("employerTotal", result.employerTotal());
    state.put("postStatutoryNetTotal", result.postStatutoryNetTotal());
    state.put("evidenceSetHash", result.evidenceSetHash());
    state.put("cycleVersionNo", result.cycleVersionNo());
    return state;
  }

  private Map<String, Object> postingState(
      StatutoryLedgerPostingExecution result) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("cycleId", result.cycleId());
    state.put("evaluationRequestId", result.evaluationRequestId());
    state.put("ledgerBatchId", result.ledgerBatchId());
    state.put("attemptNo", result.attemptNo());
    state.put("batchKind", result.batchKind());
    state.put("postedEntryCount", result.postedEntryCount());
    state.put("employeeDeltaTotal", result.employeeDeltaTotal());
    state.put("employerDeltaTotal", result.employerDeltaTotal());
    state.put("cycleEmployeeTotal", result.cycleEmployeeTotal());
    state.put("cycleEmployerTotal", result.cycleEmployerTotal());
    state.put("ledgerSetHash", result.ledgerSetHash());
    state.put("cycleVersionNo", result.cycleVersionNo());
    return state;
  }

  private Map<String, Object> correctionState(
      StatutoryCorrectionExecution result, String reason) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("cycleId", result.cycleId());
    state.put("statutoryResultId", result.statutoryResultId());
    state.put("ledgerBatchId", result.ledgerBatchId());
    state.put("attemptNo", result.attemptNo());
    state.put("postedEntryCount", result.postedEntryCount());
    state.put("employeeDeltaTotal", result.employeeDeltaTotal());
    state.put("employerDeltaTotal", result.employerDeltaTotal());
    state.put("cycleEmployeeTotal", result.cycleEmployeeTotal());
    state.put("cycleEmployerTotal", result.cycleEmployerTotal());
    state.put("ledgerSetHash", result.ledgerSetHash());
    state.put("cycleVersionNo", result.cycleVersionNo());
    state.put("reason", reason);
    return state;
  }

  private static void requireKey(String idempotencyKey) {
    if (idempotencyKey == null
        || idempotencyKey.trim().length() < 8
        || idempotencyKey.trim().length() > 120) {
      throw new IllegalArgumentException(
          "Idempotency-Key must contain between 8 and 120 characters");
    }
  }
}
