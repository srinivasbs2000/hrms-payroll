package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryEvaluationExecution(
    UUID cycleId,
    UUID calculationRequestId,
    UUID evaluationRequestId,
    int payrollResultCount,
    int statutoryResultCount,
    BigDecimal employeeTotal,
    BigDecimal employerTotal,
    BigDecimal postStatutoryNetTotal,
    String evidenceSetHash,
    long cycleVersionNo,
    Instant completedAt,
    String completedBy) {}
