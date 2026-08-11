package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class OrganisationApiIT {
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
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
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
  @MockBean com.acme.hrms.payroll.security.ApprovalAuthorityFacade approvalAuthorityFacade;

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
  void lifecycleIsMakerCheckerClassifiedAuditedAndTenantIsolated()
      throws Exception {
    String legalRequest =
        """
        {
          "code":"ACME_IN",
          "name":"Acme India",
          "countryCode":"IN",
          "currency":"INR",
          "effectiveFrom":"2026-01-01"
        }
        """;

    MvcResult created =
        mvc.perform(
                post("/api/v1/legal-entities")
                    .with(
                        token(
                            TENANT_A,
                            "creator",
                            "organisation.create"))
                    .header(
                        "Idempotency-Key",
                        "create-legal-entity-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(legalRequest))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.approvalStatus").value("DRAFT"))
            .andExpect(
                jsonPath("$.identityStatus")
                    .value("PENDING_APPROVAL"))
            .andExpect(
                jsonPath("$.identityVersionNo").value(0))
            .andReturn();

    JsonNode legal =
        objectMapper.readTree(
            created.getResponse().getContentAsString());
    String legalId = legal.get("identityId").asText();
    String legalVersionId = legal.get("versionId").asText();

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/versions/"
                        + "{versionId}/approval",
                    legalId,
                    legalVersionId)
                .with(
                    token(
                        TENANT_A,
                        "creator",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "self-approve-legal-0001"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value(
                    "urn:problem:organisation:maker-checker"));

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/versions/"
                        + "{versionId}/approval",
                    legalId,
                    legalVersionId)
                .with(
                    token(
                        TENANT_A,
                        "approver",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "approve-legal-version-0001"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(
            jsonPath("$.identityStatus").value("ACTIVE"))
        .andExpect(
            jsonPath("$.identityVersionNo").value(1))
        .andExpect(
            jsonPath("$.createdBy")
                .value(
                    "https://issuer.example.test|creator"))
        .andExpect(
            jsonPath("$.approvedBy")
                .value(
                    "https://issuer.example.test|approver"));

    assertThat(
            countOutbox(
                "LegalEntityVersionApproved",
                legalId,
                "approver"))
        .isEqualTo(1L);
    assertThat(countCompleteApprovalAudit(legalId))
        .isEqualTo(1L);

    MvcResult unitCreated =
        mvc.perform(
                post("/api/v1/payroll-statutory-units")
                    .with(
                        token(
                            TENANT_A,
                            "psu-creator",
                            "organisation.create"))
                    .header(
                        "Idempotency-Key",
                        "create-psu-00000001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"ACME_PSU",
                          "name":"Acme PSU",
                          "parentVersionId":"%s",
                          "responsibilityScope":"PAYROLL_OPERATIONS",
                          "effectiveFrom":"2026-01-01",
                          "effectiveTo":"2027-01-01"
                        }
                        """
                            .formatted(legalVersionId)))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.responsibilityScope")
                    .value("PAYROLL_OPERATIONS"))
            .andReturn();

    JsonNode unit =
        objectMapper.readTree(
            unitCreated.getResponse().getContentAsString());

    mvc.perform(
            post(
                    "/api/v1/payroll-statutory-units/{identityId}/"
                        + "versions/{versionId}/approval",
                    unit.get("identityId").asText(),
                    unit.get("versionId").asText())
                .with(
                    token(
                        TENANT_A,
                        "psu-approver",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "approve-psu-0000001"))
        .andExpect(status().isOk());

    MvcResult establishmentCreated =
        mvc.perform(
                post("/api/v1/establishments")
                    .with(
                        token(
                            TENANT_A,
                            "est-creator",
                            "organisation.create"))
                    .header(
                        "Idempotency-Key",
                        "create-establishment-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"BLR",
                          "name":"Bengaluru",
                          "stateCode":"KA",
                          "establishmentType":"OFFICE",
                          "parentVersionId":"%s",
                          "effectiveFrom":"2026-01-01",
                          "effectiveTo":"2027-01-01"
                        }
                        """
                            .formatted(
                                unit.get("versionId").asText())))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.establishmentType")
                    .value("OFFICE"))
            .andReturn();

    JsonNode establishment =
        objectMapper.readTree(
            establishmentCreated
                .getResponse()
                .getContentAsString());

    mvc.perform(
            post(
                    "/api/v1/establishments/{identityId}/versions/"
                        + "{versionId}/approval",
                    establishment.get("identityId").asText(),
                    establishment.get("versionId").asText())
                .with(
                    token(
                        TENANT_A,
                        "est-approver",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "approve-establishment-0001"))
        .andExpect(status().isOk());

    mvc.perform(
            get("/api/v1/organisation-hierarchy")
                .param("asOf", "2026-07-19")
                .with(
                    token(
                        TENANT_A,
                        "reader",
                        "organisation.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.legalEntities[0].value.code")
                .value("ACME_IN"))
        .andExpect(
            jsonPath(
                    "$.legalEntities[0].children[0].value."
                        + "responsibilityScope")
                .value("PAYROLL_OPERATIONS"))
        .andExpect(
            jsonPath(
                    "$.legalEntities[0].children[0].children[0]."
                        + "value.establishmentType")
                .value("OFFICE"));

    mvc.perform(
            get(
                    "/api/v1/legal-entities/{identityId}/audit",
                    legalId)
                .with(
                    token(
                        TENANT_A,
                        "auditor",
                        "audit.read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$[?(@.action == 'VERSION_APPROVED')]")
                .isNotEmpty());

    mvc.perform(
            get("/api/v1/legal-entities")
                .param("asOf", "2026-07-19")
                .with(
                    token(
                        TENANT_B,
                        "reader",
                        "organisation.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void retirementUsesIdentityConcurrencyAndIsIdempotent()
      throws Exception {
    String request =
        """
        {
          "code":"RETIRE_LE",
          "name":"Retirement Legal Entity",
          "countryCode":"IN",
          "currency":"INR",
          "effectiveFrom":"2026-01-01"
        }
        """;

    MvcResult created =
        mvc.perform(
                post("/api/v1/legal-entities")
                    .with(
                        token(
                            TENANT_A,
                            "retire-creator",
                            "organisation.create"))
                    .header(
                        "Idempotency-Key",
                        "create-retire-legal-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode identity =
        objectMapper.readTree(
            created.getResponse().getContentAsString());
    String identityId = identity.get("identityId").asText();
    String versionId = identity.get("versionId").asText();

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/versions/"
                        + "{versionId}/approval",
                    identityId,
                    versionId)
                .with(
                    token(
                        TENANT_A,
                        "retire-approver",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "approve-retire-legal-0001"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.identityVersionNo").value(1));

    String retirement =
        """
        {
          "effectiveDate":"2028-01-01",
          "reason":"Employer registration closed"
        }
        """;

    MvcResult retired =
        mvc.perform(
                post(
                        "/api/v1/legal-entities/{identityId}/retirement",
                        identityId)
                    .with(
                        token(
                            TENANT_A,
                            "retirer",
                            "organisation.retire"))
                    .header(
                        "Idempotency-Key",
                        "retire-legal-0000001")
                    .header("If-Match", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(retirement))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"2\""))
            .andExpect(
                jsonPath("$.identityStatus")
                    .value("RETIRED"))
            .andExpect(
                jsonPath("$.identityVersionNo").value(2))
            .andExpect(
                jsonPath("$.retirementEffectiveDate")
                    .value("2028-01-01"))
            .andExpect(
                jsonPath("$.retirementReason")
                    .value("Employer registration closed"))
            .andExpect(
                jsonPath("$.effectiveTo")
                    .value("2028-01-01"))
            .andReturn();

    JsonNode retiredBody =
        objectMapper.readTree(
            retired.getResponse().getContentAsString());

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/retirement",
                    identityId)
                .with(
                    token(
                        TENANT_A,
                        "retirer",
                        "organisation.retire"))
                .header(
                    "Idempotency-Key",
                    "retire-legal-0000001")
                .header("If-Match", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(retirement))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.identityId")
                .value(retiredBody.get("identityId").asText()));

    assertThat(
            countOutbox(
                "LegalEntityRetired",
                identityId,
                "retirer"))
        .isEqualTo(1L);

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/versions",
                    identityId)
                .with(
                    token(
                        TENANT_A,
                        "version-creator",
                        "organisation.version.create"))
                .header(
                    "Idempotency-Key",
                    "retired-version-000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"Forbidden future version",
                      "countryCode":"IN",
                      "currency":"INR",
                      "effectiveFrom":"2029-01-01"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("urn:problem:organisation:retired"));
  }

  @Test
  void concurrentVersionAllocationAndApprovalHaveSingleWinners()
      throws Exception {
    MvcResult created =
        mvc.perform(
                post("/api/v1/legal-entities")
                    .with(
                        token(
                            TENANT_A,
                            "concurrent-creator",
                            "organisation.create"))
                    .header(
                        "Idempotency-Key",
                        "concurrent-create-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"CONCURRENT_LE",
                          "name":"Concurrent Legal Entity",
                          "countryCode":"IN",
                          "currency":"INR",
                          "effectiveFrom":"2026-01-01",
                          "effectiveTo":"2028-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode first =
        objectMapper.readTree(
            created.getResponse().getContentAsString());
    String identityId = first.get("identityId").asText();
    String firstVersionId = first.get("versionId").asText();

    mvc.perform(
            post(
                    "/api/v1/legal-entities/{identityId}/versions/"
                        + "{versionId}/approval",
                    identityId,
                    firstVersionId)
                .with(
                    token(
                        TENANT_A,
                        "concurrent-initial-approver",
                        "organisation.approve"))
                .header(
                    "Idempotency-Key",
                    "concurrent-initial-approval-1"))
        .andExpect(status().isOk());

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Callable<MvcResult> addA =
          () -> {
            start.await();
            return mvc.perform(
                    post(
                            "/api/v1/legal-entities/{identityId}/versions",
                            identityId)
                        .with(
                            token(
                                TENANT_A,
                                "concurrent-version-a",
                                "organisation.version.create"))
                        .header(
                            "Idempotency-Key",
                            "concurrent-version-a-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name":"Concurrent 2028",
                              "countryCode":"IN",
                              "currency":"INR",
                              "effectiveFrom":"2028-01-01",
                              "effectiveTo":"2029-01-01"
                            }
                            """))
                .andReturn();
          };
      Callable<MvcResult> addB =
          () -> {
            start.await();
            return mvc.perform(
                    post(
                            "/api/v1/legal-entities/{identityId}/versions",
                            identityId)
                        .with(
                            token(
                                TENANT_A,
                                "concurrent-version-b",
                                "organisation.version.create"))
                        .header(
                            "Idempotency-Key",
                            "concurrent-version-b-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name":"Concurrent 2029",
                              "countryCode":"IN",
                              "currency":"INR",
                              "effectiveFrom":"2029-01-01"
                            }
                            """))
                .andReturn();
          };

      Future<MvcResult> futureA = pool.submit(addA);
      Future<MvcResult> futureB = pool.submit(addB);
      start.countDown();
      MvcResult resultA = futureA.get();
      MvcResult resultB = futureB.get();
      assertThat(resultA.getResponse().getStatus()).isEqualTo(201);
      assertThat(resultB.getResponse().getStatus()).isEqualTo(201);

      MvcResult history =
          mvc.perform(
                  get(
                          "/api/v1/legal-entities/{identityId}/versions",
                          identityId)
                      .with(
                          token(
                              TENANT_A,
                              "concurrent-reader",
                              "organisation.read")))
              .andExpect(status().isOk())
              .andReturn();
      JsonNode versions =
          objectMapper.readTree(
              history.getResponse().getContentAsString());
      List<Integer> sequences =
          java.util.stream.StreamSupport.stream(
                  versions.spliterator(), false)
              .map(node -> node.get("versionSequence").asInt())
              .sorted()
              .toList();
      assertThat(sequences).containsExactly(1, 2, 3);

      JsonNode version2028 =
          java.util.stream.StreamSupport.stream(
                  versions.spliterator(), false)
              .filter(
                  node ->
                      "2028-01-01".equals(
                          node.get("effectiveFrom").asText()))
              .findFirst()
              .orElseThrow();
      String concurrentVersionId =
          version2028.get("versionId").asText();

      CountDownLatch approveStart = new CountDownLatch(1);
      Callable<Integer> approveA =
          () -> {
            approveStart.await();
            return mvc.perform(
                    post(
                            "/api/v1/legal-entities/{identityId}/versions/"
                                + "{versionId}/approval",
                            identityId,
                            concurrentVersionId)
                        .with(
                            token(
                                TENANT_A,
                                "concurrent-approver-a",
                                "organisation.approve"))
                        .header(
                            "Idempotency-Key",
                            "concurrent-approval-a-01"))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Callable<Integer> approveB =
          () -> {
            approveStart.await();
            return mvc.perform(
                    post(
                            "/api/v1/legal-entities/{identityId}/versions/"
                                + "{versionId}/approval",
                            identityId,
                            concurrentVersionId)
                        .with(
                            token(
                                TENANT_A,
                                "concurrent-approver-b",
                                "organisation.approve"))
                        .header(
                            "Idempotency-Key",
                            "concurrent-approval-b-01"))
                .andReturn()
                .getResponse()
                .getStatus();
          };

      Future<Integer> approvalA = pool.submit(approveA);
      Future<Integer> approvalB = pool.submit(approveB);
      approveStart.countDown();
      assertThat(List.of(approvalA.get(), approvalB.get()))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void duplicateAndMalformedCodesHaveStableProblemContracts()
      throws Exception {
    String valid =
        """
        {
          "code":"DUPLICATE_LE",
          "name":"First",
          "countryCode":"IN",
          "currency":"INR",
          "effectiveFrom":"2026-01-01"
        }
        """;

    mvc.perform(
            post("/api/v1/legal-entities")
                .with(
                    token(
                        TENANT_A,
                        "creator-one",
                        "organisation.create"))
                .header(
                    "Idempotency-Key",
                    "duplicate-first-00001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/legal-entities")
                .with(
                    token(
                        TENANT_A,
                        "creator-two",
                        "organisation.create"))
                .header(
                    "Idempotency-Key",
                    "duplicate-second-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    valid.replace(
                        "\"name\":\"First\"",
                        "\"name\":\"Second\"")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("urn:problem:organisation:duplicate"));

    mvc.perform(
            post("/api/v1/legal-entities")
                .with(
                    token(
                        TENANT_A,
                        "creator-three",
                        "organisation.create"))
                .header(
                    "Idempotency-Key",
                    "invalid-code-0000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code":"bad-code",
                      "name":"Invalid",
                      "countryCode":"IN",
                      "currency":"INR",
                      "effectiveFrom":"2026-01-01"
                    }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422));
  }

  @Test
  void missingOrganisationPermissionIsForbidden()
      throws Exception {
    mvc.perform(
            get("/api/v1/organisation-hierarchy")
                .with(
                    token(
                        TENANT_A,
                        "reader",
                        "payroll.read")))
        .andExpect(status().isForbidden());
  }

  private static org.springframework.security.test.web.servlet.request
          .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
      token(
          String tenant,
          String subject,
          String... permissions) {
    GrantedAuthority[] authorities =
        Arrays.stream(permissions)
            .map(SimpleGrantedAuthority::new)
            .toArray(GrantedAuthority[]::new);
    return jwt()
        .jwt(
            jwt ->
                jwt.issuer("https://issuer.example.test")
                    .subject(subject)
                    .claim("tenant_id", tenant))
        .authorities(authorities);
  }


  private static long countCompleteApprovalAudit(
      String identityId) throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT count(*) FROM audit.audit_event "
                    + "WHERE tenant_id='"
                    + TENANT_A
                    + "' AND object_id='"
                    + identityId
                    + "' AND action='VERSION_APPROVED' "
                    + "AND after_state ?& ARRAY["
                    + "'identityId','identityVersionNo','identityStatus',"
                    + "'versionId','versionSequence','versionNo','code',"
                    + "'parentVersionId','effectiveFrom','effectiveTo',"
                    + "'approvalStatus','createdBy','approvedBy',"
                    + "'responsibilityScope','establishmentType',"
                    + "'retirementEffectiveDate','retirementReason',"
                    + "'retiredAt','retiredBy']")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long countOutbox(
      String eventType,
      String aggregateId,
      String actorSubject) throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT count(*) FROM integration.outbox_event "
                    + "WHERE tenant_id='"
                    + TENANT_A
                    + "' AND aggregate_id='"
                    + aggregateId
                    + "' AND event_type='"
                    + eventType
                    + "' AND payload->>'schemaVersion'='1' "
                    + "AND payload->>'actor'="
                    + "'https://issuer.example.test|"
                    + actorSubject
                    + "'")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
