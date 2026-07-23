package com.acme.hrms.payroll.compensation;

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
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    List<SalaryStructureLineView> lines) {}