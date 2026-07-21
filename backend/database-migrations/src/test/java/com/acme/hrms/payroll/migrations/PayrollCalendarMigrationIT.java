package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PayrollCalendarMigrationIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";
  private static final UUID TENANT_A = UUID.fromString(
      "00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString(
      "00000000-0000-0000-0000-00000000000b");
  private static final UUID CALENDAR_A = UUID.fromString(
      "30000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_PERIOD = UUID.fromString(
      "31000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV017WithLegacyCalendarData()
      throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("17"))
        .load()
        .migrate();

    seedLegacyCalendarData();

    Flyway flyway = Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load();

    flyway.migrate();
    flyway.validate();
  }

  @Test
  void v017CalendarAndPeriodIdentifiersArePreserved()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT c.id calendar_id,p.id period_id,"
                + "p.period_start,p.period_end "
                + "FROM organisation.payroll_calendar c "
                + "JOIN organisation.pay_period p "
                + "ON p.tenant_id=c.tenant_id "
                + "AND p.calendar_id=c.id "
                + "WHERE c.tenant_id='"
                + TENANT_A
                + "' AND c.id='"
                + CALENDAR_A
                + "' AND p.id='"
                + LEGACY_PERIOD
                + "'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject(
          "calendar_id", UUID.class)).isEqualTo(CALENDAR_A);
      assertThat(result.getObject(
          "period_id", UUID.class)).isEqualTo(LEGACY_PERIOD);
      assertThat(result.getObject(
          "period_start", LocalDate.class))
          .isEqualTo(LocalDate.of(2027, 12, 1));
      assertThat(result.getObject(
          "period_end", LocalDate.class))
          .isEqualTo(LocalDate.of(2027, 12, 31));
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void generationIsLeapYearSafeIdempotentAndConflictAware()
      throws Exception {
    List<UUID> first = generate(
        TENANT_A, TENANT_A, CALENDAR_A, 2028, 31);
    List<UUID> second = generate(
        TENANT_A, TENANT_A, CALENDAR_A, 2028, 31);

    assertThat(first).hasSize(12);
    assertThat(second).containsExactlyElementsOf(first);

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        try (ResultSet result = statement.executeQuery(
            "SELECT period_start,period_end,payment_date "
                + "FROM organisation.pay_period "
                + "WHERE tenant_id='"
                + TENANT_A
                + "' AND calendar_id='"
                + CALENDAR_A
                + "' AND period_code='2028-02'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getObject(
              "period_start", LocalDate.class))
              .isEqualTo(LocalDate.of(2028, 2, 1));
          assertThat(result.getObject(
              "period_end", LocalDate.class))
              .isEqualTo(LocalDate.of(2028, 2, 29));
          assertThat(result.getObject(
              "payment_date", LocalDate.class))
              .isEqualTo(LocalDate.of(2028, 2, 29));
        }
      }
      connection.rollback();
    }

    assertSqlState("23514", () ->
        generate(TENANT_A, TENANT_A, CALENDAR_A, 2028, 28));
  }

  @Test
  void controlledCommandsEnforceTenantAndRuntimePrivileges()
      throws Exception {
    UUID created;
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        try (ResultSet result = statement.executeQuery(
            "SELECT organisation.create_monthly_payroll_calendar("
                + "'" + TENANT_A + "',"
                + "'SECOND_MONTHLY',"
                + "'Second Monthly Calendar',"
                + "'Asia/Kolkata',"
                + "'calendar-test',"
                + "clock_timestamp())")) {
          assertThat(result.next()).isTrue();
          created = result.getObject(1, UUID.class);
        }
      }
      connection.commit();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        try (ResultSet result = statement.executeQuery(
            "SELECT count(*) "
                + "FROM organisation.payroll_calendar "
                + "WHERE id='" + created + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isOne();
        }
      }
      connection.rollback();
    }

    assertSqlState("42501", () -> {
      try (Connection connection = app();
          Statement statement = connection.createStatement()) {
        connection.setAutoCommit(false);
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            "INSERT INTO organisation.payroll_calendar("
                + "tenant_id,code,name,frequency,timezone,"
                + "created_by,updated_by) VALUES ('"
                + TENANT_A
                + "','DIRECT','Forbidden','MONTHLY',"
                + "'Asia/Kolkata','test','test')");
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = app();
          Statement statement = connection.createStatement()) {
        connection.setAutoCommit(false);
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            "UPDATE organisation.pay_period "
                + "SET payment_date=period_start "
                + "WHERE id='" + LEGACY_PERIOD + "'");
      }
    });

    assertSqlState("42501", () ->
        generate(TENANT_B, TENANT_A, CALENDAR_A, 2029, 31));
  }

  @Test
  void payrollCycleRejectsPeriodFromAnotherCalendar()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      seedPayGroupDependencies(statement);

      assertSqlState("23514", () -> statement.execute(
          "INSERT INTO payroll_ops.payroll_cycle("
              + "id,tenant_id,pay_group_id,pay_period_id,"
              + "cycle_type,status,created_by,updated_by) VALUES ("
              + "'60000000-0000-0000-0000-000000000099',"
              + "'" + TENANT_A + "',"
              + "'32000000-0000-0000-0000-000000000099',"
              + "'31000000-0000-0000-0000-000000000099',"
              + "'REGULAR','DRAFT','test','test')"));
    }
  }

  private static List<UUID> generate(
      UUID contextTenant,
      UUID requestedTenant,
      UUID calendarId,
      int year,
      int paymentDay) throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      List<UUID> ids = new ArrayList<>();
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + contextTenant + "'");
        try (ResultSet result = statement.executeQuery(
            "SELECT id FROM "
                + "organisation.generate_monthly_pay_periods("
                + "'" + requestedTenant + "',"
                + "'" + calendarId + "',"
                + year + ","
                + paymentDay + ","
                + "'calendar-test',clock_timestamp()) "
                + "ORDER BY period_start")) {
          while (result.next()) {
            ids.add(result.getObject("id", UUID.class));
          }
        }
      }
      connection.commit();
      return ids;
    }
  }

  private static void seedPayGroupDependencies(
      Statement statement) throws Exception {
    statement.execute(
        "INSERT INTO organisation.legal_entity("
            + "id,tenant_id,code,created_by,updated_by) VALUES ("
            + "'11000000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "','CAL_LE','test','test')");
    statement.execute(
        "INSERT INTO organisation.legal_entity_version("
            + "id,tenant_id,legal_entity_id,version_sequence,"
            + "name,country_code,currency,effective_from,"
            + "effective_to,approval_status,approved_at,"
            + "approved_by,created_by,updated_by) VALUES ("
            + "'11100000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "',"
            + "'11000000-0000-0000-0000-000000000099',"
            + "1,'Calendar Legal Entity','IN','INR',"
            + "'2028-01-01','2030-01-01','APPROVED',"
            + "clock_timestamp(),'test','test','test')");
    statement.execute(
        "INSERT INTO organisation.payroll_statutory_unit("
            + "id,tenant_id,code,created_by,updated_by) VALUES ("
            + "'12000000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "','CAL_PSU','test','test')");
    statement.execute(
        "INSERT INTO organisation.payroll_statutory_unit_version("
            + "id,tenant_id,payroll_statutory_unit_id,"
            + "legal_entity_version_id,version_sequence,name,"
            + "effective_from,effective_to,approval_status,"
            + "approved_at,approved_by,created_by,updated_by"
            + ") VALUES ("
            + "'12100000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "',"
            + "'12000000-0000-0000-0000-000000000099',"
            + "'11100000-0000-0000-0000-000000000099',"
            + "1,'Calendar PSU','2028-01-01','2030-01-01',"
            + "'APPROVED',clock_timestamp(),"
            + "'test','test','test')");
    statement.execute(
        "INSERT INTO organisation.payroll_calendar("
            + "id,tenant_id,code,name,frequency,timezone,"
            + "created_by,updated_by) VALUES ("
            + "'30000000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "','OTHER_CAL',"
            + "'Other Calendar','MONTHLY','Asia/Kolkata',"
            + "'test','test')");
    statement.execute(
        "INSERT INTO organisation.pay_period("
            + "id,tenant_id,calendar_id,period_code,"
            + "period_start,period_end,payment_date,status,"
            + "created_by,updated_by) VALUES ("
            + "'31000000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "',"
            + "'30000000-0000-0000-0000-000000000099',"
            + "'2028-01','2028-01-01','2028-01-31',"
            + "'2028-01-31','OPEN','test','test')");
    statement.execute(
        "INSERT INTO organisation.pay_group("
            + "id,tenant_id,code,created_by,updated_by) VALUES ("
            + "'32000000-0000-0000-0000-000000000199',"
            + "'" + TENANT_A + "','CAL_PG','test','test')");
    statement.execute(
        "INSERT INTO organisation.pay_group_version("
            + "id,tenant_id,pay_group_id,"
            + "payroll_statutory_unit_version_id,calendar_id,"
            + "version_sequence,name,currency,proration_method,"
            + "effective_from,effective_to,approval_status,"
            + "approved_at,approved_by,created_by,updated_by"
            + ") VALUES ("
            + "'32000000-0000-0000-0000-000000000099',"
            + "'" + TENANT_A + "',"
            + "'32000000-0000-0000-0000-000000000199',"
            + "'12100000-0000-0000-0000-000000000099',"
            + "'" + CALENDAR_A + "',"
            + "1,'Calendar Pay Group','INR','CALENDAR_DAYS',"
            + "'2028-01-01','2030-01-01','APPROVED',"
            + "clock_timestamp(),'test','test','test')");
  }

  private static void seedLegacyCalendarData()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_calendar("
              + "id,tenant_id,code,name,frequency,timezone,"
              + "created_by,updated_by) VALUES ('"
              + CALENDAR_A
              + "','" + TENANT_A
              + "','MONTHLY_IN','Monthly India','MONTHLY',"
              + "'Asia/Kolkata','test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_period("
              + "id,tenant_id,calendar_id,period_code,"
              + "period_start,period_end,payment_date,status,"
              + "created_by,updated_by) VALUES ('"
              + LEGACY_PERIOD
              + "','" + TENANT_A
              + "','" + CALENDAR_A
              + "','2027-12','2027-12-01','2027-12-31',"
              + "'2027-12-31','OPEN','test','test')");
    }
  }

  private static void createRoles() throws Exception {
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
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public "
              + "TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
      statement.execute(
          "REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute(
          "REVOKE CREATE ON SCHEMA public FROM payroll_app");
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

  private static void assertSqlState(
      String expected, SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .extracting(error -> ((SQLException) error).getSQLState())
        .isEqualTo(expected);
  }

  @FunctionalInterface
  interface SqlAction {
    void run() throws Exception;
  }
}
