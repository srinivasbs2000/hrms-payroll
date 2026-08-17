package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingView;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanCreateRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanView;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureSupplementalPlanService;
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
@RequestMapping("/api/v1")
public class SalaryStructureSupplementalPlanController {
  private final SalaryStructureSupplementalPlanService service;

  public SalaryStructureSupplementalPlanController(
      SalaryStructureSupplementalPlanService service) {
    this.service = service;
  }

  @PostMapping("/salary-supplemental-plans")
  @PreAuthorize("hasAuthority('compensation.structure.create')")
  public ResponseEntity<SupplementalPlanView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SupplementalPlanCreateRequest request) {
    SupplementalPlanView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/salary-supplemental-plans/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @GetMapping("/salary-supplemental-plans")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<SupplementalPlanView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/salary-supplemental-plans/{identityId}")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public SupplementalPlanView current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.current(identityId, asOf);
  }

  @GetMapping("/salary-supplemental-plans/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<SupplementalPlanView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/salary-supplemental-plans/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.structure.version.create')")
  public ResponseEntity<SupplementalPlanView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SupplementalPlanVersionWriteRequest request) {
    SupplementalPlanView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/salary-supplemental-plans/"
                + identityId
                + "/versions/"
                + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping(
      "/salary-supplemental-plans/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.structure.approve')")
  public ResponseEntity<SupplementalPlanView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    SupplementalPlanView approved =
        service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(approved.versionNo()))
        .body(approved);
  }

  @GetMapping("/salary-supplemental-plans/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(@PathVariable UUID identityId) {
    return service.audit(identityId);
  }

  @PostMapping(
      "/salary-structures/{salaryStructureId}/versions/"
          + "{salaryStructureVersionId}/supplemental-plans")
  @PreAuthorize("hasAuthority('compensation.structure.version.create')")
  public ResponseEntity<SupplementalPlanBindingView> bind(
      @PathVariable UUID salaryStructureId,
      @PathVariable UUID salaryStructureVersionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SupplementalPlanBindingWriteRequest request) {
    SupplementalPlanBindingView created =
        service.bind(
            salaryStructureId,
            salaryStructureVersionId,
            idempotencyKey,
            request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/salary-structures/"
                + salaryStructureId
                + "/versions/"
                + salaryStructureVersionId
                + "/supplemental-plans/"
                + created.bindingId()))
        .body(created);
  }

  @GetMapping(
      "/salary-structures/{salaryStructureId}/versions/"
          + "{salaryStructureVersionId}/supplemental-plans")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public List<SupplementalPlanBindingView> bindings(
      @PathVariable UUID salaryStructureId,
      @PathVariable UUID salaryStructureVersionId) {
    return service.bindings(salaryStructureId, salaryStructureVersionId);
  }
}
