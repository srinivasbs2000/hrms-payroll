package com.acme.hrms.payroll.payrolloperations;

import static com.acme.hrms.payroll.payrolloperations.PayrollOperationsPermissions.INPUTS_READ;
import static com.acme.hrms.payroll.payrolloperations.PayrollOperationsPermissions.INPUTS_SEAL;

import com.acme.hrms.payroll.payrolloperations.internal.application.PayrollInputSnapshotService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll-cycles/{cycleId}")
public class PayrollInputSnapshotController {
  private final PayrollInputSnapshotService service;

  public PayrollInputSnapshotController(PayrollInputSnapshotService service) {
    this.service = service;
  }

  @PostMapping("/seal-inputs")
  @PreAuthorize("hasAuthority('" + INPUTS_SEAL + "')")
  public ResponseEntity<PayrollInputSealResult> seal(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch) {
    PayrollInputSealResult result = service.seal(
        cycleId,
        idempotencyKey,
        PayrollOperationsHttpSupport.expectedVersion(ifMatch));
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @GetMapping("/input-snapshots")
  @PreAuthorize("hasAuthority('" + INPUTS_READ + "')")
  public List<PayrollInputSnapshotView> list(@PathVariable UUID cycleId) {
    return service.list(cycleId);
  }

  @GetMapping("/input-snapshots/{snapshotId}")
  @PreAuthorize("hasAuthority('" + INPUTS_READ + "')")
  public PayrollInputSnapshotDetailView get(
      @PathVariable UUID cycleId,
      @PathVariable UUID snapshotId) {
    return service.get(cycleId, snapshotId);
  }
}
