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
class OrganisationApiIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT_A = "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B = "00000000-0000-0000-0000-00000000000b";
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
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
  void seedTenants() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute("INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
          + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
          + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void securedLifecycleIsIdempotentEffectiveDatedAuditedAndTenantIsolated() throws Exception {
    String idempotencyKey = "create-legal-entity-0001";
    String request = "{\"code\":\"ACME_IN\",\"name\":\"Acme India\",\"countryCode\":\"IN\",\"currency\":\"INR\",\"effectiveFrom\":\"2026-01-01\"}";
    MvcResult created = mvc.perform(post("/api/v1/legal-entities")
            .with(token(TENANT_A, "organisation.create"))
            .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.approvalStatus").value("DRAFT")).andReturn();
    JsonNode first = objectMapper.readTree(created.getResponse().getContentAsString());

    MvcResult replay = mvc.perform(post("/api/v1/legal-entities")
            .with(token(TENANT_A, "organisation.create"))
            .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isCreated()).andReturn();
    JsonNode second = objectMapper.readTree(replay.getResponse().getContentAsString());
    assertThat(second.get("identityId").asText()).isEqualTo(first.get("identityId").asText());

    String identityId = first.get("identityId").asText();
    String versionId = first.get("versionId").asText();
    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token(TENANT_A, "organisation.approve")).header("Idempotency-Key", "approve-legal-version-0001"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

    MvcResult unitCreated = mvc.perform(post("/api/v1/payroll-statutory-units")
            .with(token(TENANT_A, "organisation.create")).header("Idempotency-Key", "create-psu-00000001")
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ACME_PSU\",\"name\":\"Acme PSU\",\"parentVersionId\":\""
                + versionId + "\",\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":\"2027-01-01\"}"))
        .andExpect(status().isCreated()).andReturn();
    JsonNode unit = objectMapper.readTree(unitCreated.getResponse().getContentAsString());
    mvc.perform(post("/api/v1/payroll-statutory-units/{identityId}/versions/{versionId}/approval",
            unit.get("identityId").asText(), unit.get("versionId").asText())
            .with(token(TENANT_A, "organisation.approve")).header("Idempotency-Key", "approve-psu-0000001"))
        .andExpect(status().isOk());

    MvcResult establishmentCreated = mvc.perform(post("/api/v1/establishments")
            .with(token(TENANT_A, "organisation.create")).header("Idempotency-Key", "create-establishment-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"BLR\",\"name\":\"Bengaluru\",\"stateCode\":\"KA\",\"parentVersionId\":\""
                + unit.get("versionId").asText() + "\",\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":\"2027-01-01\"}"))
        .andExpect(status().isCreated()).andReturn();
    JsonNode establishment = objectMapper.readTree(establishmentCreated.getResponse().getContentAsString());
    mvc.perform(post("/api/v1/establishments/{identityId}/versions/{versionId}/approval",
            establishment.get("identityId").asText(), establishment.get("versionId").asText())
            .with(token(TENANT_A, "organisation.approve"))
            .header("Idempotency-Key", "00000000-0000-4000-8000-000000000013"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/legal-entities/{identityId}", identityId)
            .param("asOf", "2026-07-19").with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value(versionId));
    mvc.perform(get("/api/v1/legal-entities/{identityId}/versions", identityId)
            .with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].approvalStatus").value("APPROVED"));
    mvc.perform(get("/api/v1/organisation-hierarchy").param("asOf", "2026-07-19")
            .with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.legalEntities[0].value.code").value("ACME_IN"))
        .andExpect(jsonPath("$.legalEntities[0].children[0].value.code").value("ACME_PSU"))
        .andExpect(jsonPath("$.legalEntities[0].children[0].children[0].value.code").value("BLR"));

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/end-date", identityId, versionId)
            .with(token(TENANT_A, "organisation.version.end-date"))
            .header("Idempotency-Key", "end-date-legal-0001").header("If-Match", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"2027-01-01\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveTo").value("2027-01-01"));
    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/end-date", identityId, versionId)
            .with(token(TENANT_A, "organisation.version.end-date"))
            .header("Idempotency-Key", "end-date-stale-000001").header("If-Match", "1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"2027-02-01\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));

    MvcResult futureCreated = mvc.perform(post("/api/v1/legal-entities/{identityId}/versions", identityId)
            .with(token(TENANT_A, "organisation.version.create")).header("Idempotency-Key", "future-legal-version-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme India Future\",\"countryCode\":\"IN\",\"currency\":\"INR\",\"effectiveFrom\":\"2027-01-01\"}"))
        .andExpect(status().isCreated()).andReturn();
    String futureVersionId = objectMapper.readTree(futureCreated.getResponse().getContentAsString()).get("versionId").asText();
    MvcResult corrected = mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/corrections", identityId, futureVersionId)
            .with(token(TENANT_A, "organisation.version.correct")).header("Idempotency-Key", "correct-future-version-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme India 2027\",\"countryCode\":\"IN\",\"currency\":\"INR\",\"effectiveFrom\":\"2027-01-01\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.supersedesVersionId").value(futureVersionId)).andReturn();
    String correctedVersionId = objectMapper.readTree(corrected.getResponse().getContentAsString()).get("versionId").asText();
    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, correctedVersionId)
            .with(token(TENANT_A, "organisation.approve")).header("Idempotency-Key", "approve-corrected-0001"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/legal-entities/{identityId}", identityId).param("asOf", "2027-07-19")
            .with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme India 2027"));
    mvc.perform(get("/api/v1/legal-entities/{identityId}/audit", identityId)
            .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].actor").value("https://issuer.example.test|synthetic-subject"))
        .andExpect(jsonPath("$[5].action").value("VERSION_APPROVED"));

    mvc.perform(get("/api/v1/legal-entities").param("asOf", "2026-07-19")
            .with(token(TENANT_B, "organisation.read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void missingOrganisationPermissionIsForbidden() throws Exception {
    mvc.perform(get("/api/v1/organisation-hierarchy").with(token(TENANT_A, "payroll.read")))
        .andExpect(status().isForbidden());
  }

  private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant, String permission) {
    return jwt().jwt(jwt -> jwt.issuer("https://issuer.example.test").subject("synthetic-subject")
        .claim("tenant_id", tenant)).authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
