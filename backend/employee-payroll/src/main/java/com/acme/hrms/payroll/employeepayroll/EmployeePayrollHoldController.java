package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.HOLD_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.HOLD_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.HOLD_RELEASE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.HOLD_WRITE;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldEvidenceRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollHoldModels.PayrollHoldWriteRequest;
import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollHoldService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll-relationships/{relationshipId}/holds")
public class EmployeePayrollHoldController {
  private final EmployeePayrollHoldService service;

  public EmployeePayrollHoldController(EmployeePayrollHoldService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + HOLD_READ + "')")
  public List<PayrollHoldView> holds(
      @PathVariable UUID relationshipId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.holds(relationshipId, asOf);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + HOLD_WRITE + "')")
  public ResponseEntity<PayrollHoldView> create(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PayrollHoldWriteRequest request) {
    PayrollHoldView view = service.create(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo())).body(view);
  }

  @PostMapping("/{versionId}/approve")
  @PreAuthorize("hasAuthority('" + HOLD_APPROVE + "')")
  public ResponseEntity<PayrollHoldView> approve(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PayrollHoldEvidenceRequest request) {
    PayrollHoldView view = service.approve(
        relationshipId, versionId, key,
        EmployeePayrollHttpSupport.expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }

  @PostMapping("/{versionId}/release")
  @PreAuthorize("hasAuthority('" + HOLD_RELEASE + "')")
  public ResponseEntity<PayrollHoldView> release(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PayrollHoldEvidenceRequest request) {
    PayrollHoldView view = service.release(
        relationshipId, versionId, key,
        EmployeePayrollHttpSupport.expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }
}
