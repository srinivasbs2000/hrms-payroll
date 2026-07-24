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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class PayrollRecalculationApiIT {
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
  void recalculationIsLinkedIdempotentAuditedAndHistorical()
      throws Exception {
    CalculatedCycle cycle = calculatedCycle("test-recalc-happy");
    String reason = "Approved payroll review rerun";

    MvcResult recalculated = recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-execute-one",
            "3",
            reason,
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"4\""))
        .andExpect(jsonPath("$.cycleId").value(cycle.cycleId()))
        .andExpect(jsonPath("$.supersededRequestId")
            .value(cycle.calculationRequestId()))
        .andExpect(jsonPath("$.attemptNo").value(2))
        .andExpect(jsonPath("$.resultCount").value(1))
        .andExpect(jsonPath("$.grossTotal").value(90000.0))
        .andExpect(jsonPath("$.deductionTotal").value(0.0))
        .andExpect(jsonPath("$.netTotal").value(90000.0))
        .andExpect(jsonPath("$.resultSetHash").isString())
        .andExpect(jsonPath("$.cycleVersionNo").value(4))
        .andExpect(jsonPath("$.completedAt").isString())
        .andReturn();

    JsonNode response = objectMapper.readTree(
        recalculated.getResponse().getContentAsString());
    String recalculationRequestId = response
        .get("calculationRequestId").asText();

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-execute-one",
            "3",
            reason,
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"4\""))
        .andExpect(jsonPath("$.calculationRequestId")
            .value(recalculationRequestId));

    mvc.perform(
            get(
                    "/api/v1/payroll-cycles/{cycleId}/calculation-requests",
                    cycle.cycleId())
                .with(token(TENANT_A, "payroll-result.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(recalculationRequestId))
        .andExpect(jsonPath("$[0].calculationKind")
            .value("RECALCULATION"))
        .andExpect(jsonPath("$[0].attemptNo").value(2))
        .andExpect(jsonPath("$[0].supersededRequestId")
            .value(cycle.calculationRequestId()))
        .andExpect(jsonPath("$[0].recalculationReason").value(reason))
        .andExpect(jsonPath("$[0].engineVersion")
            .value("STARTER_FIXED_V1"))
        .andExpect(jsonPath("$[1].id")
            .value(cycle.calculationRequestId()))
        .andExpect(jsonPath("$[1].calculationKind").value("INITIAL"))
        .andExpect(jsonPath("$[1].attemptNo").value(1));

    MvcResult results = mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/results", cycle.cycleId())
                .with(token(TENANT_A, "payroll-result.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].calculationRequestId")
            .value(recalculationRequestId))
        .andExpect(jsonPath("$[1].calculationRequestId")
            .value(cycle.calculationRequestId()))
        .andReturn();

    JsonNode resultList = objectMapper.readTree(
        results.getResponse().getContentAsString());
    for (JsonNode result : resultList) {
      mvc.perform(
              get(
                      "/api/v1/payroll-cycles/{cycleId}/results/{resultId}/trace",
                      cycle.cycleId(),
                      result.get("id").asText())
                  .with(token(
                      TENANT_A,
                      "payroll-result.trace.read")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].stepType").value("FIXED_COMPONENT"));
    }

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/audit", cycle.cycleId())
                .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[4].action").value("RECALCULATED"))
        .andExpect(jsonPath("$[5]").doesNotExist());

    org.assertj.core.api.Assertions.assertThat(
        outboxCount(cycle.cycleId(), "PayrollRecalculated"))
        .isOne();
  }

  @Test
  void recalculationRejectsStaleVersionAndConflictingReplay()
      throws Exception {
    CalculatedCycle cycle = calculatedCycle("test-recalc-conflict");

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-stale-version",
            "2",
            "Approved stale payroll rerun",
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-conflict-key",
            "3",
            "Approved payroll review rerun",
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isOk());

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-conflict-key",
            "3",
            "Approved payroll correction rerun",
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void recalculationRequiresDedicatedPermission() throws Exception {
    CalculatedCycle cycle = calculatedCycle("test-recalc-permission");

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-missing-permission",
            "3",
            "Approved payroll review rerun",
            TENANT_A,
            "payroll-result.read")
        .andExpect(status().isForbidden());
  }

  @Test
  void recalculationValidatesReason() throws Exception {
    CalculatedCycle cycle = calculatedCycle("test-recalc-validation");

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-blank-reason",
            "3",
            " ",
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isUnprocessableEntity());

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-long-reason",
            "3",
            "x".repeat(501),
            TENANT_A,
            "payroll-calculation.recalculate")
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void recalculationIsTenantIsolated() throws Exception {
    CalculatedCycle cycle = calculatedCycle("test-recalc-tenant");

    recalculatePayroll(
            cycle.cycleId(),
            "test-recalc-cross-tenant",
            "3",
            "Approved payroll review rerun",
            TENANT_B,
            "payroll-calculation.recalculate")
        .andExpect(status().isNotFound());
  }

  private CalculatedCycle calculatedCycle(String keyPrefix) throws Exception {
    MvcResult created = createCycle(keyPrefix + "-create", PERIOD_ID)
        .andExpect(status().isCreated())
        .andReturn();
    String cycleId = objectMapper.readTree(
        created.getResponse().getContentAsString()).get("id").asText();

    resolvePopulation(cycleId, keyPrefix + "-resolve", "0")
        .andExpect(status().isOk());
    sealInputs(cycleId, keyPrefix + "-seal", "1")
        .andExpect(status().isOk());
    MvcResult calculated = calculatePayroll(
            cycleId, keyPrefix + "-calculate", "2")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"3\""))
        .andReturn();
    String calculationRequestId = objectMapper.readTree(
        calculated.getResponse().getContentAsString())
        .get("calculationRequestId").asText();
    return new CalculatedCycle(cycleId, calculationRequestId);
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

  private org.springframework.test.web.servlet.ResultActions sealInputs(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/seal-inputs",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-cycle.inputs.seal"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private org.springframework.test.web.servlet.ResultActions calculatePayroll(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/calculation",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-calculation.execute"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private org.springframework.test.web.servlet.ResultActions recalculatePayroll(
      String cycleId,
      String key,
      String ifMatch,
      String reason,
      String tenant,
      String... permissions) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/recalculation",
                cycleId)
            .with(token(tenant, permissions))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                java.util.Map.of("reason", reason))));
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant, String... permissions) {
    return jwt().jwt(value -> value
        .issuer("https://issuer.example.test")
        .subject("synthetic-subject")
        .claim("tenant_id", tenant))
        .authorities(Arrays.stream(permissions)
            .map(SimpleGrantedAuthority::new)
            .toArray(SimpleGrantedAuthority[]::new));
  }

  private static long outboxCount(String cycleId, String eventType)
      throws Exception {
    try (Connection connection = admin();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT count(*) FROM integration.outbox_event "
                + "WHERE aggregate_id=?::uuid AND event_type=?")) {
      statement.setString(1, cycleId);
      statement.setString(2, eventType);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
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

  private record CalculatedCycle(
      String cycleId, String calculationRequestId) {}
}
