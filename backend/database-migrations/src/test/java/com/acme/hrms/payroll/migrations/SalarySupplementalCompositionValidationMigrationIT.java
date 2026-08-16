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
class SalarySupplementalCompositionValidationMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

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
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
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
  void v043InstallsCompositionRevisionAndExplicitPercentageBase()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from information_schema.columns
           where table_schema='compensation'
             and (
               (
                 table_name='salary_structure_version'
                 and column_name='composition_revision'
               )
               or
               (
                 table_name='salary_supplemental_plan_line'
                 and column_name in (
                   'percentage_base_component_id',
                   'percentage_base_component_version_id'
                 )
               )
             )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(3);
      }

      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from pg_constraint constraint_row
            join pg_class relation
              on relation.oid=constraint_row.conrelid
            join pg_namespace namespace
              on namespace.oid=relation.relnamespace
           where namespace.nspname='compensation'
             and relation.relname='salary_supplemental_plan_line'
             and constraint_row.conname in (
               'salary_supplemental_plan_line_percentage_base_fk',
               'salary_supplemental_plan_line_value_shape_ck'
             )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(2);
      }
    }
  }

  @Test
  void v043MakesBindingAtomicAndLeastPrivilege()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertThat(tablePrivilege(
          statement,
          "compensation.salary_structure_supplemental_plan_binding",
          "INSERT")).isFalse();

      try (ResultSet result = statement.executeQuery(
          """
          select has_function_privilege(
            'payroll_app',
            'compensation.bind_salary_structure_supplemental_plan(uuid,uuid,uuid,uuid,uuid,integer,date,date,varchar)',
            'EXECUTE'
          )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isTrue();
      }

      try (ResultSet result = statement.executeQuery(
          """
          select pg_get_functiondef(
            'compensation.bind_salary_structure_supplemental_plan(uuid,uuid,uuid,uuid,uuid,integer,date,date,varchar)'::regprocedure
          )
          """)) {
        assertThat(result.next()).isTrue();
        String definition = result.getString(1);
        assertThat(definition)
            .contains("FOR UPDATE")
            .contains("composition_revision")
            .contains(
                "salary_structure_supplemental_plan_binding");
      }
    }
  }

  @Test
  void v043MakesValidationSourceAwareAndApprovalCurrent()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from pg_indexes
           where schemaname='compensation'
             and indexname in (
               'salary_structure_validation_line_base_source_uk',
               'salary_structure_validation_line_supplemental_source_uk'
             )
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(2);
      }

      try (ResultSet result = statement.executeQuery(
          """
          select count(*)
            from pg_indexes
           where schemaname='compensation'
             and indexname=
               'salary_structure_validation_line_component_uk'
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }

      try (ResultSet result = statement.executeQuery(
          """
          select pg_get_functiondef(
            'compensation.bind_salary_structure_validation(uuid,uuid,uuid,bigint,varchar,timestamptz)'::regprocedure
          )
          """)) {
        assertThat(result.next()).isTrue();
        String definition = result.getString(1);
        assertThat(definition)
            .contains("composedSimulation")
            .contains("compositionRevision")
            .contains("sourceBindingId")
            .contains("sourcePlanLineId");
      }

      try (ResultSet result = statement.executeQuery(
          """
          select pg_get_functiondef(
            'compensation.prevent_unvalidated_supplemental_composition_approval()'::regprocedure
          )
          """)) {
        assertThat(result.next()).isTrue();
        String definition = result.getString(1);
        assertThat(definition)
            .contains("current passing composed validation")
            .contains("composition_revision")
            .doesNotContain(
                "requires completed P5-SSC-01 validation integration");
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
