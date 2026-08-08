package com.acme.hrms.payroll.statutory;

import com.acme.hrms.payroll.statutory.internal.application.RegistrationTypeService;
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
@RequestMapping("/api/v1/statutory-registration-types")
public class RegistrationTypeController {
  private final RegistrationTypeService service;

  public RegistrationTypeController(RegistrationTypeService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_TYPE_WRITE)")
  public ResponseEntity<RegistrationTypeView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RegistrationTypeCreateRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    RegistrationTypeView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/statutory-registration-types/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_TYPE_WRITE)")
  public ResponseEntity<RegistrationTypeView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RegistrationTypeVersionWriteRequest request) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    RegistrationTypeView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/statutory-registration-types/"
                    + identityId
                    + "/versions/"
                    + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_APPROVE)")
  public ResponseEntity<RegistrationTypeView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    StatutoryHttpSupport.requireIdempotencyKey(idempotencyKey);
    RegistrationTypeView approved =
        service.approve(
            identityId,
            versionId,
            idempotencyKey,
            StatutoryHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(approved.versionNo()))
        .body(approved);
  }

  @GetMapping
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public List<RegistrationTypeView> list(
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority(T(com.acme.hrms.payroll.statutory.StatutoryPermissions).REGISTRATION_READ)")
  public ResponseEntity<RegistrationTypeView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    RegistrationTypeView view = service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }
}
