package com.acme.hrms.payroll.calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollCalculationRequestView(
    UUID id,
    UUID cycleId,
    String status,
    String calculationKind,
    int attemptNo,
    UUID supersededRequestId,
    String recalculationReason,
    String engineVersion,
    short requestSchemaVersion,
    long expectedCycleVersion,
    String inputSnapshotSetHash,
    Instant requestedAt,
    Instant startedAt,
    Instant completedAt,
    String completedBy,
    Long completedCycleVersion,
    Integer resultCount,
    BigDecimal grossTotal,
    BigDecimal deductionTotal,
    BigDecimal netTotal,
    String resultSetHash,
    long versionNo) {}
