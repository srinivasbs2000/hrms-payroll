package com.acme.hrms.payroll.organisation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkLocationContractTest {
  @Test
  void workLocationRejectsEmptyEffectiveRange() {
    WorkLocationVersionWriteRequest request =
        new WorkLocationVersionWriteRequest(
            "Bengaluru",
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            "Bengaluru",
            "KA",
            "560001",
            "IN",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1));

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");
  }
}
