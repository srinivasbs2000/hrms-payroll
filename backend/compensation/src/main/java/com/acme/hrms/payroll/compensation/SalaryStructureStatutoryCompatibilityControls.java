package com.acme.hrms.payroll.compensation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalaryStructureStatutoryCompatibilityControls {
  public static final String DISCLAIMER =
      "DESIGN-TIME STATUTORY COMPATIBILITY — NOT AN OFFICIAL PAYROLL OR LEGAL CALCULATION";

  private SalaryStructureStatutoryCompatibilityControls() {}

  public record RuleVersionOption(
      UUID statutoryRuleId,
      UUID statutoryRuleVersionId,
      int versionSequence,
      String jurisdictionCode,
      String authorityCode,
      String ruleCode,
      String ruleName,
      String ruleCategory,
      String currency,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String constraintKind,
      String periodBasis,
      BigDecimal minimumAmount) {}

  public record BindingRequest(
      UUID statutoryRuleVersionId,
      String bindingPurpose,
      String enforcementLevel,
      UUID componentVersionId) {
    public void validate() {
      if (statutoryRuleVersionId == null) {
        throw new IllegalArgumentException("statutoryRuleVersionId is required");
      }
      if (!List.of("MINIMUM_WAGE", "STATUTORY_RULE").contains(bindingPurpose)) {
        throw new IllegalArgumentException(
            "bindingPurpose must be MINIMUM_WAGE or STATUTORY_RULE");
      }
      if (!List.of("BLOCKING", "ADVISORY").contains(enforcementLevel)) {
        throw new IllegalArgumentException(
            "enforcementLevel must be BLOCKING or ADVISORY");
      }
      if ("MINIMUM_WAGE".equals(bindingPurpose) && componentVersionId == null) {
        throw new IllegalArgumentException(
            "MINIMUM_WAGE binding requires componentVersionId");
      }
      if ("STATUTORY_RULE".equals(bindingPurpose) && componentVersionId != null) {
        throw new IllegalArgumentException(
            "STATUTORY_RULE binding must not carry componentVersionId");
      }
    }
  }

  public record RetireBindingRequest(long expectedVersion) {
    public void validate() {
      if (expectedVersion < 0) {
        throw new IllegalArgumentException("expectedVersion must be non-negative");
      }
    }
  }

  public record BindingView(
      UUID bindingId,
      UUID salaryStructureVersionId,
      UUID statutoryRuleId,
      UUID statutoryRuleVersionId,
      int statutoryRuleVersionSequence,
      String jurisdictionCode,
      String authorityCode,
      String ruleCode,
      String ruleName,
      String ruleCategory,
      String bindingPurpose,
      String enforcementLevel,
      UUID componentVersionId,
      String periodBasis,
      BigDecimal minimumAmount,
      String currency,
      String status,
      long versionNo,
      Instant createdAt,
      String createdBy,
      Instant retiredAt,
      String retiredBy) {}

  public record CompatibilityIssueView(
      UUID issueId,
      UUID bindingId,
      String issueCode,
      String severity,
      UUID statutoryRuleId,
      UUID statutoryRuleVersionId,
      UUID componentVersionId,
      String periodBasis,
      BigDecimal requiredAmount,
      BigDecimal actualAmount,
      String issueDetail) {}

  public record CompatibilityEvaluationView(
      UUID evaluationId,
      UUID validationId,
      UUID salaryStructureVersionId,
      long statutoryBindingRevision,
      String validationStatus,
      int blockingIssueCount,
      int advisoryIssueCount,
      String evidenceHash,
      Instant createdAt,
      String createdBy,
      List<CompatibilityIssueView> issues,
      String disclaimer) {}
}
