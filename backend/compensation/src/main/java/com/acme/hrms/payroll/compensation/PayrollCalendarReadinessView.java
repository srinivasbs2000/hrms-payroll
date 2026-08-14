package com.acme.hrms.payroll.compensation;

import java.util.List;
import java.util.UUID;

public record PayrollCalendarReadinessView(
    UUID calendarId,
    String frequency,
    String timezone,
    String lifecycleStatus,
    int milestoneRuleCount,
    int holidayCount,
    int periodCount,
    int incompletePeriodCount,
    boolean generationReady,
    boolean publicationReady,
    List<String> blockers) {}
