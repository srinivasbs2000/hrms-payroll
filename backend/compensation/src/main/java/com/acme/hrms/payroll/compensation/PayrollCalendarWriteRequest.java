package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record PayrollCalendarWriteRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 160) String name,
    String frequency,
    String timezone,
    Integer customPeriodDays,
    Boolean customFrequencyAuthorised,
    List<Integer> weekendIsoDays) {

  private static final Set<String> FREQUENCIES = Set.of(
      "MONTHLY", "FORTNIGHTLY", "WEEKLY", "DAILY", "CUSTOM");

  public PayrollCalendarWriteRequest(
      String code, String name, String frequency, String timezone) {
    this(code, name, frequency, timezone, null, false, null);
  }

  public void validate() {
    String resolvedFrequency = resolvedFrequency();
    if (!FREQUENCIES.contains(resolvedFrequency)) {
      throw new IllegalArgumentException("unsupported payroll frequency");
    }

    if ("CUSTOM".equals(resolvedFrequency)) {
      if (!Boolean.TRUE.equals(customFrequencyAuthorised)
          || customPeriodDays == null
          || customPeriodDays < 1
          || customPeriodDays > 366) {
        throw new IllegalArgumentException(
            "CUSTOM frequency requires explicit authorisation and 1..366 period days");
      }
    } else if (Boolean.TRUE.equals(customFrequencyAuthorised)
        || customPeriodDays != null) {
      throw new IllegalArgumentException(
          "custom frequency policy is valid only for CUSTOM calendars");
    }

    try {
      ZoneId.of(resolvedTimezone());
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException(
          "timezone must be a valid IANA timezone", exception);
    }

    List<Integer> weekends = resolvedWeekendIsoDays();
    if (weekends.size() > 7
        || weekends.stream().anyMatch(day -> day == null || day < 1 || day > 7)
        || new LinkedHashSet<>(weekends).size() != weekends.size()) {
      throw new IllegalArgumentException(
          "weekendIsoDays must contain unique ISO day numbers 1..7");
    }
  }

  public String resolvedFrequency() {
    return frequency == null || frequency.isBlank()
        ? "MONTHLY"
        : frequency.trim().toUpperCase();
  }

  public String resolvedTimezone() {
    return timezone == null || timezone.isBlank()
        ? "Asia/Kolkata"
        : timezone;
  }

  public boolean resolvedCustomFrequencyAuthorised() {
    return Boolean.TRUE.equals(customFrequencyAuthorised);
  }

  public List<Integer> resolvedWeekendIsoDays() {
    return weekendIsoDays == null ? List.of(6, 7) : List.copyOf(weekendIsoDays);
  }
}
