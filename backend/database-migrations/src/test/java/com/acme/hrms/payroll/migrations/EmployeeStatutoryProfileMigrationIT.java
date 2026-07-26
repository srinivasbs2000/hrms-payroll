package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class EmployeeStatutoryProfileMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";

  private static final String RELATIONSHIP_A =
      "40000000-0000-0000-0000-000000000011";
  private static final String RELATIONSHIP_B =
      "40000000-0000-0000-0000-000000000012";
  private static final String RELATIONSHIP_VERSION_A =
      "40000000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_LIFECYCLE =
      "40000000-0000-0000-0000-000000000013";
  private static final String RELATIONSHIP_VERSION_LIFECYCLE =
      "40000000-0000-0000-0000-000000000003";
  private static final String ASSIGNMENT_A =
      "50000000-0000-0000-0000-000000000011";
  private static final String ASSIGNMENT_VERSION_A =
      "50000000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_LIFECYCLE =
      "50000000-0000-0000-0000-000000000013";
  private static final String ASSIGNMENT_VERSION_LIFECYCLE =
      "50000000-0000-0000-0000-000000000003";

  private static final String RULE_A =
      "a1000000-0000-0000-0000-000000000001";
  private static final String RULE_VERSION_A =
      "a1100000-0000-0000-0000-000000000001";
  private static final String RULE_PORTION_A =
      "a1200000-0000-0000-0000-000000000001";

  private static final String PROFILE_A =
      "b1000000-0000-0000-0000-000000000001";
  private static final String PROFILE_VERSION_A =
      "b1100000-0000-0000-0000-000000000001";
  private static final String RULE_ASSIGNMENT_A =
      "b1200000-0000-0000-0000-000000000001";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateAndSeedExactParentLineage() throws Exception {
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
      statement.execute(
          "REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute(
          "REVOKE CREATE ON SCHEMA public FROM payroll_app");
    }

    Flyway flyway =
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                "payroll_migrator",
                MIGRATOR_PASSWORD)
            .locations("classpath:db/migration")
            .load();
    flyway.migrate();
    flyway.validate();

    seedParentConfiguration();
    seedApprovedStatutoryConfiguration();
  }

  @Test
  void appCanApproveAndEndDateProfileAndRuleAssignment()
      throws Exception {
    String profileId =
        "b1000000-0000-0000-0000-000000000010";
    String profileVersionId =
        "b1100000-0000-0000-0000-000000000010";
    String assignmentId =
        "b1200000-0000-0000-0000-000000000010";

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      insertProfile(
          connection,
          profileId,
          RELATIONSHIP_LIFECYCLE);
      insertProfileVersion(
          connection,
          profileVersionId,
          profileId,
          1,
          null,
          "2026-01-01");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "approve_employee_statutory_profile_version('"
                      + TENANT_A
                      + "','"
                      + profileVersionId
                      + "','test','"
                      + Instant.parse("2026-07-25T10:00:00Z")
                      + "')"))
          .isOne();

      insertRuleAssignment(
          connection,
          assignmentId,
          profileId,
          profileVersionId,
          ASSIGNMENT_LIFECYCLE,
          ASSIGNMENT_VERSION_LIFECYCLE,
          1,
          null,
          "ELIGIBLE",
          "PARTIAL",
          "COURT_ORDER",
          "2026-01-01",
          null);

      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "approve_employee_statutory_rule_assignment('"
                      + TENANT_A
                      + "','"
                      + assignmentId
                      + "','test','"
                      + Instant.parse("2026-07-25T10:01:00Z")
                      + "')"))
          .isOne();

      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "end_date_employee_statutory_rule_assignment('"
                      + TENANT_A
                      + "','"
                      + assignmentId
                      + "','2026-12-31',1,'test','"
                      + Instant.parse("2026-07-25T10:02:00Z")
                      + "')"))
          .isOne();

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT approval_status,effective_to,version_no "
                      + "FROM statutory."
                      + "employee_statutory_rule_assignment "
                      + "WHERE id='"
                      + assignmentId
                      + "'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("approval_status"))
            .isEqualTo("APPROVED");
        assertThat(result.getString("effective_to"))
            .isEqualTo("2026-12-31");
        assertThat(result.getLong("version_no")).isEqualTo(2);
      }

      connection.rollback();
    }
  }

  @Test
  void invalidExemptionShapeIsRejected() throws Exception {
    String assignmentId =
        "b1200000-0000-0000-0000-000000000020";

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "23514",
          () ->
              insertRuleAssignment(
                  connection,
                  assignmentId,
                  PROFILE_A,
                  PROFILE_VERSION_A,
                  1,
                  null,
                  "ELIGIBLE",
                  "FULL",
                  null,
                  "2027-01-01",
                  null));

      connection.rollback();
    }
  }

  @Test
  void assignmentOutsideExactParentRangeIsRejected()
      throws Exception {
    String assignmentId =
        "b1200000-0000-0000-0000-000000000030";

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "23514",
          () ->
              insertRuleAssignment(
                  connection,
                  assignmentId,
                  PROFILE_A,
                  PROFILE_VERSION_A,
                  1,
                  null,
                  "ELIGIBLE",
                  "NONE",
                  null,
                  "2025-12-01",
                  null));

      connection.rollback();
    }
  }

  @Test
  void approvedProfileVersionsCannotOverlap() throws Exception {
    String successorId =
        "b1100000-0000-0000-0000-000000000040";

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      insertProfileVersion(
          connection,
          successorId,
          PROFILE_A,
          2,
          PROFILE_VERSION_A,
          "2027-01-01");

      assertSqlState(
          connection,
          "23P01",
          () ->
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "approve_employee_statutory_profile_version('"
                      + TENANT_A
                      + "','"
                      + successorId
                      + "','test',clock_timestamp())"));

      connection.rollback();
    }
  }

  @Test
  void parentEndDateGuardProtectsApprovedAssignments()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "23514",
          () ->
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "end_date_employee_statutory_profile_version('"
                      + TENANT_A
                      + "','"
                      + PROFILE_VERSION_A
                      + "','2026-12-31',1,'test',clock_timestamp())"));

      connection.rollback();
    }
  }

  @Test
  void appCannotDirectlyRewriteApprovedHistory() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "UPDATE statutory."
                      + "employee_statutory_profile_version "
                      + "SET classification_code='REWRITTEN' WHERE id='"
                      + PROFILE_VERSION_A
                      + "'"));

      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "DELETE FROM statutory."
                      + "employee_statutory_rule_assignment WHERE id='"
                      + RULE_ASSIGNMENT_A
                      + "'"));

      connection.rollback();
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAFromTenantB()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_B);

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory."
                      + "employee_statutory_profile"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory."
                      + "employee_statutory_profile_version"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory."
                      + "employee_statutory_rule_assignment"))
          .isZero();

      connection.rollback();
    }
  }

  private static void seedParentConfiguration() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");

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
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('10000000-0000-0000-0000-00000000000a','"
              + TENANT_A
              + "','A_LE','test','test')");
      statement.execute(
          "INSERT INTO organisation.legal_entity_version("
              + "id,tenant_id,legal_entity_id,name,effective_from,"
              + "created_by,updated_by) VALUES "
              + "('11000000-0000-0000-0000-00000000000a','"
              + TENANT_A
              + "','10000000-0000-0000-0000-00000000000a',"
              + "'Synthetic A','2026-01-01','test','test')");

      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit("
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('20000000-0000-0000-0000-000000000011','"
              + TENANT_A
              + "','PSU','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit_version("
              + "id,tenant_id,payroll_statutory_unit_id,"
              + "legal_entity_version_id,name,effective_from,"
              + "created_by,updated_by) VALUES "
              + "('20000000-0000-0000-0000-000000000001','"
              + TENANT_A
              + "','20000000-0000-0000-0000-000000000011',"
              + "'11000000-0000-0000-0000-00000000000a',"
              + "'Synthetic','2026-01-01','test','test')");
      statement.execute(
          "INSERT INTO organisation.establishment("
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('21000000-0000-0000-0000-000000000011','"
              + TENANT_A
              + "','EST','test','test')");
      statement.execute(
          "INSERT INTO organisation.establishment_version("
              + "id,tenant_id,establishment_id,"
              + "payroll_statutory_unit_version_id,name,state_code,"
              + "effective_from,created_by,updated_by) VALUES "
              + "('21000000-0000-0000-0000-000000000001','"
              + TENANT_A
              + "','21000000-0000-0000-0000-000000000011',"
              + "'20000000-0000-0000-0000-000000000001',"
              + "'Synthetic','KA','2026-01-01','test','test')");

      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "created_by,updated_by) VALUES "
              + "('"
              + RELATIONSHIP_A
              + "','"
              + TENANT_A
              + "','SYNTHETIC','SYN001','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship_version("
              + "id,tenant_id,payroll_relationship_id,"
              + "legal_entity_version_id,version_sequence,"
              + "relationship_start,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES "
              + "('"
              + RELATIONSHIP_VERSION_A
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_A
              + "','11000000-0000-0000-0000-00000000000a',1,"
              + "'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment("
              + "id,tenant_id,payroll_relationship_id,"
              + "assignment_number,created_by,updated_by) VALUES "
              + "('"
              + ASSIGNMENT_A
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_A
              + "','ASN001','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment_version("
              + "id,tenant_id,payroll_assignment_id,"
              + "payroll_relationship_version_id,"
              + "establishment_version_id,version_sequence,"
              + "assignment_start,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES "
              + "('"
              + ASSIGNMENT_VERSION_A
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT_A
              + "','"
              + RELATIONSHIP_VERSION_A
              + "','21000000-0000-0000-0000-000000000001',1,"
              + "'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");

      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "created_by,updated_by) VALUES "
              + "('"
              + RELATIONSHIP_LIFECYCLE
              + "','"
              + TENANT_A
              + "','SYNTHETIC_LIFECYCLE','SYN003','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship_version("
              + "id,tenant_id,payroll_relationship_id,"
              + "legal_entity_version_id,version_sequence,"
              + "relationship_start,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES "
              + "('"
              + RELATIONSHIP_VERSION_LIFECYCLE
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_LIFECYCLE
              + "','11000000-0000-0000-0000-00000000000a',1,"
              + "'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment("
              + "id,tenant_id,payroll_relationship_id,"
              + "assignment_number,created_by,updated_by) VALUES "
              + "('"
              + ASSIGNMENT_LIFECYCLE
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_LIFECYCLE
              + "','ASN003','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment_version("
              + "id,tenant_id,payroll_assignment_id,"
              + "payroll_relationship_version_id,"
              + "establishment_version_id,version_sequence,"
              + "assignment_start,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES "
              + "('"
              + ASSIGNMENT_VERSION_LIFECYCLE
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT_LIFECYCLE
              + "','"
              + RELATIONSHIP_VERSION_LIFECYCLE
              + "','21000000-0000-0000-0000-000000000001',1,"
              + "'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");

      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_B + "',false)");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "created_by,updated_by) VALUES "
              + "('"
              + RELATIONSHIP_B
              + "','"
              + TENANT_B
              + "','SYNTHETIC_B','SYN002','test','test')");
    }
  }

  private static void seedApprovedStatutoryConfiguration()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");

      statement.execute(
          "INSERT INTO statutory.statutory_rule("
              + "id,tenant_id,jurisdiction_code,authority_code,"
              + "code,name,rule_category,created_by,updated_by) VALUES "
              + "('"
              + RULE_A
              + "','"
              + TENANT_A
              + "','IN','CENTRAL','SOCIAL_A','Social A',"
              + "'SOCIAL_INSURANCE','test','test')");
      statement.execute(
          "INSERT INTO statutory.statutory_rule_version("
              + "id,tenant_id,statutory_rule_id,version_sequence,"
              + "effective_from,currency,approval_status,"
              + "created_by,updated_by) VALUES "
              + "('"
              + RULE_VERSION_A
              + "','"
              + TENANT_A
              + "','"
              + RULE_A
              + "',1,'2026-01-01','INR','DRAFT','test','test')");
      statement.execute(
          "INSERT INTO statutory.statutory_rule_portion("
              + "id,tenant_id,statutory_rule_version_id,"
              + "liable_party,sequence_no,calculation_method,"
              + "assessment_base_code,rate_percent,"
              + "created_by,updated_by) VALUES "
              + "('"
              + RULE_PORTION_A
              + "','"
              + TENANT_A
              + "','"
              + RULE_VERSION_A
              + "','EMPLOYEE',1,'PERCENTAGE','GROSS',10,"
              + "'test','test')");
      statement.execute(
          "SELECT statutory.approve_statutory_rule_version('"
              + TENANT_A
              + "','"
              + RULE_VERSION_A
              + "','test',clock_timestamp())");

      statement.execute(
          "INSERT INTO statutory.employee_statutory_profile("
              + "id,tenant_id,payroll_relationship_id,"
              + "jurisdiction_code,authority_code,"
              + "created_by,updated_by) VALUES "
              + "('"
              + PROFILE_A
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_A
              + "','IN','CENTRAL','test','test')");
      statement.execute(
          "INSERT INTO statutory.employee_statutory_profile_version("
              + "id,tenant_id,employee_statutory_profile_id,"
              + "version_sequence,effective_from,registration_status,"
              + "classification_code,approval_status,"
              + "created_by,updated_by) VALUES "
              + "('"
              + PROFILE_VERSION_A
              + "','"
              + TENANT_A
              + "','"
              + PROFILE_A
              + "',1,'2026-01-01','REGISTERED','STANDARD',"
              + "'DRAFT','test','test')");
      statement.execute(
          "SELECT statutory."
              + "approve_employee_statutory_profile_version('"
              + TENANT_A
              + "','"
              + PROFILE_VERSION_A
              + "','test',clock_timestamp())");

      insertRuleAssignment(
          connection,
          RULE_ASSIGNMENT_A,
          PROFILE_A,
          PROFILE_VERSION_A,
          1,
          null,
          "ELIGIBLE",
          "NONE",
          null,
          "2026-01-01",
          null);
      statement.execute(
          "SELECT statutory."
              + "approve_employee_statutory_rule_assignment('"
              + TENANT_A
              + "','"
              + RULE_ASSIGNMENT_A
              + "','test',clock_timestamp())");
    }
  }

  private static void insertProfile(
      Connection connection,
      String profileId,
      String payrollRelationshipId) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.employee_statutory_profile("
            + "id,tenant_id,payroll_relationship_id,"
            + "jurisdiction_code,authority_code,"
            + "created_by,updated_by) VALUES "
            + "('"
            + profileId
            + "','"
            + TENANT_A
            + "','"
            + payrollRelationshipId
            + "','IN','CENTRAL','test','test')");
  }

  private static void insertProfileVersion(
      Connection connection,
      String versionId,
      String profileId,
      int sequence,
      String supersedesId,
      String effectiveFrom)
      throws SQLException {
    String supersedes =
        supersedesId == null ? "NULL" : "'" + supersedesId + "'";

    execute(
        connection,
        "INSERT INTO statutory.employee_statutory_profile_version("
            + "id,tenant_id,employee_statutory_profile_id,"
            + "version_sequence,effective_from,registration_status,"
            + "classification_code,approval_status,"
            + "supersedes_version_id,created_by,updated_by) VALUES "
            + "('"
            + versionId
            + "','"
            + TENANT_A
            + "','"
            + profileId
            + "',"
            + sequence
            + ",'"
            + effectiveFrom
            + "','REGISTERED','STANDARD','DRAFT',"
            + supersedes
            + ",'test','test')");
  }

  private static void insertRuleAssignment(
      Connection connection,
      String assignmentId,
      String profileId,
      String profileVersionId,
      int sequence,
      String supersedesId,
      String eligibility,
      String exemption,
      String exemptionReason,
      String effectiveFrom,
      String effectiveTo)
      throws SQLException {
    insertRuleAssignment(
        connection,
        assignmentId,
        profileId,
        profileVersionId,
        ASSIGNMENT_A,
        ASSIGNMENT_VERSION_A,
        sequence,
        supersedesId,
        eligibility,
        exemption,
        exemptionReason,
        effectiveFrom,
        effectiveTo);
  }

  private static void insertRuleAssignment(
      Connection connection,
      String assignmentId,
      String profileId,
      String profileVersionId,
      String payrollAssignmentId,
      String payrollAssignmentVersionId,
      int sequence,
      String supersedesId,
      String eligibility,
      String exemption,
      String exemptionReason,
      String effectiveFrom,
      String effectiveTo)
      throws SQLException {
    String supersedes =
        supersedesId == null ? "NULL" : "'" + supersedesId + "'";
    String reason =
        exemptionReason == null ? "NULL" : "'" + exemptionReason + "'";
    String rangeEnd =
        effectiveTo == null ? "NULL" : "'" + effectiveTo + "'";

    execute(
        connection,
        "INSERT INTO statutory.employee_statutory_rule_assignment("
            + "id,tenant_id,employee_statutory_profile_id,"
            + "employee_statutory_profile_version_id,"
            + "payroll_assignment_id,payroll_assignment_version_id,"
            + "statutory_rule_id,statutory_rule_version_id,"
            + "assignment_sequence,effective_from,effective_to,"
            + "eligibility_status,exemption_status,"
            + "exemption_reason_code,approval_status,"
            + "supersedes_assignment_id,created_by,updated_by) VALUES "
            + "('"
            + assignmentId
            + "','"
            + TENANT_A
            + "','"
            + profileId
            + "','"
            + profileVersionId
            + "','"
            + payrollAssignmentId
            + "','"
            + payrollAssignmentVersionId
            + "','"
            + RULE_A
            + "','"
            + RULE_VERSION_A
            + "',"
            + sequence
            + ",'"
            + effectiveFrom
            + "',"
            + rangeEnd
            + ",'"
            + eligibility
            + "','"
            + exemption
            + "',"
            + reason
            + ",'DRAFT',"
            + supersedes
            + ",'test','test')");
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void setTenant(Connection connection, String tenant)
      throws SQLException {
    execute(
        connection,
        "SELECT set_config('app.tenant_id','"
            + tenant
            + "',false)");
  }

  private static long queryLong(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void execute(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void assertSqlState(
      Connection connection,
      String expectedState,
      SqlAction action) throws SQLException {
    Savepoint savepoint = connection.setSavepoint();
    try {
      action.execute();
      fail("Expected SQLSTATE " + expectedState);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expectedState);
    } finally {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    }
  }

  @FunctionalInterface
  private interface SqlAction {
    void execute() throws SQLException;
  }
}
