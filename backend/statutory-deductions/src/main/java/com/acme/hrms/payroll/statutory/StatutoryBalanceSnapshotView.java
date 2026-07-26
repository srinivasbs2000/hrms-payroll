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
    @DecimalString BigDecimal periodEmployeeAmount,
    @DecimalString BigDecimal periodEmployerAmount,
    @DecimalString BigDecimal cycleEmployeeAmount,
    @DecimalString BigDecimal cycleEmployerAmount,
    @DecimalString BigDecimal yearEmployeeAmount,
    @DecimalString BigDecimal yearEmployerAmount,
    String snapshotHash,
    Instant createdAt) {}
