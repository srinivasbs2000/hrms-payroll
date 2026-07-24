package com.acme.hrms.payroll.payrolloperations;

import static com.acme.hrms.payroll.payrolloperations.PayrollOperationsPermissions.CYCLE_CREATE;
import static com.acme.hrms.payroll.payrolloperations.PayrollOperationsPermissions.CYCLE_READ;
import static com.acme.hrms.payroll.payrolloperations.PayrollOperationsPermissions.POPULATION_RESOLVE;

import com.acme.hrms.payroll.payrolloperations.internal.application.PayrollCycleService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/payroll-cycles")
public class PayrollCycleController {
  private final PayrollCycleService service;

  public PayrollCycleController(PayrollCycleService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + CYCLE_CREATE + "')")
  public ResponseEntity<PayrollCycleView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollCycleCreateRequest request) {
    PayrollCycleView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/payroll-cycles/" + created.id()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + CYCLE_READ + "')")
  public List<PayrollCycleView> list() {
    return service.list();
  }

  @GetMapping("/{cycleId}")
  @PreAuthorize("hasAuthority('" + CYCLE_READ + "')")
  public ResponseEntity<PayrollCycleView> get(@PathVariable UUID cycleId) {
    PayrollCycleView cycle = service.get(cycleId);
    return ResponseEntity.ok()
        .eTag(Long.toString(cycle.versionNo()))
        .body(cycle);
  }

  @PostMapping("/{cycleId}/population-resolution")
  @PreAuthorize("hasAuthority('" + POPULATION_RESOLVE + "')")
  public ResponseEntity<PopulationResolutionResult> resolvePopulation(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    PopulationResolutionResult result = service.resolvePopulation(
        cycleId,
        idempotencyKey,
        PayrollOperationsHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @GetMapping("/{cycleId}/population")
  @PreAuthorize("hasAuthority('" + CYCLE_READ + "')")
  public List<PopulationMemberView> population(@PathVariable UUID cycleId) {
    return service.population(cycleId);
  }

  @GetMapping("/{cycleId}/population-resolutions")
  @PreAuthorize("hasAuthority('" + CYCLE_READ + "')")
  public List<PopulationResolutionView> populationResolutions(
      @PathVariable UUID cycleId) {
    return service.populationResolutions(cycleId);
  }

  @GetMapping("/{cycleId}/population-decisions")
  @PreAuthorize("hasAuthority('" + CYCLE_READ + "')")
  public List<PopulationDecisionView> populationDecisions(
      @PathVariable UUID cycleId,
      @RequestParam(required = false) UUID resolutionId) {
    return service.populationDecisions(cycleId, resolutionId);
  }

  @GetMapping("/{cycleId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(@PathVariable UUID cycleId) {
    return service.audit(cycleId);
  }
}
