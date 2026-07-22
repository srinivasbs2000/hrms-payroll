package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryStructureLineView(
    UUID id,
    UUID componentVersionId,
    String componentCode,
    String componentName,
    String componentType,
    String componentFormulaType,
    int sequenceNo,
    BigDecimal targetAmount,
    BigDecimal targetPercentage,
    String percentageBaseCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo) {}