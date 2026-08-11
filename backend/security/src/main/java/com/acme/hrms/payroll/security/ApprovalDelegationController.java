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
@RequestMapping("/api/v1/foundation-approval-delegations")
public class ApprovalDelegationController {
  private final ApprovalAuthorityService service;
  public ApprovalDelegationController(ApprovalAuthorityService service) { this.service = service; }

  @PostMapping
  @PreAuthorize("hasAuthority('foundation-approval-delegation.write')")
  public ResponseEntity<ApprovalDelegationView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ApprovalDelegationCreateRequest request) {
    var created = service.createDelegation(idempotencyKey, request);
    return ResponseEntity.created(
        URI.create("/api/v1/foundation-approval-delegations/" + created.id()))
        .eTag(Long.toString(created.versionNo())).body(created);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('foundation-approval-authority.read')")
  public List<ApprovalDelegationView> list() { return service.delegations(); }

  @PostMapping("/{delegationId}/revocation")
  @PreAuthorize("hasAuthority('foundation-approval-delegation.write')")
  public ResponseEntity<ApprovalDelegationView> revoke(
      @PathVariable UUID delegationId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ApprovalAuthorityStateRequest request) {
    var view = service.revokeDelegation(
        delegationId, idempotencyKey, expectedVersion(ifMatch), request);
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
