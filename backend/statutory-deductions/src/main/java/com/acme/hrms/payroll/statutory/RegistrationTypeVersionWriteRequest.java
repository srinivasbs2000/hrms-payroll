package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

public record RegistrationTypeVersionWriteRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String obligationCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String authorityCode,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$") String jurisdictionLevelCode,
    @Size(max = 240) String identifierPattern,
    @NotBlank String identifierCasePolicy,
    boolean parentRequired,
    UUID parentRegistrationTypeId,
    @NotEmpty List<@NotNull RegistrationOwnerKind> ownerKinds,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public static final String IDENTIFIER_PATTERN_DIALECT = "JAVA_REGEX_V1";

  public void validate() {
    validateIdentifierPattern(identifierPattern);
    if (!"UPPER".equals(identifierCasePolicy)
        && !"PRESERVE".equals(identifierCasePolicy)) {
      throw new IllegalArgumentException(
          "identifierCasePolicy must be UPPER or PRESERVE");
    }
    if (parentRequired && parentRegistrationTypeId == null) {
      throw new IllegalArgumentException(
          "A required parent registration type must be supplied");
    }
    if (parentRegistrationTypeId != null && !parentRequired) {
      throw new IllegalArgumentException(
          "parentRegistrationTypeId requires parentRequired=true");
    }
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
    if (ownerKinds == null || ownerKinds.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one registration owner kind is required");
    }
    if (ownerKinds.stream().distinct().count() != ownerKinds.size()) {
      throw new IllegalArgumentException(
          "Registration owner kinds must be unique");
    }
  }

  public static void validateIdentifierPattern(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      return;
    }
    try {
      java.util.regex.Pattern.compile(pattern);
    } catch (PatternSyntaxException exception) {
      throw new IllegalArgumentException(
          "identifierPattern must be valid JAVA_REGEX_V1",
          exception);
    }
  }

  public static boolean matchesIdentifierPattern(
      String pattern,
      String value) {
    if (pattern == null || pattern.isBlank()) {
      return true;
    }
    validateIdentifierPattern(pattern);
    return java.util.regex.Pattern.compile(pattern)
        .matcher(value)
        .matches();
  }
}
