package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
  private static final UUID BASIC =
      UUID.fromString("21100000-0000-0000-0000-000000000001");
  private static final UUID HRA =
      UUID.fromString("21100000-0000-0000-0000-000000000002");
  private static final UUID RESIDUAL =
      UUID.fromString("21100000-0000-0000-0000-000000000003");
  private static final UUID POLICY =
      UUID.fromString("23100000-0000-0000-0000-000000000001");

  @Test
  void controllerMethodsEnforceStructurePermissions() {
    Map<String, String> permissions = Arrays.stream(
            SalaryStructureController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry("create",
            "hasAuthority('compensation.structure.create')")
        .containsEntry("simulate",
            "hasAuthority('compensation.structure.simulate')")
        .containsEntry("validations",
            "hasAuthority('compensation.structure.read')")
        .containsEntry("bindValidation",
            "hasAuthority('compensation.structure.validation.bind')")
        .containsEntry("approve",
            "hasAuthority('compensation.structure.approve')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void schemaOneConfigurationRequiresControlledEnumsAndTargets() {
    SalaryStructureWriteRequest invalid = new SalaryStructureWriteRequest(
        "DEFAULT", "Default Structure", "USD", "UNKNOWN", "MONTHLY",
        "STANDARD", null, null, "ANNUAL_CTC", BigDecimal.ZERO,
        new BigDecimal("-1.0000"), RESIDUAL, LocalDate.of(2027, 1, 1),
        LocalDate.of(2029, 1, 1), standardLines());

    assertThatThrownBy(() -> invalid.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INR");

    SalaryStructureWriteRequest noPolicy = new SalaryStructureWriteRequest(
        "DEFAULT", "Default Structure", "INR", "STANDARD", "MONTHLY",
        "STANDARD", null, null, "ANNUAL_CTC",
        new BigDecimal("1000000.0000"), BigDecimal.ZERO, RESIDUAL,
        LocalDate.of(2027, 1, 1), LocalDate.of(2029, 1, 1),
        standardLines());
    assertThatThrownBy(() -> noPolicy.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ctcPolicyVersionId");
  }

  @Test
  void monthlyGrossTargetNormalizesToAnnualExecutionAmount() {
    SalaryStructureWriteRequest monthly =
        new SalaryStructureWriteRequest(
            "MONTHLY_GROSS_TEST",
            "Monthly Gross Structure",
            "INR",
            "STANDARD",
            "MONTHLY",
            "STANDARD",
            POLICY,
            null,
            "MONTHLY_GROSS",
            new BigDecimal("100000.0000"),
            BigDecimal.ZERO,
            RESIDUAL,
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 1, 1),
            standardLines());

    monthly.validate(true);

    assertThat(monthly.resolvedTargetAnnualAmount())
        .isEqualByComparingTo(new BigDecimal("1200000.0000"));
  }

  @Test
  void duplicateSequencesComponentsAndDisplayOrdersAreRejected() {
    List<SalaryStructureLineWriteRequest> duplicate = List.of(
        fixed(BASIC, 1, 1, 1),
        fixed(HRA, 1, 2, 2),
        residual(RESIDUAL, 3, 3, 3));
    assertThatThrownBy(() -> request(duplicate).validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequence");

    List<SalaryStructureLineWriteRequest> duplicateOrder = List.of(
        fixed(BASIC, 1, 1, 1),
        percentage(HRA, 2, 1, 2),
        residual(RESIDUAL, 3, 3, 3));
    assertThatThrownBy(() -> request(duplicateOrder).validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTC display");
  }

  @Test
  void lineTargetShapeMustMatchDeclaredLineType() {
    SalaryStructureLineWriteRequest invalid =
        new SalaryStructureLineWriteRequest(
            BASIC, 1, "FIXED", new BigDecimal("1000.0000"),
            new BigDecimal("10.000000"), null, null, null, true,
            "PROHIBITED", 1, 1);

    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metadata");
  }

  @Test
  void exactlyOneFinalResidualMustMatchPolicyResidualComponent() {
    List<SalaryStructureLineWriteRequest> noResidual = List.of(
        fixed(BASIC, 1, 1, 1),
        fixed(HRA, 2, 2, 2));
    assertThatThrownBy(() -> request(noResidual).validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly one RESIDUAL");

    List<SalaryStructureLineWriteRequest> wrongResidual = List.of(
        residual(RESIDUAL, 1, 1, 1),
        fixed(BASIC, 2, 2, 2));
    assertThatThrownBy(() -> request(wrongResidual).validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("final calculation sequence");
  }

  @Test
  void configuredBoundsMustBeNonNegativeAndOrdered() {
    SalaryStructureLineWriteRequest invalid =
        new SalaryStructureLineWriteRequest(
            BASIC, 1, "FIXED", new BigDecimal("1000.0000"), null,
            null, new BigDecimal("2000.0000"),
            new BigDecimal("1000.0000"), true, "CONTROLLED", 1, 1);

    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximumAmount");
  }

  @Test
  void simulationRequestRequiresDateAndAcceptsOnlySyntheticFacts() {
    SalaryStructureSimulationRequest missingDate =
        new SalaryStructureSimulationRequest(null, Map.of());
    assertThatThrownBy(missingDate::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effectiveDate");

    SalaryStructureSimulationRequest valid =
        new SalaryStructureSimulationRequest(
            LocalDate.of(2027, 7, 1),
            Map.of("COUNTRY_CODE", JsonNodeFactory.instance.textNode("IN")));
    valid.validate();
    assertThat(valid.eligibilityFacts()).containsKey("COUNTRY_CODE");
  }

  private SalaryStructureWriteRequest request(
      List<SalaryStructureLineWriteRequest> lines) {
    return new SalaryStructureWriteRequest(
        "DEFAULT", "Default Structure", "INR", "STANDARD", "MONTHLY",
        "STANDARD", POLICY, null, "ANNUAL_CTC",
        new BigDecimal("1000000.0000"), new BigDecimal("0.0100"),
        RESIDUAL, LocalDate.of(2027, 1, 1),
        LocalDate.of(2029, 1, 1), lines);
  }

  private List<SalaryStructureLineWriteRequest> standardLines() {
    return List.of(
        fixed(BASIC, 1, 1, 1),
        percentage(HRA, 2, 2, 2),
        residual(RESIDUAL, 3, 3, 3));
  }

  private SalaryStructureLineWriteRequest fixed(
      UUID component,
      int sequence,
      int ctcOrder,
      int payslipOrder) {
    return new SalaryStructureLineWriteRequest(
        component, sequence, "FIXED", new BigDecimal("600000.0000"),
        null, null, null, null, true, "PROHIBITED", ctcOrder,
        payslipOrder);
  }

  private SalaryStructureLineWriteRequest percentage(
      UUID component,
      int sequence,
      int ctcOrder,
      int payslipOrder) {
    return new SalaryStructureLineWriteRequest(
        component, sequence, "PERCENTAGE", null,
        new BigDecimal("40.000000"), "BASIC", null, null, true,
        "CONTROLLED", ctcOrder, payslipOrder);
  }

  private SalaryStructureLineWriteRequest residual(
      UUID component,
      int sequence,
      int ctcOrder,
      int payslipOrder) {
    return new SalaryStructureLineWriteRequest(
        component, sequence, "RESIDUAL", null, null, null,
        BigDecimal.ZERO, null, true, "PROHIBITED", ctcOrder,
        payslipOrder);
  }
}
