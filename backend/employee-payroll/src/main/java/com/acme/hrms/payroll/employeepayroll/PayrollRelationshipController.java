package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_VERSION_CORRECT;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_VERSION_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.RELATIONSHIP_VERSION_END_DATE;

import com.acme.hrms.payroll.employeepayroll.internal.application.PayrollRelationshipService;
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
@RequestMapping("/api/v1/payroll-relationships")
public class PayrollRelationshipController {
  private final PayrollRelationshipService service;

  public PayrollRelationshipController(PayrollRelationshipService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_CREATE + "')")
  public ResponseEntity<PayrollRelationshipView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollRelationshipWriteRequest request) {
    PayrollRelationshipView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/payroll-relationships/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_READ + "')")
  public List<PayrollRelationshipView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_READ + "')")
  public ResponseEntity<PayrollRelationshipView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    PayrollRelationshipView result = service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_READ + "')")
  public List<PayrollRelationshipView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_VERSION_CREATE + "')")
  public ResponseEntity<PayrollRelationshipView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollRelationshipWriteRequest request) {
    PayrollRelationshipView result =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/payroll-relationships/" + identityId
                + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_VERSION_CORRECT + "')")
  public ResponseEntity<PayrollRelationshipView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollRelationshipWriteRequest request) {
    PayrollRelationshipView result = service.correctFuture(
        identityId, versionId, idempotencyKey, request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_VERSION_END_DATE + "')")
  public ResponseEntity<PayrollRelationshipView> endDate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RelationshipEndDateRequest request) {
    PayrollRelationshipView result = service.endDate(
        identityId,
        versionId,
        idempotencyKey,
        request.relationshipEnd(),
        EmployeePayrollHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('" + RELATIONSHIP_APPROVE + "')")
  public ResponseEntity<PayrollRelationshipView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    PayrollRelationshipView result =
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

  public record RelationshipEndDateRequest(
      @NotNull LocalDate relationshipEnd) {}
}
