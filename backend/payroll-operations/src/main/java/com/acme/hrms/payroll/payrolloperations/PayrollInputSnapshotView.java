package com.acme.hrms.payroll.payrolloperations;

import java.time.Instant;
import java.util.UUID;

public record PayrollInputSnapshotView(
    UUID id,
    UUID cycleId,
    UUID payrollAssignmentVersionId,
    String assignmentNumber,
    String employeeNumber,
    UUID populationResolutionId,
    UUID salaryStructureVersionId,
    short payloadSchemaVersion,
    String snapshotHash,
    Instant sealedAt,
    String sealedBy) {}
