package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmployeePayrollReadinessModels {
  private EmployeePayrollReadinessModels() {}

  public record ReadinessFindingView(
      String dimension,
      String severity,
      String status,
      String findingCode,
      String detail,
      String sourceKind,
      String sourceReference) {}

  public record ReadinessView(
      UUID payrollRelationshipId,
      String currencyCode,
      LocalDate asOf,
      boolean ready,
      List<ReadinessFindingView> findings) {}

  public record ReadinessPolicyWriteRequest(
      @NotNull UUID versionId,
      @NotBlank @Size(max = 24) String dimension,
      @NotBlank @Size(max = 32) String applicability,
      @NotBlank @Size(max = 20) String severity,
      @NotBlank @Size(max = 240) String evidenceRef,
      @NotBlank @Size(max = 500) String reason,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record ReadinessPolicyView(
      UUID id,
      String dimension,
      int versionSequence,
      String applicability,
      String severity,
      String evidenceRef,
      String reason,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      UUID supersedesVersionId,
      Instant approvedAt,
      String approvedBy) {}
}
