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
    @DecimalString BigDecimal sourceEmployeeTotal,
    @DecimalString BigDecimal sourceEmployerTotal,
    @DecimalString BigDecimal correctionEmployeeTotal,
    @DecimalString BigDecimal correctionEmployerTotal,
    @DecimalString BigDecimal expectedEmployeeTotal,
    @DecimalString BigDecimal expectedEmployerTotal,
    @DecimalString BigDecimal ledgerEmployeeTotal,
    @DecimalString BigDecimal ledgerEmployerTotal,
    @DecimalString BigDecimal employeeVariance,
    @DecimalString BigDecimal employerVariance,
    String status,
    String reconciliationHash,
    Instant createdAt) {}
