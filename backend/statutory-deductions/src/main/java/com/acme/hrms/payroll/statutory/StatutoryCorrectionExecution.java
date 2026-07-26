package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryCorrectionExecution(
    UUID cycleId,
    UUID statutoryResultId,
    UUID ledgerBatchId,
    int attemptNo,
    int postedEntryCount,
    BigDecimal employeeDeltaTotal,
    BigDecimal employerDeltaTotal,
    BigDecimal cycleEmployeeTotal,
    BigDecimal cycleEmployerTotal,
    String ledgerSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
