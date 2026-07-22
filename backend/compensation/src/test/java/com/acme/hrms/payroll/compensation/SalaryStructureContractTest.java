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

class SalaryStructureContractTest {
  private static final UUID COMPONENT_VERSION =
      UUID.fromString(
          "21100000-0000-0000-0000-000000000001");

  @Test
  void controllerMethodsEnforceStructurePermissions() {
    Map<String, String> permissions =
        Arrays.stream(
                SalaryStructureController.class
                    .getDeclaredMethods())
            .filter(method ->
                method.isAnnotationPresent(
                    PreAuthorize.class))
            .collect(Collectors.toMap(
                Method::getName,
                method -> method
                    .getAnnotation(PreAuthorize.class)
                    .value()));

    assertThat(permissions)
        .containsEntry(
            "create",
            "hasAuthority('compensation.structure.create')")
        .containsEntry(
            "list",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "current",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "history",
            "hasAuthority('compensation.structure.read')")
        .containsEntry(
            "addVersion",
            "hasAuthority("
                + "'compensation.structure.version.create')")
        .containsEntry(
            "correct",
            "hasAuthority("
                + "'compensation.structure.version.correct')")
        .containsEntry(
            "endDate",
            "hasAuthority("
                + "'compensation.structure.version.end-date')")
        .containsEntry(
            "approve",
            "hasAuthority('compensation.structure.approve')")
        .containsEntry(
            "audit",
            "hasAuthority('audit.read')");
  }

  @Test
  void duplicateSequencesAndComponentsAreRejected() {
    SalaryStructureLineWriteRequest first =
        fixedLine(1, COMPONENT_VERSION);

    SalaryStructureLineWriteRequest duplicateSequence =
        fixedLine(
            1,
            UUID.fromString(
                "21100000-0000-0000-0000-000000000002"));

    SalaryStructureWriteRequest request =
        request(List.of(first, duplicateSequence));

    assertThatThrownBy(() -> request.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence");
  }

  @Test
  void invalidLineTargetShapeIsRejected() {
    SalaryStructureLineWriteRequest invalid =
        new SalaryStructureLineWriteRequest(
            COMPONENT_VERSION,
            1,
            new BigDecimal("1000.0000"),
            new BigDecimal("10.000000"),
            null);

    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixed");
  }

  @Test
  void onlyInrAndValidEffectiveRangesAreAccepted() {
    SalaryStructureWriteRequest unsupportedCurrency =
        new SalaryStructureWriteRequest(
            "DEFAULT",
            "Default Structure",
            "USD",
            LocalDate.of(2027, 1, 1),
            null,
            List.of(fixedLine(1, COMPONENT_VERSION)));

    assertThatThrownBy(
            () -> unsupportedCurrency.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INR");

    SalaryStructureWriteRequest invalidRange =
        new SalaryStructureWriteRequest(
            "DEFAULT",
            "Default Structure",
            "INR",
            LocalDate.of(2027, 1, 2),
            LocalDate.of(2027, 1, 1),
            List.of(fixedLine(1, COMPONENT_VERSION)));

    assertThatThrownBy(() -> invalidRange.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveTo");
  }

  private SalaryStructureWriteRequest request(
      List<SalaryStructureLineWriteRequest> lines) {
    return new SalaryStructureWriteRequest(
        "DEFAULT",
        "Default Structure",
        "INR",
        LocalDate.of(2027, 1, 1),
        null,
        lines);
  }

  private SalaryStructureLineWriteRequest fixedLine(
      int sequence,
      UUID componentVersionId) {
    return new SalaryStructureLineWriteRequest(
        componentVersionId,
        sequence,
        new BigDecimal("1000.0000"),
        null,
        null);
  }
}