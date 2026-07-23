package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayComponentView(
    UUID identityId,
    String code,
    String name,
    String componentType,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String formulaType,
    String formulaExpression,
    BigDecimal fixedAmount,
    int roundingScale,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}