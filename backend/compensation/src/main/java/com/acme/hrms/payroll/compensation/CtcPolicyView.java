package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CtcPolicyView(
    UUID identityId,
    String code,
    String lifecycleStatus,
    long identityVersionNo,
    LocalDate retirementEffectiveDate,
    String retirementReason,
    Instant retiredAt,
    String retiredBy,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String currency,
    String annualisationMethod,
    BigDecimal toleranceAmount,
    UUID residualComponentId,
    UUID residualComponentVersionId,
    String residualComponentCode,
    String residualComponentName,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    List<CtcPolicyTreatmentView> treatments) {}
