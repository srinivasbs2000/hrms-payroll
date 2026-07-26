package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryLedgerBatchView(
    UUID id,
    UUID cycleId,
    UUID payPeriodId,
    UUID evaluationRequestId,
    UUID calculationRequestId,
    String batchKind,
    int attemptNo,
    UUID supersedesBatchId,
    String status,
    Instant postedAt,
    String postedBy,
    Instant completedAt,
    String completedBy,
    Integer entryCount,
    Integer balanceSnapshotCount,
    Integer remittanceSummaryCount,
    @DecimalString BigDecimal employeeDeltaTotal,
    @DecimalString BigDecimal employerDeltaTotal,
    @DecimalString BigDecimal cycleEmployeeTotal,
    @DecimalString BigDecimal cycleEmployerTotal,
    String ledgerSetHash,
    String reconciliationHash,
    long versionNo) {}
