package com.acme.hrms.payroll.statutory;

import com.acme.hrms.payroll.statutory.internal.application.RegistrationReadinessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foundation-readiness/jurisdiction-registration")
public class RegistrationReadinessController {
  private final RegistrationReadinessService service;

  public RegistrationReadinessController(RegistrationReadinessService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public RegistrationReadinessView evaluate(
      @Valid @RequestBody RegistrationReadinessRequest request) {
    return service.evaluate(request);
  }
}
