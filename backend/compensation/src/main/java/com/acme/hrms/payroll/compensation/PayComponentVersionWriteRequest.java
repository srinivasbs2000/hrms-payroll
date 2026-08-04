package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PayComponentVersionWriteRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$")
        String formulaType,
    @Size(max = 1000) String formulaExpression,
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
        BigDecimal fixedAmount,
    @Min(0) @Max(4) Integer roundingScale,
    @NotBlank String componentCategory,
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$")
        String componentSubcategory,
    @NotBlank String cashImpact,
    @NotBlank String payeeType,
    @NotBlank String paymentChannel,
    @NotBlank String settlementTiming,
    @NotBlank String payslipVisibility,
    @NotBlank String zeroValueVisibility,
    @NotBlank String negativeValuePolicy,
    @NotBlank String frequency,
    @NotBlank String valueNature,
    @NotBlank String amountRepresentation,
    @NotBlank String taxTreatment,
    @NotBlank String payrollTiming,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> FORMULA_TYPES =
      Set.of("FIXED", "PERCENTAGE_OF_COMPONENT", "RESIDUAL");
  private static final Set<String> COMPONENT_CATEGORIES = Set.of(
      "CASH_EARNING", "EMPLOYEE_DEDUCTION", "EMPLOYER_CONTRIBUTION",
      "EMPLOYER_PROVISION", "REIMBURSEMENT", "BENEFIT",
      "TAXABLE_PERQUISITE", "NOTIONAL", "ACCRUAL");
  private static final Set<String> CASH_IMPACTS =
      Set.of("INCREASE", "DECREASE", "NONE");
  private static final Set<String> PAYEES = Set.of(
      "EMPLOYEE", "AUTHORITY", "LENDER", "BENEFIT_PROVIDER", "INTERNAL", "NONE");
  private static final Set<String> PAYMENT_CHANNELS = Set.of(
      "PAYROLL_BANK", "SEPARATE_BANK", "VENDOR", "STATUTORY_REMITTANCE", "NONE");
  private static final Set<String> SETTLEMENT_TIMINGS = Set.of(
      "CURRENT_PERIOD", "DEFERRED", "ACCRUAL", "EXIT", "ANNUAL", "NONE");
  private static final Set<String> PAYSLIP_VISIBILITIES =
      Set.of("SHOW", "SUMMARISE", "HIDE", "CONDITIONAL");
  private static final Set<String> ZERO_VALUE_VISIBILITIES =
      Set.of("SHOW", "SUPPRESS");
  private static final Set<String> NEGATIVE_VALUE_POLICIES =
      Set.of("ALLOW", "PROHIBIT", "REVERSAL_ONLY");
  private static final Set<String> FREQUENCIES = Set.of(
      "PER_PAYROLL_PERIOD", "MONTHLY", "WEEKLY", "DAILY", "ANNUAL",
      "ONE_TIME", "EVENT_DRIVEN", "AD_HOC", "ON_EXIT", "ON_JOINING",
      "ON_CONFIRMATION", "ON_ANNIVERSARY");
  private static final Set<String> VALUE_NATURES = Set.of(
      "FIXED", "VARIABLE", "DERIVED", "EXTERNAL_INPUT", "EMPLOYEE_ELECTION",
      "EMPLOYER_DISCRETION", "STATUTORY", "BALANCE_RECOVERY", "PROVISION",
      "NOTIONAL");
  private static final Set<String> AMOUNT_REPRESENTATIONS = Set.of(
      "ANNUAL_AMOUNT", "MONTHLY_AMOUNT", "DAILY_RATE", "HOURLY_RATE",
      "PER_UNIT_RATE", "PERCENTAGE", "SLAB", "QUANTITY_RATE",
      "FORMULA_RESULT", "EXTERNAL_VALUE");
  private static final Set<String> TAX_TREATMENTS = Set.of(
      "DELEGATED", "TAXABLE", "EXEMPT", "PARTIALLY_EXEMPT",
      "PROOF_DEPENDENT", "REGIME_DEPENDENT", "PERQUISITE",
      "REIMBURSEMENT", "TAX_ONLY_NOTIONAL");
  private static final Set<String> PAYROLL_TIMINGS = Set.of(
      "REGULAR", "OFF_CYCLE_ONLY", "REGULAR_AND_OFF_CYCLE",
      "FINAL_SETTLEMENT_ONLY", "ANNUAL", "CORRECTION",
      "NON_PAYROLL_REPORTING");

  public void validate() {
    requireMember(FORMULA_TYPES, formulaType, "formulaType");
    requireMember(COMPONENT_CATEGORIES, componentCategory, "componentCategory");
    requireMember(CASH_IMPACTS, cashImpact, "cashImpact");
    requireMember(PAYEES, payeeType, "payeeType");
    requireMember(PAYMENT_CHANNELS, paymentChannel, "paymentChannel");
    requireMember(SETTLEMENT_TIMINGS, settlementTiming, "settlementTiming");
    requireMember(PAYSLIP_VISIBILITIES, payslipVisibility, "payslipVisibility");
    requireMember(ZERO_VALUE_VISIBILITIES, zeroValueVisibility, "zeroValueVisibility");
    requireMember(NEGATIVE_VALUE_POLICIES, negativeValuePolicy, "negativeValuePolicy");
    requireMember(FREQUENCIES, frequency, "frequency");
    requireMember(VALUE_NATURES, valueNature, "valueNature");
    requireMember(AMOUNT_REPRESENTATIONS, amountRepresentation, "amountRepresentation");
    requireMember(TAX_TREATMENTS, taxTreatment, "taxTreatment");
    requireMember(PAYROLL_TIMINGS, payrollTiming, "payrollTiming");

    if ("FIXED".equals(formulaType)) {
      if (fixedAmount == null || fixedAmount.signum() < 0) {
        throw new IllegalArgumentException(
            "FIXED formulaType requires a non-negative fixedAmount");
      }
      if (formulaExpression != null && !formulaExpression.isBlank()) {
        throw new IllegalArgumentException(
            "FIXED formulaType must not contain formulaExpression");
      }
    } else {
      if (fixedAmount != null) {
        throw new IllegalArgumentException(
            "Non-FIXED formulaType must not contain fixedAmount");
      }
      if (formulaExpression == null || formulaExpression.isBlank()) {
        throw new IllegalArgumentException(
            "Non-FIXED formulaType requires formulaExpression");
      }
    }

    if (roundingScale != null && (roundingScale < 0 || roundingScale > 4)) {
      throw new IllegalArgumentException("roundingScale must be between 0 and 4");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }

  public int resolvedRoundingScale() {
    return roundingScale == null ? 2 : roundingScale;
  }

  private static void requireMember(Set<String> values, String value, String field) {
    if (value == null || value.isBlank() || !values.contains(value)) {
      throw new IllegalArgumentException(field + " contains an unsupported value");
    }
  }
  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException("Unknown request field: " + property);
  }

}
