package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SalaryStructureValidationView(
    UUID validationId,
    UUID identityId,
    UUID versionId,
    UUID ctcPolicyVersionId,
    UUID eligibilityRuleVersionId,
    LocalDate effectiveDate,
    BigDecimal targetAmount,
    String validationStatus,
    String requestHash,
    String configurationHash,
    String resultHash,
    int blockingErrorCount,
    int warningCount,
    Map<String, Object> summary,
    Instant createdAt,
    String createdBy,
    String disclaimer,
    List<SalaryStructureValidationLineView> lines) {}
