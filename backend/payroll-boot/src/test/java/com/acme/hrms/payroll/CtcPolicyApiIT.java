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
class CtcPolicyApiIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";

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
  void lifecycleIsIdempotentMakerCheckerAuditedAndTenantIsolated()
      throws Exception {
    ComponentIds component = createAndApproveComponent();
    String request = policyPayload(
        "INDIA_STANDARD_CTC",
        component,
        true);

    MvcResult created = mvc.perform(
            post("/api/v1/ctc-policies")
                .with(token(
                    TENANT_A,
                    "policy-maker",
                    "compensation.ctc-policy.create"))
                .header(
                    "Idempotency-Key",
                    "create-ctc-policy-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.lifecycleStatus")
            .value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.treatments.length()").value(4))
        .andReturn();

    JsonNode first = objectMapper.readTree(
        created.getResponse().getContentAsString());
    MvcResult replay = mvc.perform(
            post("/api/v1/ctc-policies")
                .with(token(
                    TENANT_A,
                    "policy-maker",
                    "compensation.ctc-policy.create"))
                .header(
                    "Idempotency-Key",
                    "create-ctc-policy-0001")
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

    mvc.perform(post(
                "/api/v1/ctc-policies/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "policy-maker",
                "compensation.ctc-policy.approve"))
            .header(
                "Idempotency-Key",
                "approve-own-ctc-policy-0001"))
        .andExpect(status().isConflict());

    MvcResult approved = mvc.perform(post(
                "/api/v1/ctc-policies/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "policy-checker",
                "compensation.ctc-policy.approve"))
            .header(
                "Idempotency-Key",
                "approve-ctc-policy-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andReturn();

    JsonNode approvedJson = objectMapper.readTree(
        approved.getResponse().getContentAsString());

    mvc.perform(get(
                "/api/v1/ctc-policies/{identityId}",
                identityId)
            .param("asOf", "2027-07-01")
            .with(token(
                TENANT_A,
                "policy-reader",
                "compensation.ctc-policy.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code")
            .value("INDIA_STANDARD_CTC"))
        .andExpect(jsonPath("$.treatments[0].costView")
            .value("OFFERED"));

    mvc.perform(get("/api/v1/ctc-policies")
            .param("asOf", "2027-07-01")
            .with(token(
                TENANT_B,
                "policy-reader",
                "compensation.ctc-policy.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    MvcResult ended = mvc.perform(post(
                "/api/v1/ctc-policies/{identityId}/versions/"
                    + "{versionId}/end-date",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "policy-checker",
                "compensation.ctc-policy.version.end-date"))
            .header(
                "Idempotency-Key",
                "end-date-ctc-policy-0001")
            .header(
                "If-Match",
                approvedJson.get("versionNo").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"effectiveTo\":\"2028-01-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveTo")
            .value("2028-01-01"))
        .andExpect(jsonPath("$.treatments[0].effectiveTo")
            .value("2028-01-01"))
        .andReturn();

    JsonNode endedJson = objectMapper.readTree(
        ended.getResponse().getContentAsString());
    mvc.perform(post(
                "/api/v1/ctc-policies/{identityId}/retirement",
                identityId)
            .with(token(
                TENANT_A,
                "policy-checker",
                "compensation.ctc-policy.retire"))
            .header(
                "Idempotency-Key",
                "retire-ctc-policy-0001")
            .header(
                "If-Match",
                endedJson.get("identityVersionNo").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "effectiveDate":"2028-01-01",
                  "reason":"Synthetic policy retirement"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus")
            .value("RETIRED"));

    mvc.perform(get(
                "/api/v1/ctc-policies/{identityId}/audit",
                identityId)
            .with(token(
                TENANT_A,
                "auditor",
                "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action")
            .value("VERSION_APPROVED"))
        .andExpect(jsonPath("$[2].action")
            .value("VERSION_END_DATED"))
        .andExpect(jsonPath("$[3].action").value("RETIRED"));

    assertThat(outboxEventCount(identityId)).isEqualTo(4L);
    assertThat(outboxPayloadText(identityId))
        .doesNotContain(
            "fixedValue",
            "targetPercentage",
            "toleranceAmount");
  }

  @Test
  void versionEndpointRejectsIdentityMutationFields()
      throws Exception {
    ComponentIds component = createAndApproveComponent();
    JsonNode created = createPolicy(
        "VERSION_FIELD_TEST",
        component,
        "create-policy-for-version-test");

    String payload = policyVersionPayload(component, true)
        .replaceFirst(
            "\\{",
            "{\"code\":\"SHOULD_NOT_BE_ACCEPTED\",");

    mvc.perform(post(
                "/api/v1/ctc-policies/{identityId}/versions",
                created.get("identityId").asText())
            .with(token(
                TENANT_A,
                "policy-maker-2",
                "compensation.ctc-policy.version.create"))
            .header(
                "Idempotency-Key",
                "ctc-policy-identity-mutation-rejected")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value("Unknown request field: code"));
  }

  @Test
  void createRejectsIncompleteCostViewSet() throws Exception {
    ComponentIds component = createAndApproveComponent();
    String request = policyPayload(
        "INCOMPLETE_CTC",
        component,
        false);

    mvc.perform(post("/api/v1/ctc-policies")
            .with(token(
                TENANT_A,
                "policy-maker",
                "compensation.ctc-policy.create"))
            .header(
                "Idempotency-Key",
                "reject-incomplete-ctc-policy")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value(
                "Each CTC policy version must contain all four cost views"));
  }

  private JsonNode createPolicy(
      String code,
      ComponentIds component,
      String key) throws Exception {
    MvcResult result = mvc.perform(post("/api/v1/ctc-policies")
            .with(token(
                TENANT_A,
                "policy-maker",
                "compensation.ctc-policy.create"))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(policyPayload(code, component, true)))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(
        result.getResponse().getContentAsString());
  }

  private ComponentIds createAndApproveComponent()
      throws Exception {
    String payload = """
        {
          "code":"CTC_RESIDUAL",
          "name":"Synthetic CTC Residual",
          "componentType":"EARNING",
          "ownershipScope":"TENANT",
          "countryCode":"IN",
          "protectedFlag":false,
          "confidentialityLevel":"STANDARD",
          "version":{
            "formulaType":"FIXED",
            "fixedAmount":0.0000,
            "roundingScale":2,
            "componentCategory":"CASH_EARNING",
            "componentSubcategory":"CTC_RESIDUAL",
            "cashImpact":"INCREASE",
            "payeeType":"EMPLOYEE",
            "paymentChannel":"PAYROLL_BANK",
            "settlementTiming":"CURRENT_PERIOD",
            "payslipVisibility":"SHOW",
            "zeroValueVisibility":"SUPPRESS",
            "negativeValuePolicy":"PROHIBIT",
            "frequency":"MONTHLY",
            "valueNature":"FIXED",
            "amountRepresentation":"MONTHLY_AMOUNT",
            "taxTreatment":"DELEGATED",
            "payrollTiming":"REGULAR",
            "effectiveFrom":"2026-01-01",
            "effectiveTo":"2030-01-01"
          }
        }
        """;

    MvcResult created = mvc.perform(post("/api/v1/pay-components")
            .with(token(
                TENANT_A,
                "component-maker",
                "compensation.component.create"))
            .header(
                "Idempotency-Key",
                "create-ctc-residual-component")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode component = objectMapper.readTree(
        created.getResponse().getContentAsString());

    mvc.perform(post(
                "/api/v1/pay-components/{identityId}/versions/"
                    + "{versionId}/approval",
                component.get("identityId").asText(),
                component.get("versionId").asText())
            .with(token(
                TENANT_A,
                "component-checker",
                "compensation.component.approve"))
            .header(
                "Idempotency-Key",
                "approve-ctc-residual-component"))
        .andExpect(status().isOk());

    return new ComponentIds(
        component.get("identityId").asText(),
        component.get("versionId").asText());
  }

  private String policyPayload(
      String code,
      ComponentIds component,
      boolean complete) {
    return """
        {
          "code":"%s",
          "version":%s
        }
        """.formatted(
            code,
            policyVersionPayload(component, complete));
  }

  private String policyVersionPayload(
      ComponentIds component,
      boolean complete) {
    String treatments = complete
        ? """
            [
              %s,
              %s,
              %s,
              %s
            ]
            """.formatted(
                treatment(component, 1, "OFFERED"),
                treatment(component, 2, "TARGET"),
                treatment(component, 3, "ACCRUED"),
                treatment(component, 4, "ACTUAL_EMPLOYER_COST"))
        : "[" + treatment(component, 1, "OFFERED") + "]";

    return """
        {
          "name":"Synthetic India CTC Policy",
          "currency":"INR",
          "annualisationMethod":"EXACT_ANNUAL",
          "toleranceAmount":0.0100,
          "residualComponentId":"%s",
          "residualComponentVersionId":"%s",
          "effectiveFrom":"2027-01-01",
          "effectiveTo":"2029-01-01",
          "treatments":%s
        }
        """.formatted(
            component.identityId(),
            component.versionId(),
            treatments);
  }

  private String treatment(
      ComponentIds component,
      int sequence,
      String costView) {
    return """
        {
          "componentId":"%s",
          "componentVersionId":"%s",
          "treatmentSequence":%d,
          "costView":"%s",
          "treatmentType":"ACTUAL_VALUE"
        }
        """.formatted(
            component.identityId(),
            component.versionId(),
            sequence,
            costView);
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

  private long outboxEventCount(String identityId) throws Exception {
    try (Connection connection = admin();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from integration.outbox_event "
                + "where tenant_id=?::uuid and aggregate_type='CTC_POLICY' "
                + "and aggregate_id=?::uuid")) {
      statement.setString(1, TENANT_A);
      statement.setString(2, identityId);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private String outboxPayloadText(String identityId) throws Exception {
    try (Connection connection = admin();
        PreparedStatement statement = connection.prepareStatement(
            "select coalesce(string_agg(payload::text,'|'),'') "
                + "from integration.outbox_event "
                + "where tenant_id=?::uuid and aggregate_type='CTC_POLICY' "
                + "and aggregate_id=?::uuid")) {
      statement.setString(1, TENANT_A);
      statement.setString(2, identityId);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getString(1);
      }
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "postgres",
        "postgres");
  }

  private record ComponentIds(
      String identityId,
      String versionId) {}
}
