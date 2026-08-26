package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmployeePayrollHoldModels {
  private EmployeePayrollHoldModels() {}

  public record PayrollHoldWriteRequest(
      @NotNull UUID holdId,
      @NotNull UUID versionId,
      @NotEmpty List<@NotBlank String> scopes,
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$") String reasonCode,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 240) String sourceReference,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record PayrollHoldEvidenceRequest(
      @NotBlank @Size(max = 240) String evidenceRef) {}

  public record PayrollHoldView(
      UUID holdId,
      UUID versionId,
      UUID payrollRelationshipId,
      int versionSequence,
      String reasonCode,
      String reason,
      String sourceReference,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String lifecycleStatus,
      Instant approvedAt,
      String approvedBy,
      Instant releasedAt,
      String releasedBy,
      long versionNo,
      List<String> scopes) {}
}
