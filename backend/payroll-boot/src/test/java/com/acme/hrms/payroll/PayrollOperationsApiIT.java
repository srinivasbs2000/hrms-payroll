package com.acme.hrms.payroll;

import static org.springframework.security.test.web.servlet.request
    .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
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
class PayrollOperationsApiIT {
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();

  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_ID =
      "31000000-0000-0000-0000-000000000001";
  private static final String LEGAL_VERSION_ID =
      "31100000-0000-0000-0000-000000000001";
  private static final String PSU_ID =
      "32000000-0000-0000-0000-000000000001";
  private static final String PSU_VERSION_ID =
      "32100000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_ID =
      "33000000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_VERSION_ID =
      "33100000-0000-0000-0000-000000000001";
  private static final String CALENDAR_ID =
      "34000000-0000-0000-0000-000000000001";
  private static final String PERIOD_ID =
      "34100000-0000-0000-0000-000000000001";
  private static final String SECOND_PERIOD_ID =
      "34100000-0000-0000-0000-000000000002";
  private static final String PAY_GROUP_ID =
      "35000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_VERSION_ID =
      "35100000-0000-0000-0000-000000000001";
  private static final String COMPONENT_ID =
      "36000000-0000-0000-0000-000000000001";
  private static final String COMPONENT_VERSION_ID =
      "36100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_ID =
      "37000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_VERSION_ID =
      "37100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_LINE_ID =
      "37200000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_ID =
      "38000000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_VERSION_ID =
      "38100000-0000-0000-0000-000000000001";
  private static final String PROFILE_ID =
      "38200000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_ID =
      "39000000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_VERSION_ID =
      "39100000-0000-0000-0000-000000000001";
  private static final String GROUP_ASSIGNMENT_ID =
      "39200000-0000-0000-0000-000000000001";
  private static final String SALARY_ASSIGNMENT_ID =
      "39300000-0000-0000-0000-000000000001";

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
  void seedReadyEmployeeConfiguration() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
      seedOrganisation(statement);
      seedPayrollConfiguration(statement);
      seedEmployee(statement);
      statement.execute("RESET ROLE");
    }
  }

  @Test
  void cyclePopulationLifecycleIsIdempotentAuditedAndTenantIsolated()
      throws Exception {
    MvcResult created = createCycle("test-test-test-one", PERIOD_ID)
        .andExpect(status().isCreated())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andReturn();

    JsonNode cycle = objectMapper.readTree(
        created.getResponse().getContentAsString());
    String cycleId = cycle.get("id").asText();

    createCycle("test-test-test-one", PERIOD_ID)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(cycleId));

    mvc.perform(
            get("/api/v1/payroll-cycles")
                .with(token(TENANT_A, "payroll-cycle.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(cycleId));

    mvc.perform(
            post(
                    "/api/v1/payroll-cycles/{cycleId}/population-resolution",
                    cycleId)
                .with(token(
                    TENANT_A,
                    "payroll-cycle.population.resolve"))
                .header("Idempotency-Key", "test-test-test-two")
                .header("If-Match", "0"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.attemptNo").value(1))
        .andExpect(jsonPath("$.includedCount").value(1))
        .andExpect(jsonPath("$.excludedCount").value(0));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/population", cycleId)
                .with(token(TENANT_A, "payroll-cycle.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].employeeNumber").value("EMP-001"))
        .andExpect(jsonPath("$[0].assignmentNumber").value("ASN-001"));

    mvc.perform(
            get(
                    "/api/v1/payroll-cycles/{cycleId}/population-decisions",
                    cycleId)
                .with(token(TENANT_A, "payroll-cycle.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].decision").value("INCLUDED"))
        .andExpect(jsonPath("$[0].reasonCode").value("INCLUDED"));

    mvc.perform(
            get(
                    "/api/v1/payroll-cycles/{cycleId}/population-resolutions",
                    cycleId)
                .with(token(TENANT_A, "payroll-cycle.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].attemptNo").value(1))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/audit", cycleId)
                .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action")
            .value("POPULATION_RESOLVED"));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}", cycleId)
                .with(token(TENANT_B, "payroll-cycle.read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void createIdempotencyKeyRejectsChangedPayload() throws Exception {
    createCycle("test-test-test-three", PERIOD_ID)
        .andExpect(status().isCreated());

    createCycle("test-test-test-three", SECOND_PERIOD_ID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void stalePopulationVersionIsConflict() throws Exception {
    MvcResult created = createCycle("test-test-test-four", PERIOD_ID)
        .andExpect(status().isCreated())
        .andReturn();
    String cycleId = objectMapper.readTree(
        created.getResponse().getContentAsString()).get("id").asText();

    resolvePopulation(cycleId, "test-test-test-five", "0")
        .andExpect(status().isOk());

    resolvePopulation(cycleId, "test-test-test-six", "0")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void missingPayrollCyclePermissionIsForbidden() throws Exception {
    mvc.perform(
            get("/api/v1/payroll-cycles")
                .with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.ResultActions createCycle(
      String key, String periodId) throws Exception {
    return mvc.perform(
        post("/api/v1/payroll-cycles")
            .with(token(TENANT_A, "payroll-cycle.create"))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "payGroupVersionId":"%s",
                  "payPeriodId":"%s"
                }
                """.formatted(PAY_GROUP_VERSION_ID, periodId)));
  }

  private org.springframework.test.web.servlet.ResultActions resolvePopulation(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/population-resolution",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-cycle.population.resolve"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant, String permission) {
    return jwt().jwt(jwt -> jwt
        .issuer("https://issuer.example.test")
        .subject("synthetic-subject")
        .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static void seedOrganisation(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.legal_entity(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_IN','test','test')
        """.formatted(LEGAL_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.legal_entity_version(
          id,tenant_id,legal_entity_id,version_sequence,
          name,country_code,currency,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Acme India','IN','INR',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_PSU','test','test')
        """.formatted(PSU_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit_version(
          id,tenant_id,payroll_statutory_unit_id,
          legal_entity_version_id,version_sequence,name,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Acme PSU',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PSU_VERSION_ID, TENANT_A, PSU_ID, LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO organisation.establishment(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','BLR','test','test')
        """.formatted(ESTABLISHMENT_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.establishment_version(
          id,tenant_id,establishment_id,
          payroll_statutory_unit_version_id,version_sequence,
          name,state_code,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Bengaluru','KA',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            ESTABLISHMENT_VERSION_ID,
            TENANT_A,
            ESTABLISHMENT_ID,
            PSU_VERSION_ID));
  }

  private static void seedPayrollConfiguration(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.payroll_calendar(
          id,tenant_id,code,name,frequency,timezone,
          created_by,updated_by
        ) VALUES (
          '%s','%s','MONTHLY_IN','Monthly India',
          'MONTHLY','Asia/Kolkata','test','test'
        )
        """.formatted(CALENDAR_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.pay_period(
          id,tenant_id,calendar_id,period_code,
          period_start,period_end,payment_date,status,
          created_by,updated_by
        ) VALUES
          ('%s','%s','%s','2026-07','2026-07-01',
           '2026-07-31','2026-07-31','OPEN','test','test'),
          ('%s','%s','%s','2026-08','2026-08-01',
           '2026-08-31','2026-08-31','OPEN','test','test')
        """.formatted(
            PERIOD_ID, TENANT_A, CALENDAR_ID,
            SECOND_PERIOD_ID, TENANT_A, CALENDAR_ID));
    statement.execute(
        """
        INSERT INTO organisation.pay_group(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','MONTHLY_IN','test','test')
        """.formatted(PAY_GROUP_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.pay_group_version(
          id,tenant_id,pay_group_id,
          payroll_statutory_unit_version_id,calendar_id,
          version_sequence,name,currency,proration_method,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,
          'Monthly India','INR','CALENDAR_DAYS',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PAY_GROUP_VERSION_ID,
            TENANT_A,
            PAY_GROUP_ID,
            PSU_VERSION_ID,
            CALENDAR_ID));
    statement.execute(
        """
        INSERT INTO compensation.pay_component(
          id,tenant_id,code,name,component_type,
          created_by,updated_by
        ) VALUES (
          '%s','%s','BASIC','Basic Pay','EARNING','test','test'
        )
        """.formatted(COMPONENT_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.pay_component_version(
          id,tenant_id,component_id,version_sequence,
          formula_type,formula_expression,fixed_amount,
          rounding_scale,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'FIXED',NULL,90000.0000,2,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(COMPONENT_VERSION_ID, TENANT_A, COMPONENT_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','DEFAULT','test','test')
        """.formatted(STRUCTURE_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_version(
          id,tenant_id,salary_structure_id,version_sequence,
          name,currency,effective_from,effective_to,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Default Structure','INR',
          '2026-01-01','2027-01-01','DRAFT','test','test'
        )
        """.formatted(STRUCTURE_VERSION_ID, TENANT_A, STRUCTURE_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          id,tenant_id,salary_structure_version_id,
          component_version_id,sequence_no,target_amount,
          effective_from,effective_to,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,90000.0000,
          '2026-01-01','2027-01-01','test','test'
        )
        """.formatted(
            STRUCTURE_LINE_ID,
            TENANT_A,
            STRUCTURE_VERSION_ID,
            COMPONENT_VERSION_ID));
    statement.execute(
        "SELECT compensation.approve_salary_structure_version('"
            + TENANT_A
            + "','"
            + STRUCTURE_VERSION_ID
            + "','test',clock_timestamp())");
  }

  private static void seedEmployee(Statement statement) throws Exception {
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,
          status,created_by,updated_by
        ) VALUES (
          '%s','%s','EMP-EXT-001','EMP-001','ACTIVE','test','test'
        )
        """.formatted(RELATIONSHIP_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship_version(
          id,tenant_id,payroll_relationship_id,
          legal_entity_version_id,version_sequence,
          relationship_start,relationship_end,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            RELATIONSHIP_VERSION_ID,
            TENANT_A,
            RELATIONSHIP_ID,
            LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.employee_payroll_profile(
          id,tenant_id,payroll_relationship_id,
          currency,payroll_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','INR','READY','test','test'
        )
        """.formatted(PROFILE_ID, TENANT_A, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,
          assignment_number,status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','ASN-001','ACTIVE','test','test'
        )
        """.formatted(ASSIGNMENT_ID, TENANT_A, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,
          payroll_relationship_version_id,establishment_version_id,
          version_sequence,assignment_start,assignment_end,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            ASSIGNMENT_VERSION_ID,
            TENANT_A,
            ASSIGNMENT_ID,
            RELATIONSHIP_VERSION_ID,
            ESTABLISHMENT_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.pay_group_assignment(
          id,tenant_id,payroll_assignment_version_id,
          pay_group_version_id,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','2026-01-01','2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
        )
        """.formatted(
            GROUP_ASSIGNMENT_ID,
            TENANT_A,
            ASSIGNMENT_VERSION_ID,
            PAY_GROUP_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.salary_assignment(
          id,tenant_id,payroll_assignment_version_id,
          salary_structure_version_id,monthly_amount,currency,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',90000.0000,'INR',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            SALARY_ASSIGNMENT_ID,
            TENANT_A,
            ASSIGNMENT_VERSION_ID,
            STRUCTURE_VERSION_ID));
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
