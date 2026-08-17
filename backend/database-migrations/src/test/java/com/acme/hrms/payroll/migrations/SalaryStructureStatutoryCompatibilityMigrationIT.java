package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SalaryStructureStatutoryCompatibilityMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT =
      UUID.fromString("00000000-0000-0000-0000-0000000000f6");

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

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) "
              + "VALUES ('" + TENANT + "','G02G','G02G Test','test','test')");
    }
  }

  @Test
  void minimumWageRuleApprovesFromStatutoryOwnedConstraintWithoutLiabilityPortion()
      throws Exception {
    UUID ruleId = UUID.fromString("51000000-0000-0000-0000-000000000001");
    UUID versionId = UUID.fromString("51100000-0000-0000-0000-000000000001");
    UUID constraintId = UUID.fromString("51200000-0000-0000-0000-000000000001");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.tenant_id='" + TENANT + "'");
      statement.execute(
          "INSERT INTO statutory.statutory_rule("
              + "id,tenant_id,jurisdiction_code,authority_code,code,name,"
              + "rule_category,status,created_by,updated_by) VALUES ('"
              + ruleId + "','" + TENANT
              + "','TEST_JURISDICTION','TEST_AUTHORITY','MIN_WAGE_TEST',"
              + "'Synthetic minimum wage','MINIMUM_WAGE','ACTIVE','maker','maker')");
      statement.execute(
          "INSERT INTO statutory.statutory_rule_version("
              + "id,tenant_id,statutory_rule_id,version_sequence,effective_from,"
              + "effective_to,currency,approval_status,created_by,updated_by) VALUES ('"
              + versionId + "','" + TENANT + "','" + ruleId
              + "',1,'2027-01-01','2028-01-01','INR','DRAFT','maker','maker')");
      statement.execute(
          "INSERT INTO statutory.statutory_rule_design_constraint("
              + "id,tenant_id,statutory_rule_id,statutory_rule_version_id,"
              + "constraint_kind,period_basis,minimum_amount,source_reference,"
              + "created_by,updated_by) VALUES ('"
              + constraintId + "','" + TENANT + "','" + ruleId + "','"
              + versionId + "','MINIMUM_WAGE','MONTHLY',25000.0000,"
              + "'synthetic-source','maker','maker')");

      try (ResultSet approved = statement.executeQuery(
          "SELECT statutory.approve_statutory_rule_version('"
              + TENANT + "','" + versionId
              + "','checker','2026-08-16T10:00:00Z')")) {
        assertThat(approved.next()).isTrue();
        assertThat(approved.getLong(1)).isOne();
      }

      try (ResultSet state = statement.executeQuery(
          "SELECT approval_status FROM statutory.statutory_rule_version "
              + "WHERE id='" + versionId + "'")) {
        assertThat(state.next()).isTrue();
        assertThat(state.getString(1)).isEqualTo("APPROVED");
      }

      try (ResultSet portions = statement.executeQuery(
          "SELECT count(*) FROM statutory.statutory_rule_portion WHERE "
              + "statutory_rule_version_id='" + versionId + "'")) {
        assertThat(portions.next()).isTrue();
        assertThat(portions.getLong(1)).isZero();
      }
    }
  }

  @Test
  void compatibilityEvidenceIsTenantIsolatedAndControlled() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select n.nspname,c.relname,c.relrowsecurity,c.relforcerowsecurity
              from pg_class c
              join pg_namespace n on n.oid=c.relnamespace
             where (n.nspname,c.relname) in (
               ('statutory','statutory_rule_design_constraint'),
               ('compensation','salary_structure_statutory_state'),
               ('compensation','salary_structure_statutory_binding'),
               ('compensation','salary_structure_statutory_evaluation'),
               ('compensation','salary_structure_statutory_issue')
             )
             order by n.nspname,c.relname
            """)) {
      int rows = 0;
      while (result.next()) {
        rows++;
        assertThat(result.getBoolean("relrowsecurity")).isTrue();
        assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
      }
      assertThat(rows).isEqualTo(5);
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              has_function_privilege(
                'payroll_app',
                'compensation.bind_salary_structure_statutory_rule(uuid,uuid,uuid,uuid,varchar,varchar,uuid,varchar,timestamptz)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'compensation.evaluate_salary_structure_statutory_compatibility(uuid,uuid,uuid,uuid,varchar,timestamptz)',
                'EXECUTE'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_binding',
                'INSERT'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_evaluation',
                'INSERT')
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isFalse();
      assertThat(result.getBoolean(4)).isFalse();
    }
  }

  @Test
  void designTimeCompatibilityDoesNotCallOfficialPayrollStatutoryExecution()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.salary_structure_statutory_compatibility_issues(uuid,uuid,uuid)'
                ::regprocedure)
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("MINIMUM_WAGE_BELOW_THRESHOLD")
          .contains("MINIMUM_WAGE_RUNTIME_BASIS_UNRESOLVED")
          .contains("MINIMUM_WAGE_AUTHORITY_NOT_BOUND")
          .contains("salary_structure_validation_line")
          .doesNotContain("statutory_evaluation_result")
          .doesNotContain("payroll_employee_result");
    }
  }

  @Test
  void salaryStructureApprovalGuardRequiresCurrentPassingStatutoryEvidence()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select pg_get_functiondef(
              'compensation.assert_salary_structure_statutory_approval()'
                ::regprocedure)
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("binding.status = 'ACTIVE'")
          .contains("validation.result_hash = NEW.validation_fingerprint")
          .contains("evaluation.statutory_binding_revision = current_revision")
          .contains("evaluation.validation_status = 'PASS'")
          .contains("evaluation.blocking_issue_count = 0");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }
}
