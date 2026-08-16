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
class SalaryStructureLifecycleMigrationIT {
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
  void workflowColumnsAndImmutableActionLedgerExist() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select count(*)
              from information_schema.columns
             where table_schema='compensation'
               and table_name='salary_structure_version'
               and column_name in (
                 'workflow_status','submitted_at','submitted_by',
                 'published_at','published_by'
               )
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(5);
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select relrowsecurity,relforcerowsecurity
              from pg_class
             where oid='compensation.salary_structure_workflow_action'::regclass
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
    }
  }

  @Test
  void appUsesControlledLifecycleCommandsWithoutLedgerMutation() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
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
                'EXECUTE'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'INSERT'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_workflow_action',
                'UPDATE')
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
      assertThat(result.getBoolean(4)).isFalse();
      assertThat(result.getBoolean(5)).isFalse();
    }
  }

  @Test
  void submissionApprovalAndPublicationCaptureExactEvidence() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet submit = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.submit_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)'::regprocedure
            )
            """)) {
      assertThat(submit.next()).isTrue();
      assertThat(submit.getString(1))
          .contains("latest passing bound structural validation")
          .contains("current passing statutory compatibility evidence")
          .contains("SUBMITTED");
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet approve = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_workflow_approval()'::regprocedure
            )
            """)) {
      assertThat(approve.next()).isTrue();
      assertThat(approve.getString(1))
          .contains("must be submitted before approval")
          .contains("maker cannot be the final approver")
          .contains("APPROVED");
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet publish = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.publish_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)'::regprocedure
            )
            """)) {
      assertThat(publish.next()).isTrue();
      assertThat(publish.getString(1)).contains("PUBLISHED");
    }
  }

  @Test
  void lifecycleReadsStatutoryRevisionFromAuthoritativeStateNotStructuralValidation()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet validationColumn = statement.executeQuery(
            """
            select count(*)
              from information_schema.columns
             where table_schema='compensation'
               and table_name='salary_structure_validation'
               and column_name='statutory_binding_revision'
            """)) {
      assertThat(validationColumn.next()).isTrue();
      assertThat(validationColumn.getLong(1)).isZero();
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet submit = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.submit_salary_structure_version(uuid,uuid,uuid,bigint,character varying,character varying,timestamp with time zone)'::regprocedure
            )
            """)) {
      assertThat(submit.next()).isTrue();
      assertThat(submit.getString(1))
          .contains("salary_structure_statutory_state")
          .doesNotContain("validation.statutory_binding_revision");
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet approve = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_workflow_approval()'::regprocedure
            )
            """)) {
      assertThat(approve.next()).isTrue();
      assertThat(approve.getString(1))
          .contains("salary_structure_statutory_state")
          .doesNotContain("validation.statutory_binding_revision");
    }
  }

  @Test
  void lifecycleReadHasOnlyRequiredStatutoryStateColumns() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              has_column_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_state',
                'tenant_id',
                'SELECT'),
              has_column_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_state',
                'salary_structure_version_id',
                'SELECT'),
              has_column_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_state',
                'binding_revision',
                'SELECT'),
              has_column_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_state',
                'updated_at',
                'SELECT'),
              has_column_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_state',
                'updated_by',
                'SELECT')
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
      assertThat(result.getBoolean(4)).isFalse();
      assertThat(result.getBoolean(5)).isFalse();
    }
  }

  @Test
  void submittedEvidenceIsFrozenButLegacySchemaZeroApprovalRemainsCompatible()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet validationGuard = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_validation_binding_workflow()'::regprocedure
            )
            """)) {
      assertThat(validationGuard.next()).isTrue();
      assertThat(validationGuard.getString(1))
          .contains("cannot change after salary-structure submission");
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet approvalGuard = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_workflow_approval()'::regprocedure
            )
            """)) {
      assertThat(approvalGuard.next()).isTrue();
      assertThat(approvalGuard.getString(1))
          .contains("NEW.structure_schema_version=0")
          .contains("NEW.workflow_status:='PUBLISHED'");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
