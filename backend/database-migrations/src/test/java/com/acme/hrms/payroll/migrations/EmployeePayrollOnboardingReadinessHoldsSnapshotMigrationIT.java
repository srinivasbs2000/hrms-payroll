package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class EmployeePayrollOnboardingReadinessHoldsSnapshotMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT = "00000000-0000-0000-0000-0000000000f2";
  private static final String RELATIONSHIP = "52000000-0000-0000-0000-000000000001";
  private static final String CASE_ID = "52100000-0000-0000-0000-000000000001";
  private static final String HOLD_ID = "52200000-0000-0000-0000-000000000001";
  private static final String HOLD_VERSION = "52300000-0000-0000-0000-000000000001";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load().migrate();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("insert into platform.tenant(id,code,name,created_by,updated_by) values ('" + TENANT + "','EOR','EOR Synthetic','test','test')");
      statement.execute("set role payroll_owner");
      statement.execute("select set_config('app.tenant_id','" + TENANT + "',false)");
      statement.execute("insert into employee_payroll.payroll_relationship(id,tenant_id,external_employee_id,employee_number,created_by,updated_by) values ('" + RELATIONSHIP + "','" + TENANT + "','EOR-EXT-1','EOR-001','test','test')");
      statement.execute("reset role");
    }
  }

  @Test
  void v052StructuresAreForcedRlsAndSnapshotSchemaSupportsVersionTwo() throws Exception {
    Set<String> expected = Set.of(
        "payroll_onboarding_case", "payroll_onboarding_event",
        "payroll_readiness_policy_version", "payroll_hold",
        "payroll_hold_version", "payroll_hold_scope");
    Set<String> actual = new HashSet<>();
    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("""
            select relname,relrowsecurity,relforcerowsecurity from pg_class
             where relnamespace='employee_payroll'::regnamespace
               and relname in ('payroll_onboarding_case','payroll_onboarding_event',
                 'payroll_readiness_policy_version','payroll_hold','payroll_hold_version','payroll_hold_scope')
            """)) {
      while (result.next()) {
        actual.add(result.getString(1));
        assertThat(result.getBoolean(2)).isTrue();
        assertThat(result.getBoolean(3)).isTrue();
      }
    }
    assertThat(actual).isEqualTo(expected);
    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("""
            select pg_get_constraintdef(oid) from pg_constraint
             where conname='input_snapshot_payload_schema_ck'
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).contains("2");
    }
  }

  @Test
  void onboardingReadinessAndHoldLifecycleAreFailClosedAndMakerCheckerSeparated() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("set local app.tenant_id='" + TENANT + "'");
        assertThat(scalar(statement, "select employee_payroll.create_payroll_onboarding_case('" + TENANT + "','" + CASE_ID + "','" + RELATIONSHIP + "','start','case-evidence','maker',clock_timestamp())")).isEqualTo(1L);
        assertThat(scalar(statement, "select count(*) from employee_payroll.payroll_readiness_findings('" + TENANT + "','" + RELATIONSHIP + "',null,'2026-08-25') where dimension in ('STATUTORY','TAX') and status='NOT_EVALUATED' and severity='BLOCKING'")).isEqualTo(2L);
        assertThat(scalar(statement, "select employee_payroll.create_payroll_hold_version('" + TENANT + "','" + HOLD_ID + "','" + HOLD_VERSION + "','" + RELATIONSHIP + "','CALCULATION,PAYMENT','SECURITY_REVIEW','Review required','EOR-TEST','2026-08-01',null,'hold-maker',clock_timestamp())")).isEqualTo(1L);
        assertThatThrownBy(() -> scalar(statement, "select employee_payroll.approve_payroll_hold_version('" + TENANT + "','" + HOLD_VERSION + "',0,'hold-maker','self-approval',clock_timestamp())"))
            .hasMessageContaining("independent payroll hold approval evidence");
        connection.rollback();
        statement.execute("set local app.tenant_id='" + TENANT + "'");
        assertThat(scalar(statement, "select employee_payroll.create_payroll_hold_version('" + TENANT + "','" + HOLD_ID + "','" + HOLD_VERSION + "','" + RELATIONSHIP + "','CALCULATION,PAYMENT','SECURITY_REVIEW','Review required','EOR-TEST','2026-08-01',null,'hold-maker',clock_timestamp())")).isEqualTo(1L);
        assertThat(scalar(statement, "select employee_payroll.approve_payroll_hold_version('" + TENANT + "','" + HOLD_VERSION + "',0,'hold-checker','approved-evidence',clock_timestamp())")).isEqualTo(1L);
        assertThat(scalar(statement, "select count(*) from employee_payroll.payroll_hold_version where tenant_id='" + TENANT + "' and id='" + HOLD_VERSION + "' and lifecycle_status='ACTIVE'")).isEqualTo(1L);
      }
      connection.rollback();
    }
  }

  private static long scalar(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
