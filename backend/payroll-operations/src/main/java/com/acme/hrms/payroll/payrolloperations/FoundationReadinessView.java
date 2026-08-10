package com.acme.hrms.payroll.payrolloperations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FoundationReadinessView(
    String readinessScope,
    UUID payrollCycleId,
    String cycleStatus,
    UUID payGroupVersionId,
    UUID payrollStatutoryUnitVersionId,
    UUID payrollStatutoryUnitId,
    UUID legalEntityVersionId,
    UUID legalEntityId,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    UUID foundationConfigurationSnapshotId,
    String foundationConfigurationSnapshotHash,
    Integer foundationConfigurationCount,
    Instant foundationConfigurationSealedAt,
    boolean foundationReady,
    String readinessStatus,
    List<Dimension> dimensions,
    List<RegistrationCheck> registrationChecks,
    List<Finding> findings,
    List<String> excludedCapabilities) {

  public FoundationReadinessView {
    dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    registrationChecks =
        registrationChecks == null ? List.of() : List.copyOf(registrationChecks);
    findings = findings == null ? List.of() : List.copyOf(findings);
    excludedCapabilities =
        excludedCapabilities == null ? List.of() : List.copyOf(excludedCapabilities);
  }

  public record Dimension(
      String code,
      boolean ready,
      String status,
      int blockerCount,
      int warningCount,
      String coverage) {}

  public record RegistrationCheck(
      UUID registrationTypeId,
      String ownerKind,
      UUID ownerId,
      UUID payrollJurisdictionId,
      LocalDate asOf,
      boolean ready,
      UUID registrationVersionId) {}

  public record Finding(
      String code,
      String source,
      String severity,
      String detail) {}
}
