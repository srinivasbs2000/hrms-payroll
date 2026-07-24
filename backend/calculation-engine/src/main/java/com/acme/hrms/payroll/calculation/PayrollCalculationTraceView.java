package com.acme.hrms.payroll.calculation;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollCalculationTraceView(
    UUID id,
    UUID payrollResultId,
    UUID componentResultId,
    String componentCode,
    int stepNo,
    String stepType,
    JsonNode inputs,
    BigDecimal outputValue,
    String message,
    short traceSchemaVersion,
    UUID inputSnapshotId,
    UUID componentVersionId,
    JsonNode tracePayload,
    String traceHash,
    Instant createdAt) {}
