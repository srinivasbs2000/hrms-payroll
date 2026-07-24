package com.acme.hrms.payroll.calculation;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollComponentResultView(
    UUID id,
    String componentCode,
    int sequenceNo,
    String componentType,
    String formulaType,
    Short roundingScale,
    BigDecimal unproratedAmount,
    BigDecimal prorationFactor,
    BigDecimal calculatedAmount,
    String currency,
    UUID componentVersionId,
    UUID salaryStructureLineId,
    UUID salaryStructureVersionId,
    JsonNode componentPayload,
    String componentHash) {}
