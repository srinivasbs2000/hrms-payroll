package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record StatutoryRegistrationVersionWriteRequest(
    @NotNull UUID registrationTypeId,
    @NotNull UUID registrationTypeVersionId,
    @NotBlank @Size(max = 160) String identifier,
    @NotNull RegistrationOwnerKind ownerKind,
    @NotNull UUID ownerId,
    @NotNull UUID payrollJurisdictionId,
    @NotNull UUID payrollJurisdictionVersionId,
    UUID parentRegistrationId,
    UUID parentRegistrationVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if ((parentRegistrationId == null) != (parentRegistrationVersionId == null)) {
      throw new IllegalArgumentException(
          "Parent registration identity and version must be supplied together");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }
}
