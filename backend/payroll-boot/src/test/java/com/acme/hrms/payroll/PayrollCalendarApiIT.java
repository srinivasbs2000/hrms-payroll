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
class PayrollCalendarApiIT {
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
      statement.execute(
          "GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public "
              + "TO payroll_owner");
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
  static void properties(
      DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        POSTGRES::getJdbcUrl);
    registry.add(
        "spring.datasource.username",
        () -> "payroll_app");
    registry.add(
        "spring.datasource.password",
        () -> APP_PASSWORD);
    registry.add(
        "spring.jpa.hibernate.ddl-auto",
        () -> "none");
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
      statement.execute(
          "TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void lifecycleIsIdempotentAuditedAndTenantIsolated()
      throws Exception {
    String calendarRequest =
        """
        {
          "code":"MONTHLY_IN",
          "name":"Monthly India",
          "frequency":"MONTHLY",
          "timezone":"Asia/Kolkata"
        }
        """;

    MvcResult created = mvc.perform(
            post("/api/v1/payroll-calendars")
                .with(token(
                    TENANT_A,
                    "calendar.create"))
                .header(
                    "Idempotency-Key",
                    "calendar-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(calendarRequest))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.frequency").value("MONTHLY"))
        .andExpect(
            jsonPath("$.timezone")
                .value("Asia/Kolkata"))
        .andReturn();

    JsonNode first = objectMapper.readTree(
        created.getResponse().getContentAsString());
    String calendarId = first.get("id").asText();

    MvcResult replay = mvc.perform(
            post("/api/v1/payroll-calendars")
                .with(token(
                    TENANT_A,
                    "calendar.create"))
                .header(
                    "Idempotency-Key",
                    "calendar-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(calendarRequest))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode replayed = objectMapper.readTree(
        replay.getResponse().getContentAsString());
    assertThat(replayed.get("id").asText())
        .isEqualTo(calendarId);

    mvc.perform(
            get("/api/v1/payroll-calendars")
                .with(token(
                    TENANT_A,
                    "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(calendarId));

    String generationRequest =
        """
        {
          "year":2028,
          "paymentDay":31
        }
        """;

    MvcResult generated = mvc.perform(
            post(
                "/api/v1/payroll-calendars/"
                    + "{calendarId}/periods",
                calendarId)
                .with(token(
                    TENANT_A,
                    "calendar.period.generate"))
                .header(
                    "Idempotency-Key",
                    "calendar-periods-2028")
                .contentType(MediaType.APPLICATION_JSON)
                .content(generationRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(12))
        .andExpect(
            jsonPath("$[1].periodCode")
                .value("2028-02"))
        .andExpect(
            jsonPath("$[1].periodEnd")
                .value("2028-02-29"))
        .andExpect(
            jsonPath("$[1].paymentDate")
                .value("2028-02-29"))
        .andReturn();

    JsonNode generatedBody = objectMapper.readTree(
        generated.getResponse().getContentAsString());

    MvcResult generationReplay = mvc.perform(
            post(
                "/api/v1/payroll-calendars/"
                    + "{calendarId}/periods",
                calendarId)
                .with(token(
                    TENANT_A,
                    "calendar.period.generate"))
                .header(
                    "Idempotency-Key",
                    "calendar-periods-2028")
                .contentType(MediaType.APPLICATION_JSON)
                .content(generationRequest))
        .andExpect(status().isCreated())
        .andReturn();

    JsonNode replayBody = objectMapper.readTree(
        generationReplay.getResponse()
            .getContentAsString());
    assertThat(replayBody.get(0).get("id").asText())
        .isEqualTo(
            generatedBody.get(0).get("id").asText());

    mvc.perform(
            get(
                "/api/v1/payroll-calendars/"
                    + "{calendarId}/periods",
                calendarId)
                .param("year", "2028")
                .with(token(
                    TENANT_A,
                    "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(12));

    mvc.perform(
            get(
                "/api/v1/payroll-calendars/"
                    + "{calendarId}/audit",
                calendarId)
                .with(token(
                    TENANT_A,
                    "audit.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].action").value("CREATED"))
        .andExpect(
            jsonPath("$[1].action")
                .value("PERIODS_GENERATED"));

    mvc.perform(
            get("/api/v1/payroll-calendars")
                .with(token(
                    TENANT_B,
                    "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void configurationAndReadinessAreIdempotentLifecycleSafeAndTenantIsolated()
      throws Exception {
    MvcResult created = mvc.perform(
            post("/api/v1/payroll-calendars")
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code":"B02_WEEKLY",
                      "name":"B02 Weekly",
                      "frequency":"WEEKLY",
                      "timezone":"Asia/Kolkata"
                    }
                    """))
        .andExpect(status().isCreated())
        .andReturn();
    String calendarId = objectMapper.readTree(
        created.getResponse().getContentAsString()).get("id").asText();

    String rules = """
        {
          "rules":[
            {"milestoneType":"INPUT_CUTOFF","anchorType":"PERIOD_END","offsetDays":-3,"adjustmentPolicy":"PREVIOUS_WORKING_DAY"},
            {"milestoneType":"CALCULATION","anchorType":"PERIOD_END","offsetDays":-2,"adjustmentPolicy":"PREVIOUS_WORKING_DAY"},
            {"milestoneType":"APPROVAL","anchorType":"PERIOD_END","offsetDays":-1,"adjustmentPolicy":"PREVIOUS_WORKING_DAY"},
            {"milestoneType":"RELEASE","anchorType":"PERIOD_END","offsetDays":0,"adjustmentPolicy":"PREVIOUS_WORKING_DAY"},
            {"milestoneType":"PAYMENT","anchorType":"PERIOD_END","offsetDays":0,"adjustmentPolicy":"NEXT_WORKING_DAY"}
          ]
        }
        """;
    MvcResult configured = mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/milestone-rules", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-rules-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rules))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].milestoneType").value("INPUT_CUTOFF"))
        .andReturn();

    MvcResult replay = mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/milestone-rules", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-rules-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rules))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).get(0).get("id"))
        .isEqualTo(objectMapper.readTree(
            configured.getResponse().getContentAsString()).get(0).get("id"));

    mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/holidays", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-holiday-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"holidayDate":"2028-01-03","holidayName":"Synthetic Holiday"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNo").value(0));

    mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/holidays", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-holiday-correction-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"holidayDate":"2028-01-03","holidayName":"Corrected Holiday"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.holidayName").value("Corrected Holiday"))
        .andExpect(jsonPath("$.versionNo").value(1));

    assertThat(domainEventCount("PayrollCalendarMilestoneRuleConfigured")).isEqualTo(5);
    assertThat(domainEventCount("PayrollCalendarHolidayConfigured")).isEqualTo(2);
    mvc.perform(
            get("/api/v1/payroll-calendars/{calendarId}/audit", calendarId)
                .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].action").value("MILESTONE_RULES_CONFIGURED"))
        .andExpect(jsonPath("$[2].action").value("HOLIDAY_CONFIGURED"))
        .andExpect(jsonPath("$[3].action").value("HOLIDAY_CONFIGURED"));

    mvc.perform(
            get("/api/v1/payroll-calendars/{calendarId}/readiness", calendarId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generationReady").value(true))
        .andExpect(jsonPath("$.publicationReady").value(false))
        .andExpect(jsonPath("$.blockers[0]").value("PAY_PERIODS_NOT_GENERATED"));

    mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/periods", calendarId)
                .with(token(TENANT_A, "calendar.period.generate"))
                .header("Idempotency-Key", "b02-calendar-periods-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"startDate":"2028-01-01","periodCount":2}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2));

    mvc.perform(
            get("/api/v1/payroll-calendars/{calendarId}/readiness", calendarId)
                .with(token(TENANT_A, "calendar.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationReady").value(true))
        .andExpect(jsonPath("$.blockers").isEmpty());

    mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/publication", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-publish-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"B02 contract test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("PUBLISHED"));

    mvc.perform(
            post("/api/v1/payroll-calendars/{calendarId}/holidays", calendarId)
                .with(token(TENANT_A, "calendar.create"))
                .header("Idempotency-Key", "b02-calendar-holiday-after-publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"holidayDate":"2028-01-04","holidayName":"Too Late"}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail")
            .value("Payroll calendar configuration is immutable or invalid"));

    mvc.perform(
            get("/api/v1/payroll-calendars/{calendarId}/milestone-rules", calendarId)
                .with(token(TENANT_B, "calendar.read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingCalendarPermissionIsForbidden()
      throws Exception {
    mvc.perform(
            get("/api/v1/payroll-calendars")
                .with(token(
                    TENANT_A,
                    "organisation.read")))
        .andExpect(status().isForbidden());
  }

  private static org.springframework.security.test.web.servlet
      .request.SecurityMockMvcRequestPostProcessors
      .JwtRequestPostProcessor token(
          String tenant, String permission) {
    return jwt().jwt(jwt -> jwt
        .issuer("https://issuer.example.test")
        .subject("synthetic-subject")
        .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "postgres",
        "postgres");
  }

  private static int domainEventCount(String eventType) throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT count(*) FROM integration.outbox_event WHERE event_type='"
                + eventType + "'")) {
      if (!result.next()) {
        throw new IllegalStateException("Outbox count query returned no row");
      }
      return result.getInt(1);
    }
  }
}
