package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class BankingReadinessApiIT extends JrfApiITSupport {
  private static final UUID READY_BANK =
      UUID.fromString("95000000-0000-0000-0000-000000000001");
  private static final UUID READY_BANK_VERSION =
      UUID.fromString("95100000-0000-0000-0000-000000000001");
  private static final UUID DEFAULT_READY_BANK =
      UUID.fromString("95000000-0000-0000-0000-000000000002");
  private static final UUID DEFAULT_READY_BANK_VERSION =
      UUID.fromString("95100000-0000-0000-0000-000000000002");
  private static final UUID READY_SIGNATORY =
      UUID.fromString("96000000-0000-0000-0000-000000000001");
  private static final UUID READY_SIGNATORY_VERSION =
      UUID.fromString("96100000-0000-0000-0000-000000000001");

  @Test
  void boundedReadinessRequiresDefaultBankAndDelegatedAuthority()
      throws Exception {
    seedActiveBank(
        READY_BANK,
        READY_BANK_VERSION,
        "READY_BANK",
        false,
        "1234",
        "a".repeat(64),
        "bank-maker",
        "2026-01-01",
        "2026-01-03T00:00:00Z");
    seedActiveSignatory();

    JsonNode missingDefault = readiness("500000.00");
    assertThat(missingDefault.path("readinessScope").asText())
        .isEqualTo("BANKING_AND_SIGNATORY_ONLY");
    assertThat(missingDefault.path("bankReady").asBoolean()).isFalse();
    assertThat(missingDefault.path("signatoryReady").asBoolean()).isTrue();
    assertThat(missingDefault.path("ready").asBoolean()).isFalse();
    assertThat(findingCodes(missingDefault))
        .contains("DEFAULT_BANK_ACCOUNT_MISSING");

    seedActiveBank(
        DEFAULT_READY_BANK,
        DEFAULT_READY_BANK_VERSION,
        "READY_DEFAULT_BANK",
        true,
        "5678",
        "b".repeat(64),
        "default-bank-maker",
        "2026-01-01",
        "2026-01-04T00:00:00Z");

    JsonNode ready = readiness("500000.00");
    assertThat(ready.path("bankReady").asBoolean()).isTrue();
    assertThat(ready.path("signatoryReady").asBoolean()).isTrue();
    assertThat(ready.path("ready").asBoolean()).isTrue();
    assertThat(ready.path("findings").size()).isZero();

    JsonNode overLimit = readiness("1500000.00");
    assertThat(overLimit.path("bankReady").asBoolean()).isTrue();
    assertThat(overLimit.path("signatoryReady").asBoolean()).isFalse();
    assertThat(overLimit.path("ready").asBoolean()).isFalse();
    assertThat(findingCodes(overLimit))
        .contains("SIGNATORY_AMOUNT_LIMIT_EXCEEDED");

    mvc.perform(
            get("/api/v1/banking-readiness")
                .with(
                    token(
                        TENANT_B,
                        "other-tenant-readiness",
                        "organisation.banking-readiness.read"))
                .param("ownerKind", "LEGAL_ENTITY")
                .param("ownerId", LEGAL_ID.toString())
                .param("currencyCode", "INR")
                .param("purposeCode", "PAYROLL_FUNDING")
                .param("amount", "500000.00")
                .param("asOf", "2026-08-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(
            jsonPath("$.findings[0].code")
                .value("BANK_ACCOUNT_MISSING"));
  }

  @Test
  void suspendedSuccessorDoesNotResurrectSupersededBankVersion()
      throws Exception {
    UUID identity =
        UUID.fromString("97000000-0000-0000-0000-000000000001");
    UUID predecessor =
        UUID.fromString("97100000-0000-0000-0000-000000000001");
    UUID successor =
        UUID.fromString("97100000-0000-0000-0000-000000000002");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      setTenantContext(statement);

      statement.execute(
          """
          insert into organisation.employer_bank_account(
            id,tenant_id,code,owner_kind,legal_entity_id,
            created_by,updated_by
          ) values (
            '%s','%s','HISTORICAL_BANK','LEGAL_ENTITY','%s',
            'history-maker','history-maker'
          )
          """.formatted(identity, TENANT_A, LEGAL_ID));

      insertBankDraft(
          statement,
          predecessor,
          identity,
          1,
          null,
          "1111",
          "1".repeat(64),
          false,
          "history-maker",
          "2026-01-01");

      advanceBankToActive(
          statement,
          predecessor,
          "history-maker",
          "history-verifier",
          "history-approver",
          "2026-01-03T00:00:00Z");

      insertBankDraft(
          statement,
          successor,
          identity,
          2,
          predecessor,
          "2222",
          "2".repeat(64),
          false,
          "successor-maker",
          "2026-02-01");

      advanceBankToActive(
          statement,
          successor,
          "successor-maker",
          "successor-verifier",
          "successor-approver",
          "2026-02-03T00:00:00Z");

      assertFunctionResult(
          statement,
          """
          select organisation.suspend_employer_bank_account_version(
            '%s','%s',4,'successor-suspender',
            'Funding authority withdrawn','2026-03-01T00:00:00Z'
          )
          """.formatted(TENANT_A, successor));
    }

    mvc.perform(
            get("/api/v1/employer-bank-accounts/{identityId}", identity)
                .with(
                    token(
                        TENANT_A,
                        "bank-reader",
                        "organisation.bank-account.read"))
                .param("asOf", "2026-08-10"))
        .andExpect(status().isNotFound());
  }

  private JsonNode readiness(String amount) throws Exception {
    return json(
        mvc.perform(
                get("/api/v1/banking-readiness")
                    .with(
                        token(
                            TENANT_A,
                            "readiness-reader",
                            "organisation.banking-readiness.read"))
                    .param("ownerKind", "LEGAL_ENTITY")
                    .param("ownerId", LEGAL_ID.toString())
                    .param("currencyCode", "INR")
                    .param("purposeCode", "PAYROLL_FUNDING")
                    .param("amount", amount)
                    .param("asOf", "2026-08-10"))
            .andExpect(status().isOk())
            .andReturn());
  }

  private void seedActiveBank(
      UUID identityId,
      UUID versionId,
      String code,
      boolean defaultAccount,
      String last4,
      String fingerprint,
      String maker,
      String effectiveFrom,
      String approvedAt)
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      setTenantContext(statement);

      statement.execute(
          """
          insert into organisation.employer_bank_account(
            id,tenant_id,code,owner_kind,legal_entity_id,
            created_by,updated_by
          ) values (
            '%s','%s','%s','LEGAL_ENTITY','%s',
            '%s','%s'
          )
          """.formatted(
              identityId,
              TENANT_A,
              code,
              LEGAL_ID,
              maker,
              maker));

      insertBankDraft(
          statement,
          versionId,
          identityId,
          1,
          null,
          last4,
          fingerprint,
          defaultAccount,
          maker,
          effectiveFrom);

      advanceBankToActive(
          statement,
          versionId,
          maker,
          maker + "-verifier",
          maker + "-approver",
          approvedAt);
    }
  }

  private void insertBankDraft(
      Statement statement,
      UUID versionId,
      UUID identityId,
      int sequence,
      UUID supersedes,
      String last4,
      String fingerprint,
      boolean defaultAccount,
      String maker,
      String effectiveFrom)
      throws Exception {
    String supersedesSql =
        supersedes == null ? "NULL" : "'" + supersedes + "'";
    statement.execute(
        """
        insert into organisation.employer_bank_account_version(
          id,tenant_id,employer_bank_account_id,owner_key,version_sequence,
          bank_name,account_holder_name,currency_code,
          account_number_ciphertext,account_number_iv,
          encryption_key_version,account_number_fingerprint,
          account_number_last4,is_default,effective_from,
          supersedes_version_id,created_by,updated_by
        ) values (
          '%s','%s','%s','LEGAL_ENTITY:%s',%s,
          'Readiness Bank','Readiness Employer','INR',
          decode('0102030405060708090a0b0c0d0e0f1011121314','hex'),
          decode('0102030405060708090a0b0c','hex'),
          'v1','%s','%s',%s,'%s',
          %s,'%s','%s'
        )
        """.formatted(
            versionId,
            TENANT_A,
            identityId,
            LEGAL_ID,
            sequence,
            fingerprint,
            last4,
            defaultAccount,
            effectiveFrom,
            supersedesSql,
            maker,
            maker));
  }

  private void advanceBankToActive(
      Statement statement,
      UUID versionId,
      String maker,
      String verifier,
      String approver,
      String approvedAt)
      throws Exception {
    assertFunctionResult(
        statement,
        """
        select organisation.submit_employer_bank_account_version(
          '%s','%s',0,'%s','2026-01-01T01:00:00Z'
        )
        """.formatted(TENANT_A, versionId, maker));
    assertFunctionResult(
        statement,
        """
        select organisation.verify_employer_bank_account_version(
          '%s','%s',1,'%s','BANK:VERIFY:%s','2026-01-01T02:00:00Z'
        )
        """.formatted(TENANT_A, versionId, verifier, versionId));
    assertFunctionResult(
        statement,
        """
        select organisation.request_employer_bank_account_approval(
          '%s','%s',2,'%s','2026-01-01T03:00:00Z'
        )
        """.formatted(TENANT_A, versionId, verifier));
    assertFunctionResult(
        statement,
        """
        select organisation.activate_employer_bank_account_version(
          '%s','%s',3,'%s','BANK:APPROVE:%s','%s'
        )
        """.formatted(TENANT_A, versionId, approver, versionId, approvedAt));
  }

  private void seedActiveSignatory() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      setTenantContext(statement);

      statement.execute(
          """
          insert into organisation.authorised_signatory(
            id,tenant_id,code,owner_kind,legal_entity_id,
            created_by,updated_by
          ) values (
            '%s','%s','READY_SIGNATORY','LEGAL_ENTITY','%s',
            'signatory-maker','signatory-maker'
          )
          """.formatted(READY_SIGNATORY, TENANT_A, LEGAL_ID));

      statement.execute(
          """
          insert into organisation.authorised_signatory_version(
            id,tenant_id,authorised_signatory_id,owner_key,version_sequence,
            full_name,designation,authority_reference,effective_from,
            created_by,updated_by
          ) values (
            '%s','%s','%s','LEGAL_ENTITY:%s',1,
            'Ready Director','Director','BOARD:READY:001','2026-01-01',
            'signatory-maker','signatory-maker'
          )
          """.formatted(
              READY_SIGNATORY_VERSION,
              TENANT_A,
              READY_SIGNATORY,
              LEGAL_ID));

      statement.execute(
          """
          insert into organisation.authorised_signatory_scope(
            tenant_id,authorised_signatory_id,authorised_signatory_version_id,
            purpose_code,currency_code,maximum_amount,created_by
          ) values (
            '%s','%s','%s','PAYROLL_FUNDING','INR',1000000.00,
            'signatory-maker'
          )
          """.formatted(
              TENANT_A,
              READY_SIGNATORY,
              READY_SIGNATORY_VERSION));

      assertFunctionResult(
          statement,
          """
          select organisation.submit_authorised_signatory_version(
            '%s','%s',0,'signatory-maker','2026-01-01T01:00:00Z'
          )
          """.formatted(TENANT_A, READY_SIGNATORY_VERSION));
      assertFunctionResult(
          statement,
          """
          select organisation.verify_authorised_signatory_version(
            '%s','%s',1,'signatory-verifier',
            'SIGNATORY:VERIFY:READY','2026-01-01T02:00:00Z'
          )
          """.formatted(TENANT_A, READY_SIGNATORY_VERSION));
      assertFunctionResult(
          statement,
          """
          select organisation.request_authorised_signatory_approval(
            '%s','%s',2,'signatory-verifier','2026-01-01T03:00:00Z'
          )
          """.formatted(TENANT_A, READY_SIGNATORY_VERSION));
      assertFunctionResult(
          statement,
          """
          select organisation.activate_authorised_signatory_version(
            '%s','%s',3,'signatory-approver',
            'SIGNATORY:APPROVE:READY','2026-01-03T00:00:00Z'
          )
          """.formatted(TENANT_A, READY_SIGNATORY_VERSION));
    }
  }

  private void setTenantContext(Statement statement) throws Exception {
    statement.execute(
        """
        select set_config(
          'app.tenant_id',
          '%s',
          false
        )
        """.formatted(TENANT_A));
  }

  private void assertFunctionResult(
      Statement statement,
      String sql)
      throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(1);
    }
  }

  private java.util.List<String> findingCodes(JsonNode readiness) {
    java.util.List<String> codes = new java.util.ArrayList<>();
    readiness
        .path("findings")
        .forEach(node -> codes.add(node.path("code").asText()));
    return codes;
  }
}
