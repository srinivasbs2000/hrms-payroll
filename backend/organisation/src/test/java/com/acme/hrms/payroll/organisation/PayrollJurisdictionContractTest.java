package com.acme.hrms.payroll.organisation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollJurisdictionContractTest {
  @Test
  void rootJurisdictionRejectsParent() {
    PayrollJurisdictionVersionWriteRequest request =
        new PayrollJurisdictionVersionWriteRequest(
            "India",
            "IN",
            "COUNTRY",
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Root jurisdiction");
  }

  @Test
  void childJurisdictionRequiresParent() {
    PayrollJurisdictionVersionWriteRequest request =
        new PayrollJurisdictionVersionWriteRequest(
            "Karnataka",
            "IN",
            "STATE",
            2,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires a parent");
  }
}
