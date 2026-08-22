package com.acme.hrms.payroll.employeepayroll;

import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EmployeeBankAccountView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EmployeeBankAccountWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.EvidenceRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchResolveRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.IdentityMismatchWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.ImpactReviewRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentReadinessView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionClearRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentRestrictionWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PayrollIdentifierView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PayrollIdentifierWriteRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.RevealRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.RevealView;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.SuspendRequest;
import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeeIdentityPaymentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/payroll-relationships/{relationshipId}")
public class EmployeeIdentityPaymentController {
  private final EmployeeIdentityPaymentService service;

  public EmployeeIdentityPaymentController(EmployeeIdentityPaymentService service) {
    this.service = service;
  }

  @GetMapping("/identifiers")
  @PreAuthorize("hasAuthority('employee-payroll.identifier.read')")
  public List<PayrollIdentifierView> identifiers(@PathVariable UUID relationshipId) {
    return service.identifiers(relationshipId);
  }

  @PostMapping("/identifiers")
  @PreAuthorize("hasAuthority('employee-payroll.identifier.write')")
  public ResponseEntity<PayrollIdentifierView> writeIdentifier(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PayrollIdentifierWriteRequest request) {
    PayrollIdentifierView view =
        service.writeIdentifier(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @PostMapping("/identifiers/{versionId}/verify")
  @PreAuthorize("hasAuthority('employee-payroll.identifier.verify')")
  public ResponseEntity<PayrollIdentifierView> verifyIdentifier(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvidenceRequest request) {
    return ok(
        service.verifyIdentifier(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/identifiers/{versionId}/approve")
  @PreAuthorize("hasAuthority('employee-payroll.identifier.approve')")
  public ResponseEntity<PayrollIdentifierView> approveIdentifier(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvidenceRequest request) {
    return ok(
        service.approveIdentifier(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/identifiers/{versionId}/reveal")
  @PreAuthorize("hasAuthority('employee-payroll.identifier.reveal')")
  public ResponseEntity<RevealView> revealIdentifier(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @Valid @RequestBody RevealRequest request) {
    return secret(service.revealIdentifier(relationshipId, versionId, request));
  }

  @GetMapping("/identity-mismatches")
  @PreAuthorize("hasAuthority('employee-payroll.identity-mismatch.read')")
  public List<IdentityMismatchView> mismatches(@PathVariable UUID relationshipId) {
    return service.mismatches(relationshipId);
  }

  @PostMapping("/identity-mismatches")
  @PreAuthorize("hasAuthority('employee-payroll.identity-mismatch.write')")
  public ResponseEntity<IdentityMismatchView> createMismatch(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody IdentityMismatchWriteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.createMismatch(relationshipId, key, request));
  }

  @PostMapping("/identity-mismatches/{caseId}/resolve")
  @PreAuthorize("hasAuthority('employee-payroll.identity-mismatch.resolve')")
  public ResponseEntity<IdentityMismatchView> resolveMismatch(
      @PathVariable UUID relationshipId,
      @PathVariable UUID caseId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody IdentityMismatchResolveRequest request) {
    IdentityMismatchView view =
        service.resolveMismatch(
            relationshipId, caseId, key, expectedVersion(ifMatch), request);
    return ResponseEntity.ok()
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @GetMapping("/bank-accounts")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.read')")
  public List<EmployeeBankAccountView> bankAccounts(
      @PathVariable UUID relationshipId) {
    return service.bankAccounts(relationshipId);
  }

  @PostMapping("/bank-accounts")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.write')")
  public ResponseEntity<EmployeeBankAccountView> writeBankAccount(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody EmployeeBankAccountWriteRequest request) {
    EmployeeBankAccountView view =
        service.writeBankAccount(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @PostMapping("/bank-accounts/{versionId}/verify")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.verify')")
  public ResponseEntity<EmployeeBankAccountView> verifyBank(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvidenceRequest request) {
    return ok(
        service.verifyBank(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/bank-accounts/{versionId}/impact-review")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.verify')")
  public ResponseEntity<EmployeeBankAccountView> reviewBankImpact(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ImpactReviewRequest request) {
    return ok(
        service.reviewBankImpact(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/bank-accounts/{versionId}/approve")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.approve')")
  public ResponseEntity<EmployeeBankAccountView> approveBank(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvidenceRequest request) {
    return ok(
        service.approveBank(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/bank-accounts/{versionId}/suspend")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.approve')")
  public ResponseEntity<EmployeeBankAccountView> suspendBank(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody SuspendRequest request) {
    return ok(
        service.suspendBank(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/bank-accounts/{versionId}/reveal")
  @PreAuthorize("hasAuthority('employee-payroll.bank-account.reveal')")
  public ResponseEntity<RevealView> revealBank(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @Valid @RequestBody RevealRequest request) {
    return secret(service.revealBank(relationshipId, versionId, request));
  }

  @GetMapping("/payment-instructions")
  @PreAuthorize("hasAuthority('employee-payroll.payment-instruction.read')")
  public List<PaymentInstructionView> instructions(
      @PathVariable UUID relationshipId) {
    return service.instructions(relationshipId);
  }

  @PostMapping("/payment-instructions")
  @PreAuthorize("hasAuthority('employee-payroll.payment-instruction.write')")
  public ResponseEntity<PaymentInstructionView> writeInstruction(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PaymentInstructionWriteRequest request) {
    PaymentInstructionView view =
        service.writeInstruction(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @PostMapping("/payment-instructions/{versionId}/impact-review")
  @PreAuthorize("hasAuthority('employee-payroll.payment-instruction.write')")
  public ResponseEntity<PaymentInstructionView> reviewInstructionImpact(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ImpactReviewRequest request) {
    return ok(
        service.reviewInstructionImpact(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @PostMapping("/payment-instructions/{versionId}/approve")
  @PreAuthorize("hasAuthority('employee-payroll.payment-instruction.approve')")
  public ResponseEntity<PaymentInstructionView> approveInstruction(
      @PathVariable UUID relationshipId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvidenceRequest request) {
    return ok(
        service.approveInstruction(
            relationshipId, versionId, key, expectedVersion(ifMatch), request));
  }

  @GetMapping("/payment-restrictions")
  @PreAuthorize("hasAuthority('employee-payroll.payment-restriction.read')")
  public List<PaymentRestrictionView> restrictions(
      @PathVariable UUID relationshipId) {
    return service.restrictions(relationshipId);
  }

  @PostMapping("/payment-restrictions")
  @PreAuthorize("hasAuthority('employee-payroll.payment-restriction.write')")
  public ResponseEntity<PaymentRestrictionView> createRestriction(
      @PathVariable UUID relationshipId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PaymentRestrictionWriteRequest request) {
    PaymentRestrictionView view =
        service.createRestriction(relationshipId, key, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @PostMapping("/payment-restrictions/{restrictionId}/clear")
  @PreAuthorize("hasAuthority('employee-payroll.payment-restriction.clear')")
  public ResponseEntity<PaymentRestrictionView> clearRestriction(
      @PathVariable UUID relationshipId,
      @PathVariable UUID restrictionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PaymentRestrictionClearRequest request) {
    PaymentRestrictionView view =
        service.clearRestriction(
            relationshipId, restrictionId, key,
            expectedVersion(ifMatch), request);
    return ResponseEntity.ok()
        .eTag(Long.toString(view.versionNo()))
        .body(view);
  }

  @GetMapping("/payment-readiness")
  @PreAuthorize("hasAuthority('employee-payroll.payment-readiness.read')")
  public PaymentReadinessView readiness(
      @PathVariable UUID relationshipId,
      @RequestParam String currencyCode,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return service.readiness(relationshipId, currencyCode, asOf);
  }

  private <T> ResponseEntity<T> ok(T body) {
    long version = 0;
    if (body instanceof PayrollIdentifierView view) {
      version = view.versionNo();
    } else if (body instanceof EmployeeBankAccountView view) {
      version = view.versionNo();
    } else if (body instanceof PaymentInstructionView view) {
      version = view.versionNo();
    }
    return ResponseEntity.ok()
        .eTag(Long.toString(version))
        .body(body);
  }

  private ResponseEntity<RevealView> secret(RevealView view) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
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
