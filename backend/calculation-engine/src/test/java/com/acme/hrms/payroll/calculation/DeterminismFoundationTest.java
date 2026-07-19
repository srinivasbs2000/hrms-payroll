package com.acme.hrms.payroll.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeterminismFoundationTest {
  @Test
  void componentOrderIsStableAndDependencySafe() {
    var result = new FixedGrossToNetCalculator().calculate(
        new BigDecimal("50000"), new BigDecimal("0.40"), new BigDecimal("90000"), BigDecimal.ONE);
    assertThat(result.components().keySet())
        .containsExactly("BASIC", "HRA", "SPECIAL_ALLOWANCE");
  }

  @Test
  void canonicalHashIgnoresMapInsertionOrderAndDecimalScale() {
    var first = new LinkedHashMap<String, Object>();
    first.put("tenant", "synthetic");
    first.put("amount", new BigDecimal("90000.00"));
    first.put("components", List.of(Map.of("code", "BASIC", "amount", new BigDecimal("50000.0"))));
    var second = new LinkedHashMap<String, Object>();
    second.put("components", List.of(Map.of("amount", new BigDecimal("50000.0000"), "code", "BASIC")));
    second.put("amount", new BigDecimal("90000"));
    second.put("tenant", "synthetic");
    var hasher = new CanonicalPayloadHasher();
    assertThat(hasher.canonicalJson(first)).isEqualTo(hasher.canonicalJson(second));
    assertThat(hasher.sha256(first)).isEqualTo(hasher.sha256(second)).hasSize(64);
  }
}
