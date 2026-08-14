package com.acme.hrms.payroll.compensation;

import java.util.UUID;

public record PayGroupResolutionView(
    UUID payGroupVersionId,
    String resolutionSource,
    UUID routingRuleId) {}
