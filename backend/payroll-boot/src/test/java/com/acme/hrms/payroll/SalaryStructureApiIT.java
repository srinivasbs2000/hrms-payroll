package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request
    .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

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
@org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc
class SalaryStructureApiIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";

  private static final String BASIC_ID =
      "21000000-0000-0000-0000-000000000001";
  private static final String BASIC_VERSION_ID =
      "21100000-0000-0000-0000-000000000001";
  private static final String HRA_ID =
      "21000000-0000-0000-0000-000000000002";
  private static final String HRA_VERSION_ID =
      "21100000-0000-0000-0000-000000000002";
  private static final String SPECIAL_ID =
      "21000000-0000-0000-0000-000000000003";
  private static final String SPECIAL_VERSION_ID =
      "21100000-0000-0000-0000-000000000003";

  private static final String CTC_POLICY_ID =
      "23000000-0000-0000-0000-000000000001";
  private static final String CTC_POLICY_VERSION_ID =
      "23100000-0000-0000-0000-000000000001";
  private static final String ELIGIBILITY_RULE_ID =
      "24000000-0000-0000-0000-000000000001";
  private static final String ELIGIBILITY_RULE_VERSION_ID =
      "24100000-0000-0000-0000-000000000001";

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
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER "
              + "NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
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
  void seedDependencies() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");

      seedComponent(
          statement,
          BASIC_ID,
          BASIC_VERSION_ID,
          "BASIC",
          "Basic Pay");
      seedComponent(
          statement,
          HRA_ID,
          HRA_VERSION_ID,
          "HRA",
          "House Rent Allowance");
      seedComponent(
          statement,
          SPECIAL_ID,
          SPECIAL_VERSION_ID,
          "SPECIAL_ALLOWANCE",
          "Special Allowance");
      seedCtcPolicy(statement);
      seedEligibilityRule(statement);
    }
  }

  @Test
  void passingSimulationIsIdempotentBindableAndPublishable()
      throws Exception {
    JsonNode structure = createStructure(
        "STANDARD_SALARY",
        "1000000.0000",
        "create-standard-salary-0001");
    String identityId = structure.get("identityId").asText();
    String versionId = structure.get("versionId").asText();

    String simulation = simulationPayload(false);
    MvcResult firstResult = mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/simulations",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-analyst",
                "compensation.structure.simulate"))
            .header(
                "Idempotency-Key",
                "simulate-standard-salary-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(simulation))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validationStatus").value("PASS"))
        .andExpect(jsonPath("$.blockingErrorCount").value(0))
        .andExpect(jsonPath("$.lines[0].annualAmount")
            .value(600000.0))
        .andExpect(jsonPath("$.lines[1].annualAmount")
            .value(240000.0))
        .andExpect(jsonPath("$.lines[2].annualAmount")
            .value(160000.0))
        .andExpect(jsonPath("$.summary.totalAnnualAmount")
            .value(1000000.0))
        .andExpect(jsonPath("$.summary.reconciliationDelta")
            .value(0.0))
        .andExpect(jsonPath("$.summary.eligibilityResult")
            .value("ELIGIBLE"))
        .andExpect(jsonPath("$.ctcPolicyVersionId")
            .value(CTC_POLICY_VERSION_ID))
        .andExpect(jsonPath("$.eligibilityRuleVersionId")
            .value(ELIGIBILITY_RULE_VERSION_ID))
        .andExpect(jsonPath("$.warningCount").value(1))
        .andExpect(jsonPath("$.summary.costViews.OFFERED")
            .value(600000.0))
        .andExpect(jsonPath("$.summary.costViews.TARGET")
            .value(1000000.0))
        .andExpect(jsonPath("$.summary.costViews.ACCRUED")
            .value(160000.0))
        .andExpect(jsonPath("$.summary.costViews.ACTUAL_EMPLOYER_COST")
            .value(160000.0))
        .andExpect(jsonPath("$.summary.statutoryCompatibilityStatus")
            .value("STRUCTURAL_ONLY"))
        .andExpect(jsonPath("$.summary.warnings[0].code")
            .value("MINIMUM_WAGE_RULESET_NOT_BOUND"))
        .andExpect(jsonPath("$.disclaimer").value(
            "DESIGN-TIME SALARY-STRUCTURE SIMULATION — "
                + "NOT AN EMPLOYEE PAYROLL RESULT"))
        .andReturn();

    JsonNode first = objectMapper.readTree(
        firstResult.getResponse().getContentAsString());
    String validationId = first.get("validationId").asText();

    MvcResult replayResult = mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/simulations",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-analyst",
                "compensation.structure.simulate"))
            .header(
                "Idempotency-Key",
                "simulate-standard-salary-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(simulation))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode replay = objectMapper.readTree(
        replayResult.getResponse().getContentAsString());
    assertThat(replay.get("validationId").asText())
        .isEqualTo(validationId);
    assertThat(validationCount(versionId)).isEqualTo(1L);

    mvc.perform(get(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/validations",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-reader",
                "compensation.structure.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].validationId")
            .value(validationId));

    MvcResult boundResult = mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/validations/{validationId}/binding",
                identityId,
                versionId,
                validationId)
            .with(token(
                TENANT_A,
                "structure-maker",
                "compensation.structure.validation.bind"))
            .header(
                "Idempotency-Key",
                "bind-standard-salary-0001")
            .header("If-Match", structure.get("versionNo").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationFingerprint")
            .value(first.get("resultHash").asText()))
        .andExpect(jsonPath("$.versionNo").value(1))
        .andReturn();

    JsonNode bound = objectMapper.readTree(
        boundResult.getResponse().getContentAsString());
    mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-checker",
                "compensation.structure.approve"))
            .header(
                "Idempotency-Key",
                "approve-standard-salary-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.validationFingerprint")
            .value(first.get("resultHash").asText()))
        .andExpect(jsonPath("$.versionNo")
            .value(bound.get("versionNo").asLong() + 1));

    mvc.perform(get(
                "/api/v1/salary-structures/{identityId}/audit",
                identityId)
            .with(token(TENANT_A, "auditor", "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action").value("SIMULATED"))
        .andExpect(jsonPath("$[2].action")
            .value("VALIDATION_BOUND"))
        .andExpect(jsonPath("$[3].action")
            .value("VERSION_APPROVED"));

    mvc.perform(get("/api/v1/salary-structures")
            .param("asOf", "2027-07-01")
            .with(token(
                TENANT_B,
                "structure-reader",
                "compensation.structure.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void negativeResidualProducesFailingUnbindableEvidence()
      throws Exception {
    JsonNode structure = createStructure(
        "OVER_ALLOCATED_SALARY",
        "700000.0000",
        "create-over-allocated-salary-0001");
    String identityId = structure.get("identityId").asText();
    String versionId = structure.get("versionId").asText();

    MvcResult failedResult = mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/simulations",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-analyst",
                "compensation.structure.simulate"))
            .header(
                "Idempotency-Key",
                "simulate-over-allocated-salary-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(simulationPayload(false)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validationStatus").value("FAIL"))
        .andExpect(jsonPath("$.blockingErrorCount").value(2))
        .andExpect(jsonPath("$.summary.blockingErrors[0].code")
            .value("NEGATIVE_RESIDUAL"))
        .andReturn();

    JsonNode failed = objectMapper.readTree(
        failedResult.getResponse().getContentAsString());
    mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/validations/{validationId}/binding",
                identityId,
                versionId,
                failed.get("validationId").asText())
            .with(token(
                TENANT_A,
                "structure-maker",
                "compensation.structure.validation.bind"))
            .header(
                "Idempotency-Key",
                "bind-failing-salary-validation-0001")
            .header("If-Match", structure.get("versionNo").asText()))
        .andExpect(status().isConflict());
  }

  @Test
  void unknownEligibilityFactIsRejectedWithoutPersistingEvidence()
      throws Exception {
    JsonNode structure = createStructure(
        "FACT_VALIDATION_SALARY",
        "1000000.0000",
        "create-fact-validation-salary-0001");
    String identityId = structure.get("identityId").asText();
    String versionId = structure.get("versionId").asText();

    mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/simulations",
                identityId,
                versionId)
            .with(token(
                TENANT_A,
                "structure-analyst",
                "compensation.structure.simulate"))
            .header(
                "Idempotency-Key",
                "simulate-extra-fact-salary-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(simulationPayload(true)))
        .andExpect(status().isUnprocessableEntity());

    assertThat(validationCount(versionId)).isZero();
  }

  @Test
  void missingSimulationPermissionIsForbidden() throws Exception {
    JsonNode structure = createStructure(
        "PERMISSION_SALARY",
        "1000000.0000",
        "create-permission-salary-0001");

    mvc.perform(post(
                "/api/v1/salary-structures/{identityId}/versions/"
                    + "{versionId}/simulations",
                structure.get("identityId").asText(),
                structure.get("versionId").asText())
            .with(token(
                TENANT_A,
                "structure-reader",
                "compensation.structure.read"))
            .header(
                "Idempotency-Key",
                "simulate-without-permission-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(simulationPayload(false)))
        .andExpect(status().isForbidden());
  }

  private JsonNode createStructure(
      String code,
      String target,
      String idempotencyKey) throws Exception {
    MvcResult result = mvc.perform(post("/api/v1/salary-structures")
            .with(token(
                TENANT_A,
                "structure-maker",
                "compensation.structure.create"))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(structurePayload(code, target)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.structureSchemaVersion").value(1))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.configurationHash")
            .isNotEmpty())
        .andExpect(jsonPath("$.lines.length()").value(3))
        .andReturn();
    return objectMapper.readTree(
        result.getResponse().getContentAsString());
  }

  private String structurePayload(String code, String target) {
    return """
        {
          "code":"%s",
          "name":"%s",
          "currency":"INR",
          "structureType":"STANDARD",
          "payFrequency":"MONTHLY",
          "confidentialityLevel":"STANDARD",
          "ctcPolicyVersionId":"%s",
          "eligibilityRuleVersionId":"%s",
          "targetType":"ANNUAL_CTC",
          "targetAnnualAmount":%s,
          "toleranceAmount":0.0100,
          "residualComponentVersionId":"%s",
          "effectiveFrom":"2027-01-01",
          "effectiveTo":"2029-01-01",
          "lines":[
            {
              "componentVersionId":"%s",
              "sequenceNo":1,
              "lineType":"FIXED",
              "targetAmount":600000.0000,
              "mandatory":true,
              "overridePolicy":"PROHIBITED",
              "ctcDisplayOrder":1,
              "payslipDisplayOrder":1
            },
            {
              "componentVersionId":"%s",
              "sequenceNo":2,
              "lineType":"PERCENTAGE",
              "targetPercentage":40.000000,
              "percentageBaseCode":"BASIC",
              "mandatory":true,
              "overridePolicy":"CONTROLLED",
              "ctcDisplayOrder":2,
              "payslipDisplayOrder":2
            },
            {
              "componentVersionId":"%s",
              "sequenceNo":3,
              "lineType":"RESIDUAL",
              "minimumAmount":0.0000,
              "mandatory":true,
              "overridePolicy":"PROHIBITED",
              "ctcDisplayOrder":3,
              "payslipDisplayOrder":3
            }
          ]
        }
        """.formatted(
            code,
            code.replace('_', ' '),
            CTC_POLICY_VERSION_ID,
            ELIGIBILITY_RULE_VERSION_ID,
            target,
            SPECIAL_VERSION_ID,
            BASIC_VERSION_ID,
            HRA_VERSION_ID,
            SPECIAL_VERSION_ID);
  }

  private String simulationPayload(boolean extraFact) {
    if (extraFact) {
      return """
          {
            "effectiveDate":"2027-07-01",
            "eligibilityFacts":{
              "COUNTRY_CODE":"IN",
              "UNDECLARED_FACT":"SHOULD_FAIL"
            }
          }
          """;
    }
    return """
        {
          "effectiveDate":"2027-07-01",
          "eligibilityFacts":{"COUNTRY_CODE":"IN"}
        }
        """;
  }

  private void seedComponent(
      Statement statement,
      String identityId,
      String versionId,
      String code,
      String name) throws Exception {
    statement.execute(
        "INSERT INTO compensation.pay_component("
            + "id,tenant_id,code,name,component_type,lifecycle_status,"
            + "created_by,updated_by) VALUES ('"
            + identityId + "','" + TENANT_A + "','" + code + "','"
            + name + "','EARNING','ACTIVE','component-maker','component-maker')");
    statement.execute(
        "INSERT INTO compensation.pay_component_version("
            + "id,tenant_id,component_id,version_sequence,formula_type,"
            + "formula_expression,fixed_amount,rounding_scale,effective_from,"
            + "effective_to,approval_status,approved_at,approved_by,"
            + "created_by,updated_by) VALUES ('"
            + versionId + "','" + TENANT_A + "','" + identityId
            + "',1,'FIXED',NULL,1.0000,2,'2026-01-01','2030-01-01',"
            + "'APPROVED',clock_timestamp(),'component-checker',"
            + "'component-maker','component-checker')");
  }

  private void seedCtcPolicy(Statement statement) throws Exception {
    statement.execute(
        "INSERT INTO compensation.ctc_policy("
            + "id,tenant_id,code,lifecycle_status,created_by,updated_by) "
            + "VALUES ('" + CTC_POLICY_ID + "','" + TENANT_A
            + "','STANDARD_CTC','PENDING_APPROVAL','policy-maker','policy-maker')");
    statement.execute(
        "INSERT INTO compensation.ctc_policy_version("
            + "id,tenant_id,ctc_policy_id,version_sequence,name,currency,"
            + "annualisation_method,tolerance_amount,residual_component_id,"
            + "residual_component_version_id,effective_from,effective_to,"
            + "approval_status,created_by,updated_by) VALUES ('"
            + CTC_POLICY_VERSION_ID + "','" + TENANT_A + "','"
            + CTC_POLICY_ID + "',1,'Standard CTC','INR','EXACT_ANNUAL',"
            + "0.0100,'" + SPECIAL_ID + "','" + SPECIAL_VERSION_ID
            + "','2026-01-01','2030-01-01','DRAFT','policy-maker','policy-maker')");

    seedTreatment(
        statement,
        "23200000-0000-0000-0000-000000000001",
        1,
        "OFFERED",
        "ACTUAL_VALUE",
        BASIC_ID,
        BASIC_VERSION_ID,
        null);
    seedTreatment(
        statement,
        "23200000-0000-0000-0000-000000000002",
        2,
        "TARGET",
        "TARGET_VALUE",
        HRA_ID,
        HRA_VERSION_ID,
        "100.00000000");
    seedTreatment(
        statement,
        "23200000-0000-0000-0000-000000000003",
        3,
        "ACCRUED",
        "PROVISION",
        SPECIAL_ID,
        SPECIAL_VERSION_ID,
        null);
    seedTreatment(
        statement,
        "23200000-0000-0000-0000-000000000004",
        4,
        "ACTUAL_EMPLOYER_COST",
        "EMPLOYER_CONTRIBUTION",
        SPECIAL_ID,
        SPECIAL_VERSION_ID,
        null);

    statement.execute(
        "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
    statement.execute(
        "SELECT compensation.approve_ctc_policy_version('"
            + TENANT_A + "','" + CTC_POLICY_VERSION_ID
            + "','policy-checker',clock_timestamp())");
  }

  private void seedTreatment(
      Statement statement,
      String id,
      int sequence,
      String costView,
      String treatmentType,
      String componentId,
      String componentVersionId,
      String targetPercentage) throws Exception {
    String percentage = targetPercentage == null
        ? "NULL" : targetPercentage;
    statement.execute(
        "INSERT INTO compensation.ctc_policy_treatment("
            + "id,tenant_id,ctc_policy_id,ctc_policy_version_id,"
            + "component_id,component_version_id,treatment_sequence,"
            + "cost_view,treatment_type,fixed_value,target_percentage,"
            + "effective_from,effective_to,created_by,updated_by) VALUES ('"
            + id + "','" + TENANT_A + "','" + CTC_POLICY_ID + "','"
            + CTC_POLICY_VERSION_ID + "','" + componentId + "','"
            + componentVersionId + "'," + sequence + ",'" + costView
            + "','" + treatmentType + "',NULL," + percentage
            + ",'2026-01-01','2030-01-01','policy-maker','policy-maker')");
  }

  private void seedEligibilityRule(Statement statement) throws Exception {
    statement.execute(
        "INSERT INTO compensation.eligibility_rule("
            + "id,tenant_id,code,lifecycle_status,created_by,updated_by) "
            + "VALUES ('" + ELIGIBILITY_RULE_ID + "','" + TENANT_A
            + "','INDIA_ONLY','PENDING_APPROVAL','rule-maker','rule-maker')");
    statement.execute(
        "INSERT INTO compensation.eligibility_rule_version("
            + "id,tenant_id,eligibility_rule_id,version_sequence,name,"
            + "result_when_matched,result_when_not_matched,effective_from,"
            + "effective_to,approval_status,created_by,updated_by) VALUES ('"
            + ELIGIBILITY_RULE_VERSION_ID + "','" + TENANT_A + "','"
            + ELIGIBILITY_RULE_ID + "',1,'India Only','ELIGIBLE',"
            + "'NOT_ELIGIBLE','2026-01-01','2030-01-01','DRAFT',"
            + "'rule-maker','rule-maker')");
    statement.execute(
        "INSERT INTO compensation.eligibility_rule_criterion("
            + "id,tenant_id,eligibility_rule_id,eligibility_rule_version_id,"
            + "criterion_sequence,fact_key,fact_type,comparison_operator,"
            + "value_json,created_by,updated_by) VALUES ("
            + "'24200000-0000-0000-0000-000000000001','" + TENANT_A
            + "','" + ELIGIBILITY_RULE_ID + "','"
            + ELIGIBILITY_RULE_VERSION_ID + "',1,'COUNTRY_CODE','TEXT',"
            + "'EQ',cast('\"IN\"' as jsonb),'rule-maker','rule-maker')");
    statement.execute(
        "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
    statement.execute(
        "SELECT compensation.approve_eligibility_rule_version('"
            + TENANT_A + "','" + ELIGIBILITY_RULE_VERSION_ID
            + "','rule-checker',clock_timestamp())");
  }

  private long validationCount(String versionId) throws Exception {
    try (Connection connection = admin();
        PreparedStatement statement = connection.prepareStatement(
            "select count(*) from compensation.salary_structure_validation "
                + "where tenant_id=?::uuid and salary_structure_version_id=?::uuid")) {
      statement.setString(1, TENANT_A);
      statement.setString(2, versionId);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private static org.springframework.security.test.web.servlet
      .request.SecurityMockMvcRequestPostProcessors
      .JwtRequestPostProcessor token(
          String tenant,
          String subject,
          String permission) {
    return jwt().jwt(jwt -> jwt
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
}
