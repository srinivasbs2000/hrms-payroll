package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
class CompensationCatalogueMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

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
              + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void exactVersionMembershipUsesMakerCheckerAndPreservesDecimalScale()
      throws Exception {
    Catalogue catalogue = seedApprovedCatalogue();

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID membershipId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_base_membership("
                + "id,tenant_id,payroll_base_id,payroll_base_version_id,component_id,"
                + "component_version_id,membership_sequence,membership_type,inclusion_percent,"
                + "effective_from,approval_status,created_by,updated_by) VALUES ('"
                + membershipId + "','" + TENANT_A + "','" + catalogue.baseId + "','"
                + catalogue.baseVersionId + "','" + catalogue.componentId + "','"
                + catalogue.componentVersionId
                + "',1,'INCLUDE',33.33333333,'2027-01-01','DRAFT','membership-maker','membership-maker')");

        assertThat(approveMembership(statement, membershipId, "membership-maker")).isZero();
        assertThat(approveMembership(statement, membershipId, "membership-checker")).isOne();

        try (ResultSet result = statement.executeQuery(
            "SELECT inclusion_percent,approval_status FROM compensation.component_base_membership "
                + "WHERE id='" + membershipId + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getBigDecimal("inclusion_percent"))
              .isEqualByComparingTo(new BigDecimal("33.33333333"));
          assertThat(result.getString("approval_status")).isEqualTo("APPROVED");
        }

        try (ResultSet privilege = statement.executeQuery(
            "SELECT has_table_privilege(current_user,"
                + "'compensation.component_base_membership','UPDATE')")) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
        }
      }
      connection.commit();
    }
  }

  @Test
  void exactForeignKeysRejectMismatchedComponentIdentity() throws Exception {
    Catalogue catalogue = seedApprovedCatalogue();
    UUID otherComponent = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO compensation.pay_component(id,tenant_id,code,name,component_type,"
              + "created_by,updated_by) VALUES ('" + otherComponent + "','" + TENANT_A
              + "','OTHER_" + otherComponent.toString().substring(0, 8).toUpperCase()
              + "','Other','EARNING','maker','maker')");
      assertThatThrownBy(() -> statement.execute(
          "INSERT INTO compensation.component_base_membership("
              + "id,tenant_id,payroll_base_id,payroll_base_version_id,component_id,"
              + "component_version_id,membership_sequence,membership_type,inclusion_percent,"
              + "effective_from,created_by,updated_by) VALUES ('" + membershipId + "','"
              + TENANT_A + "','" + catalogue.baseId + "','" + catalogue.baseVersionId + "','"
              + otherComponent + "','" + catalogue.componentVersionId
              + "',1,'INCLUDE',100,'2027-01-01','maker','maker')"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("component_base_membership_component_version_fk");
    }
  }

  @Test
  void retirementIsBlockedByApprovedFutureDependencies() throws Exception {
    Catalogue catalogue = seedApprovedCatalogue();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute("SAVEPOINT before_component_retirement");
        assertSqlState("P5A23", () -> statement.executeQuery(
            "SELECT compensation.retire_pay_component('" + TENANT_A + "','"
                + catalogue.componentId
                + "','2026-12-31',1,'still active','checker','2026-08-05T00:00:00Z')"));
        statement.execute("ROLLBACK TO SAVEPOINT before_component_retirement");
        assertSqlState("P5A24", () -> statement.executeQuery(
            "SELECT compensation.retire_payroll_base('" + TENANT_A + "','"
                + catalogue.baseId
                + "','2026-12-31',1,'still active','checker','2026-08-05T00:00:00Z')"));
      }
      connection.rollback();
    }
  }

  @Test
  void newCatalogueTablesAreForcedRlsAndTenantIsolated() throws Exception {
    seedApprovedCatalogue();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_B + "'");
        assertThat(count(statement, "compensation.payroll_base")).isZero();
        assertThat(count(statement, "compensation.payroll_base_version")).isZero();
        assertThat(count(statement, "compensation.component_base_membership")).isZero();
      }
      connection.rollback();
    }
    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                + "WHERE n.nspname='compensation' AND c.relname IN "
                + "('payroll_base','payroll_base_version','component_base_membership') "
                + "AND c.relrowsecurity AND c.relforcerowsecurity")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(3);
    }
  }

  private static Catalogue seedApprovedCatalogue() throws Exception {
    UUID componentId = UUID.randomUUID();
    UUID componentVersionId = UUID.randomUUID();
    UUID baseId = UUID.randomUUID();
    UUID baseVersionId = UUID.randomUUID();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        String suffix = componentId.toString().substring(0, 8).toUpperCase();
        statement.execute(
            "INSERT INTO compensation.pay_component(id,tenant_id,code,name,component_type,"
                + "created_by,updated_by) VALUES ('" + componentId + "','" + TENANT_A
                + "','COMP_" + suffix + "','Component','EARNING','component-maker','component-maker')");
        statement.execute(
            "INSERT INTO compensation.pay_component_version("
                + "id,tenant_id,component_id,version_sequence,formula_type,fixed_amount,"
                + "rounding_scale,catalogue_schema_version,component_category,component_subcategory,"
                + "cash_impact,payee_type,payment_channel,settlement_timing,payslip_visibility,"
                + "zero_value_visibility,negative_value_policy,frequency,value_nature,"
                + "amount_representation,tax_treatment,payroll_timing,effective_from,approval_status,"
                + "created_by,updated_by) VALUES ('" + componentVersionId + "','" + TENANT_A
                + "','" + componentId + "',1,'FIXED',1000,2,1,'CASH_EARNING','BASIC_PAY',"
                + "'INCREASE','EMPLOYEE','PAYROLL_BANK','CURRENT_PERIOD','SHOW','SUPPRESS',"
                + "'PROHIBIT','MONTHLY','FIXED','MONTHLY_AMOUNT','DELEGATED','REGULAR',"
                + "'2027-01-01','DRAFT','component-maker','component-maker')");
        statement.execute(
            "INSERT INTO compensation.payroll_base(id,tenant_id,code,name,created_by,updated_by) "
                + "VALUES ('" + baseId + "','" + TENANT_A + "','BASE_" + suffix
                + "','Base','base-maker','base-maker')");
        statement.execute(
            "INSERT INTO compensation.payroll_base_version(id,tenant_id,payroll_base_id,"
                + "version_sequence,base_category,aggregation_method,effective_from,approval_status,"
                + "created_by,updated_by) VALUES ('" + baseVersionId + "','" + TENANT_A + "','"
                + baseId + "',1,'CALCULATION','SUM','2027-01-01','DRAFT','base-maker','base-maker')");
        try (ResultSet componentApproval = statement.executeQuery(
            "SELECT compensation.approve_pay_component_version('" + TENANT_A + "','"
                + componentVersionId + "','component-checker','2026-08-05T00:00:00Z')")) {
          assertThat(componentApproval.next()).isTrue();
          assertThat(componentApproval.getLong(1)).isOne();
        }
        try (ResultSet baseApproval = statement.executeQuery(
            "SELECT compensation.approve_payroll_base_version('" + TENANT_A + "','"
                + baseVersionId + "','base-checker','2026-08-05T00:00:00Z')")) {
          assertThat(baseApproval.next()).isTrue();
          assertThat(baseApproval.getLong(1)).isOne();
        }
      }
      connection.commit();
    }
    return new Catalogue(componentId, componentVersionId, baseId, baseVersionId);
  }

  private static long approveMembership(
      Statement statement, UUID membershipId, String actor) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "SELECT compensation.approve_component_base_membership('" + TENANT_A + "','"
            + membershipId + "','" + actor + "','2026-08-05T00:02:00Z')")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long count(Statement statement, String table) throws Exception {
    try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void assertSqlState(String state, SqlRunnable runnable) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(SQLException.class)
        .extracting(error -> ((SQLException) error).getSQLState())
        .isEqualTo(state);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private record Catalogue(
      UUID componentId, UUID componentVersionId, UUID baseId, UUID baseVersionId) {}

  @FunctionalInterface
  private interface SqlRunnable {
    void run() throws Exception;
  }
}
