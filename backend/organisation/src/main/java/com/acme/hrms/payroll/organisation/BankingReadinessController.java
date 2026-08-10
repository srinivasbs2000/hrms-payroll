package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.BankingReadinessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/banking-readiness")
public class BankingReadinessController {
  private final BankingReadinessService service;

  public BankingReadinessController(BankingReadinessService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('organisation.banking-readiness.read')")
  public BankingReadinessView readiness(
      @RequestParam String ownerKind,
      @RequestParam UUID ownerId,
      @RequestParam String currencyCode,
      @RequestParam String purposeCode,
      @RequestParam(required = false) BigDecimal amount,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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
