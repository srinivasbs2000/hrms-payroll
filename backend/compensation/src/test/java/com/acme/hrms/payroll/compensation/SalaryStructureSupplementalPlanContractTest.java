package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanBindingWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanLineWriteRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureSupplementalPlanControls.SupplementalPlanVersionWriteRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SalaryStructureSupplementalPlanContractTest {
  @Test
  void versionAcceptsDistinctComponentVersionReferences() {
    var request = new SupplementalPlanVersionWriteRequest(
        "Corporate Benefit Top-up",
        "BENEFIT",
        LocalDate.of(2027, 1, 1),
        null,
        List.of(
            fixedLine(UUID.randomUUID(), 1),
            fixedLine(UUID.randomUUID(), 2)));

    request.validate();

    assertThat(request.lines()).hasSize(2);
  }

  @Test
  void versionRejectsDuplicateComponentVersionsAndSequences() {
    UUID component = UUID.randomUUID();
    var duplicateComponent = new SupplementalPlanVersionWriteRequest(
        "Duplicate component",
        "ALLOWANCE",
        LocalDate.of(2027, 1, 1),
        null,
        List.of(
            fixedLine(component, 1),
            fixedLine(component, 2)));

    assertThatThrownBy(duplicateComponent::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be duplicated");

    var duplicateSequence = new SupplementalPlanVersionWriteRequest(
        "Duplicate sequence",
        "ALLOWANCE",
        LocalDate.of(2027, 1, 1),
        null,
        List.of(
            fixedLine(UUID.randomUUID(), 1),
            fixedLine(UUID.randomUUID(), 1)));

    assertThatThrownBy(duplicateSequence::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequences must be unique");
  }

  @Test
  void lineRequiresExactlyOneCalculationValue() {
    var empty = new SupplementalPlanLineWriteRequest(
        UUID.randomUUID(),
        1,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null);

    assertThatThrownBy(() -> empty.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly one");

    var ambiguous = new SupplementalPlanLineWriteRequest(
        UUID.randomUUID(),
        1,
        BigDecimal.TEN,
        BigDecimal.ONE,
        UUID.randomUUID(),
        null,
        null,
        true,
        null,
        null);

    assertThatThrownBy(() -> ambiguous.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly one");
  }

  @Test
  void percentageDefaultRequiresExplicitNonSelfBase() {
    UUID component = UUID.randomUUID();
    var missingBase = new SupplementalPlanLineWriteRequest(
        component,
        1,
        null,
        BigDecimal.TEN,
        null,
        null,
        null,
        false,
        null,
        null);

    assertThatThrownBy(() -> missingBase.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("percentageBaseComponentVersionId");

    var selfBase = new SupplementalPlanLineWriteRequest(
        component,
        1,
        null,
        BigDecimal.TEN,
        component,
        null,
        null,
        false,
        null,
        null);

    assertThatThrownBy(() -> selfBase.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot calculate from itself");

    var percentage = new SupplementalPlanLineWriteRequest(
        component,
        1,
        null,
        BigDecimal.TEN,
        UUID.randomUUID(),
        BigDecimal.ZERO,
        BigDecimal.valueOf(5000),
        false,
        null,
        null);
    percentage.validate(LocalDate.of(2027, 1, 1), null);
  }

  @Test
  void lineRejectsInvalidBounds() {
    var bounds = new SupplementalPlanLineWriteRequest(
        UUID.randomUUID(),
        1,
        BigDecimal.ONE,
        null,
        null,
        BigDecimal.TEN,
        BigDecimal.ONE,
        false,
        null,
        null);

    assertThatThrownBy(() -> bounds.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximumAmount");
  }

  @Test
  void bindingRequiresPositiveSequenceAndHalfOpenRange() {
    var request = new SupplementalPlanBindingWriteRequest(
        UUID.randomUUID(),
        1,
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 12, 31));
    request.validate();

    var invalid = new SupplementalPlanBindingWriteRequest(
        UUID.randomUUID(),
        0,
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 1, 1));
    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void controllersUseExistingSalaryStructureAuthorities()
      throws Exception {
    assertPermission(
        SalaryStructureSupplementalPlanController.class,
        "create",
        "compensation.structure.create",
        String.class,
        SalaryStructureSupplementalPlanControls
            .SupplementalPlanCreateRequest.class);
    assertPermission(
        SalaryStructureSupplementalPlanController.class,
        "addVersion",
        "compensation.structure.version.create",
        UUID.class,
        String.class,
        SupplementalPlanVersionWriteRequest.class);
    assertPermission(
        SalaryStructureSupplementalPlanController.class,
        "approve",
        "compensation.structure.approve",
        UUID.class,
        UUID.class,
        String.class);
    assertPermission(
        SalaryStructureSupplementalPlanController.class,
        "bind",
        "compensation.structure.version.create",
        UUID.class,
        UUID.class,
        String.class,
        SupplementalPlanBindingWriteRequest.class);
    assertPermission(
        SalaryStructureSupplementalPlanController.class,
        "audit",
        "audit.read",
        UUID.class);
    assertPermission(
        SalaryStructureCompositionController.class,
        "simulate",
        "compensation.structure.simulate",
        UUID.class,
        UUID.class,
        String.class,
        SalaryStructureSimulationRequest.class);
  }

  private SupplementalPlanLineWriteRequest fixedLine(
      UUID componentVersionId,
      int sequenceNo) {
    return new SupplementalPlanLineWriteRequest(
        componentVersionId,
        sequenceNo,
        BigDecimal.valueOf(1000),
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.valueOf(5000),
        true,
        null,
        null);
  }

  private void assertPermission(
      Class<?> controller,
      String methodName,
      String authority,
      Class<?>... parameterTypes)
      throws Exception {
    Method method = controller.getDeclaredMethod(
        methodName,
        parameterTypes);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains(authority);
  }
}
