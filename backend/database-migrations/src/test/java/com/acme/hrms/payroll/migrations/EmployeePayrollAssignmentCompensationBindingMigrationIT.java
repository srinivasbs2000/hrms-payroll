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
class EmployeePayrollAssignmentCompensationBindingMigrationIT {
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
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void v050TablesAreTenantIsolatedAndForcedRls() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select relname,relrowsecurity,relforcerowsecurity
              from pg_class
             where relnamespace='employee_payroll'::regnamespace
               and relname in (
                 'pay_group_assignment_impact_period',
                 'compensation_change_event',
                 'compensation_change_impact',
                 'employee_component_override',
                 'payroll_lifecycle_lineage')
             order by relname
            """)) {
      int count = 0;
      while (result.next()) {
        count++;
        assertThat(result.getBoolean("relrowsecurity")).isTrue();
        assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
      }
      assertThat(count).isEqualTo(5);
    }
  }

  @Test
  void v050PreservesLegacyColumnsAndAddsCompleteBindingContracts()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              exists(select 1 from information_schema.columns
                where table_schema='employee_payroll'
                  and table_name='payroll_relationship_version'
                  and column_name='payroll_statutory_unit_version_id'),
              exists(select 1 from information_schema.columns
                where table_schema='employee_payroll'
                  and table_name='payroll_assignment'
                  and column_name='source_work_assignment_ref'),
              exists(select 1 from information_schema.columns
                where table_schema='employee_payroll'
                  and table_name='payroll_assignment_version'
                  and column_name='payroll_role'),
              exists(select 1 from information_schema.columns
                where table_schema='employee_payroll'
                  and table_name='salary_assignment'
                  and column_name='target_type'),
              exists(select 1 from information_schema.columns
                where table_schema='employee_payroll'
                  and table_name='salary_assignment'
                  and column_name='monthly_amount')
            """)) {
      assertThat(result.next()).isTrue();
      for (int index = 1; index <= 5; index++) {
        assertThat(result.getBoolean(index)).isTrue();
      }
    }
  }

  @Test
  void v050KeepsLegacyInsertDefaultsAndAvoidsPlpgsqlNameShadowing()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet defaults = statement.executeQuery(
            """
            select table_name,column_name,column_default
              from information_schema.columns
             where table_schema='employee_payroll'
               and (
                 (table_name='payroll_relationship_version'
                  and column_name='boundary_schema_version')
                 or
                 (table_name='payroll_assignment_version'
                  and column_name='binding_schema_version')
                 or
                 (table_name='pay_group_assignment'
                  and column_name='contract_schema_version')
                 or
                 (table_name='salary_assignment'
                  and column_name='contract_schema_version')
               )
             order by table_name,column_name
            """)) {
      int count = 0;
      while (defaults.next()) {
        count++;
        assertThat(defaults.getString("column_default")).contains("0");
      }
      assertThat(count).isEqualTo(4);
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet function = statement.executeQuery(
            """
            select prosrc
              from pg_proc
             where oid=(
               'employee_payroll.assert_pay_group_assignment_dependencies()'
             )::regprocedure
            """)) {
      assertThat(function.next()).isTrue();
      assertThat(function.getString(1))
          .contains("v_establishment_id")
          .contains("v_relationship_version_id")
          .doesNotContain("id=establishment_id")
          .doesNotContain("id=relationship_version_id");
    }
  }

  @Test
  void runtimeUsesControlledMutationFunctionsForNewBindingState()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              has_function_privilege(
                'payroll_app',
                'employee_payroll.bind_payroll_assignment_source_ref(uuid,uuid,varchar,bigint,varchar,timestamptz)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'employee_payroll.assess_compensation_change(uuid,uuid,date,varchar,timestamptz)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'employee_payroll.approve_compensation_change_event(uuid,uuid,varchar,timestamptz)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'employee_payroll.approve_employee_component_override(uuid,uuid,varchar,timestamptz)',
                'EXECUTE'),
              has_function_privilege(
                'payroll_app',
                'employee_payroll.approve_payroll_lifecycle_lineage(uuid,uuid,varchar,timestamptz)',
                'EXECUTE'),
              has_table_privilege(
                'payroll_app','employee_payroll.compensation_change_event','UPDATE'),
              has_table_privilege(
                'payroll_app','employee_payroll.employee_component_override','DELETE'),
              has_table_privilege(
                'payroll_app','employee_payroll.compensation_change_impact','INSERT'),
              has_table_privilege(
                'payroll_app','employee_payroll.pay_group_assignment_impact_period','INSERT')
            """)) {
      assertThat(result.next()).isTrue();
      for (int index = 1; index <= 5; index++) {
        assertThat(result.getBoolean(index)).isTrue();
      }
      assertThat(result.getBoolean(6)).isFalse();
      assertThat(result.getBoolean(7)).isFalse();
      assertThat(result.getBoolean(8)).isFalse();
      assertThat(result.getBoolean(9)).isFalse();
    }
  }

  @Test
  void approvalFunctionsPreserveLegacyAndRequireCompleteV050Contracts()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select r.prosrc,a.prosrc,g.prosrc,s.prosrc
              from pg_proc r,pg_proc a,pg_proc g,pg_proc s
             where r.oid=(
               'employee_payroll.approve_payroll_relationship_version'
               || '(uuid,uuid,varchar,timestamptz)')::regprocedure
               and a.oid=(
               'employee_payroll.approve_payroll_assignment_version'
               || '(uuid,uuid,varchar,timestamptz)')::regprocedure
               and g.oid='employee_payroll.approve_pay_group_assignment(uuid,uuid,varchar,timestamptz)'::regprocedure
               and s.oid='employee_payroll.approve_salary_assignment(uuid,uuid,varchar,timestamptz)'::regprocedure
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1))
          .contains("boundary_schema_version=0")
          .contains("boundary_schema_version=1");
      assertThat(result.getString(2))
          .contains("binding_schema_version=0")
          .contains("binding_schema_version=1");
      assertThat(result.getString(3))
          .contains("contract_schema_version=0")
          .contains("contract_schema_version=1");
      assertThat(result.getString(4))
          .contains("contract_schema_version=1")
          .contains("contract_schema_version=0")
          .contains("structure_schema_version=0");
    }
  }

  @Test
  void runtimeDraftTriggerKeepsCompensationOnlyFieldsTableScoped()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          """
          select prosrc
            from pg_proc
           where oid='employee_payroll.require_v050_runtime_draft_insert()'::regprocedure
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1))
            .doesNotContain("assessment_through")
            .doesNotContain("impact_assessed_at")
            .doesNotContain("impact_assessed_by");
      }

      try (ResultSet result = statement.executeQuery(
          """
          select prosrc
            from pg_proc
           where oid=(
             'employee_payroll.'
             || 'require_compensation_change_runtime_assessment_control()'
           )::regprocedure
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1))
            .contains("assessment_through")
            .contains("impact_assessed_at")
            .contains("impact_assessed_by");
      }

      try (ResultSet result = statement.executeQuery(
          """
          select c.relname,p.proname
            from pg_trigger t
            join pg_class c on c.oid=t.tgrelid
            join pg_proc p on p.oid=t.tgfoid
           where not t.tgisinternal
             and c.relnamespace='employee_payroll'::regnamespace
             and p.proname in (
               'require_v050_runtime_draft_insert',
               'require_compensation_change_runtime_assessment_control')
           order by c.relname,p.proname
          """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("compensation_change_event");
        assertThat(result.getString(2))
            .isEqualTo("require_compensation_change_runtime_assessment_control");
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("compensation_change_event");
        assertThat(result.getString(2)).isEqualTo("require_v050_runtime_draft_insert");
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("employee_component_override");
        assertThat(result.getString(2)).isEqualTo("require_v050_runtime_draft_insert");
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("payroll_lifecycle_lineage");
        assertThat(result.getString(2)).isEqualTo("require_v050_runtime_draft_insert");
        assertThat(result.next()).isFalse();
      }
    }
  }

  @Test
  void impactAssessmentIsAppendOnlyAndDoesNotExecuteRetroPayroll()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select prosrc
              from pg_proc
             where oid='employee_payroll.assess_compensation_change(uuid,uuid,date,varchar,timestamptz)'::regprocedure
            """)) {
      assertThat(result.next()).isTrue();
      String definition = result.getString(1);
      assertThat(definition)
          .contains("compensation_change_impact")
          .contains("assessment_through IS NULL")
          .doesNotContain("DELETE FROM")
          .doesNotContain("payroll_result")
          .doesNotContain("payment");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
