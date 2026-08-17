package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SalaryStructureDesignImpactContractTest {
  @Test
  void comparisonIsReadOnlyAndUsesExistingSalaryStructureReadAuthority() {
    Map<String, String> permissions = Arrays.stream(
            SalaryStructureDesignImpactController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsOnlyKeys("compare")
        .containsEntry(
            "compare",
            "hasAuthority('compensation.structure.read')");
  }

  @Test
  void workbenchRemainsExplicitlyDesignTimeOnly() {
    assertThat(SalaryStructureDesignImpactControls.DISCLAIMER)
        .contains("DESIGN-TIME")
        .contains("NOT AN EMPLOYEE PAYROLL")
        .contains("STATUTORY RESULT");
  }
}
