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
  void controllerMethodsEnforcePayComponentPermissions() {
    Map<String, String> permissions =
        Arrays.stream(
                PayComponentController.class.getDeclaredMethods())
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
            "hasAuthority('compensation.component.create')")
        .containsEntry(
            "list",
            "hasAuthority('compensation.component.read')")
        .containsEntry(
            "current",
            "hasAuthority('compensation.component.read')")
        .containsEntry(
            "history",
            "hasAuthority('compensation.component.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority("
                + "'compensation.component.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority("
                + "'compensation.component.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority("
                + "'compensation.component.version.end-date')")
        .containsEntry(
            "approve",
            "hasAuthority('compensation.component.approve')")
        .containsEntry(
            "audit",
            "hasAuthority('audit.read')");
  }

  @Test
  void formulaAndEffectiveDateRulesAreValidated() {
    PayComponentWriteRequest missingFixedAmount =
        new PayComponentWriteRequest(
            "BASIC",
            "Basic Pay",
            "EARNING",
            "FIXED",
            null,
            null,
            2,
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(
            () -> missingFixedAmount.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixedAmount");

    PayComponentWriteRequest invalidExpression =
        new PayComponentWriteRequest(
            "HRA",
            "House Rent Allowance",
            "EARNING",
            "PERCENTAGE_OF_COMPONENT",
            null,
            null,
            2,
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(
            () -> invalidExpression.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("formulaExpression");

    PayComponentWriteRequest invalidRange =
        new PayComponentWriteRequest(
            "BASIC",
            "Basic Pay",
            "EARNING",
            "FIXED",
            null,
            new BigDecimal("1000.0000"),
            2,
            LocalDate.of(2027, 1, 2),
            LocalDate.of(2027, 1, 1));

    assertThatThrownBy(() -> invalidRange.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");
  }
}