package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record EligibilityCriterionView(
    UUID id,
    int criterionSequence,
    String factKey,
    String factType,
    String comparisonOperator,
    JsonNode value,
    long versionNo) {}
