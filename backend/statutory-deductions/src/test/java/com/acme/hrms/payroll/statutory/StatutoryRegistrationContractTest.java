package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatutoryRegistrationContractTest {
  @Test
  void registrationIdentityTypeMustMatchVersionType() {
    UUID typeA = UUID.randomUUID();
    UUID typeB = UUID.randomUUID();

    StatutoryRegistrationCreateRequest request =
        new StatutoryRegistrationCreateRequest(
            typeA,
            "REG_A",
            new StatutoryRegistrationVersionWriteRequest(
                typeB,
                UUID.randomUUID(),
                "ABC-123",
                RegistrationOwnerKind.LEGAL_ENTITY,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null));

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match");
  }

  @Test
  void parentReferencesAreAtomic() {
    StatutoryRegistrationVersionWriteRequest request =
        new StatutoryRegistrationVersionWriteRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ABC-123",
            RegistrationOwnerKind.ESTABLISHMENT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            LocalDate.of(2026, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supplied together");
  }
}
