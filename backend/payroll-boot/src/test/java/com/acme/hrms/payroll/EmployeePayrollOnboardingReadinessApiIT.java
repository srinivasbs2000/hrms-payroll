package com.acme.hrms.payroll;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class EmployeePayrollOnboardingReadinessApiIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT_A = "00000000-0000-0000-0000-0000000000a1";
  private static final String TENANT_B = "00000000-0000-0000-0000-0000000000b1";
  private static final String RELATIONSHIP = "53000000-0000-0000-0000-000000000001";
  private static final String CASE_ID = "53100000-0000-0000-0000-000000000001";
  private static final String HOLD_ID = "53200000-0000-0000-0000-000000000001";
  private static final String HOLD_VERSION = "53300000-0000-0000-0000-000000000001";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  static {
    POSTGRES.start();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load().migrate();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "payroll_app");
    registry.add("spring.datasource.password", () -> APP_PASSWORD);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "https://issuer.example.test");
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "https://issuer.example.test/jwks");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean JwtDecoder jwtDecoder;

  @BeforeEach
  void seed() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute("insert into platform.tenant(id,code,name,created_by,updated_by) values ('" + TENANT_A + "','A','Tenant A','test','test'),('" + TENANT_B + "','B','Tenant B','test','test')");
      statement.execute("set role payroll_owner");
      statement.execute("select set_config('app.tenant_id','" + TENANT_A + "',false)");
      statement.execute("insert into employee_payroll.payroll_relationship(id,tenant_id,external_employee_id,employee_number,created_by,updated_by) values ('" + RELATIONSHIP + "','" + TENANT_A + "','EOR-EXT','EOR-001','test','test')");
      statement.execute("reset role");
    }
  }

  @Test
  void onboardingReadinessHoldsWorkbenchAndTenantIsolationAreExposed() throws Exception {
    mvc.perform(post("/api/v1/payroll-relationships/{relationshipId}/onboarding", RELATIONSHIP)
            .with(token(TENANT_A, "employee-payroll.onboarding.write", "onboarding-maker"))
            .header("Idempotency-Key", "eor-onboarding-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"caseId\":\"" + CASE_ID + "\",\"reason\":\"Payroll onboarding\",\"evidenceRef\":\"ONB-EVIDENCE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentStatus").value("DATA_COLLECTION"));

    mvc.perform(get("/api/v1/payroll-relationships/{relationshipId}/readiness", RELATIONSHIP)
            .param("asOf", "2026-08-25")
            .with(token(TENANT_A, "employee-payroll.readiness.read", "readiness-reader")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(jsonPath("$.findings[?(@.dimension == 'STATUTORY' && @.status == 'NOT_EVALUATED')]").exists())
        .andExpect(jsonPath("$.findings[?(@.dimension == 'TAX' && @.status == 'NOT_EVALUATED')]").exists());

    mvc.perform(post("/api/v1/payroll-relationships/{relationshipId}/holds", RELATIONSHIP)
            .with(token(TENANT_A, "employee-payroll.hold.read", "read-only-user"))
            .header("Idempotency-Key", "eor-hold-denied")
            .contentType(MediaType.APPLICATION_JSON)
            .content(holdRequest()))
        .andExpect(status().isForbidden());

    MvcResult created = mvc.perform(post("/api/v1/payroll-relationships/{relationshipId}/holds", RELATIONSHIP)
            .with(token(TENANT_A, "employee-payroll.hold.write", "hold-maker"))
            .header("Idempotency-Key", "eor-hold-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(holdRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
        .andReturn();
    JsonNode hold = objectMapper.readTree(created.getResponse().getContentAsString());

    mvc.perform(post("/api/v1/payroll-relationships/{relationshipId}/holds/{versionId}/approve", RELATIONSHIP, hold.get("versionId").asText())
            .with(token(TENANT_A, "employee-payroll.hold.approve", "hold-checker"))
            .header("Idempotency-Key", "eor-hold-approve")
            .header("If-Match", "0")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"evidenceRef\":\"HOLD-APPROVAL\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));

    mvc.perform(get("/api/v1/employee-payroll/workbench")
            .param("asOf", "2026-08-25")
            .with(token(TENANT_A, "employee-payroll.workbench.read", "workbench-reader")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].activeHoldScopes[?(@ == 'CALCULATION')]").exists());

    mvc.perform(get("/api/v1/employee-payroll/workbench")
            .param("asOf", "2026-08-25")
            .with(token(TENANT_B, "employee-payroll.workbench.read", "tenant-b-reader")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  private String holdRequest() {
    return "{\"holdId\":\"" + HOLD_ID + "\",\"versionId\":\"" + HOLD_VERSION
        + "\",\"scopes\":[\"CALCULATION\",\"PAYMENT\"],\"reasonCode\":\"SECURITY_REVIEW\","
        + "\"reason\":\"Security review\",\"sourceReference\":\"EOR-API-IT\","
        + "\"effectiveFrom\":\"2026-08-01\"}";
  }

  private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant, String permission, String subject) {
    return jwt().jwt(jwt -> jwt.issuer("https://issuer.example.test").subject(subject)
        .claim("tenant_id", tenant)).authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
