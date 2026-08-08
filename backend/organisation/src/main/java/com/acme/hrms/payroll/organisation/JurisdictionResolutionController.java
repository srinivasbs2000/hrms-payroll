package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.JurisdictionResolutionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jurisdiction-resolutions")
public class JurisdictionResolutionController {
  private final JurisdictionResolutionService service;

  public JurisdictionResolutionController(
      JurisdictionResolutionService service) {
    this.service = service;
  }

  @PostMapping("/preview")
  @PreAuthorize("hasAuthority('organisation.read')")
  public JurisdictionResolutionView preview(
      @Valid @RequestBody JurisdictionResolutionRequest request) {
    return service.preview(request);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('organisation.create')")
  public JurisdictionResolutionView resolve(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody JurisdictionResolutionRequest request) {
    return service.resolve(idempotencyKey, request);
  }
}
