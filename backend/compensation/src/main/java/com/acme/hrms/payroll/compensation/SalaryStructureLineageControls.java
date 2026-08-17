package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalaryStructureLineageControls {
  public static final String DISCLAIMER =
      "SALARY-STRUCTURE AUDIT / LINEAGE EVIDENCE — "
          + "READ ONLY; NOT AN EMPLOYEE PAYROLL, TAX OR STATUTORY RESULT";

  private SalaryStructureLineageControls() {}

  public record ValidationEvidenceView(
      UUID validationId,
      String validationStatus,
      String requestHash,
      String configurationHash,
      String resultHash,
      int blockingErrorCount,
      int warningCount,
      Instant createdAt,
      String createdBy) {}

  public record StatutoryEvidenceView(
      UUID evaluationId,
      UUID validationId,
      long statutoryBindingRevision,
      String validationStatus,
      int blockingIssueCount,
      int advisoryIssueCount,
      String evidenceHash,
      Instant createdAt,
      String createdBy) {}

  public record WorkflowEvidenceView(
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

  public record AuditEvidenceView(
      UUID auditEventId,
      Instant occurredAt,
      String actor,
      String action,
      UUID correlationId,
      JsonNode beforeState,
      JsonNode afterState,
      JsonNode metadata) {}

  public record DomainEventEvidenceView(
      UUID eventId,
      String eventType,
      int eventVersion,
      Instant occurredAt,
      UUID correlationId,
      UUID causationId,
      String status,
      JsonNode payload,
      JsonNode headers) {}

  public record LineageView(
      UUID identityId,
      UUID versionId,
      long versionNo,
      short structureSchemaVersion,
      String workflowStatus,
      String approvalStatus,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String configurationHash,
      String validationFingerprint,
      long statutoryBindingRevision,
      String currentStatutoryEvidenceHash,
      List<ValidationEvidenceView> validations,
      List<StatutoryEvidenceView> statutoryEvaluations,
      List<WorkflowEvidenceView> workflowActions,
      List<AuditEvidenceView> auditEvents,
      List<DomainEventEvidenceView> domainEvents,
      String lineageHash,
      String disclaimer) {}
}
