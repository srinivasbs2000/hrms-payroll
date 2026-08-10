package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record AuthorisedSignatoryScopeRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String purposeCode,
    @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
    @DecimalMin(value = "0.0001") BigDecimal maximumAmount) {

  public void validate() {
    if (purposeCode == null
        || !purposeCode.matches("^[A-Z][A-Z0-9_]{1,59}$")) {
      throw new IllegalArgumentException(
          "purposeCode must use uppercase letters, numbers, and underscores");
    }
    if (currencyCode != null && !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException(
          "currencyCode must be a three-letter uppercase code");
    }
    if (maximumAmount != null && maximumAmount.signum() <= 0) {
      throw new IllegalArgumentException("maximumAmount must be greater than zero");
    }
    if (maximumAmount != null && currencyCode == null) {
      throw new IllegalArgumentException(
          "currencyCode is required when maximumAmount is configured");
    }
  }

  public String scopeKey() {
    return purposeCode + "|" + (currencyCode == null ? "*" : currencyCode);
  }
}
