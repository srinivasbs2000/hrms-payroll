package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

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
class PayGroupMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID LEGAL_ID =
      UUID.fromString("11000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID =
      UUID.fromString("11100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID =
      UUID.fromString("12000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID =
      UUID.fromString("12100000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID =
      UUID.fromString("13000000-0000-0000-0000-000000000001");
  private static final UUID PERIOD_ID =
      UUID.fromString("13100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_PAY_GROUP_VERSION_ID =
      UUID.fromString("14000000-0000-0000-0000-000000000001");
  private static final UUID CYCLE_ID =
      UUID.fromString("15000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV016WithLegacyPayGroupData() throws Exception {
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
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("16"))
        .load()
        .migrate();

    seedLegacyV016Data();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void legacyVersionIdAndPayrollCycleLineageArePreserved() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT i.code,v.id,v.approval_status,c.pay_group_id "
                    + "FROM organisation.pay_group i "
                    + "JOIN organisation.pay_group_version v "
                    + "ON v.tenant_id=i.tenant_id AND v.pay_group_id=i.id "
                    + "JOIN payroll_ops.payroll_cycle c "
                    + "ON c.tenant_id=v.tenant_id AND c.pay_group_id=v.id "
                    + "WHERE i.tenant_id='"
                    + TENANT_A
                    + "'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("code")).isEqualTo("MONTHLY_IN");
      assertThat(result.getObject("id", UUID.class))
          .isEqualTo(LEGACY_PAY_GROUP_VERSION_ID);
      assertThat(result.getString("approval_status")).isEqualTo("APPROVED");
      assertThat(result.getObject("pay_group_id", UUID.class))
          .isEqualTo(LEGACY_PAY_GROUP_VERSION_ID);
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void appRoleCanCreateAndApproveAValidVersionButCannotMutateItDirectly()
      throws Exception {
    UUID identityId = UUID.fromString("14000000-0000-0000-0000-000000000002");
    UUID versionId = UUID.fromString("14100000-0000-0000-0000-000000000002");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            "INSERT INTO organisation.pay_group("
                + "id,tenant_id,code,created_by,updated_by) VALUES ('"
                + identityId
                + "','"
                + TENANT_A
                + "','MONTHLY_IN_2','test','test')");
        statement.execute(
            "INSERT INTO organisation.pay_group_version("
                + "id,tenant_id,pay_group_id,payroll_statutory_unit_version_id,"
                + "calendar_id,version_sequence,name,currency,proration_method,"
                + "effective_from,effective_to,approval_status,created_by,updated_by"
                + ") VALUES ('"
                + versionId
                + "','"
                + TENANT_A
                + "','"
                + identityId
                + "','"
                + PSU_VERSION_ID
                + "','"
                + CALENDAR_ID
                + "',1,'Monthly India 2','INR','CALENDAR_DAYS',"
                + "'2026-01-01','2027-01-01','DRAFT','test','test')");

        try (ResultSet approved =
            statement.executeQuery(
                "SELECT organisation.approve_pay_group_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','test','"
                    + Instant.parse("2026-07-21T00:00:00Z")
                    + "')")) {
          assertThat(approved.next()).isTrue();
          assertThat(approved.getLong(1)).isOne();
        }

        try (ResultSet status =
            statement.executeQuery(
                "SELECT approval_status,version_no "
                    + "FROM organisation.pay_group_version WHERE id='"
                    + versionId
                    + "'")) {
          assertThat(status.next()).isTrue();
          assertThat(status.getString("approval_status")).isEqualTo("APPROVED");
          assertThat(status.getLong("version_no")).isOne();
        }

        try (ResultSet privilege =
            statement.executeQuery(
                "SELECT has_table_privilege("
                    + "current_user,'organisation.pay_group_version','UPDATE')")) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
        }
      }
      connection.commit();
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAFromTenantB() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_B + "'");
        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM organisation.pay_group")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }
        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM organisation.pay_group_version")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }
      }
      connection.rollback();
    }
  }

  private static void seedLegacyV016Data() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test')");

      statement.execute(
          "INSERT INTO organisation.legal_entity("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + LEGAL_ID
              + "','"
              + TENANT_A
              + "','ACME_IN','test','test')");
      statement.execute(
          "INSERT INTO organisation.legal_entity_version("
              + "id,tenant_id,legal_entity_id,version_sequence,name,country_code,"
              + "currency,effective_from,effective_to,approval_status,approved_at,"
              + "approved_by,created_by,updated_by) VALUES ('"
              + LEGAL_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + LEGAL_ID
              + "',1,'Acme India','IN','INR','2026-01-01','2027-01-01',"
              + "'APPROVED',clock_timestamp(),'test','test','test')");

      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + PSU_ID
              + "','"
              + TENANT_A
              + "','ACME_PSU','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit_version("
              + "id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,"
              + "version_sequence,name,effective_from,effective_to,approval_status,"
              + "approved_at,approved_by,created_by,updated_by) VALUES ('"
              + PSU_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + PSU_ID
              + "','"
              + LEGAL_VERSION_ID
              + "',1,'Acme PSU','2026-01-01','2027-01-01','APPROVED',"
              + "clock_timestamp(),'test','test','test')");

      statement.execute(
          "INSERT INTO organisation.payroll_calendar("
              + "id,tenant_id,code,name,frequency,timezone,created_by,updated_by"
              + ") VALUES ('"
              + CALENDAR_ID
              + "','"
              + TENANT_A
              + "','MONTHLY_IN','Monthly India','MONTHLY','Asia/Kolkata',"
              + "'test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_period("
              + "id,tenant_id,calendar_id,period_code,period_start,period_end,"
              + "payment_date,status,created_by,updated_by) VALUES ('"
              + PERIOD_ID
              + "','"
              + TENANT_A
              + "','"
              + CALENDAR_ID
              + "','2026-01','2026-01-01','2026-01-31','2026-01-31',"
              + "'OPEN','test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_group("
              + "id,tenant_id,statutory_unit_id,calendar_id,code,name,currency,"
              + "proration_method,effective_from,effective_to,created_by,updated_by"
              + ") VALUES ('"
              + LEGACY_PAY_GROUP_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + PSU_VERSION_ID
              + "','"
              + CALENDAR_ID
              + "','MONTHLY_IN','Monthly India','INR','CALENDAR_DAYS',"
              + "'2026-01-01','2027-01-01','test','test')");
      statement.execute(
          "INSERT INTO payroll_ops.payroll_cycle("
              + "id,tenant_id,pay_group_id,pay_period_id,cycle_type,status,"
              + "created_by,updated_by) VALUES ('"
              + CYCLE_ID
              + "','"
              + TENANT_A
              + "','"
              + LEGACY_PAY_GROUP_VERSION_ID
              + "','"
              + PERIOD_ID
              + "','REGULAR','DRAFT','test','test')");
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
