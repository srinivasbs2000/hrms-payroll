package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
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
class ApprovalAuthorityEnforcementApiIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT_A = "00000000-0000-0000-0000-00000000000a";
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  static {
    POSTGRES.start();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
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
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "https://issuer.example.test");
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "https://issuer.example.test/jwks");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean JwtDecoder jwtDecoder;

  @BeforeEach
  void reset() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test')");
    }
  }

  @Test
  void endpointPermissionAndSharedAuthorityAreBothRequiredAndDecisionIsAudited() throws Exception {
    JsonNode draft = createLegalEntity("FAD_AND", "maker-and");
    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token("approver-and", false, "organisation.approve"))
            .header("Idempotency-Key", "fad-permission-only"))
        .andExpect(status().isForbidden());

    UUID authorityId = seedAuthority(identityId, "approver-and");

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token("approver-and", false))
            .header("Idempotency-Key", "fad-authority-only"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token("approver-and", false, "organisation.approve"))
            .header("Idempotency-Key", "fad-both"))
        .andExpect(status().isOk());

    try (Connection connection = admin(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery(
             "SELECT after_state->>'authorityId', after_state->>'delegationId' "
                 + "FROM audit.audit_event WHERE object_type='APPLICATION_APPROVAL_DECISION' "
                 + "AND actor='https://issuer.example.test|approver-and'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo(authorityId.toString());
      assertThat(result.getString(2)).isNull();
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void delegatedApprovalPreservesAuthorityAndDelegationIds() throws Exception {
    JsonNode draft = createLegalEntity("FAD_DEL", "maker-del");
    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();
    UUID authorityId = seedAuthority(identityId, "delegator");
    UUID delegationId = seedDelegation(authorityId, "delegator", "delegate");

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token("delegate", false, "organisation.approve"))
            .header("Idempotency-Key", "fad-delegated"))
        .andExpect(status().isOk());

    try (Connection connection = admin(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery(
             "SELECT after_state->>'authorityId', after_state->>'delegationId', after_state->>'sourceActorId' "
                 + "FROM audit.audit_event WHERE object_type='APPLICATION_APPROVAL_DECISION' "
                 + "AND actor='https://issuer.example.test|delegate'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isEqualTo(authorityId.toString());
      assertThat(result.getString(2)).isEqualTo(delegationId.toString());
      assertThat(result.getString(3)).isEqualTo("https://issuer.example.test|delegator");
    }
  }

  @Test
  void keycloakServiceAccountCannotExerciseFinalApprovalEvenWithPermissionAndAuthority() throws Exception {
    JsonNode draft = createLegalEntity("FAD_SVC", "maker-svc");
    String identityId = draft.path("identityId").asText();
    String versionId = draft.path("versionId").asText();
    seedAuthority(identityId, "service-subject");

    mvc.perform(post("/api/v1/legal-entities/{identityId}/versions/{versionId}/approval", identityId, versionId)
            .with(token("service-subject", true, "organisation.approve"))
            .header("Idempotency-Key", "fad-service-account"))
        .andExpect(status().isForbidden());

    assertThat(decisionCount("https://issuer.example.test|service-subject")).isZero();
  }

  private JsonNode createLegalEntity(String code, String maker) throws Exception {
    MvcResult result =
        mvc.perform(post("/api/v1/legal-entities")
                .with(token(maker, false, "organisation.create"))
                .header("Idempotency-Key", "create-" + code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code":"%s",
                      "name":"%s Legal Entity",
                      "countryCode":"IN",
                      "currency":"INR",
                      "effectiveFrom":"2026-01-01"
                    }
                    """.formatted(code, code)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private UUID seedAuthority(String ownerId, String subject) throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = app(); Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
      statement.execute("""
          INSERT INTO security.approval_authority_assignment(
            id,tenant_id,owner_kind,owner_id,approval_role,domain_code,action_code,
            actor_id,effective_from,effective_to,created_by,updated_by
          ) VALUES (
            '%s','%s','LEGAL_ENTITY','%s','FINAL_APPROVER','ORGANISATION_CONFIG','APPROVE',
            'https://issuer.example.test|%s',DATE '2026-01-01',NULL,'test-admin','test-admin'
          )
          """.formatted(id, TENANT_A, ownerId, subject));
      connection.commit();
    }
    return id;
  }

  private UUID seedDelegation(UUID authorityId, String delegator, String delegate) throws Exception {
    UUID id = UUID.randomUUID();
    try (Connection connection = app(); Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
      statement.execute("""
          INSERT INTO security.approval_delegation(
            id,tenant_id,source_authority_id,delegator_actor_id,delegate_actor_id,
            effective_from,effective_to,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','https://issuer.example.test|%s','https://issuer.example.test|%s',
            DATE '2026-01-01',DATE '2027-01-01','test-admin','test-admin'
          )
          """.formatted(id, TENANT_A, authorityId, delegator, delegate));
      connection.commit();
    }
    return id;
  }

  private long decisionCount(String actor) throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery(
             "SELECT count(*) FROM audit.audit_event WHERE object_type='APPLICATION_APPROVAL_DECISION' "
                 + "AND actor='" + actor.replace("'", "''") + "'")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String subject, boolean serviceAccount, String... permissions) {
    var authorities = Arrays.stream(permissions)
        .map(SimpleGrantedAuthority::new)
        .toArray(SimpleGrantedAuthority[]::new);
    return jwt().jwt(builder -> {
      builder.issuer("https://issuer.example.test").subject(subject).claim("tenant_id", TENANT_A);
      if (serviceAccount) {
        builder.claim("client_id", "payroll-service-client");
      }
    }).authorities(authorities);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}
