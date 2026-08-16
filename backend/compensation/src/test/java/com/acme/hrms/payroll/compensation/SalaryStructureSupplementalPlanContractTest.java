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
            line(UUID.randomUUID(), 1),
            line(UUID.randomUUID(), 2)));

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
            line(component, 1),
            line(component, 2)));

    assertThatThrownBy(duplicateComponent::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be duplicated");

    var duplicateSequence = new SupplementalPlanVersionWriteRequest(
        "Duplicate sequence",
        "ALLOWANCE",
        LocalDate.of(2027, 1, 1),
        null,
        List.of(
            line(UUID.randomUUID(), 1),
            line(UUID.randomUUID(), 1)));

    assertThatThrownBy(duplicateSequence::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequences must be unique");
  }

  @Test
  void lineRejectsAmbiguousDefaultsAndInvalidBounds() {
    var ambiguous = new SupplementalPlanLineWriteRequest(
        UUID.randomUUID(),
        1,
        BigDecimal.TEN,
        BigDecimal.ONE,
        null,
        null,
        true,
        null,
        null);

    assertThatThrownBy(() -> ambiguous.validate(
        LocalDate.of(2027, 1, 1),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutually exclusive");

    var bounds = new SupplementalPlanLineWriteRequest(
        UUID.randomUUID(),
        1,
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
  void controllerReusesSalaryStructureLeastPrivilegeAuthorities()
      throws Exception {
    assertPermission(
        "create",
        "compensation.structure.create",
        String.class,
        SalaryStructureSupplementalPlanControls
            .SupplementalPlanCreateRequest.class);
    assertPermission(
        "addVersion",
        "compensation.structure.version.create",
        UUID.class,
        String.class,
        SupplementalPlanVersionWriteRequest.class);
    assertPermission(
        "approve",
        "compensation.structure.approve",
        UUID.class,
        UUID.class,
        String.class);
    assertPermission(
        "bind",
        "compensation.structure.version.create",
        UUID.class,
        UUID.class,
        String.class,
        SupplementalPlanBindingWriteRequest.class);
    assertPermission("audit", "audit.read", UUID.class);
  }

  private SupplementalPlanLineWriteRequest line(
      UUID componentVersionId,
      int sequenceNo) {
    return new SupplementalPlanLineWriteRequest(
        componentVersionId,
        sequenceNo,
        BigDecimal.valueOf(1000),
        null,
        BigDecimal.ZERO,
        BigDecimal.valueOf(5000),
        true,
        null,
        null);
  }

  private void assertPermission(
      String methodName,
      String authority,
      Class<?>... parameterTypes)
      throws Exception {
    Method method = SalaryStructureSupplementalPlanController.class
        .getDeclaredMethod(methodName, parameterTypes);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains(authority);
  }
}
