package com.acme.hrms.payroll.organisation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AuthorityEvaluationView(
    boolean authorised,
    String reasonCode,
    String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    String purposeCode,
    String currencyCode,
    BigDecimal requestedAmount,
    LocalDate asOf,
    UUID signatoryIdentityId,
    UUID signatoryVersionId,
    String signatoryCode,
    String signatoryName,
    String scopeCurrencyCode,
    BigDecimal maximumAmount) {}
