package com.acme.hrms.payroll.payrolloperations.internal.application;

import com.acme.hrms.payroll.organisation.BankingReadinessFacade;
import com.acme.hrms.payroll.organisation.BankingReadinessView;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest.OwnerKind;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessView;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessView.Dimension;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessView.Finding;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessView.RegistrationCheck;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.FoundationReadinessRepository;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.FoundationReadinessRepository.FoundationContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import com.acme.hrms.payroll.statutory.RegistrationReadinessFacade;
import com.acme.hrms.payroll.statutory.RegistrationReadinessRequest;
import com.acme.hrms.payroll.statutory.RegistrationReadinessView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FoundationReadinessService {
  private static final String SCOPE = "FOUNDATION_ONLY";
  private static final String REGISTRATION_COVERAGE =
      "CALLER_DECLARED_REQUIREMENTS_ONLY";
  private static final List<String> EXCLUDED_CAPABILITIES =
      List.of(
          "COUNTRY_SPECIFIC_STATUTORY_RULES_RATES",
          "EMPLOYEE_BANK_ACCOUNTS",
          "PAYMENT_EXECUTION_BANK_INTEGRATION",
          "RETRO_OFF_CYCLE_FINAL_SETTLEMENT",
          "ACCOUNTING_ERP_POSTING",
          "MIGRATION_CUTOVER_PRODUCTION_OPERATIONS");

  private final FoundationReadinessRepository repository;
  private final BankingReadinessFacade banking;
  private final RegistrationReadinessFacade registrations;
  private final TenantTransactionExecutor transactions;

  public FoundationReadinessService(
      FoundationReadinessRepository repository,
      BankingReadinessFacade banking,
      RegistrationReadinessFacade registrations,
      TenantTransactionExecutor transactions) {
    this.repository = repository;
    this.banking = banking;
    this.registrations = registrations;
    this.transactions = transactions;
  }

  public FoundationReadinessView evaluate(
      UUID cycleId, FoundationReadinessRequest request) {
    FoundationContext context =
        transactions.read(() -> repository.context(cycleId));

    List<Finding> findings = new ArrayList<>();
    List<Dimension> dimensions = new ArrayList<>();
    List<RegistrationCheck> registrationChecks = new ArrayList<>();

    boolean configurationReady = configurationReady(context);
    if (!configurationReady) {
      findings.add(
          blocker(
              "FOUNDATION_CONFIGURATION_NOT_SEALED",
              "CONFIGURATION_SNAPSHOT",
              "The payroll cycle is not bound to a complete immutable foundation configuration snapshot"));
    }
    dimensions.add(
        dimension(
            "CONFIGURATION_SNAPSHOT",
            configurationReady,
            findings,
            "CONFIGURATION_SNAPSHOT",
            "EXACT_CYCLE_SNAPSHOT_ONLY"));

    UUID bankingOwnerId = ownerId(context, request.banking().ownerKind());
    BankingReadinessView bankingView =
        banking.evaluate(
            request.banking().ownerKind().name(),
            bankingOwnerId,
            request.banking().currencyCode(),
            request.banking().purposeCode(),
            request.banking().amount(),
            context.paymentDate());

    bankingView.findings().stream()
        .map(
            finding ->
                new Finding(
                    finding.code(),
                    finding.source(),
                    finding.severity(),
                    finding.detail()))
        .forEach(findings::add);

    dimensions.add(
        dimension(
            "BANK_ACCOUNT",
            bankingView.bankReady(),
            findings,
            "BANK_ACCOUNT",
            "BANKING_AND_SIGNATORY_ONLY"));
    dimensions.add(
        dimension(
            "SIGNATORY_AUTHORITY",
            bankingView.signatoryReady(),
            findings,
            "SIGNATORY",
            "BANKING_AND_SIGNATORY_ONLY"));

    boolean registrationsReady = true;
    for (FoundationReadinessRequest.RegistrationRequirement requirement :
        request.registrations()) {
      UUID registrationOwnerId = ownerId(context, requirement.ownerKind());
      RegistrationReadinessView readiness =
          registrations.evaluate(
              new RegistrationReadinessRequest(
                  requirement.registrationTypeId(),
                  RegistrationOwnerKind.valueOf(requirement.ownerKind().name()),
                  registrationOwnerId,
                  requirement.payrollJurisdictionId(),
                  context.periodEnd(),
                  requirement.warningHorizonDays()));

      registrationsReady &= readiness.ready();
      registrationChecks.add(
          new RegistrationCheck(
              readiness.registrationTypeId(),
              readiness.ownerKind().name(),
              readiness.ownerId(),
              readiness.payrollJurisdictionId(),
              readiness.asOf(),
              readiness.ready(),
              readiness.registrationVersionId()));
      readiness.findings().stream()
          .map(
              finding ->
                  new Finding(
                      finding.code(),
                      "JURISDICTION_REGISTRATION",
                      finding.severity(),
                      finding.message()))
          .forEach(findings::add);
    }

    dimensions.add(
        dimension(
            "JURISDICTION_REGISTRATION",
            registrationsReady,
            findings,
            "JURISDICTION_REGISTRATION",
            REGISTRATION_COVERAGE));

    boolean foundationReady =
        configurationReady
            && bankingView.bankReady()
            && bankingView.signatoryReady()
            && registrationsReady;
    String readinessStatus =
        foundationReady
            ? (findings.stream().anyMatch(finding -> "WARNING".equals(finding.severity()))
                ? "READY_WITH_WARNINGS"
                : "READY")
            : "BLOCKED";

    return new FoundationReadinessView(
        SCOPE,
        context.payrollCycleId(),
        context.cycleStatus(),
        context.payGroupVersionId(),
        context.payrollStatutoryUnitVersionId(),
        context.payrollStatutoryUnitId(),
        context.legalEntityVersionId(),
        context.legalEntityId(),
        context.periodStart(),
        context.periodEnd(),
        context.paymentDate(),
        context.snapshotId(),
        context.snapshotHash(),
        context.snapshotCount(),
        context.snapshotSealedAt(),
        foundationReady,
        readinessStatus,
        dimensions,
        registrationChecks,
        findings,
        EXCLUDED_CAPABILITIES);
  }

  private boolean configurationReady(FoundationContext context) {
    return context.snapshotId() != null
        && context.snapshotHash() != null
        && context.snapshotHash().matches("[0-9a-f]{64}")
        && context.snapshotCount() != null
        && context.snapshotCount() >= 6
        && context.snapshotSealedAt() != null;
  }

  private UUID ownerId(FoundationContext context, OwnerKind ownerKind) {
    return switch (ownerKind) {
      case LEGAL_ENTITY -> context.legalEntityId();
      case PAYROLL_STATUTORY_UNIT -> context.payrollStatutoryUnitId();
    };
  }

  private Dimension dimension(
      String code,
      boolean ready,
      List<Finding> findings,
      String source,
      String coverage) {
    int blockers =
        (int)
            findings.stream()
                .filter(finding -> source.equals(finding.source()))
                .filter(finding -> "BLOCKER".equals(finding.severity()))
                .count();
    int warnings =
        (int)
            findings.stream()
                .filter(finding -> source.equals(finding.source()))
                .filter(finding -> "WARNING".equals(finding.severity()))
                .count();
    String status =
        ready ? (warnings > 0 ? "READY_WITH_WARNINGS" : "READY") : "BLOCKED";
    return new Dimension(code, ready, status, blockers, warnings, coverage);
  }

  private Finding blocker(String code, String source, String detail) {
    return new Finding(code, source, "BLOCKER", detail);
  }
}
