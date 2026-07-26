package com.acme.hrms.payroll.statutory;

import static com.acme.hrms.payroll.statutory.StatutoryPermissions.BALANCE_READ;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.EVALUATION_EXECUTE;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.EVALUATION_READ;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.LEDGER_CORRECT;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.LEDGER_POST;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.LEDGER_READ;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.RECONCILIATION_READ;
import static com.acme.hrms.payroll.statutory.StatutoryPermissions.REMITTANCE_READ;

import com.acme.hrms.payroll.statutory.internal.application.StatutoryService;
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
@RequestMapping("/api/v1/payroll-cycles/{cycleId}/statutory")
public class StatutoryController {
  private final StatutoryService service;

  public StatutoryController(StatutoryService service) {
    this.service = service;
  }

  @PostMapping("/evaluations")
  @PreAuthorize("hasAuthority('" + EVALUATION_EXECUTE + "')")
  public ResponseEntity<StatutoryEvaluationExecution> evaluate(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody StatutoryEvaluationCommand command) {
    StatutoryEvaluationExecution result = service.evaluate(
        cycleId,
        idempotencyKey,
        StatutoryHttpSupport.expectedVersion(ifMatch),
        command);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @PostMapping("/postings")
  @PreAuthorize("hasAuthority('" + LEDGER_POST + "')")
  public ResponseEntity<StatutoryLedgerPostingExecution> post(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody StatutoryLedgerPostingCommand command) {
    StatutoryLedgerPostingExecution result = service.post(
        cycleId,
        idempotencyKey,
        StatutoryHttpSupport.expectedVersion(ifMatch),
        command);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @PostMapping("/corrections")
  @PreAuthorize("hasAuthority('" + LEDGER_CORRECT + "')")
  public ResponseEntity<StatutoryCorrectionExecution> correct(
      @PathVariable UUID cycleId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody StatutoryCorrectionCommand command) {
    StatutoryCorrectionExecution result = service.correct(
        cycleId,
        idempotencyKey,
        StatutoryHttpSupport.expectedVersion(ifMatch),
        command);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.cycleVersionNo()))
        .body(result);
  }

  @GetMapping("/evaluations")
  @PreAuthorize("hasAuthority('" + EVALUATION_READ + "')")
  public List<StatutoryEvaluationRequestView> evaluations(
      @PathVariable UUID cycleId) {
    return service.evaluations(cycleId);
  }

  @GetMapping("/results")
  @PreAuthorize("hasAuthority('" + EVALUATION_READ + "')")
  public List<StatutoryResultView> results(@PathVariable UUID cycleId) {
    return service.results(cycleId);
  }

  @GetMapping("/ledger-batches")
  @PreAuthorize("hasAuthority('" + LEDGER_READ + "')")
  public List<StatutoryLedgerBatchView> ledgerBatches(
      @PathVariable UUID cycleId) {
    return service.ledgerBatches(cycleId);
  }

  @GetMapping("/ledger-entries")
  @PreAuthorize("hasAuthority('" + LEDGER_READ + "')")
  public List<StatutoryLedgerEntryView> ledgerEntries(
      @PathVariable UUID cycleId) {
    return service.ledgerEntries(cycleId);
  }

  @GetMapping("/balance-snapshots")
  @PreAuthorize("hasAuthority('" + BALANCE_READ + "')")
  public List<StatutoryBalanceSnapshotView> balances(
      @PathVariable UUID cycleId) {
    return service.balances(cycleId);
  }

  @GetMapping("/reconciliations")
  @PreAuthorize("hasAuthority('" + RECONCILIATION_READ + "')")
  public List<StatutoryReconciliationView> reconciliations(
      @PathVariable UUID cycleId) {
    return service.reconciliations(cycleId);
  }

  @GetMapping("/remittance-summaries")
  @PreAuthorize("hasAuthority('" + REMITTANCE_READ + "')")
  public List<StatutoryRemittanceSummaryView> remittances(
      @PathVariable UUID cycleId) {
    return service.remittances(cycleId);
  }
}
