package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryLedgerEntryView(
    UUID id,
    UUID ledgerBatchId,
    UUID cycleId,
    UUID payPeriodId,
    UUID evaluationRequestId,
    UUID sourceEvaluationRequestId,
    UUID statutoryResultId,
    UUID employeeStatutoryProfileId,
    UUID statutoryRuleId,
    UUID statutoryRuleVersionId,
    UUID balanceYearId,
    String jurisdictionCode,
    String authorityCode,
    int sequenceNo,
    String entryKind,
    UUID sourceEntryId,
    String currency,
    @DecimalString BigDecimal employeeAmountDelta,
    @DecimalString BigDecimal employerAmountDelta,
    String reasonCode,
    String reasonDetail,
    String entryHash,
    Instant createdAt) {}
