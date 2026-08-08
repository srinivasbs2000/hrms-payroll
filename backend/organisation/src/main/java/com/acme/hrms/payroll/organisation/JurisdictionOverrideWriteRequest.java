package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record JurisdictionOverrideWriteRequest(
    @NotBlank String targetKind,
    UUID workLocationVersionId,
    UUID establishmentVersionId,
    @NotNull UUID payrollJurisdictionId,
    @NotNull UUID payrollJurisdictionVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotBlank @Size(max = 500) String reason) {

  public void validate() {
    if (!"WORK_LOCATION".equals(targetKind)
        && !"ESTABLISHMENT".equals(targetKind)) {
      throw new IllegalArgumentException(
          "targetKind must be WORK_LOCATION or ESTABLISHMENT");
    }
    boolean workLocation = workLocationVersionId != null;
    boolean establishment = establishmentVersionId != null;
    if (workLocation == establishment) {
      throw new IllegalArgumentException(
          "Exactly one override target must be supplied");
    }
    if ("WORK_LOCATION".equals(targetKind) && !workLocation) {
      throw new IllegalArgumentException(
          "WORK_LOCATION override requires workLocationVersionId");
    }
    if ("ESTABLISHMENT".equals(targetKind) && !establishment) {
      throw new IllegalArgumentException(
          "ESTABLISHMENT override requires establishmentVersionId");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }
}
