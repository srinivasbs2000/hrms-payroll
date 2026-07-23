package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayPeriodView(
    UUID id,
    UUID calendarId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    String status) {}
