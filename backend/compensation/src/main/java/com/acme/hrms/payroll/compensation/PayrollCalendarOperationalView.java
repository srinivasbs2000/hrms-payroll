package com.acme.hrms.payroll.compensation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollCalendarOperationalView(
    UUID id,
    UUID calendarSeriesId,
    int calendarVersion,
    UUID supersedesCalendarId,
    String code,
    String name,
    String frequency,
    String timezone,
    Integer customPeriodDays,
    boolean customFrequencyAuthorised,
    String lifecycleStatus,
    UUID latestLifecycleEventId,
    Instant lifecycleChangedAt,
    String lifecycleChangedBy,
    String lifecycleReason,
    int milestoneRuleCount,
    int holidayCount,
    int periodCount,
    LocalDate firstPeriodStart,
    LocalDate lastPeriodEnd) {}
