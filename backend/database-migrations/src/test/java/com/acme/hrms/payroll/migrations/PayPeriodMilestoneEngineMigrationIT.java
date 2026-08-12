package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PayPeriodMilestoneEngineMigrationIT {
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
  static void migratePopulatedV037ToExpandedV038() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("37"))
        .load()
        .migrate();

    UUID pre38Tenant =
        UUID.fromString("00000000-0000-0000-0000-0000000000f8");
    UUID pre38Calendar =
        UUID.fromString("84000000-0000-0000-0000-0000000000f8");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id,code,name,created_by,updated_by
          ) VALUES (
            '%s','PRE38','Pre V038 tenant','test','test'
          )
          """
              .formatted(pre38Tenant));

      statement.execute(
          """
          INSERT INTO organisation.payroll_calendar(
            id,tenant_id,code,name,frequency,timezone,created_by,updated_by
          ) VALUES (
            '%s','%s','PRE38_MONTHLY','Pre V038 Monthly',
            'MONTHLY','Asia/Kolkata','test','test'
          )
          """
              .formatted(pre38Calendar, pre38Tenant));

      statement.execute(
          """
          INSERT INTO organisation.pay_period(
            id,tenant_id,calendar_id,period_code,
            period_start,period_end,payment_date,status,
            created_by,updated_by
          ) VALUES (
            gen_random_uuid(),'%s','%s','2026-01',
            DATE '2026-01-01',DATE '2026-01-31',DATE '2026-01-31','OPEN',
            'test','test'
          )
          """
              .formatted(pre38Tenant, pre38Calendar));
    }

    Flyway flyway =
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                "payroll_migrator",
                MIGRATOR_PASSWORD)
            .locations("classpath:db/migration")
            .load();

    flyway.migrate();
    flyway.validate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertThat(
              scalarLong(
                  statement,
                  """
                  SELECT count(*)
                  FROM organisation.pay_period
                  WHERE tenant_id = '%s'
                    AND calendar_id = '%s'
                    AND period_code = '2026-01'
                  """
                      .formatted(pre38Tenant, pre38Calendar)))
          .isEqualTo(1);
    }
  }

  @BeforeEach
  void reset() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      seedTenant(statement, TENANT_A, "A");
      seedTenant(statement, TENANT_B, "B");
    }
  }

  @Test
  void createsG02RelationsWithForcedRlsAndFrequencyPolicy() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertThat(
              scalarLong(
                  statement,
                  """
                  SELECT count(*)
                  FROM pg_class relation
                  JOIN pg_namespace namespace
                    ON namespace.oid = relation.relnamespace
                  WHERE namespace.nspname = 'organisation'
                    AND relation.relname IN (
                      'payroll_calendar_milestone_rule',
                      'payroll_calendar_holiday',
                      'pay_period_milestone'
                    )
                    AND relation.relrowsecurity
                    AND relation.relforcerowsecurity
                  """))
          .isEqualTo(3);

      assertThat(
              scalarLong(
                  statement,
                  """
                  SELECT count(*)
                  FROM pg_constraint
                  WHERE conname IN (
                    'payroll_calendar_frequency_ck',
                    'payroll_calendar_custom_frequency_ck',
                    'pay_period_calendar_no_overlap'
                  )
                  """))
          .isEqualTo(3);
    }
  }

  @Test
  void standardFrequenciesGenerateDeterministicContiguousPeriods()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        UUID monthly =
            createCalendar(
                statement, "MONTHLY_A", "MONTHLY", null, false);
        UUID fortnightly =
            createCalendar(
                statement, "FORTNIGHTLY_A", "FORTNIGHTLY", null, false);
        UUID weekly =
            createCalendar(
                statement, "WEEKLY_A", "WEEKLY", null, false);
        UUID daily =
            createCalendar(
                statement, "DAILY_A", "DAILY", null, false);

        configureDefaultRules(statement, monthly);
        configureDefaultRules(statement, fortnightly);
        configureDefaultRules(statement, weekly);
        configureDefaultRules(statement, daily);

        generate(statement, monthly, "2026-01-01", 2);
        generate(statement, fortnightly, "2026-01-01", 2);
        generate(statement, weekly, "2026-01-01", 2);
        generate(statement, daily, "2026-01-01", 2);

        assertPeriod(
            statement,
            monthly,
            1,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31));
        assertPeriod(
            statement,
            monthly,
            2,
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28));

        assertPeriod(
            statement,
            fortnightly,
            1,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 14));
        assertPeriod(
            statement,
            fortnightly,
            2,
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 28));

        assertPeriod(
            statement,
            weekly,
            1,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 7));
        assertPeriod(
            statement,
            weekly,
            2,
            LocalDate.of(2026, 1, 8),
            LocalDate.of(2026, 1, 14));

        assertPeriod(
            statement,
            daily,
            1,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1));
        assertPeriod(
            statement,
            daily,
            2,
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 2));
      }
    }
  }

  @Test
  void customFrequencyRequiresExplicitAuthorisationAndFixedDayPolicy()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        Savepoint unauthorised = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.executeQuery(
                    createCalendarSql(
                        "CUSTOM_BAD", "CUSTOM", 10, false)));
        connection.rollback(unauthorised);

        Savepoint missingPolicy = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.executeQuery(
                    createCalendarSql(
                        "CUSTOM_MISSING", "CUSTOM", null, true)));
        connection.rollback(missingPolicy);

        UUID custom =
            createCalendar(
                statement, "CUSTOM_10", "CUSTOM", 10, true);
        configureDefaultRules(statement, custom);
        generate(statement, custom, "2026-03-01", 2);

        assertPeriod(
            statement,
            custom,
            1,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 10));
        assertPeriod(
            statement,
            custom,
            2,
            LocalDate.of(2026, 3, 11),
            LocalDate.of(2026, 3, 20));

        Savepoint finalSettlement = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.executeQuery(
                    """
                    SELECT organisation.configure_payroll_calendar_milestone_rule(
                      '%s','%s','FINAL_SETTLEMENT','PERIOD_END',0,'NONE',
                      'issuer|test',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, custom)));
        connection.rollback(finalSettlement);
      }
    }
  }

  @Test
  void milestoneEvidencePreservesOriginalAndAdjustedHolidayWeekendDates()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        UUID weekly =
            createCalendar(
                statement, "WEEKLY_MILESTONE", "WEEKLY", null, false);

        configureRule(
            statement,
            weekly,
            "INPUT_CUTOFF",
            "PERIOD_END",
            -5,
            "NONE");
        configureRule(
            statement,
            weekly,
            "CALCULATION",
            "PERIOD_END",
            -3,
            "NONE");
        configureRule(
            statement,
            weekly,
            "APPROVAL",
            "PERIOD_END",
            -2,
            "NONE");
        configureRule(
            statement,
            weekly,
            "RELEASE",
            "PERIOD_END",
            -1,
            "PREVIOUS_WORKING_DAY");
        configureRule(
            statement,
            weekly,
            "PAYMENT",
            "PERIOD_END",
            1,
            "NEXT_WORKING_DAY");

        statement.executeQuery(
            """
            SELECT organisation.add_payroll_calendar_holiday(
              '%s','%s',DATE '2026-08-10','Bank holiday',
              'issuer|test',clock_timestamp()
            )
            """
                .formatted(TENANT_A, weekly));

        UUID periodId = generateFirstId(
            statement, weekly, "2026-08-03", 1);

        assertMilestone(
            statement,
            periodId,
            "INPUT_CUTOFF",
            LocalDate.of(2026, 8, 4),
            LocalDate.of(2026, 8, 4),
            0);
        assertMilestone(
            statement,
            periodId,
            "CALCULATION",
            LocalDate.of(2026, 8, 6),
            LocalDate.of(2026, 8, 6),
            0);
        assertMilestone(
            statement,
            periodId,
            "APPROVAL",
            LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 7),
            0);
        assertMilestone(
            statement,
            periodId,
            "RELEASE",
            LocalDate.of(2026, 8, 8),
            LocalDate.of(2026, 8, 7),
            -1);
        assertMilestone(
            statement,
            periodId,
            "PAYMENT",
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 11),
            1);

        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT payment_date
                FROM organisation.pay_period
                WHERE tenant_id = '%s'
                  AND id = '%s'
                """
                    .formatted(TENANT_A, periodId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getObject(1, LocalDate.class))
              .isEqualTo(LocalDate.of(2026, 8, 11));
        }
      }
    }
  }

  @Test
  void generationIsIdempotentOverlapSafeAndTenantIsolated()
      throws Exception {
    UUID weekly;
    UUID firstId;

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        weekly =
            createCalendar(
                statement, "WEEKLY_SAFE", "WEEKLY", null, false);
        configureDefaultRules(statement, weekly);

        firstId = generateFirstId(
            statement, weekly, "2026-09-01", 2);
        UUID repeatedId = generateFirstId(
            statement, weekly, "2026-09-01", 2);

        assertThat(repeatedId).isEqualTo(firstId);
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT count(*)
                    FROM organisation.pay_period
                    WHERE tenant_id = '%s'
                      AND calendar_id = '%s'
                    """
                        .formatted(TENANT_A, weekly)))
            .isEqualTo(2);
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT count(*)
                    FROM organisation.pay_period_milestone milestone
                    JOIN organisation.pay_period period
                      ON period.tenant_id = milestone.tenant_id
                     AND period.id = milestone.pay_period_id
                    WHERE milestone.tenant_id = '%s'
                      AND period.calendar_id = '%s'
                    """
                        .formatted(TENANT_A, weekly)))
            .isEqualTo(10);

        Savepoint overlap = connection.setSavepoint();
        assertSqlState(
            "23P01",
            () ->
                statement.executeQuery(
                    generateSql(weekly, "2026-09-02", 1)));
        connection.rollback(overlap);

        statement.executeQuery(
            """
            SELECT organisation.add_payroll_calendar_holiday(
              '%s','%s',DATE '2026-09-14','Tenant A holiday',
              'issuer|test',clock_timestamp()
            )
            """
                .formatted(TENANT_A, weekly));

        connection.commit();
      }
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_B);

        assertThat(
                scalarLong(
                    statement,
                    "SELECT count(*) "
                        + "FROM organisation.payroll_calendar_holiday"))
            .isZero();
        assertThat(
                scalarLong(
                    statement,
                    "SELECT count(*) "
                        + "FROM organisation.payroll_calendar_milestone_rule"))
            .isZero();
        assertThat(
                scalarLong(
                    statement,
                    "SELECT count(*) "
                        + "FROM organisation.pay_period_milestone"))
            .isZero();
      }
    }
  }

  private static UUID createCalendar(
      Statement statement,
      String code,
      String frequency,
      Integer customPeriodDays,
      boolean customAuthorised)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            createCalendarSql(
                code, frequency, customPeriodDays, customAuthorised))) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static String createCalendarSql(
      String code,
      String frequency,
      Integer customPeriodDays,
      boolean customAuthorised) {
    String customDays =
        customPeriodDays == null ? "NULL" : customPeriodDays.toString();

    return """
        SELECT organisation.create_payroll_calendar(
          '%s','%s','%s calendar','%s','Asia/Kolkata',
          %s,%s,ARRAY[6,7]::smallint[],
          'issuer|test',clock_timestamp()
        )
        """
        .formatted(
            TENANT_A,
            code,
            code,
            frequency,
            customDays,
            customAuthorised);
  }

  private static void configureDefaultRules(
      Statement statement, UUID calendarId) throws SQLException {
    configureRule(
        statement,
        calendarId,
        "INPUT_CUTOFF",
        "PERIOD_END",
        -4,
        "NONE");
    configureRule(
        statement,
        calendarId,
        "CALCULATION",
        "PERIOD_END",
        -3,
        "NONE");
    configureRule(
        statement,
        calendarId,
        "APPROVAL",
        "PERIOD_END",
        -2,
        "NONE");
    configureRule(
        statement,
        calendarId,
        "RELEASE",
        "PERIOD_END",
        -1,
        "NONE");
    configureRule(
        statement,
        calendarId,
        "PAYMENT",
        "PERIOD_END",
        1,
        "NONE");
  }

  private static void configureRule(
      Statement statement,
      UUID calendarId,
      String milestoneType,
      String anchorType,
      int offsetDays,
      String adjustmentPolicy)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            """
            SELECT organisation.configure_payroll_calendar_milestone_rule(
              '%s','%s','%s','%s',%s,'%s',
              'issuer|test',clock_timestamp()
            )
            """
                .formatted(
                    TENANT_A,
                    calendarId,
                    milestoneType,
                    anchorType,
                    offsetDays,
                    adjustmentPolicy))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject(1, UUID.class)).isNotNull();
    }
  }

  private static void generate(
      Statement statement,
      UUID calendarId,
      String startDate,
      int periodCount)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            generateSql(calendarId, startDate, periodCount))) {
      int rows = 0;
      while (result.next()) {
        rows++;
      }
      assertThat(rows).isEqualTo(periodCount);
    }
  }

  private static UUID generateFirstId(
      Statement statement,
      UUID calendarId,
      String startDate,
      int periodCount)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            generateSql(calendarId, startDate, periodCount))) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static String generateSql(
      UUID calendarId, String startDate, int periodCount) {
    return """
        SELECT *
        FROM organisation.generate_pay_periods(
          '%s','%s',DATE '%s',%s,'issuer|test',clock_timestamp()
        )
        """
        .formatted(TENANT_A, calendarId, startDate, periodCount);
  }

  private static void assertPeriod(
      Statement statement,
      UUID calendarId,
      int ordinal,
      LocalDate expectedStart,
      LocalDate expectedEnd)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            """
            SELECT period_start, period_end
            FROM organisation.pay_period
            WHERE tenant_id = '%s'
              AND calendar_id = '%s'
            ORDER BY period_start
            OFFSET %s LIMIT 1
            """
                .formatted(TENANT_A, calendarId, ordinal - 1))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject(1, LocalDate.class))
          .isEqualTo(expectedStart);
      assertThat(result.getObject(2, LocalDate.class))
          .isEqualTo(expectedEnd);
    }
  }

  private static void assertMilestone(
      Statement statement,
      UUID periodId,
      String milestoneType,
      LocalDate expectedOriginal,
      LocalDate expectedAdjusted,
      int expectedMovement)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            """
            SELECT original_date, adjusted_date, adjustment_days
            FROM organisation.pay_period_milestone
            WHERE tenant_id = '%s'
              AND pay_period_id = '%s'
              AND milestone_type = '%s'
            """
                .formatted(TENANT_A, periodId, milestoneType))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject(1, LocalDate.class))
          .isEqualTo(expectedOriginal);
      assertThat(result.getObject(2, LocalDate.class))
          .isEqualTo(expectedAdjusted);
      assertThat(result.getInt(3)).isEqualTo(expectedMovement);
    }
  }

  private static void seedTenant(
      Statement statement, UUID tenantId, String code) throws SQLException {
    statement.execute(
        """
        INSERT INTO platform.tenant(
          id,code,name,created_by,updated_by
        ) VALUES ('%s','%s','Synthetic Tenant %s','test','test')
        """
            .formatted(tenantId, code, code));
  }

  private static void setTenant(Statement statement, UUID tenant)
      throws SQLException {
    statement.execute("SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static long scalarLong(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
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
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void createRoles() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
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
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
  }

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }
}
