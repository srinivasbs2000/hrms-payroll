package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.EffectiveEndRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaDependencyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaValidationRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.FormulaValidationView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateLookupView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyView;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.StatutoryWageReferenceView;
import com.acme.hrms.payroll.compensation.internal.application.ComponentCatalogueControlService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
public class ComponentCatalogueControlController {
  private final ComponentCatalogueControlService service;

  public ComponentCatalogueControlController(ComponentCatalogueControlService service) {
    this.service = service;
  }

  @PostMapping("/pay-components/formula-validation")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public FormulaValidationView validateFormula(@Valid @RequestBody FormulaValidationRequest request) {
    return service.validateFormula(request);
  }

  @GetMapping("/pay-components/{identityId}/dependencies")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<FormulaDependencyView> dependencies(@PathVariable UUID identityId) {
    return service.dependencies(identityId);
  }

  @GetMapping("/pay-components/{identityId}/statutory-wage-references")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<StatutoryWageReferenceView> statutoryWageReferences(
      @PathVariable UUID identityId) {
    return service.statutoryWageReferences(identityId);
  }

  @PostMapping("/component-rate-tables")
  @PreAuthorize("hasAuthority('compensation.component.create')")
  public ResponseEntity<RateTableView> createRateTable(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RateTableCreateRequest request) {
    RateTableView result = service.createRateTable(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/component-rate-tables/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/component-rate-tables")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<RateTableView> listRateTables(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.listRateTables(asOf);
  }

  @GetMapping("/component-rate-tables/{identityId}")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public ResponseEntity<RateTableView> rateTable(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    RateTableView result = service.rateTable(identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/component-rate-tables/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<RateTableView> rateTableHistory(@PathVariable UUID identityId) {
    return service.rateTableHistory(identityId);
  }

  @PostMapping("/component-rate-tables/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.version.create')")
  public ResponseEntity<RateTableView> addRateTableVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RateTableVersionWriteRequest request) {
    RateTableView result = service.addRateTableVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/component-rate-tables/" + identityId + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/component-rate-tables/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.component.approve')")
  public ResponseEntity<RateTableView> approveRateTable(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    RateTableView result = service.approveRateTable(
        identityId, versionId, idempotencyKey, expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/component-rate-tables/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('compensation.component.version.end-date')")
  public ResponseEntity<RateTableView> endDateRateTable(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EffectiveEndRequest request) {
    request.validate();
    RateTableView result = service.endDateRateTable(
        identityId, versionId, idempotencyKey, request.effectiveTo(), expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/component-rate-tables/{identityId}/lookup")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public RateLookupView lookupRate(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
      @RequestBody Map<String, String> dimensions) {
    return service.lookupRate(identityId, asOf, dimensions);
  }

  @GetMapping("/component-rate-tables/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> rateTableAudit(@PathVariable UUID identityId) {
    return service.rateAudit(identityId);
  }

  @PostMapping("/component-rounding-policies")
  @PreAuthorize("hasAuthority('compensation.component.create')")
  public ResponseEntity<RoundingPolicyView> createRoundingPolicy(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RoundingPolicyCreateRequest request) {
    RoundingPolicyView result = service.createRoundingPolicy(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/component-rounding-policies/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/component-rounding-policies")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<RoundingPolicyView> listRoundingPolicies(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.listRoundingPolicies(asOf);
  }

  @GetMapping("/component-rounding-policies/{identityId}")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public ResponseEntity<RoundingPolicyView> roundingPolicy(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    RoundingPolicyView result = service.roundingPolicy(identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/component-rounding-policies/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<RoundingPolicyView> roundingHistory(@PathVariable UUID identityId) {
    return service.roundingHistory(identityId);
  }

  @PostMapping("/component-rounding-policies/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.version.create')")
  public ResponseEntity<RoundingPolicyView> addRoundingPolicyVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RoundingPolicyVersionWriteRequest request) {
    RoundingPolicyView result = service.addRoundingPolicyVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/component-rounding-policies/" + identityId + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/component-rounding-policies/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.component.approve')")
  public ResponseEntity<RoundingPolicyView> approveRoundingPolicy(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    RoundingPolicyView result = service.approveRoundingPolicy(
        identityId, versionId, idempotencyKey, expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/component-rounding-policies/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('compensation.component.version.end-date')")
  public ResponseEntity<RoundingPolicyView> endDateRoundingPolicy(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EffectiveEndRequest request) {
    request.validate();
    RoundingPolicyView result = service.endDateRoundingPolicy(
        identityId, versionId, idempotencyKey, request.effectiveTo(), expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/component-rounding-policies/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> roundingAudit(@PathVariable UUID identityId) {
    return service.roundingAudit(identityId);
  }

  @PostMapping("/component-proration-policies")
  @PreAuthorize("hasAuthority('compensation.component.create')")
  public ResponseEntity<ProrationPolicyView> createProrationPolicy(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ProrationPolicyCreateRequest request) {
    ProrationPolicyView result = service.createProrationPolicy(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/component-proration-policies/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/component-proration-policies")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<ProrationPolicyView> listProrationPolicies(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.listProrationPolicies(asOf);
  }

  @GetMapping("/component-proration-policies/{identityId}")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public ResponseEntity<ProrationPolicyView> prorationPolicy(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    ProrationPolicyView result = service.prorationPolicy(identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/component-proration-policies/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<ProrationPolicyView> prorationHistory(@PathVariable UUID identityId) {
    return service.prorationHistory(identityId);
  }

  @PostMapping("/component-proration-policies/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.version.create')")
  public ResponseEntity<ProrationPolicyView> addProrationPolicyVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ProrationPolicyVersionWriteRequest request) {
    ProrationPolicyView result = service.addProrationPolicyVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/component-proration-policies/" + identityId + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/component-proration-policies/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.component.approve')")
  public ResponseEntity<ProrationPolicyView> approveProrationPolicy(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    ProrationPolicyView result = service.approveProrationPolicy(
        identityId, versionId, idempotencyKey, expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/component-proration-policies/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('compensation.component.version.end-date')")
  public ResponseEntity<ProrationPolicyView> endDateProrationPolicy(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EffectiveEndRequest request) {
    request.validate();
    ProrationPolicyView result = service.endDateProrationPolicy(
        identityId, versionId, idempotencyKey, request.effectiveTo(), expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/component-proration-policies/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> prorationAudit(@PathVariable UUID identityId) {
    return service.prorationAudit(identityId);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("If-Match must contain a numeric version", exception);
    }
  }
}
