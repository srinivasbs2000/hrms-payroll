package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalaryStructureView(
    UUID identityId,
    String code,
    String identityStatus,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String currency,
    short structureSchemaVersion,
    String structureType,
    String payFrequency,
    String confidentialityLevel,
    UUID ctcPolicyVersionId,
    UUID eligibilityRuleVersionId,
    String targetType,
    String targetFrequency,
    BigDecimal targetSourceAmount,
    BigDecimal targetAnnualizationFactor,
    String targetExecutionMode,
    UUID inclusivePayrollBaseVersionId,
    UUID exclusivePayrollBaseVersionId,
    BigDecimal targetAnnualAmount,
    BigDecimal toleranceAmount,
    UUID residualComponentVersionId,
    String configurationHash,
    String validationFingerprint,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    List<SalaryStructureLineView> lines) {}
