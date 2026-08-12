package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record GeneratePeriodsRequest(
    @Min(2020) @Max(2100) Integer year,
    @Min(1) @Max(31) Integer paymentDay,
    LocalDate startDate,
    @Min(1) @Max(1000) Integer periodCount) {

  public GeneratePeriodsRequest(int year, Integer paymentDay) {
    this(year, paymentDay, null, null);
  }

  public void validate() {
    validateFor("MONTHLY");
  }

  public void validateFor(String frequency) {
    if (usesLegacyMonthlyMode()) {
      if (!"MONTHLY".equals(frequency)) {
        throw new IllegalArgumentException(
            "year/paymentDay generation is valid only for MONTHLY calendars");
      }
      if (year < 2020 || year > 2100) {
        throw new IllegalArgumentException("year must be between 2020 and 2100");
      }
      if (resolvedPaymentDay() < 1 || resolvedPaymentDay() > 31) {
        throw new IllegalArgumentException("paymentDay must be between 1 and 31");
      }
      return;
    }

    if (year != null || paymentDay != null || startDate == null || periodCount == null) {
      throw new IllegalArgumentException(
          "use either year/paymentDay or startDate/periodCount generation mode");
    }
    if (periodCount < 1 || periodCount > 1000) {
      throw new IllegalArgumentException("periodCount must be between 1 and 1000");
    }
  }

  public boolean usesLegacyMonthlyMode() {
    return year != null && startDate == null && periodCount == null;
  }

  public int resolvedPaymentDay() {
    return paymentDay == null ? 31 : paymentDay;
  }
}
