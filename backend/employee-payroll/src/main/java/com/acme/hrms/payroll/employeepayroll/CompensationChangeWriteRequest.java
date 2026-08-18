package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CompensationChangeWriteRequest(
    @NotNull UUID payrollAssignmentId,
    @NotBlank String eventType,
    @NotNull LocalDate effectiveDate,
    UUID sourceEventId,
    @NotBlank String reason) {
  private static final Set<String> TYPES = Set.of(
      "PROSPECTIVE", "CURRENT_PERIOD", "RETROSPECTIVE", "CORRECTION", "REVERSAL");

  public void validate() {
    if (!TYPES.contains(eventType)) {
      throw new IllegalArgumentException("eventType is unsupported");
    }
    boolean lineageRequired = "CORRECTION".equals(eventType) || "REVERSAL".equals(eventType);
    if (lineageRequired != (sourceEventId != null)) {
      throw new IllegalArgumentException(
          "CORRECTION/REVERSAL require sourceEventId and other event types must not provide it");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
  }
}
