package com.acme.hrms.payroll.compensation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record PayrollCalendarMilestoneRulesRequest(
    @NotNull @Size(min = 5, max = 5)
    List<@Valid PayrollCalendarMilestoneRuleWriteRequest> rules) {

  private static final Set<String> REQUIRED_MILESTONES = Set.of(
      "INPUT_CUTOFF", "CALCULATION", "APPROVAL", "RELEASE", "PAYMENT");
  private static final Set<String> ANCHORS = Set.of("PERIOD_START", "PERIOD_END");
  private static final Set<String> ADJUSTMENTS = Set.of(
      "NONE", "PREVIOUS_WORKING_DAY", "NEXT_WORKING_DAY");

  public void validate() {
    if (rules == null || rules.size() != REQUIRED_MILESTONES.size()) {
      throw new IllegalArgumentException("exactly five milestone rules are required");
    }
    if (rules.stream().anyMatch(rule -> rule == null
        || rule.milestoneType() == null || rule.milestoneType().isBlank()
        || rule.anchorType() == null || rule.anchorType().isBlank()
        || rule.adjustmentPolicy() == null || rule.adjustmentPolicy().isBlank())) {
      throw new IllegalArgumentException("every milestone-rule field is required");
    }
    Set<String> supplied = rules.stream()
        .map(PayrollCalendarMilestoneRuleWriteRequest::resolvedMilestoneType)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!supplied.equals(REQUIRED_MILESTONES)) {
      throw new IllegalArgumentException(
          "milestone rules must contain each required milestone exactly once");
    }
    for (PayrollCalendarMilestoneRuleWriteRequest rule : rules) {
      if (!ANCHORS.contains(rule.resolvedAnchorType())) {
        throw new IllegalArgumentException("unsupported milestone anchor");
      }
      if (rule.offsetDays() < -366 || rule.offsetDays() > 366) {
        throw new IllegalArgumentException("milestone offset must be between -366 and 366");
      }
      if (!ADJUSTMENTS.contains(rule.resolvedAdjustmentPolicy())) {
        throw new IllegalArgumentException("unsupported milestone adjustment policy");
      }
    }
  }
}
