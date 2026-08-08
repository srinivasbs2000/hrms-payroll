package com.acme.hrms.payroll.organisation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JurisdictionOverrideView(
    UUID id,
    long versionNo,
    String targetKind,
    UUID workLocationVersionId,
    UUID establishmentVersionId,
    UUID payrollJurisdictionId,
    UUID payrollJurisdictionVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String reason,
    String approvalStatus,
    String createdBy,
    Instant approvedAt,
    String approvedBy) {}
