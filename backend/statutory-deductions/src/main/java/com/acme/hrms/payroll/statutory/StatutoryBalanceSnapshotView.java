package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryBalanceSnapshotView(
    UUID id,
    UUID ledgerBatchId,
    UUID cycleId,
    UUID payPeriodId,
    UUID employeeStatutoryProfileId,
    UUID statutoryRuleId,
    UUID statutoryRuleVersionId,
    UUID balanceYearId,
    String jurisdictionCode,
    String authorityCode,
    String currency,
    BigDecimal periodEmployeeAmount,
    BigDecimal periodEmployerAmount,
    BigDecimal cycleEmployeeAmount,
    BigDecimal cycleEmployerAmount,
    BigDecimal yearEmployeeAmount,
    BigDecimal yearEmployerAmount,
    String snapshotHash,
    Instant createdAt) {}
