package com.acme.hrms.payroll.organisation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JurisdictionResolutionContractTest {
  @Test
  void resolutionRequiresAtLeastOneTarget() {
    JurisdictionResolutionRequest request =
        new JurisdictionResolutionRequest(
            LocalDate.of(2026, 8, 7),
            null,
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("work-location or establishment");
  }

  @Test
  void workLocationOverrideRejectsEstablishmentTarget() {
    JurisdictionOverrideWriteRequest request =
        new JurisdictionOverrideWriteRequest(
            "WORK_LOCATION",
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 8, 7),
            null,
            "Synthetic override");

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WORK_LOCATION");
  }

  @Test
  void overrideRejectsNonPositiveRange() {
    UUID workLocationVersionId = UUID.randomUUID();
    JurisdictionOverrideWriteRequest request =
        new JurisdictionOverrideWriteRequest(
            "WORK_LOCATION",
            workLocationVersionId,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 7),
            "Synthetic override");

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");
  }
}
