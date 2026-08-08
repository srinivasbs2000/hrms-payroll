package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationTypeContractTest {
  @Test
  void parentRequiredNeedsParentType() {
    RegistrationTypeVersionWriteRequest request =
        new RegistrationTypeVersionWriteRequest(
            "Generic child",
            "GENERIC",
            "AUTHORITY",
            "STATE",
            null,
            "UPPER",
            true,
            null,
            List.of(RegistrationOwnerKind.LEGAL_ENTITY),
            LocalDate.of(2026, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parent registration type");
  }

  @Test
  void identifierPatternUsesExplicitJavaRegexV1WholeStringSemantics() {
    assertThat(RegistrationTypeVersionWriteRequest.IDENTIFIER_PATTERN_DIALECT)
        .isEqualTo("JAVA_REGEX_V1");
    assertThat(
            RegistrationTypeVersionWriteRequest.matchesIdentifierPattern(
                "ABC-[0-9]{3}",
                "ABC-123"))
        .isTrue();
    assertThat(
            RegistrationTypeVersionWriteRequest.matchesIdentifierPattern(
                "ABC-[0-9]{3}",
                "XABC-123"))
        .isFalse();
    assertThatThrownBy(
            () ->
                RegistrationTypeVersionWriteRequest.validateIdentifierPattern(
                    "["))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JAVA_REGEX_V1");
  }

  @Test
  void ownerKindsMustBeUnique() {
    RegistrationTypeVersionWriteRequest request =
        new RegistrationTypeVersionWriteRequest(
            "Generic",
            "GENERIC",
            "AUTHORITY",
            "COUNTRY",
            null,
            "PRESERVE",
            false,
            null,
            List.of(
                RegistrationOwnerKind.LEGAL_ENTITY,
                RegistrationOwnerKind.LEGAL_ENTITY),
            LocalDate.of(2026, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
  }
}
