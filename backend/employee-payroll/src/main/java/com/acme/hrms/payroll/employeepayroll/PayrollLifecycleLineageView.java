package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollLifecycleLineageView(
    UUID id,
    String eventType,
    String relationshipDecision,
    UUID predecessorRelationshipId,
    UUID successorRelationshipId,
    UUID predecessorAssignmentId,
    UUID successorAssignmentId,
    LocalDate effectiveDate,
    String reason,
    String approvalStatus,
    long versionNo) {}
