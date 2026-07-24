package com.acme.hrms.payroll.calculation;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayrollResultDetailView(
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
    Instant calculatedAt,
    short resultSchemaVersion,
    String inputSnapshotHash,
    UUID salaryStructureVersionId,
    JsonNode resultPayload,
    List<PayrollComponentResultView> components) {}
