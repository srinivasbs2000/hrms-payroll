package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.organisation.internal.application.JurisdictionResolutionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jurisdiction-overrides")
public class JurisdictionOverrideController {
  private final JurisdictionResolutionService service;

  public JurisdictionOverrideController(
      JurisdictionResolutionService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('organisation.create')")
  public ResponseEntity<JurisdictionOverrideView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody JurisdictionOverrideWriteRequest request) {
    JurisdictionOverrideView created =
        service.createOverride(idempotencyKey, request);
    return ResponseEntity
        .created(
            URI.create(
                "/api/v1/jurisdiction-overrides/" + created.id()))
        .eTag(Long.toString(created.versionNo()))
        .body(created);
  }

  @PostMapping("/{overrideId}/approval")
  @PreAuthorize("hasAuthority('organisation.approve')")
  public ResponseEntity<JurisdictionOverrideView> approve(
      @PathVariable UUID overrideId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    JurisdictionOverrideView approved =
        service.approveOverride(
            overrideId,
            idempotencyKey,
            expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(approved.versionNo()))
        .body(approved);
  }

  @GetMapping("/{overrideId}")
  @PreAuthorize("hasAuthority('organisation.read')")
  public ResponseEntity<JurisdictionOverrideView> get(
      @PathVariable UUID overrideId) {
    JurisdictionOverrideView view = service.override(overrideId);
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
