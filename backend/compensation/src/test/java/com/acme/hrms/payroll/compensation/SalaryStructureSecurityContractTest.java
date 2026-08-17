package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SalaryStructureSecurityContractTest {
  @Test
  void lifecycleUsesSeparateMakerCheckerPublisherAuthorities() {
    Map<String, String> permissions =
        permissions(SalaryStructureLifecycleController.class);

    assertThat(permissions)
        .containsEntry(
            "lifecycle",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "submit",
            "hasAuthority('compensation.structure.submit')")
        .containsEntry(
            "reject",
            "hasAuthority('compensation.structure.approve')")
        .containsEntry(
            "publish",
            "hasAuthority('compensation.structure.publish')");

    assertThat(Set.of(
        permissions.get("submit"),
        permissions.get("reject"),
        permissions.get("publish")))
        .hasSize(3);
  }

  @Test
  void coreMutationAuthoritiesRemainLeastPrivilegeByOperation() {
    Map<String, String> permissions =
        permissions(SalaryStructureController.class);

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('compensation.structure.create')")
        .containsEntry(
            "addVersion",
            "hasAuthority('compensation.structure.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority('compensation.structure.version.correct')")
        .containsEntry(
            "simulate",
            "hasAuthority('compensation.structure.simulate')")
        .containsEntry(
            "bindValidation",
            "hasAuthority('compensation.structure.validation.bind')")
        .containsEntry(
            "approve",
            "hasAuthority('compensation.structure.approve')")
        .containsEntry(
            "endDate",
            "hasAuthority('compensation.structure.version.end-date')")
        .containsEntry(
            "audit",
            "hasAuthority('audit.read')");
  }

  private Map<String, String> permissions(Class<?> controller) {
    return Arrays.stream(controller.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));
  }
}
