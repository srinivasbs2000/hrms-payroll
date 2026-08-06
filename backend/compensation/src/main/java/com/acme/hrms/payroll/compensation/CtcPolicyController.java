package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.internal.application.CtcPolicyService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/ctc-policies")
public class CtcPolicyController {
  private final CtcPolicyService service;

  public CtcPolicyController(CtcPolicyService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('compensation.ctc-policy.create')")
  public ResponseEntity<CtcPolicyView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CtcPolicyCreateRequest request) {
    CtcPolicyView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/ctc-policies/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compensation.ctc-policy.read')")
  public List<CtcPolicyView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('compensation.ctc-policy.read')")
  public ResponseEntity<CtcPolicyView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    CtcPolicyView result = service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.ctc-policy.read')")
  public List<CtcPolicyView> history(
      @PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize(
      "hasAuthority('compensation.ctc-policy.version.create')")
  public ResponseEntity<CtcPolicyView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CtcPolicyVersionWriteRequest request) {
    CtcPolicyView result =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/ctc-policies/" + identityId
                + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize(
      "hasAuthority('compensation.ctc-policy.version.correct')")
  public ResponseEntity<CtcPolicyView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CtcPolicyVersionWriteRequest request) {
    CtcPolicyView result = service.correctFuture(
        identityId,
        versionId,
        idempotencyKey,
        request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize(
      "hasAuthority('compensation.ctc-policy.version.end-date')")
  public ResponseEntity<CtcPolicyView> endDate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EndDateRequest request) {
    CtcPolicyView result = service.endDate(
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
  @PreAuthorize("hasAuthority('compensation.ctc-policy.approve')")
  public ResponseEntity<CtcPolicyView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    CtcPolicyView result =
        service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/retirement")
  @PreAuthorize("hasAuthority('compensation.ctc-policy.retire')")
  public ResponseEntity<CtcPolicyView> retire(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RetirementRequest request) {
    CtcPolicyView result = service.retire(
        identityId,
        idempotencyKey,
        request.effectiveDate(),
        expectedVersion(ifMatch),
        request.reason());
    return ResponseEntity.ok()
        .eTag(Long.toString(result.identityVersionNo()))
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
          "If-Match must contain a numeric version",
          exception);
    }
  }

  public record EndDateRequest(@NotNull LocalDate effectiveTo) {}

  public record RetirementRequest(
      @NotNull LocalDate effectiveDate,
      @NotBlank @Size(max = 500) String reason) {}
}
