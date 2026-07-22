package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.internal.application.PayComponentService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/pay-components")
public class PayComponentController {
  private final PayComponentService service;

  public PayComponentController(PayComponentService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('compensation.component.create')")
  public ResponseEntity<PayComponentView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayComponentWriteRequest request) {
    PayComponentView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/pay-components/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<PayComponentView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public ResponseEntity<PayComponentView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    PayComponentView result = service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.component.read')")
  public List<PayComponentView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize(
      "hasAuthority('compensation.component.version.create')")
  public ResponseEntity<PayComponentView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayComponentWriteRequest request) {
    PayComponentView result =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/pay-components/" + identityId
                + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize(
      "hasAuthority('compensation.component.version.correct')")
  public ResponseEntity<PayComponentView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayComponentWriteRequest request) {
    PayComponentView result = service.correctFuture(
        identityId, versionId, idempotencyKey, request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize(
      "hasAuthority('compensation.component.version.end-date')")
  public ResponseEntity<PayComponentView> endDate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EndDateRequest request) {
    PayComponentView result = service.endDate(
        identityId,
        versionId,
        idempotencyKey,
        request.effectiveTo(),
        expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.component.approve')")
  public ResponseEntity<PayComponentView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    PayComponentView result =
        service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(
      @PathVariable UUID identityId) {
    return service.audit(identityId);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(
          ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version", exception);
    }
  }

  public record EndDateRequest(@NotNull LocalDate effectiveTo) {}
}