package com.acme.hrms.payroll.compensation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record PayrollBaseCreateRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String code,
    @NotBlank @Size(max = 160) String name,
    String ownershipScope,
    @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
    Boolean protectedFlag,
    String confidentialityLevel,
    @NotNull @Valid PayrollBaseVersionWriteRequest version) {

  private static final Set<String> OWNERSHIP_SCOPES =
      Set.of("SYSTEM", "COUNTRY_PACK", "TENANT");
  private static final Set<String> CONFIDENTIALITY_LEVELS =
      Set.of("STANDARD", "RESTRICTED", "EXECUTIVE");

  public void validate() {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (!OWNERSHIP_SCOPES.contains(resolvedOwnershipScope())) {
      throw new IllegalArgumentException("ownershipScope contains an unsupported value");
    }
    if (!CONFIDENTIALITY_LEVELS.contains(resolvedConfidentialityLevel())) {
      throw new IllegalArgumentException(
          "confidentialityLevel contains an unsupported value");
    }
    if (version == null) {
      throw new IllegalArgumentException("version is required");
    }
    version.validate();
  }

  public String resolvedOwnershipScope() {
    return ownershipScope == null || ownershipScope.isBlank()
        ? "TENANT"
        : ownershipScope;
  }

  public boolean resolvedProtectedFlag() {
    return Boolean.TRUE.equals(protectedFlag);
  }

  public String resolvedConfidentialityLevel() {
    return confidentialityLevel == null || confidentialityLevel.isBlank()
        ? "STANDARD"
        : confidentialityLevel;
  }
}
