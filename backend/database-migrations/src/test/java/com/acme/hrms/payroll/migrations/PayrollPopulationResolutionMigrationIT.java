package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
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
class PayrollPopulationResolutionMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

  private static final UUID TENANT_A = id("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = id("00000000-0000-0000-0000-00000000000b");
  private static final UUID LEGAL_ID = id("41000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID = id("41100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID = id("42000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID = id("42100000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_ID = id("43000000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_VERSION_ID = id("43100000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = id("44000000-0000-0000-0000-000000000001");
  private static final UUID JAN_PERIOD_ID = id("44100000-0000-0000-0000-000000000001");
  private static final UUID FEB_PERIOD_ID = id("44100000-0000-0000-0000-000000000002");
  private static final UUID CLOSED_PERIOD_ID = id("44100000-0000-0000-0000-000000000003");
  private static final UUID PAY_GROUP_ID = id("45000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION_ID = id("45100000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_ID = id("46000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID = id("46100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_ID = id("47000000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_VERSION_ID = id("47100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_LINE_ID = id("47200000-0000-0000-0000-000000000001");

  private static final UUID READY_RELATIONSHIP_ID = id("48000000-0000-0000-0000-000000000001");
  private static final UUID READY_RELATIONSHIP_VERSION_ID = id("48100000-0000-0000-0000-000000000001");
  private static final UUID READY_PROFILE_ID = id("48200000-0000-0000-0000-000000000001");
  private static final UUID READY_ASSIGNMENT_ID = id("48300000-0000-0000-0000-000000000001");
  private static final UUID READY_ASSIGNMENT_VERSION_ID = id("48400000-0000-0000-0000-000000000001");
  private static final UUID READY_GROUP_ASSIGNMENT_ID = id("48500000-0000-0000-0000-000000000001");
  private static final UUID READY_SALARY_ASSIGNMENT_ID = id("48600000-0000-0000-0000-000000000001");

  private static final UUID HOLD_RELATIONSHIP_ID = id("49000000-0000-0000-0000-000000000001");
  private static final UUID HOLD_RELATIONSHIP_VERSION_ID = id("49100000-0000-0000-0000-000000000001");
  private static final UUID HOLD_PROFILE_ID = id("49200000-0000-0000-0000-000000000001");
  private static final UUID HOLD_ASSIGNMENT_ID = id("49300000-0000-0000-0000-000000000001");
  private static final UUID HOLD_ASSIGNMENT_VERSION_ID = id("49400000-0000-0000-0000-000000000001");
  private static final UUID HOLD_GROUP_ASSIGNMENT_ID = id("49500000-0000-0000-0000-000000000001");
  private static final UUID HOLD_SALARY_ASSIGNMENT_ID = id("49600000-0000-0000-0000-000000000001");

  private static final UUID LEGACY_CYCLE_ID = id("4a000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_MEMBER_ID = id("4a100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_SNAPSHOT_ID = id("4a200000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV022WithLegacyPopulation() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("22"))
        .load()
        .migrate();

    seedV022FoundationAndLegacyPopulation();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("23"))
        .load()
        .migrate();

    seedV023LegacyInputSnapshot();

    Flyway flyway = Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load();
    flyway.migrate();
    flyway.validate();
  }

  @Test
  void legacyPopulationAndSnapshotAreBackfilledWithExactLineage() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        try (ResultSet result = statement.executeQuery("""
            SELECT c.status,c.active_population_resolution_id,
                   c.input_snapshot_count,c.input_snapshot_set_hash,
                   r.attempt_no,r.included_count,r.excluded_count,
                   m.payroll_relationship_version_id,
                   m.employee_payroll_profile_id,
                   m.pay_group_assignment_id,m.salary_assignment_id,
                   d.decision,d.reason_code,
                   s.id AS snapshot_id,s.payload_schema_version,
                   s.population_resolution_id,s.population_member_id,
                   s.population_decision_id,s.salary_structure_version_id,
                   s.snapshot_hash,
                   encode(digest(s.snapshot_payload::text,'sha256'),'hex')
                     AS calculated_snapshot_hash
            FROM payroll_ops.payroll_cycle c
            JOIN payroll_ops.population_resolution r
              ON r.tenant_id=c.tenant_id
             AND r.id=c.active_population_resolution_id
            JOIN payroll_ops.population_member m
              ON m.tenant_id=c.tenant_id
             AND m.payroll_cycle_id=c.id
            JOIN payroll_ops.population_decision d
              ON d.tenant_id=m.tenant_id
             AND d.population_resolution_id=m.population_resolution_id
             AND d.payroll_assignment_version_id=
                 m.payroll_assignment_version_id
            JOIN payroll_ops.input_snapshot s
              ON s.tenant_id=m.tenant_id
             AND s.population_member_id=m.id
             AND s.population_decision_id=d.id
            WHERE c.id='%s'
            """.formatted(LEGACY_CYCLE_ID))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("status")).isEqualTo("INPUTS_SEALED");
          assertThat(result.getObject("active_population_resolution_id", UUID.class))
              .isNotNull();
          assertThat(result.getInt("attempt_no")).isOne();
          assertThat(result.getInt("included_count")).isOne();
          assertThat(result.getInt("excluded_count")).isZero();
          assertThat(result.getObject("payroll_relationship_version_id", UUID.class))
              .isEqualTo(READY_RELATIONSHIP_VERSION_ID);
          assertThat(result.getObject("employee_payroll_profile_id", UUID.class))
              .isEqualTo(READY_PROFILE_ID);
          assertThat(result.getObject("pay_group_assignment_id", UUID.class))
              .isEqualTo(READY_GROUP_ASSIGNMENT_ID);
          assertThat(result.getObject("salary_assignment_id", UUID.class))
              .isEqualTo(READY_SALARY_ASSIGNMENT_ID);
          assertThat(result.getString("decision")).isEqualTo("INCLUDED");
          assertThat(result.getString("reason_code")).isEqualTo("INCLUDED");
          assertThat(result.getInt("input_snapshot_count")).isOne();
          assertThat(result.getString("input_snapshot_set_hash"))
              .matches("[0-9a-f]{64}");
          assertThat(result.getObject("snapshot_id", UUID.class))
              .isEqualTo(LEGACY_SNAPSHOT_ID);
          assertThat(result.getInt("payload_schema_version")).isZero();
          assertThat(result.getObject("population_resolution_id", UUID.class))
              .isEqualTo(result.getObject("active_population_resolution_id", UUID.class));
          assertThat(result.getObject("population_member_id", UUID.class))
              .isEqualTo(LEGACY_MEMBER_ID);
          assertThat(result.getObject("population_decision_id", UUID.class))
              .isNotNull();
          assertThat(result.getObject("salary_structure_version_id", UUID.class))
              .isEqualTo(STRUCTURE_VERSION_ID);
          assertThat(result.getString("snapshot_hash"))
              .isEqualTo(result.getString("calculated_snapshot_hash"));
          assertThat(result.next()).isFalse();
        }
      }
      connection.rollback();
    }
  }

  @Test
  void resolutionIncludesReadyEmployeeExcludesOnHoldAndPreservesAttempts()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);

        Resolution first = resolve(statement, TENANT_A, cycleId, 0);
        assertThat(first.attemptNo()).isOne();
        assertThat(first.includedCount()).isOne();
        assertThat(first.excludedCount()).isOne();
        assertThat(first.cycleVersionNo()).isOne();

        assertThat(reasonCodes(statement, first.resolutionId()))
            .containsExactlyInAnyOrder("INCLUDED", "PROFILE_NOT_READY");
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_member "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isOne();

        Resolution second = resolve(statement, TENANT_A, cycleId, 1);
        assertThat(second.attemptNo()).isEqualTo(2);
        assertThat(second.includedCount()).isOne();
        assertThat(second.excludedCount()).isOne();
        assertThat(second.cycleVersionNo()).isEqualTo(2);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_resolution "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(2);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_decision "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(4);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_member "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isOne();
      }
      connection.rollback();
    }
  }

  @Test
  void staleVersionAndTenantMismatchAreRejected() throws Exception {
    assertSqlState("40001", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          resolve(statement, TENANT_A, cycleId, 99);
        }
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          resolve(statement, TENANT_B, LEGACY_CYCLE_ID, 0);
        }
      }
    });
  }

  @Test
  void runtimeRoleUsesControlledCommandsAndCannotSeeAnotherTenant()
      throws Exception {
    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          statement.execute("""
              INSERT INTO payroll_ops.payroll_cycle(
                tenant_id,pay_group_id,pay_period_id,
                cycle_type,status,created_by,updated_by
              ) VALUES (
                '%s','%s','%s','REGULAR','DRAFT','test','test'
              )
              """.formatted(TENANT_A, PAY_GROUP_VERSION_ID, FEB_PERIOD_ID));
        }
      }
    });

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_B);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.payroll_cycle"))
            .isZero();
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_resolution"))
            .isZero();
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.population_decision"))
            .isZero();
      }
      connection.rollback();
    }
  }

  @Test
  void populationEvidenceAndCycleStateRejectUncontrolledMutation()
      throws Exception {
    UUID resolutionId;
    UUID decisionId;
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery(
          "SELECT active_population_resolution_id FROM payroll_ops.payroll_cycle "
              + "WHERE id='" + LEGACY_CYCLE_ID + "'")) {
        assertThat(result.next()).isTrue();
        resolutionId = result.getObject(1, UUID.class);
      }
      try (ResultSet result = statement.executeQuery(
          "SELECT id FROM payroll_ops.population_decision "
              + "WHERE population_resolution_id='" + resolutionId + "'")) {
        assertThat(result.next()).isTrue();
        decisionId = result.getObject(1, UUID.class);
      }
    }

    assertSqlState("42501", () -> {
      try (Connection connection = admin();
          Statement statement = connection.createStatement()) {
        statement.execute("UPDATE payroll_ops.population_resolution "
            + "SET included_count=99 WHERE id='" + resolutionId + "'");
      }
    });

    assertSqlState("P0001", () -> {
      try (Connection connection = admin();
          Statement statement = connection.createStatement()) {
        statement.execute("DELETE FROM payroll_ops.population_decision "
            + "WHERE id='" + decisionId + "'");
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = admin();
          Statement statement = connection.createStatement()) {
        statement.execute("UPDATE payroll_ops.payroll_cycle "
            + "SET status='FAILED' WHERE id='" + LEGACY_CYCLE_ID + "'");
      }
    });
  }

  @Test
  void sealingCreatesCanonicalSnapshotAndAdvancesCycle() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        Resolution resolution = resolve(statement, TENANT_A, cycleId, 0);

        SealSummary sealed = seal(statement, TENANT_A, cycleId, 1);
        assertThat(sealed.snapshotCount()).isOne();
        assertThat(sealed.combinedHash()).matches("[0-9a-f]{64}");
        assertThat(sealed.cycleVersionNo()).isEqualTo(2);

        try (ResultSet result = statement.executeQuery("""
            SELECT c.status,c.input_snapshot_count,c.input_snapshot_set_hash,
                   s.payload_schema_version,s.population_resolution_id,
                   s.snapshot_payload #>> '{payGroup,prorationMethod}' AS proration,
                   s.snapshot_payload #>> '{salaryAssignment,monthlyAmount}' AS monthly_amount,
                   s.snapshot_payload #>> '{salaryStructure,lines,0,component,code}' AS component_code,
                   s.snapshot_hash,
                   encode(digest(s.snapshot_payload::text,'sha256'),'hex') AS calculated_hash
            FROM payroll_ops.payroll_cycle c
            JOIN payroll_ops.input_snapshot s
              ON s.tenant_id=c.tenant_id
             AND s.payroll_cycle_id=c.id
            WHERE c.id='%s'
            """.formatted(cycleId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("status")).isEqualTo("INPUTS_SEALED");
          assertThat(result.getInt("input_snapshot_count")).isOne();
          assertThat(result.getString("input_snapshot_set_hash"))
              .isEqualTo(sealed.combinedHash());
          assertThat(result.getInt("payload_schema_version")).isOne();
          assertThat(result.getObject("population_resolution_id", UUID.class))
              .isEqualTo(resolution.resolutionId());
          assertThat(result.getString("proration")).isEqualTo("CALENDAR_DAYS");
          assertThat(result.getString("monthly_amount")).isEqualTo("75000.0000");
          assertThat(result.getString("component_code")).isEqualTo("BASIC");
          assertThat(result.getString("snapshot_hash"))
              .isEqualTo(result.getString("calculated_hash"));
          assertThat(result.next()).isFalse();
        }

        assertSqlState("23514", () -> seal(statement, TENANT_A, cycleId, 2));
      }
      connection.rollback();
    }
  }

  @Test
  void configurationDriftAndStaleSealAreRejected() throws Exception {
    assertSqlState("40001", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          resolve(statement, TENANT_A, cycleId, 0);
          seal(statement, TENANT_A, cycleId, 99);
        }
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          seal(statement, TENANT_B, LEGACY_CYCLE_ID, 2);
        }
      }
    });

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        statement.execute("SELECT employee_payroll.update_employee_payroll_profile_status("
            + "'" + TENANT_A + "','" + READY_PROFILE_ID
            + "','ON_HOLD',1,'drift-test',clock_timestamp())");

        Savepoint beforeSeal = connection.setSavepoint();
        assertSqlState("23514", () -> seal(statement, TENANT_A, cycleId, 1));
        connection.rollback(beforeSeal);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.input_snapshot "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isZero();
        try (ResultSet result = statement.executeQuery(
            "SELECT status FROM payroll_ops.payroll_cycle WHERE id='" + cycleId + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("POPULATION_RESOLVED");
        }
      }
      connection.rollback();
    }
  }

  @Test
  void inputSnapshotsRequireControlledSealingAndRemainImmutable() throws Exception {
    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          statement.execute("""
              INSERT INTO payroll_ops.input_snapshot(
                tenant_id,payroll_cycle_id,payroll_assignment_version_id,
                snapshot_hash,snapshot_payload,sealed_at,
                created_by,updated_by,payload_schema_version,
                population_resolution_id,population_member_id,
                population_decision_id,payroll_relationship_version_id,
                employee_payroll_profile_id,pay_group_assignment_id,
                salary_assignment_id,salary_structure_version_id
              ) VALUES (
                '%s','%s','%s',repeat('a',64),'{}',clock_timestamp(),
                'test','test',1,
                (SELECT active_population_resolution_id FROM payroll_ops.payroll_cycle WHERE id='%s'),
                '%s',(SELECT id FROM payroll_ops.population_decision WHERE payroll_cycle_id='%s' LIMIT 1),
                '%s','%s','%s','%s','%s'
              )
              """.formatted(
                  TENANT_A, LEGACY_CYCLE_ID, READY_ASSIGNMENT_VERSION_ID,
                  LEGACY_CYCLE_ID, LEGACY_MEMBER_ID, LEGACY_CYCLE_ID,
                  READY_RELATIONSHIP_VERSION_ID, READY_PROFILE_ID,
                  READY_GROUP_ASSIGNMENT_ID, READY_SALARY_ASSIGNMENT_ID,
                  STRUCTURE_VERSION_ID));
        }
      }
    });

    assertSqlState("P0001", () -> {
      try (Connection connection = admin();
          Statement statement = connection.createStatement()) {
        statement.execute("UPDATE payroll_ops.input_snapshot "
            + "SET updated_by='forbidden' WHERE id='" + LEGACY_SNAPSHOT_ID + "'");
      }
    });

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_B);
        assertThat(queryLong(statement,
            "SELECT count(*) FROM payroll_ops.input_snapshot"))
            .isZero();
      }
      connection.rollback();
    }
  }

  @Test
  void deterministicCalculationPersistsImmutableResultAndTrace()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        seal(statement, TENANT_A, cycleId, 1);

        String requestHash = "d".repeat(64);
        CalculationSummary calculated = calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-test-one",
            requestHash);

        assertThat(calculated.resultCount()).isOne();
        assertThat(calculated.grossTotal()).isEqualTo("75000.0000");
        assertThat(calculated.deductionTotal()).isEqualTo("0.0000");
        assertThat(calculated.netTotal()).isEqualTo("75000.0000");
        assertThat(calculated.resultSetHash()).matches("[0-9a-f]{64}");
        assertThat(calculated.cycleVersionNo()).isEqualTo(3);

        try (ResultSet result = statement.executeQuery("""
            SELECT c.status,c.version_no,c.control_total,
                   c.active_calculation_request_id,
                   c.calculation_result_count,
                   c.calculation_result_set_hash,
                   r.status AS request_status,
                   r.request_schema_version,
                   p.result_schema_version,p.result_hash,
                   encode(
                     digest(p.result_payload::text,'sha256'),
                     'hex'
                   ) AS calculated_result_hash,
                   p.gross_amount,p.deduction_amount,p.net_amount,
                   p.component_count,
                   component.component_schema_version,
                   component.component_code,
                   component.formula_type,
                   component.calculated_amount,
                   trace.trace_schema_version,
                   trace.step_type,
                   trace.output_value
            FROM payroll_ops.payroll_cycle c
            JOIN payroll_calc.calculation_request r
              ON r.tenant_id=c.tenant_id
             AND r.id=c.active_calculation_request_id
            JOIN payroll_calc.payroll_result p
              ON p.tenant_id=r.tenant_id
             AND p.calculation_request_id=r.id
            JOIN payroll_calc.component_result component
              ON component.tenant_id=p.tenant_id
             AND component.payroll_result_id=p.id
            JOIN payroll_calc.calculation_trace trace
              ON trace.tenant_id=component.tenant_id
             AND trace.component_result_id=component.id
            WHERE c.id='%s'
            """.formatted(cycleId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("status")).isEqualTo("CALCULATED");
          assertThat(result.getLong("version_no")).isEqualTo(3);
          assertThat(result.getBigDecimal("control_total"))
              .isEqualByComparingTo("75000.0000");
          assertThat(result.getObject(
              "active_calculation_request_id",
              UUID.class)).isEqualTo(calculated.requestId());
          assertThat(result.getInt("calculation_result_count")).isOne();
          assertThat(result.getString("calculation_result_set_hash"))
              .isEqualTo(calculated.resultSetHash());
          assertThat(result.getString("request_status"))
              .isEqualTo("COMPLETED");
          assertThat(result.getInt("request_schema_version")).isOne();
          assertThat(result.getInt("result_schema_version")).isOne();
          assertThat(result.getString("result_hash"))
              .isEqualTo(result.getString("calculated_result_hash"));
          assertThat(result.getBigDecimal("gross_amount"))
              .isEqualByComparingTo("75000.0000");
          assertThat(result.getBigDecimal("deduction_amount"))
              .isEqualByComparingTo("0.0000");
          assertThat(result.getBigDecimal("net_amount"))
              .isEqualByComparingTo("75000.0000");
          assertThat(result.getInt("component_count")).isOne();
          assertThat(result.getInt("component_schema_version")).isOne();
          assertThat(result.getString("component_code")).isEqualTo("BASIC");
          assertThat(result.getString("formula_type")).isEqualTo("FIXED");
          assertThat(result.getBigDecimal("calculated_amount"))
              .isEqualByComparingTo("75000.0000");
          assertThat(result.getInt("trace_schema_version")).isOne();
          assertThat(result.getString("step_type"))
              .isEqualTo("FIXED_COMPONENT");
          assertThat(result.getBigDecimal("output_value"))
              .isEqualByComparingTo("75000.000000");
          assertThat(result.next()).isFalse();
        }

        CalculationSummary replay = calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-test-one",
            requestHash);
        assertThat(replay).isEqualTo(calculated);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_request "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isOne();
      }
      connection.rollback();
    }
  }

  @Test
  void calculationRejectsStaleVersionAndTenantMismatch()
      throws Exception {
    assertSqlState("40001", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          resolve(statement, TENANT_A, cycleId, 0);
          seal(statement, TENANT_A, cycleId, 1);
          calculate(
              statement,
              TENANT_A,
              cycleId,
              99,
              "calculation-stale",
              "e".repeat(64));
        }
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          calculate(
              statement,
              TENANT_B,
              LEGACY_CYCLE_ID,
              2,
              "calculation-tenant",
              "f".repeat(64));
        }
      }
    });
  }

  @Test
  void calculationRejectsUnsealedAndLegacySnapshots()
      throws Exception {
    assertSqlState("23514", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          calculate(
              statement,
              TENANT_A,
              cycleId,
              0,
              "calculation-unsealed",
              "1".repeat(64));
        }
      }
    });

    assertSqlState("23514", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          long version = queryLong(
              statement,
              "SELECT version_no FROM payroll_ops.payroll_cycle "
                  + "WHERE id='" + LEGACY_CYCLE_ID + "'");
          calculate(
              statement,
              TENANT_A,
              LEGACY_CYCLE_ID,
              version,
              "calculation-legacy",
              "2".repeat(64));
        }
      }
    });
  }

  @Test
  void calculationWritesRequireControlledCommandAndRemainTenantSafe()
      throws Exception {
    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          statement.execute("""
              INSERT INTO payroll_calc.calculation_request(
                tenant_id,payroll_cycle_id,idempotency_key,
                request_hash,created_by,updated_by
              ) VALUES (
                '%s','%s','forbidden-request',repeat('a',64),
                'test','test'
              )
              """.formatted(TENANT_A, LEGACY_CYCLE_ID));
        }
      }
    });

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        seal(statement, TENANT_A, cycleId, 1);
        calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-tenant-read",
            "3".repeat(64));

        tenant(statement, TENANT_B);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_request"))
            .isZero();
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.payroll_result"))
            .isZero();
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.component_result"))
            .isZero();
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_trace"))
            .isZero();
      }
      connection.rollback();
    }
  }

  @Test
  void recalculationCreatesLinkedAttemptAndPreservesHistory()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        seal(statement, TENANT_A, cycleId, 1);
        CalculationSummary initial = calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-recalc-initial",
            "4".repeat(64));

        RecalculationSummary recalculated = recalculate(
            statement,
            TENANT_A,
            cycleId,
            3,
            "recalculation-test-one",
            "5".repeat(64),
            "Approved payroll review rerun");

        assertThat(recalculated.supersededRequestId())
            .isEqualTo(initial.requestId());
        assertThat(recalculated.attemptNo()).isEqualTo(2);
        assertThat(recalculated.resultCount()).isOne();
        assertThat(recalculated.grossTotal()).isEqualTo("75000.0000");
        assertThat(recalculated.deductionTotal()).isEqualTo("0.0000");
        assertThat(recalculated.netTotal()).isEqualTo("75000.0000");
        assertThat(recalculated.resultSetHash())
            .isEqualTo(initial.resultSetHash());
        assertThat(recalculated.cycleVersionNo()).isEqualTo(4);

        RecalculationSummary replay = recalculate(
            statement,
            TENANT_A,
            cycleId,
            3,
            "recalculation-test-one",
            "5".repeat(64),
            "Approved payroll review rerun");
        assertThat(replay).isEqualTo(recalculated);

        try (ResultSet result = statement.executeQuery("""
            SELECT cycle.status,cycle.version_no,
                   cycle.active_calculation_request_id,
                   request.calculation_kind,request.attempt_no,
                   request.supersedes_request_id,
                   request.recalculation_reason,
                   request.engine_version
            FROM payroll_ops.payroll_cycle cycle
            JOIN payroll_calc.calculation_request request
              ON request.tenant_id=cycle.tenant_id
             AND request.id=cycle.active_calculation_request_id
            WHERE cycle.id='%s'
            """.formatted(cycleId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("status")).isEqualTo("CALCULATED");
          assertThat(result.getLong("version_no")).isEqualTo(4);
          assertThat(result.getObject(
              "active_calculation_request_id",
              UUID.class)).isEqualTo(recalculated.requestId());
          assertThat(result.getString("calculation_kind"))
              .isEqualTo("RECALCULATION");
          assertThat(result.getInt("attempt_no")).isEqualTo(2);
          assertThat(result.getObject(
              "supersedes_request_id",
              UUID.class)).isEqualTo(initial.requestId());
          assertThat(result.getString("recalculation_reason"))
              .isEqualTo("Approved payroll review rerun");
          assertThat(result.getString("engine_version"))
              .isEqualTo("STARTER_FIXED_V1");
        }

        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_request "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(2);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.payroll_result "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(2);
        assertThat(queryLong(
            statement,
            "SELECT count(DISTINCT result_hash) "
                + "FROM payroll_calc.payroll_result "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isOne();
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.component_result component "
                + "JOIN payroll_calc.payroll_result result "
                + "ON result.tenant_id=component.tenant_id "
                + "AND result.id=component.payroll_result_id "
                + "WHERE result.payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(2);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_trace trace "
                + "JOIN payroll_calc.payroll_result result "
                + "ON result.tenant_id=trace.tenant_id "
                + "AND result.id=trace.payroll_result_id "
                + "WHERE result.payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(2);
      }
      connection.rollback();
    }
  }

  @Test
  void recalculationChainsAttemptsFromTheCurrentActiveRequest()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        seal(statement, TENANT_A, cycleId, 1);
        CalculationSummary initial = calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-recalc-chain",
            "6".repeat(64));
        RecalculationSummary second = recalculate(
            statement,
            TENANT_A,
            cycleId,
            3,
            "recalculation-chain-two",
            "7".repeat(64),
            "First controlled payroll rerun");
        RecalculationSummary third = recalculate(
            statement,
            TENANT_A,
            cycleId,
            4,
            "recalculation-chain-three",
            "8".repeat(64),
            "Second controlled payroll rerun");

        assertThat(second.supersededRequestId())
            .isEqualTo(initial.requestId());
        assertThat(third.supersededRequestId())
            .isEqualTo(second.requestId());
        assertThat(third.attemptNo()).isEqualTo(3);
        assertThat(third.cycleVersionNo()).isEqualTo(5);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_request "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(3);
        assertThat(queryLong(
            statement,
            "SELECT max(attempt_no) FROM payroll_calc.calculation_request "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isEqualTo(3);
        assertThat(queryLong(
            statement,
            "SELECT count(*) FROM payroll_calc.calculation_request "
                + "WHERE payroll_cycle_id='" + cycleId + "' "
                + "AND supersedes_request_id IS NOT NULL"))
            .isEqualTo(2);
        assertThat(queryLong(
            statement,
            "SELECT count(DISTINCT result_hash) "
                + "FROM payroll_calc.payroll_result "
                + "WHERE payroll_cycle_id='" + cycleId + "'"))
            .isOne();
      }
      connection.rollback();
    }
  }

  @Test
  void recalculationRejectsInvalidStateReasonVersionTenantAndReplayConflict()
      throws Exception {
    assertSqlState("23514", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          recalculate(
              statement,
              TENANT_A,
              cycleId,
              0,
              "recalculation-not-calculated",
              "9".repeat(64),
              "Cycle is not calculated yet");
        }
      }
    });

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        tenant(statement, TENANT_A);
        UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
        resolve(statement, TENANT_A, cycleId, 0);
        seal(statement, TENANT_A, cycleId, 1);
        calculate(
            statement,
            TENANT_A,
            cycleId,
            2,
            "calculation-recalc-negative",
            "a".repeat(64));

        assertSqlState("23514", () -> recalculate(
            statement,
            TENANT_A,
            cycleId,
            3,
            "recalculation-blank-reason",
            "b".repeat(64),
            " "));

        connection.rollback();
      }
    }

    assertSqlState("40001", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          resolve(statement, TENANT_A, cycleId, 0);
          seal(statement, TENANT_A, cycleId, 1);
          calculate(
              statement,
              TENANT_A,
              cycleId,
              2,
              "calculation-recalc-stale",
              "c".repeat(64));
          recalculate(
              statement,
              TENANT_A,
              cycleId,
              2,
              "recalculation-stale-version",
              "d".repeat(64),
              "Stale version should fail");
        }
      }
    });

    assertSqlState("42501", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          recalculate(
              statement,
              TENANT_B,
              LEGACY_CYCLE_ID,
              3,
              "recalculation-tenant-mismatch",
              "e".repeat(64),
              "Tenant mismatch should fail");
        }
      }
    });

    assertSqlState("23505", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          UUID cycleId = createCycle(statement, TENANT_A, FEB_PERIOD_ID);
          resolve(statement, TENANT_A, cycleId, 0);
          seal(statement, TENANT_A, cycleId, 1);
          calculate(
              statement,
              TENANT_A,
              cycleId,
              2,
              "calculation-recalc-conflict",
              "f".repeat(64));
          recalculate(
              statement,
              TENANT_A,
              cycleId,
              3,
              "recalculation-conflict-key",
              "1".repeat(64),
              "Controlled replay conflict test");
          recalculate(
              statement,
              TENANT_A,
              cycleId,
              3,
              "recalculation-conflict-key",
              "2".repeat(64),
              "Controlled replay conflict test");
        }
      }
    });
  }
  @Test
  void cycleCreationRejectsClosedPeriodAndDuplicateCycle() throws Exception {
    assertSqlState("23514", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          createCycle(statement, TENANT_A, CLOSED_PERIOD_ID);
        }
      }
    });

    assertSqlState("23505", () -> {
      try (Connection connection = app()) {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
          tenant(statement, TENANT_A);
          createCycle(statement, TENANT_A, JAN_PERIOD_ID);
        }
      }
    });
  }

  private static CalculationSummary calculate(
      Statement statement,
      UUID requestedTenant,
      UUID cycleId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash) throws Exception {
    try (ResultSet result = statement.executeQuery("""
        SELECT calculation_request_id,result_count,
               gross_total,deduction_total,net_total,
               result_set_hash,cycle_version_no
        FROM payroll_calc.calculate_sealed_payroll(
          '%s','%s',%d,'%s','%s','calculation-test',
          clock_timestamp()
        )
        """.formatted(
            requestedTenant,
            cycleId,
            expectedVersion,
            idempotencyKey,
            requestHash))) {
      assertThat(result.next()).isTrue();
      return new CalculationSummary(
          result.getObject("calculation_request_id", UUID.class),
          result.getInt("result_count"),
          result.getBigDecimal("gross_total").toPlainString(),
          result.getBigDecimal("deduction_total").toPlainString(),
          result.getBigDecimal("net_total").toPlainString(),
          result.getString("result_set_hash"),
          result.getLong("cycle_version_no"));
    }
  }

  private record CalculationSummary(
      UUID requestId,
      int resultCount,
      String grossTotal,
      String deductionTotal,
      String netTotal,
      String resultSetHash,
      long cycleVersionNo) {}

  private static RecalculationSummary recalculate(
      Statement statement,
      UUID requestedTenant,
      UUID cycleId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String reason) throws Exception {
    try (ResultSet result = statement.executeQuery("""
        SELECT calculation_request_id,superseded_request_id,
               attempt_no,result_count,gross_total,deduction_total,
               net_total,result_set_hash,cycle_version_no
        FROM payroll_calc.recalculate_sealed_payroll(
          '%s','%s',%d,'%s','%s','%s','calculation-test',
          clock_timestamp()
        )
        """.formatted(
            requestedTenant,
            cycleId,
            expectedVersion,
            idempotencyKey,
            requestHash,
            reason))) {
      assertThat(result.next()).isTrue();
      return new RecalculationSummary(
          result.getObject("calculation_request_id", UUID.class),
          result.getObject("superseded_request_id", UUID.class),
          result.getInt("attempt_no"),
          result.getInt("result_count"),
          result.getBigDecimal("gross_total").toPlainString(),
          result.getBigDecimal("deduction_total").toPlainString(),
          result.getBigDecimal("net_total").toPlainString(),
          result.getString("result_set_hash"),
          result.getLong("cycle_version_no"));
    }
  }

  private record RecalculationSummary(
      UUID requestId,
      UUID supersededRequestId,
      int attemptNo,
      int resultCount,
      String grossTotal,
      String deductionTotal,
      String netTotal,
      String resultSetHash,
      long cycleVersionNo) {}
  private static UUID createCycle(
      Statement statement, UUID requestedTenant, UUID periodId)
      throws Exception {
    try (ResultSet result = statement.executeQuery("""
        SELECT payroll_ops.create_regular_payroll_cycle(
          '%s','%s','%s','population-test',clock_timestamp()
        )
        """.formatted(requestedTenant, PAY_GROUP_VERSION_ID, periodId))) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static Resolution resolve(
      Statement statement,
      UUID requestedTenant,
      UUID cycleId,
      long expectedVersion) throws Exception {
    try (ResultSet result = statement.executeQuery("""
        SELECT resolution_id,attempt_no,included_count,
               excluded_count,cycle_version_no
        FROM payroll_ops.resolve_payroll_population(
          '%s','%s',%d,'population-test',clock_timestamp()
        )
        """.formatted(requestedTenant, cycleId, expectedVersion))) {
      assertThat(result.next()).isTrue();
      return new Resolution(
          result.getObject("resolution_id", UUID.class),
          result.getInt("attempt_no"),
          result.getInt("included_count"),
          result.getInt("excluded_count"),
          result.getLong("cycle_version_no"));
    }
  }

  private static SealSummary seal(
      Statement statement,
      UUID requestedTenant,
      UUID cycleId,
      long expectedVersion) throws Exception {
    try (ResultSet result = statement.executeQuery("""
        SELECT snapshot_count,combined_hash,cycle_version_no
        FROM payroll_ops.seal_payroll_inputs(
          '%s','%s',%d,'snapshot-test',clock_timestamp()
        )
        """.formatted(requestedTenant, cycleId, expectedVersion))) {
      assertThat(result.next()).isTrue();
      return new SealSummary(
          result.getInt("snapshot_count"),
          result.getString("combined_hash"),
          result.getLong("cycle_version_no"));
    }
  }

  private static List<String> reasonCodes(
      Statement statement, UUID resolutionId) throws Exception {
    List<String> values = new ArrayList<>();
    try (ResultSet result = statement.executeQuery(
        "SELECT reason_code FROM payroll_ops.population_decision "
            + "WHERE population_resolution_id='" + resolutionId + "' "
            + "ORDER BY reason_code")) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }

  private static long queryLong(Statement statement, String sql)
      throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void seedV022FoundationAndLegacyPopulation()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.tenant_id='" + TENANT_A + "'");
      statement.execute("""
          INSERT INTO platform.tenant(
            id,code,name,created_by,updated_by
          ) VALUES
            ('%s','A','Synthetic Tenant A','test','test'),
            ('%s','B','Synthetic Tenant B','test','test')
          """.formatted(TENANT_A, TENANT_B));

      statement.execute("""
          INSERT INTO organisation.legal_entity(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','EXEC_LE','test','test')
          """.formatted(LEGAL_ID, TENANT_A));
      statement.execute("""
          INSERT INTO organisation.legal_entity_version(
            id,tenant_id,legal_entity_id,version_sequence,name,
            country_code,currency,effective_from,effective_to,
            approval_status,approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s',1,'Execution Legal Entity','IN','INR',
            '2026-01-01','2030-01-01','APPROVED',clock_timestamp(),
            'test','test','test'
          )
          """.formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));
      statement.execute("""
          INSERT INTO organisation.payroll_statutory_unit(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','EXEC_PSU','test','test')
          """.formatted(PSU_ID, TENANT_A));
      statement.execute("""
          INSERT INTO organisation.payroll_statutory_unit_version(
            id,tenant_id,payroll_statutory_unit_id,
            legal_entity_version_id,version_sequence,name,
            effective_from,effective_to,approval_status,
            approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s',1,'Execution PSU',
            '2026-01-01','2030-01-01','APPROVED',clock_timestamp(),
            'test','test','test'
          )
          """.formatted(PSU_VERSION_ID, TENANT_A, PSU_ID, LEGAL_VERSION_ID));
      statement.execute("""
          INSERT INTO organisation.establishment(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','EXEC_BLR','test','test')
          """.formatted(ESTABLISHMENT_ID, TENANT_A));
      statement.execute("""
          INSERT INTO organisation.establishment_version(
            id,tenant_id,establishment_id,
            payroll_statutory_unit_version_id,version_sequence,
            name,state_code,effective_from,effective_to,
            approval_status,approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s',1,'Execution Bengaluru','KA',
            '2026-01-01','2030-01-01','APPROVED',clock_timestamp(),
            'test','test','test'
          )
          """.formatted(
              ESTABLISHMENT_VERSION_ID,
              TENANT_A,
              ESTABLISHMENT_ID,
              PSU_VERSION_ID));

      statement.execute("""
          INSERT INTO organisation.payroll_calendar(
            id,tenant_id,code,name,frequency,timezone,
            created_by,updated_by
          ) VALUES (
            '%s','%s','EXEC_MONTHLY','Execution Monthly',
            'MONTHLY','Asia/Kolkata','test','test'
          )
          """.formatted(CALENDAR_ID, TENANT_A));
      insertPeriod(statement, JAN_PERIOD_ID, "2027-01", "2027-01-01", "2027-01-31", "OPEN");
      insertPeriod(statement, FEB_PERIOD_ID, "2027-02", "2027-02-01", "2027-02-28", "OPEN");
      insertPeriod(statement, CLOSED_PERIOD_ID, "2027-03", "2027-03-01", "2027-03-31", "CLOSED");
      statement.execute("""
          INSERT INTO organisation.pay_group(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','EXEC_MONTHLY','test','test')
          """.formatted(PAY_GROUP_ID, TENANT_A));
      statement.execute("""
          INSERT INTO organisation.pay_group_version(
            id,tenant_id,pay_group_id,
            payroll_statutory_unit_version_id,calendar_id,
            version_sequence,name,currency,proration_method,
            effective_from,effective_to,approval_status,
            approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s','%s',1,'Execution Monthly',
            'INR','CALENDAR_DAYS','2026-01-01','2030-01-01',
            'APPROVED',clock_timestamp(),'test','test','test'
          )
          """.formatted(
              PAY_GROUP_VERSION_ID,
              TENANT_A,
              PAY_GROUP_ID,
              PSU_VERSION_ID,
              CALENDAR_ID));

      statement.execute("""
          INSERT INTO compensation.pay_component(
            id,tenant_id,code,name,component_type,
            created_by,updated_by
          ) VALUES (
            '%s','%s','BASIC','Basic Pay','EARNING','test','test'
          )
          """.formatted(COMPONENT_ID, TENANT_A));
      statement.execute("""
          INSERT INTO compensation.pay_component_version(
            id,tenant_id,component_id,version_sequence,
            formula_type,fixed_amount,rounding_scale,
            effective_from,effective_to,approval_status,
            approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s',1,'FIXED',75000.0000,2,
            '2026-01-01','2030-01-01','APPROVED',clock_timestamp(),
            'test','test','test'
          )
          """.formatted(COMPONENT_VERSION_ID, TENANT_A, COMPONENT_ID));
      statement.execute("""
          INSERT INTO compensation.salary_structure(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','EXEC_DEFAULT','test','test')
          """.formatted(STRUCTURE_ID, TENANT_A));
      statement.execute("""
          INSERT INTO compensation.salary_structure_version(
            id,tenant_id,salary_structure_id,version_sequence,
            name,currency,effective_from,effective_to,
            approval_status,created_by,updated_by
          ) VALUES (
            '%s','%s','%s',1,'Execution Structure','INR',
            '2026-01-01','2030-01-01','DRAFT','test','test'
          )
          """.formatted(STRUCTURE_VERSION_ID, TENANT_A, STRUCTURE_ID));
      statement.execute("""
          INSERT INTO compensation.salary_structure_line(
            id,tenant_id,salary_structure_version_id,
            component_version_id,sequence_no,target_amount,
            effective_from,effective_to,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s',1,75000.0000,
            '2026-01-01','2030-01-01','test','test'
          )
          """.formatted(
              STRUCTURE_LINE_ID,
              TENANT_A,
              STRUCTURE_VERSION_ID,
              COMPONENT_VERSION_ID));
      statement.execute("SELECT compensation.approve_salary_structure_version("
          + "'" + TENANT_A + "','" + STRUCTURE_VERSION_ID
          + "','test',clock_timestamp())");

      seedEmployee(
          statement,
          "READY",
          READY_RELATIONSHIP_ID,
          READY_RELATIONSHIP_VERSION_ID,
          READY_PROFILE_ID,
          READY_ASSIGNMENT_ID,
          READY_ASSIGNMENT_VERSION_ID,
          READY_GROUP_ASSIGNMENT_ID,
          READY_SALARY_ASSIGNMENT_ID,
          "READY");
      seedEmployee(
          statement,
          "HOLD",
          HOLD_RELATIONSHIP_ID,
          HOLD_RELATIONSHIP_VERSION_ID,
          HOLD_PROFILE_ID,
          HOLD_ASSIGNMENT_ID,
          HOLD_ASSIGNMENT_VERSION_ID,
          HOLD_GROUP_ASSIGNMENT_ID,
          HOLD_SALARY_ASSIGNMENT_ID,
          "ON_HOLD");

      statement.execute("""
          INSERT INTO payroll_ops.payroll_cycle(
            id,tenant_id,pay_group_id,pay_period_id,
            cycle_type,status,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s','REGULAR','DRAFT','test','test'
          )
          """.formatted(
              LEGACY_CYCLE_ID,
              TENANT_A,
              PAY_GROUP_VERSION_ID,
              JAN_PERIOD_ID));
      statement.execute("""
          INSERT INTO payroll_ops.population_member(
            id,tenant_id,payroll_cycle_id,
            payroll_assignment_version_id,inclusion_reason,status,
            created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s','LEGACY_READY','INCLUDED','test','test'
          )
          """.formatted(
              LEGACY_MEMBER_ID,
              TENANT_A,
              LEGACY_CYCLE_ID,
              READY_ASSIGNMENT_VERSION_ID));
    }
  }

  private static void seedV023LegacyInputSnapshot() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.tenant_id='" + TENANT_A + "'");
      statement.execute("""
          INSERT INTO payroll_ops.input_snapshot(
            id,tenant_id,payroll_cycle_id,
            payroll_assignment_version_id,snapshot_hash,
            snapshot_payload,sealed_at,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s',repeat('a',64),'{}',
            clock_timestamp(),'legacy-test','legacy-test'
          )
          """.formatted(
              LEGACY_SNAPSHOT_ID,
              TENANT_A,
              LEGACY_CYCLE_ID,
              READY_ASSIGNMENT_VERSION_ID));
    }
  }

  private static void insertPeriod(
      Statement statement,
      UUID id,
      String code,
      String start,
      String end,
      String status) throws Exception {
    statement.execute("""
        INSERT INTO organisation.pay_period(
          id,tenant_id,calendar_id,period_code,
          period_start,period_end,payment_date,status,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s','%s','%s','%s','test','test'
        )
        """.formatted(
            id,
            TENANT_A,
            CALENDAR_ID,
            code,
            start,
            end,
            end,
            status));
  }

  private static void seedEmployee(
      Statement statement,
      String code,
      UUID relationshipId,
      UUID relationshipVersionId,
      UUID profileId,
      UUID assignmentId,
      UUID assignmentVersionId,
      UUID groupAssignmentId,
      UUID salaryAssignmentId,
      String profileStatus) throws Exception {
    statement.execute("""
        INSERT INTO employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,
          status,created_by,updated_by
        ) VALUES (
          '%s','%s','EXT-%s','EMP-%s','ACTIVE','test','test'
        )
        """.formatted(relationshipId, TENANT_A, code, code));
    statement.execute("""
        INSERT INTO employee_payroll.payroll_relationship_version(
          id,tenant_id,payroll_relationship_id,
          legal_entity_version_id,version_sequence,
          relationship_start,relationship_end,approval_status,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'2026-01-01','2030-01-01',
          'DRAFT','test','test'
        )
        """.formatted(
            relationshipVersionId,
            TENANT_A,
            relationshipId,
            LEGAL_VERSION_ID));
    statement.execute(
        "SELECT employee_payroll.approve_payroll_relationship_version("
            + "'" + TENANT_A + "','" + relationshipVersionId
            + "','test',clock_timestamp())");
    statement.execute("""
        INSERT INTO employee_payroll.employee_payroll_profile(
          id,tenant_id,payroll_relationship_id,
          currency,payroll_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','INR','INCOMPLETE','test','test'
        )
        """.formatted(profileId, TENANT_A, relationshipId));
    statement.execute("""
        INSERT INTO employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,
          assignment_number,status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','ASN-%s','ACTIVE','test','test'
        )
        """.formatted(assignmentId, TENANT_A, relationshipId, code));
    statement.execute("""
        INSERT INTO employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,
          payroll_relationship_version_id,establishment_version_id,
          version_sequence,assignment_start,assignment_end,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,'2026-01-01','2030-01-01',
          'DRAFT','test','test'
        )
        """.formatted(
            assignmentVersionId,
            TENANT_A,
            assignmentId,
            relationshipVersionId,
            ESTABLISHMENT_VERSION_ID));
    statement.execute(
        "SELECT employee_payroll.approve_payroll_assignment_version("
            + "'" + TENANT_A + "','" + assignmentVersionId
            + "','test',clock_timestamp())");
    statement.execute("""
        INSERT INTO employee_payroll.pay_group_assignment(
          id,tenant_id,payroll_assignment_version_id,
          pay_group_version_id,effective_from,effective_to,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','2026-01-01','2030-01-01',
          'DRAFT','test','test'
        )
        """.formatted(
            groupAssignmentId,
            TENANT_A,
            assignmentVersionId,
            PAY_GROUP_VERSION_ID));
    statement.execute(
        "SELECT employee_payroll.approve_pay_group_assignment("
            + "'" + TENANT_A + "','" + groupAssignmentId
            + "','test',clock_timestamp())");
    statement.execute("""
        INSERT INTO employee_payroll.salary_assignment(
          id,tenant_id,payroll_assignment_version_id,
          salary_structure_version_id,monthly_amount,currency,
          effective_from,effective_to,approval_status,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',75000.0000,'INR',
          '2026-01-01','2030-01-01','DRAFT','test','test'
        )
        """.formatted(
            salaryAssignmentId,
            TENANT_A,
            assignmentVersionId,
            STRUCTURE_VERSION_ID));
    statement.execute(
        "SELECT employee_payroll.approve_salary_assignment("
            + "'" + TENANT_A + "','" + salaryAssignmentId
            + "','test',clock_timestamp())");
    statement.execute(
        "SELECT employee_payroll.update_employee_payroll_profile_status("
            + "'" + TENANT_A + "','" + profileId + "','" + profileStatus
            + "',0,'test',clock_timestamp())");
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
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
      statement.execute(
          "REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute(
          "REVOKE CREATE ON SCHEMA public FROM payroll_app");
    }
  }

  private static void tenant(Statement statement, UUID tenantId)
      throws Exception {
    statement.execute("SET LOCAL app.tenant_id='" + tenantId + "'");
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static UUID id(String value) {
    return UUID.fromString(value);
  }

  private static void assertSqlState(String expected, SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .extracting(error -> ((SQLException) error).getSQLState())
        .isEqualTo(expected);
  }

  private record Resolution(
      UUID resolutionId,
      int attemptNo,
      int includedCount,
      int excludedCount,
      long cycleVersionNo) {}

  private record SealSummary(
      int snapshotCount,
      String combinedHash,
      long cycleVersionNo) {}

  @FunctionalInterface
  interface SqlAction {
    void run() throws Exception;
  }
}
