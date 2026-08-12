package com.acme.hrms.payroll.compensation;

import java.util.UUID;

public record PayrollCalendarView(
    UUID id,
    UUID calendarSeriesId,
    int calendarVersion,
    UUID supersedesCalendarId,
    String code,
    String name,
    String frequency,
    String timezone) {
  public PayrollCalendarView(
      UUID id, String code, String name, String frequency, String timezone) {
    this(id, id, 1, null, code, name, frequency, timezone);
  }
}
