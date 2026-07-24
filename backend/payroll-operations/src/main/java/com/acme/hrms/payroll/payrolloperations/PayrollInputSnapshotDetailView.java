package com.acme.hrms.payroll.payrolloperations;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record PayrollInputSnapshotDetailView(
    UUID id,
    UUID cycleId,
    UUID payrollAssignmentVersionId,
    String assignmentNumber,
    UUID payrollRelationshipVersionId,
    String employeeNumber,
    UUID populationResolutionId,
    UUID populationMemberId,
    UUID populationDecisionId,
    UUID employeePayrollProfileId,
    UUID payGroupAssignmentId,
    UUID salaryAssignmentId,
    UUID salaryStructureVersionId,
    short payloadSchemaVersion,
    String snapshotHash,
    JsonNode snapshotPayload,
    Instant sealedAt,
    String sealedBy) {}
