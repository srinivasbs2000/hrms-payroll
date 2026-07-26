package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class StatutoryRuleMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromZero() throws Exception {
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
      statement.execute("REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute("REVOKE CREATE ON SCHEMA public FROM payroll_app");
    }

    Flyway flyway = Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load();
    flyway.migrate();
    flyway.validate();
  }

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
  void appRoleCanCreateApproveAndEndDatePercentageRule()
      throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID portionId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "TAX_A");
      insertVersion(
          connection,
          TENANT_A,
          versionId,
          ruleId,
          1,
          null,
          "2027-01-01",
          null);
      insertPercentagePortion(
          connection,
          TENANT_A,
          portionId,
          versionId,
          "EMPLOYEE",
          1,
          "GROSS_TAXABLE",
          "10.00000000");

      assertThat(queryLong(
          connection,
          "SELECT statutory.approve_statutory_rule_version('"
              + TENANT_A
              + "','"
              + versionId
              + "','test','"
              + Instant.parse("2026-07-25T00:00:00Z")
              + "')"))
          .isOne();

      assertThat(queryLong(
          connection,
          "SELECT statutory.end_date_statutory_rule_version('"
              + TENANT_A
              + "','"
              + versionId
              + "','2027-12-01',1,'test','"
              + Instant.parse("2026-07-25T00:01:00Z")
              + "')"))
          .isOne();

      try (Statement statement = connection.createStatement();
          ResultSet state = statement.executeQuery(
              "SELECT approval_status,effective_to,version_no "
                  + "FROM statutory.statutory_rule_version WHERE id='"
                  + versionId
                  + "'")) {
        assertThat(state.next()).isTrue();
        assertThat(state.getString("approval_status"))
            .isEqualTo("APPROVED");
        assertThat(state.getString("effective_to"))
            .isEqualTo("2027-12-01");
        assertThat(state.getLong("version_no")).isEqualTo(2);
      }

      assertThat(queryBoolean(
          connection,
          "SELECT has_table_privilege(current_user,"
              + "'statutory.statutory_rule_version','UPDATE')"))
          .isFalse();
      assertThat(queryBoolean(
          connection,
          "SELECT has_schema_privilege(current_user,"
              + "'statutory','CREATE')"))
          .isFalse();
      connection.commit();
    }
  }

  @Test
  void approvalRejectsEmptyRuleVersion() throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "EMPTY_RULE");
      insertVersion(
          connection,
          TENANT_A,
          versionId,
          ruleId,
          1,
          null,
          "2027-01-01",
          null);

      assertSqlState(
          "23514",
          () -> execute(
              connection,
              "SELECT statutory.approve_statutory_rule_version('"
                  + TENANT_A
                  + "','"
                  + versionId
                  + "','test','2026-07-25T00:00:00Z')"));
    }
  }

  @Test
  void directApprovedVersionInsertIsRejected() throws Exception {
    UUID ruleId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "CONTROLLED_APPROVAL");
      assertSqlState(
          "23514",
          () -> execute(
              connection,
              "INSERT INTO statutory.statutory_rule_version("
                  + "tenant_id,statutory_rule_id,version_sequence,"
                  + "effective_from,currency,rounding_scale,rounding_mode,"
                  + "approval_status,approved_at,approved_by,created_by,"
                  + "updated_by) VALUES ('"
                  + TENANT_A
                  + "','"
                  + ruleId
                  + "',1,'2027-01-01','INR',2,'HALF_UP','APPROVED',"
                  + "'2026-07-25T00:00:00Z','test','test','test')"));
    }
  }

  @Test
  void invalidMethodShapesAreRejected() throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "INVALID_SHAPES");
      insertVersion(
          connection,
          TENANT_A,
          versionId,
          ruleId,
          1,
          null,
          "2027-01-01",
          null);

      assertSqlState(
          "23514",
          () -> execute(
              connection,
              "INSERT INTO statutory.statutory_rule_portion("
                  + "tenant_id,statutory_rule_version_id,liable_party,"
                  + "sequence_no,calculation_method,assessment_base_code,"
                  + "rate_percent,created_by,updated_by) VALUES ('"
                  + TENANT_A
                  + "','"
                  + versionId
                  + "','EMPLOYEE',1,'PERCENTAGE','GROSS_TAXABLE',"
                  + "101.00000000,'test','test')"));
    }
  }

  @Test
  void slabApprovalRejectsGaps() throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID portionId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "GAPPED_SLAB");
      insertVersion(
          connection,
          TENANT_A,
          versionId,
          ruleId,
          1,
          null,
          "2027-01-01",
          null);
      insertSlabPortion(
          connection,
          TENANT_A,
          portionId,
          versionId,
          "EMPLOYEE",
          1,
          "ANNUAL_TAXABLE");
      insertSlab(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          versionId,
          portionId,
          1,
          "0.0000",
          "1000.0000",
          "0.00000000");
      insertSlab(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          versionId,
          portionId,
          2,
          "1500.0000",
          null,
          "10.00000000");

      assertSqlState(
          "23514",
          () -> execute(
              connection,
              "SELECT statutory.approve_statutory_rule_version('"
                  + TENANT_A
                  + "','"
                  + versionId
                  + "','test','2026-07-25T00:00:00Z')"));
    }
  }

  @Test
  void approvedSlabRuleIsImmutableAndRejectsNewBands()
      throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID portionId = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "VALID_SLAB");
      insertVersion(
          connection,
          TENANT_A,
          versionId,
          ruleId,
          1,
          null,
          "2027-01-01",
          null);
      insertSlabPortion(
          connection,
          TENANT_A,
          portionId,
          versionId,
          "EMPLOYEE",
          1,
          "ANNUAL_TAXABLE");
      insertSlab(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          versionId,
          portionId,
          1,
          "0.0000",
          "1000.0000",
          "0.00000000");
      insertSlab(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          versionId,
          portionId,
          2,
          "1000.0000",
          null,
          "10.00000000");
      assertThat(queryLong(
          connection,
          "SELECT statutory.approve_statutory_rule_version('"
              + TENANT_A
              + "','"
              + versionId
              + "','test','2026-07-25T00:00:00Z')"))
          .isOne();
      connection.commit();
    }

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      assertSqlState(
          "23514",
          () -> insertSlab(
              connection,
              TENANT_A,
              UUID.randomUUID(),
              versionId,
              portionId,
              3,
              "5000.0000",
              null,
              "20.00000000"));
    }

    try (Connection connection = admin()) {
      assertSqlState(
          "42501",
          () -> execute(
              connection,
              "UPDATE statutory.statutory_rule_version "
                  + "SET rounding_scale=4 WHERE id='"
                  + versionId
                  + "'"));
    }
  }

  @Test
  void tenantIsolationAndCompositeForeignKeysAreEnforced()
      throws Exception {
    UUID ruleA = UUID.randomUUID();
    UUID ruleB = UUID.randomUUID();

    try (Connection connection = admin()) {
      insertRule(connection, TENANT_A, ruleA, "RULE_A");
      insertRule(connection, TENANT_B, ruleB, "RULE_B");
    }

    try (Connection connection = app()) {
      setTenant(connection, TENANT_B);
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_rule "
              + "WHERE id='"
              + ruleA
              + "'"))
          .isZero();
      connection.rollback();
    }

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      assertSqlState(
          "23503",
          () -> insertVersion(
              connection,
              TENANT_A,
              UUID.randomUUID(),
              ruleB,
              1,
              null,
              "2027-01-01",
              null));
    }

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      assertSqlState(
          "42501",
          () -> insertRule(
              connection,
              TENANT_B,
              UUID.randomUUID(),
              "CROSS_TENANT"));
    }
  }

  @Test
  void approvedVersionsCannotOverlap() throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID firstVersion = UUID.randomUUID();
    UUID secondVersion = UUID.randomUUID();

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertRule(connection, TENANT_A, ruleId, "OVERLAP_RULE");
      insertVersion(
          connection,
          TENANT_A,
          firstVersion,
          ruleId,
          1,
          null,
          "2027-01-01",
          "2028-01-01");
      insertPercentagePortion(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          firstVersion,
          "EMPLOYEE",
          1,
          "GROSS_TAXABLE",
          "5.00000000");
      assertThat(queryLong(
          connection,
          "SELECT statutory.approve_statutory_rule_version('"
              + TENANT_A
              + "','"
              + firstVersion
              + "','test','2026-07-25T00:00:00Z')"))
          .isOne();
      connection.commit();
    }

    try (Connection connection = app()) {
      setTenant(connection, TENANT_A);
      insertVersion(
          connection,
          TENANT_A,
          secondVersion,
          ruleId,
          2,
          firstVersion,
          "2027-06-01",
          "2028-06-01");
      insertPercentagePortion(
          connection,
          TENANT_A,
          UUID.randomUUID(),
          secondVersion,
          "EMPLOYEE",
          1,
          "GROSS_TAXABLE",
          "7.00000000");
      assertSqlState(
          "23P01",
          () -> execute(
              connection,
              "SELECT statutory.approve_statutory_rule_version('"
                  + TENANT_A
                  + "','"
                  + secondVersion
                  + "','test','2026-07-25T00:02:00Z')"));
    }
  }

  private static void insertRule(
      Connection connection,
      UUID tenantId,
      UUID ruleId,
      String code) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule("
            + "id,tenant_id,jurisdiction_code,authority_code,code,name,"
            + "rule_category,created_by,updated_by) VALUES ('"
            + ruleId
            + "','"
            + tenantId
            + "','IN','SYNTHETIC_AUTH','"
            + code
            + "','Synthetic "
            + code
            + "','INCOME_TAX','test','test')");
  }

  private static void insertVersion(
      Connection connection,
      UUID tenantId,
      UUID versionId,
      UUID ruleId,
      int versionSequence,
      UUID supersedesVersionId,
      String effectiveFrom,
      String effectiveTo) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule_version("
            + "id,tenant_id,statutory_rule_id,version_sequence,"
            + "effective_from,effective_to,currency,rounding_scale,"
            + "rounding_mode,approval_status,supersedes_version_id,"
            + "created_by,updated_by) VALUES ('"
            + versionId
            + "','"
            + tenantId
            + "','"
            + ruleId
            + "',"
            + versionSequence
            + ",'"
            + effectiveFrom
            + "',"
            + nullable(effectiveTo)
            + ",'INR',2,'HALF_UP','DRAFT',"
            + nullable(supersedesVersionId)
            + ",'test','test')");
  }

  private static void insertPercentagePortion(
      Connection connection,
      UUID tenantId,
      UUID portionId,
      UUID versionId,
      String liableParty,
      int sequence,
      String assessmentBase,
      String rate) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule_portion("
            + "id,tenant_id,statutory_rule_version_id,liable_party,"
            + "sequence_no,calculation_method,assessment_base_code,"
            + "rate_percent,created_by,updated_by) VALUES ('"
            + portionId
            + "','"
            + tenantId
            + "','"
            + versionId
            + "','"
            + liableParty
            + "',"
            + sequence
            + ",'PERCENTAGE','"
            + assessmentBase
            + "',"
            + rate
            + ",'test','test')");
  }

  private static void insertSlabPortion(
      Connection connection,
      UUID tenantId,
      UUID portionId,
      UUID versionId,
      String liableParty,
      int sequence,
      String assessmentBase) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule_portion("
            + "id,tenant_id,statutory_rule_version_id,liable_party,"
            + "sequence_no,calculation_method,assessment_base_code,"
            + "created_by,updated_by) VALUES ('"
            + portionId
            + "','"
            + tenantId
            + "','"
            + versionId
            + "','"
            + liableParty
            + "',"
            + sequence
            + ",'SLAB','"
            + assessmentBase
            + "','test','test')");
  }

  private static void insertSlab(
      Connection connection,
      UUID tenantId,
      UUID slabId,
      UUID versionId,
      UUID portionId,
      int sequence,
      String lowerBound,
      String upperBound,
      String rate) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule_slab("
            + "id,tenant_id,statutory_rule_version_id,"
            + "statutory_rule_portion_id,sequence_no,lower_bound,"
            + "upper_bound,fixed_amount,rate_percent,created_by,updated_by) "
            + "VALUES ('"
            + slabId
            + "','"
            + tenantId
            + "','"
            + versionId
            + "','"
            + portionId
            + "',"
            + sequence
            + ","
            + lowerBound
            + ","
            + nullable(upperBound)
            + ",0.0000,"
            + rate
            + ",'test','test')");
  }

  private static String nullable(String value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private static String nullable(UUID value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private static long queryLong(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static boolean queryBoolean(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getBoolean(1);
    }
  }

  private static void setTenant(Connection connection, UUID tenantId)
      throws SQLException {
    connection.setAutoCommit(false);
    execute(connection, "SET LOCAL app.tenant_id='" + tenantId + "'");
  }

  private static void execute(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void assertSqlState(
      String expectedState,
      SqlAction action) throws Exception {
    try {
      action.run();
      fail("Expected SQLSTATE " + expectedState);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expectedState);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }
}
