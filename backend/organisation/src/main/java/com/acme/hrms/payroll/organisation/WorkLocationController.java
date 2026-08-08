package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.WorkLocationService;
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
@RequestMapping("/api/v1/work-locations")
public class WorkLocationController {
  private final WorkLocationService service;

  public WorkLocationController(WorkLocationService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('organisation.create')")
  public ResponseEntity<WorkLocationView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody WorkLocationCreateRequest request) {
    WorkLocationView created = service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create("/api/v1/work-locations/" + created.identityId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('organisation.version.create')")
  public ResponseEntity<WorkLocationView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody WorkLocationVersionWriteRequest request) {
    WorkLocationView created =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/work-locations/"
                    + identityId
                    + "/versions/"
                    + created.versionId()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{identityId}/versions/{versionId}/approval")
  @PreAuthorize("hasAuthority('organisation.approve')")
  public ResponseEntity<WorkLocationView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    WorkLocationView approved =
        service.approve(
            identityId,
            versionId,
            idempotencyKey,
            expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(approved.versionNo()))
        .body(approved);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('organisation.read')")
  public List<WorkLocationView> list(
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('organisation.read')")
  public ResponseEntity<WorkLocationView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    WorkLocationView view = service.current(identityId, asOf);
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
          "If-Match must contain a numeric version", exception);
    }
  }
}
