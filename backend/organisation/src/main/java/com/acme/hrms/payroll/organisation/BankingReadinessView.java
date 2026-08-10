package com.acme.hrms.payroll.organisation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BankingReadinessView(
    String readinessScope,
    String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    String currencyCode,
    String purposeCode,
    BigDecimal amount,
    LocalDate asOf,
    boolean bankReady,
    boolean signatoryReady,
    boolean ready,
    AuthorityEvaluationView authorityEvaluation,
    List<Finding> findings) {

  public BankingReadinessView {
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  public record Finding(
      String code,
      String source,
      String severity,
      String detail) {}
}
