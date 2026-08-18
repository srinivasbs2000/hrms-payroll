package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record PayrollAssignmentWriteRequest(
    UUID payrollRelationshipId,
    String assignmentNumber,
    String sourceWorkAssignmentRef,
    @NotNull UUID payrollRelationshipVersionId,
    @NotNull UUID establishmentVersionId,
    String payrollRole,
    LocalDate payrollEligibilityFrom,
    LocalDate payrollEligibilityTo,
    @NotNull LocalDate assignmentStart,
    LocalDate assignmentEnd) {

  private static final Set<String> ROLES = Set.of("PRIMARY", "SECONDARY");

  public PayrollAssignmentWriteRequest(
      UUID payrollRelationshipId,
      String assignmentNumber,
      UUID payrollRelationshipVersionId,
      UUID establishmentVersionId,
      LocalDate assignmentStart,
      LocalDate assignmentEnd) {
    this(
        payrollRelationshipId,
        assignmentNumber,
        null,
        payrollRelationshipVersionId,
        establishmentVersionId,
        null,
        null,
        null,
        assignmentStart,
        assignmentEnd);
  }

  public void validate(boolean creatingIdentity) {
    if (creatingIdentity) {
      validateForCreate();
    } else {
      validateVersion();
    }
  }

  public void validateForCreate() {
    if (payrollRelationshipId == null) {
      throw new IllegalArgumentException("payrollRelationshipId is required");
    }
    if (assignmentNumber == null || assignmentNumber.isBlank()) {
      throw new IllegalArgumentException("assignmentNumber is required");
    }
    validateVersion();
  }

  public void validateVersion() {
    if (payrollRelationshipVersionId == null) {
      throw new IllegalArgumentException("payrollRelationshipVersionId is required");
    }
    if (establishmentVersionId == null) {
      throw new IllegalArgumentException("establishmentVersionId is required");
    }
    if (assignmentStart == null) {
      throw new IllegalArgumentException("assignmentStart is required");
    }
    if (assignmentEnd != null && !assignmentEnd.isAfter(assignmentStart)) {
      throw new IllegalArgumentException("assignmentEnd must be after assignmentStart");
    }
    boolean anyBinding = sourceWorkAssignmentRef != null
        || payrollRole != null
        || payrollEligibilityFrom != null
        || payrollEligibilityTo != null;
    if (anyBinding) {
      if (sourceWorkAssignmentRef == null || sourceWorkAssignmentRef.isBlank()) {
        throw new IllegalArgumentException("sourceWorkAssignmentRef is required for V050 binding");
      }
      if (!ROLES.contains(payrollRole)) {
        throw new IllegalArgumentException("payrollRole must be PRIMARY or SECONDARY");
      }
      if (payrollEligibilityFrom == null) {
        throw new IllegalArgumentException("payrollEligibilityFrom is required for V050 binding");
      }
      if (payrollEligibilityTo != null
          && !payrollEligibilityTo.isAfter(payrollEligibilityFrom)) {
        throw new IllegalArgumentException(
            "payrollEligibilityTo must be after payrollEligibilityFrom");
      }
    }
  }

  public boolean completeBinding() {
    return sourceWorkAssignmentRef != null
        && !sourceWorkAssignmentRef.isBlank()
        && ROLES.contains(payrollRole)
        && payrollEligibilityFrom != null;
  }
}
