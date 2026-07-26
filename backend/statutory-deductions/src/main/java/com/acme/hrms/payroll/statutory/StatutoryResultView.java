package com.acme.hrms.payroll.statutory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryResultView(
    UUID id,
    UUID evaluationRequestId,
    UUID payrollResultId,
    UUID statutoryInputSnapshotId,
    UUID employeeStatutoryProfileId,
    UUID employeeStatutoryRuleAssignmentId,
    UUID statutoryRuleId,
    UUID statutoryRuleVersionId,
    String currency,
    BigDecimal employeeAmount,
    BigDecimal employerAmount,
    String resultHash,
    Instant createdAt) {}
