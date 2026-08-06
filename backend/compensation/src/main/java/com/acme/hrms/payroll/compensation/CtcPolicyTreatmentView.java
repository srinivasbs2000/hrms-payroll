package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CtcPolicyTreatmentView(
    UUID id,
    UUID componentId,
    UUID componentVersionId,
    String componentCode,
    String componentName,
    String componentCategory,
    int treatmentSequence,
    String costView,
    String treatmentType,
    BigDecimal fixedValue,
    BigDecimal targetPercentage,
    UUID payrollBaseId,
    UUID payrollBaseVersionId,
    String payrollBaseCode,
    String payrollBaseName,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    long versionNo) {}
