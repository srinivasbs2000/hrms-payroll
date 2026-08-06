package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryStructureLineView(
    UUID id,
    UUID componentId,
    UUID componentVersionId,
    String componentCode,
    String componentName,
    String componentType,
    String componentFormulaType,
    int sequenceNo,
    short lineSchemaVersion,
    String lineType,
    BigDecimal targetAmount,
    BigDecimal targetPercentage,
    String percentageBaseCode,
    BigDecimal minimumAmount,
    BigDecimal maximumAmount,
    boolean mandatory,
    String overridePolicy,
    int ctcDisplayOrder,
    int payslipDisplayOrder,
    LocalDate effectiveFrom,
    LocalDate effectiveTo) {}
