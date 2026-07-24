package com.acme.hrms.payroll.payrolloperations;

import java.time.Instant;
import java.util.UUID;

public record PayrollInputSealResult(
    UUID cycleId,
    int snapshotCount,
    String combinedHash,
    long cycleVersionNo,
    Instant sealedAt) {}
