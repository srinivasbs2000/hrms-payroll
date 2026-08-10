package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.BankingReadinessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Public bounded-readiness facade for cross-module composition.
 *
 * <p>This adapter delegates to the organisation-owned readiness service and does
 * not redefine banking or signatory business rules.
 */
@Component
public class BankingReadinessFacade {
  private final BankingReadinessService service;

  public BankingReadinessFacade(BankingReadinessService service) {
    this.service = service;
  }

  public BankingReadinessView evaluate(
      String ownerKind,
      UUID ownerId,
      String currencyCode,
      String purposeCode,
      BigDecimal amount,
      LocalDate asOf) {
    return service.readiness(
        ownerKind,
        ownerId,
        currencyCode,
        purposeCode,
        amount,
        asOf);
  }
}
