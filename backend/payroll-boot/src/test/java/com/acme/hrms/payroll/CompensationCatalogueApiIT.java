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
class CompensationCatalogueApiIT {
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
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
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
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void namedBaseAndExactMembershipLifecycleIsTenantSafeAndAudited()
      throws Exception {
    JsonNode component = createAndApproveComponent();

    String baseRequest = """
        {
          "code":"REGULAR_GROSS",
          "name":"Regular Gross",
          "ownershipScope":"TENANT",
          "countryCode":"IN",
          "version":{
            "baseCategory":"CALCULATION",
            "aggregationMethod":"SUM",
            "description":"Approved recurring cash earnings",
            "effectiveFrom":"2027-01-01"
          }
        }
        """;

    MvcResult baseCreated = mvc.perform(post("/api/v1/payroll-bases")
            .with(token(TENANT_A, "base-maker", "compensation.base.create"))
            .header("Idempotency-Key", "create-regular-gross")
            .contentType(MediaType.APPLICATION_JSON)
            .content(baseRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn();
    JsonNode base = objectMapper.readTree(
        baseCreated.getResponse().getContentAsString());

    mvc.perform(post(
                "/api/v1/payroll-bases/{identityId}/versions/{versionId}/approval",
                base.get("identityId").asText(),
                base.get("versionId").asText())
            .with(token(TENANT_A, "base-checker", "compensation.base.approve"))
            .header("Idempotency-Key", "approve-regular-gross"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));

    String membership = """
        {
          "payrollBaseVersionId":"%s",
          "componentId":"%s",
          "componentVersionId":"%s",
          "membershipType":"INCLUDE",
          "inclusionPercent":"100.00000000",
          "effectiveFrom":"2027-01-01"
        }
        """.formatted(
            base.get("versionId").asText(),
            component.get("identityId").asText(),
            component.get("versionId").asText());

    MvcResult membershipCreated = mvc.perform(post(
                "/api/v1/payroll-bases/{identityId}/memberships",
                base.get("identityId").asText())
            .with(token(
                TENANT_A,
                "membership-maker",
                "compensation.base.membership.create"))
            .header("Idempotency-Key", "create-basic-regular-gross")
            .contentType(MediaType.APPLICATION_JSON)
            .content(membership))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.inclusionPercent").value("100.00000000"))
        .andReturn();
    JsonNode membershipView = objectMapper.readTree(
        membershipCreated.getResponse().getContentAsString());

    mvc.perform(post(
                "/api/v1/payroll-bases/{identityId}/memberships/{membershipId}/approval",
                base.get("identityId").asText(),
                membershipView.get("membershipId").asText())
            .with(token(
                TENANT_A,
                "membership-maker",
                "compensation.base.membership.approve"))
            .header("Idempotency-Key", "reject-own-membership-approval"))
        .andExpect(status().isConflict());

    mvc.perform(post(
                "/api/v1/payroll-bases/{identityId}/memberships/{membershipId}/approval",
                base.get("identityId").asText(),
                membershipView.get("membershipId").asText())
            .with(token(
                TENANT_A,
                "membership-checker",
                "compensation.base.membership.approve"))
            .header("Idempotency-Key", "approve-basic-regular-gross"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

    mvc.perform(get(
                "/api/v1/payroll-bases/{identityId}/memberships",
                base.get("identityId").asText())
            .param("asOf", "2027-02-01")
            .with(token(TENANT_A, "reader", "compensation.base.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].componentCode").value("BASIC"))
        .andExpect(jsonPath("$[0].membershipType").value("INCLUDE"))
        .andExpect(jsonPath("$[0].inclusionPercent").value("100.00000000"));

    mvc.perform(get(
                "/api/v1/payroll-bases/{identityId}/audit",
                base.get("identityId").asText())
            .with(token(TENANT_A, "auditor", "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action").value("VERSION_APPROVED"))
        .andExpect(jsonPath("$[2].action").value("MEMBERSHIP_CREATED"))
        .andExpect(jsonPath("$[3].action").value("MEMBERSHIP_APPROVED"));

    mvc.perform(get("/api/v1/payroll-bases")
            .param("asOf", "2027-02-01")
            .with(token(TENANT_B, "reader", "compensation.base.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private JsonNode createAndApproveComponent() throws Exception {
    String payload = """
        {"code":"BASIC","name":"Basic Pay","componentType":"EARNING",
         "version":{"formulaType":"FIXED","fixedAmount":50000,"componentCategory":"CASH_EARNING",
         "componentSubcategory":"BASIC_PAY","cashImpact":"INCREASE","payeeType":"EMPLOYEE",
         "paymentChannel":"PAYROLL_BANK","settlementTiming":"CURRENT_PERIOD",
         "payslipVisibility":"SHOW","zeroValueVisibility":"SUPPRESS",
         "negativeValuePolicy":"PROHIBIT","frequency":"MONTHLY","valueNature":"FIXED",
         "amountRepresentation":"MONTHLY_AMOUNT","taxTreatment":"DELEGATED",
         "payrollTiming":"REGULAR","effectiveFrom":"2027-01-01"}}
        """;
    MvcResult created = mvc.perform(post("/api/v1/pay-components")
            .with(token(TENANT_A, "component-maker", "compensation.component.create"))
            .header("Idempotency-Key", "create-basic-for-base")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode component = objectMapper.readTree(
        created.getResponse().getContentAsString());
    mvc.perform(post(
                "/api/v1/pay-components/{identityId}/versions/{versionId}/approval",
                component.get("identityId").asText(),
                component.get("versionId").asText())
            .with(token(TENANT_A, "component-checker", "compensation.component.approve"))
            .header("Idempotency-Key", "approve-basic-for-base"))
        .andExpect(status().isOk());
    return component;
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
          String tenant, String subject, String permission) {
    return jwt().jwt(value -> value
            .issuer("https://issuer.example.test")
            .subject(subject)
            .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
