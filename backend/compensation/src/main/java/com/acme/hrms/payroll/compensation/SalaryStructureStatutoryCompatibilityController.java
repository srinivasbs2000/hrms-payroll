package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.CompatibilityEvaluationView;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.RetireBindingRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.RuleVersionOption;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureStatutoryCompatibilityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/salary-structure-statutory-compatibility")
public class SalaryStructureStatutoryCompatibilityController {
  private final SalaryStructureStatutoryCompatibilityService service;

  public SalaryStructureStatutoryCompatibilityController(
      SalaryStructureStatutoryCompatibilityService service) {
    this.service = service;
  }

  @GetMapping("/rule-versions")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<RuleVersionOption> ruleVersions(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.ruleVersions(asOf);
  }

  @GetMapping("/{identityId}/versions/{versionId}/bindings")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<BindingView> bindings(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId) {
    return service.bindings(identityId, versionId);
  }

  @PostMapping("/{identityId}/versions/{versionId}/bindings")
  @PreAuthorize("hasAuthority('compensation.structure.version.create')")
  public BindingView bind(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody BindingRequest request) {
    return service.bind(identityId, versionId, idempotencyKey, request);
  }

  @PostMapping(
      "/{identityId}/versions/{versionId}/bindings/{bindingId}/retirement")
  @PreAuthorize("hasAuthority('compensation.structure.version.create')")
  public BindingView retire(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @PathVariable UUID bindingId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody RetireBindingRequest request) {
    return service.retire(
        identityId,
        versionId,
        bindingId,
        idempotencyKey,
        request);
  }

  @PostMapping(
      "/{identityId}/versions/{versionId}/validations/{validationId}/evaluations")
  @PreAuthorize("hasAuthority('compensation.structure.simulate')")
  public CompatibilityEvaluationView evaluate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @PathVariable UUID validationId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return service.evaluate(
        identityId,
        versionId,
        validationId,
        idempotencyKey);
  }

  @GetMapping(
      "/{identityId}/versions/{versionId}/validations/{validationId}/evaluations")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<CompatibilityEvaluationView> evaluations(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @PathVariable UUID validationId) {
    return service.evaluations(identityId, versionId, validationId);
  }
}
