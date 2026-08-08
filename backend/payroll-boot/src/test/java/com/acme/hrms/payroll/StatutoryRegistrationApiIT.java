package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class StatutoryRegistrationApiIT extends JrfApiITSupport {
  @Test
  void registrationLifecycleIsVerifiedMakerCheckedNormalizedAndTenantScoped()
      throws Exception {
    EntityRef jurisdiction = createAndApproveJurisdiction("IN_COUNTRY_REG");
    EntityRef type = createAndApproveRegistrationType("GENERIC_REG_API");

    String payload = """
        {
          "registrationTypeId":"%s",
          "referenceCode":"REG_MAIN",
          "version":{
            "registrationTypeId":"%s",
            "registrationTypeVersionId":"%s",
            "identifier":"abc-123",
            "ownerKind":"LEGAL_ENTITY",
            "ownerId":"%s",
            "payrollJurisdictionId":"%s",
            "payrollJurisdictionVersionId":"%s",
            "effectiveFrom":"2026-01-01"
          }
        }
        """.formatted(
            type.identityId(),
            type.identityId(),
            type.versionId(),
            LEGAL_ID,
            jurisdiction.identityId(),
            jurisdiction.versionId());

    JsonNode draft = json(mvc.perform(post("/api/v1/statutory-registrations")
            .with(token(
                TENANT_A,
                "registration-maker",
                "statutory-registration.write"))
            .header("Idempotency-Key", "g02h-registration-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
        .andExpect(jsonPath("$.identifier").value("****-123"))
        .andExpect(jsonPath("$.identifierNormalized").value("****-123"))
        .andReturn());

    mvc.perform(post(
            "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/verification",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(
                TENANT_A,
                "registration-verifier",
                "statutory-registration.verify"))
            .header("Idempotency-Key", "g02h-registration-verify-before-submit")
            .header("If-Match", draft.path("versionNo").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"evidenceRef\":\"premature-verification\"}"))
        .andExpect(status().isConflict());

    ActiveRegistration active = createActiveRegistration(
        "REG_SECOND",
        jurisdiction,
        type);
    assertThat(active.identifierNormalized()).isEqualTo("****-123");

    mvc.perform(get("/api/v1/statutory-registrations")
            .with(token(
                TENANT_A,
                "registration-reader-a",
                "statutory-registration.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$[0].identifier").value("****-123"))
        .andExpect(jsonPath("$[0].identifierNormalized").value("****-123"));

    mvc.perform(
            post(
                    "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/identifier-reveal",
                    active.identityId(),
                    active.versionId())
                .with(
                    token(
                        TENANT_A,
                        "registration-reader-no-reveal",
                        "statutory-registration.read")))
        .andExpect(status().isForbidden());

    mvc.perform(
            post(
                "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/identifier-reveal",
                active.identityId(),
                active.versionId()))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            post(
                    "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/identifier-reveal",
                    active.identityId(),
                    active.versionId())
                .with(
                    token(
                        TENANT_B,
                        "cross-tenant-identifier-reviewer",
                        "statutory-registration.identifier.read")))
        .andExpect(status().isNotFound());

    mvc.perform(
            post(
                    "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/identifier-reveal",
                    active.identityId(),
                    active.versionId())
                .with(
                    token(
                        TENANT_A,
                        "registration-identifier-reviewer",
                        "statutory-registration.identifier.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.identifier").value("abc-123"))
        .andExpect(jsonPath("$.identifierNormalized").value("ABC-123"))
        .andExpect(header().string("Cache-Control", "no-store"));

    assertThat(
            evidenceContainsExactIdentifier(
                "audit.audit_event",
                "coalesce(before_state,'{}'::jsonb)::text || "
                    + "coalesce(after_state,'{}'::jsonb)::text || "
                    + "metadata::text",
                "ABC-123"))
        .isFalse();
    assertThat(
            evidenceContainsExactIdentifier(
                "integration.outbox_event",
                "payload::text || headers::text",
                "ABC-123"))
        .isFalse();

    mvc.perform(get("/api/v1/statutory-registrations")
            .with(token(
                TENANT_B,
                "registration-reader-b",
                "statutory-registration.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    assertThat(tenantCount("statutory.registration", TENANT_A)).isEqualTo(2L);
    assertThat(tenantCount("statutory.registration", TENANT_B)).isZero();
  }

  @Test
  void registrationTypePatternContractIsExplicitAndMalformedPatternsAreRejected()
      throws Exception {
    String invalidPatternPayload = """
        {
          "code":"INVALID_PATTERN_API",
          "version":{
            "name":"Invalid pattern registration",
            "obligationCode":"GENERIC_OBLIGATION",
            "authorityCode":"GENERIC_AUTHORITY",
            "jurisdictionLevelCode":"COUNTRY",
            "identifierPattern":"[",
            "identifierCasePolicy":"UPPER",
            "parentRequired":false,
            "ownerKinds":["LEGAL_ENTITY"],
            "effectiveFrom":"2026-01-01"
          }
        }
        """;

    mvc.perform(post("/api/v1/statutory-registration-types")
            .with(token(
                TENANT_A,
                "invalid-pattern-maker",
                "statutory-registration-type.write"))
            .header("Idempotency-Key", "invalid-pattern-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidPatternPayload))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.type")
            .value("urn:problem:unprocessable-entity"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.detail")
            .value("identifierPattern must be valid JAVA_REGEX_V1"));

    String validPatternPayload = """
        {
          "code":"JAVA_REGEX_API",
          "version":{
            "name":"Java regex registration",
            "obligationCode":"GENERIC_OBLIGATION",
            "authorityCode":"GENERIC_AUTHORITY",
            "jurisdictionLevelCode":"COUNTRY",
            "identifierPattern":"ABC-[0-9]{3}",
            "identifierCasePolicy":"UPPER",
            "parentRequired":false,
            "ownerKinds":["LEGAL_ENTITY"],
            "effectiveFrom":"2026-01-01"
          }
        }
        """;

    mvc.perform(post("/api/v1/statutory-registration-types")
            .with(token(
                TENANT_A,
                "java-regex-maker",
                "statutory-registration-type.write"))
            .header("Idempotency-Key", "java-regex-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPatternPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.identifierPatternDialect").value("JAVA_REGEX_V1"));
  }

  @Test
  void registrationTypeFutureDraftKeepsCurrentApprovedVersionVisible()
      throws Exception {
    EntityRef current =
        createAndApproveRegistrationType("TYPE_FUTURE_DRAFT");

    String successorPayload = """
        {
          "name":"Future registration type",
          "obligationCode":"GENERIC_OBLIGATION",
          "authorityCode":"GENERIC_AUTHORITY",
          "jurisdictionLevelCode":"COUNTRY",
          "identifierPattern":"^[A-Z0-9-]{3,30}$",
          "identifierCasePolicy":"UPPER",
          "parentRequired":false,
          "ownerKinds":["LEGAL_ENTITY"],
          "effectiveFrom":"2027-01-01"
        }
        """;

    mvc.perform(
            post(
                    "/api/v1/statutory-registration-types/{identityId}/versions",
                    current.identityId())
                .with(
                    token(
                        TENANT_A,
                        "type-version-maker",
                        "statutory-registration-type.write"))
                .header(
                    "Idempotency-Key",
                    "type-future-draft-version-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(successorPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.versionSequence").value(2))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"));

    mvc.perform(
            get("/api/v1/statutory-registration-types")
                .with(
                    token(
                        TENANT_A,
                        "type-current-reader",
                        "statutory-registration.read"))
                .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(
            jsonPath("$[0].versionId").value(current.versionId()));
  }

  private boolean evidenceContainsExactIdentifier(
      String table,
      String evidenceExpression,
      String identifier) throws Exception {
    if (!table.matches("[a-z_]+\\.[a-z_]+")) {
      throw new IllegalArgumentException("Unsafe evidence table");
    }
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select count(*) from "
                    + table
                    + " where tenant_id='"
                    + TENANT_A
                    + "' and lower("
                    + evidenceExpression
                    + ") like '%"
                    + identifier.toLowerCase()
                    + "%'")) {
      result.next();
      return result.getLong(1) > 0;
    }
  }
}
