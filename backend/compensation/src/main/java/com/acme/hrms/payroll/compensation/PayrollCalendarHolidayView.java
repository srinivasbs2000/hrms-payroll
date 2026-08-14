package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollCalendarHolidayView(
    UUID id,
    UUID calendarId,
    LocalDate holidayDate,
    String holidayName,
    long versionNo) {}
