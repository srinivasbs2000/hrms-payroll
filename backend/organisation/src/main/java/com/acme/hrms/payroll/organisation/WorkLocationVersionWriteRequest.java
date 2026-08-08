package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record WorkLocationVersionWriteRequest(
    @NotBlank @Size(max = 160) String name,
    UUID establishmentVersionId,
    @NotNull UUID payrollJurisdictionId,
    @NotNull UUID payrollJurisdictionVersionId,
    @Size(max = 200) String addressLine1,
    @Size(max = 200) String addressLine2,
    @Size(max = 120) String locality,
    @Size(max = 40) String stateCode,
    @Size(max = 24) String postalCode,
    @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
}
