package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ComponentBaseMembershipWriteRequest(
    @NotNull UUID payrollBaseVersionId,
    @NotNull UUID componentId,
    @NotNull UUID componentVersionId,
    @NotBlank String membershipType,
    @NotNull @DecimalMin(value = "0.00000001") @DecimalMax("100.00000000")
        @Digits(integer = 3, fraction = 8)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal inclusionPercent,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  private static final Set<String> TYPES = Set.of(
      "INCLUDE", "EXCLUDE", "ADD_BACK", "ELIGIBILITY_ONLY",
      "CONTRIBUTION_ONLY", "NOTIONAL");

  public void validate() {
    if (!TYPES.contains(membershipType)) {
      throw new IllegalArgumentException("membershipType contains an unsupported value");
    }
    if (inclusionPercent == null
        || inclusionPercent.signum() <= 0
        || inclusionPercent.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException(
          "inclusionPercent must be greater than 0 and not exceed 100");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
  }
  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException("Unknown request field: " + property);
  }

}
