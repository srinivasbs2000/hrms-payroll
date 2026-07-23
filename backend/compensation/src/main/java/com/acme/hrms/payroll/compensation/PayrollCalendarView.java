package com.acme.hrms.payroll.compensation;

import java.util.UUID;

public record PayrollCalendarView(
    UUID id,
    String code,
    String name,
    String frequency,
    String timezone) {}
