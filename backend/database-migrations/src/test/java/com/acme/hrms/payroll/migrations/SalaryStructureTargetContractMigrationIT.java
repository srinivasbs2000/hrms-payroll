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
class SalaryStructureTargetContractMigrationIT {
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
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER "
              + "NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app NOLOGIN NOSUPERUSER "
              + "NOCREATEDB NOCREATEROLE NOINHERIT "
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
  void targetContractColumnsAndShapeAreInstalled() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select count(*)
              from information_schema.columns
             where table_schema='compensation'
               and table_name='salary_structure_version'
               and column_name in (
                 'target_source_amount',
                 'target_frequency',
                 'target_annualization_factor',
                 'target_execution_mode',
                 'inclusive_payroll_base_version_id',
                 'exclusive_payroll_base_version_id'
               )
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isEqualTo(6);
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_constraintdef(oid)
              from pg_constraint
             where conrelid =
               'compensation.salary_structure_version'::regclass
               and conname =
                 'salary_structure_version_p5ssc_target_shape_ck'
            """)) {
      assertThat(result.next()).isTrue();
      String definition = result.getString(1);
      assertThat(definition)
          .contains("ANNUAL_TOTAL_CTC")
          .contains("ANNUAL_FIXED_CTC")
          .contains("HOURLY_RATE")
          .contains("DAILY_RATE")
          .contains("GRADE_MIDPOINT")
          .contains("TOTAL_CASH_TARGET")
          .contains("NET_PAY_TARGET")
          .contains("EMPLOYER_COST_TARGET")
          .contains("TARGET_RESOLVER_REQUIRED")
          .contains("CALCULATION_ENGINE");
    }
  }

  @Test
  void targetBaseDependencyTriggerIsPresent() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_target_bases()'
                ::regprocedure
            )
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("active approved payroll-base versions")
          .contains("salary-structure target bases must be CALCULATION or CTC bases")
          .contains("salary-structure range must be contained");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
