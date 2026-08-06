package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EligibilityRuleContractTest {

  @Test
  void controllerMethodsEnforceEligibilityPermissions() {
    Map<String, String> permissions = Arrays.stream(
            EligibilityRuleController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('compensation.eligibility-rule.create')")
        .containsEntry(
            "list",
            "hasAuthority('compensation.eligibility-rule.read')")
        .containsEntry(
            "current",
            "hasAuthority('compensation.eligibility-rule.read')")
        .containsEntry(
            "history",
            "hasAuthority('compensation.eligibility-rule.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority('compensation.eligibility-rule.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority('compensation.eligibility-rule.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority('compensation.eligibility-rule.version.end-date')")
        .containsEntry(
            "approve",
            "hasAuthority('compensation.eligibility-rule.approve')")
        .containsEntry(
            "evaluate",
            "hasAuthority('compensation.eligibility-rule.evaluate')")
        .containsEntry(
            "retire",
            "hasAuthority('compensation.eligibility-rule.retire')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void createVersionAndTransientEvaluationContractsAreSeparated() {
    assertThat(Arrays.stream(
            EligibilityRuleVersionWriteRequest.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain(
            "code",
            "lifecycleStatus",
            "facts",
            "evaluationResult");

    assertThat(Arrays.stream(
            EligibilityRuleView.EvaluationView.class
                .getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain(
            "employeeId",
            "assignmentId",
            "evaluatedAt",
            "readinessStatus");
  }

  @Test
  void factKeyTypeOperatorAndTypedValueAreValidated() {
    EligibilityCriterionWriteRequest typeMismatch =
        criterion(
            1,
            "SERVICE_MONTHS",
            "TEXT",
            "EQ",
            JsonNodeFactory.instance.textNode("12"));
    assertThatThrownBy(typeMismatch::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be NUMBER");

    EligibilityCriterionWriteRequest unknownFact =
        criterion(
            1,
            "UNRESTRICTED_EXPRESSION",
            "TEXT",
            "EQ",
            JsonNodeFactory.instance.textNode("true"));
    assertThatThrownBy(unknownFact::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");

    EligibilityCriterionWriteRequest orderedText =
        criterion(
            1,
            "COUNTRY_CODE",
            "TEXT",
            "GTE",
            JsonNodeFactory.instance.textNode("IN"));
    assertThatThrownBy(orderedText::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NUMBER and DATE");

    EligibilityCriterionWriteRequest scalarIn =
        criterion(
            1,
            "COUNTRY_CODE",
            "TEXT",
            "IN",
            JsonNodeFactory.instance.textNode("IN"));
    assertThatThrownBy(scalarIn::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-empty array");

    EligibilityCriterionWriteRequest validDate =
        criterion(
            1,
            "EFFECTIVE_DATE",
            "DATE",
            "LTE",
            JsonNodeFactory.instance.textNode("2029-12-31"));
    validDate.validate();
  }

  @Test
  void versionResultsDateRangeAndSequenceUniquenessAreValidated() {
    EligibilityRuleVersionWriteRequest valid =
        version(List.of(
            criterion(
                1,
                "COUNTRY_CODE",
                "TEXT",
                "EQ",
                JsonNodeFactory.instance.textNode("IN")),
            criterion(
                2,
                "SERVICE_MONTHS",
                "NUMBER",
                "GTE",
                JsonNodeFactory.instance.numberNode(12))));
    valid.validate();

    EligibilityRuleVersionWriteRequest invalidResult =
        new EligibilityRuleVersionWriteRequest(
            "Invalid result",
            "ALLOW",
            "NOT_ELIGIBLE",
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 1, 1),
            valid.criteria());
    assertThatThrownBy(invalidResult::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resultWhenMatched");

    EligibilityRuleVersionWriteRequest invalidRange =
        new EligibilityRuleVersionWriteRequest(
            "Invalid range",
            "ELIGIBLE",
            "NOT_ELIGIBLE",
            LocalDate.of(2029, 1, 1),
            LocalDate.of(2028, 1, 1),
            valid.criteria());
    assertThatThrownBy(invalidRange::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");

    EligibilityRuleVersionWriteRequest duplicateSequence =
        version(List.of(
            criterion(
                1,
                "COUNTRY_CODE",
                "TEXT",
                "EQ",
                JsonNodeFactory.instance.textNode("IN")),
            criterion(
                1,
                "SERVICE_MONTHS",
                "NUMBER",
                "GTE",
                JsonNodeFactory.instance.numberNode(12))));
    assertThatThrownBy(duplicateSequence::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence numbers");
  }

  private EligibilityRuleVersionWriteRequest version(
      List<EligibilityCriterionWriteRequest> criteria) {
    return new EligibilityRuleVersionWriteRequest(
        "Synthetic India eligibility",
        "ELIGIBLE",
        "REQUIRES_APPROVAL",
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2029, 1, 1),
        criteria);
  }

  private EligibilityCriterionWriteRequest criterion(
      int sequence,
      String key,
      String type,
      String operator,
      JsonNode value) {
    return new EligibilityCriterionWriteRequest(
        sequence,
        key,
        type,
        operator,
        value);
  }
}
