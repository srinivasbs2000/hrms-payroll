package com.acme.hrms.payroll.compensation.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DependencyView;
import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureDesignImpactRepository.Evidence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalaryStructureDesignImpactAssemblerTest {
  private static final UUID IDENTITY =
      UUID.fromString("70000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT =
      UUID.fromString("70000000-0000-0000-0000-000000000002");
  private static final UUID COMPONENT_V1 =
      UUID.fromString("70000000-0000-0000-0000-000000000003");
  private static final UUID COMPONENT_V2 =
      UUID.fromString("70000000-0000-0000-0000-000000000004");
  private static final UUID CTC =
      UUID.fromString("70000000-0000-0000-0000-000000000005");

  @Test
  void comparisonSurfacesTargetComponentStatutoryAndPublicationImpact() {
    SalaryStructureView baseline = structure(
        UUID.fromString("70000000-0000-0000-0000-000000000010"),
        1,
        new BigDecimal("1200000"),
        COMPONENT_V1,
        "a".repeat(64),
        "b".repeat(64),
        "APPROVED");
    SalaryStructureView proposed = structure(
        UUID.fromString("70000000-0000-0000-0000-000000000011"),
        2,
        new BigDecimal("1320000"),
        COMPONENT_V2,
        "c".repeat(64),
        "d".repeat(64),
        "DRAFT");

    var assembler = new SalaryStructureDesignImpactAssembler();
    var result = assembler.assemble(
        baseline,
        new Evidence("PUBLISHED", 1, "e".repeat(64)),
        List.of(new DependencyView(
            "STATUTORY_RULE",
            UUID.fromString("70000000-0000-0000-0000-000000000020"),
            UUID.fromString("70000000-0000-0000-0000-000000000021"),
            "MINIMUM_WAGE",
            "MINIMUM_WAGE",
            "BLOCKING:ACTIVE")),
        proposed,
        new Evidence("DRAFT", 2, null),
        List.of(new DependencyView(
            "STATUTORY_RULE",
            UUID.fromString("70000000-0000-0000-0000-000000000020"),
            UUID.fromString("70000000-0000-0000-0000-000000000022"),
            "MINIMUM_WAGE",
            "MINIMUM_WAGE",
            "BLOCKING:ACTIVE")));

    assertThat(result.changes())
        .anySatisfy(change -> {
          assertThat(change.area()).isEqualTo("TARGET");
          assertThat(change.key()).isEqualTo("TARGET_ANNUAL_AMOUNT");
        })
        .anySatisfy(change -> assertThat(change.area())
            .isEqualTo("COMPONENT"))
        .anySatisfy(change -> assertThat(change.area())
            .isEqualTo("STATUTORY"));

    assertThat(result.downstreamImpacts())
        .extracting(item -> item.impactCode())
        .contains(
            "CONFIGURATION_VALIDATION_REQUIRED",
            "STATUTORY_REEVALUATION_REQUIRED",
            "APPROVAL_REVIEW_REQUIRED",
            "PUBLICATION_REQUIRED");

    assertThat(result.comparisonHash())
        .matches("[0-9a-f]{64}");
  }

  private SalaryStructureView structure(
      UUID versionId,
      int sequence,
      BigDecimal target,
      UUID componentVersionId,
      String configurationHash,
      String validationFingerprint,
      String approvalStatus) {
    SalaryStructureLineView line = new SalaryStructureLineView(
        UUID.randomUUID(),
        COMPONENT,
        componentVersionId,
        "BASIC",
        "Basic",
        "EARNING",
        "FIXED",
        1,
        (short) 1,
        "FIXED",
        target,
        null,
        null,
        BigDecimal.ZERO,
        null,
        true,
        "CONTROLLED",
        1,
        1,
        LocalDate.of(2027, 1, 1),
        null);

    return new SalaryStructureView(
        IDENTITY,
        "DEFAULT",
        "ACTIVE",
        versionId,
        sequence,
        sequence,
        "Default",
        "INR",
        (short) 1,
        "STANDARD",
        "MONTHLY",
        "STANDARD",
        CTC,
        null,
        "ANNUAL_CTC",
        "ANNUAL",
        target,
        BigDecimal.ONE,
        "STRUCTURAL",
        null,
        null,
        target,
        new BigDecimal("0.01"),
        componentVersionId,
        configurationHash,
        validationFingerprint,
        LocalDate.of(2027, 1, 1),
        null,
        approvalStatus,
        null,
        false,
        List.of(line));
  }
}
