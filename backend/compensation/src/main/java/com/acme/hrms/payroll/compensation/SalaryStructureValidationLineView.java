package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record SalaryStructureValidationLineView(
    UUID id,
    int lineSequence,
    UUID componentId,
    UUID componentVersionId,
    String componentCode,
    String componentName,
    BigDecimal annualAmount,
    BigDecimal monthlyAmount,
    String classification,
    Map<String, Object> evidence) {}
