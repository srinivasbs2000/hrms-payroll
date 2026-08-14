package com.acme.hrms.payroll.compensation;

import java.util.UUID;

public record PayrollCalendarMilestoneRuleView(
    UUID id,
    UUID calendarId,
    String milestoneType,
    String anchorType,
    int offsetDays,
    String adjustmentPolicy,
    long versionNo) {}
