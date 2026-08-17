package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.LineageView;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

class SalaryStructureLineageContractTest {
  @Test
  void versionLineageIsAuditRestrictedAndVersionScoped() throws Exception {
    Method method = SalaryStructureLineageController.class.getDeclaredMethod(
        "lineage",
        UUID.class,
        UUID.class);

    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/{identityId}/versions/{versionId}/lineage");
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAuthority('audit.read')");
    assertThat(method.getReturnType()).isEqualTo(LineageView.class);
  }

  @Test
  void lineageContractCarriesAllGovernedEvidenceLayers() {
    assertThat(SalaryStructureLineageControls.DISCLAIMER)
        .contains("AUDIT / LINEAGE EVIDENCE")
        .contains("READ ONLY")
        .contains("NOT AN EMPLOYEE PAYROLL");

    assertThat(LineageView.class.getRecordComponents())
        .extracting(component -> component.getName())
        .contains(
            "configurationHash",
            "validationFingerprint",
            "statutoryBindingRevision",
            "currentStatutoryEvidenceHash",
            "validations",
            "statutoryEvaluations",
            "workflowActions",
            "auditEvents",
            "domainEvents",
            "lineageHash");
  }
}
