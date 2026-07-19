package com.acme.hrms.payroll.calculation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalPayloadHasher {
  private final ObjectMapper mapper;

  public CanonicalPayloadHasher() {
    mapper = new ObjectMapper();
    mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    mapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
  }

  public String canonicalJson(Object payload) {
    try {
      return mapper.writeValueAsString(normalize(payload));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("Payload is not canonically serializable", exception);
    }
  }

  public String sha256(Object payload) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonicalJson(payload).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private Object normalize(Object value) {
    if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros();
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((key, nested) -> sorted.put(String.valueOf(key), normalize(nested)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      var normalized = new ArrayList<>();
      list.forEach(item -> normalized.add(normalize(item)));
      return normalized;
    }
    return value;
  }
}
