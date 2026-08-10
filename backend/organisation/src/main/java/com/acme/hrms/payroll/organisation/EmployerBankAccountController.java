package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.EmployerBankAccountService;
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
@RequestMapping("/api/v1/employer-bank-accounts")
public class EmployerBankAccountController {
  private final EmployerBankAccountService service;

  public EmployerBankAccountController(EmployerBankAccountService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('organisation.bank-account.write')")
  public ResponseEntity<EmployerBankAccountView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EmployerBankAccountCreateRequest request) {
    EmployerBankAccountView created =
        service.create(idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/employer-bank-accounts/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.bank-account.write')")
  public ResponseEntity<EmployerBankAccountView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EmployerBankAccountVersionWriteRequest request) {
    EmployerBankAccountView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/employer-bank-accounts/"
                    + identityId
                    + "/versions/"
                    + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions/{versionId}/submit")
  @PreAuthorize("hasAuthority('organisation.bank-account.write')")
  public ResponseEntity<EmployerBankAccountView> submit(
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
  @PreAuthorize("hasAuthority('organisation.bank-account.verify')")
  public ResponseEntity<EmployerBankAccountView> verify(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EmployerBankAccountEvidenceRequest request) {
    return ok(
        service.verify(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/request-approval")
  @PreAuthorize("hasAuthority('organisation.bank-account.verify')")
  public ResponseEntity<EmployerBankAccountView> requestApproval(
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
  @PreAuthorize("hasAuthority('organisation.bank-account.approve')")
  public ResponseEntity<EmployerBankAccountView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EmployerBankAccountEvidenceRequest request) {
    return ok(
        service.approve(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/reject")
  @PreAuthorize("hasAuthority('organisation.bank-account.approve')")
  public ResponseEntity<EmployerBankAccountView> reject(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EmployerBankAccountRejectRequest request) {
    return ok(
        service.reject(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @PostMapping("/{identityId}/versions/{versionId}/suspend")
  @PreAuthorize("hasAuthority('organisation.bank-account.approve')")
  public ResponseEntity<EmployerBankAccountView> suspend(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EmployerBankAccountSuspendRequest request) {
    return ok(
        service.suspend(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch),
            request));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('organisation.bank-account.read')")
  public List<EmployerBankAccountView> list(
      @RequestParam(required = false) String ownerKind,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) String currencyCode,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.list(ownerKind, ownerId, currencyCode, asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('organisation.bank-account.read')")
  public ResponseEntity<EmployerBankAccountView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return ok(service.current(identityId, asOf));
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.bank-account.read')")
  public List<EmployerBankAccountView> history(
      @PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions/{versionId}/reveal")
  @PreAuthorize("hasAuthority('organisation.bank-account.reveal')")
  public ResponseEntity<EmployerBankAccountRevealView> reveal(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @Valid @RequestBody EmployerBankAccountRevealRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .body(service.reveal(identityId, versionId, request));
  }

  private ResponseEntity<EmployerBankAccountView> ok(
      EmployerBankAccountView view) {
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
