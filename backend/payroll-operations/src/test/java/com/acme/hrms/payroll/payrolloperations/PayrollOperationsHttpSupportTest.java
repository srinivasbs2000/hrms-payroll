package com.acme.hrms.payroll.payrolloperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PayrollOperationsHttpSupportTest {

  @Test
  void acceptsPlainQuotedAndWeakNumericVersions() {
    assertThat(PayrollOperationsHttpSupport.expectedVersion("0")).isZero();
    assertThat(PayrollOperationsHttpSupport.expectedVersion(" 42 "))
        .isEqualTo(42);
    assertThat(PayrollOperationsHttpSupport.expectedVersion("\"7\""))
        .isEqualTo(7);
    assertThat(PayrollOperationsHttpSupport.expectedVersion("W/\"9\""))
        .isEqualTo(9);
    assertThat(PayrollOperationsHttpSupport.expectedVersion("W/ \"11\""))
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
        () -> PayrollOperationsHttpSupport.expectedVersion(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
  }
}
