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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class EmployeePayrollApiIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";

  private static final String LEGAL_ID =
      "21000000-0000-0000-0000-000000000001";
  private static final String LEGAL_VERSION_ID =
      "21100000-0000-0000-0000-000000000001";
  private static final String PSU_ID =
      "22000000-0000-0000-0000-000000000001";
  private static final String PSU_VERSION_ID =
      "22100000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_ID =
      "23000000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_VERSION_ID =
      "23100000-0000-0000-0000-000000000001";
  private static final String CALENDAR_ID =
      "24000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_ID =
      "25000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_VERSION_ID =
      "25100000-0000-0000-0000-000000000001";
  private static final String COMPONENT_ID =
      "26000000-0000-0000-0000-000000000001";
  private static final String COMPONENT_VERSION_ID =
      "26100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_ID =
      "27000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_VERSION_ID =
      "27100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_LINE_ID =
      "27200000-0000-0000-0000-000000000001";

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
  void seedApprovedDependencies() throws Exception {
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
      seedPayGroup(statement);
      seedSalaryStructure(statement);
      statement.execute("RESET ROLE");
    }
  }

  @Test
  void completeLifecycleIsIdempotentAuditedReadyAndTenantIsolated()
      throws Exception {
    String relationshipRequest =
        """
        {
          "externalEmployeeId":"EMP-EXT-001",
          "employeeNumber":"EMP-001",
          "legalEntityVersionId":"%s",
          "relationshipStart":"2026-01-01",
          "relationshipEnd":"2029-01-01"
        }
        """.formatted(LEGAL_VERSION_ID);

    MvcResult relationshipCreated = mvc.perform(
            post("/api/v1/payroll-relationships")
                .with(token(
                    TENANT_A,
                    "employee-payroll.relationship.create"))
                .header("Idempotency-Key", "relationship-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(relationshipRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.versionNo").value(0))
        .andReturn();
    JsonNode relationship = objectMapper.readTree(
        relationshipCreated.getResponse().getContentAsString());

    MvcResult relationshipReplay = mvc.perform(
            post("/api/v1/payroll-relationships")
                .with(token(
                    TENANT_A,
                    "employee-payroll.relationship.create"))
                .header("Idempotency-Key", "relationship-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(relationshipRequest))
        .andExpect(status().isCreated())
        .andReturn();
    assertThat(objectMapper.readTree(
            relationshipReplay.getResponse().getContentAsString())
        .get("identityId").asText())
        .isEqualTo(relationship.get("identityId").asText());

    String relationshipId = relationship.get("identityId").asText();
    String relationshipVersionId = relationship.get("versionId").asText();
    approveRelationship(relationshipId, relationshipVersionId);

    String assignmentRequest =
        """
        {
          "payrollRelationshipId":"%s",
          "assignmentNumber":"ASN-001",
          "payrollRelationshipVersionId":"%s",
          "establishmentVersionId":"%s",
          "assignmentStart":"2026-01-01",
          "assignmentEnd":"2029-01-01"
        }
        """.formatted(
            relationshipId,
            relationshipVersionId,
            ESTABLISHMENT_VERSION_ID);
    MvcResult assignmentCreated = mvc.perform(
            post("/api/v1/payroll-assignments")
                .with(token(
                    TENANT_A,
                    "employee-payroll.assignment.create"))
                .header("Idempotency-Key", "assignment-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn();
    JsonNode assignment = objectMapper.readTree(
        assignmentCreated.getResponse().getContentAsString());
    String assignmentId = assignment.get("identityId").asText();
    String assignmentVersionId = assignment.get("versionId").asText();
    approveAssignment(assignmentId, assignmentVersionId);

    MvcResult profileCreated = mvc.perform(
            post("/api/v1/employee-payroll-profiles")
                .with(token(
                    TENANT_A,
                    "employee-payroll.profile.create"))
                .header("Idempotency-Key", "profile-create-0000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"payrollRelationshipId\":\""
                        + relationshipId
                        + "\",\"currency\":\"INR\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payrollStatus").value("INCOMPLETE"))
        .andExpect(jsonPath("$.versionNo").value(0))
        .andReturn();
    String profileId = objectMapper.readTree(
        profileCreated.getResponse().getContentAsString())
        .get("id").asText();

    MvcResult groupAssignmentCreated = mvc.perform(
            post("/api/v1/pay-group-assignments")
                .with(token(
                    TENANT_A,
                    "employee-payroll.pay-group-assignment.create"))
                .header("Idempotency-Key", "group-assignment-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "payrollAssignmentVersionId":"%s",
                      "payGroupVersionId":"%s",
                      "effectiveFrom":"2026-01-01",
                      "effectiveTo":"2029-01-01"
                    }
                    """.formatted(
                        assignmentVersionId,
                        PAY_GROUP_VERSION_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn();
    String groupAssignmentId = objectMapper.readTree(
        groupAssignmentCreated.getResponse().getContentAsString())
        .get("id").asText();
    approvePayGroupAssignment(groupAssignmentId);

    MvcResult salaryAssignmentCreated = mvc.perform(
            post("/api/v1/salary-assignments")
                .with(token(
                    TENANT_A,
                    "employee-payroll.salary-assignment.create"))
                .header("Idempotency-Key", "salary-assignment-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "payrollAssignmentVersionId":"%s",
                      "salaryStructureVersionId":"%s",
                      "monthlyAmount":75000.0000,
                      "currency":"INR",
                      "effectiveFrom":"2026-01-01",
                      "effectiveTo":"2029-01-01"
                    }
                    """.formatted(
                        assignmentVersionId,
                        STRUCTURE_VERSION_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn();
    String salaryAssignmentId = objectMapper.readTree(
        salaryAssignmentCreated.getResponse().getContentAsString())
        .get("id").asText();
    approveSalaryAssignment(salaryAssignmentId);

    mvc.perform(
            post("/api/v1/employee-payroll-profiles/{profileId}/status", profileId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.profile.status.update"))
                .header("Idempotency-Key", "profile-ready-00000001")
                .header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payrollStatus\":\"READY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payrollStatus").value("READY"))
        .andExpect(jsonPath("$.versionNo").value(1));

    mvc.perform(
            get("/api/v1/payroll-relationships/{relationshipId}/profile",
                relationshipId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.profile.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(profileId))
        .andExpect(jsonPath("$.payrollStatus").value("READY"));

    mvc.perform(
            get("/api/v1/payroll-assignments")
                .param("payrollRelationshipId", relationshipId)
                .param("asOf", "2026-07-23")
                .with(token(
                    TENANT_A,
                    "employee-payroll.assignment.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].identityId").value(assignmentId));

    mvc.perform(
            get("/api/v1/pay-group-assignments")
                .param("payrollAssignmentVersionId", assignmentVersionId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.pay-group-assignment.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(groupAssignmentId));

    mvc.perform(
            get("/api/v1/salary-assignments")
                .param("payrollAssignmentVersionId", assignmentVersionId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.salary-assignment.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(salaryAssignmentId));

    mvc.perform(
            get("/api/v1/payroll-relationships/{identityId}/audit", relationshipId)
                .with(token(TENANT_A, "audit.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[1].action").value("VERSION_APPROVED"));

    mvc.perform(
            get("/api/v1/payroll-relationships")
                .param("asOf", "2026-07-23")
                .with(token(
                    TENANT_B,
                    "employee-payroll.relationship.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mvc.perform(
            post("/api/v1/employee-payroll-profiles/{profileId}/status", profileId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.profile.status.update"))
                .header("Idempotency-Key", "profile-stale-00000001")
                .header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payrollStatus\":\"ON_HOLD\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void idempotencyKeyReuseWithDifferentRelationshipPayloadIsConflict()
      throws Exception {
    String first =
        relationshipRequest("EMP-EXT-002", "EMP-002");
    String changed =
        relationshipRequest("EMP-EXT-002", "EMP-CHANGED");

    mvc.perform(
            post("/api/v1/payroll-relationships")
                .with(token(
                    TENANT_A,
                    "employee-payroll.relationship.create"))
                .header("Idempotency-Key", "relationship-different-payload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(first))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/payroll-relationships")
                .with(token(
                    TENANT_A,
                    "employee-payroll.relationship.create"))
                .header("Idempotency-Key", "relationship-different-payload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changed))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void missingEmployeePayrollPermissionIsForbidden() throws Exception {
    mvc.perform(
            get("/api/v1/payroll-relationships")
                .with(token(TENANT_A, "organisation.read")))
        .andExpect(status().isForbidden());
  }

  private void approveRelationship(
      String relationshipId, String relationshipVersionId) throws Exception {
    mvc.perform(
            post(
                "/api/v1/payroll-relationships/{identityId}/versions/"
                    + "{versionId}/approval",
                relationshipId,
                relationshipVersionId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.relationship.approve"))
                .header("Idempotency-Key", "test-test-test-one"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.versionNo").value(1));
  }

  private void approveAssignment(
      String assignmentId, String assignmentVersionId) throws Exception {
    mvc.perform(
            post(
                "/api/v1/payroll-assignments/{identityId}/versions/"
                    + "{versionId}/approval",
                assignmentId,
                assignmentVersionId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.assignment.approve"))
                .header("Idempotency-Key", "test-test-test-two"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
  }

  private void approvePayGroupAssignment(String assignmentId)
      throws Exception {
    mvc.perform(
            post("/api/v1/pay-group-assignments/{assignmentId}/approval",
                assignmentId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.pay-group-assignment.approve"))
                .header("Idempotency-Key", "test-test-test-three"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
  }

  private void approveSalaryAssignment(String assignmentId)
      throws Exception {
    mvc.perform(
            post("/api/v1/salary-assignments/{assignmentId}/approval",
                assignmentId)
                .with(token(
                    TENANT_A,
                    "employee-payroll.salary-assignment.approve"))
                .header("Idempotency-Key", "test-test-test-four"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
  }

  private String relationshipRequest(
      String externalEmployeeId, String employeeNumber) {
    return """
        {
          "externalEmployeeId":"%s",
          "employeeNumber":"%s",
          "legalEntityVersionId":"%s",
          "relationshipStart":"2026-01-01",
          "relationshipEnd":"2029-01-01"
        }
        """.formatted(
            externalEmployeeId,
            employeeNumber,
            LEGAL_VERSION_ID);
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
          '2026-01-01','2030-01-01','APPROVED',
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PSU_VERSION_ID,
            TENANT_A,
            PSU_ID,
            LEGAL_VERSION_ID));
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            ESTABLISHMENT_VERSION_ID,
            TENANT_A,
            ESTABLISHMENT_ID,
            PSU_VERSION_ID));
  }

  private static void seedPayGroup(Statement statement)
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PAY_GROUP_VERSION_ID,
            TENANT_A,
            PAY_GROUP_ID,
            PSU_VERSION_ID,
            CALENDAR_ID));
  }

  private static void seedSalaryStructure(Statement statement)
      throws Exception {
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
          '%s','%s','%s',1,'FIXED',NULL,75000.0000,2,
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            COMPONENT_VERSION_ID,
            TENANT_A,
            COMPONENT_ID));
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
          '2026-01-01','2030-01-01','DRAFT','test','test'
        )
        """.formatted(
            STRUCTURE_VERSION_ID,
            TENANT_A,
            STRUCTURE_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          id,tenant_id,salary_structure_version_id,
          component_version_id,sequence_no,target_amount,
          effective_from,effective_to,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,75000.0000,
          '2026-01-01','2030-01-01','test','test'
        )
        """.formatted(
            STRUCTURE_LINE_ID,
            TENANT_A,
            STRUCTURE_VERSION_ID,
            COMPONENT_VERSION_ID));
    statement.execute(
        "SELECT compensation.approve_salary_structure_version("
            + "'"
            + TENANT_A
            + "','"
            + STRUCTURE_VERSION_ID
            + "','test',clock_timestamp())");
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
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
