package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayGroupView(
    UUID identityId,
    String code,
    String identityStatus,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    UUID payrollStatutoryUnitVersionId,
    UUID calendarId,
    String currency,
    String prorationMethod,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {}
