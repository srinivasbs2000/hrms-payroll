package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class EligibilityRuleApiIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_ENTITY_VERSION_ID =
      "20000000-0000-0000-0000-000000000001";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  static {
    POSTGRES.start();
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "payroll_app");
    registry.add("spring.datasource.password", () -> APP_PASSWORD);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "https://issuer.example.test");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "https://issuer.example.test/jwks");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean JwtDecoder jwtDecoder;

  @BeforeEach
  void seedTenants() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) "
              + "VALUES ('" + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void lifecycleEvaluationIsDeterministicAuditedAndTenantIsolated()
      throws Exception {
    String request = rulePayload("INDIA_STANDARD_ELIGIBILITY");

    MvcResult created = mvc.perform(
            post("/api/v1/eligibility-rules")
                .with(token(
                    TENANT_A,
                    "rule-maker",
                    "compensation.eligibility-rule.create"))
                .header(
                    "Idempotency-Key",
                    "create-eligibility-rule-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.lifecycleStatus")
            .value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.criteria.length()").value(4))
        .andReturn();

    JsonNode first = objectMapper.readTree(
        created.getResponse().getContentAsString());
    MvcResult replay = mvc.perform(
            post("/api/v1/eligibility-rules")
                .with(token(
                    TENANT_A,
                    "rule-maker",
                    "compensation.eligibility-rule.create"))
                .header(
                    "Idempotency-Key",
                    "create-eligibility-rule-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode replayed = objectMapper.readTree(
        replay.getResponse().getContentAsString());
    assertThat(replayed.get("identityId").asText())
        .isEqualTo(first.get("identityId").asText());

    String identityId = first.get("identityId").asText();
    String versionId = first.get("versionId").asText();

    DatabaseCounts beforeEvaluation = counts(identityId);
    MvcResult draftEvaluation = evaluate(
        identityId,
        versionId,
        matchingFacts(false));
    JsonNode draftResult = objectMapper.readTree(
        draftEvaluation.getResponse().getContentAsString());
    assertThat(draftResult.get("result").asText())
        .isEqualTo("ELIGIBLE");
    assertThat(draftResult.get("matched").asBoolean()).isTrue();
    assertThat(draftResult.get("disclaimer").asText())
        .isEqualTo(
            "DESIGN-TIME ELIGIBILITY EVALUATION — "
                + "NOT AN EMPLOYEE ELIGIBILITY DECISION");
    assertThat(counts(identityId)).isEqualTo(beforeEvaluation);

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-maker",
                "compensation.eligibility-rule.approve"))
            .header(
                "Idempotency-Key",
                "approve-own-eligibility-rule-0001"))
        .andExpect(status().isConflict());

    MvcResult approved = mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-checker",
                "compensation.eligibility-rule.approve"))
            .header(
                "Idempotency-Key",
                "approve-eligibility-rule-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andReturn();

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-checker",
                "compensation.eligibility-rule.approve"))
            .header(
                "Idempotency-Key",
                "approve-eligibility-rule-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

    mvc.perform(get(
                "/api/v1/eligibility-rules/{identityId}",
                identityId)
            .param("asOf", "2028-06-30")
            .with(token(
                TENANT_A,
                "rule-reader",
                "compensation.eligibility-rule.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code")
            .value("INDIA_STANDARD_ELIGIBILITY"))
        .andExpect(jsonPath("$.criteria[0].factKey")
            .value("COUNTRY_CODE"));

    mvc.perform(get("/api/v1/eligibility-rules")
            .param("asOf", "2028-06-30")
            .with(token(
                TENANT_B,
                "rule-reader",
                "compensation.eligibility-rule.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    DatabaseCounts beforeApprovedEvaluation = counts(identityId);
    MvcResult firstEvaluation = evaluate(
        identityId,
        versionId,
        matchingFacts(false));
    MvcResult reorderedEvaluation = evaluate(
        identityId,
        versionId,
        matchingFacts(true));

    JsonNode evaluated = objectMapper.readTree(
        firstEvaluation.getResponse().getContentAsString());
    JsonNode reordered = objectMapper.readTree(
        reorderedEvaluation.getResponse().getContentAsString());
    assertThat(reordered.get("factsHash").asText())
        .isEqualTo(evaluated.get("factsHash").asText());
    assertThat(reordered.get("evaluationHash").asText())
        .isEqualTo(evaluated.get("evaluationHash").asText());
    assertThat(reordered.get("configurationHash").asText())
        .isEqualTo(draftResult.get("configurationHash").asText());
    assertThat(reordered.get("evaluationHash").asText())
        .isEqualTo(draftResult.get("evaluationHash").asText());
    assertThat(reordered.get("criteria").get(0)
        .get("criterionSequence").asInt()).isEqualTo(1);
    assertThat(counts(identityId))
        .isEqualTo(beforeApprovedEvaluation);

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/evaluation",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-evaluator",
                "compensation.eligibility-rule.evaluate"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(nonMatchingFacts()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matched").value(false))
        .andExpect(jsonPath("$.result")
            .value("REQUIRES_APPROVAL"));

    mvc.perform(get(
                "/api/v1/eligibility-rules/{identityId}/audit",
                identityId)
            .with(token(
                TENANT_A,
                "auditor",
                "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action")
            .value("VERSION_APPROVED"))
        .andExpect(jsonPath("$.length()").value(2));

    assertThat(outboxEventCount(identityId)).isEqualTo(2L);
    assertThat(outboxPayloadText(identityId))
        .contains("configurationHash")
        .doesNotContain(
            "expectedValue",
            "actualValue",
            "valueJson",
            "ANNUAL_COMPENSATION_AMOUNT\":");
    assertThat(objectMapper.readTree(
            approved.getResponse().getContentAsString())
        .get("versionNo").asLong()).isGreaterThan(0L);
  }

  @Test
  void createRejectsUnlistedMismatchedAndExecutableCriteria()
      throws Exception {
    String mismatched = rulePayload("MISMATCHED_TYPE")
        .replace(
            "\"factType\":\"NUMBER\"",
            "\"factType\":\"TEXT\"");
    mvc.perform(post("/api/v1/eligibility-rules")
            .with(token(
                TENANT_A,
                "rule-maker",
                "compensation.eligibility-rule.create"))
            .header(
                "Idempotency-Key",
                "reject-mismatched-criterion")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mismatched))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value("factType for SERVICE_MONTHS must be NUMBER"));

    String unlisted = rulePayload("UNLISTED_FACT")
        .replace(
            "\"factKey\":\"COUNTRY_CODE\"",
            "\"factKey\":\"JAVA_EXPRESSION\"");
    mvc.perform(post("/api/v1/eligibility-rules")
            .with(token(
                TENANT_A,
                "rule-maker",
                "compensation.eligibility-rule.create"))
            .header(
                "Idempotency-Key",
                "reject-unlisted-criterion")
            .contentType(MediaType.APPLICATION_JSON)
            .content(unlisted))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(
                "factKey contains an unsupported value: "
                    + "JAVA_EXPRESSION"));

    String executable = rulePayload("EXECUTABLE_VALUE")
        .replace(
            "\"value\":\"IN\"",
            "\"value\":{\"spel\":\"T(java.lang.Runtime)\"}");
    mvc.perform(post("/api/v1/eligibility-rules")
            .with(token(
                TENANT_A,
                "rule-maker",
                "compensation.eligibility-rule.create"))
            .header(
                "Idempotency-Key",
                "reject-executable-criterion")
            .contentType(MediaType.APPLICATION_JSON)
            .content(executable))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(
                "criterion value must be a non-blank TEXT value"));

    assertThat(ruleCount()).isZero();

    JsonNode valid = createRule(
        "VERSION_CONTRACT",
        "create-version-contract-rule");
    String versionPayload = objectMapper.readTree(
        rulePayload("VERSION_CONTRACT")).get("version").toString();
    String identityMutation = versionPayload.replaceFirst(
        "\\{",
        "{\"code\":\"SHOULD_NOT_BE_ACCEPTED\",");
    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions",
                valid.get("identityId").asText())
            .with(token(
                TENANT_A,
                "rule-maker-2",
                "compensation.eligibility-rule.version.create"))
            .header(
                "Idempotency-Key",
                "eligibility-identity-mutation-rejected")
            .contentType(MediaType.APPLICATION_JSON)
            .content(identityMutation))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value("Unknown request field: code"));
  }

  @Test
  void evaluationFactContractAndControlledRetirementAreEnforced()
      throws Exception {
    JsonNode created = createRule(
        "RETIREMENT_ELIGIBILITY",
        "create-retirement-rule");
    JsonNode approved = approveRule(
        created,
        "approve-retirement-rule");

    String identityId = approved.get("identityId").asText();
    String versionId = approved.get("versionId").asText();

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/evaluation",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-evaluator",
                "compensation.eligibility-rule.evaluate"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "facts":{
                    "COUNTRY_CODE":"IN",
                    "SERVICE_MONTHS":18,
                    "EFFECTIVE_DATE":"2028-06-30"
                  }
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(org.hamcrest.Matchers.containsString(
                "contain exactly the rule fact keys")));

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/evaluation",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-evaluator",
                "compensation.eligibility-rule.evaluate"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(matchingFacts(false)
                .replace(
                    "\"SERVICE_MONTHS\":18",
                    "\"SERVICE_MONTHS\":\"18\"")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(
                "Supplied fact SERVICE_MONTHS must be a NUMBER value"));

    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/evaluation",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-evaluator",
                "compensation.eligibility-rule.evaluate"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(matchingFacts(false)
                .replace(
                    "\"COUNTRY_CODE\":\"IN\"",
                    "\"COUNTRY_CODE\":\"IN\",\"SCRIPT\":\"return true\"")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(
                "factKey contains an unsupported value: SCRIPT"));

    MvcResult ended = mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/end-date",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-checker",
                "compensation.eligibility-rule.version.end-date"))
            .header(
                "Idempotency-Key",
                "end-date-eligibility-rule")
            .header(
                "If-Match",
                approved.get("versionNo").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"effectiveTo\":\"2028-01-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveTo")
            .value("2028-01-01"))
        .andReturn();

    JsonNode endedJson = objectMapper.readTree(
        ended.getResponse().getContentAsString());
    mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/retirement",
                identityId)
            .with(token(
                TENANT_A,
                "rule-checker",
                "compensation.eligibility-rule.retire"))
            .header(
                "Idempotency-Key",
                "retire-eligibility-rule")
            .header(
                "If-Match",
                endedJson.get("identityVersionNo").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "effectiveDate":"2028-01-01",
                  "reason":"Synthetic eligibility rule retirement"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus")
            .value("RETIRED"));
  }

  private JsonNode createRule(String code, String key)
      throws Exception {
    MvcResult created = mvc.perform(post("/api/v1/eligibility-rules")
            .with(token(
                TENANT_A,
                "rule-maker",
                "compensation.eligibility-rule.create"))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rulePayload(code)))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(
        created.getResponse().getContentAsString());
  }

  private JsonNode approveRule(JsonNode created, String key)
      throws Exception {
    MvcResult approved = mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/approval",
                created.get("identityId").asText(),
                created.get("versionId").asText())
            .with(token(
                TENANT_A,
                "rule-checker",
                "compensation.eligibility-rule.approve"))
            .header("Idempotency-Key", key))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(
        approved.getResponse().getContentAsString());
  }

  private MvcResult evaluate(
      String identityId,
      String versionId,
      String facts) throws Exception {
    return mvc.perform(post(
                "/api/v1/eligibility-rules/{identityId}/versions/"
                    + "{versionId}/evaluation",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "rule-evaluator",
                "compensation.eligibility-rule.evaluate"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(facts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteria.length()").value(4))
        .andReturn();
  }

  private String rulePayload(String code) {
    return """
        {
          "code":"%s",
          "version":{
            "name":"Synthetic India eligibility",
            "resultWhenMatched":"ELIGIBLE",
            "resultWhenNotMatched":"REQUIRES_APPROVAL",
            "effectiveFrom":"2027-01-01",
            "effectiveTo":"2030-01-01",
            "criteria":[
              {
                "criterionSequence":1,
                "factKey":"COUNTRY_CODE",
                "factType":"TEXT",
                "comparisonOperator":"EQ",
                "value":"IN"
              },
              {
                "criterionSequence":2,
                "factKey":"SERVICE_MONTHS",
                "factType":"NUMBER",
                "comparisonOperator":"GTE",
                "value":12
              },
              {
                "criterionSequence":3,
                "factKey":"EFFECTIVE_DATE",
                "factType":"DATE",
                "comparisonOperator":"LTE",
                "value":"2029-12-31"
              },
              {
                "criterionSequence":4,
                "factKey":"LEGAL_ENTITY_VERSION_ID",
                "factType":"UUID",
                "comparisonOperator":"IN",
                "value":["%s"]
              }
            ]
          }
        }
        """.formatted(code, LEGAL_ENTITY_VERSION_ID);
  }

  private String matchingFacts(boolean reversed) {
    if (reversed) {
      return """
          {
            "facts":{
              "LEGAL_ENTITY_VERSION_ID":"%s",
              "EFFECTIVE_DATE":"2028-06-30",
              "SERVICE_MONTHS":18,
              "COUNTRY_CODE":"IN"
            }
          }
          """.formatted(LEGAL_ENTITY_VERSION_ID);
    }
    return """
        {
          "facts":{
            "COUNTRY_CODE":"IN",
            "SERVICE_MONTHS":18,
            "EFFECTIVE_DATE":"2028-06-30",
            "LEGAL_ENTITY_VERSION_ID":"%s"
          }
        }
        """.formatted(LEGAL_ENTITY_VERSION_ID);
  }

  private String nonMatchingFacts() {
    return """
        {
          "facts":{
            "COUNTRY_CODE":"IN",
            "SERVICE_MONTHS":6,
            "EFFECTIVE_DATE":"2028-06-30",
            "LEGAL_ENTITY_VERSION_ID":"%s"
          }
        }
        """.formatted(LEGAL_ENTITY_VERSION_ID);
  }

  private DatabaseCounts counts(String identityId) throws Exception {
    UUID id = UUID.fromString(identityId);
    return new DatabaseCounts(
        count(
            "select count(*) from compensation.eligibility_rule "
                + "where tenant_id=? and id=?",
            id),
        count(
            "select count(*) from compensation.eligibility_rule_version "
                + "where tenant_id=? and eligibility_rule_id=?",
            id),
        count(
            "select count(*) from compensation.eligibility_rule_criterion "
                + "where tenant_id=? and eligibility_rule_id=?",
            id),
        outboxEventCount(identityId));
  }

  private long ruleCount() throws Exception {
    try (Connection connection = admin();
        PreparedStatement query = connection.prepareStatement(
            "select count(*) from compensation.eligibility_rule")) {
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long count(String sql, UUID identityId)
      throws Exception {
    try (Connection connection = admin();
        PreparedStatement query = connection.prepareStatement(sql)) {
      query.setObject(1, UUID.fromString(TENANT_A));
      query.setObject(2, identityId);
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private long outboxEventCount(String identityId)
      throws Exception {
    try (Connection connection = admin();
        PreparedStatement query = connection.prepareStatement(
            "select count(*) from integration.outbox_event "
                + "where tenant_id=? and aggregate_id=?")) {
      query.setObject(1, UUID.fromString(TENANT_A));
      query.setObject(2, UUID.fromString(identityId));
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private String outboxPayloadText(String identityId)
      throws Exception {
    try (Connection connection = admin();
        PreparedStatement query = connection.prepareStatement(
            "select string_agg(payload::text,' ') "
                + "from integration.outbox_event "
                + "where tenant_id=? and aggregate_id=?")) {
      query.setObject(1, UUID.fromString(TENANT_A));
      query.setObject(2, UUID.fromString(identityId));
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getString(1);
      }
    }
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
          String tenant,
          String subject,
          String permission) {
    return jwt().jwt(value -> value
            .issuer("https://issuer.example.test")
            .subject(subject)
            .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "postgres",
        "postgres");
  }

  private record DatabaseCounts(
      long identities,
      long versions,
      long criteria,
      long outboxEvents) {}
}
