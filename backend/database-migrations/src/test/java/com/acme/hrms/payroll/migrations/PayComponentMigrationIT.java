package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PayComponentMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID LEGACY_COMPONENT_ID =
      UUID.fromString("21000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_COMPONENT_VERSION_ID =
      UUID.fromString("21100000-0000-0000-0000-000000000001");
  private static final UUID SALARY_STRUCTURE_ID =
      UUID.fromString("22000000-0000-0000-0000-000000000001");
  private static final UUID SALARY_STRUCTURE_LINE_ID =
      UUID.fromString("22100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV018WithLegacyComponentData() throws Exception {
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
        .target(MigrationVersion.fromVersion("18"))
        .load()
        .migrate();

    seedLegacyV018Data();

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
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES ('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void legacyVersionAndSalaryStructureLineageArePreserved()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT c.code,v.id,v.version_sequence,"
                    + "v.approval_status,l.component_version_id "
                    + "FROM compensation.pay_component c "
                    + "JOIN compensation.pay_component_version v "
                    + "ON v.tenant_id=c.tenant_id "
                    + "AND v.component_id=c.id "
                    + "JOIN compensation.salary_structure_line l "
                    + "ON l.tenant_id=v.tenant_id "
                    + "AND l.component_version_id=v.id "
                    + "WHERE c.tenant_id='"
                    + TENANT_A
                    + "'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("code")).isEqualTo("BASIC");
      assertThat(result.getObject("id", UUID.class))
          .isEqualTo(LEGACY_COMPONENT_VERSION_ID);
      assertThat(result.getInt("version_sequence")).isOne();
      assertThat(result.getString("approval_status"))
          .isEqualTo("APPROVED");
      assertThat(result.getObject("component_version_id", UUID.class))
          .isEqualTo(LEGACY_COMPONENT_VERSION_ID);
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void appRoleCanApproveAndEndDateButCannotDirectlyUpdate()
      throws Exception {
    UUID identityId =
        UUID.fromString("21000000-0000-0000-0000-000000000002");
    UUID versionId =
        UUID.fromString("21100000-0000-0000-0000-000000000002");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            "INSERT INTO compensation.pay_component("
                + "id,tenant_id,code,name,component_type,"
                + "created_by,updated_by) VALUES ('"
                + identityId
                + "','"
                + TENANT_A
                + "','BASIC_2','Basic Pay 2','EARNING',"
                + "'test','test')");

        statement.execute(
            "INSERT INTO compensation.pay_component_version("
                + "id,tenant_id,component_id,version_sequence,"
                + "formula_type,formula_expression,fixed_amount,"
                + "rounding_scale,effective_from,effective_to,"
                + "approval_status,created_by,updated_by) VALUES ('"
                + versionId
                + "','"
                + TENANT_A
                + "','"
                + identityId
                + "',1,'FIXED',NULL,2000.0000,2,"
                + "'2027-01-01','2028-01-01','DRAFT',"
                + "'test','test')");

        try (ResultSet approved =
            statement.executeQuery(
                "SELECT compensation.approve_pay_component_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','test','"
                    + Instant.parse("2026-07-22T00:00:00Z")
                    + "')")) {
          assertThat(approved.next()).isTrue();
          assertThat(approved.getLong(1)).isOne();
        }

        try (ResultSet ended =
            statement.executeQuery(
                "SELECT compensation.end_date_pay_component_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','2027-12-01',1,'test','"
                    + Instant.parse("2026-07-22T00:01:00Z")
                    + "')")) {
          assertThat(ended.next()).isTrue();
          assertThat(ended.getLong(1)).isOne();
        }

        try (ResultSet state =
            statement.executeQuery(
                "SELECT approval_status,effective_to,version_no "
                    + "FROM compensation.pay_component_version "
                    + "WHERE id='"
                    + versionId
                    + "'")) {
          assertThat(state.next()).isTrue();
          assertThat(state.getString("approval_status"))
              .isEqualTo("APPROVED");
          assertThat(state.getString("effective_to"))
              .isEqualTo("2027-12-01");
          assertThat(state.getLong("version_no")).isEqualTo(2);
        }

        try (ResultSet privilege =
            statement.executeQuery(
                "SELECT has_table_privilege("
                    + "current_user,"
                    + "'compensation.pay_component_version',"
                    + "'UPDATE')")) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
        }
      }
      connection.commit();
    }
  }

  @Test
  void invalidFixedFormulaShapeIsRejected() throws Exception {
    UUID identityId =
        UUID.fromString("21000000-0000-0000-0000-000000000003");
    UUID versionId =
        UUID.fromString("21100000-0000-0000-0000-000000000003");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO compensation.pay_component("
              + "id,tenant_id,code,name,component_type,"
              + "created_by,updated_by) VALUES ('"
              + identityId
              + "','"
              + TENANT_A
              + "','INVALID_FIXED','Invalid Fixed','EARNING',"
              + "'test','test')");

      assertThatThrownBy(
              () ->
                  statement.execute(
                      "INSERT INTO compensation.pay_component_version("
                          + "id,tenant_id,component_id,"
                          + "version_sequence,formula_type,"
                          + "rounding_scale,effective_from,"
                          + "approval_status,created_by,updated_by"
                          + ") VALUES ('"
                          + versionId
                          + "','"
                          + TENANT_A
                          + "','"
                          + identityId
                          + "',1,'FIXED',2,'2027-01-01',"
                          + "'DRAFT','test','test')"))
          .hasMessageContaining(
              "pay_component_version_formula_shape_ck");
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAFromTenantB() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_B + "'");

        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) "
                    + "FROM compensation.pay_component")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) "
                    + "FROM compensation.pay_component_version")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }
      }
      connection.rollback();
    }
  }

  private static void seedLegacyV018Data() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES ('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test')");

      statement.execute(
          "INSERT INTO compensation.pay_component("
              + "id,tenant_id,code,name,component_type,"
              + "created_by,updated_by) VALUES ('"
              + LEGACY_COMPONENT_ID
              + "','"
              + TENANT_A
              + "','BASIC','Basic Pay','EARNING','test','test')");

      statement.execute(
          "INSERT INTO compensation.pay_component_version("
              + "id,tenant_id,component_id,version_no_business,"
              + "formula_type,formula_expression,fixed_amount,"
              + "rounding_scale,effective_from,effective_to,status,"
              + "created_by,updated_by) VALUES ('"
              + LEGACY_COMPONENT_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + LEGACY_COMPONENT_ID
              + "',1,'FIXED',NULL,1000.0000,2,"
              + "'2026-01-01','2027-01-01','APPROVED',"
              + "'test','test')");

      statement.execute(
          "INSERT INTO compensation.salary_structure("
              + "id,tenant_id,code,name,currency,"
              + "effective_from,effective_to,created_by,updated_by"
              + ") VALUES ('"
              + SALARY_STRUCTURE_ID
              + "','"
              + TENANT_A
              + "','DEFAULT','Default Structure','INR',"
              + "'2026-01-01','2027-01-01','test','test')");

      statement.execute(
          "INSERT INTO compensation.salary_structure_line("
              + "id,tenant_id,structure_id,component_version_id,"
              + "sequence_no,target_amount,effective_from,effective_to,"
              + "created_by,updated_by) VALUES ('"
              + SALARY_STRUCTURE_LINE_ID
              + "','"
              + TENANT_A
              + "','"
              + SALARY_STRUCTURE_ID
              + "','"
              + LEGACY_COMPONENT_VERSION_ID
              + "',1,1000.0000,'2026-01-01','2027-01-01',"
              + "'test','test')");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}