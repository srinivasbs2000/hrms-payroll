package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public record PayrollBaseVersionWriteRequest(
    @NotBlank String baseCategory,
    @NotBlank String aggregationMethod,
    @Size(max = 1000) String description,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> CATEGORIES =
      Set.of("CALCULATION", "STATUTORY", "TAX", "CTC", "REPORTING");
  private static final Set<String> METHODS =
      Set.of("SUM", "AVERAGE", "MAXIMUM", "MINIMUM", "CUSTOM");

  public void validate() {
    if (!CATEGORIES.contains(baseCategory)) {
      throw new IllegalArgumentException("baseCategory contains an unsupported value");
    }
    if (!METHODS.contains(aggregationMethod)) {
      throw new IllegalArgumentException(
          "aggregationMethod contains an unsupported value");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException("Unknown request field: " + property);
  }

}
