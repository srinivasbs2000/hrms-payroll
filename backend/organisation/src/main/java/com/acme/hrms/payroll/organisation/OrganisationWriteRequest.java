package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record OrganisationWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 200) String name,
    @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @Pattern(regexp = "^[A-Z0-9]{2,3}$") String stateCode,
    UUID parentVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validateFor(OrganisationKind kind, boolean identityCreation) {
    if (identityCreation && (code == null || code.isBlank())) {
      throw new IllegalArgumentException("code is required when creating an identity");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    if (kind != OrganisationKind.LEGAL_ENTITY && parentVersionId == null) {
      throw new IllegalArgumentException("parentVersionId is required");
    }
    if (kind == OrganisationKind.ESTABLISHMENT && (stateCode == null || stateCode.isBlank())) {
      throw new IllegalArgumentException("stateCode is required for an establishment");
    }
  }
}
