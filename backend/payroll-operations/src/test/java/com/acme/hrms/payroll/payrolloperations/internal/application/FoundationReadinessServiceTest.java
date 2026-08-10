package com.acme.hrms.payroll.payrolloperations.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.hrms.payroll.organisation.BankingReadinessFacade;
import com.acme.hrms.payroll.organisation.BankingReadinessView;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest.BankingRequirement;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest.OwnerKind;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessRequest.RegistrationRequirement;
import com.acme.hrms.payroll.payrolloperations.FoundationReadinessView;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.FoundationReadinessRepository;
import com.acme.hrms.payroll.payrolloperations.internal.infrastructure.FoundationReadinessRepository.FoundationContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.statutory.RegistrationOwnerKind;
import com.acme.hrms.payroll.statutory.RegistrationReadinessFacade;
import com.acme.hrms.payroll.statutory.RegistrationReadinessFindingView;
import com.acme.hrms.payroll.statutory.RegistrationReadinessRequest;
import com.acme.hrms.payroll.statutory.RegistrationReadinessView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FoundationReadinessServiceTest {
  private static final UUID CYCLE =
      UUID.fromString("81000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION =
      UUID.fromString("81100000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION =
      UUID.fromString("81200000-0000-0000-0000-000000000001");
  private static final UUID PSU =
      UUID.fromString("81300000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION =
      UUID.fromString("81400000-0000-0000-0000-000000000001");
  private static final UUID LEGAL =
      UUID.fromString("81500000-0000-0000-0000-000000000001");
  private static final UUID SNAPSHOT =
      UUID.fromString("81600000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_TYPE =
      UUID.fromString("81700000-0000-0000-0000-000000000001");
  private static final UUID JURISDICTION =
      UUID.fromString("81800000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_VERSION =
      UUID.fromString("81900000-0000-0000-0000-000000000001");

  private FoundationReadinessRepository repository;
  private BankingReadinessFacade banking;
  private RegistrationReadinessFacade registrations;
  private TenantTransactionExecutor transactions;
  private FoundationReadinessService service;

  @BeforeEach
  void setUp() {
    repository = mock(FoundationReadinessRepository.class);
    banking = mock(BankingReadinessFacade.class);
    registrations = mock(RegistrationReadinessFacade.class);
    transactions = mock(TenantTransactionExecutor.class);
    when(transactions.read(any()))
        .thenAnswer(
            invocation -> {
              Supplier<?> supplier = invocation.getArgument(0);
              return supplier.get();
            });
    service =
        new FoundationReadinessService(
            repository, banking, registrations, transactions);
  }

  @Test
  void composesExactCycleContextAndPreservesWarningsWithoutOverclaim() {
    when(repository.context(CYCLE)).thenReturn(sealedContext());
    when(banking.evaluate(
            "LEGAL_ENTITY",
            LEGAL,
            "INR",
            "PAYROLL_FUNDING",
            new BigDecimal("500000.00"),
            LocalDate.of(2026, 8, 31)))
        .thenReturn(
            new BankingReadinessView(
                "BANKING_AND_SIGNATORY_ONLY",
                "LEGAL_ENTITY",
                LEGAL,
                null,
                "INR",
                "PAYROLL_FUNDING",
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 8, 31),
                true,
                true,
                true,
                null,
                List.of()));

    when(registrations.evaluate(any(RegistrationReadinessRequest.class)))
        .thenReturn(
            new RegistrationReadinessView(
                REGISTRATION_TYPE,
                RegistrationOwnerKind.PAYROLL_STATUTORY_UNIT,
                PSU,
                JURISDICTION,
                LocalDate.of(2026, 8, 31),
                true,
                REGISTRATION_VERSION,
                List.of(
                    new RegistrationReadinessFindingView(
                        "REGISTRATION_EXPIRING_SOON",
                        "WARNING",
                        "The registration expires within the configured warning horizon"))));

    FoundationReadinessView result =
        service.evaluate(
            CYCLE,
            new FoundationReadinessRequest(
                new BankingRequirement(
                    OwnerKind.LEGAL_ENTITY,
                    "INR",
                    "PAYROLL_FUNDING",
                    new BigDecimal("500000.00")),
                List.of(
                    new RegistrationRequirement(
                        REGISTRATION_TYPE,
                        OwnerKind.PAYROLL_STATUTORY_UNIT,
                        JURISDICTION,
                        45))));

    assertThat(result.readinessScope()).isEqualTo("FOUNDATION_ONLY");
    assertThat(result.foundationReady()).isTrue();
    assertThat(result.readinessStatus()).isEqualTo("READY_WITH_WARNINGS");
    assertThat(result.foundationConfigurationSnapshotId()).isEqualTo(SNAPSHOT);
    assertThat(result.excludedCapabilities())
        .contains(
            "COUNTRY_SPECIFIC_STATUTORY_RULES_RATES",
            "PAYMENT_EXECUTION_BANK_INTEGRATION");
    assertThat(result.findings())
        .extracting(FoundationReadinessView.Finding::code)
        .containsExactly("REGISTRATION_EXPIRING_SOON");
    assertThat(result.registrationChecks()).hasSize(1);

    ArgumentCaptor<RegistrationReadinessRequest> request =
        ArgumentCaptor.forClass(RegistrationReadinessRequest.class);
    verify(registrations).evaluate(request.capture());
    assertThat(request.getValue().ownerId()).isEqualTo(PSU);
    assertThat(request.getValue().asOf()).isEqualTo(LocalDate.of(2026, 8, 31));
  }

  @Test
  void blocksWhenSnapshotBankSignatoryAndRegistrationAreNotReady() {
    FoundationContext unsealed =
        new FoundationContext(
            CYCLE,
            "POPULATION_RESOLVED",
            PAY_GROUP_VERSION,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2026, 8, 31),
            PSU_VERSION,
            PSU,
            LEGAL_VERSION,
            LEGAL,
            null,
            null,
            null,
            null);
    when(repository.context(CYCLE)).thenReturn(unsealed);

    when(banking.evaluate(
            "PAYROLL_STATUTORY_UNIT",
            PSU,
            "INR",
            "PAYROLL_FUNDING",
            null,
            LocalDate.of(2026, 8, 31)))
        .thenReturn(
            new BankingReadinessView(
                "BANKING_AND_SIGNATORY_ONLY",
                "PAYROLL_STATUTORY_UNIT",
                null,
                PSU,
                "INR",
                "PAYROLL_FUNDING",
                null,
                LocalDate.of(2026, 8, 31),
                false,
                false,
                false,
                null,
                List.of(
                    new BankingReadinessView.Finding(
                        "BANK_ACCOUNT_MISSING",
                        "BANK_ACCOUNT",
                        "BLOCKER",
                        "No employer bank account is configured"),
                    new BankingReadinessView.Finding(
                        "SIGNATORY_AUTHORITY_MISSING",
                        "SIGNATORY",
                        "BLOCKER",
                        "No authorised signatory is configured"))));

    when(registrations.evaluate(any(RegistrationReadinessRequest.class)))
        .thenReturn(
            new RegistrationReadinessView(
                REGISTRATION_TYPE,
                RegistrationOwnerKind.LEGAL_ENTITY,
                LEGAL,
                JURISDICTION,
                LocalDate.of(2026, 8, 31),
                false,
                null,
                List.of(
                    new RegistrationReadinessFindingView(
                        "REGISTRATION_MISSING",
                        "BLOCKER",
                        "No effective active registration exists"))));

    FoundationReadinessView result =
        service.evaluate(
            CYCLE,
            new FoundationReadinessRequest(
                new BankingRequirement(
                    OwnerKind.PAYROLL_STATUTORY_UNIT,
                    "INR",
                    "PAYROLL_FUNDING",
                    null),
                List.of(
                    new RegistrationRequirement(
                        REGISTRATION_TYPE,
                        OwnerKind.LEGAL_ENTITY,
                        JURISDICTION,
                        30))));

    assertThat(result.foundationReady()).isFalse();
    assertThat(result.readinessStatus()).isEqualTo("BLOCKED");
    assertThat(result.findings())
        .extracting(FoundationReadinessView.Finding::code)
        .contains(
            "FOUNDATION_CONFIGURATION_NOT_SEALED",
            "BANK_ACCOUNT_MISSING",
            "SIGNATORY_AUTHORITY_MISSING",
            "REGISTRATION_MISSING");
    assertThat(result.dimensions())
        .filteredOn(dimension -> !dimension.ready())
        .extracting(FoundationReadinessView.Dimension::code)
        .containsExactlyInAnyOrder(
            "CONFIGURATION_SNAPSHOT",
            "BANK_ACCOUNT",
            "SIGNATORY_AUTHORITY",
            "JURISDICTION_REGISTRATION");
  }

  @Test
  void emptyRegistrationListIsExplicitlyBoundedRatherThanAStatutoryConclusion() {
    when(repository.context(CYCLE)).thenReturn(sealedContext());
    when(banking.evaluate(
            "LEGAL_ENTITY",
            LEGAL,
            "INR",
            "PAYROLL_FUNDING",
            null,
            LocalDate.of(2026, 8, 31)))
        .thenReturn(
            new BankingReadinessView(
                "BANKING_AND_SIGNATORY_ONLY",
                "LEGAL_ENTITY",
                LEGAL,
                null,
                "INR",
                "PAYROLL_FUNDING",
                null,
                LocalDate.of(2026, 8, 31),
                true,
                true,
                true,
                null,
                List.of()));

    FoundationReadinessView result =
        service.evaluate(
            CYCLE,
            new FoundationReadinessRequest(
                new BankingRequirement(
                    OwnerKind.LEGAL_ENTITY,
                    "INR",
                    "PAYROLL_FUNDING",
                    null),
                List.of()));

    assertThat(result.foundationReady()).isTrue();
    assertThat(result.registrationChecks()).isEmpty();
    assertThat(result.dimensions())
        .filteredOn(dimension -> dimension.code().equals("JURISDICTION_REGISTRATION"))
        .singleElement()
        .extracting(FoundationReadinessView.Dimension::coverage)
        .isEqualTo("CALLER_DECLARED_REQUIREMENTS_ONLY");
    assertThat(result.excludedCapabilities())
        .contains("COUNTRY_SPECIFIC_STATUTORY_RULES_RATES");
  }

  private FoundationContext sealedContext() {
    return new FoundationContext(
        CYCLE,
        "INPUTS_SEALED",
        PAY_GROUP_VERSION,
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        LocalDate.of(2026, 8, 31),
        PSU_VERSION,
        PSU,
        LEGAL_VERSION,
        LEGAL,
        SNAPSHOT,
        "a".repeat(64),
        11,
        Instant.parse("2026-08-31T00:00:00Z"));
  }
}
