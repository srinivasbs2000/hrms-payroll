package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryRemittanceSummaryView(
    UUID id,
    UUID ledgerBatchId,
    UUID cycleId,
    UUID payPeriodId,
    UUID balanceYearId,
    String jurisdictionCode,
    String authorityCode,
    UUID statutoryRuleId,
    UUID statutoryRuleVersionId,
    String currency,
    BigDecimal batchEmployeeDelta,
    BigDecimal batchEmployerDelta,
    BigDecimal periodEmployeeTotal,
    BigDecimal periodEmployerTotal,
    BigDecimal yearEmployeeTotal,
    BigDecimal yearEmployerTotal,
    BigDecimal remittanceAmount,
    String remittancePosition,
    String summaryHash,
    Instant createdAt) {}
