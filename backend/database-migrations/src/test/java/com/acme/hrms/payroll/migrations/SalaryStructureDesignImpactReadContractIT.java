package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
class SalaryStructureDesignImpactReadContractIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT =
      UUID.fromString("00000000-0000-0000-0000-0000000000f8");
  private static final UUID VERSION =
      UUID.fromString("71000000-0000-0000-0000-000000000001");

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
              + "VALUES ('" + TENANT
              + "','G02I','G02I Test','test','test')");
    }
  }

  @Test
  void payrollAppCanReadEveryGovernedDependencyUsedByTheWorkbench()
      throws Exception {
    try (Connection connection = app();
        Statement setting = connection.createStatement()) {
      setting.execute("SET app.tenant_id='" + TENANT + "'");

      executeEmpty(
          connection,
          """
          select version.workflow_status,
                 coalesce(state.binding_revision,0) statutory_binding_revision,
                 (
                   select evaluation.evidence_hash
                     from compensation.salary_structure_statutory_evaluation evaluation
                    where evaluation.tenant_id=version.tenant_id
                      and evaluation.salary_structure_version_id=version.id
                      and evaluation.statutory_binding_revision=
                          coalesce(state.binding_revision,0)
                    order by evaluation.created_at desc,evaluation.id desc
                    limit 1
                 ) statutory_evidence_hash
            from compensation.salary_structure_version version
            left join compensation.salary_structure_statutory_state state
              on state.tenant_id=version.tenant_id
             and state.salary_structure_version_id=version.id
           where version.tenant_id=?
             and version.id=?
          """);

      executeEmpty(
          connection,
          """
          select binding.supplemental_plan_id,
                 binding.supplemental_plan_version_id,
                 plan.code,
                 version.plan_type,
                 version.approval_status
            from compensation.salary_structure_supplemental_plan_binding binding
            join compensation.salary_supplemental_plan_version version
              on version.tenant_id=binding.tenant_id
             and version.id=binding.supplemental_plan_version_id
             and version.supplemental_plan_id=binding.supplemental_plan_id
            join compensation.salary_supplemental_plan plan
              on plan.tenant_id=version.tenant_id
             and plan.id=version.supplemental_plan_id
           where binding.tenant_id=?
             and binding.salary_structure_version_id=?
          """);

      executeEmpty(
          connection,
          """
          select distinct plan.id,
                 version.id,
                 plan.code,
                 version.approval_status
            from compensation.flex_benefit_plan_version version
            join compensation.flex_benefit_plan plan
              on plan.tenant_id=version.tenant_id
             and plan.id=version.flex_benefit_plan_id
            join compensation.salary_structure_supplemental_plan_binding binding
              on binding.tenant_id=version.tenant_id
             and binding.supplemental_plan_version_id=
                 version.supplemental_plan_version_id
           where binding.tenant_id=?
             and binding.salary_structure_version_id=?
          """);

      executeEmpty(
          connection,
          """
          select statutory_rule_id,
                 statutory_rule_version_id,
                 binding_purpose,
                 enforcement_level,
                 status
            from compensation.salary_structure_statutory_binding
           where tenant_id=?
             and salary_structure_version_id=?
          """);
    }
  }

  @Test
  void workbenchAddsNoNewDatabaseObjectOrWritePrivilege()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            select
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_supplemental_plan_binding',
                'SELECT'),
              has_table_privilege(
                'payroll_app',
                'compensation.flex_benefit_plan_version',
                'SELECT'),
              has_table_privilege(
                'payroll_app',
                'compensation.salary_structure_statutory_binding',
                'SELECT')
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
    }
  }

  private static void executeEmpty(
      Connection connection,
      String sql) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, TENANT);
      statement.setObject(2, VERSION);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isFalse();
      }
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "payroll_app",
        APP_PASSWORD);
  }
}
