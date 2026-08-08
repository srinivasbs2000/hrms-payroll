package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrationReadinessContractTest {
  @Test
  void readinessRequestCarriesOperationalWarningHorizon() {
    RegistrationReadinessRequest request =
        new RegistrationReadinessRequest(
            UUID.randomUUID(),
            RegistrationOwnerKind.PAYROLL_STATUTORY_UNIT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 8, 7),
            45);

    assertThat(request.warningHorizonDays()).isEqualTo(45);
    assertThat(request.asOf()).isEqualTo(LocalDate.of(2026, 8, 7));
  }
}
