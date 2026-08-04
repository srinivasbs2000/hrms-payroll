package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PayrollBaseContractTest {

  @Test
  void controllerMethodsEnforceBaseAndMembershipPermissions() {
    Map<String, String> permissions = Arrays.stream(
            PayrollBaseController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry("create", "hasAuthority('compensation.base.create')")
        .containsEntry("list", "hasAuthority('compensation.base.read')")
        .containsEntry("current", "hasAuthority('compensation.base.read')")
        .containsEntry("history", "hasAuthority('compensation.base.read')")
        .containsEntry(
            "addVersion", "hasAuthority('compensation.base.version.create')")
        .containsEntry(
            "correctVersion", "hasAuthority('compensation.base.version.correct')")
        .containsEntry(
            "approveVersion", "hasAuthority('compensation.base.approve')")
        .containsEntry(
            "createMembership",
            "hasAuthority('compensation.base.membership.create')")
        .containsEntry(
            "approveMembership",
            "hasAuthority('compensation.base.membership.approve')")
        .containsEntry("retire", "hasAuthority('compensation.base.retire')");
  }

  @Test
  void membershipPercentageUsesEightDecimalPlacesAndClosedRange() {
    ComponentBaseMembershipWriteRequest valid =
        new ComponentBaseMembershipWriteRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "INCLUDE", new BigDecimal("33.33333333"),
            LocalDate.of(2027, 1, 1), null);
    valid.validate();

    ComponentBaseMembershipWriteRequest invalid =
        new ComponentBaseMembershipWriteRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "INCLUDE", new BigDecimal("100.00000001"),
            LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inclusionPercent");
  }

  @Test
  void baseVersionRejectsUnsupportedAggregationMethod() {
    PayrollBaseVersionWriteRequest request =
        new PayrollBaseVersionWriteRequest(
            "CALCULATION", "MEDIAN", null,
            LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("aggregationMethod");
  }
}
