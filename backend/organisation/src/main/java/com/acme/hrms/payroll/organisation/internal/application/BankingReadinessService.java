package com.acme.hrms.payroll.organisation.internal.application;

import com.acme.hrms.payroll.organisation.AuthorityEvaluationRequest;
import com.acme.hrms.payroll.organisation.AuthorityEvaluationView;
import com.acme.hrms.payroll.organisation.BankingReadinessView;
import com.acme.hrms.payroll.organisation.BankingReadinessView.Finding;
import com.acme.hrms.payroll.organisation.internal.infrastructure.BankingReadinessRepository;
import com.acme.hrms.payroll.organisation.internal.infrastructure.BankingReadinessRepository.BankStatus;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BankingReadinessService {
  private final BankingReadinessRepository repository;
  private final AuthorisedSignatoryService signatories;
  private final TenantTransactionExecutor transactions;
  private final Clock clock;

  public BankingReadinessService(
      BankingReadinessRepository repository,
      AuthorisedSignatoryService signatories,
      TenantTransactionExecutor transactions,
      Clock clock) {
    this.repository = repository;
    this.signatories = signatories;
    this.transactions = transactions;
    this.clock = clock;
  }

  public BankingReadinessView readiness(
      String ownerKind,
      UUID ownerId,
      String currencyCode,
      String purposeCode,
      BigDecimal amount,
      LocalDate asOf) {
    AuthorityEvaluationRequest request =
        authorityRequest(
            ownerKind,
            ownerId,
            currencyCode,
            purposeCode,
            amount,
            asOf);
    request.validate();
    LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;

    return transactions.read(
        () -> {
          BankStatus bank =
              repository.bankStatus(
                  request.ownerKey(),
                  currencyCode,
                  date);
          AuthorityEvaluationView authority =
              signatories.evaluateWithinTransaction(request, date);

          List<Finding> findings = new ArrayList<>();
          if (!bank.configured()) {
            findings.add(
                blocker(
                    "BANK_ACCOUNT_MISSING",
                    "BANK_ACCOUNT",
                    "No employer bank account is configured for the required owner and currency"));
          } else if (!bank.active()) {
            findings.add(
                blocker(
                    "BANK_ACCOUNT_NOT_ACTIVE",
                    "BANK_ACCOUNT",
                    "No approved active employer bank account is effective for the requested date"));
          } else if (!bank.activeDefault()) {
            findings.add(
                blocker(
                    "DEFAULT_BANK_ACCOUNT_MISSING",
                    "BANK_ACCOUNT",
                    "No active default employer bank account is configured for the required owner and currency"));
          }

          if (!authority.authorised()) {
            findings.add(
                authorityFinding(
                    authority.reasonCode(),
                    repository.signatoryConfigured(request.ownerKey())));
          }

          boolean bankReady = bank.active() && bank.activeDefault();
          boolean signatoryReady = authority.authorised();
          return new BankingReadinessView(
              "BANKING_AND_SIGNATORY_ONLY",
              request.ownerKind(),
              request.legalEntityId(),
              request.payrollStatutoryUnitId(),
              currencyCode,
              purposeCode,
              amount,
              date,
              bankReady,
              signatoryReady,
              bankReady && signatoryReady,
              authority,
              List.copyOf(findings));
        });
  }

  private AuthorityEvaluationRequest authorityRequest(
      String ownerKind,
      UUID ownerId,
      String currencyCode,
      String purposeCode,
      BigDecimal amount,
      LocalDate asOf) {
    UUID legal = "LEGAL_ENTITY".equals(ownerKind) ? ownerId : null;
    UUID psu =
        "PAYROLL_STATUTORY_UNIT".equals(ownerKind)
            ? ownerId
            : null;
    return new AuthorityEvaluationRequest(
        ownerKind,
        legal,
        psu,
        purposeCode,
        currencyCode,
        amount,
        asOf);
  }

  private Finding authorityFinding(
      String reasonCode,
      boolean configured) {
    if ("NO_ACTIVE_SIGNATORY".equals(reasonCode)) {
      return configured
          ? blocker(
              "SIGNATORY_AUTHORITY_NOT_ACTIVE",
              "SIGNATORY",
              "Signatory configuration exists but no approved active authority is effective for the requested date")
          : blocker(
              "SIGNATORY_AUTHORITY_MISSING",
              "SIGNATORY",
              "No authorised signatory is configured for the required owner");
    }

    return switch (reasonCode) {
      case "PURPOSE_NOT_AUTHORIZED" ->
          blocker(
              "SIGNATORY_PURPOSE_MISMATCH",
              "SIGNATORY",
              "No active signatory has the required authority purpose");
      case "CURRENCY_MISMATCH" ->
          blocker(
              "SIGNATORY_CURRENCY_MISMATCH",
              "SIGNATORY",
              "No active purpose scope authorises the requested currency");
      case "AMOUNT_REQUIRED_FOR_LIMITED_SCOPE" ->
          blocker(
              "SIGNATORY_AMOUNT_REQUIRED",
              "SIGNATORY",
              "An amount is required because all matching authority scopes are limited");
      case "AMOUNT_EXCEEDS_LIMIT" ->
          blocker(
              "SIGNATORY_AMOUNT_LIMIT_EXCEEDED",
              "SIGNATORY",
              "The requested amount exceeds every matching delegated-authority limit");
      default ->
          blocker(
              "SIGNATORY_AUTHORITY_NOT_READY",
              "SIGNATORY",
              "Authorised-signatory authority is not ready");
    };
  }

  private Finding blocker(
      String code,
      String source,
      String detail) {
    return new Finding(code, source, "BLOCKER", detail);
  }
}
