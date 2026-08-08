package com.acme.hrms.payroll.statutory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record StatutoryRegistrationCreateRequest(
    @NotNull java.util.UUID registrationTypeId,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String referenceCode,
    @NotNull @Valid StatutoryRegistrationVersionWriteRequest version) {

  public void validate() {
    version.validate();
    if (!registrationTypeId.equals(version.registrationTypeId())) {
      throw new IllegalArgumentException(
          "Registration identity type must match the version type");
    }
  }
}
