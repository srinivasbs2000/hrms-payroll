package com.acme.hrms.payroll.payrolloperations;

import java.time.Instant;
import java.util.UUID;

public record PopulationResolutionView(
    UUID id,
    UUID cycleId,
    int attemptNo,
    String status,
    int includedCount,
    int excludedCount,
    Instant resolvedAt,
    String resolvedBy,
    long versionNo) {}
