package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AuthorityEvaluationRequest(
    @NotBlank
        @Pattern(regexp = "^(LEGAL_ENTITY|PAYROLL_STATUTORY_UNIT)$")
        String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String purposeCode,
    @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
    @DecimalMin(value = "0.0001") BigDecimal amount,
    LocalDate asOf) {

  public void validate() {
    boolean legal = "LEGAL_ENTITY".equals(ownerKind);
    boolean psu = "PAYROLL_STATUTORY_UNIT".equals(ownerKind);
    if (!legal && !psu) {
      throw new IllegalArgumentException(
          "ownerKind must be LEGAL_ENTITY or PAYROLL_STATUTORY_UNIT");
    }
    if (legal && (legalEntityId == null || payrollStatutoryUnitId != null)) {
      throw new IllegalArgumentException(
          "LEGAL_ENTITY owner requires only legalEntityId");
    }
    if (psu && (payrollStatutoryUnitId == null || legalEntityId != null)) {
      throw new IllegalArgumentException(
          "PAYROLL_STATUTORY_UNIT owner requires only payrollStatutoryUnitId");
    }
    if (purposeCode == null
        || !purposeCode.matches("^[A-Z][A-Z0-9_]{1,59}$")) {
      throw new IllegalArgumentException("purposeCode is invalid");
    }
    if (currencyCode != null && !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException(
          "currencyCode must be a three-letter uppercase code");
    }
    if (amount != null && amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    if (amount != null && currencyCode == null) {
      throw new IllegalArgumentException(
          "currencyCode is required when amount is supplied");
    }
  }

  public UUID ownerId() {
    return "LEGAL_ENTITY".equals(ownerKind)
        ? legalEntityId
        : payrollStatutoryUnitId;
  }

  public String ownerKey() {
    return ownerKind + ":" + ownerId();
  }
}
