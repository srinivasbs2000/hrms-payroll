package com.acme.hrms.payroll.employeepayroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EmployeeIdentityPaymentModels {
  private EmployeeIdentityPaymentModels() {}

  public record EvidenceRequest(String evidenceRef) {
    public void validate() {
      requireText(evidenceRef, "evidenceRef", 240);
    }
  }

  public record RevealRequest(String reason) {
    public void validate() {
      requireText(reason, "reason", 500);
    }
  }

  public record RevealView(
      UUID identityId,
      UUID versionId,
      String kind,
      String value,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record ImpactReviewRequest(String evidenceRef) {
    public void validate() {
      requireText(evidenceRef, "evidenceRef", 240);
    }
  }

  public record SuspendRequest(String reason) {
    public void validate() {
      requireText(reason, "reason", 500);
    }
  }

  public record PayrollIdentifierWriteRequest(
      UUID identityId,
      String schemeCode,
      String value,
      String sourceAuthority,
      String sourceReference,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      requireCode(schemeCode, "schemeCode", 40);
      requireText(value, "value", 128);
      optionalText(sourceAuthority, "sourceAuthority", 120);
      optionalText(sourceReference, "sourceReference", 240);
      requireRange(effectiveFrom, effectiveTo);
    }
  }

  public record PayrollIdentifierView(
      UUID identityId,
      UUID payrollRelationshipId,
      String schemeCode,
      String identityStatus,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String maskedValue,
      String sourceAuthority,
      String sourceReference,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String lifecycleStatus,
      String verificationEvidenceRef,
      Instant verifiedAt,
      String verifiedBy,
      Instant approvedAt,
      String approvedBy,
      String approvalEvidenceRef,
      UUID supersedesVersionId) {}

  public record IdentityMismatchWriteRequest(
      String affectedField,
      String sourceKind,
      String sourceAuthority,
      String sourceReference,
      String authoritativeValue,
      String observedValue,
      String classification,
      String paymentImpact,
      String correctionOwner) {
    public void validate() {
      requireCode(affectedField, "affectedField", 40);
      requireCode(sourceKind, "sourceKind", 40);
      optionalText(sourceAuthority, "sourceAuthority", 120);
      optionalText(sourceReference, "sourceReference", 240);
      if ((authoritativeValue == null || authoritativeValue.isBlank())
          && (observedValue == null || observedValue.isBlank())) {
        throw new IllegalArgumentException(
            "At least one comparison value is required");
      }
      requireCode(classification, "classification", 40);
      String impact = normalize(paymentImpact);
      if (!List.of("BLOCKING", "WARNING", "INFORMATIONAL").contains(impact)) {
        throw new IllegalArgumentException(
            "paymentImpact must be BLOCKING, WARNING or INFORMATIONAL");
      }
      requireText(correctionOwner, "correctionOwner", 120);
    }
  }

  public record IdentityMismatchResolveRequest(
      String resolution,
      String reason,
      String evidenceRef) {
    public void validate() {
      String value = normalize(resolution);
      if (!List.of(
              "CORRECTED_AT_SOURCE",
              "ACCEPTED_VARIANCE",
              "FALSE_POSITIVE")
          .contains(value)) {
        throw new IllegalArgumentException("Unsupported mismatch resolution");
      }
      requireText(reason, "reason", 500);
      requireText(evidenceRef, "evidenceRef", 240);
    }
  }

  public record IdentityMismatchView(
      UUID id,
      UUID payrollRelationshipId,
      long versionNo,
      String affectedField,
      String sourceKind,
      String sourceAuthority,
      String sourceReference,
      String classification,
      String paymentImpact,
      String correctionOwner,
      String status,
      Instant detectedAt,
      Instant resolvedAt,
      String resolvedBy) {}

  public record EmployeeBankAccountWriteRequest(
      UUID identityId,
      String code,
      String bankName,
      String branchName,
      String routingCode,
      String accountHolderName,
      String currencyCode,
      String accountNumber,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      requireCode(code, "code", 60);
      requireText(bankName, "bankName", 160);
      optionalText(branchName, "branchName", 160);
      optionalText(routingCode, "routingCode", 80);
      requireText(accountHolderName, "accountHolderName", 160);
      requireCurrency(currencyCode);
      requireText(accountNumber, "accountNumber", 128);
      requireRange(effectiveFrom, effectiveTo);
    }
  }

  public record EmployeeBankAccountView(
      UUID identityId,
      UUID payrollRelationshipId,
      String code,
      String identityStatus,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String bankName,
      String branchName,
      String routingCode,
      String maskedAccountHolderName,
      String currencyCode,
      String maskedAccountNumber,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String lifecycleStatus,
      String verificationEvidenceRef,
      Instant verifiedAt,
      String verifiedBy,
      Instant impactReviewedAt,
      String impactReviewedBy,
      String impactReviewEvidenceRef,
      Instant approvedAt,
      String approvedBy,
      String approvalEvidenceRef,
      Instant suspendedAt,
      String suspendedBy,
      String suspensionReason,
      UUID supersedesVersionId) {}

  public record PaymentInstructionLineRequest(
      int lineSequence,
      UUID employeeBankAccountVersionId,
      String lineType,
      BigDecimal percentage,
      BigDecimal fixedAmount) {
    public void validate() {
      if (lineSequence <= 0) {
        throw new IllegalArgumentException("lineSequence must be positive");
      }
      if (employeeBankAccountVersionId == null) {
        throw new IllegalArgumentException(
            "employeeBankAccountVersionId is required");
      }
      String type = normalize(lineType);
      switch (type) {
        case "PERCENTAGE" -> {
          if (percentage == null
              || percentage.signum() <= 0
              || percentage.compareTo(new BigDecimal("100")) > 0
              || fixedAmount != null) {
            throw new IllegalArgumentException(
                "PERCENTAGE line requires percentage in (0,100] and no fixedAmount");
          }
        }
        case "FIXED_AMOUNT" -> {
          if (fixedAmount == null
              || fixedAmount.signum() <= 0
              || percentage != null) {
            throw new IllegalArgumentException(
                "FIXED_AMOUNT line requires positive fixedAmount and no percentage");
          }
        }
        case "REMAINING_BALANCE" -> {
          if (percentage != null || fixedAmount != null) {
            throw new IllegalArgumentException(
                "REMAINING_BALANCE line cannot define percentage or fixedAmount");
          }
        }
        default -> throw new IllegalArgumentException("Unsupported lineType");
      }
    }
  }

  public record PaymentInstructionWriteRequest(
      UUID identityId,
      String code,
      String currencyCode,
      String allocationMode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      List<PaymentInstructionLineRequest> lines) {
    public void validate() {
      requireCode(code, "code", 60);
      requireCurrency(currencyCode);
      String mode = normalize(allocationMode);
      if (!List.of("PERCENTAGE", "FIXED_THEN_REMAINDER").contains(mode)) {
        throw new IllegalArgumentException("Unsupported allocationMode");
      }
      requireRange(effectiveFrom, effectiveTo);
      if (lines == null || lines.isEmpty()) {
        throw new IllegalArgumentException(
            "Payment instruction requires at least one line");
      }
      lines.forEach(PaymentInstructionLineRequest::validate);
      long distinct = lines.stream().map(PaymentInstructionLineRequest::lineSequence)
          .distinct().count();
      if (distinct != lines.size()) {
        throw new IllegalArgumentException("lineSequence values must be unique");
      }
      if ("PERCENTAGE".equals(mode)) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentInstructionLineRequest line : lines) {
          if (!"PERCENTAGE".equals(normalize(line.lineType()))) {
            throw new IllegalArgumentException(
                "PERCENTAGE mode allows percentage lines only");
          }
          total = total.add(line.percentage());
        }
        if (total.compareTo(new BigDecimal("100")) != 0) {
          throw new IllegalArgumentException(
              "PERCENTAGE allocation must total exactly 100");
        }
      } else {
        long fixed = lines.stream()
            .filter(line -> "FIXED_AMOUNT".equals(normalize(line.lineType())))
            .count();
        long remainder = lines.stream()
            .filter(line -> "REMAINING_BALANCE".equals(normalize(line.lineType())))
            .count();
        long percentage = lines.stream()
            .filter(line -> "PERCENTAGE".equals(normalize(line.lineType())))
            .count();
        if (fixed < 1 || remainder != 1 || percentage != 0
            || fixed + remainder != lines.size()) {
          throw new IllegalArgumentException(
              "FIXED_THEN_REMAINDER requires fixed lines and exactly one remaining balance");
        }
      }
    }
  }

  public record PaymentInstructionLineView(
      UUID id,
      int lineSequence,
      UUID employeeBankAccountVersionId,
      String lineType,
      BigDecimal percentage,
      BigDecimal fixedAmount) {}

  public record PaymentInstructionView(
      UUID identityId,
      UUID payrollRelationshipId,
      String code,
      String identityStatus,
      UUID versionId,
      int versionSequence,
      long versionNo,
      String currencyCode,
      String allocationMode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String lifecycleStatus,
      Instant impactReviewedAt,
      String impactReviewedBy,
      String impactReviewEvidenceRef,
      Instant approvedAt,
      String approvedBy,
      String approvalEvidenceRef,
      UUID supersedesVersionId,
      List<PaymentInstructionLineView> lines) {}

  public record PaymentRestrictionWriteRequest(
      String restrictionKind,
      String sourceReference,
      String reasonCode,
      String evidenceRef,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    public void validate() {
      String kind = normalize(restrictionKind);
      if (!List.of("FRAUD", "SECURITY", "BENEFICIARY").contains(kind)) {
        throw new IllegalArgumentException(
            "restrictionKind must be FRAUD, SECURITY or BENEFICIARY");
      }
      requireText(sourceReference, "sourceReference", 240);
      requireCode(reasonCode, "reasonCode", 80);
      requireText(evidenceRef, "evidenceRef", 240);
      requireRange(effectiveFrom, effectiveTo);
    }
  }

  public record PaymentRestrictionClearRequest(String evidenceRef) {
    public void validate() {
      requireText(evidenceRef, "evidenceRef", 240);
    }
  }

  public record PaymentRestrictionView(
      UUID id,
      UUID payrollRelationshipId,
      long versionNo,
      String restrictionKind,
      String sourceReference,
      String reasonCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String currentState,
      Instant createdAt,
      String createdBy,
      String latestEventType,
      Instant latestEventAt,
      String latestEventActor) {}

  public record PaymentReadinessFindingView(
      String severity,
      String code,
      String detail) {}

  public record PaymentReadinessView(
      UUID payrollRelationshipId,
      String currencyCode,
      LocalDate asOf,
      boolean ready,
      List<PaymentReadinessFindingView> findings) {}

  static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  static void requireCode(String value, String name, int max) {
    String normalized = normalize(value);
    if (normalized.length() < 2
        || normalized.length() > max
        || !normalized.matches("[A-Z][A-Z0-9_]*")) {
      throw new IllegalArgumentException(
          name + " must be an uppercase code of at most " + max + " characters");
    }
  }

  static void requireCurrency(String value) {
    if (!normalize(value).matches("[A-Z]{3}")) {
      throw new IllegalArgumentException(
          "currencyCode must be a three-letter ISO code");
    }
  }

  static void requireText(String value, String name, int max) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > max) {
      throw new IllegalArgumentException(
          name + " is required and must be at most " + max + " characters");
    }
  }

  static void optionalText(String value, String name, int max) {
    if (value != null && (value.isBlank() || value.strip().length() > max)) {
      throw new IllegalArgumentException(
          name + " must be non-blank and at most " + max + " characters");
    }
  }

  static void requireRange(LocalDate from, LocalDate to) {
    if (from == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    if (to != null && !to.isAfter(from)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }
}
