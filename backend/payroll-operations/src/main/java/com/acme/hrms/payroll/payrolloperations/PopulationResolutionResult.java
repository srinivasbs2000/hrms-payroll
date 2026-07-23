package com.acme.hrms.payroll.payrolloperations;

import java.util.UUID;

public record PopulationResolutionResult(
    UUID resolutionId,
    UUID cycleId,
    int attemptNo,
    int includedCount,
    int excludedCount,
    long cycleVersionNo) {}
