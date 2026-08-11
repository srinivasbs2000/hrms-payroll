package com.acme.hrms.payroll.security;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
public record ApprovalDelegationCreateRequest(
    @NotNull UUID sourceAuthorityId,
    @NotBlank @Size(max=160) String delegateActorId,
    @NotNull LocalDate effectiveFrom,
    @NotNull LocalDate effectiveTo) {
  public void validate() {
    if (sourceAuthorityId == null) throw new IllegalArgumentException("sourceAuthorityId is required");
    if (delegateActorId == null || delegateActorId.isBlank() || delegateActorId.length() > 160) {
      throw new IllegalArgumentException("delegateActorId must contain 1 to 160 characters");
    }
    if (effectiveFrom == null || effectiveTo == null) {
      throw new IllegalArgumentException("delegation effectiveFrom and effectiveTo are required");
    }
    if (!effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("delegation effectiveTo must be after effectiveFrom");
    }
  }
}
