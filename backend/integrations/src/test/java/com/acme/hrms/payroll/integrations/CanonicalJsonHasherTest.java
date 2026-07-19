package com.acme.hrms.payroll.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalJsonHasherTest {
  @Test
  void mapInsertionOrderDoesNotChangeCanonicalHash() {
    var first = new LinkedHashMap<String, Object>();
    first.put("z", 2);
    first.put("a", Map.of("y", 4, "b", 3));
    var second = new LinkedHashMap<String, Object>();
    second.put("a", Map.of("b", 3, "y", 4));
    second.put("z", 2);

    var hasher = new CanonicalJsonHasher(new ObjectMapper());
    assertThat(hasher.json(first)).isEqualTo(hasher.json(second));
    assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second)).hasSize(64);
  }
}
