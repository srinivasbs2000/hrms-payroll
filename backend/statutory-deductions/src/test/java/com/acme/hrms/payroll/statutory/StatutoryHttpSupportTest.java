package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StatutoryHttpSupportTest {
  @Test
  void acceptsPlainQuotedAndWeakNumericVersions() {
    assertThat(StatutoryHttpSupport.expectedVersion("0")).isZero();
    assertThat(StatutoryHttpSupport.expectedVersion(" 42 ")).isEqualTo(42);
    assertThat(StatutoryHttpSupport.expectedVersion("\"7\"")).isEqualTo(7);
    assertThat(StatutoryHttpSupport.expectedVersion("W/\"9\"")).isEqualTo(9);
    assertThat(StatutoryHttpSupport.expectedVersion("W/ \"11\"")).isEqualTo(11);
  }

  @Test
  void rejectsInvalidVersionsAndIdempotencyKeys() {
    assertThatThrownBy(() -> StatutoryHttpSupport.expectedVersion(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numeric version");
    assertThatThrownBy(() -> StatutoryHttpSupport.expectedVersion("-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numeric version");
    assertThatThrownBy(() -> StatutoryHttpSupport.expectedVersion("abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numeric version");
    assertThatThrownBy(
        () -> StatutoryHttpSupport.requireIdempotencyKey("short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 8 and 120");
  }
}
