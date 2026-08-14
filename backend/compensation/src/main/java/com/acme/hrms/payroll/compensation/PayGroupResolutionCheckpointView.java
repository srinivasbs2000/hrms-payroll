package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.UUID;

public record PayGroupResolutionCheckpointView(
    LocalDate asOf,
    UUID payGroupVersionId,
    String resolutionSource,
    UUID routingRuleId,
    boolean matchesRequestedPayGroup) {}
