package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CtcPolicyContractTest {
  private static final UUID COMPONENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000002");

  @Test
  void controllerMethodsEnforceCtcPolicyPermissions() {
    Map<String, String> permissions = Arrays.stream(
            CtcPolicyController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('compensation.ctc-policy.create')")
        .containsEntry(
            "list",
            "hasAuthority('compensation.ctc-policy.read')")
        .containsEntry(
            "current",
            "hasAuthority('compensation.ctc-policy.read')")
        .containsEntry(
            "history",
            "hasAuthority('compensation.ctc-policy.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority('compensation.ctc-policy.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority('compensation.ctc-policy.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority('compensation.ctc-policy.version.end-date')")
        .containsEntry(
            "approve",
            "hasAuthority('compensation.ctc-policy.approve')")
        .containsEntry(
            "retire",
            "hasAuthority('compensation.ctc-policy.retire')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void createAndVersionRequestsAreSeparated() {
    assertThat(Arrays.stream(
            CtcPolicyVersionWriteRequest.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("code", "lifecycleStatus");
  }

  @Test
  void versionRequiresAllCostViewsAndResidualMembership() {
    CtcPolicyVersionWriteRequest complete =
        version(treatments("ACTUAL_VALUE"));
    complete.validate();

    CtcPolicyVersionWriteRequest incomplete = version(List.of(
        treatment(1, "OFFERED", "ACTUAL_VALUE")));
    assertThatThrownBy(incomplete::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("all four cost views");

    CtcPolicyVersionWriteRequest excludedResidual =
        version(treatments("EXCLUDE"));
    assertThatThrownBy(excludedResidual::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("residual component");
  }

  @Test
  void treatmentShapesBasePairsAndDateContainmentAreValidated() {
    CtcPolicyTreatmentWriteRequest invalidFixed =
        new CtcPolicyTreatmentWriteRequest(
            COMPONENT_ID,
            COMPONENT_VERSION_ID,
            1,
            "OFFERED",
            "FIXED_VALUE",
            null,
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> invalidFixed.validate(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixedValue");

    CtcPolicyTreatmentWriteRequest partialBase =
        new CtcPolicyTreatmentWriteRequest(
            COMPONENT_ID,
            COMPONENT_VERSION_ID,
            1,
            "OFFERED",
            "ACTUAL_VALUE",
            null,
            null,
            UUID.randomUUID(),
            null,
            null,
            null);
    assertThatThrownBy(() -> partialBase.validate(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supplied together");

    CtcPolicyTreatmentWriteRequest outsideRange =
        new CtcPolicyTreatmentWriteRequest(
            COMPONENT_ID,
            COMPONENT_VERSION_ID,
            1,
            "OFFERED",
            "ACTUAL_VALUE",
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2028, 1, 1));
    assertThatThrownBy(() -> outsideRange.validate(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contained");
  }

  private CtcPolicyVersionWriteRequest version(
      List<CtcPolicyTreatmentWriteRequest> values) {
    return new CtcPolicyVersionWriteRequest(
        "Synthetic India CTC Policy",
        "INR",
        "EXACT_ANNUAL",
        new BigDecimal("0.0100"),
        COMPONENT_ID,
        COMPONENT_VERSION_ID,
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2029, 1, 1),
        values);
  }

  private List<CtcPolicyTreatmentWriteRequest> treatments(
      String treatmentType) {
    return List.of(
        treatment(1, "OFFERED", treatmentType),
        treatment(2, "TARGET", treatmentType),
        treatment(3, "ACCRUED", treatmentType),
        treatment(4, "ACTUAL_EMPLOYER_COST", treatmentType));
  }

  private CtcPolicyTreatmentWriteRequest treatment(
      int sequence,
      String costView,
      String treatmentType) {
    return new CtcPolicyTreatmentWriteRequest(
        COMPONENT_ID,
        COMPONENT_VERSION_ID,
        sequence,
        costView,
        treatmentType,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
