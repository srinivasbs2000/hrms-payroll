package com.acme.hrms.payroll.payrolloperations.internal.application;

import com.acme.hrms.payroll.payrolloperations.PayrollCycleView;
import com.acme.hrms.payroll.payrolloperations.PayrollInputSealResult;
import com.acme.hrms.payroll.payrolloperations.PayrollInputSnapshotDetailView;
import com.acme.hrms.payroll.payrolloperations.PayrollInputSnapshotView;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.PayrollInputSnapshotRepository;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.PayrollOperationsRepository;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PayrollInputSnapshotService {
  private final PayrollInputSnapshotRepository snapshots;
  private final PayrollOperationsRepository cycles;
  private final PayrollOperationsCommandExecutor commands;
  private final PayrollOperationsEventRecorder recorder;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;

  public PayrollInputSnapshotService(
      PayrollInputSnapshotRepository snapshots,
      PayrollOperationsRepository cycles,
      PayrollOperationsCommandExecutor commands,
      PayrollOperationsEventRecorder recorder,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock) {
    this.snapshots = snapshots;
    this.cycles = cycles;
    this.commands = commands;
    this.recorder = recorder;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
  }

  public PayrollInputSealResult seal(
      UUID cycleId, String key, long expectedVersion) {
    Map<String, Object> request = Map.of(
        "cycleId", cycleId,
        "expectedVersion", expectedVersion);
    return commands.execute(
        "payroll-operations:cycle:seal-inputs:" + cycleId,
        key,
        request,
        PayrollInputSealResult.class,
        () -> {
          PayrollCycleView before = cycles.cycle(cycleId);
          Instant sealedAt = clock.instant();
          PayrollInputSealResult result = snapshots.seal(
              cycleId,
              expectedVersion,
              actor.require(),
              sealedAt);
          PayrollCycleView after = cycles.cycle(cycleId);
          recorder.record(
              "INPUTS_SEALED",
              "PayrollInputsSealed",
              cycleId,
              after.versionNo() + 1,
              PayrollCycleService.cycleState(before),
              PayrollCycleService.cycleState(after),
              Map.of(
                  "snapshotCount", result.snapshotCount(),
                  "combinedHash", result.combinedHash()));
          return result;
        });
  }

  public List<PayrollInputSnapshotView> list(UUID cycleId) {
    return transactions.read(() -> {
      cycles.cycle(cycleId);
      return snapshots.list(cycleId);
    });
  }

  public PayrollInputSnapshotDetailView get(
      UUID cycleId, UUID snapshotId) {
    return transactions.read(() -> {
      cycles.cycle(cycleId);
      return snapshots.get(cycleId, snapshotId);
    });
  }
}
