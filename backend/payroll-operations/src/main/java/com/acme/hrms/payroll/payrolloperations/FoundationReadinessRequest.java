package com.acme.hrms.payroll.payrolloperations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Caller-declared bounded requirements for one payroll-cycle foundation-readiness evaluation.
 *
 * <p>Owner identifiers and evaluation dates are derived from the payroll cycle.
 * The generic foundation does not infer country-specific legal registration obligations.
 */
public record FoundationReadinessRequest(
    @NotNull @Valid BankingRequirement banking,
    @NotNull List<@Valid RegistrationRequirement> registrations) {

  public FoundationReadinessRequest {
    if (registrations != null) {
      registrations = List.copyOf(registrations);
    }
  }

  public enum OwnerKind {
    LEGAL_ENTITY,
    PAYROLL_STATUTORY_UNIT
  }

  public record BankingRequirement(
      @NotNull OwnerKind ownerKind,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String purposeCode,
      @DecimalMin(value = "0", inclusive = false) BigDecimal amount) {}

  public record RegistrationRequirement(
      @NotNull UUID registrationTypeId,
      @NotNull OwnerKind ownerKind,
      @NotNull UUID payrollJurisdictionId,
      @Min(0) @Max(365) int warningHorizonDays) {}
}
