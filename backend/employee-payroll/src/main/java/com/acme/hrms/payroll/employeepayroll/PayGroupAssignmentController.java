package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_APPROVE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_CORRECT;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_CREATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_END_DATE;
import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.PAY_GROUP_ASSIGNMENT_READ;

import com.acme.hrms.payroll.employeepayroll.internal.application.PayGroupAssignmentService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/pay-group-assignments")
public class PayGroupAssignmentController {
  private final PayGroupAssignmentService service;

  public PayGroupAssignmentController(PayGroupAssignmentService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_CREATE + "')")
  public ResponseEntity<PayGroupAssignmentView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayGroupAssignmentWriteRequest request) {
    PayGroupAssignmentView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/pay-group-assignments/" + result.id()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_READ + "')")
  public List<PayGroupAssignmentView> list(
      @RequestParam UUID payrollAssignmentVersionId) {
    return service.list(payrollAssignmentVersionId);
  }

  @PostMapping("/{assignmentId}/corrections")
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_CORRECT + "')")
  public ResponseEntity<PayGroupAssignmentView> correct(
      @PathVariable UUID assignmentId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayGroupAssignmentWriteRequest request) {
    PayGroupAssignmentView result =
        service.correctFuture(assignmentId, idempotencyKey, request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{assignmentId}/approval")
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_APPROVE + "')")
  public ResponseEntity<PayGroupAssignmentView> approve(
      @PathVariable UUID assignmentId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    PayGroupAssignmentView result = service.approve(assignmentId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{assignmentId}/end-date")
  @PreAuthorize("hasAuthority('" + PAY_GROUP_ASSIGNMENT_END_DATE + "')")
  public ResponseEntity<PayGroupAssignmentView> endDate(
      @PathVariable UUID assignmentId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EffectiveEndDateRequest request) {
    PayGroupAssignmentView result = service.endDate(
        assignmentId,
        idempotencyKey,
        request.effectiveTo(),
        EmployeePayrollHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{assignmentId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(
      @PathVariable UUID assignmentId) {
    return service.audit(assignmentId);
  }

  public record EffectiveEndDateRequest(@NotNull LocalDate effectiveTo) {}
}
