package com.acme.hrms.payroll.payrolloperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollCycleView(
    UUID id,
    UUID payGroupVersionId,
    String payGroupCode,
    String payGroupName,
    UUID payPeriodId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    String cycleType,
    String status,
    UUID activePopulationResolutionId,
    Instant inputSealedAt,
    String inputSealedBy,
    Integer inputSnapshotCount,
    String inputSnapshotSetHash,
    BigDecimal controlTotal,
    long versionNo) {}
