package com.acme.hrms.payroll.statutory;

import com.acme.hrms.payroll.statutory.internal.application.StatutoryRegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
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
@RequestMapping("/api/v1/statutory-registrations")
public class StatutoryRegistrationController {
  private final StatutoryRegistrationService service;

  public StatutoryRegistrationController(StatutoryRegistrationService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_WRITE)")
  public ResponseEntity<StatutoryRegistrationView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody StatutoryRegistrationCreateRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    StatutoryRegistrationView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/statutory-registrations/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_WRITE)")
  public ResponseEntity<StatutoryRegistrationView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody StatutoryRegistrationVersionWriteRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    StatutoryRegistrationView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/statutory-registrations/"
                    + identityId
                    + "/versions/"
                    + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions/{versionId}/submission")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_WRITE)")
  public ResponseEntity<StatutoryRegistrationView> submit(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.submit(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch)));
  }

  @PostMapping("/{identityId}/versions/{versionId}/verification")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_VERIFY)")
  public ResponseEntity<StatutoryRegistrationView> verify(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RegistrationVerificationRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.verify(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval-request")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_VERIFY)")
  public ResponseEntity<StatutoryRegistrationView> requestApproval(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.requestApproval(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch)));
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_APPROVE)")
  public ResponseEntity<StatutoryRegistrationView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RegistrationApprovalRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.approve(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/rejection")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_APPROVE)")
  public ResponseEntity<StatutoryRegistrationView> reject(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RegistrationRejectionRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.reject(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/suspension")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_APPROVE)")
  public ResponseEntity<StatutoryRegistrationView> suspend(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RegistrationSuspensionRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    return response(
        service.suspend(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/identifier-reveal")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_IDENTIFIER_READ)")
  public ResponseEntity<RegistrationIdentifierRevealView> revealIdentifier(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId) {
    StatutoryRegistrationView exact =
        service.revealIdentifier(identityId, versionId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new RegistrationIdentifierRevealView(
                exact.identityId(),
                exact.versionId(),
                exact.identifier(),
                exact.identifierNormalized()));
  }

  @GetMapping
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public List<StatutoryRegistrationView> list(
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public ResponseEntity<StatutoryRegistrationView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return response(service.current(identityId, asOf));
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public List<StatutoryRegistrationView> history(
      @PathVariable UUID identityId) {
    return service.history(identityId);
  }

  public record RegistrationIdentifierRevealView(
      UUID identityId,
      UUID versionId,
      String identifier,
      String identifierNormalized) {}

  private ResponseEntity<StatutoryRegistrationView> response(
      StatutoryRegistrationView view) {
    return ResponseEntity.ok()
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }
}
