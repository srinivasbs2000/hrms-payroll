package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record SalaryAssignmentWriteRequest(
    @NotNull UUID payrollAssignmentVersionId,
    @NotNull UUID salaryStructureVersionId,
    BigDecimal monthlyAmount,
    String targetType,
    BigDecimal targetValue,
    String targetFrequency,
    String currency,
    UUID sourceCompensationEventId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> TARGET_FREQUENCIES =
      Set.of("ANNUAL", "MONTHLY", "HOURLY", "DAILY");

  public SalaryAssignmentWriteRequest(
      UUID payrollAssignmentVersionId,
      UUID salaryStructureVersionId,
      BigDecimal monthlyAmount,
      String currency,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    this(
        payrollAssignmentVersionId,
        salaryStructureVersionId,
        monthlyAmount,
        null,
        null,
        null,
        currency,
        null,
        effectiveFrom,
        effectiveTo);
  }

  public void validate() {
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    boolean legacy = monthlyAmount != null;
    boolean target = targetType != null || targetValue != null
        || targetFrequency != null || sourceCompensationEventId != null;
    if (legacy == target) {
      throw new IllegalArgumentException(
          "Provide either legacy monthlyAmount or the V050 target contract, not both");
    }
    if (legacy) {
      if (monthlyAmount.signum() < 0) {
        throw new IllegalArgumentException("monthlyAmount must not be negative");
      }
      if (currency != null && !"INR".equals(currency)) {
        throw new IllegalArgumentException(
            "Legacy salary-assignment compatibility supports INR only");
      }
      return;
    }
    if (targetType == null || targetType.isBlank()) {
      throw new IllegalArgumentException("targetType is required");
    }
    if (targetValue == null || targetValue.signum() < 0) {
      throw new IllegalArgumentException("targetValue must not be negative");
    }
    if (!TARGET_FREQUENCIES.contains(targetFrequency)) {
      throw new IllegalArgumentException("targetFrequency is unsupported");
    }
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency is required");
    }
    if (sourceCompensationEventId == null) {
      throw new IllegalArgumentException("sourceCompensationEventId is required");
    }
  }

  public boolean targetContract() {
    return monthlyAmount == null;
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }
}
