package com.acme.hrms.payroll.organisation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AuthorisedSignatoryCreateRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String code,
    @NotBlank
        @Pattern(regexp = "^(LEGAL_ENTITY|PAYROLL_STATUTORY_UNIT)$")
        String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    @NotNull @Valid AuthorisedSignatoryVersionWriteRequest version) {

  public void validate() {
    boolean legal = "LEGAL_ENTITY".equals(ownerKind);
    boolean psu = "PAYROLL_STATUTORY_UNIT".equals(ownerKind);
    if (!legal && !psu) {
      throw new IllegalArgumentException(
          "ownerKind must be LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT");
    }
    if (legal && (legalEntityId == null || payrollStatutoryUnitId != null)) {
      throw new IllegalArgumentException(
          "LEGAL_ENTITY owner requires only legalEntityId");
    }
    if (psu && (payrollStatutoryUnitId == null || legalEntityId != null)) {
      throw new IllegalArgumentException(
          "PAYROLL_STATUTORY_UNIT owner requires only payrollStatutoryUnitId");
    }
    version.validate();
  }
}
