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
class PayGroupApiIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_ID =
      "11000000-0000-0000-0000-000000000001";
  private static final String LEGAL_VERSION_ID =
      "11100000-0000-0000-0000-000000000001";
  private static final String PSU_ID =
      "12000000-0000-0000-0000-000000000001";
  private static final String PSU_VERSION_ID =
      "12100000-0000-0000-0000-000000000001";
  private static final String CALENDAR_ID =
      "13000000-0000-0000-0000-000000000001";

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
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add(
        "spring.datasource.username", () -> "payroll_app");
    registry.add(
        "spring.datasource.password", () -> APP_PASSWORD);
    registry.add(
        "spring.jpa.hibernate.ddl-auto", () -> "none");
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
              + "('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");

      statement.execute(
          "INSERT INTO organisation.legal_entity("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + LEGAL_ID
              + "','"
              + TENANT_A
              + "','ACME_IN','test','test')");
      statement.execute(
          "INSERT INTO organisation.legal_entity_version("
              + "id,tenant_id,legal_entity_id,version_sequence,"
              + "name,country_code,currency,effective_from,"
              + "effective_to,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES ('"
              + LEGAL_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + LEGAL_ID
              + "',1,'Acme India','IN','INR','2026-01-01',"
              + "'2028-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + PSU_ID
              + "','"
              + TENANT_A
              + "','ACME_PSU','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit_version("
              + "id,tenant_id,payroll_statutory_unit_id,"
              + "legal_entity_version_id,version_sequence,name,"
              + "effective_from,effective_to,approval_status,"
              + "approved_at,approved_by,created_by,updated_by"
              + ") VALUES ('"
              + PSU_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + PSU_ID
              + "','"
              + LEGAL_VERSION_ID
              + "',1,'Acme PSU','2026-01-01','2028-01-01',"
              + "'APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_calendar("
              + "id,tenant_id,code,name,frequency,timezone,"
              + "created_by,updated_by) VALUES ('"
              + CALENDAR_ID
              + "','"
              + TENANT_A
              + "','MONTHLY_IN','Monthly India','MONTHLY',"
              + "'Asia/Kolkata','test','test')");
    }
  }

  @Test
  void lifecycleIsIdempotentAuditedAndTenantIsolated()
      throws Exception {
    String request =
        """
        {
          "code":"MONTHLY_IN",
          "name":"Monthly India",
          "payrollStatutoryUnitVersionId":"%s",
          "calendarId":"%s",
          "currency":"INR",
          "prorationMethod":"CALENDAR_DAYS",
          "effectiveFrom":"2026-01-01",
          "effectiveTo":"2028-01-01"
        }
        """.formatted(PSU_VERSION_ID, CALENDAR_ID);

    MvcResult created = mvc.perform(
            post("/api/v1/pay-groups")
                .with(token(TENANT_A, "pay-group.create"))
                .header(
                    "Idempotency-Key",
                    "create-pay-group-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(
            jsonPath("$.currency").value("INR"))
        .andExpect(
            jsonPath("$.prorationMethod")
                .value("CALENDAR_DAYS"))
        .andReturn();

    JsonNode first = objectMapper.readTree(
        created.getResponse().getContentAsString());

    MvcResult replay = mvc.perform(
            post("/api/v1/pay-groups")
                .with(token(TENANT_A, "pay-group.create"))
                .header(
                    "Idempotency-Key",
                    "create-pay-group-0001")
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

    mvc.perform(
            post(
                "/api/v1/pay-groups/{identityId}/versions/"
                    + "{versionId}/approval",
                identityId,
                versionId)
                .with(token(TENANT_A, "pay-group.approve"))
                .header(
                    "Idempotency-Key",
                    "approve-pay-group-0001"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.approvalStatus")
                .value("APPROVED"))
        .andExpect(jsonPath("$.versionNo").value(1));

    mvc.perform(
            get("/api/v1/pay-groups/{identityId}", identityId)
                .param("asOf", "2026-07-21")
                .with(token(TENANT_A, "pay-group.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.code").value("MONTHLY_IN"))
        .andExpect(
            jsonPath("$.versionId").value(versionId));

    mvc.perform(
            get(
                "/api/v1/pay-groups/{identityId}/versions",
                identityId)
                .with(token(TENANT_A, "pay-group.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].approvalStatus")
                .value("APPROVED"));

    mvc.perform(
            get(
                "/api/v1/pay-groups/{identityId}/audit",
                identityId)
                .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[0].action").value("CREATED"))
        .andExpect(
            jsonPath("$[1].action")
                .value("VERSION_APPROVED"));

    mvc.perform(
            get("/api/v1/pay-groups")
                .param("asOf", "2026-07-21")
                .with(token(TENANT_B, "pay-group.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void missingPayGroupPermissionIsForbidden()
      throws Exception {
    mvc.perform(
            get("/api/v1/pay-groups")
                .with(token(TENANT_A, "organisation.read")))
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
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
