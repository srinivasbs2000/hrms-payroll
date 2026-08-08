package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollJurisdictionVersionWriteRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$") String levelCode,
    @Positive int levelRank,
    UUID parentJurisdictionId,
    UUID parentJurisdictionVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if ((parentJurisdictionId == null) != (parentJurisdictionVersionId == null)) {
      throw new IllegalArgumentException(
          "Parent jurisdiction identity and version must be supplied together");
    }
    if (levelRank == 1 && parentJurisdictionId != null) {
      throw new IllegalArgumentException("Root jurisdiction cannot have a parent");
    }
    if (levelRank > 1 && parentJurisdictionId == null) {
      throw new IllegalArgumentException("Non-root jurisdiction requires a parent");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
}
