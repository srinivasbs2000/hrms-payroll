package com.acme.hrms.payroll.calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollRecalculationResult(
    UUID cycleId,
    UUID calculationRequestId,
    UUID supersededRequestId,
    int attemptNo,
    int resultCount,
    BigDecimal grossTotal,
    BigDecimal deductionTotal,
    BigDecimal netTotal,
    String resultSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
