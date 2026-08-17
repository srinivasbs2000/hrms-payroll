package com.acme.hrms.payroll.compensation;

import java.util.List;
import java.util.Map;

public final class SalaryStructureEventContract {
  public static final int SCHEMA_VERSION = 1;

  public static final String CREATED = "SalaryStructureCREATED";
  public static final String VERSION_CREATED =
      "SalaryStructureVERSION_CREATED";
  public static final String VERSION_CORRECTED =
      "SalaryStructureVERSION_CORRECTED";
  public static final String SIMULATED = "SalaryStructureSIMULATED";
  public static final String VALIDATION_BOUND =
      "SalaryStructureVALIDATION_BOUND";
  public static final String VERSION_APPROVED =
      "SalaryStructureVERSION_APPROVED";
  public static final String VERSION_END_DATED =
      "SalaryStructureVERSION_END_DATED";
  public static final String VERSION_SUBMITTED =
      "SalaryStructureVERSION_SUBMITTED";
  public static final String VERSION_REJECTED =
      "SalaryStructureVERSION_REJECTED";
  public static final String VERSION_PUBLISHED =
      "SalaryStructureVERSION_PUBLISHED";

  public static final String STATUTORY_BINDING_CREATED =
      "SalaryStructureStatutoryBindingCreated";
  public static final String STATUTORY_BINDING_RETIRED =
      "SalaryStructureStatutoryBindingRetired";
  public static final String STATUTORY_COMPATIBILITY_EVALUATED =
      "SalaryStructureStatutoryCompatibilityEvaluated";

  private SalaryStructureEventContract() {}

  public static String eventType(String action) {
    return switch (action) {
      case "CREATED" -> CREATED;
      case "VERSION_CREATED" -> VERSION_CREATED;
      case "VERSION_CORRECTED" -> VERSION_CORRECTED;
      case "VALIDATION_BOUND" -> VALIDATION_BOUND;
      case "VERSION_APPROVED" -> VERSION_APPROVED;
      case "VERSION_END_DATED" -> VERSION_END_DATED;
      case "VERSION_SUBMITTED" -> VERSION_SUBMITTED;
      case "VERSION_REJECTED" -> VERSION_REJECTED;
      case "VERSION_PUBLISHED" -> VERSION_PUBLISHED;
      default -> throw new IllegalArgumentException(
          "Unsupported salary-structure event action: " + action);
    };
  }

  public static String statutoryBindingEventType(String action) {
    return switch (action) {
      case "STATUTORY_BINDING_CREATED" -> STATUTORY_BINDING_CREATED;
      case "STATUTORY_BINDING_RETIRED" -> STATUTORY_BINDING_RETIRED;
      default -> throw new IllegalArgumentException(
          "Unsupported statutory binding event action: " + action);
    };
  }

  public static Map<String, Object> validatePayload(
      String eventType,
      Map<String, Object> payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Event payload is required");
    }
    for (String field : requiredFields(eventType)) {
      if (!payload.containsKey(field)) {
        throw new IllegalArgumentException(
            eventType + " payload is missing required field " + field);
      }
    }
    return payload;
  }

  private static List<String> requiredFields(String eventType) {
    return switch (eventType) {
      case CREATED,
          VERSION_CREATED,
          VERSION_CORRECTED,
          VALIDATION_BOUND,
          VERSION_APPROVED,
          VERSION_END_DATED ->
          List.of(
              "identityId",
              "versionId",
              "configurationHash",
              "approvalStatus");

      case SIMULATED ->
          List.of(
              "validationId",
              "versionId",
              "validationStatus",
              "configurationHash",
              "resultHash");

      case VERSION_SUBMITTED,
          VERSION_REJECTED,
          VERSION_PUBLISHED ->
          List.of(
              "versionId",
              "versionNo",
              "workflowStatus",
              "approvalStatus",
              "validationFingerprint",
              "statutoryBindingRevision");

      case STATUTORY_BINDING_CREATED,
          STATUTORY_BINDING_RETIRED ->
          List.of(
              "bindingId",
              "salaryStructureVersionId",
              "statutoryRuleVersionId",
              "status",
              "versionNo");

      case STATUTORY_COMPATIBILITY_EVALUATED ->
          List.of(
              "evaluationId",
              "validationId",
              "salaryStructureVersionId",
              "statutoryBindingRevision",
              "validationStatus",
              "evidenceHash");

      default -> throw new IllegalArgumentException(
          "Unknown salary-structure event type: " + eventType);
    };
  }
}
