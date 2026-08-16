package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureCompositionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructureCompositionController {
  private final SalaryStructureCompositionService service;

  public SalaryStructureCompositionController(
      SalaryStructureCompositionService service) {
    this.service = service;
  }

  @PostMapping("/{identityId}/versions/{versionId}/composed-simulations")
  @PreAuthorize("hasAuthority('compensation.structure.simulate')")
  public ResponseEntity<SalaryStructureValidationView> simulate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SalaryStructureSimulationRequest request) {
    SalaryStructureValidationView result = service.simulate(
        identityId,
        versionId,
        idempotencyKey,
        request);

    return ResponseEntity
        .created(URI.create(
            "/api/v1/salary-structures/"
                + identityId
                + "/versions/"
                + versionId
                + "/validations/"
                + result.validationId()))
        .body(result);
  }
}
