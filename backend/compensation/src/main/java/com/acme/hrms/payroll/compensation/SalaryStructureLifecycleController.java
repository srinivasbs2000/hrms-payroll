package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleCommentRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleView;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.RejectionRequest;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureLifecycleService;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructureLifecycleController {
  private final SalaryStructureLifecycleService service;

  public SalaryStructureLifecycleController(
      SalaryStructureLifecycleService service) {
    this.service = service;
  }

  @GetMapping("/{identityId}/versions/{versionId}/lifecycle")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public ResponseEntity<LifecycleView> lifecycle(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId) {
    LifecycleView result = service.lifecycle(identityId, versionId);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/submission")
  @PreAuthorize("hasAuthority('compensation.structure.submit')")
  public ResponseEntity<LifecycleView> submit(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody(required = false) LifecycleCommentRequest request) {
    LifecycleCommentRequest effective =
        request == null ? new LifecycleCommentRequest(null) : request;
    LifecycleView result = service.submit(
        identityId,
        versionId,
        idempotencyKey,
        expectedVersion(ifMatch),
        effective);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/rejection")
  @PreAuthorize("hasAuthority('compensation.structure.approve')")
  public ResponseEntity<LifecycleView> reject(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RejectionRequest request) {
    LifecycleView result = service.reject(
        identityId,
        versionId,
        idempotencyKey,
        expectedVersion(ifMatch),
        request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/publication")
  @PreAuthorize("hasAuthority('compensation.structure.publish')")
  public ResponseEntity<LifecycleView> publish(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody(required = false) LifecycleCommentRequest request) {
    LifecycleCommentRequest effective =
        request == null ? new LifecycleCommentRequest(null) : request;
    LifecycleView result = service.publish(
        identityId,
        versionId,
        idempotencyKey,
        expectedVersion(ifMatch),
        effective);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version", exception);
    }
  }
}
