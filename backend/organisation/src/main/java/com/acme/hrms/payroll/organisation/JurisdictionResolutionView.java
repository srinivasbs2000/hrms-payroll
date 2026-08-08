package com.acme.hrms.payroll.organisation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JurisdictionResolutionView(
    UUID evidenceId,
    LocalDate asOf,
    UUID workLocationVersionId,
    UUID establishmentVersionId,
    UUID overrideId,
    UUID resolvedJurisdictionId,
    UUID resolvedJurisdictionVersionId,
    String resolutionSource,
    String resolutionStatus,
    String inputFingerprint,
    String resultFingerprint,
    List<JurisdictionFindingView> findings) {}
