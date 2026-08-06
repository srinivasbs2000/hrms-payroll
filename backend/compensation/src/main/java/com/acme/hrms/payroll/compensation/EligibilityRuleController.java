package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.EligibilityRuleView.EvaluationRequest;
import com.acme.hrms.payroll.compensation.EligibilityRuleView.EvaluationView;
import com.acme.hrms.payroll.compensation.internal.application.EligibilityRuleService;
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
@RequestMapping("/api/v1/eligibility-rules")
public class EligibilityRuleController {
  private final EligibilityRuleService service;

  public EligibilityRuleController(EligibilityRuleService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('compensation.eligibility-rule.create')")
  public ResponseEntity<EligibilityRuleView> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EligibilityRuleCreateRequest request) {
    EligibilityRuleView result =
        service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/eligibility-rules/" + result.identityId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compensation.eligibility-rule.read')")
  public List<EligibilityRuleView> list(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    return service.list(asOf);
  }

  @GetMapping("/{identityId}")
  @PreAuthorize("hasAuthority('compensation.eligibility-rule.read')")
  public ResponseEntity<EligibilityRuleView> current(
      @PathVariable UUID identityId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate asOf) {
    EligibilityRuleView result =
        service.current(identityId, asOf);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/versions")
  @PreAuthorize("hasAuthority('compensation.eligibility-rule.read')")
  public List<EligibilityRuleView> history(
      @PathVariable UUID identityId) {
    return service.history(identityId);
  }

  @PostMapping("/{identityId}/versions")
  @PreAuthorize(
      "hasAuthority('compensation.eligibility-rule.version.create')")
  public ResponseEntity<EligibilityRuleView> addVersion(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EligibilityRuleVersionWriteRequest request) {
    EligibilityRuleView result =
        service.addVersion(identityId, idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/eligibility-rules/" + identityId
                + "/versions/" + result.versionId()))
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/corrections")
  @PreAuthorize(
      "hasAuthority('compensation.eligibility-rule.version.correct')")
  public ResponseEntity<EligibilityRuleView> correct(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody EligibilityRuleVersionWriteRequest request) {
    EligibilityRuleView result = service.correctFuture(
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
      "hasAuthority('compensation.eligibility-rule.version.end-date')")
  public ResponseEntity<EligibilityRuleView> endDate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EndDateRequest request) {
    EligibilityRuleView result = service.endDate(
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
  @PreAuthorize(
      "hasAuthority('compensation.eligibility-rule.approve')")
  public ResponseEntity<EligibilityRuleView> approve(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    EligibilityRuleView result =
        service.approve(identityId, versionId, idempotencyKey);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @PostMapping("/{identityId}/versions/{versionId}/evaluation")
  @PreAuthorize(
      "hasAuthority('compensation.eligibility-rule.evaluate')")
  public EvaluationView evaluate(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId,
      @Valid @RequestBody EvaluationRequest request) {
    request.validate();
    return service.evaluate(identityId, versionId, request.facts());
  }

  @PostMapping("/{identityId}/retirement")
  @PreAuthorize(
      "hasAuthority('compensation.eligibility-rule.retire')")
  public ResponseEntity<EligibilityRuleView> retire(
      @PathVariable UUID identityId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody RetirementRequest request) {
    EligibilityRuleView result = service.retire(
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
