package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.security.internal.application.ApprovalAuthorityService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/foundation-approval-authorities")
public class ApprovalAuthorityController {
  private final ApprovalAuthorityService service;
  public ApprovalAuthorityController(ApprovalAuthorityService service) { this.service = service; }

  @PostMapping
  @PreAuthorize("hasAuthority('foundation-approval-authority.write')")
  public ResponseEntity<ApprovalAuthorityAssignmentView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ApprovalAuthorityAssignmentCreateRequest request) {
    ApprovalAuthorityAssignmentView created = service.createAssignment(idempotencyKey, request);
    return ResponseEntity.created(
        URI.create("/api/v1/foundation-approval-authorities/" + created.id()))
        .eTag(Long.toString(created.versionNo())).body(created);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('foundation-approval-authority.read')")
  public List<ApprovalAuthorityAssignmentView> list() { return service.assignments(); }

  @PostMapping("/{authorityId}/suspension")
  @PreAuthorize("hasAuthority('foundation-approval-authority.write')")
  public ResponseEntity<ApprovalAuthorityAssignmentView> suspend(
      @PathVariable UUID authorityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ApprovalAuthorityStateRequest request) {
    var view = service.suspend(authorityId, idempotencyKey, expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }

  @PostMapping("/{authorityId}/retirement")
  @PreAuthorize("hasAuthority('foundation-approval-authority.write')")
  public ResponseEntity<ApprovalAuthorityAssignmentView> retire(
      @PathVariable UUID authorityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ApprovalAuthorityStateRequest request) {
    var view = service.retire(authorityId, idempotencyKey, expectedVersion(ifMatch), request);
    return ResponseEntity.ok().eTag(Long.toString(view.versionNo())).body(view);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("If-Match must contain a numeric version", exception);
    }
  }
}
