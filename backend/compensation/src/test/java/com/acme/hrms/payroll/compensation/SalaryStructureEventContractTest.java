package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalaryStructureEventContractTest {
  @Test
  void eventNamesRemainBackwardCompatibleAndSchemaVersioned() {
    assertThat(SalaryStructureEventContract.SCHEMA_VERSION).isEqualTo(1);
    assertThat(SalaryStructureEventContract.eventType("CREATED"))
        .isEqualTo("SalaryStructureCREATED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_CREATED"))
        .isEqualTo("SalaryStructureVERSION_CREATED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_CORRECTED"))
        .isEqualTo("SalaryStructureVERSION_CORRECTED");
    assertThat(SalaryStructureEventContract.SIMULATED)
        .isEqualTo("SalaryStructureSIMULATED");
    assertThat(SalaryStructureEventContract.eventType("VALIDATION_BOUND"))
        .isEqualTo("SalaryStructureVALIDATION_BOUND");
    assertThat(SalaryStructureEventContract.eventType("VERSION_APPROVED"))
        .isEqualTo("SalaryStructureVERSION_APPROVED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_END_DATED"))
        .isEqualTo("SalaryStructureVERSION_END_DATED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_SUBMITTED"))
        .isEqualTo("SalaryStructureVERSION_SUBMITTED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_REJECTED"))
        .isEqualTo("SalaryStructureVERSION_REJECTED");
    assertThat(SalaryStructureEventContract.eventType("VERSION_PUBLISHED"))
        .isEqualTo("SalaryStructureVERSION_PUBLISHED");

    assertThat(
        SalaryStructureEventContract.statutoryBindingEventType(
            "STATUTORY_BINDING_CREATED"))
        .isEqualTo("SalaryStructureStatutoryBindingCreated");
    assertThat(
        SalaryStructureEventContract.statutoryBindingEventType(
            "STATUTORY_BINDING_RETIRED"))
        .isEqualTo("SalaryStructureStatutoryBindingRetired");
    assertThat(
        SalaryStructureEventContract.STATUTORY_COMPATIBILITY_EVALUATED)
        .isEqualTo("SalaryStructureStatutoryCompatibilityEvaluated");
  }

  @Test
  void corePayloadRequiresStableIdentityVersionConfigurationAndStatusFields() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("identityId", "identity");
    payload.put("versionId", "version");
    payload.put("configurationHash", "configuration-hash");
    payload.put("approvalStatus", "DRAFT");

    assertThat(SalaryStructureEventContract.validatePayload(
        SalaryStructureEventContract.VERSION_CREATED,
        payload)).isSameAs(payload);

    payload.remove("configurationHash");
    assertThatThrownBy(() -> SalaryStructureEventContract.validatePayload(
        SalaryStructureEventContract.VERSION_CREATED,
        payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configurationHash");
  }

  @Test
  void simulationLifecycleAndStatutoryPayloadsRequireDecisionEvidence() {
    assertThat(SalaryStructureEventContract.validatePayload(
        SalaryStructureEventContract.SIMULATED,
        Map.of(
            "validationId", "validation",
            "versionId", "version",
            "validationStatus", "PASS",
            "configurationHash", "configuration",
            "resultHash", "result")))
        .containsEntry("resultHash", "result");

    Map<String, Object> lifecycle = new LinkedHashMap<>();
    lifecycle.put("versionId", "version");
    lifecycle.put("versionNo", 7L);
    lifecycle.put("workflowStatus", "SUBMITTED");
    lifecycle.put("approvalStatus", "DRAFT");
    lifecycle.put("validationFingerprint", "validation-fingerprint");
    lifecycle.put("statutoryBindingRevision", 3L);
    assertThat(SalaryStructureEventContract.validatePayload(
        SalaryStructureEventContract.VERSION_SUBMITTED,
        lifecycle)).containsEntry("workflowStatus", "SUBMITTED");

    assertThat(SalaryStructureEventContract.validatePayload(
        SalaryStructureEventContract.STATUTORY_COMPATIBILITY_EVALUATED,
        Map.of(
            "evaluationId", "evaluation",
            "validationId", "validation",
            "salaryStructureVersionId", "version",
            "statutoryBindingRevision", 3L,
            "validationStatus", "PASS",
            "evidenceHash", "evidence")))
        .containsEntry("evidenceHash", "evidence");
  }

  @Test
  void unsupportedActionsAndUnknownEventTypesFailClosed() {
    assertThatThrownBy(() ->
        SalaryStructureEventContract.eventType("UNCONTROLLED"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported salary-structure event action");

    assertThatThrownBy(() ->
        SalaryStructureEventContract.validatePayload(
            "SalaryStructureUnknown",
            Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown salary-structure event type");
  }
}
