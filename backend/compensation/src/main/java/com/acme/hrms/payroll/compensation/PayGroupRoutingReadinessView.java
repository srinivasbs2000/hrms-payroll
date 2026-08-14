package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayGroupRoutingReadinessView(
    UUID payrollAssignmentVersionId,
    UUID requestedPayGroupVersionId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    UUID payrollStatutoryUnitVersionId,
    UUID calendarId,
    String calendarFrequency,
    String calendarTimezone,
    PayGroupResolutionView resolutionAtEffectiveFrom,
    boolean compatible,
    boolean routingCoverageComplete,
    boolean routingMatchesRequestedPayGroup,
    boolean ready,
    List<PayGroupResolutionCheckpointView> resolutionCheckpoints,
    List<PayGroupCompatibilityIssueView> issues) {}
