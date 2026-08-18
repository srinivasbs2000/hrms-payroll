package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPENSATION_CHANGE_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPENSATION_CHANGE_ASSESS;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPENSATION_CHANGE_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPENSATION_CHANGE_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPONENT_OVERRIDE_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPONENT_OVERRIDE_CORRECT;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPONENT_OVERRIDE_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.COMPONENT_OVERRIDE_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.LIFECYCLE_LINEAGE_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.LIFECYCLE_LINEAGE_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.LIFECYCLE_LINEAGE_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_READ;

import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollBindingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class EmployeePayrollBindingController {
  private final EmployeePayrollBindingService service;

  public EmployeePayrollBindingController(EmployeePayrollBindingService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/compensation-changes")
  @PreAuthorize("hasAuthority('" + COMPENSATION_CHANGE_CREATE + "')")
  public ResponseEntity<CompensationChangeView> createCompensationChange(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CompensationChangeWriteRequest request) {
    CompensationChangeView result = service.createCompensationChange(key, request);
    return ResponseEntity.created(URI.create("/api/v1/compensation-changes/" + result.id()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/compensation-changes")
  @PreAuthorize("hasAuthority('" + COMPENSATION_CHANGE_READ + "')")
  public List<CompensationChangeView> compensationChanges(
      @RequestParam UUID payrollAssignmentId) {
    return service.compensationChanges(payrollAssignmentId);
  }

  @PostMapping("/api/v1/compensation-changes/{id}/impact-assessment")
  @PreAuthorize("hasAuthority('" + COMPENSATION_CHANGE_ASSESS + "')")
  public ResponseEntity<CompensationChangeView> assessCompensationChange(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CompensationChangeAssessmentRequest request) {
    CompensationChangeView result = service.assessCompensationChange(id, key, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/compensation-changes/{id}/impact")
  @PreAuthorize("hasAuthority('" + COMPENSATION_CHANGE_READ + "')")
  public List<CompensationChangeImpactView> compensationChangeImpact(@PathVariable UUID id) {
    return service.compensationChangeImpact(id);
  }

  @PostMapping("/api/v1/compensation-changes/{id}/approval")
  @PreAuthorize("hasAuthority('" + COMPENSATION_CHANGE_APPROVE + "')")
  public ResponseEntity<CompensationChangeView> approveCompensationChange(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key) {
    CompensationChangeView result = service.approveCompensationChange(id, key);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/compensation-changes/{id}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> compensationChangeAudit(@PathVariable UUID id) {
    return service.audit(EmployeePayrollBindingService.COMPENSATION_CHANGE, id);
  }

  @PostMapping("/api/v1/employee-component-overrides")
  @PreAuthorize("hasAuthority('" + COMPONENT_OVERRIDE_CREATE + "')")
  public ResponseEntity<EmployeeComponentOverrideView> createOverride(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody EmployeeComponentOverrideWriteRequest request) {
    EmployeeComponentOverrideView result = service.createOverride(key, request);
    return ResponseEntity.created(URI.create("/api/v1/employee-component-overrides/" + result.id()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/employee-component-overrides")
  @PreAuthorize("hasAuthority('" + COMPONENT_OVERRIDE_READ + "')")
  public List<EmployeeComponentOverrideView> overrides(
      @RequestParam UUID payrollAssignmentVersionId) {
    return service.overrides(payrollAssignmentVersionId);
  }

  @PostMapping("/api/v1/employee-component-overrides/{id}/corrections")
  @PreAuthorize("hasAuthority('" + COMPONENT_OVERRIDE_CORRECT + "')")
  public ResponseEntity<EmployeeComponentOverrideView> correctOverride(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody EmployeeComponentOverrideWriteRequest request) {
    EmployeeComponentOverrideView result = service.correctOverride(id, key, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/api/v1/employee-component-overrides/{id}/approval")
  @PreAuthorize("hasAuthority('" + COMPONENT_OVERRIDE_APPROVE + "')")
  public ResponseEntity<EmployeeComponentOverrideView> approveOverride(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key) {
    EmployeeComponentOverrideView result = service.approveOverride(id, key);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/employee-component-overrides/{id}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> overrideAudit(@PathVariable UUID id) {
    return service.audit(EmployeePayrollBindingService.COMPONENT_OVERRIDE, id);
  }

  @PostMapping("/api/v1/payroll-lifecycle-lineage")
  @PreAuthorize("hasAuthority('" + LIFECYCLE_LINEAGE_CREATE + "')")
  public ResponseEntity<PayrollLifecycleLineageView> createLineage(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PayrollLifecycleLineageWriteRequest request) {
    PayrollLifecycleLineageView result = service.createLineage(key, request);
    return ResponseEntity.created(URI.create("/api/v1/payroll-lifecycle-lineage/" + result.id()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/payroll-lifecycle-lineage")
  @PreAuthorize("hasAuthority('" + LIFECYCLE_LINEAGE_READ + "')")
  public List<PayrollLifecycleLineageView> lineage(@RequestParam UUID payrollRelationshipId) {
    return service.lineage(payrollRelationshipId);
  }

  @PostMapping("/api/v1/payroll-lifecycle-lineage/{id}/approval")
  @PreAuthorize("hasAuthority('" + LIFECYCLE_LINEAGE_APPROVE + "')")
  public ResponseEntity<PayrollLifecycleLineageView> approveLineage(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key) {
    PayrollLifecycleLineageView result = service.approveLineage(id, key);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/api/v1/payroll-lifecycle-lineage/{id}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> lineageAudit(@PathVariable UUID id) {
    return service.audit(EmployeePayrollBindingService.LIFECYCLE_LINEAGE, id);
  }

  @GetMapping("/api/v1/pay-group-assignments/{id}/impact")
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_READ + "')")
  public List<PayGroupAssignmentImpactView> payGroupImpact(@PathVariable UUID id) {
    return service.payGroupImpact(id);
  }
}
