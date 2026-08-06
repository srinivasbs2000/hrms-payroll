package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EligibilityRuleVersionWriteRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank
        @Pattern(regexp = "^(ELIGIBLE|NOT_ELIGIBLE|REQUIRES_APPROVAL)$")
        String resultWhenMatched,
    @NotBlank
        @Pattern(regexp = "^(ELIGIBLE|NOT_ELIGIBLE|REQUIRES_APPROVAL)$")
        String resultWhenNotMatched,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotEmpty List<@Valid EligibilityCriterionWriteRequest> criteria) {

  private static final Set<String> RESULTS =
      Set.of("ELIGIBLE", "NOT_ELIGIBLE", "REQUIRES_APPROVAL");

  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (!RESULTS.contains(resultWhenMatched)) {
      throw new IllegalArgumentException(
          "resultWhenMatched contains an unsupported value");
    }
    if (!RESULTS.contains(resultWhenNotMatched)) {
      throw new IllegalArgumentException(
          "resultWhenNotMatched contains an unsupported value");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException(
          "effectiveFrom is required");
    }
    if (effectiveTo != null
        && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
    if (criteria == null || criteria.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one eligibility criterion is required");
    }

    Set<Integer> sequences = new HashSet<>();
    for (EligibilityCriterionWriteRequest criterion : criteria) {
      if (criterion == null) {
        throw new IllegalArgumentException(
            "Eligibility criteria must not contain null entries");
      }
      criterion.validate();
      if (!sequences.add(criterion.criterionSequence())) {
        throw new IllegalArgumentException(
            "Eligibility criterion sequence numbers must be unique");
      }
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown request field: " + property);
  }
}
