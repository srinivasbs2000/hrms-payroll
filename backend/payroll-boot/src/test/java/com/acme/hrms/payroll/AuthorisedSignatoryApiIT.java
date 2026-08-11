package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AuthorisedSignatoryApiIT extends JrfApiITSupport {

  @Test
  void lifecycleEnforcesIndependentActorsAndEvaluatesDelegatedAuthority()
      throws Exception {
    String payload =
        createPayload(
            "PAYROLL_SIGNATORY",
            "Synthetic Payroll Director",
            "2026-01-01",
            "PAYROLL_FUNDING",
            "INR",
            "1000000.00");

    mvc.perform(
            post("/api/v1/authorised-signatories")
                .header("Idempotency-Key", "signatory-noauth-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/authorised-signatories")
                .with(
                    token(
                        TENANT_A,
                        "signatory-reader",
                        "organisation.signatory.read"))
                .header("Idempotency-Key", "signatory-forbidden-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden());

    JsonNode draft =
        json(
            mvc.perform(
                    post("/api/v1/authorised-signatories")
                        .with(
                            token(
                                TENANT_A,
                                "signatory-maker",
                                "organisation.signatory.write"))
                        .header("Idempotency-Key", "signatory-create-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.scopes.length()").value(1))
                .andExpect(
                    jsonPath("$.scopes[0].purposeCode")
                        .value("PAYROLL_FUNDING"))
                .andReturn());

    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();

    JsonNode submitted =
        transitionWithoutBody(
            identityId,
            versionId,
            "submit",
            "signatory-maker",
            "organisation.signatory.write",
            "signatory-submit-0001",
            draft.path("versionNo").asLong(),
            200);

    mvc.perform(
            post(
                    "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/verify",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "signatory-maker",
                        "organisation.signatory.verify"))
                .header("Idempotency-Key", "signatory-self-verify-0001")
                .header("If-Match", submitted.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceRef\":\"SIGNATORY:VERIFY:SELF\"}"))
        .andExpect(status().isForbidden());

    JsonNode verified =
        json(
            mvc.perform(
                    post(
                            "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/verify",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                "signatory-verifier",
                                "organisation.signatory.verify"))
                        .header("Idempotency-Key", "signatory-verify-0001")
                        .header(
                            "If-Match",
                            submitted.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"SIGNATORY:VERIFY:001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("VERIFIED"))
                .andReturn());

    JsonNode pending =
        transitionWithoutBody(
            identityId,
            versionId,
            "request-approval",
            "signatory-verifier",
            "organisation.signatory.verify",
            "signatory-request-0001",
            verified.path("versionNo").asLong(),
            200);

    mvc.perform(
            post(
                    "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/approve",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "signatory-verifier",
                        "organisation.signatory.approve"))
                .header("Idempotency-Key", "signatory-self-approve-0001")
                .header("If-Match", pending.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceRef\":\"SIGNATORY:APPROVE:SELF\"}"))
        .andExpect(status().isForbidden());

    JsonNode active =
        json(
            mvc.perform(
                    post(
                            "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/approve",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                "signatory-approver",
                                "organisation.signatory.approve"))
                        .header("Idempotency-Key", "signatory-approve-0001")
                        .header("If-Match", pending.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"SIGNATORY:APPROVE:001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.identityStatus").value("ACTIVE"))
                .andReturn());
    String activeAsOf = approvalDateUtc(active);

    mvc.perform(
            get("/api/v1/authorised-signatories/{identityId}", identityId)
                .with(
                    token(
                        TENANT_A,
                        "Synthetic Payroll Director",
                        "organisation.bank-account.read")))
        .andExpect(status().isForbidden());

    JsonNode allowed =
        evaluate("INR", "500000.00", "PAYROLL_FUNDING", activeAsOf);
    assertThat(allowed.path("authorised").asBoolean()).isTrue();
    assertThat(allowed.path("reasonCode").asText()).isEqualTo("AUTHORIZED");
    assertThat(allowed.path("signatoryVersionId").asText())
        .isEqualTo(active.path("versionId").asText());

    JsonNode overLimit =
        evaluate("INR", "1500000.00", "PAYROLL_FUNDING", activeAsOf);
    assertThat(overLimit.path("authorised").asBoolean()).isFalse();
    assertThat(overLimit.path("reasonCode").asText())
        .isEqualTo("AMOUNT_EXCEEDS_LIMIT");

    JsonNode wrongCurrency =
        evaluate("USD", "100.00", "PAYROLL_FUNDING", activeAsOf);
    assertThat(wrongCurrency.path("authorised").asBoolean()).isFalse();
    assertThat(wrongCurrency.path("reasonCode").asText())
        .isEqualTo("CURRENCY_MISMATCH");

    JsonNode wrongPurpose =
        evaluate("INR", "100.00", "STATUTORY_REMITTANCE", activeAsOf);
    assertThat(wrongPurpose.path("authorised").asBoolean()).isFalse();
    assertThat(wrongPurpose.path("reasonCode").asText())
        .isEqualTo("PURPOSE_NOT_AUTHORIZED");

    mvc.perform(
            get("/api/v1/authorised-signatories/{identityId}", identityId)
                .with(
                    token(
                        TENANT_B,
                        "other-tenant-reader",
                        "organisation.signatory.read")))
        .andExpect(status().isNotFound());

    mvc.perform(
            post(
                    "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/suspend",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "signatory-suspender",
                        "organisation.signatory.approve"))
                .header("Idempotency-Key", "signatory-suspend-0001")
                .header("If-Match", active.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Delegated authority withdrawn\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("SUSPENDED"));

    JsonNode afterSuspension =
        evaluate("INR", "100.00", "PAYROLL_FUNDING", activeAsOf);
    assertThat(afterSuspension.path("authorised").asBoolean()).isFalse();
    assertThat(afterSuspension.path("reasonCode").asText())
        .isEqualTo("NO_ACTIVE_SIGNATORY");
  }

  @Test
  void futureDatedSignatoryCannotActivateEarly()
      throws Exception {
    JsonNode draft =
        json(
            mvc.perform(
                    post("/api/v1/authorised-signatories")
                        .with(
                            token(
                                TENANT_A,
                                "future-maker",
                                "organisation.signatory.write"))
                        .header("Idempotency-Key", "future-signatory-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            createPayload(
                                "FUTURE_SIGNATORY",
                                "Future Director",
                                "2099-01-01",
                                "PAYROLL_FUNDING",
                                "INR",
                                "1000000.00")))
                .andExpect(status().isCreated())
                .andReturn());

    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();

    JsonNode submitted =
        transitionWithoutBody(
            identityId,
            versionId,
            "submit",
            "future-maker",
            "organisation.signatory.write",
            "future-submit",
            draft.path("versionNo").asLong(),
            200);
    JsonNode verified =
        verify(
            identityId,
            versionId,
            submitted.path("versionNo").asLong(),
            "future-verifier",
            "future-verify");
    JsonNode pending =
        transitionWithoutBody(
            identityId,
            versionId,
            "request-approval",
            "future-verifier",
            "organisation.signatory.verify",
            "future-request",
            verified.path("versionNo").asLong(),
            200);

    mvc.perform(
            post(
                    "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/approve",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "future-approver",
                        "organisation.signatory.approve"))
                .header("Idempotency-Key", "future-approve")
                .header("If-Match", pending.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceRef\":\"SIGNATORY:APPROVE:FUTURE\"}"))
        .andExpect(status().isConflict());
  }

  private static String approvalDateUtc(JsonNode active) {
    return Instant.parse(active.path("approvedAt").asText())
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString();
  }

  private JsonNode evaluate(
      String currency,
      String amount,
      String purpose,
      String asOf) throws Exception {
    String body =
        """
        {
          "ownerKind":"LEGAL_ENTITY",
          "legalEntityId":"%s",
          "purposeCode":"%s",
          "currencyCode":"%s",
          "amount":%s,
          "asOf":"%s"
        }
        """.formatted(LEGAL_ID, purpose, currency, amount, asOf);
    return json(
        mvc.perform(
                post("/api/v1/authorised-signatories/authority-evaluations")
                    .with(
                        token(
                            TENANT_A,
                            "authority-reader",
                            "organisation.signatory.read"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode verify(
      String identityId,
      String versionId,
      long versionNo,
      String subject,
      String key) throws Exception {
    return json(
        mvc.perform(
                post(
                        "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/verify",
                        identityId,
                        versionId)
                    .with(
                        token(
                            TENANT_A,
                            subject,
                            "organisation.signatory.verify"))
                    .header("Idempotency-Key", key)
                    .header("If-Match", Long.toString(versionNo))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"evidenceRef\":\"SIGNATORY:VERIFY:"
                            + key
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn());
  }

  private JsonNode transitionWithoutBody(
      String identityId,
      String versionId,
      String action,
      String subject,
      String permission,
      String idempotencyKey,
      long expectedVersion,
      int expectedStatus) throws Exception {
    var result =
        mvc.perform(
                post(
                        "/api/v1/authorised-signatories/{identityId}/versions/{versionId}/"
                            + action,
                        identityId,
                        versionId)
                    .with(token(TENANT_A, subject, permission))
                    .header("Idempotency-Key", idempotencyKey)
                    .header("If-Match", Long.toString(expectedVersion)))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return json(result);
  }

  private String createPayload(
      String code,
      String fullName,
      String effectiveFrom,
      String purpose,
      String currency,
      String maximumAmount) {
    return """
        {
          "code":"%s",
          "ownerKind":"LEGAL_ENTITY",
          "legalEntityId":"%s",
          "version":{
            "fullName":"%s",
            "designation":"Director",
            "authorityReference":"BOARD:2026:001",
            "effectiveFrom":"%s",
            "scopes":[
              {
                "purposeCode":"%s",
                "currencyCode":"%s",
                "maximumAmount":%s
              }
            ]
          }
        }
        """.formatted(
            code,
            LEGAL_ID,
            fullName,
            effectiveFrom,
            purpose,
            currency,
            maximumAmount);
  }
}
