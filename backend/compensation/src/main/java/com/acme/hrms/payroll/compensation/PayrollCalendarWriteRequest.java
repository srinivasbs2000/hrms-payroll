package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.ZoneId;

public record PayrollCalendarWriteRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$")
    String code,
    @NotBlank
    @Size(max = 160)
    String name,
    String frequency,
    String timezone) {

  public void validate() {
    if (!"MONTHLY".equals(resolvedFrequency())) {
      throw new IllegalArgumentException(
          "Only MONTHLY payroll calendars are supported");
    }

    try {
      ZoneId.of(resolvedTimezone());
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException(
          "timezone must be a valid IANA timezone",
          exception);
    }
  }

  public String resolvedFrequency() {
    return frequency == null || frequency.isBlank()
        ? "MONTHLY"
        : frequency;
  }

  public String resolvedTimezone() {
    return timezone == null || timezone.isBlank()
        ? "Asia/Kolkata"
        : timezone;
  }
}
