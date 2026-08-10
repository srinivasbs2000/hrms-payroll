package com.acme.hrms.payroll.payrolloperations;

import com.acme.hrms.payroll.payrolloperations.internal.application.FoundationReadinessService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payroll-cycles/{cycleId}/foundation-readiness")
public class FoundationReadinessController {
  private final FoundationReadinessService service;

  public FoundationReadinessController(FoundationReadinessService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize(
      "hasAuthority('payroll-cycle.read')"
          + " and hasAuthority('organisation.banking-readiness.read')"
          + " and hasAuthority('statutory-registration.read')")
  public FoundationReadinessView evaluate(
      @PathVariable UUID cycleId,
      @Valid @RequestBody FoundationReadinessRequest request) {
    return service.evaluate(cycleId, request);
  }
}
