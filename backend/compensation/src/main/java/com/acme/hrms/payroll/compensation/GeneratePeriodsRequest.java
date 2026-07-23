package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GeneratePeriodsRequest(
    @Min(2020) @Max(2100) int year,
    @Min(1) @Max(31) Integer paymentDay) {

  public void validate() {
    if (year < 2020 || year > 2100) {
      throw new IllegalArgumentException(
          "year must be between 2020 and 2100");
    }
    if (resolvedPaymentDay() < 1
        || resolvedPaymentDay() > 31) {
      throw new IllegalArgumentException(
          "paymentDay must be between 1 and 31");
    }
  }

  public int resolvedPaymentDay() {
    return paymentDay == null ? 31 : paymentDay;
  }
}
