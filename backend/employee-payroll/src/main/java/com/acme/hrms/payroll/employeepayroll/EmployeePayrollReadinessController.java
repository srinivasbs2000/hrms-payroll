package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.READINESS_POLICY_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.READINESS_POLICY_WRITE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.READINESS_READ;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessPolicyWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessView;
import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollReadinessService;
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
@RequestMapping("/api/v1")
public class EmployeePayrollReadinessController {
  private final EmployeePayrollReadinessService service;

  public EmployeePayrollReadinessController(EmployeePayrollReadinessService service) {
    this.service = service;
  }

  @GetMapping("/payroll-relationships/{relationshipId}/readiness")
  @PreAuthorize("hasAuthority('" + READINESS_READ + "')")
  public ReadinessView readiness(
      @PathVariable UUID relationshipId,
      @RequestParam(required = false) String currencyCode,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.readiness(relationshipId, currencyCode, asOf);
  }

  @GetMapping("/employee-payroll/readiness-policies")
  @PreAuthorize("hasAuthority('" + READINESS_POLICY_READ + "')")
  public List<ReadinessPolicyView> policies(
      @RequestParam(required = false) String dimension) {
    return service.policies(dimension);
  }

  @PostMapping("/employee-payroll/readiness-policies")
  @PreAuthorize("hasAuthority('" + READINESS_POLICY_WRITE + "')")
  public ResponseEntity<ReadinessPolicyView> createPolicy(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ReadinessPolicyWriteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createPolicy(key, request));
  }
}
