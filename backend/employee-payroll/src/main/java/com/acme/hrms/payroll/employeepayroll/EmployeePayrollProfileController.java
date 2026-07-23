package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PROFILE_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PROFILE_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PROFILE_STATUS_UPDATE;

import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollProfileService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class EmployeePayrollProfileController {
  private final EmployeePayrollProfileService service;

  public EmployeePayrollProfileController(EmployeePayrollProfileService service) {
    this.service = service;
  }

  @PostMapping("/employee-payroll-profiles")
  @PreAuthorize("hasAuthority('" + PROFILE_CREATE + "')")
  public ResponseEntity<EmployeePayrollProfileView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EmployeePayrollProfileWriteRequest request) {
    EmployeePayrollProfileView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/employee-payroll-profiles/" + result.id()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/employee-payroll-profiles/{profileId}")
  @PreAuthorize("hasAuthority('" + PROFILE_READ + "')")
  public ResponseEntity<EmployeePayrollProfileView> get(
      @PathVariable UUID profileId) {
    EmployeePayrollProfileView result = service.get(profileId);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/payroll-relationships/{relationshipId}/profile")
  @PreAuthorize("hasAuthority('" + PROFILE_READ + "')")
  public ResponseEntity<EmployeePayrollProfileView> forRelationship(
      @PathVariable UUID relationshipId) {
    EmployeePayrollProfileView result =
        service.forRelationship(relationshipId);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/employee-payroll-profiles/{profileId}/status")
  @PreAuthorize("hasAuthority('" + PROFILE_STATUS_UPDATE + "')")
  public ResponseEntity<EmployeePayrollProfileView> updateStatus(
      @PathVariable UUID profileId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EmployeePayrollProfileStatusRequest request) {
    EmployeePayrollProfileView result = service.updateStatus(
        profileId,
        idempotencyKey,
        request,
        EmployeePayrollHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/employee-payroll-profiles/{profileId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(
      @PathVariable UUID profileId) {
    return service.audit(profileId);
  }
}
