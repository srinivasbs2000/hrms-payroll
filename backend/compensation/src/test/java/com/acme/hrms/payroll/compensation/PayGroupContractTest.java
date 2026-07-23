package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PayGroupContractTest {
  private static final UUID PSU_VERSION =
      UUID.fromString(
          "10000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR =
      UUID.fromString(
          "20000000-0000-0000-0000-000000000001");

  @Test
  void controllerMethodsEnforcePayGroupPermissions() {
    Map<String, String> permissions =
        Arrays.stream(
                PayGroupController.class.getDeclaredMethods())
            .filter(method ->
                method.isAnnotationPresent(PreAuthorize.class))
            .collect(Collectors.toMap(
                Method::getName,
                method -> method
                    .getAnnotation(PreAuthorize.class)
                    .value()));

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('pay-group.create')")
        .containsEntry(
            "list",
            "hasAuthority('pay-group.read')")
        .containsEntry(
            "current",
            "hasAuthority('pay-group.read')")
        .containsEntry(
            "history",
            "hasAuthority('pay-group.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority('pay-group.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority('pay-group.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority('pay-group.version.end-date')")
        .containsEntry(
            "approve",
            "hasAuthority('pay-group.approve')")
        .containsEntry(
            "audit",
            "hasAuthority('audit.read')");
  }

  @Test
  void approvedSliceConstraintsAreValidatedBeforePersistence() {
    PayGroupWriteRequest invalidRange =
        new PayGroupWriteRequest(
            "PG",
            "Monthly India",
            PSU_VERSION,
            CALENDAR,
            "INR",
            "CALENDAR_DAYS",
            LocalDate.of(2027, 1, 2),
            LocalDate.of(2027, 1, 1));

    assertThatThrownBy(() -> invalidRange.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");

    PayGroupWriteRequest unsupportedCurrency =
        new PayGroupWriteRequest(
            "PG",
            "Monthly India",
            PSU_VERSION,
            CALENDAR,
            "USD",
            "CALENDAR_DAYS",
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(
            () -> unsupportedCurrency.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INR");

    PayGroupWriteRequest unsupportedProration =
        new PayGroupWriteRequest(
            "PG",
            "Monthly India",
            PSU_VERSION,
            CALENDAR,
            "INR",
            "WORKING_DAYS",
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(
            () -> unsupportedProration.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CALENDAR_DAYS");
  }
}
