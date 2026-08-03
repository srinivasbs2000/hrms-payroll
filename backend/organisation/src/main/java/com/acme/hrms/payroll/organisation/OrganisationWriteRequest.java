package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record OrganisationWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 200) String name,
    @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @Pattern(regexp = "^[A-Z0-9]{2,3}$") String stateCode,
    UUID parentVersionId,
    @Pattern(regexp = "^(TAX_AND_STATUTORY|TAX_ONLY|STATUTORY_ONLY|PAYROLL_OPERATIONS)$")
        String responsibilityScope,
    @Pattern(regexp = "^(OFFICE|BRANCH|FACTORY|SHOP|CONSTRUCTION|OTHER)$")
        String establishmentType,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validateFor(OrganisationKind kind, boolean identityCreation) {
    if (identityCreation && (code == null || code.isBlank())) {
      throw new IllegalArgumentException("code is required when creating an identity");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }

    switch (kind) {
      case LEGAL_ENTITY -> {
        if (parentVersionId != null || stateCode != null
            || responsibilityScope != null || establishmentType != null) {
          throw new IllegalArgumentException(
              "legal entity requests cannot contain parent, state or classification fields");
        }
      }
      case PAYROLL_STATUTORY_UNIT -> {
        if (parentVersionId == null) {
          throw new IllegalArgumentException("parentVersionId is required");
        }
        if (countryCode != null || currency != null
            || stateCode != null || establishmentType != null) {
          throw new IllegalArgumentException(
              "payroll statutory unit requests can contain only PSU hierarchy and classification fields");
        }
      }
      case ESTABLISHMENT -> {
        if (parentVersionId == null) {
          throw new IllegalArgumentException("parentVersionId is required");
        }
        if (stateCode == null || stateCode.isBlank()) {
          throw new IllegalArgumentException("stateCode is required for an establishment");
        }
        if (countryCode != null || currency != null
            || responsibilityScope != null) {
          throw new IllegalArgumentException(
              "establishment requests cannot contain legal-entity or PSU classification fields");
        }
      }
    }
  }
}
