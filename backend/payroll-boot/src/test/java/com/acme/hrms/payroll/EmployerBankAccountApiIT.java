package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class EmployerBankAccountApiIT extends JrfApiITSupport {
  private static final String ACCOUNT_NUMBER = "0011-2233-4455-6677";

  @DynamicPropertySource
  static void bankCryptoProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "PAYROLL_BANK_ACTIVE_KEY_VERSION",
        () -> "v1");
    registry.add(
        "PAYROLL_BANK_ENCRYPTION_KEYS",
        () ->
            "v1="
                + Base64.getEncoder()
                    .encodeToString(keyBytes("encryption")));
    registry.add(
        "PAYROLL_BANK_FINGERPRINT_KEY",
        () ->
            Base64.getEncoder()
                .encodeToString(keyBytes("fingerprint")));
  }

  @Test
  void bankLifecycleMasksSecretsAndRequiresIndependentActors()
      throws Exception {
    String payload = createPayload("PAYROLL_MAIN", ACCOUNT_NUMBER, true);

    mvc.perform(
            post("/api/v1/employer-bank-accounts")
                .header("Idempotency-Key", "bank-noauth-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post("/api/v1/employer-bank-accounts")
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read"))
                .header("Idempotency-Key", "bank-forbidden-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden());

    JsonNode draft =
        json(
            mvc.perform(
                    post("/api/v1/employer-bank-accounts")
                        .with(
                            token(
                                TENANT_A,
                                "bank-maker",
                                "organisation.bank-account.write"))
                        .header("Idempotency-Key", "bank-create-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.maskedAccountNumber").value("****6677"))
                .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
                .andReturn());

    assertNoSecretFields(draft);

    mvc.perform(
            post("/api/v1/employer-bank-accounts")
                .with(
                    token(
                        TENANT_A,
                        "bank-maker",
                        "organisation.bank-account.write"))
                .header("Idempotency-Key", "bank-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    createPayload(
                        "PAYROLL_MAIN",
                        "9999-8888-7777-6666",
                        true)))
        .andExpect(status().isConflict());

    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();

    JsonNode submitted =
        transitionWithoutBody(
            identityId,
            versionId,
            "submit",
            "bank-maker",
            "organisation.bank-account.write",
            "bank-submit-0001",
            draft.path("versionNo").asLong(),
            200);
    assertThat(submitted.path("lifecycleStatus").asText())
        .isEqualTo("PENDING_VERIFICATION");

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/verify",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "bank-maker",
                        "organisation.bank-account.verify"))
                .header("Idempotency-Key", "bank-self-verify-0001")
                .header("If-Match", submitted.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceRef\":\"BANK:VERIFY:001\"}"))
        .andExpect(status().isForbidden());

    JsonNode verified =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/verify",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                "bank-verifier",
                                "organisation.bank-account.verify"))
                        .header("Idempotency-Key", "bank-verify-0001")
                        .header(
                            "If-Match",
                            submitted.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"BANK:VERIFY:001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("VERIFIED"))
                .andReturn());

    JsonNode pending =
        transitionWithoutBody(
            identityId,
            versionId,
            "request-approval",
            "bank-verifier",
            "organisation.bank-account.verify",
            "bank-request-approval-0001",
            verified.path("versionNo").asLong(),
            200);
    assertThat(pending.path("lifecycleStatus").asText())
        .isEqualTo("APPROVAL_PENDING");

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/approve",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "bank-verifier",
                        "organisation.bank-account.approve"))
                .header("Idempotency-Key", "bank-self-approve-0001")
                .header("If-Match", pending.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceRef\":\"BANK:APPROVE:001\"}"))
        .andExpect(status().isForbidden());

    JsonNode active =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/approve",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                "bank-approver",
                                "organisation.bank-account.approve"))
                        .header("Idempotency-Key", "bank-approve-0001")
                        .header(
                            "If-Match",
                            pending.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"BANK:APPROVE:001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.identityStatus").value("ACTIVE"))
                .andExpect(
                    jsonPath("$.maskedAccountNumber").value("****6677"))
                .andReturn());

    assertNoSecretFields(active);

    JsonNode listed =
        json(
            mvc.perform(
                    get("/api/v1/employer-bank-accounts")
                        .with(
                            token(
                                TENANT_A,
                                "bank-reader",
                                "organisation.bank-account.read"))
                        .param("ownerKind", "LEGAL_ENTITY")
                        .param("ownerId", LEGAL_ID)
                        .param("currencyCode", "INR")
                        .param("asOf", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                    jsonPath("$[0].maskedAccountNumber")
                        .value("****6677"))
                .andReturn());
    assertNoSecretFields(listed.get(0));

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/reveal",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Treasury verification\"}"))
        .andExpect(status().isForbidden());

    JsonNode revealed =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/reveal",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                "bank-revealer",
                                "organisation.bank-account.reveal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"reason\":\"Treasury verification\"}"))
                .andExpect(status().isOk())
                .andExpect(
                    header().string(
                        "Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andReturn());

    assertThat(revealed.path("accountNumber").asText())
        .isEqualTo(ACCOUNT_NUMBER);

    mvc.perform(
            get("/api/v1/employer-bank-accounts")
                .with(
                    token(
                        TENANT_B,
                        "tenant-b-reader",
                        "organisation.bank-account.read"))
                .param("asOf", "2026-08-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/reveal",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_B,
                        "tenant-b-revealer",
                        "organisation.bank-account.reveal"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cross tenant attempt\"}"))
        .andExpect(status().isNotFound());

    assertCiphertextAndRevealAuditAreSafe(versionId);

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/suspend",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "bank-suspender",
                        "organisation.bank-account.approve"))
                .header("Idempotency-Key", "bank-suspend-0001")
                .header("If-Match", active.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Funding account temporarily unavailable\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("SUSPENDED"));

    mvc.perform(
            get("/api/v1/employer-bank-accounts/{identityId}", identityId)
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read"))
                .param("asOf", "2026-08-10"))
        .andExpect(status().isNotFound());
  }

  @Test
  void successorDraftPreservesActiveCurrentAndHistory()
      throws Exception {
    JsonNode draft =
        createDraft(
            "PAYROLL_VERSIONED",
            "1111-2222-3333-4444",
            false,
            "2026-01-01");
    JsonNode active = activate(draft, "versioned-1");

    String identityId = active.path("identityId").asText();

    String successorPayload =
        """
        {
          "bankName":"Example Bank",
          "branchName":"Bengaluru Main",
          "routingCode":"EXAMPLE001",
          "accountHolderName":"Example Payroll Employer",
          "currencyCode":"INR",
          "accountNumber":"1111-2222-3333-4444",
          "defaultAccount":false,
          "effectiveFrom":"2099-01-01"
        }
        """;

    JsonNode successor =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions",
                            identityId)
                        .with(
                            token(
                                TENANT_A,
                                "successor-maker",
                                "organisation.bank-account.write"))
                        .header(
                            "Idempotency-Key",
                            "bank-successor-create-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successorPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionSequence").value(2))
                .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
                .andReturn());

    mvc.perform(
            get("/api/v1/employer-bank-accounts/{identityId}", identityId)
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read"))
                .param("asOf", "2026-08-10"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.versionId")
                .value(active.path("versionId").asText()));

    mvc.perform(
            get(
                    "/api/v1/employer-bank-accounts/{identityId}/versions",
                    identityId)
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(
            jsonPath("$[0].versionId")
                .value(successor.path("versionId").asText()));

    JsonNode submitted =
        transitionWithoutBody(
            identityId,
            successor.path("versionId").asText(),
            "submit",
            "successor-maker",
            "organisation.bank-account.write",
            "bank-successor-submit-0001",
            successor.path("versionNo").asLong(),
            200);

    JsonNode verified =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/verify",
                            identityId,
                            successor.path("versionId").asText())
                        .with(
                            token(
                                TENANT_A,
                                "successor-verifier",
                                "organisation.bank-account.verify"))
                        .header(
                            "Idempotency-Key",
                            "bank-successor-verify-0001")
                        .header(
                            "If-Match",
                            submitted.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"BANK:VERIFY:FUTURE\"}"))
                .andExpect(status().isOk())
                .andReturn());

    JsonNode pending =
        transitionWithoutBody(
            identityId,
            successor.path("versionId").asText(),
            "request-approval",
            "successor-verifier",
            "organisation.bank-account.verify",
            "bank-successor-request-0001",
            verified.path("versionNo").asLong(),
            200);

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/approve",
                    identityId,
                    successor.path("versionId").asText())
                .with(
                    token(
                        TENANT_A,
                        "successor-approver",
                        "organisation.bank-account.approve"))
                .header(
                    "Idempotency-Key",
                    "bank-successor-future-approve-0001")
                .header("If-Match", pending.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"evidenceRef\":\"BANK:APPROVE:FUTURE\"}"))
        .andExpect(status().isConflict());

    mvc.perform(
            post(
                    "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/reject",
                    identityId,
                    successor.path("versionId").asText())
                .with(
                    token(
                        TENANT_A,
                        "successor-rejector",
                        "organisation.bank-account.approve"))
                .header(
                    "Idempotency-Key",
                    "bank-successor-reject-0001")
                .header("If-Match", pending.path("versionNo").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reason\":\"Future authority not yet approved\","
                        + "\"evidenceRef\":\"BANK:REJECT:FUTURE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("REJECTED"));
  }

  private JsonNode createDraft(
      String code,
      String accountNumber,
      boolean defaultAccount,
      String effectiveFrom) throws Exception {
    return json(
        mvc.perform(
                post("/api/v1/employer-bank-accounts")
                    .with(
                        token(
                            TENANT_A,
                            code + "-maker",
                            "organisation.bank-account.write"))
                    .header("Idempotency-Key", code + "-create-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            code,
                            accountNumber,
                            defaultAccount,
                            effectiveFrom)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private JsonNode activate(JsonNode draft, String keyPrefix)
      throws Exception {
    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();
    String maker = draft.path("code").asText() + "-maker";

    JsonNode submitted =
        transitionWithoutBody(
            identityId,
            versionId,
            "submit",
            maker,
            "organisation.bank-account.write",
            keyPrefix + "-submit",
            draft.path("versionNo").asLong(),
            200);

    JsonNode verified =
        json(
            mvc.perform(
                    post(
                            "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/verify",
                            identityId,
                            versionId)
                        .with(
                            token(
                                TENANT_A,
                                keyPrefix + "-verifier",
                                "organisation.bank-account.verify"))
                        .header("Idempotency-Key", keyPrefix + "-verify")
                        .header(
                            "If-Match",
                            submitted.path("versionNo").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"evidenceRef\":\"BANK:VERIFY:"
                                + keyPrefix
                                + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

    JsonNode pending =
        transitionWithoutBody(
            identityId,
            versionId,
            "request-approval",
            keyPrefix + "-verifier",
            "organisation.bank-account.verify",
            keyPrefix + "-request",
            verified.path("versionNo").asLong(),
            200);

    return json(
        mvc.perform(
                post(
                        "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/approve",
                        identityId,
                        versionId)
                    .with(
                        token(
                            TENANT_A,
                            keyPrefix + "-approver",
                            "organisation.bank-account.approve"))
                    .header("Idempotency-Key", keyPrefix + "-approve")
                    .header(
                        "If-Match",
                        pending.path("versionNo").asText())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"evidenceRef\":\"BANK:APPROVE:"
                            + keyPrefix
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
                        "/api/v1/employer-bank-accounts/{identityId}/versions/{versionId}/"
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

  private void assertNoSecretFields(JsonNode node) {
    assertThat(node.has("accountNumber")).isFalse();
    assertThat(node.has("accountNumberCiphertext")).isFalse();
    assertThat(node.has("accountNumberFingerprint")).isFalse();
    assertThat(node.has("encryptionKeyVersion")).isFalse();
  }

  private void assertCiphertextAndRevealAuditAreSafe(String versionId)
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result =
          statement.executeQuery(
              """
              select
                encode(account_number_ciphertext,'hex') ciphertext_hex,
                account_number_fingerprint
              from organisation.employer_bank_account_version
              where id='%s'
              """.formatted(versionId))) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("ciphertext_hex"))
            .doesNotContain("0011223344556677");
        assertThat(result.getString("account_number_fingerprint"))
            .hasSize(64);
      }

      try (ResultSet result =
          statement.executeQuery(
              """
              select before_state::text,after_state::text,metadata::text
              from audit.audit_event
              where object_type='EMPLOYER_BANK_ACCOUNT'
                and object_id=(
                  select employer_bank_account_id
                  from organisation.employer_bank_account_version
                  where id='%s'
                )
                and action='ACCOUNT_NUMBER_REVEALED'
              order by occurred_at desc
              limit 1
              """.formatted(versionId))) {
        assertThat(result.next()).isTrue();
        String auditText =
            String.valueOf(result.getString(1))
                + String.valueOf(result.getString(2))
                + String.valueOf(result.getString(3));
        assertThat(auditText)
            .doesNotContain(ACCOUNT_NUMBER)
            .doesNotContain("0011223344556677")
            .contains("****6677")
            .contains("Treasury verification");
      }
    }
  }

  private static byte[] keyBytes(String seed) {
    byte[] source = seed.getBytes(StandardCharsets.UTF_8);
    byte[] key = new byte[32];
    for (int index = 0; index < key.length; index++) {
      key[index] = source[index % source.length];
    }
    return key;
  }

  private String createPayload(
      String code,
      String accountNumber,
      boolean defaultAccount) {
    return createPayload(
        code,
        accountNumber,
        defaultAccount,
        "2026-01-01");
  }

  private String createPayload(
      String code,
      String accountNumber,
      boolean defaultAccount,
      String effectiveFrom) {
    return """
        {
          "code":"%s",
          "ownerKind":"LEGAL_ENTITY",
          "legalEntityId":"%s",
          "version":{
            "bankName":"Example Bank",
            "branchName":"Bengaluru Main",
            "routingCode":"EXAMPLE001",
            "accountHolderName":"Example Payroll Employer",
            "currencyCode":"INR",
            "accountNumber":"%s",
            "defaultAccount":%s,
            "effectiveFrom":"%s"
          }
        }
        """.formatted(
            code,
            LEGAL_ID,
            accountNumber,
            defaultAccount,
            effectiveFrom);
  }
}
