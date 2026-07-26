package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryEvaluationRequestView(
    UUID id,
    UUID cycleId,
    UUID calculationRequestId,
    String status,
    String engineVersion,
    long expectedCycleVersion,
    String calculationResultSetHash,
    Instant startedAt,
    Instant completedAt,
    String completedBy,
    Integer payrollResultCount,
    Integer statutoryResultCount,
    BigDecimal employeeTotal,
    BigDecimal employerTotal,
    BigDecimal postStatutoryNetTotal,
    String evidenceSetHash,
    long versionNo) {}
