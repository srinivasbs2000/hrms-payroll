package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayPeriodOperationalView(
    UUID id,
    UUID calendarId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    String status,
    LocalDate inputCutoffOriginalDate,
    LocalDate inputCutoffAdjustedDate,
    LocalDate calculationOriginalDate,
    LocalDate calculationAdjustedDate,
    LocalDate approvalOriginalDate,
    LocalDate approvalAdjustedDate,
    LocalDate releaseOriginalDate,
    LocalDate releaseAdjustedDate,
    LocalDate paymentOriginalDate,
    LocalDate paymentAdjustedDate) {}
