package com.acme.hrms.payroll.statutory;

import com.acme.hrms.payroll.statutory.internal.application.RegistrationReadinessService;
import org.springframework.stereotype.Component;

/**
 * Public bounded-readiness facade for cross-module composition.
 *
 * <p>This adapter delegates to the statutory-owned readiness service and does
 * not redefine jurisdiction or registration business rules.
 */
@Component
public class RegistrationReadinessFacade {
  private final RegistrationReadinessService service;

  public RegistrationReadinessFacade(RegistrationReadinessService service) {
    this.service = service;
  }

  public RegistrationReadinessView evaluate(RegistrationReadinessRequest request) {
    return service.evaluate(request);
  }
}
