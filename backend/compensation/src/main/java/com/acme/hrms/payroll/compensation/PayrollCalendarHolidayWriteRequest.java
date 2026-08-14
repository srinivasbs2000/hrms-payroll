package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PayrollCalendarHolidayWriteRequest(
    @NotNull LocalDate holidayDate,
    @NotBlank @Size(max = 160) String holidayName) {

  public void validate() {
    if (holidayDate == null) {
      throw new IllegalArgumentException("holidayDate is required");
    }
    if (holidayName == null || holidayName.isBlank() || holidayName.length() > 160) {
      throw new IllegalArgumentException("holidayName must contain 1..160 characters");
    }
  }
}
