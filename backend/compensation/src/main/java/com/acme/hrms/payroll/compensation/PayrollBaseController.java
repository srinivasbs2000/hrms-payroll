package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.internal.application.PayrollBaseService;
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
@RequestMapping("/api/v1/payroll-bases")
public class PayrollBaseController {
  private final PayrollBaseService service;

  public PayrollBaseController(PayrollBaseService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('compensation.base.create')")
  public ResponseEntity<PayrollBaseView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollBaseCreateRequest request) {
    PayrollBaseView result = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/payroll-bases/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compensation.base.read')")
  public List<PayrollBaseView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('compensation.base.read')")
  public ResponseEntity<PayrollBaseView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    PayrollBaseView result = service.current(identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.base.read')")
  public List<PayrollBaseView> history(@PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.base.version.create')")
  public ResponseEntity<PayrollBaseView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollBaseVersionWriteRequest request) {
    PayrollBaseView result = service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/payroll-bases/" + identityId + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize("hasAuthority('compensation.base.version.correct')")
  public ResponseEntity<PayrollBaseView> correctVersion(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PayrollBaseVersionWriteRequest request) {
    PayrollBaseView result =
        service.correctFuture(identityId, versionId, idempotencyKey, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('compensation.base.approve')")
  public ResponseEntity<PayrollBaseView> approveVersion(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    PayrollBaseView result = service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('compensation.base.version.end-date')")
  public ResponseEntity<PayrollBaseView> endDateVersion(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EndDateRequest request) {
    PayrollBaseView result = service.endDate(
        identityId,
        versionId,
        idempotencyKey,
        request.effectiveTo(),
        expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/retirement")
  @PreAuthorize("hasAuthority('compensation.base.retire')")
  public ResponseEntity<PayrollBaseView> retire(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RetirementRequest request) {
    PayrollBaseView result = service.retire(
        identityId,
        idempotencyKey,
        request.effectiveDate(),
        expectedVersion(ifMatch),
        request.reason());
    return ResponseEntity.ok().eTag(Long.toString(result.identityVersionNo())).body(result);
  }

  @GetMapping("/{identityId}/memberships")
  @PreAuthorize("hasAuthority('compensation.base.read')")
  public List<ComponentBaseMembershipView> memberships(
      @PathVariable UUID identityId,
      @RequestParam(defaultValue = "false") boolean includeHistory,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.memberships(identityId, includeHistory, asOf);
  }

  @PostMapping("/{identityId}/memberships")
  @PreAuthorize("hasAuthority('compensation.base.membership.create')")
  public ResponseEntity<ComponentBaseMembershipView> createMembership(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ComponentBaseMembershipWriteRequest request) {
    ComponentBaseMembershipView result =
        service.createMembership(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/payroll-bases/" + identityId
                + "/memberships/" + result.membershipId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/memberships/{membershipId}/corrections")
  @PreAuthorize("hasAuthority('compensation.base.membership.correct')")
  public ResponseEntity<ComponentBaseMembershipView> correctMembership(
      @PathVariable UUID identityId,
      @PathVariable UUID membershipId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ComponentBaseMembershipWriteRequest request) {
    ComponentBaseMembershipView result = service.correctMembership(
        identityId, membershipId, idempotencyKey, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/memberships/{membershipId}/approval")
  @PreAuthorize("hasAuthority('compensation.base.membership.approve')")
  public ResponseEntity<ComponentBaseMembershipView> approveMembership(
      @PathVariable UUID identityId,
      @PathVariable UUID membershipId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    ComponentBaseMembershipView result =
        service.approveMembership(identityId, membershipId, idempotencyKey);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{identityId}/memberships/{membershipId}/end-date")
  @PreAuthorize("hasAuthority('compensation.base.membership.end-date')")
  public ResponseEntity<ComponentBaseMembershipView> endDateMembership(
      @PathVariable UUID identityId,
      @PathVariable UUID membershipId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EndDateRequest request) {
    ComponentBaseMembershipView result = service.endDateMembership(
        identityId,
        membershipId,
        idempotencyKey,
        request.effectiveTo(),
        expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(@PathVariable UUID identityId) {
    return service.audit(identityId);
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version", exception);
    }
  }

  public record EndDateRequest(@NotNull LocalDate effectiveTo) {}

  public record RetirementRequest(
      @NotNull LocalDate effectiveDate,
      @NotBlank @Size(max = 500) String reason) {}
}
