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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SalaryStructureWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 160) String name,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotBlank
        @Pattern(regexp = "^(STANDARD|EXECUTIVE|SALES|HOURLY|CONTRACT)$")
        String structureType,
    @NotBlank
        @Pattern(regexp = "^(MONTHLY|WEEKLY|BIWEEKLY|SEMIMONTHLY)$")
        String payFrequency,
    @NotBlank
        @Pattern(regexp = "^(STANDARD|RESTRICTED|EXECUTIVE)$")
        String confidentialityLevel,
    @NotNull UUID ctcPolicyVersionId,
    UUID eligibilityRuleVersionId,
    @NotBlank
        @Pattern(regexp = "^(ANNUAL_CTC|ANNUAL_GROSS|MONTHLY_GROSS)$")
        String targetType,
    @NotNull @DecimalMin(value = "0.0000", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal targetAnnualAmount,
    @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal toleranceAmount,
    @NotNull UUID residualComponentVersionId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotEmpty List<@Valid SalaryStructureLineWriteRequest> lines) {

  private static final Set<String> STRUCTURE_TYPES = Set.of(
      "STANDARD", "EXECUTIVE", "SALES", "HOURLY", "CONTRACT");
  private static final Set<String> PAY_FREQUENCIES = Set.of(
      "MONTHLY", "WEEKLY", "BIWEEKLY", "SEMIMONTHLY");
  private static final Set<String> CONFIDENTIALITY_LEVELS = Set.of(
      "STANDARD", "RESTRICTED", "EXECUTIVE");
  private static final Set<String> TARGET_TYPES = Set.of(
      "ANNUAL_CTC", "ANNUAL_GROSS", "MONTHLY_GROSS");

  public void validate(boolean identityCreation) {
    if (identityCreation && (code == null || code.isBlank())) {
      throw new IllegalArgumentException(
          "code is required when creating a salary-structure identity");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (currency != null && !currency.isBlank()
        && !"INR".equals(currency)) {
      throw new IllegalArgumentException(
          "Only INR currency is supported");
    }
    if (!STRUCTURE_TYPES.contains(structureType)) {
      throw new IllegalArgumentException(
          "structureType contains an unsupported value");
    }
    if (!PAY_FREQUENCIES.contains(payFrequency)) {
      throw new IllegalArgumentException(
          "payFrequency contains an unsupported value");
    }
    if (!CONFIDENTIALITY_LEVELS.contains(confidentialityLevel)) {
      throw new IllegalArgumentException(
          "confidentialityLevel contains an unsupported value");
    }
    if (!TARGET_TYPES.contains(targetType)) {
      throw new IllegalArgumentException(
          "targetType contains an unsupported value");
    }
    if (ctcPolicyVersionId == null) {
      throw new IllegalArgumentException(
          "ctcPolicyVersionId is required");
    }
    if (residualComponentVersionId == null) {
      throw new IllegalArgumentException(
          "residualComponentVersionId is required");
    }
    if (targetAnnualAmount == null
        || targetAnnualAmount.signum() <= 0) {
      throw new IllegalArgumentException(
          "targetAnnualAmount must be greater than zero");
    }
    if (toleranceAmount == null || toleranceAmount.signum() < 0) {
      throw new IllegalArgumentException(
          "toleranceAmount must not be negative");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException(
          "effectiveFrom is required");
    }
    if (effectiveTo != null
        && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one salary-structure line is required");
    }

    Set<Integer> sequences = new HashSet<>();
    Set<Integer> ctcOrders = new HashSet<>();
    Set<Integer> payslipOrders = new HashSet<>();
    Set<UUID> components = new HashSet<>();
    List<SalaryStructureLineWriteRequest> residuals = lines.stream()
        .filter(line -> line != null && "RESIDUAL".equals(line.lineType()))
        .toList();

    for (SalaryStructureLineWriteRequest line : lines) {
      if (line == null) {
        throw new IllegalArgumentException(
            "Salary-structure lines must not contain null entries");
      }
      line.validate();
      if (!sequences.add(line.sequenceNo())) {
        throw new IllegalArgumentException(
            "Salary-structure line sequence numbers must be unique");
      }
      if (!ctcOrders.add(line.ctcDisplayOrder())) {
        throw new IllegalArgumentException(
            "CTC display orders must be unique");
      }
      if (!payslipOrders.add(line.payslipDisplayOrder())) {
        throw new IllegalArgumentException(
            "Payslip display orders must be unique");
      }
      if (!components.add(line.componentVersionId())) {
        throw new IllegalArgumentException(
            "A pay-component version may appear only once in a structure");
      }
    }

    if (residuals.size() != 1) {
      throw new IllegalArgumentException(
          "Exactly one RESIDUAL salary-structure line is required");
    }
    SalaryStructureLineWriteRequest residual = residuals.getFirst();
    if (!residual.componentVersionId()
        .equals(residualComponentVersionId)) {
      throw new IllegalArgumentException(
          "The RESIDUAL line must use residualComponentVersionId");
    }
    int maximumSequence = lines.stream()
        .max(Comparator.comparingInt(
            SalaryStructureLineWriteRequest::sequenceNo))
        .orElseThrow()
        .sequenceNo();
    if (residual.sequenceNo() != maximumSequence) {
      throw new IllegalArgumentException(
          "The RESIDUAL line must be the final calculation sequence");
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown salary-structure field: " + property);
  }
}
