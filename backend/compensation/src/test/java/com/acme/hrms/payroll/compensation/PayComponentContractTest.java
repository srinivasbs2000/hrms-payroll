package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PayComponentContractTest {

  @Test
  void controllerMethodsEnforceCataloguePermissions() {
    Map<String, String> permissions = Arrays.stream(
            PayComponentController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry("create", "hasAuthority('compensation.component.create')")
        .containsEntry("list", "hasAuthority('compensation.component.read')")
        .containsEntry("current", "hasAuthority('compensation.component.read')")
        .containsEntry("history", "hasAuthority('compensation.component.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority('compensation.component.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority('compensation.component.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority('compensation.component.version.end-date')")
        .containsEntry("approve", "hasAuthority('compensation.component.approve')")
        .containsEntry("retire", "hasAuthority('compensation.component.retire')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void createAndVersionRequestsAreSeparated() {
    assertThat(Arrays.stream(PayComponentVersionWriteRequest.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("code", "name", "componentType", "ownershipScope");
  }

  @Test
  void formulaClassificationAndEffectiveDateRulesAreValidated() {
    PayComponentVersionWriteRequest missingFixedAmount = version(
        "FIXED", null, null, LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(missingFixedAmount::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixedAmount");

    PayComponentVersionWriteRequest invalidRange = version(
        "FIXED",
        null,
        new BigDecimal("1000.0000"),
        LocalDate.of(2027, 1, 2),
        LocalDate.of(2027, 1, 1));
    assertThatThrownBy(invalidRange::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");

    PayComponentVersionWriteRequest invalidCategory =
        new PayComponentVersionWriteRequest(
            "FIXED", null, new BigDecimal("1000"), 2,
            "UNKNOWN", "BASIC_PAY", "INCREASE", "EMPLOYEE",
            "PAYROLL_BANK", "CURRENT_PERIOD", "SHOW", "SUPPRESS",
            "PROHIBIT", "MONTHLY", "FIXED", "MONTHLY_AMOUNT",
            "DELEGATED", "REGULAR", LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(invalidCategory::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("componentCategory");
  }

  private PayComponentVersionWriteRequest version(
      String formulaType,
      String expression,
      BigDecimal fixedAmount,
      LocalDate from,
      LocalDate to) {
    return new PayComponentVersionWriteRequest(
        formulaType, expression, fixedAmount, 2,
        "CASH_EARNING", "BASIC_PAY", "INCREASE", "EMPLOYEE",
        "PAYROLL_BANK", "CURRENT_PERIOD", "SHOW", "SUPPRESS",
        "PROHIBIT", "MONTHLY", "FIXED", "MONTHLY_AMOUNT",
        "DELEGATED", "REGULAR", from, to);
  }
}
