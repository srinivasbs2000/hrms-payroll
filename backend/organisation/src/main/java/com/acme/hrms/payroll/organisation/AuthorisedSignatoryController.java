package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.AuthorisedSignatoryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/authorised-signatories")
public class AuthorisedSignatoryController {
  private final AuthorisedSignatoryService service;

  public AuthorisedSignatoryController(AuthorisedSignatoryService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('organisation.signatory.write')")
  public ResponseEntity<AuthorisedSignatoryView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody AuthorisedSignatoryCreateRequest request) {
    AuthorisedSignatoryView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/authorised-signatories/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.signatory.write')")
  public ResponseEntity<AuthorisedSignatoryView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody AuthorisedSignatoryVersionWriteRequest request) {
    AuthorisedSignatoryView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/authorised-signatories/"
                    + identityId
                    + "/versions/"
                    + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions/{versionId}/submit")
  @PreAuthorize("hasAuthority('organisation.signatory.write')")
  public ResponseEntity<AuthorisedSignatoryView> submit(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    return ok(
        service.submit(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch)));
  }

  @PostMapping("/{identityId}/versions/{versionId}/verify")
  @PreAuthorize("hasAuthority('organisation.signatory.verify')")
  public ResponseEntity<AuthorisedSignatoryView> verify(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AuthorisedSignatoryEvidenceRequest request) {
    return ok(
        service.verify(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/request-approval")
  @PreAuthorize("hasAuthority('organisation.signatory.verify')")
  public ResponseEntity<AuthorisedSignatoryView> requestApproval(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    return ok(
        service.requestApproval(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch)));
  }

  @PostMapping("/{identityId}/versions/{versionId}/approve")
  @PreAuthorize("hasAuthority('organisation.signatory.approve')")
  public ResponseEntity<AuthorisedSignatoryView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AuthorisedSignatoryEvidenceRequest request) {
    return ok(
        service.approve(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/reject")
  @PreAuthorize("hasAuthority('organisation.signatory.approve')")
  public ResponseEntity<AuthorisedSignatoryView> reject(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AuthorisedSignatoryRejectRequest request) {
    return ok(
        service.reject(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/suspend")
  @PreAuthorize("hasAuthority('organisation.signatory.approve')")
  public ResponseEntity<AuthorisedSignatoryView> suspend(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AuthorisedSignatorySuspendRequest request) {
    return ok(
        service.suspend(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('organisation.signatory.read')")
  public List<AuthorisedSignatoryView> list(
      @RequestParam(required = false) String ownerKind,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.list(ownerKind, ownerId, asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('organisation.signatory.read')")
  public ResponseEntity<AuthorisedSignatoryView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return ok(service.current(identityId, asOf));
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.signatory.read')")
  public List<AuthorisedSignatoryView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/authority-evaluations")
  @PreAuthorize("hasAuthority('organisation.signatory.read')")
  public AuthorityEvaluationView evaluateAuthority(
      @Valid @RequestBody AuthorityEvaluationRequest request) {
    return service.evaluateAuthority(request);
  }

  private ResponseEntity<AuthorisedSignatoryView> ok(
      AuthorisedSignatoryView view) {
    return ResponseEntity.ok()
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(
          ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version",
          exception);
    }
  }
}
