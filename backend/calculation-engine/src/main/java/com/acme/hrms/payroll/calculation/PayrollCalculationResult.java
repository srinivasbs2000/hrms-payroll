package com.acme.hrms.payroll.calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollCalculationResult(
    UUID cycleId,
    UUID calculationRequestId,
    int resultCount,
    BigDecimal grossTotal,
    BigDecimal deductionTotal,
    BigDecimal netTotal,
    String resultSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
