package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EligibilityRuleView(
    UUID identityId,
    String code,
    String lifecycleStatus,
    long identityVersionNo,
    LocalDate retirementEffectiveDate,
    String retirementReason,
    Instant retiredAt,
    String retiredBy,
    UUID versionId,
    int versionSequence,
    long versionNo,
    String name,
    String resultWhenMatched,
    String resultWhenNotMatched,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesVersionId,
    boolean superseded,
    List<EligibilityCriterionView> criteria) {

  @JsonIgnoreProperties(ignoreUnknown = false)
  public record EvaluationRequest(
      @NotEmpty Map<@NotBlank String, @NotNull JsonNode> facts) {

    public void validate() {
      if (facts == null || facts.isEmpty()) {
        throw new IllegalArgumentException(
            "At least one synthetic eligibility fact is required");
      }
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String property, Object value) {
      throw new IllegalArgumentException(
          "Unknown request field: " + property);
    }
  }

  public record CriterionEvaluationView(
      int criterionSequence,
      String factKey,
      String factType,
      String comparisonOperator,
      JsonNode expectedValue,
      JsonNode actualValue,
      boolean matched) {}

  public record EvaluationView(
      UUID identityId,
      UUID versionId,
      String result,
      boolean matched,
      String configurationHash,
      String factsHash,
      String evaluationHash,
      String disclaimer,
      List<CriterionEvaluationView> criteria) {}
}
