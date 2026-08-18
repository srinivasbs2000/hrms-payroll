package com.acme.hrms.payroll.employeepayroll;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompensationChangeView(
    UUID id,
    UUID payrollAssignmentId,
    String eventType,
    LocalDate effectiveDate,
    UUID sourceEventId,
    String reason,
    LocalDate assessmentThrough,
    Instant impactAssessedAt,
    String impactAssessedBy,
    int impactedPeriodCount,
    String approvalStatus,
    Instant approvedAt,
    String approvedBy,
    long versionNo) {}
