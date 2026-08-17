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
class SalaryStructureSecuritySodMigrationIT {
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
  void publicationRequiresActorDistinctFromMakerAndApprover()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.publish_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)'::regprocedure
            )
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("version.submitted_by")
          .contains("version.approved_by")
          .contains("maker cannot publish their own submission")
          .contains("approver cannot publish their own approval")
          .contains("complete maker-checker approval chain");
    }
  }

  @Test
  void existingMakerCheckerApprovalGuardRemainsAuthoritative()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_workflow_approval()'::regprocedure
            )
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("must be submitted before approval")
          .contains("maker cannot be the final approver")
          .contains("approval evidence differs");
    }
  }

  @Test
  void runtimeRemainsRlsBoundAndCannotMutateWorkflowEvidenceDirectly()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              role.rolbypassrls,
              workflow.relrowsecurity,
              workflow.relforcerowsecurity,
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'INSERT'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'UPDATE'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'DELETE'),
              has_function_privilege(
                'payroll_app',
                'compensation.submit_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'compensation.reject_salary_structure_submission(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'compensation.publish_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)',
                'EXECUTE')
              from pg_roles role
              cross join pg_class workflow
             where role.rolname='payroll_app'
               and workflow.oid=
                   'compensation.salary_structure_workflow_action'::regclass
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isFalse();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
      assertThat(result.getBoolean(4)).isFalse();
      assertThat(result.getBoolean(5)).isFalse();
      assertThat(result.getBoolean(6)).isFalse();
      assertThat(result.getBoolean(7)).isTrue();
      assertThat(result.getBoolean(8)).isTrue();
      assertThat(result.getBoolean(9)).isTrue();
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
