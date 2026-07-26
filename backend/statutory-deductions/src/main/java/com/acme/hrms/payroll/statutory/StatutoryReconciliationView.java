package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryReconciliationView(
    UUID id,
    UUID ledgerBatchId,
    UUID cycleId,
    UUID evaluationRequestId,
    String currency,
    BigDecimal sourceEmployeeTotal,
    BigDecimal sourceEmployerTotal,
    BigDecimal correctionEmployeeTotal,
    BigDecimal correctionEmployerTotal,
    BigDecimal expectedEmployeeTotal,
    BigDecimal expectedEmployerTotal,
    BigDecimal ledgerEmployeeTotal,
    BigDecimal ledgerEmployerTotal,
    BigDecimal employeeVariance,
    BigDecimal employerVariance,
    String status,
    String reconciliationHash,
    Instant createdAt) {}
