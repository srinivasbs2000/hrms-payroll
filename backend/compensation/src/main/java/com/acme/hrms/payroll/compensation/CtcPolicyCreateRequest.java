package com.acme.hrms.payroll.compensation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CtcPolicyCreateRequest(
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
    @NotNull @Valid CtcPolicyVersionWriteRequest version) {

  public void validate() {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code is required");
    }
    if (version == null) {
      throw new IllegalArgumentException("version is required");
    }
    version.validate();
  }
}
