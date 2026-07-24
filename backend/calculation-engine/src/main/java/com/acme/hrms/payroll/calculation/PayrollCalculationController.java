package com.acme.hrms.payroll.calculation;

import static com.acme.hrms.payroll.calculation.PayrollCalculationPermissions.EXECUTE;
import static com.acme.hrms.payroll.calculation.PayrollCalculationPermissions.RECALCULATE;
import static com.acme.hrms.payroll.calculation.PayrollCalculationPermissions.RESULT_READ;
import static com.acme.hrms.payroll.calculation.PayrollCalculationPermissions.TRACE_READ;

import com.acme.hrms.payroll.calculation.internal.application.PayrollCalculationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payroll-cycles/{cycleId}")
public class PayrollCalculationController {
  private final PayrollCalculationService service;

  public PayrollCalculationController(PayrollCalculationService service) {
    this.service = service;
  }

  @PostMapping("/calculation")
  @PreAuthorize("hasAuthority('" + EXECUTE + "')")
  public ResponseEntity<PayrollCalculationResult> calculate(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    PayrollCalculationResult result = service.calculate(
        cycleId,
        idempotencyKey,
        expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @PostMapping("/recalculation")
  @PreAuthorize("hasAuthority('" + RECALCULATE + "')")
  public ResponseEntity<PayrollRecalculationResult> recalculate(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PayrollRecalculationRequest request) {
    PayrollRecalculationResult result = service.recalculate(
        cycleId,
        idempotencyKey,
        expectedVersion(ifMatch),
        request);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @GetMapping("/calculation-requests")
  @PreAuthorize("hasAuthority('" + RESULT_READ + "')")
  public List<PayrollCalculationRequestView> requests(
      @PathVariable UUID cycleId) {
    return service.requests(cycleId);
  }

  @GetMapping("/results")
  @PreAuthorize("hasAuthority('" + RESULT_READ + "')")
  public List<PayrollResultSummaryView> results(
      @PathVariable UUID cycleId) {
    return service.results(cycleId);
  }

  @GetMapping("/results/{resultId}")
  @PreAuthorize("hasAuthority('" + RESULT_READ + "')")
  public PayrollResultDetailView result(
      @PathVariable UUID cycleId,
      @PathVariable UUID resultId) {
    return service.result(cycleId, resultId);
  }

  @GetMapping("/results/{resultId}/trace")
  @PreAuthorize("hasAuthority('" + TRACE_READ + "')")
  public List<PayrollCalculationTraceView> trace(
      @PathVariable UUID cycleId,
      @PathVariable UUID resultId) {
    return service.trace(cycleId, resultId);
  }

  private static long expectedVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version");
    }

    String value = ifMatch.trim();
    if (value.startsWith("W/")) {
      value = value.substring(2).trim();
    }
    if (value.length() >= 2
        && value.startsWith("\"")
        && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    if (!value.matches("[0-9]+")) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version");
    }

    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match version is outside the supported range", exception);
    }
  }
}
