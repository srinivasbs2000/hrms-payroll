package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ONBOARDING_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ONBOARDING_READ;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.ONBOARDING_WRITE;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCaseView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingCreateRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingEventView;
import com.acme.hrms.payroll.employeepayroll.EmployeePayrollOnboardingModels.OnboardingTransitionRequest;
import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollOnboardingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll-relationships/{relationshipId}/onboarding")
public class EmployeePayrollOnboardingController {
  private final EmployeePayrollOnboardingService service;

  public EmployeePayrollOnboardingController(EmployeePayrollOnboardingService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + ONBOARDING_READ + "')")
  public ResponseEntity<OnboardingCaseView> get(@PathVariable UUID relationshipId) {
    OnboardingCaseView view = service.get(relationshipId);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + ONBOARDING_WRITE + "')")
  public ResponseEntity<OnboardingCaseView> create(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody OnboardingCreateRequest request) {
    OnboardingCaseView view = service.create(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo())).body(view);
  }

  @GetMapping("/history")
  @PreAuthorize("hasAuthority('" + ONBOARDING_READ + "')")
  public List<OnboardingEventView> history(@PathVariable UUID relationshipId) {
    return service.history(relationshipId);
  }

  @PostMapping("/transition")
  @PreAuthorize("hasAuthority('" + ONBOARDING_WRITE + "')")
  public ResponseEntity<OnboardingCaseView> transition(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody OnboardingTransitionRequest request) {
    OnboardingCaseView view = service.transition(
        relationshipId, key, EmployeePayrollHttpSupport.expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }

  @PostMapping("/approve")
  @PreAuthorize("hasAuthority('" + ONBOARDING_APPROVE + "')")
  public ResponseEntity<OnboardingCaseView> approve(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody OnboardingTransitionRequest request) {
    OnboardingCaseView view = service.approve(
        relationshipId, key, EmployeePayrollHttpSupport.expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }
}
