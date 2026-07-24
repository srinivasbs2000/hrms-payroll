package com.acme.hrms.payroll.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PayrollCalculationHttpSupportTest {

  @Test
  void acceptsPlainQuotedAndWeakNumericVersions() {
    assertThat(PayrollCalculationHttpSupport.expectedVersion("0")).isZero();
    assertThat(PayrollCalculationHttpSupport.expectedVersion(" 42 "))
        .isEqualTo(42);
    assertThat(PayrollCalculationHttpSupport.expectedVersion("\"7\""))
        .isEqualTo(7);
    assertThat(PayrollCalculationHttpSupport.expectedVersion("W/\"9\""))
        .isEqualTo(9);
    assertThat(PayrollCalculationHttpSupport.expectedVersion("W/ \"11\""))
        .isEqualTo(11);
  }

  @Test
  void rejectsMissingNegativeMalformedAndOverflowingVersions() {
    assertInvalid(null, "numeric version");
    assertInvalid("", "numeric version");
    assertInvalid(" ", "numeric version");
    assertInvalid("-1", "numeric version");
    assertInvalid("1.0", "numeric version");
    assertInvalid("abc", "numeric version");
    assertInvalid("\"1", "numeric version");
    assertInvalid("1\"", "numeric version");
    assertInvalid("w/\"1\"", "numeric version");
    assertInvalid("9223372036854775808", "outside the supported range");
  }

  private static void assertInvalid(String value, String message) {
    assertThatThrownBy(
        () -> PayrollCalculationHttpSupport.expectedVersion(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
  }
}
