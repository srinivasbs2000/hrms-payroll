package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PayGroupCalendarPublicationMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID LEGAL = UUID.fromString("81000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION = UUID.fromString("81100000-0000-0000-0000-000000000001");
  private static final UUID PSU = UUID.fromString("82000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION = UUID.fromString("82100000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP = UUID.fromString("83000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION = UUID.fromString("83100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    createRoles();
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load().migrate();
  }

  @BeforeEach
  void reset() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute("INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES ('" + TENANT + "','G03','G03 synthetic','test','test')");
    }
  }

  @Test
  void runtimeLegacyCreationCommandsRemainCompatibleButRequirePublication() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);

        UUID generalized = scalarUuid(
            statement,
            "SELECT organisation.create_payroll_calendar('"
                + TENANT
                + "'::uuid,'LEGACY_GENERIC'::varchar,'Legacy Generic'::varchar,"
                + "'MONTHLY'::varchar,'Asia/Kolkata'::varchar,NULL::integer,"
                + "false,ARRAY[6,7]::smallint[],'test'::varchar,clock_timestamp())");

        UUID monthly = scalarUuid(
            statement,
            "SELECT organisation.create_monthly_payroll_calendar('"
                + TENANT
                + "'::uuid,'LEGACY_MONTHLY'::varchar,'Legacy Monthly'::varchar,"
                + "'Asia/Kolkata'::varchar,'test'::varchar,clock_timestamp())");

        assertThat(
                scalarLong(
                    statement,
                    "SELECT count(*) FROM organisation.payroll_calendar "
                        + "WHERE tenant_id='"
                        + TENANT
                        + "' AND id IN ('"
                        + generalized
                        + "','"
                        + monthly
                        + "') AND publication_required"))
            .isEqualTo(2);
        connection.commit();
      }
    }
  }

  @Test
  void publishAndRetireAreAppendOnlyAndOperationallyVisible() throws Exception {
    UUID calendar = createReadyCalendar("MONTHLY_G03", "MONTHLY", null, false);
    UUID period = firstPeriod(calendar);

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        UUID published = scalarUuid(statement, "SELECT organisation.publish_payroll_calendar('" + TENANT + "'::uuid,'" + calendar + "'::uuid,'initial publication'::varchar,'test'::varchar,clock_timestamp())");
        assertThat(published).isNotNull();
        assertThat(scalarText(statement, "SELECT lifecycle_status FROM organisation.payroll_calendar_operational_v WHERE tenant_id='" + TENANT + "' AND id='" + calendar + "'"))
            .isEqualTo("PUBLISHED");

        Savepoint mutation = connection.setSavepoint();
        assertSqlState("23514", () -> statement.execute("UPDATE organisation.pay_period SET payment_date=payment_date+1 WHERE tenant_id='" + TENANT + "' AND id='" + period + "'"));
        connection.rollback(mutation);

        UUID retired = scalarUuid(statement, "SELECT organisation.retire_payroll_calendar('" + TENANT + "'::uuid,'" + calendar + "'::uuid,'policy retired'::varchar,'test'::varchar,clock_timestamp())");
        assertThat(retired).isNotEqualTo(published);
        assertThat(scalarText(statement, "SELECT lifecycle_status FROM organisation.payroll_calendar_operational_v WHERE tenant_id='" + TENANT + "' AND id='" + calendar + "'"))
            .isEqualTo("RETIRED");
        assertThat(scalarLong(statement, "SELECT count(*) FROM organisation.payroll_calendar_lifecycle_event WHERE tenant_id='" + TENANT + "' AND calendar_id='" + calendar + "'"))
            .isEqualTo(2);
        connection.commit();
      }
    }
  }

  @Test
  void amendmentPublishesSuccessorWithoutRewritingSourceHistory() throws Exception {
    UUID source = createReadyCalendar("WEEKLY_G03", "WEEKLY", null, false);
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        scalarUuid(statement, "SELECT organisation.publish_payroll_calendar('" + TENANT + "'::uuid,'" + source + "'::uuid,'source'::varchar,'test'::varchar,clock_timestamp())");
        UUID successor = scalarUuid(statement, "SELECT organisation.amend_payroll_calendar('" + TENANT + "'::uuid,'" + source + "'::uuid,'test'::varchar,clock_timestamp())");
        assertThat(scalarText(statement, "SELECT lifecycle_status FROM organisation.payroll_calendar_operational_v WHERE tenant_id='" + TENANT + "' AND id='" + successor + "'"))
            .isEqualTo("DRAFT");
        assertThat(scalarLong(statement, "SELECT calendar_version FROM organisation.payroll_calendar WHERE tenant_id='" + TENANT + "' AND id='" + successor + "'"))
            .isEqualTo(2);

        statement.execute("SELECT * FROM organisation.generate_pay_periods('" + TENANT + "','" + successor + "',DATE '2027-01-01',2,'test',clock_timestamp())");
        scalarUuid(statement, "SELECT organisation.publish_payroll_calendar('" + TENANT + "'::uuid,'" + successor + "'::uuid,'successor'::varchar,'test'::varchar,clock_timestamp())");
        assertThat(scalarText(statement, "SELECT lifecycle_status FROM organisation.payroll_calendar_operational_v WHERE tenant_id='" + TENANT + "' AND id='" + source + "'"))
            .isEqualTo("RETIRED");
        assertThat(scalarText(statement, "SELECT lifecycle_status FROM organisation.payroll_calendar_operational_v WHERE tenant_id='" + TENANT + "' AND id='" + successor + "'"))
            .isEqualTo("PUBLISHED");
        connection.commit();
      }
    }
  }

  @Test
  void cycleCreationFailsClosedUntilCalendarIsPublished() throws Exception {
    UUID calendar = createReadyCalendar("CYCLE_G03", "MONTHLY", null, false);
    UUID period = firstPeriod(calendar);
    seedPayGroup(calendar);

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        Savepoint blocked = connection.setSavepoint();
        assertSqlState("23514", () -> statement.execute(
            "SELECT payroll_ops.create_regular_payroll_cycle('" + TENANT + "','" + PAY_GROUP_VERSION + "','" + period + "','test',clock_timestamp())"));
        connection.rollback(blocked);

        scalarUuid(statement, "SELECT organisation.publish_payroll_calendar('" + TENANT + "'::uuid,'" + calendar + "'::uuid,'cycle ready'::varchar,'test'::varchar,clock_timestamp())");
        UUID cycle = scalarUuid(statement,
            "SELECT payroll_ops.create_regular_payroll_cycle('" + TENANT + "','" + PAY_GROUP_VERSION + "','" + period + "','test',clock_timestamp())");
        assertThat(cycle).isNotNull();
        connection.commit();
      }
    }
  }

  @Test
  void lifecycleRowsAreTenantIsolated() throws Exception {
    UUID calendar = createReadyCalendar("RLS_G03", "MONTHLY", null, false);
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        scalarUuid(statement, "SELECT organisation.publish_payroll_calendar('" + TENANT + "'::uuid,'" + calendar + "'::uuid,'rls'::varchar,'test'::varchar,clock_timestamp())");
        assertThat(scalarLong(statement, "SELECT count(*) FROM organisation.payroll_calendar_lifecycle_event"))
            .isEqualTo(1);
        statement.execute("SET LOCAL app.tenant_id='00000000-0000-0000-0000-0000000000ff'");
        assertThat(scalarLong(statement, "SELECT count(*) FROM organisation.payroll_calendar_lifecycle_event"))
            .isZero();
      }
    }
  }

  private UUID createReadyCalendar(String code, String frequency, Integer customDays, boolean custom) throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        String customDaysSql = customDays == null ? "NULL" : customDays.toString();
        UUID calendar = scalarUuid(statement,
            "SELECT organisation.create_governed_payroll_calendar('" + TENANT + "','" + code + "','" + code + "','" + frequency + "','Asia/Kolkata'," + customDaysSql + "," + custom + ",ARRAY[6,7]::smallint[],'test',clock_timestamp())");
        configureRules(statement, calendar);
        statement.execute("SELECT * FROM organisation.generate_pay_periods('" + TENANT + "','" + calendar + "',DATE '2026-01-01',2,'test',clock_timestamp())");
        connection.commit();
        return calendar;
      }
    }
  }

  private void configureRules(Statement statement, UUID calendar) throws SQLException {
    String[][] rules = {
      {"INPUT_CUTOFF", "PERIOD_START", "0", "PREVIOUS_WORKING_DAY"},
      {"CALCULATION", "PERIOD_END", "-3", "PREVIOUS_WORKING_DAY"},
      {"APPROVAL", "PERIOD_END", "-2", "PREVIOUS_WORKING_DAY"},
      {"RELEASE", "PERIOD_END", "-1", "PREVIOUS_WORKING_DAY"},
      {"PAYMENT", "PERIOD_END", "0", "PREVIOUS_WORKING_DAY"}
    };
    for (String[] rule : rules) {
      scalarUuid(statement,
          "SELECT organisation.configure_payroll_calendar_milestone_rule('" + TENANT + "','" + calendar + "','" + rule[0] + "','" + rule[1] + "'," + rule[2] + ",'" + rule[3] + "','test',clock_timestamp())");
    }
  }

  private UUID firstPeriod(UUID calendar) throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement);
        return scalarUuid(statement, "SELECT id FROM organisation.pay_period WHERE tenant_id='" + TENANT + "' AND calendar_id='" + calendar + "' ORDER BY period_start LIMIT 1");
      }
    }
  }

  private void seedPayGroup(UUID calendar) throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO organisation.legal_entity(id,tenant_id,code,created_by,updated_by) VALUES ('" + LEGAL + "','" + TENANT + "','G03_LE','test','test')");
      statement.execute("INSERT INTO organisation.legal_entity_version(id,tenant_id,legal_entity_id,version_sequence,name,country_code,currency,effective_from,effective_to,approval_status,approved_at,approved_by,created_by,updated_by) VALUES ('" + LEGAL_VERSION + "','" + TENANT + "','" + LEGAL + "',1,'G03 Legal','IN','INR',DATE '2025-01-01',DATE '2030-01-01','APPROVED',clock_timestamp(),'test','test','test')");
      statement.execute("INSERT INTO organisation.payroll_statutory_unit(id,tenant_id,code,created_by,updated_by) VALUES ('" + PSU + "','" + TENANT + "','G03_PSU','test','test')");
      statement.execute("INSERT INTO organisation.payroll_statutory_unit_version(id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,version_sequence,name,effective_from,effective_to,approval_status,approved_at,approved_by,created_by,updated_by) VALUES ('" + PSU_VERSION + "','" + TENANT + "','" + PSU + "','" + LEGAL_VERSION + "',1,'G03 PSU',DATE '2025-01-01',DATE '2030-01-01','APPROVED',clock_timestamp(),'test','test','test')");
      statement.execute("INSERT INTO organisation.pay_group(id,tenant_id,code,created_by,updated_by) VALUES ('" + PAY_GROUP + "','" + TENANT + "','G03_GROUP','test','test')");
      statement.execute("INSERT INTO organisation.pay_group_version(id,tenant_id,pay_group_id,payroll_statutory_unit_version_id,calendar_id,version_sequence,name,currency,proration_method,effective_from,effective_to,approval_status,approved_at,approved_by,created_by,updated_by) VALUES ('" + PAY_GROUP_VERSION + "','" + TENANT + "','" + PAY_GROUP + "','" + PSU_VERSION + "','" + calendar + "',1,'G03 Group','INR','CALENDAR_DAYS',DATE '2025-01-01',DATE '2030-01-01','APPROVED',clock_timestamp(),'test','test','test')");
    }
  }

  private static void setTenant(Statement statement) throws SQLException {
    statement.execute("SET LOCAL app.tenant_id='" + TENANT + "'");
  }

  private static UUID scalarUuid(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static long scalarLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String scalarText(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static void assertSqlState(String state, SqlWork work) {
    try {
      work.run();
      throw new AssertionError("Expected SQL state " + state);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(state);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void createRoles() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
  }

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }
}
