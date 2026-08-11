package com.acme.hrms.payroll.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ApprovalAuthorityAssignmentCreateRequest(
    @NotNull ApprovalOwnerKind ownerKind,
    @NotNull UUID ownerId,
    @NotNull ApprovalRole approvalRole,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$") String domainCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$") String actionCode,
    @NotBlank @Size(max = 160) String actorId,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {
  public void validate() {
    if (ownerKind == null || ownerId == null || approvalRole == null) {
      throw new IllegalArgumentException("ownerKind, ownerId and approvalRole are required");
    }
    validateCode("domainCode", domainCode);
    validateCode("actionCode", actionCode);
    if (actorId == null || actorId.isBlank() || actorId.length() > 160) {
      throw new IllegalArgumentException("actorId must contain 1 to 160 characters");
    }
    if (effectiveFrom == null) throw new IllegalArgumentException("effectiveFrom is required");
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    if (approvalRole == ApprovalRole.FINAL_APPROVER && actorId.startsWith("service:")) {
      throw new IllegalArgumentException(
          "service identities cannot receive interactive final-approval authority");
    }
  }
  static void validateCode(String field, String value) {
    if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,79}$")) {
      throw new IllegalArgumentException(field + " must use the uppercase approval-code vocabulary");
    }
  }
}
