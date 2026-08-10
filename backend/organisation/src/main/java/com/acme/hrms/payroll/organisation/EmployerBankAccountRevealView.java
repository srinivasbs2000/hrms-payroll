package com.acme.hrms.payroll.organisation;

import java.time.LocalDate;
import java.util.UUID;

public record EmployerBankAccountRevealView(
    UUID identityId,
    UUID versionId,
    String code,
    String ownerKind,
    UUID legalEntityId,
    UUID payrollStatutoryUnitId,
    String bankName,
    String branchName,
    String routingCode,
    String accountHolderName,
    String currencyCode,
    String accountNumber,
    LocalDate effectiveFrom,
    LocalDate effectiveTo) {}
