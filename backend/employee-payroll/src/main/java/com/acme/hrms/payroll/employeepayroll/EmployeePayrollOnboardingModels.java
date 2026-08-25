package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class EmployeePayrollOnboardingModels {
  private EmployeePayrollOnboardingModels() {}

  public record OnboardingCreateRequest(
      @NotNull UUID caseId,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 240) String evidenceRef) {}

  public record OnboardingTransitionRequest(
      @NotBlank @Size(max = 24) String targetStatus,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 240) String evidenceRef,
      LocalDate asOf) {}

  public record OnboardingCaseView(
      UUID id,
      UUID payrollRelationshipId,
      String currentStatus,
      Instant createdAt,
      String createdBy,
      Instant updatedAt,
      String updatedBy,
      long versionNo) {}

  public record OnboardingEventView(
      UUID id,
      UUID onboardingCaseId,
      UUID payrollRelationshipId,
      int eventSequence,
      String fromStatus,
      String toStatus,
      String reason,
      String evidenceRef,
      Instant occurredAt,
      String actor) {}
}
