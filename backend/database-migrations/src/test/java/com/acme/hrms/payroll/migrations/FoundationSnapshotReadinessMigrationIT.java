package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
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
class FoundationSnapshotReadinessMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION =
      UUID.fromString("45100000-0000-0000-0000-000000000001");
  private static final UUID JULY_PERIOD =
      UUID.fromString("44100000-0000-0000-0000-000000000001");
  private static final UUID AUGUST_PERIOD =
      UUID.fromString("44100000-0000-0000-0000-000000000002");

  private static UUID historicalCycleId;
  private static UUID historicalInputId;
  private static UUID historicalResultId;
  private static String historicalInputHash;
  private static String historicalInputPayload;
  private static String historicalResultHash;
  private static String historicalResultPayload;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV035ToV036() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("35"))
        .load()
        .migrate();

    applyExecutableFixture();
    createHistoricalCalculatedCycle();
    captureHistoricalEvidence();

    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
            .locations("classpath:db/migration")
            .load();
    flyway.migrate();
    flyway.validate();
  }

  @Test
  void populatedUpgradePreservesHistoricalPayloadsAndBindsExactConfiguration()
      throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      assertThat(
              scalarString(statement,
                  "SELECT snapshot_hash FROM payroll_ops.input_snapshot WHERE id='"
                      + historicalInputId + "'"))
          .isEqualTo(historicalInputHash);
      assertThat(
              scalarString(statement,
                  "SELECT snapshot_payload::text FROM payroll_ops.input_snapshot WHERE id='"
                      + historicalInputId + "'"))
          .isEqualTo(historicalInputPayload);
      assertThat(
              scalarString(statement,
                  "SELECT result_hash FROM payroll_calc.payroll_result WHERE id='"
                      + historicalResultId + "'"))
          .isEqualTo(historicalResultHash);
      assertThat(
              scalarString(statement,
                  "SELECT result_payload::text FROM payroll_calc.payroll_result WHERE id='"
                      + historicalResultId + "'"))
          .isEqualTo(historicalResultPayload);

      try (ResultSet result = statement.executeQuery(
          """
          SELECT cycle.foundation_config_snapshot_id,
                 cycle.foundation_config_snapshot_hash,
                 input.foundation_config_snapshot_id,
                 input.foundation_config_snapshot_hash,
                 request.foundation_config_snapshot_id,
                 request.foundation_config_snapshot_hash,
                 payroll_result.foundation_config_snapshot_id,
                 payroll_result.foundation_config_snapshot_hash
          FROM payroll_ops.payroll_cycle cycle
          JOIN payroll_ops.input_snapshot input
            ON input.tenant_id=cycle.tenant_id
           AND input.payroll_cycle_id=cycle.id
          JOIN payroll_calc.calculation_request request
            ON request.tenant_id=cycle.tenant_id
           AND request.payroll_cycle_id=cycle.id
           AND request.status='COMPLETED'
          JOIN payroll_calc.payroll_result payroll_result
            ON payroll_result.tenant_id=request.tenant_id
           AND payroll_result.calculation_request_id=request.id
          WHERE cycle.id='%s'
          """.formatted(historicalCycleId))) {
        assertThat(result.next()).isTrue();
        UUID snapshotId = result.getObject(1, UUID.class);
        String snapshotHash = result.getString(2);
        assertThat(snapshotId).isNotNull();
        assertThat(snapshotHash).matches("[0-9a-f]{64}");
        assertThat(result.getObject(3, UUID.class)).isEqualTo(snapshotId);
        assertThat(result.getString(4)).isEqualTo(snapshotHash);
        assertThat(result.getObject(5, UUID.class)).isEqualTo(snapshotId);
        assertThat(result.getString(6)).isEqualTo(snapshotHash);
        assertThat(result.getObject(7, UUID.class)).isEqualTo(snapshotId);
        assertThat(result.getString(8)).isEqualTo(snapshotHash);
      }
    }
  }

  @Test
  void freshInputSealCreatesOneImmutableConfigurationAndCalculationKeepsLineage()
      throws Exception {
    UUID cycleId;
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT);
        cycleId = scalarUuid(statement,
            "SELECT payroll_ops.create_regular_payroll_cycle('"
                + TENANT + "','" + PAY_GROUP_VERSION + "','" + AUGUST_PERIOD
                + "','fsr-test','2026-08-01T00:00:00Z')");
        assertThat(functionResult(statement,
            "SELECT cycle_version_no FROM payroll_ops.resolve_payroll_population('"
                + TENANT + "','" + cycleId
                + "',0,'fsr-test','2026-08-01T00:01:00Z')"))
            .isEqualTo(1);
        assertThat(functionResult(statement,
            "SELECT cycle_version_no FROM payroll_ops.seal_payroll_inputs('"
                + TENANT + "','" + cycleId
                + "',1,'fsr-test','2026-08-01T00:02:00Z')"))
            .isEqualTo(2);
      }
      connection.commit();
    }

    UUID configId;
    String configHash;
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          """
          SELECT cycle.foundation_config_snapshot_id,
                 cycle.foundation_config_snapshot_hash,
                 cycle.foundation_config_count,
                 snapshot.snapshot_hash = encode(
                   public.digest(snapshot.snapshot_payload::text,'sha256'),'hex'
                 ) hash_valid,
                 input.foundation_config_snapshot_id,
                 input.foundation_config_snapshot_hash,
                 input.snapshot_payload ->> 'foundationConfigurationSnapshotId' payload_id,
                 input.snapshot_payload ->> 'foundationConfigurationSnapshotHash' payload_hash
          FROM payroll_ops.payroll_cycle cycle
          JOIN payroll_ops.foundation_configuration_snapshot snapshot
            ON snapshot.tenant_id=cycle.tenant_id
           AND snapshot.id=cycle.foundation_config_snapshot_id
          JOIN payroll_ops.input_snapshot input
            ON input.tenant_id=cycle.tenant_id
           AND input.payroll_cycle_id=cycle.id
          WHERE cycle.id='%s'
          """.formatted(cycleId))) {
        assertThat(result.next()).isTrue();
        configId = result.getObject(1, UUID.class);
        configHash = result.getString(2);
        assertThat(configId).isNotNull();
        assertThat(configHash).matches("[0-9a-f]{64}");
        assertThat(result.getInt(3)).isGreaterThanOrEqualTo(6);
        assertThat(result.getBoolean(4)).isTrue();
        assertThat(result.getObject(5, UUID.class)).isEqualTo(configId);
        assertThat(result.getString(6)).isEqualTo(configHash);
        assertThat(result.getString(7)).isEqualTo(configId.toString());
        assertThat(result.getString(8)).isEqualTo(configHash);
      }
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT);
        assertThat(functionResult(statement,
            "SELECT cycle_version_no FROM payroll_calc.calculate_sealed_payroll('"
                + TENANT + "','" + cycleId
                + "',2,'fsr-calc',repeat('c',64),'fsr-test','2026-08-01T00:03:00Z')"))
            .isEqualTo(3);
      }
      connection.commit();
    }

    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            SELECT request.foundation_config_snapshot_id,
                   request.foundation_config_snapshot_hash,
                   payroll_result.foundation_config_snapshot_id,
                   payroll_result.foundation_config_snapshot_hash,
                   payroll_result.result_payload ->> 'foundationConfigurationSnapshotId',
                   payroll_result.result_payload ->> 'foundationConfigurationSnapshotHash'
            FROM payroll_calc.calculation_request request
            JOIN payroll_calc.payroll_result payroll_result
              ON payroll_result.tenant_id=request.tenant_id
             AND payroll_result.calculation_request_id=request.id
            WHERE request.payroll_cycle_id='%s'
              AND request.status='COMPLETED'
            """.formatted(cycleId))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject(1, UUID.class)).isEqualTo(configId);
      assertThat(result.getString(2)).isEqualTo(configHash);
      assertThat(result.getObject(3, UUID.class)).isEqualTo(configId);
      assertThat(result.getString(4)).isEqualTo(configHash);
      assertThat(result.getString(5)).isEqualTo(configId.toString());
      assertThat(result.getString(6)).isEqualTo(configHash);
    }
  }

  @Test
  void foundationSnapshotIsForcedRlsTenantIsolatedAndImmutable() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            """
            SELECT relation.relrowsecurity, relation.relforcerowsecurity
            FROM pg_class relation
            JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
            WHERE namespace.nspname='payroll_ops'
              AND relation.relname='foundation_configuration_snapshot'
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
    }

    try (Connection noTenant = app(); Statement statement = noTenant.createStatement()) {
      assertThat(count(statement, "payroll_ops.foundation_configuration_snapshot")).isZero();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT);
        assertThat(count(statement, "payroll_ops.foundation_configuration_snapshot"))
            .isGreaterThanOrEqualTo(1);
        Savepoint savepoint = connection.setSavepoint();
        assertSqlState("42501", () -> statement.execute(
            "UPDATE payroll_ops.foundation_configuration_snapshot SET sealed_by='forbidden'"));
        connection.rollback(savepoint);
      }
      connection.rollback();
    }

    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      assertSqlState("P0001", () -> statement.execute(
          "UPDATE payroll_ops.foundation_configuration_snapshot SET sealed_by='forbidden'"));
    }
  }

  private static void createHistoricalCalculatedCycle() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT);
        historicalCycleId = scalarUuid(statement,
            "SELECT payroll_ops.create_regular_payroll_cycle('"
                + TENANT + "','" + PAY_GROUP_VERSION + "','" + JULY_PERIOD
                + "','pre-v036','2026-07-01T00:00:00Z')");
        functionResult(statement,
            "SELECT cycle_version_no FROM payroll_ops.resolve_payroll_population('"
                + TENANT + "','" + historicalCycleId
                + "',0,'pre-v036','2026-07-01T00:01:00Z')");
        functionResult(statement,
            "SELECT cycle_version_no FROM payroll_ops.seal_payroll_inputs('"
                + TENANT + "','" + historicalCycleId
                + "',1,'pre-v036','2026-07-01T00:02:00Z')");
        functionResult(statement,
            "SELECT cycle_version_no FROM payroll_calc.calculate_sealed_payroll('"
                + TENANT + "','" + historicalCycleId
                + "',2,'pre-v036-calc',repeat('b',64),'pre-v036','2026-07-01T00:03:00Z')");
      }
      connection.commit();
    }
  }

  private static void captureHistoricalEvidence() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          "SELECT id,snapshot_hash,snapshot_payload::text FROM payroll_ops.input_snapshot "
              + "WHERE payroll_cycle_id='" + historicalCycleId + "' ORDER BY id LIMIT 1")) {
        assertThat(result.next()).isTrue();
        historicalInputId = result.getObject(1, UUID.class);
        historicalInputHash = result.getString(2);
        historicalInputPayload = result.getString(3);
      }
      try (ResultSet result = statement.executeQuery(
          "SELECT id,result_hash,result_payload::text FROM payroll_calc.payroll_result "
              + "WHERE payroll_cycle_id='" + historicalCycleId + "' ORDER BY id LIMIT 1")) {
        assertThat(result.next()).isTrue();
        historicalResultId = result.getObject(1, UUID.class);
        historicalResultHash = result.getString(2);
        historicalResultPayload = result.getString(3);
      }
    }
  }

  private static void applyExecutableFixture() throws Exception {
    Path fixture = locateRepositoryRoot().resolve(
        "database/flyway/e2e/fixtures/S03_001__sprint_3_executable_payroll.sql");
    String sql = Files.readString(fixture, StandardCharsets.UTF_8)
        .replaceFirst("(?m)^\\\\set ON_ERROR_STOP on\\R", "");
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Path locateRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      Path fixture = current.resolve(
          "database/flyway/e2e/fixtures/S03_001__sprint_3_executable_payroll.sql");
      if (Files.isRegularFile(fixture)) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "Could not locate repository root containing the Sprint 3 executable payroll fixture");
  }

  private static long functionResult(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static UUID scalarUuid(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static String scalarString(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static long count(Statement statement, String relation)
      throws SQLException {
    try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + relation)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void setTenant(Statement statement, UUID tenant)
      throws SQLException {
    statement.execute("SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static void assertSqlState(String expectedState, SqlWork work) {
    try {
      work.run();
      throw new AssertionError("Expected SQL state " + expectedState);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expectedState);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void createRoles() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
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
  }

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }
}
