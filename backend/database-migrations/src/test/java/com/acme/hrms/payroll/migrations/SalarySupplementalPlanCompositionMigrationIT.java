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
class SalarySupplementalPlanCompositionMigrationIT {
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
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
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
  void v042InstallsTenantIsolatedAppendOnlyCompositionAuthority()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from pg_class c
            join pg_namespace n on n.oid=c.relnamespace
           where n.nspname='compensation'
             and c.relname in (
               'salary_supplemental_plan',
               'salary_supplemental_plan_version',
               'salary_supplemental_plan_line',
               'salary_structure_supplemental_plan_binding'
             )
             and c.relkind='r'
             and c.relrowsecurity
             and c.relforcerowsecurity
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(4);
      }

      assertThat(tablePrivilege(
          statement,
          "compensation.salary_supplemental_plan",
          "SELECT")).isTrue();
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_supplemental_plan",
          "INSERT")).isTrue();
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_supplemental_plan",
          "UPDATE")).isFalse();
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_supplemental_plan_version",
          "UPDATE")).isFalse();
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_supplemental_plan_line",
          "DELETE")).isFalse();
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_structure_supplemental_plan_binding",
          "UPDATE")).isFalse();

      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from pg_trigger trigger
            join pg_class relation on relation.oid=trigger.tgrelid
            join pg_namespace namespace on namespace.oid=relation.relnamespace
           where namespace.nspname='compensation'
             and relation.relname='salary_structure_version'
             and trigger.tgname='salary_structure_supplemental_approval_guard'
             and not trigger.tgisinternal
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }

      try (ResultSet result = statement.executeQuery(
          """
          select
            has_function_privilege(
              'payroll_app',
              'compensation.lock_salary_supplemental_plan(uuid,uuid)',
              'EXECUTE'
            ),
            has_function_privilege(
              'payroll_app',
              'compensation.approve_salary_supplemental_plan_version(uuid,uuid,varchar,timestamptz)',
              'EXECUTE'
            )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isTrue();
        assertThat(result.getBoolean(2)).isTrue();
      }
    }
  }

  private static boolean tablePrivilege(
      Statement statement,
      String table,
      String privilege) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "select has_table_privilege('payroll_app','"
            + table
            + "','"
            + privilege
            + "')")) {
      assertThat(result.next()).isTrue();
      return result.getBoolean(1);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
