package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.compensation.SalaryStructureStatutoryCompatibilityControls.BindingRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SalaryStructureStatutoryCompatibilityContractTest {
  private static final UUID RULE_VERSION =
      UUID.fromString("51000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION =
      UUID.fromString("51100000-0000-0000-0000-000000000001");

  @Test
  void controllerSeparatesReadConfigurationAndSimulationPermissions() {
    Map<String, String> permissions = Arrays.stream(
            SalaryStructureStatutoryCompatibilityController.class
                .getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry(
            "ruleVersions",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "bindings",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "bind",
            "hasAuthority('compensation.structure.version.create')")
        .containsEntry(
            "retire",
            "hasAuthority('compensation.structure.version.create')")
        .containsEntry(
            "evaluate",
            "hasAuthority('compensation.structure.simulate')")
        .containsEntry(
            "evaluations",
            "hasAuthority('compensation.structure.read')");
  }

  @Test
  void minimumWageBindingRequiresAComponentAndControlledSeverity() {
    new BindingRequest(
        RULE_VERSION,
        "MINIMUM_WAGE",
        "BLOCKING",
        COMPONENT_VERSION).validate();

    assertThatThrownBy(() -> new BindingRequest(
            RULE_VERSION,
            "MINIMUM_WAGE",
            "BLOCKING",
            null).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("componentVersionId");

    assertThatThrownBy(() -> new BindingRequest(
            RULE_VERSION,
            "MINIMUM_WAGE",
            "IGNORE",
            COMPONENT_VERSION).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("enforcementLevel");
  }

  @Test
  void generalStatutoryBindingDoesNotSmuggleAComponentComparison() {
    new BindingRequest(
        RULE_VERSION,
        "STATUTORY_RULE",
        "ADVISORY",
        null).validate();

    assertThatThrownBy(() -> new BindingRequest(
            RULE_VERSION,
            "STATUTORY_RULE",
            "BLOCKING",
            COMPONENT_VERSION).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not carry componentVersionId");
  }
}
