package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.OrganisationService;
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
@RequestMapping("/api/v1")
public class OrganisationController {
  private final OrganisationService service;

  public OrganisationController(OrganisationService service) { this.service = service; }

  @PostMapping("/{collection:legal-entities|payroll-statutory-units|establishments}")
  @PreAuthorize("hasAuthority('organisation.create')")
  public ResponseEntity<OrganisationView> create(@PathVariable String collection,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody OrganisationWriteRequest request) {
    OrganisationView result = service.create(kind(collection), idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/" + collection + "/" + result.identityId()))
        .eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{collection:legal-entities|payroll-statutory-units|establishments}")
  @PreAuthorize("hasAuthority('organisation.read')")
  public List<OrganisationView> list(@PathVariable String collection,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.list(kind(collection), asOf);
  }

  @GetMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}")
  @PreAuthorize("hasAuthority('organisation.read')")
  public ResponseEntity<OrganisationView> current(@PathVariable String collection, @PathVariable UUID identityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    OrganisationView result = service.current(kind(collection), identityId, asOf);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.read')")
  public List<OrganisationView> history(@PathVariable String collection, @PathVariable UUID identityId) {
    return service.history(kind(collection), identityId);
  }

  @PostMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.version.create')")
  public ResponseEntity<OrganisationView> addVersion(@PathVariable String collection, @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody OrganisationWriteRequest request) {
    OrganisationView result = service.addVersion(kind(collection), identityId, idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/" + collection + "/" + identityId
        + "/versions/" + result.versionId())).eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize("hasAuthority('organisation.version.correct')")
  public ResponseEntity<OrganisationView> correct(@PathVariable String collection, @PathVariable UUID identityId,
      @PathVariable UUID versionId, @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody OrganisationWriteRequest request) {
    OrganisationView result = service.correctFuture(kind(collection), identityId, versionId,
        idempotencyKey, request);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/versions/{versionId}/end-date")
  @PreAuthorize("hasAuthority('organisation.version.end-date')")
  public ResponseEntity<OrganisationView> endDate(@PathVariable String collection, @PathVariable UUID identityId,
      @PathVariable UUID versionId, @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody EndDateRequest request) {
    OrganisationView result = service.endDate(kind(collection), identityId, versionId, idempotencyKey,
        request.effectiveTo(), expectedVersion(ifMatch));
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @PostMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('organisation.approve')")
  public ResponseEntity<OrganisationView> approve(@PathVariable String collection, @PathVariable UUID identityId,
      @PathVariable UUID versionId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
    OrganisationView result = service.approve(kind(collection), identityId, versionId, idempotencyKey);
    return ResponseEntity.ok().eTag(Long.toString(result.versionNo())).body(result);
  }

  @GetMapping("/{collection:legal-entities|payroll-statutory-units|establishments}/{identityId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(@PathVariable String collection, @PathVariable UUID identityId) {
    return service.audit(kind(collection), identityId);
  }

  @GetMapping("/organisation-hierarchy")
  @PreAuthorize("hasAuthority('organisation.read')")
  public OrganisationHierarchy hierarchy(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.hierarchy(asOf);
  }

  private OrganisationKind kind(String collection) {
    return switch (collection) {
      case "legal-entities" -> OrganisationKind.LEGAL_ENTITY;
      case "payroll-statutory-units" -> OrganisationKind.PAYROLL_STATUTORY_UNIT;
      case "establishments" -> OrganisationKind.ESTABLISHMENT;
      default -> throw new IllegalArgumentException("Unsupported organisation collection");
    };
  }

  private long expectedVersion(String ifMatch) {
    try {
      return Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("If-Match must contain a numeric version", exception);
    }
  }

  public record EndDateRequest(@NotNull LocalDate effectiveTo) {}
}
