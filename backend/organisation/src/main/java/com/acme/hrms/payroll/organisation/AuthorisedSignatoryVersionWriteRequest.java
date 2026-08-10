package com.acme.hrms.payroll.organisation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

public record AuthorisedSignatoryVersionWriteRequest(
    @NotBlank @Size(max = 160) String fullName,
    @Size(max = 120) String designation,
    @NotBlank @Size(max = 240) String authorityReference,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotEmpty List<@Valid AuthorisedSignatoryScopeRequest> scopes) {

  public void validate() {
    if (fullName == null || fullName.isBlank()) {
      throw new IllegalArgumentException("fullName is required");
    }
    if (authorityReference == null || authorityReference.isBlank()) {
      throw new IllegalArgumentException("authorityReference is required");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    if (scopes == null || scopes.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one authorised-signatory scope is required");
    }

    HashSet<String> keys = new HashSet<>();
    for (AuthorisedSignatoryScopeRequest scope : scopes) {
      if (scope == null) {
        throw new IllegalArgumentException("Signatory scopes must not contain null values");
      }
      scope.validate();
      if (!keys.add(scope.scopeKey())) {
        throw new IllegalArgumentException(
            "Duplicate purpose/currency scope: " + scope.scopeKey());
      }
    }
  }
}
