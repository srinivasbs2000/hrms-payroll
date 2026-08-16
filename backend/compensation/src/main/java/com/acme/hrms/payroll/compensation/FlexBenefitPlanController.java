package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanCreateRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanView;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionValidationRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionValidationView;
import com.acme.hrms.payroll.compensation.internal.application.FlexBenefitPlanService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/flex-benefit-plans")
public class FlexBenefitPlanController {
  private final FlexBenefitPlanService service;

  public FlexBenefitPlanController(FlexBenefitPlanService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('compensation.structure.create')")
  public ResponseEntity<FlexBenefitPlanView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody FlexBenefitPlanCreateRequest request) {
    FlexBenefitPlanView result = service.create(idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/flex-benefit-plans/" + result.identityId()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<FlexBenefitPlanView> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public ResponseEntity<FlexBenefitPlanView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    FlexBenefitPlanView result = service.current(identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<FlexBenefitPlanView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.structure.version.create')")
  public ResponseEntity<FlexBenefitPlanView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody FlexBenefitPlanVersionWriteRequest request) {
    FlexBenefitPlanView result = service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity.created(URI.create(
        "/api/v1/flex-benefit-plans/" + identityId + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize("hasAuthority('compensation.structure.version.correct')")
  public ResponseEntity<FlexBenefitPlanView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody FlexBenefitPlanVersionWriteRequest request) {
    FlexBenefitPlanView result = service.correctFuture(identityId, versionId, idempotencyKey, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.structure.approve')")
  public ResponseEntity<FlexBenefitPlanView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    FlexBenefitPlanView result = service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/election-validation")
  @PreAuthorize("hasAuthority('compensation.structure.simulate')")
  public FlexElectionValidationView validateElection(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @Valid @RequestBody FlexElectionValidationRequest request) {
    return service.validateElection(identityId, versionId, request);
  }

  @GetMapping("/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(@PathVariable UUID identityId) {
    return service.audit(identityId);
  }
}
