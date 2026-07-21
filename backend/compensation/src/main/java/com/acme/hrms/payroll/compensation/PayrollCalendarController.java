package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.internal.application
    .PayrollCalendarService;
import com.acme.hrms.payroll.platform.AuditReader;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payroll-calendars")
public class PayrollCalendarController {
  private final PayrollCalendarService service;

  public PayrollCalendarController(
      PayrollCalendarService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('calendar.create')")
  public ResponseEntity<PayrollCalendarView> create(
      @RequestHeader("Idempotency-Key")
      String idempotencyKey,
      @Valid @RequestBody
      PayrollCalendarWriteRequest request) {
    PayrollCalendarView result =
        service.create(idempotencyKey, request);
    return ResponseEntity
        .created(URI.create(
            "/api/v1/payroll-calendars/" + result.id()))
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('calendar.read')")
  public List<PayrollCalendarView> list() {
    return service.list();
  }

  @PostMapping("/{calendarId}/periods")
  @PreAuthorize(
      "hasAuthority('calendar.period.generate')")
  public ResponseEntity<List<PayPeriodView>> generate(
      @PathVariable UUID calendarId,
      @RequestHeader("Idempotency-Key")
      String idempotencyKey,
      @Valid @RequestBody
      GeneratePeriodsRequest request) {
    List<PayPeriodView> result = service.generate(
        calendarId,
        idempotencyKey,
        request);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/payroll-calendars/"
                    + calendarId
                    + "/periods?year="
                    + request.year()))
        .body(result);
  }

  @GetMapping("/{calendarId}/periods")
  @PreAuthorize("hasAuthority('calendar.read')")
  public List<PayPeriodView> periods(
      @PathVariable UUID calendarId,
      @RequestParam(required = false)
      Integer year) {
    return service.periods(calendarId, year);
  }

  @GetMapping("/{calendarId}/audit")
  @PreAuthorize("hasAuthority('audit.read')")
  public List<AuditReader.AuditEventView> audit(
      @PathVariable UUID calendarId) {
    return service.audit(calendarId);
  }
}
