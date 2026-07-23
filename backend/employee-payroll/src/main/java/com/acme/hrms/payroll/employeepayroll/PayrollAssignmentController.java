package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_VERSION_CORRECT;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_VERSION_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ASSIGNMENT_VERSION_END_DATE;

import com.acme.hrms.payroll.employeepayroll.internal.application.PayrollAssignmentService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/payroll-assignments")
public class PayrollAssignmentController {
  private final PayrollAssignmentService service;

  public PayrollAssignmentController(PayrollAssignmentService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_CREATE + "')")
  public ResponseEntity<PayrollAssignmentView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollAssignmentWriteRequest request) {
    PayrollAssignmentView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/payroll-assignments/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_READ + "')")
  public List<PayrollAssignmentView> list(
      @RequestParam UUID payrollRelationshipId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(payrollRelationshipId, asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_READ + "')")
  public ResponseEntity<PayrollAssignmentView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    PayrollAssignmentView result = service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_READ + "')")
  public List<PayrollAssignmentView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_VERSION_CREATE + "')")
  public ResponseEntity<PayrollAssignmentView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollAssignmentWriteRequest request) {
    PayrollAssignmentView result =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/payroll-assignments/" + identityId
                + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_VERSION_CORRECT + "')")
  public ResponseEntity<PayrollAssignmentView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollAssignmentWriteRequest request) {
    PayrollAssignmentView result = service.correctFuture(
        identityId, versionId, idempotencyKey, request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_VERSION_END_DATE + "')")
  public ResponseEntity<PayrollAssignmentView> endDate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AssignmentEndDateRequest request) {
    PayrollAssignmentView result = service.endDate(
        identityId,
        versionId,
        idempotencyKey,
        request.assignmentEnd(),
        EmployeePayrollHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('" + ASSIGNMENT_APPROVE + "')")
  public ResponseEntity<PayrollAssignmentView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    PayrollAssignmentView result =
        service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(
      @PathVariable UUID identityId) {
    return service.audit(identityId);
  }

  public record AssignmentEndDateRequest(
      @NotNull LocalDate assignmentEnd) {}
}
