package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PayrollCalendarContractTest {
  @Test
  void controllerMethodsEnforceCalendarPermissions() {
    Map<String, String> permissions = Arrays.stream(
            PayrollCalendarController.class
                .getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(
            PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method
                .getAnnotation(PreAuthorize.class)
                .value()));

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('calendar.create')")
        .containsEntry(
            "list",
            "hasAuthority('calendar.read')")
        .containsEntry(
            "generate",
            "hasAuthority('calendar.period.generate')")
        .containsEntry(
            "periods",
            "hasAuthority('calendar.read')")
        .containsEntry(
            "audit",
            "hasAuthority('audit.read')");
  }

  @Test
  void unsupportedCalendarAndPeriodInputsAreRejected() {
    PayrollCalendarWriteRequest unsupportedFrequency =
        new PayrollCalendarWriteRequest(
            "MONTHLY_IN",
            "Monthly India",
            "WEEKLY",
            "Asia/Kolkata");

    assertThatThrownBy(unsupportedFrequency::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MONTHLY");

    PayrollCalendarWriteRequest invalidTimezone =
        new PayrollCalendarWriteRequest(
            "MONTHLY_IN",
            "Monthly India",
            "MONTHLY",
            "Not/A_Timezone");

    assertThatThrownBy(invalidTimezone::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timezone");

    assertThatThrownBy(
            () -> new GeneratePeriodsRequest(
                2019, 31).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("year");

    assertThatThrownBy(
            () -> new GeneratePeriodsRequest(
                2028, 32).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("paymentDay");
  }
}
