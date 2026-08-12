package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.Size;

public record PayrollCalendarLifecycleRequest(
    @Size(max = 500) String reason) {

  public void requireReason() {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
  }
}
