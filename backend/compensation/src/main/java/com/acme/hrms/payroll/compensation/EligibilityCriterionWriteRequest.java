package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EligibilityCriterionWriteRequest(
    @NotNull @Min(1) Integer criterionSequence,
    @NotBlank String factKey,
    @NotBlank String factType,
    @NotBlank String comparisonOperator,
    @NotNull JsonNode value) {

  private static final Map<String, String> FACT_TYPES = Map.ofEntries(
      Map.entry("COUNTRY_CODE", "TEXT"),
      Map.entry("STATE_CODE", "TEXT"),
      Map.entry("LOCATION_CODE", "TEXT"),
      Map.entry("LEGAL_ENTITY_VERSION_ID", "UUID"),
      Map.entry("PAYROLL_STATUTORY_UNIT_VERSION_ID", "UUID"),
      Map.entry("ESTABLISHMENT_VERSION_ID", "UUID"),
      Map.entry("PAY_GROUP_VERSION_ID", "UUID"),
      Map.entry("EMPLOYMENT_TYPE", "TEXT"),
      Map.entry("EMPLOYEE_CATEGORY", "TEXT"),
      Map.entry("GRADE_CODE", "TEXT"),
      Map.entry("JOB_CODE", "TEXT"),
      Map.entry("SERVICE_MONTHS", "NUMBER"),
      Map.entry("ANNUAL_COMPENSATION_AMOUNT", "NUMBER"),
      Map.entry("EFFECTIVE_DATE", "DATE"));
  private static final Set<String> OPERATORS =
      Set.of("EQ", "NE", "IN", "NOT_IN", "GTE", "LTE");
  private static final Set<String> ORDERED_TYPES =
      Set.of("NUMBER", "DATE");

  public void validate() {
    if (criterionSequence == null || criterionSequence < 1) {
      throw new IllegalArgumentException(
          "criterionSequence must be greater than zero");
    }
    String expectedType = expectedFactType(factKey);
    if (!expectedType.equals(factType)) {
      throw new IllegalArgumentException(
          "factType for " + factKey + " must be " + expectedType);
    }
    if (comparisonOperator == null
        || !OPERATORS.contains(comparisonOperator)) {
      throw new IllegalArgumentException(
          "comparisonOperator contains an unsupported value");
    }
    if (("GTE".equals(comparisonOperator)
            || "LTE".equals(comparisonOperator))
        && !ORDERED_TYPES.contains(factType)) {
      throw new IllegalArgumentException(
          "GTE and LTE are supported only for NUMBER and DATE facts");
    }
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException(
          "criterion value is required");
    }

    if ("IN".equals(comparisonOperator)
        || "NOT_IN".equals(comparisonOperator)) {
      if (!value.isArray() || value.isEmpty()) {
        throw new IllegalArgumentException(
            "IN and NOT_IN require a non-empty array value");
      }
      for (JsonNode item : value) {
        validateScalar(factType, item, "criterion value");
      }
    } else {
      if (value.isArray()) {
        throw new IllegalArgumentException(
            comparisonOperator + " requires a scalar value");
      }
      validateScalar(factType, value, "criterion value");
    }
  }

  public static Set<String> supportedFactKeys() {
    return Set.copyOf(FACT_TYPES.keySet());
  }

  public static String expectedFactType(String key) {
    String expected = FACT_TYPES.get(key);
    if (expected == null) {
      throw new IllegalArgumentException(
          "factKey contains an unsupported value: " + key);
    }
    return expected;
  }

  public static void validateSuppliedFact(
      String key,
      JsonNode suppliedValue) {
    String expectedType = expectedFactType(key);
    if (suppliedValue == null
        || suppliedValue.isNull()
        || suppliedValue.isArray()
        || suppliedValue.isObject()) {
      throw new IllegalArgumentException(
          "Supplied fact " + key + " must be a scalar "
              + expectedType + " value");
    }
    validateScalar(expectedType, suppliedValue, "Supplied fact " + key);
  }

  private static void validateScalar(
      String type,
      JsonNode candidate,
      String label) {
    if (candidate == null || candidate.isNull()) {
      throw new IllegalArgumentException(label + " must not be null");
    }

    switch (type) {
      case "TEXT" -> {
        if (!candidate.isTextual()
            || candidate.textValue().isBlank()) {
          throw new IllegalArgumentException(
              label + " must be a non-blank TEXT value");
        }
      }
      case "NUMBER" -> {
        if (!candidate.isNumber()) {
          throw new IllegalArgumentException(
              label + " must be a NUMBER value");
        }
      }
      case "DATE" -> {
        if (!candidate.isTextual()) {
          throw new IllegalArgumentException(
              label + " must be an ISO-8601 DATE string");
        }
        try {
          LocalDate.parse(candidate.textValue());
        } catch (DateTimeParseException exception) {
          throw new IllegalArgumentException(
              label + " must be an ISO-8601 DATE string",
              exception);
        }
      }
      case "UUID" -> {
        if (!candidate.isTextual()) {
          throw new IllegalArgumentException(
              label + " must be a UUID string");
        }
        try {
          UUID.fromString(candidate.textValue());
        } catch (IllegalArgumentException exception) {
          throw new IllegalArgumentException(
              label + " must be a UUID string",
              exception);
        }
      }
      default -> throw new IllegalArgumentException(
          "factType contains an unsupported value: " + type);
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown request field: " + property);
  }
}
