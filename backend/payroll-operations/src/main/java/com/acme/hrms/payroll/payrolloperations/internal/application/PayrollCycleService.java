package com.acme.hrms.payroll.payrolloperations.internal.application;

import com.acme.hrms.payroll.payrolloperations.PayrollCycleCreateRequest;
import com.acme.hrms.payroll.payrolloperations.PayrollCycleView;
import com.acme.hrms.payroll.payrolloperations.PopulationDecisionView;
import com.acme.hrms.payroll.payrolloperations.PopulationMemberView;
import com.acme.hrms.payroll.payrolloperations.PopulationResolutionResult;
import com.acme.hrms.payroll.payrolloperations.PopulationResolutionView;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.PayrollOperationsRepository;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PayrollCycleService {
  public static final String OBJECT_TYPE = "PAYROLL_CYCLE";

  private final PayrollOperationsRepository repository;
  private final PayrollOperationsCommandExecutor commands;
  private final PayrollOperationsEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final AuditReader auditReader;
  private final Clock clock;

  public PayrollCycleService(
      PayrollOperationsRepository repository,
      PayrollOperationsCommandExecutor commands,
      PayrollOperationsEventRecorder recorder,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      AuditReader auditReader,
      Clock clock) {
    this.repository = repository;
    this.commands = commands;
    this.recorder = recorder;
    this.transactions = transactions;
    this.actor = actor;
    this.auditReader = auditReader;
    this.clock = clock;
  }

  public PayrollCycleView create(
      String key, PayrollCycleCreateRequest request) {
    return commands.execute(
        "payroll-operations:cycle:create",
        key,
        request,
        PayrollCycleView.class,
        () -> {
          PayrollCycleView created =
              repository.createCycle(request, actor.require(), clock.instant());
          recorder.record(
              "CREATED",
              "PayrollCycleCreated",
              created.id(),
              created.versionNo() + 1,
              null,
              cycleState(created),
              Map.of(
                  "payGroupVersionId", created.payGroupVersionId(),
                  "payPeriodId", created.payPeriodId()));
          return created;
        });
  }

  public PopulationResolutionResult resolvePopulation(
      UUID cycleId, String key, long expectedVersion) {
    Map<String, Object> request = Map.of(
        "cycleId", cycleId,
        "expectedVersion", expectedVersion);
    return commands.execute(
        "payroll-operations:cycle:resolve-population:" + cycleId,
        key,
        request,
        PopulationResolutionResult.class,
        () -> {
          PayrollCycleView before = repository.cycle(cycleId);
          PopulationResolutionResult result =
              repository.resolvePopulation(
                  cycleId,
                  expectedVersion,
                  actor.require(),
                  clock.instant());
          PayrollCycleView after = repository.cycle(cycleId);
          recorder.record(
              "POPULATION_RESOLVED",
              "PayrollPopulationResolved",
              cycleId,
              after.versionNo() + 1,
              cycleState(before),
              cycleState(after),
              Map.of(
                  "resolutionId", result.resolutionId(),
                  "attemptNo", result.attemptNo(),
                  "includedCount", result.includedCount(),
                  "excludedCount", result.excludedCount()));
          return result;
        });
  }

  public PayrollCycleView get(UUID cycleId) {
    return transactions.read(() -> repository.cycle(cycleId));
  }

  public List<PayrollCycleView> list() {
    return transactions.read(repository::cycles);
  }

  public List<PopulationMemberView> population(UUID cycleId) {
    return transactions.read(() -> repository.population(cycleId));
  }

  public List<PopulationResolutionView> populationResolutions(UUID cycleId) {
    return transactions.read(() -> repository.resolutions(cycleId));
  }

  public List<PopulationDecisionView> populationDecisions(
      UUID cycleId, UUID resolutionId) {
    return transactions.read(
        () -> repository.decisions(cycleId, resolutionId));
  }

  public List<AuditReader.AuditEventView> audit(UUID cycleId) {
    return transactions.read(() -> {
      repository.cycle(cycleId);
      return auditReader.forObject(OBJECT_TYPE, cycleId);
    });
  }

  static Map<String, Object> cycleState(PayrollCycleView cycle) {
    if (cycle == null) {
      return null;
    }
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", cycle.id());
    state.put("payGroupVersionId", cycle.payGroupVersionId());
    state.put("payPeriodId", cycle.payPeriodId());
    state.put("cycleType", cycle.cycleType());
    state.put("status", cycle.status());
    state.put(
        "activePopulationResolutionId",
        cycle.activePopulationResolutionId());
    state.put("inputSealedAt", cycle.inputSealedAt());
    state.put("inputSealedBy", cycle.inputSealedBy());
    state.put("inputSnapshotCount", cycle.inputSnapshotCount());
    state.put("inputSnapshotSetHash", cycle.inputSnapshotSetHash());
    state.put("versionNo", cycle.versionNo());
    return state;
  }
}
