package com.acme.hrms.payroll.compensation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SalaryStructureWriteRequest(
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotBlank @Size(max = 160) String name,
    @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotEmpty List<@Valid SalaryStructureLineWriteRequest> lines) {

  public void validate(boolean identityCreation) {
    if (identityCreation && (code == null || code.isBlank())) {
      throw new IllegalArgumentException(
          "code is required when creating a salary-structure identity");
    }

    if (currency != null
        && !currency.isBlank()
        && !"INR".equals(currency)) {
      throw new IllegalArgumentException(
          "Only INR currency is supported");
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
    Set<UUID> components = new HashSet<>();

    for (SalaryStructureLineWriteRequest line : lines) {
      line.validate();

      if (!sequences.add(line.sequenceNo())) {
        throw new IllegalArgumentException(
            "Salary-structure line sequence numbers must be unique");
      }

      if (!components.add(line.componentVersionId())) {
        throw new IllegalArgumentException(
            "A pay-component version may appear only once "
                + "in a salary-structure version");
      }
    }
  }

  public String resolvedCurrency() {
    return currency == null || currency.isBlank() ? "INR" : currency;
  }
}