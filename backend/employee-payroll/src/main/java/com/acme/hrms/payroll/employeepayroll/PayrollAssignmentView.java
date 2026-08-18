package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollAssignmentView(
    UUID identityId,
    UUID payrollRelationshipId,
    String assignmentNumber,
    String identityStatus,
    String sourceWorkAssignmentRef,
    UUID versionId,
    int versionSequence,
    long versionNo,
    UUID payrollRelationshipVersionId,
    UUID establishmentVersionId,
    String payrollRole,
    LocalDate payrollEligibilityFrom,
    LocalDate payrollEligibilityTo,
    LocalDate assignmentStart,
    LocalDate assignmentEnd,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded) {

  public PayrollAssignmentView(
      UUID identityId, UUID payrollRelationshipId, String assignmentNumber,
      String identityStatus, UUID versionId, int versionSequence, long versionNo,
      UUID payrollRelationshipVersionId, UUID establishmentVersionId,
      LocalDate assignmentStart, LocalDate assignmentEnd, String approvalStatus,
      UUID supersedesVersionId, boolean superseded) {
    this(identityId, payrollRelationshipId, assignmentNumber, identityStatus, null,
        versionId, versionSequence, versionNo, payrollRelationshipVersionId,
        establishmentVersionId, null, null, null, assignmentStart, assignmentEnd,
        approvalStatus, supersedesVersionId, superseded);
  }
}
