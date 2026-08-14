package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PayrollCalendarContractTest {
  @Test
  void controllerMethodsEnforceCalendarPermissions() {
    Map<String, String> permissions = Arrays.stream(
            PayrollCalendarController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry("create", "hasAuthority('calendar.create')")
        .containsEntry("list", "hasAuthority('calendar.read')")
        .containsEntry("generate", "hasAuthority('calendar.period.generate')")
        .containsEntry("periods", "hasAuthority('calendar.read')")
        .containsEntry("milestoneRules", "hasAuthority('calendar.read')")
        .containsEntry("configureMilestoneRules", "hasAuthority('calendar.create')")
        .containsEntry("holidays", "hasAuthority('calendar.read')")
        .containsEntry("configureHoliday", "hasAuthority('calendar.create')")
        .containsEntry("readiness", "hasAuthority('calendar.read')")
        .containsEntry("publish", "hasAuthority('calendar.create')")
        .containsEntry("amend", "hasAuthority('calendar.create')")
        .containsEntry("retire", "hasAuthority('calendar.create')")
        .containsEntry("operations", "hasAuthority('calendar.read')")
        .containsEntry("periodOperations", "hasAuthority('calendar.read')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void standardAndAuthorisedCustomCalendarsValidate() {
    new PayrollCalendarWriteRequest(
        "WEEKLY_IN", "Weekly India", "WEEKLY", "Asia/Kolkata").validate();

    new PayrollCalendarWriteRequest(
        "CUSTOM_10", "Ten day", "CUSTOM", "Asia/Kolkata",
        10, true, java.util.List.of(6, 7)).validate();

    assertThatThrownBy(() -> new PayrollCalendarWriteRequest(
        "CUSTOM_BAD", "Bad custom", "CUSTOM", "Asia/Kolkata",
        10, false, java.util.List.of(6, 7)).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CUSTOM");
  }

  @Test
  void periodGenerationModesAreFailClosed() {
    new GeneratePeriodsRequest(2028, 31).validateFor("MONTHLY");
    new GeneratePeriodsRequest(null, null, LocalDate.of(2028, 1, 1), 52)
        .validateFor("WEEKLY");

    assertThatThrownBy(
            () -> new GeneratePeriodsRequest(2028, 31).validateFor("WEEKLY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MONTHLY");
  }

  @Test
  void invalidTimezoneAndRetirementReasonAreRejected() {
    assertThatThrownBy(() -> new PayrollCalendarWriteRequest(
        "MONTHLY_IN", "Monthly India", "MONTHLY", "Not/A_Timezone").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timezone");

    assertThatThrownBy(() -> new PayrollCalendarLifecycleRequest(" ").requireReason())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void completeMilestoneRuleSetIsRequired() {
    java.util.List<PayrollCalendarMilestoneRuleWriteRequest> complete = java.util.List.of(
        rule("INPUT_CUTOFF", -3),
        rule("CALCULATION", -2),
        rule("APPROVAL", -1),
        rule("RELEASE", 0),
        rule("PAYMENT", 0));
    new PayrollCalendarMilestoneRulesRequest(complete).validate();

    assertThatThrownBy(() -> new PayrollCalendarMilestoneRulesRequest(
        java.util.List.of(
            rule("INPUT_CUTOFF", -3),
            rule("CALCULATION", -2),
            rule("APPROVAL", -1),
            rule("RELEASE", 0),
            rule("RELEASE", 1))).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly once");
  }

  private PayrollCalendarMilestoneRuleWriteRequest rule(String type, int offset) {
    return new PayrollCalendarMilestoneRuleWriteRequest(
        type, "PERIOD_END", offset, "PREVIOUS_WORKING_DAY");
  }
}
