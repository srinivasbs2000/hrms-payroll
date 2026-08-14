package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PayrollCalendarMilestoneRuleWriteRequest(
    @NotBlank String milestoneType,
    @NotBlank String anchorType,
    @Min(-366) @Max(366) int offsetDays,
    @NotBlank String adjustmentPolicy) {

  public String resolvedMilestoneType() {
    return milestoneType.trim().toUpperCase();
  }

  public String resolvedAnchorType() {
    return anchorType.trim().toUpperCase();
  }

  public String resolvedAdjustmentPolicy() {
    return adjustmentPolicy.trim().toUpperCase();
  }
}
