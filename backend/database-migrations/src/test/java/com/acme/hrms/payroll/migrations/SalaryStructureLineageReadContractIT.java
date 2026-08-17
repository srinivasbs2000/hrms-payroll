package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SalaryStructureLineageReadContractIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
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
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
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

  @Test
  void runtimeCanReadEveryLineageEvidenceStore() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertReadable(
          statement,
          "compensation.salary_structure");
      assertReadable(
          statement,
          "compensation.salary_structure_version");
      assertReadable(
          statement,
          "compensation.salary_structure_validation");
      assertColumnReadable(
          statement,
          "compensation.salary_structure_statutory_state",
          "tenant_id");
      assertColumnReadable(
          statement,
          "compensation.salary_structure_statutory_state",
          "salary_structure_version_id");
      assertColumnReadable(
          statement,
          "compensation.salary_structure_statutory_state",
          "binding_revision");
      assertColumnNotReadable(
          statement,
          "compensation.salary_structure_statutory_state",
          "updated_at");
      assertColumnNotReadable(
          statement,
          "compensation.salary_structure_statutory_state",
          "updated_by");
      assertReadable(
          statement,
          "compensation.salary_structure_statutory_evaluation");
      assertReadable(
          statement,
          "compensation.salary_structure_workflow_action");
      assertReadable(
          statement,
          "audit.audit_event");
      assertReadable(
          statement,
          "integration.outbox_event");
    }
  }

  @Test
  void immutableEvidenceStoresRetainTheirProtection() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              validation.relrowsecurity,
              validation.relforcerowsecurity,
              workflow.relrowsecurity,
              workflow.relforcerowsecurity,
              has_table_privilege(
                'payroll_app',
                'audit.audit_event',
                'UPDATE'),
              has_table_privilege(
                'payroll_app',
                'audit.audit_event',
                'DELETE'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'UPDATE'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'DELETE')
              from pg_class validation
              cross join pg_class workflow
             where validation.oid=
                   'compensation.salary_structure_validation'::regclass
               and workflow.oid=
                   'compensation.salary_structure_workflow_action'::regclass
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
      assertThat(result.getBoolean(4)).isTrue();
      assertThat(result.getBoolean(5)).isFalse();
      assertThat(result.getBoolean(6)).isFalse();
      assertThat(result.getBoolean(7)).isFalse();
      assertThat(result.getBoolean(8)).isFalse();
    }
  }

  private static void assertReadable(
      Statement statement,
      String table) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "select has_table_privilege("
            + "'payroll_app','"
            + table
            + "','SELECT')")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1))
          .as("payroll_app SELECT on %s", table)
          .isTrue();
    }
  }

  private static void assertColumnReadable(
      Statement statement,
      String table,
      String column) throws Exception {
    assertColumnPrivilege(statement, table, column, true);
  }

  private static void assertColumnNotReadable(
      Statement statement,
      String table,
      String column) throws Exception {
    assertColumnPrivilege(statement, table, column, false);
  }

  private static void assertColumnPrivilege(
      Statement statement,
      String table,
      String column,
      boolean expected) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "select has_column_privilege("
            + "'payroll_app','"
            + table
            + "','"
            + column
            + "','SELECT')")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1))
          .as(
              "payroll_app SELECT on %s.%s expected %s",
              table,
              column,
              expected)
          .isEqualTo(expected);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
