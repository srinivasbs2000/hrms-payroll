package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CtcPolicyVersionWriteRequest(
    @NotBlank @Size(max = 160) String name,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotBlank
        @Pattern(regexp = "^(MONTHLY_X_12|PAY_PERIOD_FACTOR|EXACT_ANNUAL)$")
        String annualisationMethod,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal toleranceAmount,
    @NotNull UUID residualComponentId,
    @NotNull UUID residualComponentVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotEmpty List<@Valid CtcPolicyTreatmentWriteRequest> treatments) {

  private static final Set<String> ANNUALISATION_METHODS =
      Set.of("MONTHLY_X_12", "PAY_PERIOD_FACTOR", "EXACT_ANNUAL");
  private static final Set<String> REQUIRED_COST_VIEWS =
      Set.of("OFFERED", "TARGET", "ACCRUED", "ACTUAL_EMPLOYER_COST");

  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (!"INR".equals(resolvedCurrency())) {
      throw new IllegalArgumentException("Only INR currency is supported");
    }
    if (annualisationMethod == null
        || !ANNUALISATION_METHODS.contains(annualisationMethod)) {
      throw new IllegalArgumentException(
          "annualisationMethod contains an unsupported value");
    }
    if (toleranceAmount != null && toleranceAmount.signum() < 0) {
      throw new IllegalArgumentException(
          "toleranceAmount must be non-negative");
    }
    if (residualComponentId == null || residualComponentVersionId == null) {
      throw new IllegalArgumentException(
          "residualComponentId and residualComponentVersionId are required");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
    if (treatments == null || treatments.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one CTC policy treatment is required");
    }

    Set<Integer> sequences = new HashSet<>();
    Set<TreatmentKey> treatmentKeys = new HashSet<>();
    Set<String> costViews = new HashSet<>();
    boolean residualIncluded = false;

    for (CtcPolicyTreatmentWriteRequest treatment : treatments) {
      if (treatment == null) {
        throw new IllegalArgumentException(
            "CTC policy treatments must not contain null entries");
      }
      treatment.validate(effectiveFrom, effectiveTo);
      if (!sequences.add(treatment.treatmentSequence())) {
        throw new IllegalArgumentException(
            "CTC policy treatment sequence numbers must be unique");
      }
      TreatmentKey key =
          new TreatmentKey(
              treatment.costView(),
              treatment.componentVersionId());
      if (!treatmentKeys.add(key)) {
        throw new IllegalArgumentException(
            "A component version may appear only once in each cost view");
      }
      costViews.add(treatment.costView());
      if (residualComponentId.equals(treatment.componentId())
          && residualComponentVersionId.equals(
              treatment.componentVersionId())
          && !"EXCLUDE".equals(treatment.treatmentType())) {
        residualIncluded = true;
      }
    }

    if (!costViews.equals(REQUIRED_COST_VIEWS)) {
      throw new IllegalArgumentException(
          "Each CTC policy version must contain all four cost views");
    }
    if (!residualIncluded) {
      throw new IllegalArgumentException(
          "The residual component must have at least one non-EXCLUDE treatment");
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }

  public BigDecimal resolvedToleranceAmount() {
    return toleranceAmount == null
        ? BigDecimal.ZERO.setScale(4)
        : toleranceAmount;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown request field: " + property);
  }

  private record TreatmentKey(
      String costView,
      UUID componentVersionId) {}
}
