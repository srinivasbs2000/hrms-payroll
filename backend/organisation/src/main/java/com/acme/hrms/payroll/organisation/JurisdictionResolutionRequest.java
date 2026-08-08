package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record JurisdictionResolutionRequest(
    @NotNull LocalDate asOf,
    UUID workLocationVersionId,
    UUID establishmentVersionId) {

  public void validate() {
    if (workLocationVersionId == null && establishmentVersionId == null) {
      throw new IllegalArgumentException(
          "A work-location or establishment version is required");
    }
  }
}
