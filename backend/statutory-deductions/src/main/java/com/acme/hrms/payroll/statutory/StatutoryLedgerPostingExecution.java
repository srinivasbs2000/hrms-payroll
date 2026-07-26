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
    @DecimalString BigDecimal employeeDeltaTotal,
    @DecimalString BigDecimal employerDeltaTotal,
    @DecimalString BigDecimal cycleEmployeeTotal,
    @DecimalString BigDecimal cycleEmployerTotal,
    String ledgerSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
