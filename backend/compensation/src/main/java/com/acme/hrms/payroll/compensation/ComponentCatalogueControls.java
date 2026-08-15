package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public API records for P5-CCF-01 component formula/rate/control administration. */
public final class ComponentCatalogueControls {
  private ComponentCatalogueControls() {}

  private static final Set<String> PHASES =
      Set.of("INPUT", "PRE_TAX", "TAX", "POST_TAX", "NET");
  private static final Set<String> RATE_TYPES =
      Set.of("TEXT", "NUMBER", "BOOLEAN", "DATE");
  private static final Set<String> RATE_VALUE_TYPES =
      Set.of("AMOUNT", "PERCENTAGE", "FACTOR", "QUANTITY");
  private static final Set<String> ROUNDING_METHODS =
      Set.of("HALF_UP", "HALF_EVEN", "HALF_DOWN", "UP", "DOWN", "CEILING", "FLOOR");
  private static final Set<String> ROUNDING_STAGES =
      Set.of("COMPONENT", "INTERMEDIATE", "FINAL");
  private static final Set<String> NEGATIVE_TREATMENTS =
      Set.of("SYMMETRIC", "TOWARD_ZERO", "AWAY_FROM_ZERO", "PROHIBIT");
  private static final Set<String> PRORATION_EVENTS =
      Set.of("JOINING", "EXIT", "UNPAID_LEAVE", "TRANSFER", "SALARY_REVISION");
  private static final Set<String> PRORATION_METHODS =
      Set.of("CALENDAR_DAYS", "WORKING_DAYS", "ACTUAL_DAYS", "NONE");
  private static final Set<String> PRORATION_BASES =
      Set.of("PAY_PERIOD", "MONTH", "ANNUAL", "DAILY_RATE");

  public record FormulaValidationRequest(
      @NotBlank @Size(max = 1000) String expression,
      String calculationPhase,
      String resultContract) {
    public String resolvedCalculationPhase() {
      return resolveMember(PHASES, calculationPhase, "PRE_TAX", "calculationPhase");
    }

    public String resolvedResultContract() {
      String resolved = resultContract == null || resultContract.isBlank()
          ? "DECIMAL"
          : resultContract;
      if (!"DECIMAL".equals(resolved)) {
        throw new IllegalArgumentException("resultContract contains an unsupported value");
      }
      return resolved;
    }
  }

  public record FormulaValidationView(
      String canonicalExpression,
      List<String> dependencies,
      String calculationPhase,
      String resultContract,
      String formulaFingerprint) {}

  public record FormulaDependencyView(
      UUID componentId,
      UUID componentVersionId,
      String componentCode,
      String calculationPhase,
      UUID dependencyComponentId,
      UUID dependencyComponentVersionId,
      String dependencyCode,
      String dependencyPhase,
      int dependencyOrder,
      String formulaFingerprint) {}

  public record FormulaDependantView(
      UUID dependantComponentId,
      UUID dependantComponentVersionId,
      String dependantComponentCode,
      UUID dependencyComponentVersionId,
      int dependencyOrder,
      String formulaFingerprint) {}

  public record ComponentImpactView(
      UUID componentId,
      List<FormulaDependencyView> outgoingDependencies,
      List<FormulaDependantView> formulaDependants,
      List<UUID> payrollBaseIds,
      List<UUID> salaryStructureIds,
      List<UUID> roundingPolicyIds,
      List<UUID> prorationPolicyIds) {}

  /** Exact approved statutory rule-version reference; legal interpretation remains in the statutory context. */
  public record StatutoryWageReferenceRequest(
      @NotNull UUID statutoryRuleId,
      @NotNull UUID statutoryRuleVersionId) {
    public void validate() {
      if (statutoryRuleId == null || statutoryRuleVersionId == null) {
        throw new IllegalArgumentException(
            "statutoryRuleId and statutoryRuleVersionId are required");
      }
    }
  }

  public record StatutoryWageReferenceView(
      UUID componentId,
      UUID componentVersionId,
      UUID statutoryRuleId,
      UUID statutoryRuleVersionId,
      String statutoryRuleCode,
      String ruleCategory,
      LocalDate ruleEffectiveFrom,
      LocalDate ruleEffectiveTo) {}

  public record RateTableCreateRequest(
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String code,
      @NotBlank @Size(max = 160) String name,
      @NotNull @Valid RateTableVersionWriteRequest version) {
    public void validate() {
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("code is required");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name is required");
      }
      if (version == null) {
        throw new IllegalArgumentException("version is required");
      }
      version.validate();
    }
  }

  public record RateTableVersionWriteRequest(
      @NotBlank String valueType,
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$") String unitCode,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      @NotEmpty @Size(max = 8) List<@Valid RateDimensionRequest> dimensions,
      @NotEmpty @Size(max = 500) List<@Valid RateCellRequest> cells) {
    public void validate() {
      requireRateValueContract(valueType, unitCode);
      requireRange(effectiveFrom, effectiveTo);
      if (dimensions == null || dimensions.isEmpty()) {
        throw new IllegalArgumentException("at least one rate-table dimension is required");
      }
      if (cells == null || cells.isEmpty()) {
        throw new IllegalArgumentException("at least one rate-table cell is required");
      }
      Map<String, String> dimensionTypes = new LinkedHashMap<>();
      for (RateDimensionRequest dimension : dimensions) {
        if (dimension == null) {
          throw new IllegalArgumentException("rate-table dimension is required");
        }
        dimension.validate();
        if (dimensionTypes.putIfAbsent(dimension.code(), dimension.dataType()) != null) {
          throw new IllegalArgumentException("rate-table dimension codes must be unique");
        }
      }
      Set<Map<String, String>> uniqueKeys = new java.util.HashSet<>();
      for (RateCellRequest cell : cells) {
        if (cell == null) {
          throw new IllegalArgumentException("rate-table cell is required");
        }
        cell.validate(dimensionTypes);
        if (!uniqueKeys.add(Map.copyOf(cell.dimensionValues()))) {
          throw new IllegalArgumentException("rate-table cell dimension values must be unique");
        }
      }
    }
  }

  public record RateDimensionRequest(
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
      @NotBlank @Size(max = 120) String name,
      @NotBlank String dataType) {
    public void validate() {
      requireMember(RATE_TYPES, dataType, "dataType");
    }
  }

  public record RateCellRequest(
      @NotNull Map<@NotBlank String, @NotBlank String> dimensionValues,
      @NotNull @Digits(integer = 19, fraction = 10)
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rateValue) {
    public void validate(Map<String, String> dimensionTypes) {
      if (dimensionValues == null || dimensionValues.isEmpty()) {
        throw new IllegalArgumentException("dimensionValues are required");
      }
      if (!dimensionValues.keySet().equals(dimensionTypes.keySet())) {
        throw new IllegalArgumentException(
            "each rate-table cell must provide exactly the configured dimensions");
      }
      validateDimensionValues(dimensionTypes, dimensionValues);
      if (rateValue == null) {
        throw new IllegalArgumentException("rateValue is required");
      }
      if (rateValue.precision() > 29 || Math.max(rateValue.scale(), 0) > 10) {
        throw new IllegalArgumentException("rateValue exceeds precision 29 / scale 10");
      }
    }
  }

  public record RateDimensionView(UUID id, int sequence, String code, String name, String dataType) {}

  public record RateCellView(
      UUID id,
      int sequence,
      Map<String, String> dimensionValues,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rateValue) {}

  public record RateTableView(
      UUID identityId,
      String code,
      String name,
      String lifecycleStatus,
      long identityVersionNo,
      LocalDate retirementEffectiveDate,
      String retirementReason,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String valueType,
      String unitCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String approvalStatus,
      UUID supersedesVersionId,
      boolean superseded,
      List<RateDimensionView> dimensions,
      List<RateCellView> cells) {}

  public record RateLookupView(
      UUID identityId,
      UUID versionId,
      String valueType,
      String unitCode,
      Map<String, String> dimensionValues,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rateValue,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record RoundingPolicyCreateRequest(
      @NotNull UUID componentId,
      @NotNull @Valid RoundingPolicyVersionWriteRequest version) {
    public void validate() {
      if (componentId == null || version == null) {
        throw new IllegalArgumentException("componentId and version are required");
      }
      version.validate();
    }
  }

  public record RoundingPolicyVersionWriteRequest(
      @NotBlank String method,
      @NotNull @Min(0) @Max(10) Integer scale,
      @NotBlank String stage,
      @NotBlank String negativeTreatment,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      requireMember(ROUNDING_METHODS, method, "method");
      if (scale == null || scale < 0 || scale > 10) {
        throw new IllegalArgumentException("scale must be between 0 and 10");
      }
      requireMember(ROUNDING_STAGES, stage, "stage");
      requireMember(NEGATIVE_TREATMENTS, negativeTreatment, "negativeTreatment");
      requireRange(effectiveFrom, effectiveTo);
    }
  }

  public record RoundingPolicyView(
      UUID identityId,
      UUID componentId,
      String componentCode,
      String lifecycleStatus,
      long identityVersionNo,
      LocalDate retirementEffectiveDate,
      String retirementReason,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String method,
      int scale,
      String stage,
      String negativeTreatment,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String approvalStatus,
      UUID supersedesVersionId,
      boolean superseded) {}

  public record ProrationPolicyCreateRequest(
      @NotNull UUID componentId,
      @NotBlank String eventType,
      @NotNull @Valid ProrationPolicyVersionWriteRequest version) {
    public void validate() {
      if (componentId == null || version == null) {
        throw new IllegalArgumentException("componentId and version are required");
      }
      requireMember(PRORATION_EVENTS, eventType, "eventType");
      version.validate();
    }
  }

  public record ProrationPolicyVersionWriteRequest(
      @NotBlank String method,
      @NotBlank String basis,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      requireMember(PRORATION_METHODS, method, "method");
      requireMember(PRORATION_BASES, basis, "basis");
      requireRange(effectiveFrom, effectiveTo);
    }
  }

  public record ProrationPolicyView(
      UUID identityId,
      UUID componentId,
      String componentCode,
      String eventType,
      String lifecycleStatus,
      long identityVersionNo,
      LocalDate retirementEffectiveDate,
      String retirementReason,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String method,
      String basis,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String approvalStatus,
      UUID supersedesVersionId,
      boolean superseded) {}

  public record EffectiveEndRequest(@NotNull LocalDate effectiveTo) {
    public void validate() {
      if (effectiveTo == null) {
        throw new IllegalArgumentException("effectiveTo is required");
      }
    }
  }

  public record RetirementRequest(
      @NotNull LocalDate effectiveDate,
      @NotBlank @Size(max = 500) String reason) {
    public void validate() {
      if (effectiveDate == null || reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("effectiveDate and reason are required");
      }
    }
  }

  public record ApprovalRequest(@NotNull Long expectedVersion) {
    public long resolvedExpectedVersion() {
      if (expectedVersion == null || expectedVersion < 0) {
        throw new IllegalArgumentException("expectedVersion must be non-negative");
      }
      return expectedVersion;
    }
  }

  public static void validateRateDimensionValues(
      List<RateDimensionView> dimensions, Map<String, String> values) {
    Map<String, String> types = new LinkedHashMap<>();
    for (RateDimensionView dimension : dimensions) {
      types.put(dimension.code(), dimension.dataType());
    }
    if (values == null || !values.keySet().equals(types.keySet())) {
      throw new IllegalArgumentException(
          "rate lookup must provide exactly the configured dimensions");
    }
    validateDimensionValues(types, values);
  }

  private static void validateDimensionValues(
      Map<String, String> dimensionTypes, Map<String, String> values) {
    for (Map.Entry<String, String> dimension : dimensionTypes.entrySet()) {
      String value = values.get(dimension.getKey());
      if (value == null || value.isBlank() || !value.equals(value.trim())) {
        throw new IllegalArgumentException(
            "rate-table dimension values must be non-blank canonical strings");
      }
      switch (dimension.getValue()) {
        case "TEXT" -> {
          // The trim/non-blank rule above is the complete TEXT canonical form.
        }
        case "NUMBER" -> {
          BigDecimal parsed;
          try {
            parsed = new BigDecimal(value);
          } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                "NUMBER rate-table dimensions require canonical decimal values", exception);
          }
          String canonical = parsed.signum() == 0
              ? "0"
              : parsed.stripTrailingZeros().toPlainString();
          if (!canonical.equals(value)) {
            throw new IllegalArgumentException(
                "NUMBER rate-table dimensions require canonical decimal values");
          }
        }
        case "BOOLEAN" -> {
          if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(
                "BOOLEAN rate-table dimensions require true or false");
          }
        }
        case "DATE" -> {
          try {
            if (!LocalDate.parse(value).toString().equals(value)) {
              throw new IllegalArgumentException(
                  "DATE rate-table dimensions require ISO-8601 dates");
            }
          } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                "DATE rate-table dimensions require ISO-8601 dates", exception);
          }
        }
        default -> throw new IllegalArgumentException("unsupported rate-table dimension type");
      }
    }
  }

  private static void requireRateValueContract(String valueType, String unitCode) {
    requireMember(RATE_VALUE_TYPES, valueType, "valueType");
    if (unitCode == null || !unitCode.matches("^[A-Z][A-Z0-9_]{1,19}$")) {
      throw new IllegalArgumentException("unitCode contains an unsupported value");
    }
    boolean valid = switch (valueType) {
      case "AMOUNT" -> {
        if (!unitCode.matches("^[A-Z]{3}$")) {
          yield false;
        }
        try {
          Currency.getInstance(unitCode);
          yield true;
        } catch (IllegalArgumentException exception) {
          yield false;
        }
      }
      case "PERCENTAGE" -> "PERCENT".equals(unitCode);
      case "FACTOR" -> "FACTOR".equals(unitCode);
      case "QUANTITY" -> !"PERCENT".equals(unitCode) && !"FACTOR".equals(unitCode);
      default -> false;
    };
    if (!valid) {
      throw new IllegalArgumentException("valueType and unitCode are inconsistent");
    }
  }

  private static String resolveMember(
      Set<String> allowed, String value, String fallback, String field) {
    String resolved = value == null || value.isBlank() ? fallback : value;
    requireMember(allowed, resolved, field);
    return resolved;
  }

  private static void requireMember(Set<String> allowed, String value, String field) {
    if (value == null || value.isBlank() || !allowed.contains(value)) {
      throw new IllegalArgumentException(field + " contains an unsupported value");
    }
  }

  private static void requireRange(LocalDate from, LocalDate to) {
    if (from == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    if (to != null && !to.isAfter(from)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
}
