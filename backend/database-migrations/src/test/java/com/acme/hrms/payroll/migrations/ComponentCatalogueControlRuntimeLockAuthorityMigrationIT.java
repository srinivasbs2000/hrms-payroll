package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ComponentCatalogueControlRuntimeLockAuthorityMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT + "','A','Synthetic Tenant A','test','test')");
    }
  }

  @Test
  void appUsesGovernedRateTableRowLockWhileDirectUpdateAuthorityRemainsRevoked()
      throws Exception {
    UUID identityId = UUID.randomUUID();

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT + "'");
        statement.execute(
            "INSERT INTO compensation.component_rate_table("
                + "id,tenant_id,code,name,created_by,updated_by) VALUES ('"
                + identityId + "','" + TENANT
                + "','LOCK_AUTH_RATE','Lock authority rate','maker','maker')");

        Savepoint beforeDirectLock = connection.setSavepoint();
        assertThatThrownBy(() -> statement.executeQuery(
            "SELECT lifecycle_status FROM compensation.component_rate_table "
                + "WHERE tenant_id='" + TENANT + "' AND id='" + identityId + "' FOR UPDATE"))
            .isInstanceOf(SQLException.class)
            .satisfies(exception ->
                assertThat(((SQLException) exception).getSQLState()).isEqualTo("42501"));
        connection.rollback(beforeDirectLock);

        try (ResultSet result = statement.executeQuery(
            "SELECT compensation.lock_component_rate_table('"
                + TENANT + "','" + identityId + "')")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("PENDING_APPROVAL");
        }
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void allThreeIdentityLocksAreExecutableWithoutGrantingTableUpdate() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT "
                + "has_function_privilege('payroll_app',"
                + "'compensation.lock_component_rate_table(uuid,uuid)','EXECUTE'),"
                + "has_function_privilege('payroll_app',"
                + "'compensation.lock_component_rounding_policy(uuid,uuid)','EXECUTE'),"
                + "has_function_privilege('payroll_app',"
                + "'compensation.lock_component_proration_policy(uuid,uuid)','EXECUTE'),"
                + "has_table_privilege('payroll_app',"
                + "'compensation.component_rate_table','UPDATE'),"
                + "has_table_privilege('payroll_app',"
                + "'compensation.component_rounding_policy','UPDATE'),"
                + "has_table_privilege('payroll_app',"
                + "'compensation.component_proration_policy','UPDATE')")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
      assertThat(result.getBoolean(3)).isTrue();
      assertThat(result.getBoolean(4)).isFalse();
      assertThat(result.getBoolean(5)).isFalse();
      assertThat(result.getBoolean(6)).isFalse();
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}
