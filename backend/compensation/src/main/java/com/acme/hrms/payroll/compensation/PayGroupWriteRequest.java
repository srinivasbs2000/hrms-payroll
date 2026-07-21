package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PayGroupWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 160) String name,
    @NotNull UUID payrollStatutoryUnitVersionId,
    @NotNull UUID calendarId,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @Pattern(regexp = "^[A-Z_]{3,40}$") String prorationMethod,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate(boolean identityCreation) {
    if (identityCreation && (code == null || code.isBlank())) {
      throw new IllegalArgumentException(
          "code is required when creating a pay-group identity");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
    if (currency != null && !"INR".equals(currency)) {
      throw new IllegalArgumentException(
          "Only INR currency is supported in the approved payroll slice");
    }
    if (prorationMethod != null
        && !"CALENDAR_DAYS".equals(prorationMethod)) {
      throw new IllegalArgumentException(
          "Only CALENDAR_DAYS proration is supported");
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }

  public String resolvedProrationMethod() {
    return prorationMethod == null || prorationMethod.isBlank()
        ? "CALENDAR_DAYS"
        : prorationMethod;
  }
}
