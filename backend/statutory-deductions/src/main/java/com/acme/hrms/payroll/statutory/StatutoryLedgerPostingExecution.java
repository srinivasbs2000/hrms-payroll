package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryLedgerPostingExecution(
    UUID cycleId,
    UUID evaluationRequestId,
    UUID ledgerBatchId,
    int attemptNo,
    String batchKind,
    int postedEntryCount,
    BigDecimal employeeDeltaTotal,
    BigDecimal employerDeltaTotal,
    BigDecimal cycleEmployeeTotal,
    BigDecimal cycleEmployerTotal,
    String ledgerSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
