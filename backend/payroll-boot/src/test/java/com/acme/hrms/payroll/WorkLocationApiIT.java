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
class WorkLocationApiIT extends JrfApiITSupport {
  @Test
  void workLocationLifecycleIsSecuredMakerCheckedAndTenantScoped()
      throws Exception {
    EntityRef jurisdiction = createAndApproveJurisdiction("IN_COUNTRY_WL");

    String payload = """
        {
          "code":"BLR_HQ",
          "version":{
            "name":"Bengaluru HQ",
            "payrollJurisdictionId":"%s",
            "payrollJurisdictionVersionId":"%s",
            "addressLine1":"1 Foundation Road",
            "locality":"Bengaluru",
            "stateCode":"KA",
            "postalCode":"560001",
            "countryCode":"IN",
            "effectiveFrom":"2026-01-01"
          }
        }
        """.formatted(jurisdiction.identityId(), jurisdiction.versionId());

    mvc.perform(post("/api/v1/work-locations")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "g02h-work-location-noauth")
            .content(payload))
        .andExpect(status().isUnauthorized());

    mvc.perform(post("/api/v1/work-locations")
            .with(token(TENANT_A, "location-maker", "organisation.read"))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "g02h-work-location-forbidden")
            .content(payload))
        .andExpect(status().isForbidden());

    JsonNode draft = json(mvc.perform(post("/api/v1/work-locations")
            .with(token(TENANT_A, "location-maker", "organisation.create"))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "g02h-work-location-create")
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("BLR_HQ"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn());

    mvc.perform(post(
            "/api/v1/work-locations/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(TENANT_A, "location-maker", "organisation.approve"))
            .header("Idempotency-Key", "g02h-work-location-self-approve")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isForbidden());

    mvc.perform(post(
            "/api/v1/work-locations/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(TENANT_A, "location-approver", "organisation.approve"))
            .header("Idempotency-Key", "g02h-work-location-approve")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.identityStatus").value("ACTIVE"));

    String futurePayload = """
        {
          "name":"Bengaluru HQ future",
          "payrollJurisdictionId":"%s",
          "payrollJurisdictionVersionId":"%s",
          "addressLine1":"1 Foundation Road",
          "locality":"Bengaluru",
          "stateCode":"KA",
          "postalCode":"560001",
          "countryCode":"IN",
          "effectiveFrom":"2027-01-01"
        }
        """.formatted(
            jurisdiction.identityId(),
            jurisdiction.versionId());

    mvc.perform(
            post(
                    "/api/v1/work-locations/{identityId}/versions",
                    draft.path("identityId").asText())
                .with(
                    token(
                        TENANT_A,
                        "location-version-maker",
                        "organisation.version.create"))
                .header(
                    "Idempotency-Key",
                    "g02h-work-location-future-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content(futurePayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.versionSequence").value(2))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"));

    mvc.perform(get("/api/v1/work-locations")
            .with(token(TENANT_A, "reader-a", "organisation.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("BLR_HQ"))
        .andExpect(
            jsonPath("$[0].versionId")
                .value(draft.path("versionId").asText()));

    mvc.perform(get("/api/v1/work-locations")
            .with(token(TENANT_B, "reader-b", "organisation.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    assertThat(tenantCount("organisation.work_location", TENANT_A)).isEqualTo(1L);
    assertThat(tenantCount("organisation.work_location", TENANT_B)).isZero();
  }
}

abstract class JrfApiITSupport {
  static final String APP_PASSWORD = "synthetic-jrf-app-password";
  static final String MIGRATOR_PASSWORD = "synthetic-jrf-migrator-password";
  static final String TENANT_A = "00000000-0000-0000-0000-00000000000a";
  static final String TENANT_B = "00000000-0000-0000-0000-00000000000b";
  static final String LEGAL_ID = "51000000-0000-0000-0000-000000000001";
  static final String LEGAL_VERSION_ID = "51100000-0000-0000-0000-000000000001";

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
              + "NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
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
  @MockBean com.acme.hrms.payroll.security.ApprovalAuthorityFacade approvalAuthorityFacade;

  @BeforeEach
  void seedFoundationDependencies() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
      statement.execute("SET ROLE payroll_owner");
      statement.execute("SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
      statement.execute(
          """
          INSERT INTO organisation.legal_entity(
            id,tenant_id,code,status,created_by,updated_by
          ) VALUES ('%s','%s','JRF_LEGAL','ACTIVE','test','test')
          """.formatted(LEGAL_ID, TENANT_A));
      statement.execute(
          """
          INSERT INTO organisation.legal_entity_version(
            id,tenant_id,legal_entity_id,version_sequence,name,
            country_code,currency,effective_from,effective_to,
            approval_status,approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s',1,'JRF Legal Entity','IN','INR',
            '2026-01-01',NULL,'APPROVED',clock_timestamp(),
            'test-approver','test-maker','test-maker'
          )
          """.formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));
      statement.execute("RESET ROLE");
    }
  }

  protected EntityRef createAndApproveJurisdiction(String code)
      throws Exception {
    String payload = """
        {
          "code":"%s",
          "version":{
            "name":"%s jurisdiction",
            "countryCode":"IN",
            "levelCode":"COUNTRY",
            "levelRank":1,
            "effectiveFrom":"2026-01-01"
          }
        }
        """.formatted(code, code);
    JsonNode draft = json(mvc.perform(post("/api/v1/payroll-jurisdictions")
            .with(token(TENANT_A, "jurisdiction-maker", "organisation.create"))
            .header("Idempotency-Key", code + "-create-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn());
    JsonNode approved = json(mvc.perform(post(
            "/api/v1/payroll-jurisdictions/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(TENANT_A, "jurisdiction-approver", "organisation.approve"))
            .header("Idempotency-Key", code + "-approve-0001")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isOk())
        .andReturn());
    return new EntityRef(
        approved.path("identityId").asText(),
        approved.path("versionId").asText(),
        approved.path("versionNo").asLong());
  }

  protected EntityRef createAndApproveRegistrationType(String code)
      throws Exception {
    return createAndApproveRegistrationType(code, null);
  }

  protected EntityRef createAndApproveRegistrationType(
      String code,
      EntityRef parentType)
      throws Exception {
    String parentTypeJson =
        parentType == null
            ? "null"
            : "\"" + parentType.identityId() + "\"";
    String payload = """
        {
          "code":"%s",
          "version":{
            "name":"%s registration",
            "obligationCode":"GENERIC_OBLIGATION",
            "authorityCode":"GENERIC_AUTHORITY",
            "jurisdictionLevelCode":"COUNTRY",
            "identifierPattern":"^[A-Z0-9-]{3,30}$",
            "identifierCasePolicy":"UPPER",
            "parentRequired":%s,
            "parentRegistrationTypeId":%s,
            "ownerKinds":["LEGAL_ENTITY"],
            "effectiveFrom":"2026-01-01"
          }
        }
        """.formatted(
            code,
            code,
            parentType != null,
            parentTypeJson);
    JsonNode draft = json(mvc.perform(post("/api/v1/statutory-registration-types")
            .with(token(
                TENANT_A,
                "type-maker",
                "statutory-registration-type.write"))
            .header("Idempotency-Key", code + "-type-create-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn());
    JsonNode approved = json(mvc.perform(post(
            "/api/v1/statutory-registration-types/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(
                TENANT_A,
                "type-approver",
                "statutory-registration.approve"))
            .header("Idempotency-Key", code + "-type-approve-0001")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isOk())
        .andReturn());
    return new EntityRef(
        approved.path("identityId").asText(),
        approved.path("versionId").asText(),
        approved.path("versionNo").asLong());
  }

  protected ActiveRegistration createActiveRegistration(
      String referenceCode,
      EntityRef jurisdiction,
      EntityRef type) throws Exception {
    return createActiveRegistration(
        referenceCode,
        jurisdiction,
        type,
        null);
  }

  protected ActiveRegistration createActiveRegistration(
      String referenceCode,
      EntityRef jurisdiction,
      EntityRef type,
      ActiveRegistration parent) throws Exception {
    String parentIdentityJson =
        parent == null
            ? "null"
            : "\"" + parent.identityId() + "\"";
    String parentVersionJson =
        parent == null
            ? "null"
            : "\"" + parent.versionId() + "\"";
    String payload = """
        {
          "registrationTypeId":"%s",
          "referenceCode":"%s",
          "version":{
            "registrationTypeId":"%s",
            "registrationTypeVersionId":"%s",
            "identifier":"abc-123",
            "ownerKind":"LEGAL_ENTITY",
            "ownerId":"%s",
            "payrollJurisdictionId":"%s",
            "payrollJurisdictionVersionId":"%s",
            "parentRegistrationId":%s,
            "parentRegistrationVersionId":%s,
            "effectiveFrom":"2026-01-01"
          }
        }
        """.formatted(
            type.identityId(),
            referenceCode,
            type.identityId(),
            type.versionId(),
            LEGAL_ID,
            jurisdiction.identityId(),
            jurisdiction.versionId(),
            parentIdentityJson,
            parentVersionJson);

    JsonNode current = json(mvc.perform(post("/api/v1/statutory-registrations")
            .with(token(
                TENANT_A,
                "registration-maker",
                "statutory-registration.write"))
            .header("Idempotency-Key", referenceCode + "-create-0001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andReturn());

    current = transition(
        current,
        "submission",
        "registration-maker",
        "statutory-registration.write",
        referenceCode + "-submit-0001",
        null);
    current = transition(
        current,
        "verification",
        "registration-verifier",
        "statutory-registration.verify",
        referenceCode + "-verify-0001",
        "{\"evidenceRef\":\"verification-evidence-001\"}");
    current = transition(
        current,
        "approval-request",
        "registration-verifier",
        "statutory-registration.verify",
        referenceCode + "-approval-request-0001",
        null);
    current = transition(
        current,
        "approval",
        "registration-approver",
        "statutory-registration.approve",
        referenceCode + "-approval-0001",
        "{\"evidenceRef\":\"approval-evidence-001\"}");

    return new ActiveRegistration(
        current.path("identityId").asText(),
        current.path("versionId").asText(),
        current.path("versionNo").asLong(),
        current.path("identifierNormalized").asText());
  }

  private JsonNode transition(
      JsonNode current,
      String action,
      String subject,
      String permission,
      String key,
      String body) throws Exception {
    var request = post(
            "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/" + action,
            current.path("identityId").asText(),
            current.path("versionId").asText())
        .with(token(TENANT_A, subject, permission))
        .header("Idempotency-Key", key)
        .header("If-Match", current.path("versionNo").asText());
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return json(mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn());
  }

  protected JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  protected static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant,
      String subject,
      String... permissions) {
    var authorities = Arrays.stream(permissions)
        .map(SimpleGrantedAuthority::new)
        .toArray(SimpleGrantedAuthority[]::new);
    return jwt().jwt(jwt -> jwt
        .issuer("https://issuer.example.test")
        .subject(subject)
        .claim("tenant_id", tenant))
        .authorities(authorities);
  }

  protected static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  protected long tenantCount(String tableName, String tenant) throws Exception {
    if (!tableName.matches("[a-z_]+\\.[a-z_]+")) {
      throw new IllegalArgumentException("Unsafe table name");
    }
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "select count(*) from " + tableName + " where tenant_id='" + tenant + "'")) {
      result.next();
      return result.getLong(1);
    }
  }

  protected record EntityRef(String identityId, String versionId, long versionNo) {}

  protected record ActiveRegistration(
      String identityId,
      String versionId,
      long versionNo,
      String identifierNormalized) {}
}
