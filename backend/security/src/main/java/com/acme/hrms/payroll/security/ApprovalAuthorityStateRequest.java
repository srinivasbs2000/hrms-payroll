package com.acme.hrms.payroll.security;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ApprovalAuthorityStateRequest(@NotBlank @Size(max=500) String reason) {
  public void validate() {
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new IllegalArgumentException("reason must contain 1 to 500 characters");
    }
  }
}
