package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet
    .request.SecurityMockMvcRequestPostProcessors.jwt;
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
class PayrollCalendarLifecycleApiIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
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
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
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
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void publicationAmendmentRetirementAndOperationalEvidenceAreExposed()
      throws Exception {
    MvcResult created =
        mvc.perform(
                post("/api/v1/payroll-calendars")
                    .with(token(TENANT_A, "calendar.create"))
                    .header("Idempotency-Key", "r3-calendar-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"R3_MONTHLY",
                          "name":"R3 Monthly",
                          "frequency":"MONTHLY",
                          "timezone":"Asia/Kolkata"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.calendarVersion").value(1))
            .andReturn();

    JsonNode createdBody =
        objectMapper.readTree(created.getResponse().getContentAsString());
    String calendarId = createdBody.get("id").asText();

    mvc.perform(
            get(
                    "/api/v1/payroll-calendars/{calendarId}/operations",
                    calendarId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationRequired").value(true))
        .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
        .andExpect(jsonPath("$.milestoneRuleCount").value(0));

    configureRules(calendarId);

    mvc.perform(
            post(
                    "/api/v1/payroll-calendars/{calendarId}/periods",
                    calendarId)
                .with(token(TENANT_A, "calendar.period.generate"))
                .header("Idempotency-Key", "r3-calendar-periods-v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate":"2028-01-01",
                      "periodCount":1
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].periodCode").value("2028-01"))
        .andExpect(jsonPath("$[0].paymentDate").value("2028-01-31"));

    mvc.perform(
            get(
                    "/api/v1/payroll-calendars/{calendarId}/period-operations",
                    calendarId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(
            jsonPath("$[0].approvalOriginalDate").value("2028-01-29"))
        .andExpect(
            jsonPath("$[0].approvalAdjustedDate").value("2028-01-28"))
        .andExpect(
            jsonPath("$[0].releaseOriginalDate").value("2028-01-30"))
        .andExpect(
            jsonPath("$[0].releaseAdjustedDate").value("2028-01-31"));

    mvc.perform(
            post(
                    "/api/v1/payroll-calendars/{calendarId}/publication",
                    calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "r3-calendar-publish-v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"initial publication\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationRequired").value(true))
        .andExpect(jsonPath("$.lifecycleStatus").value("PUBLISHED"))
        .andExpect(jsonPath("$.milestoneRuleCount").value(5))
        .andExpect(jsonPath("$.periodCount").value(1));

    MvcResult amended =
        mvc.perform(
                post(
                        "/api/v1/payroll-calendars/{calendarId}/amendments",
                        calendarId)
                    .with(token(TENANT_A, "calendar.create"))
                    .header("Idempotency-Key", "r3-calendar-amend-v2"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.calendarVersion").value(2))
            .andExpect(jsonPath("$.supersedesCalendarId").value(calendarId))
            .andReturn();

    String successorId =
        objectMapper
            .readTree(amended.getResponse().getContentAsString())
            .get("id")
            .asText();

    mvc.perform(
            get(
                    "/api/v1/payroll-calendars/{calendarId}/operations",
                    successorId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationRequired").value(true))
        .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
        .andExpect(jsonPath("$.milestoneRuleCount").value(5))
        .andExpect(jsonPath("$.periodCount").value(0));

    mvc.perform(
            post(
                    "/api/v1/payroll-calendars/{calendarId}/periods",
                    successorId)
                .with(token(TENANT_A, "calendar.period.generate"))
                .header("Idempotency-Key", "r3-calendar-periods-v2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate":"2028-01-01",
                      "periodCount":1
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1));

    mvc.perform(
            post(
                    "/api/v1/payroll-calendars/{calendarId}/publication",
                    successorId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "r3-calendar-publish-v2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"successor publication\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("PUBLISHED"));

    mvc.perform(
            get(
                    "/api/v1/payroll-calendars/{calendarId}/operations",
                    calendarId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("RETIRED"));

    mvc.perform(
            post(
                    "/api/v1/payroll-calendars/{calendarId}/retirement",
                    successorId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "r3-calendar-retire-v2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"superseded operational policy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("RETIRED"));

    mvc.perform(
            get(
                    "/api/v1/payroll-calendars/{calendarId}/operations",
                    calendarId)
                .with(token(TENANT_B, "calendar.read")))
        .andExpect(status().isNotFound());
  }

  private static void configureRules(String calendarId) throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        String[][] rules = {
          {"INPUT_CUTOFF", "PERIOD_START", "0", "NEXT_WORKING_DAY"},
          {"CALCULATION", "PERIOD_END", "-3", "PREVIOUS_WORKING_DAY"},
          {"APPROVAL", "PERIOD_END", "-2", "PREVIOUS_WORKING_DAY"},
          {"RELEASE", "PERIOD_END", "-1", "NEXT_WORKING_DAY"},
          {"PAYMENT", "PERIOD_END", "0", "PREVIOUS_WORKING_DAY"}
        };
        for (String[] rule : rules) {
          statement.executeQuery(
              "SELECT organisation.configure_payroll_calendar_milestone_rule("
                  + "'"
                  + TENANT_A
                  + "'::uuid,'"
                  + calendarId
                  + "'::uuid,'"
                  + rule[0]
                  + "'::varchar,'"
                  + rule[1]
                  + "'::varchar,"
                  + rule[2]
                  + ",'"
                  + rule[3]
                  + "'::varchar,'api-r3'::varchar,clock_timestamp())")
              .close();
        }
      }
      connection.commit();
    }
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
          String tenant, String permission) {
    return jwt()
        .jwt(
            jwt ->
                jwt.issuer("https://issuer.example.test")
                    .subject("synthetic-subject")
                    .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}
