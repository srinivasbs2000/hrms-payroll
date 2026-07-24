package com.acme.hrms.payroll.calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollResultSummaryView(
    UUID id,
    UUID calculationRequestId,
    UUID cycleId,
    UUID payrollAssignmentVersionId,
    String assignmentNumber,
    String employeeNumber,
    UUID inputSnapshotId,
    String resultStatus,
    String currency,
    BigDecimal grossAmount,
    BigDecimal deductionAmount,
    BigDecimal netAmount,
    Integer componentCount,
    String resultHash,
    Instant calculatedAt) {}
