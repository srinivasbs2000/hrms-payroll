package com.acme.hrms.payroll.compensation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SalaryStructureLifecycleControls {
  private SalaryStructureLifecycleControls() {}

  public record LifecycleCommentRequest(String comment) {
    public void validate() {
      if (comment != null && comment.trim().length() > 1000) {
        throw new IllegalArgumentException("comment must not exceed 1000 characters");
      }
    }
  }

  public record RejectionRequest(String reason) {
    public void validate() {
      if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
        throw new IllegalArgumentException(
            "rejection reason must contain between 1 and 1000 characters");
      }
    }
  }

  public record WorkflowActionView(
      UUID actionId,
      int actionSequence,
      String actionType,
      String actor,
      Instant occurredAt,
      String comment,
      String configurationHash,
      String validationFingerprint,
      long statutoryBindingRevision,
      String statutoryEvidenceHash,
      long structureVersionNo,
      String actionHash) {}

  public record LifecycleView(
      UUID identityId,
      UUID versionId,
      long versionNo,
      String workflowStatus,
      String approvalStatus,
      boolean publishedActive,
      Instant submittedAt,
      String submittedBy,
      Instant approvedAt,
      String approvedBy,
      Instant publishedAt,
      String publishedBy,
      String configurationHash,
      String validationFingerprint,
      long statutoryBindingRevision,
      List<WorkflowActionView> actions) {}
}
