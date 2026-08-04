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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class CompensationCatalogueCalculationCompatibilityIT {
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
  private static final String TENANT =
      "00000000-0000-0000-0000-00000000000a";

  private static final String LEGAL_ID =
      "a1000000-0000-0000-0000-000000000001";
  private static final String LEGAL_VERSION_ID =
      "a1100000-0000-0000-0000-000000000001";
  private static final String PSU_ID =
      "a2000000-0000-0000-0000-000000000001";
  private static final String PSU_VERSION_ID =
      "a2100000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_ID =
      "a3000000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_VERSION_ID =
      "a3100000-0000-0000-0000-000000000001";
  private static final String CALENDAR_ID =
      "a4000000-0000-0000-0000-000000000001";
  private static final String PERIOD_ID =
      "a4100000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_ID =
      "a5000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_VERSION_ID =
      "a5100000-0000-0000-0000-000000000001";

  private static final String BASIC_ID =
      "a6000000-0000-0000-0000-000000000001";
  private static final String BASIC_VERSION_ID =
      "a6100000-0000-0000-0000-000000000001";
  private static final String HRA_ID =
      "a6000000-0000-0000-0000-000000000002";
  private static final String HRA_VERSION_ID =
      "a6100000-0000-0000-0000-000000000002";
  private static final String SPECIAL_ID =
      "a6000000-0000-0000-0000-000000000003";
  private static final String SPECIAL_VERSION_ID =
      "a6100000-0000-0000-0000-000000000003";

  private static final String STRUCTURE_ID =
      "a7000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_VERSION_ID =
      "a7100000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_ID =
      "a8000000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_VERSION_ID =
      "a8100000-0000-0000-0000-000000000001";
  private static final String PROFILE_ID =
      "a8200000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_ID =
      "a9000000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_VERSION_ID =
      "a9100000-0000-0000-0000-000000000001";
  private static final String GROUP_ASSIGNMENT_ID =
      "a9200000-0000-0000-0000-000000000001";
  private static final String SALARY_ASSIGNMENT_ID =
      "a9300000-0000-0000-0000-000000000001";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  private static String calculationFunctionBeforeV032;

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
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
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
        .target(MigrationVersion.fromVersion("31"))
        .load()
        .migrate();

    calculationFunctionBeforeV032 = calculationFunction();

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
  void seedConfigurationWithNamedBases() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) "
              + "VALUES ('" + TENANT + "','A','Compatibility Tenant','test','test')");
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT + "',false)");
      seedOrganisation(statement);
      seedPayrollConfiguration(statement);
      seedEmployee(statement);
      seedNamedBase(statement);
      statement.execute("RESET ROLE");
    }
  }

  @Test
  void v032PreservesTheStarterCalculatorAndExactExistingResults()
      throws Exception {
    String after = calculationFunction();
    assertThat(after).isEqualTo(calculationFunctionBeforeV032);
    assertThat(after)
        .doesNotContain("payroll_base")
        .doesNotContain("component_base_membership");

    MvcResult created = mvc.perform(
            post("/api/v1/payroll-cycles")
                .with(token("payroll-cycle.create"))
                .header("Idempotency-Key", "compat-cycle-create")
                .contentType("application/json")
                .content(
                    """
                    {
                      "payGroupVersionId":"%s",
                      "payPeriodId":"%s"
                    }
                    """.formatted(PAY_GROUP_VERSION_ID, PERIOD_ID)))
        .andExpect(status().isCreated())
        .andReturn();
    String cycleId = objectMapper.readTree(
        created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/population-resolution", cycleId)
                .with(token("payroll-cycle.population.resolve"))
                .header("Idempotency-Key", "compat-population-resolve")
                .header("If-Match", "0"))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/seal-inputs", cycleId)
                .with(token("payroll-cycle.inputs.seal"))
                .header("Idempotency-Key", "compat-input-seal")
                .header("If-Match", "1"))
        .andExpect(status().isOk());

    MvcResult calculation = mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/calculation", cycleId)
                .with(token("payroll-calculation.execute"))
                .header("Idempotency-Key", "compat-calculate")
                .header("If-Match", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grossTotal").value(85000.0))
        .andExpect(jsonPath("$.deductionTotal").value(0.0))
        .andExpect(jsonPath("$.netTotal").value(85000.0))
        .andExpect(jsonPath("$.resultSetHash").isString())
        .andReturn();

    JsonNode calculated = objectMapper.readTree(
        calculation.getResponse().getContentAsString());
    String resultSetHash = calculated.get("resultSetHash").asText();
    assertThat(resultSetHash).matches("[0-9a-f]{64}");

    MvcResult resultList = mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/results", cycleId)
                .with(token("payroll-result.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].grossAmount").value(85000.0))
        .andExpect(jsonPath("$[0].netAmount").value(85000.0))
        .andReturn();
    String resultId = objectMapper.readTree(
        resultList.getResponse().getContentAsString()).get(0).get("id").asText();

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/results/{resultId}",
                cycleId, resultId)
                .with(token("payroll-result.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.length()").value(3))
        .andExpect(jsonPath("$.components[0].componentCode").value("BASIC"))
        .andExpect(jsonPath("$.components[0].calculatedAmount").value(50000.0))
        .andExpect(jsonPath("$.components[1].componentCode").value("HRA"))
        .andExpect(jsonPath("$.components[1].calculatedAmount").value(20000.0))
        .andExpect(jsonPath("$.components[2].componentCode")
            .value("SPECIAL_ALLOWANCE"))
        .andExpect(jsonPath("$.components[2].calculatedAmount").value(15000.0))
        .andExpect(jsonPath("$.resultHash").isString());

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/results/{resultId}/trace",
                cycleId, resultId)
                .with(token("payroll-result.trace.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].stepType").value("FIXED_COMPONENT"))
        .andExpect(jsonPath("$[1].stepType").value("FIXED_COMPONENT"))
        .andExpect(jsonPath("$[2].stepType").value("FIXED_COMPONENT"))
        .andExpect(jsonPath("$[0].traceHash").isString());
  }

  private static void seedOrganisation(Statement statement) throws Exception {
    statement.execute(
        "INSERT INTO organisation.legal_entity("
            + "id,tenant_id,code,status,created_by,updated_by) VALUES ('"
            + LEGAL_ID + "','" + TENANT + "','ACME_IN','ACTIVE','test','test')");
    statement.execute(
        """
        INSERT INTO organisation.legal_entity_version(
          id,tenant_id,legal_entity_id,version_sequence,name,country_code,
          currency,effective_from,effective_to,approval_status,approved_at,
          approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s',1,'Acme India','IN','INR',
          '2026-01-01','2027-01-01','APPROVED',clock_timestamp(),
          'checker','maker','maker')
        """.formatted(LEGAL_VERSION_ID, TENANT, LEGAL_ID));
    statement.execute(
        "INSERT INTO organisation.payroll_statutory_unit("
            + "id,tenant_id,code,status,created_by,updated_by) VALUES ('"
            + PSU_ID + "','" + TENANT + "','ACME_PSU','ACTIVE','test','test')");
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit_version(
          id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,
          version_sequence,name,effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s','%s',1,'Acme PSU','2026-01-01',
          '2027-01-01','APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(PSU_VERSION_ID, TENANT, PSU_ID, LEGAL_VERSION_ID));
    statement.execute(
        "INSERT INTO organisation.establishment("
            + "id,tenant_id,code,status,created_by,updated_by) VALUES ('"
            + ESTABLISHMENT_ID + "','" + TENANT
            + "','BLR','ACTIVE','test','test')");
    statement.execute(
        """
        INSERT INTO organisation.establishment_version(
          id,tenant_id,establishment_id,payroll_statutory_unit_version_id,
          version_sequence,name,state_code,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s','%s',1,'Bengaluru','KA','2026-01-01',
          '2027-01-01','APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(
            ESTABLISHMENT_VERSION_ID, TENANT, ESTABLISHMENT_ID, PSU_VERSION_ID));
  }

  private static void seedPayrollConfiguration(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.payroll_calendar(
          id,tenant_id,code,name,frequency,timezone,created_by,updated_by
        ) VALUES ('%s','%s','MONTHLY_IN','Monthly India','MONTHLY',
          'Asia/Kolkata','test','test')
        """.formatted(CALENDAR_ID, TENANT));
    statement.execute(
        """
        INSERT INTO organisation.pay_period(
          id,tenant_id,calendar_id,period_code,period_start,period_end,
          payment_date,status,created_by,updated_by
        ) VALUES ('%s','%s','%s','2026-07','2026-07-01','2026-07-31',
          '2026-07-31','OPEN','test','test')
        """.formatted(PERIOD_ID, TENANT, CALENDAR_ID));
    statement.execute(
        "INSERT INTO organisation.pay_group("
            + "id,tenant_id,code,created_by,updated_by) VALUES ('"
            + PAY_GROUP_ID + "','" + TENANT + "','MONTHLY_IN','test','test')");
    statement.execute(
        """
        INSERT INTO organisation.pay_group_version(
          id,tenant_id,pay_group_id,payroll_statutory_unit_version_id,
          calendar_id,version_sequence,name,currency,proration_method,
          effective_from,effective_to,approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','%s','%s',1,'Monthly India','INR',
          'CALENDAR_DAYS','2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'checker','maker','maker')
        """.formatted(
            PAY_GROUP_VERSION_ID, TENANT, PAY_GROUP_ID, PSU_VERSION_ID, CALENDAR_ID));

    insertComponent(statement, BASIC_ID, BASIC_VERSION_ID, "BASIC", "Basic Pay", "50000.0000");
    insertComponent(statement, HRA_ID, HRA_VERSION_ID, "HRA", "House Rent Allowance", "20000.0000");
    insertComponent(
        statement,
        SPECIAL_ID,
        SPECIAL_VERSION_ID,
        "SPECIAL_ALLOWANCE",
        "Special Allowance",
        "15000.0000");

    statement.execute(
        "INSERT INTO compensation.salary_structure("
            + "id,tenant_id,code,created_by,updated_by) VALUES ('"
            + STRUCTURE_ID + "','" + TENANT + "','DEFAULT','test','test')");
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_version(
          id,tenant_id,salary_structure_id,version_sequence,name,currency,
          effective_from,effective_to,approval_status,created_by,updated_by
        ) VALUES ('%s','%s','%s',1,'Default Structure','INR',
          '2026-01-01','2027-01-01','DRAFT','structure-maker','structure-maker')
        """.formatted(STRUCTURE_VERSION_ID, TENANT, STRUCTURE_ID));

    insertStructureLine(statement, 1, BASIC_VERSION_ID, "50000.0000");
    insertStructureLine(statement, 2, HRA_VERSION_ID, "20000.0000");
    insertStructureLine(statement, 3, SPECIAL_VERSION_ID, "15000.0000");
    statement.execute(
        "SELECT compensation.approve_salary_structure_version('"
            + TENANT + "','" + STRUCTURE_VERSION_ID
            + "','structure-checker',clock_timestamp())");
  }

  private static void insertComponent(
      Statement statement,
      String identityId,
      String versionId,
      String code,
      String name,
      String amount) throws Exception {
    statement.execute(
        """
        INSERT INTO compensation.pay_component(
          id,tenant_id,code,name,component_type,lifecycle_status,
          ownership_scope,protected_flag,confidentiality_level,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','%s','EARNING','ACTIVE','TENANT',false,
          'STANDARD','component-maker','component-maker')
        """.formatted(identityId, TENANT, code, name));
    statement.execute(
        """
        INSERT INTO compensation.pay_component_version(
          id,tenant_id,component_id,version_sequence,formula_type,
          fixed_amount,rounding_scale,catalogue_schema_version,
          component_category,cash_impact,payee_type,payment_channel,
          settlement_timing,payslip_visibility,zero_value_visibility,
          negative_value_policy,frequency,value_nature,amount_representation,
          tax_treatment,payroll_timing,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s',1,'FIXED',%s,2,1,'CASH_EARNING',
          'INCREASE','EMPLOYEE','PAYROLL_BANK','CURRENT_PERIOD','SHOW',
          'SUPPRESS','PROHIBIT','MONTHLY','FIXED','MONTHLY_AMOUNT',
          'DELEGATED','REGULAR','2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'component-checker','component-maker','component-maker')
        """.formatted(versionId, TENANT, identityId, amount));
  }

  private static void insertStructureLine(
      Statement statement, int sequence, String componentVersionId, String amount)
      throws Exception {
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          tenant_id,salary_structure_version_id,component_version_id,
          sequence_no,target_amount,effective_from,effective_to,
          created_by,updated_by
        ) VALUES ('%s','%s','%s',%d,%s,'2026-01-01','2027-01-01',
          'test','test')
        """.formatted(
            TENANT, STRUCTURE_VERSION_ID, componentVersionId, sequence, amount));
  }

  private static void seedNamedBase(Statement statement) throws Exception {
    String baseId = "aa000000-0000-0000-0000-000000000001";
    String baseVersionId = "aa100000-0000-0000-0000-000000000001";
    statement.execute(
        """
        INSERT INTO compensation.payroll_base(
          id,tenant_id,code,name,lifecycle_status,created_by,updated_by
        ) VALUES ('%s','%s','COMPAT_GROSS','Compatibility Gross','ACTIVE',
          'base-maker','base-maker')
        """.formatted(baseId, TENANT));
    statement.execute(
        """
        INSERT INTO compensation.payroll_base_version(
          id,tenant_id,payroll_base_id,version_sequence,base_category,
          aggregation_method,effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s',1,'CALCULATION','SUM','2026-01-01',
          '2027-01-01','APPROVED',clock_timestamp(),'base-checker',
          'base-maker','base-maker')
        """.formatted(baseVersionId, TENANT, baseId));

    insertMembership(statement, baseId, baseVersionId, BASIC_ID, BASIC_VERSION_ID, 1, "50.00000000");
    insertMembership(statement, baseId, baseVersionId, HRA_ID, HRA_VERSION_ID, 1, "25.00000000");
    insertMembership(statement, baseId, baseVersionId, SPECIAL_ID, SPECIAL_VERSION_ID, 1, "10.00000000");
  }

  private static void insertMembership(
      Statement statement,
      String baseId,
      String baseVersionId,
      String componentId,
      String componentVersionId,
      int sequence,
      String percent) throws Exception {
    statement.execute(
        """
        INSERT INTO compensation.component_base_membership(
          tenant_id,payroll_base_id,payroll_base_version_id,component_id,
          component_version_id,membership_sequence,membership_type,
          inclusion_percent,effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s','%s','%s',%d,'INCLUDE',%s,
          '2026-01-01','2027-01-01','APPROVED',clock_timestamp(),
          'membership-checker','membership-maker','membership-maker')
        """.formatted(
            TENANT,
            baseId,
            baseVersionId,
            componentId,
            componentVersionId,
            sequence,
            percent));
  }

  private static void seedEmployee(Statement statement) throws Exception {
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,status,
          created_by,updated_by
        ) VALUES ('%s','%s','EMP-EXT-001','EMP-001','ACTIVE','test','test')
        """.formatted(RELATIONSHIP_ID, TENANT));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship_version(
          id,tenant_id,payroll_relationship_id,legal_entity_version_id,
          version_sequence,relationship_start,relationship_end,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES ('%s','%s','%s','%s',1,'2026-01-01','2027-01-01',
          'APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(
            RELATIONSHIP_VERSION_ID, TENANT, RELATIONSHIP_ID, LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.employee_payroll_profile(
          id,tenant_id,payroll_relationship_id,currency,payroll_status,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','INR','READY','test','test')
        """.formatted(PROFILE_ID, TENANT, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,assignment_number,status,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','ASN-001','ACTIVE','test','test')
        """.formatted(ASSIGNMENT_ID, TENANT, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,payroll_relationship_version_id,
          establishment_version_id,version_sequence,assignment_start,
          assignment_end,approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','%s','%s',1,'2026-01-01','2027-01-01',
          'APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(
            ASSIGNMENT_VERSION_ID,
            TENANT,
            ASSIGNMENT_ID,
            RELATIONSHIP_VERSION_ID,
            ESTABLISHMENT_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.pay_group_assignment(
          id,tenant_id,payroll_assignment_version_id,pay_group_version_id,
          effective_from,effective_to,approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','%s','2026-01-01','2027-01-01',
          'APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(
            GROUP_ASSIGNMENT_ID, TENANT, ASSIGNMENT_VERSION_ID, PAY_GROUP_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.salary_assignment(
          id,tenant_id,payroll_assignment_version_id,
          salary_structure_version_id,monthly_amount,currency,effective_from,
          effective_to,approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES ('%s','%s','%s','%s',85000.0000,'INR','2026-01-01',
          '2027-01-01','APPROVED',clock_timestamp(),'checker','maker','maker')
        """.formatted(
            SALARY_ASSIGNMENT_ID, TENANT, ASSIGNMENT_VERSION_ID, STRUCTURE_VERSION_ID));
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String permission) {
    return jwt().jwt(jwt -> jwt
        .issuer("https://issuer.example.test")
        .subject("compatibility-checker")
        .claim("tenant_id", TENANT))
        .authorities(() -> permission);
  }

  private static String calculationFunction() {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT pg_get_functiondef('payroll_calc.calculate_sealed_payroll("
                + "uuid,uuid,bigint,character varying,character varying,"
                + "character varying,timestamp with time zone)'::regprocedure)")) {
      if (!result.next()) {
        throw new IllegalStateException("Calculation function was not found");
      }
      return result.getString(1);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Could not read calculation function definition", exception);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
