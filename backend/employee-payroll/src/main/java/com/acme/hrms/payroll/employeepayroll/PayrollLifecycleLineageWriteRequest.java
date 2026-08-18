package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record PayrollLifecycleLineageWriteRequest(
    @NotBlank String eventType,
    @NotBlank String relationshipDecision,
    UUID predecessorRelationshipId,
    UUID successorRelationshipId,
    UUID predecessorAssignmentId,
    UUID successorAssignmentId,
    @NotNull LocalDate effectiveDate,
    @NotBlank String reason) {
  private static final Set<String> EVENTS =
      Set.of("TRANSFER", "REHIRE", "CONCURRENT_ASSIGNMENT");
  private static final Set<String> DECISIONS = Set.of("CONTINUE", "SUCCESSOR");

  public void validate() {
    if (!EVENTS.contains(eventType)) {
      throw new IllegalArgumentException("eventType is unsupported");
    }
    if (!DECISIONS.contains(relationshipDecision)) {
      throw new IllegalArgumentException("relationshipDecision is unsupported");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    if ("CONCURRENT_ASSIGNMENT".equals(eventType)) {
      if (!"CONTINUE".equals(relationshipDecision)
          || predecessorRelationshipId == null
          || successorRelationshipId == null
          || !predecessorRelationshipId.equals(successorRelationshipId)
          || predecessorAssignmentId == null
          || successorAssignmentId == null
          || predecessorAssignmentId.equals(successorAssignmentId)) {
        throw new IllegalArgumentException(
            "Concurrent assignment requires one continuing relationship and distinct assignment identities");
      }
    } else {
      if (predecessorRelationshipId == null || successorRelationshipId == null) {
        throw new IllegalArgumentException(
            "Transfer/rehire requires predecessor and successor relationship identities");
      }
      if ("CONTINUE".equals(relationshipDecision)
          != predecessorRelationshipId.equals(successorRelationshipId)) {
        throw new IllegalArgumentException(
            "CONTINUE must retain relationship identity; SUCCESSOR must use a distinct identity");
      }
    }
  }
}
