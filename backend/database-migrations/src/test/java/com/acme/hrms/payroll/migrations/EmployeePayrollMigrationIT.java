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
class EmployeePayrollMigrationIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final UUID LEGAL_ID =
      UUID.fromString("41000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID =
      UUID.fromString("41100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID =
      UUID.fromString("42000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID =
      UUID.fromString("42100000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_ID =
      UUID.fromString("43000000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_VERSION_ID =
      UUID.fromString("43100000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID =
      UUID.fromString("44000000-0000-0000-0000-000000000001");
  private static final UUID PERIOD_ID =
      UUID.fromString("44100000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_ID =
      UUID.fromString("45000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION_ID =
      UUID.fromString("45100000-0000-0000-0000-000000000001");

  private static final UUID COMPONENT_ID =
      UUID.fromString("46000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID =
      UUID.fromString("46100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_ID =
      UUID.fromString("47000000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_VERSION_ID =
      UUID.fromString("47100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_LINE_ID =
      UUID.fromString("47200000-0000-0000-0000-000000000001");

  private static final UUID LEGACY_RELATIONSHIP_VERSION_ID =
      UUID.fromString("48000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_ASSIGNMENT_VERSION_ID =
      UUID.fromString("48100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_PROFILE_ID =
      UUID.fromString("48200000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_GROUP_ASSIGNMENT_ID =
      UUID.fromString("48300000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_SALARY_ASSIGNMENT_ID =
      UUID.fromString("48400000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_CYCLE_ID =
      UUID.fromString("48500000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_POPULATION_ID =
      UUID.fromString("48600000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_SNAPSHOT_ID =
      UUID.fromString("48700000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_REQUEST_ID =
      UUID.fromString("48800000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_RESULT_ID =
      UUID.fromString("48900000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV020WithLegacyEmployeePayrollData()
      throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("20"))
        .load()
        .migrate();

    seedLegacyV020Data();

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
          """
          INSERT INTO platform.tenant(
            id,
            code,
            name,
            created_by,
            updated_by
          ) VALUES (
            '%s',
            'B',
            'Synthetic Tenant B',
            'test',
            'test'
          )
          """
              .formatted(TENANT_B));
    }
  }

  @Test
  void legacyVersionAndDownstreamLineageArePreserved()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT
                  relationship_identity.employee_number,
                  relationship_version.id relationship_version_id,
                  relationship_version.approval_status
                    relationship_status,
                  assignment_identity.assignment_number,
                  assignment_version.id assignment_version_id,
                  assignment_version.approval_status assignment_status,
                  profile.payroll_relationship_id profile_relationship_id,
                  group_assignment.payroll_assignment_version_id
                    group_assignment_version_id,
                  group_assignment.pay_group_version_id,
                  salary_assignment.payroll_assignment_version_id
                    salary_assignment_version_id,
                  salary_assignment.salary_structure_version_id,
                  population.payroll_assignment_version_id
                    population_assignment_version_id,
                  snapshot.payroll_assignment_version_id
                    snapshot_assignment_version_id,
                  result.payroll_assignment_version_id
                    result_assignment_version_id
                FROM employee_payroll.payroll_relationship
                  relationship_identity
                JOIN employee_payroll.payroll_relationship_version
                  relationship_version
                  ON relationship_version.tenant_id =
                    relationship_identity.tenant_id
                 AND relationship_version.payroll_relationship_id =
                    relationship_identity.id
                JOIN employee_payroll.payroll_assignment
                  assignment_identity
                  ON assignment_identity.tenant_id =
                    relationship_identity.tenant_id
                 AND assignment_identity.payroll_relationship_id =
                    relationship_identity.id
                JOIN employee_payroll.payroll_assignment_version
                  assignment_version
                  ON assignment_version.tenant_id =
                    assignment_identity.tenant_id
                 AND assignment_version.payroll_assignment_id =
                    assignment_identity.id
                JOIN employee_payroll.employee_payroll_profile profile
                  ON profile.tenant_id = relationship_identity.tenant_id
                 AND profile.payroll_relationship_id =
                    relationship_identity.id
                JOIN employee_payroll.pay_group_assignment group_assignment
                  ON group_assignment.tenant_id =
                    assignment_version.tenant_id
                 AND group_assignment.payroll_assignment_version_id =
                    assignment_version.id
                JOIN employee_payroll.salary_assignment salary_assignment
                  ON salary_assignment.tenant_id =
                    assignment_version.tenant_id
                 AND salary_assignment.payroll_assignment_version_id =
                    assignment_version.id
                JOIN payroll_ops.population_member population
                  ON population.tenant_id = assignment_version.tenant_id
                 AND population.payroll_assignment_version_id =
                    assignment_version.id
                JOIN payroll_ops.input_snapshot snapshot
                  ON snapshot.tenant_id = assignment_version.tenant_id
                 AND snapshot.payroll_assignment_version_id =
                    assignment_version.id
                JOIN payroll_calc.payroll_result result
                  ON result.tenant_id = assignment_version.tenant_id
                 AND result.payroll_assignment_version_id =
                    assignment_version.id
                 AND result.input_snapshot_id = snapshot.id
                WHERE relationship_identity.tenant_id = '%s'
                """
                    .formatted(TENANT_A))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("employee_number"))
          .isEqualTo("EMP-1");
      assertThat(
              result.getObject(
                  "relationship_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_RELATIONSHIP_VERSION_ID);
      assertThat(result.getString("relationship_status"))
          .isEqualTo("APPROVED");
      assertThat(result.getString("assignment_number"))
          .isEqualTo("ASN-1");
      assertThat(
              result.getObject(
                  "assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(result.getString("assignment_status"))
          .isEqualTo("APPROVED");
      assertThat(
              result.getObject(
                  "group_assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(
              result.getObject(
                  "pay_group_version_id",
                  UUID.class))
          .isEqualTo(PAY_GROUP_VERSION_ID);
      assertThat(
              result.getObject(
                  "salary_assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(
              result.getObject(
                  "salary_structure_version_id",
                  UUID.class))
          .isEqualTo(STRUCTURE_VERSION_ID);
      assertThat(
              result.getObject(
                  "population_assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(
              result.getObject(
                  "snapshot_assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(
              result.getObject(
                  "result_assignment_version_id",
                  UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(
              result.getObject(
                  "profile_relationship_id",
                  UUID.class))
          .isNotEqualTo(LEGACY_RELATIONSHIP_VERSION_ID);
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void appRoleCanApproveACompleteEmployeeConfiguration()
      throws Exception {
    UUID relationshipId =
        UUID.fromString("49000000-0000-0000-0000-000000000001");
    UUID relationshipVersionId =
        UUID.fromString("49100000-0000-0000-0000-000000000001");
    UUID assignmentId =
        UUID.fromString("49200000-0000-0000-0000-000000000001");
    UUID assignmentVersionId =
        UUID.fromString("49300000-0000-0000-0000-000000000001");
    UUID profileId =
        UUID.fromString("49400000-0000-0000-0000-000000000001");
    UUID groupAssignmentId =
        UUID.fromString("49500000-0000-0000-0000-000000000001");
    UUID salaryAssignmentId =
        UUID.fromString("49600000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship(
              id,
              tenant_id,
              external_employee_id,
              employee_number,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              'EMP-EXT-2',
              'EMP-2',
              'test',
              'test'
            )
            """
                .formatted(relationshipId, TENANT_A));

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship_version(
              id,
              tenant_id,
              payroll_relationship_id,
              legal_entity_version_id,
              version_sequence,
              relationship_start,
              relationship_end,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              1,
              '2027-01-01',
              '2029-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    relationshipVersionId,
                    TENANT_A,
                    relationshipId,
                    LEGAL_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT
              employee_payroll.approve_payroll_relationship_version(
                '%s',
                '%s',
                'test',
                '%s'
              )
            """
                .formatted(
                    TENANT_A,
                    relationshipVersionId,
                    Instant.parse("2026-07-22T06:00:00Z")),
            1);

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_assignment(
              id,
              tenant_id,
              payroll_relationship_id,
              assignment_number,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              'ASN-2',
              'test',
              'test'
            )
            """
                .formatted(
                    assignmentId,
                    TENANT_A,
                    relationshipId));

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_assignment_version(
              id,
              tenant_id,
              payroll_assignment_id,
              payroll_relationship_version_id,
              establishment_version_id,
              version_sequence,
              assignment_start,
              assignment_end,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              '%s',
              1,
              '2027-01-01',
              '2029-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    assignmentVersionId,
                    TENANT_A,
                    assignmentId,
                    relationshipVersionId,
                    ESTABLISHMENT_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT
              employee_payroll.approve_payroll_assignment_version(
                '%s',
                '%s',
                'test',
                '%s'
              )
            """
                .formatted(
                    TENANT_A,
                    assignmentVersionId,
                    Instant.parse("2026-07-22T06:01:00Z")),
            1);

        statement.execute(
            """
            INSERT INTO employee_payroll.employee_payroll_profile(
              id,
              tenant_id,
              payroll_relationship_id,
              currency,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              'INR',
              'test',
              'test'
            )
            """
                .formatted(
                    profileId,
                    TENANT_A,
                    relationshipId));

        statement.execute(
            """
            INSERT INTO employee_payroll.pay_group_assignment(
              id,
              tenant_id,
              payroll_assignment_version_id,
              pay_group_version_id,
              effective_from,
              effective_to,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              '2027-01-01',
              '2029-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    groupAssignmentId,
                    TENANT_A,
                    assignmentVersionId,
                    PAY_GROUP_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.approve_pay_group_assignment(
              '%s',
              '%s',
              'test',
              '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    groupAssignmentId,
                    Instant.parse("2026-07-22T06:02:00Z")),
            1);

        statement.execute(
            """
            INSERT INTO employee_payroll.salary_assignment(
              id,
              tenant_id,
              payroll_assignment_version_id,
              salary_structure_version_id,
              monthly_amount,
              currency,
              effective_from,
              effective_to,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              75000.0000,
              'INR',
              '2027-01-01',
              '2029-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    salaryAssignmentId,
                    TENANT_A,
                    assignmentVersionId,
                    STRUCTURE_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.approve_salary_assignment(
              '%s',
              '%s',
              'test',
              '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    salaryAssignmentId,
                    Instant.parse("2026-07-22T06:03:00Z")),
            1);

        assertFunctionResult(
            statement,
            """
            SELECT
              employee_payroll.update_employee_payroll_profile_status(
                '%s',
                '%s',
                'READY',
                0,
                'test',
                '%s'
              )
            """
                .formatted(
                    TENANT_A,
                    profileId,
                    Instant.parse("2026-07-22T06:04:00Z")),
            1);

        assertFunctionResult(
            statement,
            """
            SELECT
              employee_payroll.update_employee_payroll_profile_status(
                '%s',
                '%s',
                'ON_HOLD',
                1,
                'test',
                '%s'
              )
            """
                .formatted(
                    TENANT_A,
                    profileId,
                    Instant.parse("2026-07-22T06:05:00Z")),
            1);

        try (ResultSet state =
            statement.executeQuery(
                """
                SELECT
                  relationship.approval_status relationship_status,
                  assignment.approval_status assignment_status,
                  profile.payroll_status,
                  profile.version_no profile_version,
                  group_assignment.approval_status group_status,
                  salary_assignment.approval_status salary_status
                FROM employee_payroll.payroll_relationship_version
                  relationship
                JOIN employee_payroll.payroll_assignment_version assignment
                  ON assignment.tenant_id = relationship.tenant_id
                 AND assignment.payroll_relationship_version_id =
                   relationship.id
                JOIN employee_payroll.employee_payroll_profile profile
                  ON profile.tenant_id = relationship.tenant_id
                 AND profile.payroll_relationship_id =
                   relationship.payroll_relationship_id
                JOIN employee_payroll.pay_group_assignment group_assignment
                  ON group_assignment.tenant_id = assignment.tenant_id
                 AND group_assignment.payroll_assignment_version_id =
                   assignment.id
                JOIN employee_payroll.salary_assignment salary_assignment
                  ON salary_assignment.tenant_id = assignment.tenant_id
                 AND salary_assignment.payroll_assignment_version_id =
                   assignment.id
                WHERE relationship.id = '%s'
                """
                    .formatted(relationshipVersionId))) {
          assertThat(state.next()).isTrue();
          assertThat(state.getString("relationship_status"))
              .isEqualTo("APPROVED");
          assertThat(state.getString("assignment_status"))
              .isEqualTo("APPROVED");
          assertThat(state.getString("payroll_status"))
              .isEqualTo("ON_HOLD");
          assertThat(state.getLong("profile_version")).isEqualTo(2);
          assertThat(state.getString("group_status"))
              .isEqualTo("APPROVED");
          assertThat(state.getString("salary_status"))
              .isEqualTo("APPROVED");
        }

        try (ResultSet privilege =
            statement.executeQuery(
                """
                SELECT
                  has_table_privilege(
                    current_user,
                    'employee_payroll.payroll_relationship_version',
                    'UPDATE'
                  ),
                  has_table_privilege(
                    current_user,
                    'employee_payroll.payroll_assignment_version',
                    'UPDATE'
                  ),
                  has_table_privilege(
                    current_user,
                    'employee_payroll.salary_assignment',
                    'UPDATE'
                  )
                """)) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
          assertThat(privilege.getBoolean(2)).isFalse();
          assertThat(privilege.getBoolean(3)).isFalse();
        }
      }

      connection.commit();
    }
  }

  @Test
  void runtimeCannotCreateAReadyPayrollProfileDirectly()
      throws Exception {
    UUID relationshipId =
        UUID.fromString("49a00000-0000-0000-0000-000000000001");
    UUID profileId =
        UUID.fromString("49a00000-0000-0000-0000-000000000002");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship(
              id,
              tenant_id,
              external_employee_id,
              employee_number,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              'EMP-EXT-DIRECT-READY',
              'EMP-DIRECT-READY',
              'test',
              'test'
            )
            """
                .formatted(relationshipId, TENANT_A));

        assertThatThrownBy(
                () ->
                    statement.execute(
                        """
                        INSERT INTO employee_payroll.employee_payroll_profile(
                          id,
                          tenant_id,
                          payroll_relationship_id,
                          currency,
                          payroll_status,
                          created_by,
                          updated_by
                        ) VALUES (
                          '%s',
                          '%s',
                          '%s',
                          'INR',
                          'READY',
                          'test',
                          'test'
                        )
                        """
                            .formatted(
                                profileId,
                                TENANT_A,
                                relationshipId)))
            .hasMessageContaining(
                "runtime employee payroll profiles must be created "
                    + "as INCOMPLETE");
      }

      connection.rollback();
    }
  }

  @Test
  void incompletePayrollProfileCannotBecomeReady()
      throws Exception {
    UUID relationshipId =
        UUID.fromString("49b00000-0000-0000-0000-000000000001");
    UUID relationshipVersionId =
        UUID.fromString("49c00000-0000-0000-0000-000000000001");
    UUID profileId =
        UUID.fromString("49d00000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship(
              id,
              tenant_id,
              external_employee_id,
              employee_number,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              'EMP-EXT-INCOMPLETE',
              'EMP-INCOMPLETE',
              'test',
              'test'
            )
            """
                .formatted(relationshipId, TENANT_A));

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship_version(
              id,
              tenant_id,
              payroll_relationship_id,
              legal_entity_version_id,
              version_sequence,
              relationship_start,
              relationship_end,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              1,
              '2027-01-01',
              '2029-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    relationshipVersionId,
                    TENANT_A,
                    relationshipId,
                    LEGAL_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT
              employee_payroll.approve_payroll_relationship_version(
                '%s',
                '%s',
                'test',
                '%s'
              )
            """
                .formatted(
                    TENANT_A,
                    relationshipVersionId,
                    Instant.parse("2026-07-22T06:06:00Z")),
            1);

        statement.execute(
            """
            INSERT INTO employee_payroll.employee_payroll_profile(
              id,
              tenant_id,
              payroll_relationship_id,
              currency,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              'INR',
              'test',
              'test'
            )
            """
                .formatted(
                    profileId,
                    TENANT_A,
                    relationshipId));

        try (ResultSet profile =
            statement.executeQuery(
                """
                SELECT payroll_status
                FROM employee_payroll.employee_payroll_profile
                WHERE id = '%s'
                """
                    .formatted(profileId))) {
          assertThat(profile.next()).isTrue();
          assertThat(profile.getString("payroll_status"))
              .isEqualTo("INCOMPLETE");
        }

        assertThatThrownBy(
                () ->
                    statement.executeQuery(
                        """
                        SELECT
                          employee_payroll.update_employee_payroll_profile_status(
                            '%s',
                            '%s',
                            'READY',
                            0,
                            'test',
                            '%s'
                          )
                        """
                            .formatted(
                                TENANT_A,
                                profileId,
                                Instant.parse(
                                    "2026-07-22T06:07:00Z"))))
            .hasMessageContaining(
                "READY requires an active relationship with overlapping "
                    + "approved assignment, pay-group and salary configuration");
      }

      connection.rollback();
    }
  }

  @Test
  void overlappingApprovedSalaryAssignmentsAreRejected()
      throws Exception {
    UUID overlappingAssignmentId =
        UUID.fromString("49700000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            """
            INSERT INTO employee_payroll.salary_assignment(
              id,
              tenant_id,
              payroll_assignment_version_id,
              salary_structure_version_id,
              monthly_amount,
              currency,
              effective_from,
              effective_to,
              approval_status,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              '%s',
              60000.0000,
              'INR',
              '2027-01-01',
              '2028-01-01',
              'DRAFT',
              'test',
              'test'
            )
            """
                .formatted(
                    overlappingAssignmentId,
                    TENANT_A,
                    LEGACY_ASSIGNMENT_VERSION_ID,
                    STRUCTURE_VERSION_ID));

        assertThatThrownBy(
                () ->
                    statement.executeQuery(
                        """
                        SELECT
                          employee_payroll.approve_salary_assignment(
                            '%s',
                            '%s',
                            'test',
                            '%s'
                          )
                        """
                            .formatted(
                                TENANT_A,
                                overlappingAssignmentId,
                                Instant.parse(
                                    "2026-07-22T06:10:00Z"))))
            .hasMessageContaining(
                "salary_assignment_approved_no_overlap");
      }

      connection.rollback();
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAFromTenantB()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_B + "'");

        assertCount(statement, "employee_payroll.payroll_relationship", 0);
        assertCount(
            statement,
            "employee_payroll.payroll_relationship_version",
            0);
        assertCount(statement, "employee_payroll.payroll_assignment", 0);
        assertCount(
            statement,
            "employee_payroll.payroll_assignment_version",
            0);
        assertCount(
            statement,
            "employee_payroll.employee_payroll_profile",
            0);
        assertCount(
            statement,
            "employee_payroll.pay_group_assignment",
            0);
        assertCount(
            statement,
            "employee_payroll.salary_assignment",
            0);
      }

      connection.rollback();
    }
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
  }

  private static void seedLegacyV020Data() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id,
            code,
            name,
            created_by,
            updated_by
          ) VALUES (
            '%s',
            'A',
            'Synthetic Tenant A',
            'test',
            'test'
          )
          """
              .formatted(TENANT_A));
    }

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        seedOrganisation(statement);
        seedCompensation(statement);
        seedLegacyEmployeePayroll(statement);
      }

      connection.commit();
    }
  }

  private static void seedOrganisation(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.legal_entity(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'ACME_IN',
          'test',
          'test'
        )
        """
            .formatted(LEGAL_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.legal_entity_version(
          id,
          tenant_id,
          legal_entity_id,
          version_sequence,
          name,
          country_code,
          currency,
          effective_from,
          effective_to,
          approval_status,
          approved_at,
          approved_by,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'Acme India',
          'IN',
          'INR',
          '2026-01-01',
          '2030-01-01',
          'APPROVED',
          clock_timestamp(),
          'test',
          'test',
          'test'
        )
        """
            .formatted(
                LEGAL_VERSION_ID,
                TENANT_A,
                LEGAL_ID));

    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'ACME_PSU',
          'test',
          'test'
        )
        """
            .formatted(PSU_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit_version(
          id,
          tenant_id,
          payroll_statutory_unit_id,
          legal_entity_version_id,
          version_sequence,
          name,
          effective_from,
          effective_to,
          approval_status,
          approved_at,
          approved_by,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          1,
          'Acme PSU',
          '2026-01-01',
          '2030-01-01',
          'APPROVED',
          clock_timestamp(),
          'test',
          'test',
          'test'
        )
        """
            .formatted(
                PSU_VERSION_ID,
                TENANT_A,
                PSU_ID,
                LEGAL_VERSION_ID));

    statement.execute(
        """
        INSERT INTO organisation.establishment(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'BLR',
          'test',
          'test'
        )
        """
            .formatted(ESTABLISHMENT_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.establishment_version(
          id,
          tenant_id,
          establishment_id,
          payroll_statutory_unit_version_id,
          version_sequence,
          name,
          state_code,
          effective_from,
          effective_to,
          approval_status,
          approved_at,
          approved_by,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          1,
          'Bengaluru',
          'KA',
          '2026-01-01',
          '2030-01-01',
          'APPROVED',
          clock_timestamp(),
          'test',
          'test',
          'test'
        )
        """
            .formatted(
                ESTABLISHMENT_VERSION_ID,
                TENANT_A,
                ESTABLISHMENT_ID,
                PSU_VERSION_ID));

    statement.execute(
        """
        INSERT INTO organisation.payroll_calendar(
          id,
          tenant_id,
          code,
          name,
          frequency,
          timezone,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'MONTHLY_IN',
          'Monthly India',
          'MONTHLY',
          'Asia/Kolkata',
          'test',
          'test'
        )
        """
            .formatted(CALENDAR_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.pay_period(
          id,
          tenant_id,
          calendar_id,
          period_code,
          period_start,
          period_end,
          payment_date,
          status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '2027-01',
          '2027-01-01',
          '2027-01-31',
          '2027-01-31',
          'OPEN',
          'test',
          'test'
        )
        """
            .formatted(PERIOD_ID, TENANT_A, CALENDAR_ID));

    statement.execute(
        """
        INSERT INTO organisation.pay_group(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'MONTHLY_IN',
          'test',
          'test'
        )
        """
            .formatted(PAY_GROUP_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.pay_group_version(
          id,
          tenant_id,
          pay_group_id,
          payroll_statutory_unit_version_id,
          calendar_id,
          version_sequence,
          name,
          currency,
          proration_method,
          effective_from,
          effective_to,
          approval_status,
          approved_at,
          approved_by,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          1,
          'Monthly India',
          'INR',
          'CALENDAR_DAYS',
          '2026-01-01',
          '2030-01-01',
          'APPROVED',
          clock_timestamp(),
          'test',
          'test',
          'test'
        )
        """
            .formatted(
                PAY_GROUP_VERSION_ID,
                TENANT_A,
                PAY_GROUP_ID,
                PSU_VERSION_ID,
                CALENDAR_ID));
  }

  private static void seedCompensation(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO compensation.pay_component(
          id,
          tenant_id,
          code,
          name,
          component_type,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'BASIC',
          'Basic Pay',
          'EARNING',
          'test',
          'test'
        )
        """
            .formatted(COMPONENT_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO compensation.pay_component_version(
          id,
          tenant_id,
          component_id,
          version_sequence,
          formula_type,
          formula_expression,
          fixed_amount,
          rounding_scale,
          effective_from,
          effective_to,
          approval_status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'FIXED',
          NULL,
          1000.0000,
          2,
          '2026-01-01',
          '2030-01-01',
          'DRAFT',
          'test',
          'test'
        )
        """
            .formatted(
                COMPONENT_VERSION_ID,
                TENANT_A,
                COMPONENT_ID));

    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_pay_component_version(
          '%s',
          '%s',
          'test',
          '%s'
        )
        """
            .formatted(
                TENANT_A,
                COMPONENT_VERSION_ID,
                Instant.parse("2026-07-22T05:00:00Z")),
        1);

    statement.execute(
        """
        INSERT INTO compensation.salary_structure(
          id,
          tenant_id,
          code,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'DEFAULT',
          'test',
          'test'
        )
        """
            .formatted(STRUCTURE_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO compensation.salary_structure_version(
          id,
          tenant_id,
          salary_structure_id,
          version_sequence,
          name,
          currency,
          effective_from,
          effective_to,
          approval_status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'Default Structure',
          'INR',
          '2026-01-01',
          '2030-01-01',
          'DRAFT',
          'test',
          'test'
        )
        """
            .formatted(
                STRUCTURE_VERSION_ID,
                TENANT_A,
                STRUCTURE_ID));

    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          id,
          tenant_id,
          salary_structure_version_id,
          component_version_id,
          sequence_no,
          target_amount,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          1,
          1000.0000,
          '2026-01-01',
          '2030-01-01',
          'test',
          'test'
        )
        """
            .formatted(
                STRUCTURE_LINE_ID,
                TENANT_A,
                STRUCTURE_VERSION_ID,
                COMPONENT_VERSION_ID));

    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_salary_structure_version(
          '%s',
          '%s',
          'test',
          '%s'
        )
        """
            .formatted(
                TENANT_A,
                STRUCTURE_VERSION_ID,
                Instant.parse("2026-07-22T05:01:00Z")),
        1);
  }

  private static void seedLegacyEmployeePayroll(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship(
          id,
          tenant_id,
          external_employee_id,
          employee_number,
          legal_entity_id,
          relationship_start,
          relationship_end,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          'EMP-EXT-1',
          'EMP-1',
          '%s',
          '2026-01-01',
          '2030-01-01',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_RELATIONSHIP_VERSION_ID,
                TENANT_A,
                LEGAL_VERSION_ID));

    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment(
          id,
          tenant_id,
          payroll_relationship_id,
          establishment_id,
          assignment_number,
          assignment_start,
          assignment_end,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          'ASN-1',
          '2026-01-01',
          '2030-01-01',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_ASSIGNMENT_VERSION_ID,
                TENANT_A,
                LEGACY_RELATIONSHIP_VERSION_ID,
                ESTABLISHMENT_VERSION_ID));

    statement.execute(
        """
        INSERT INTO employee_payroll.employee_payroll_profile(
          id,
          tenant_id,
          payroll_relationship_id,
          currency,
          payroll_status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          'INR',
          'READY',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_PROFILE_ID,
                TENANT_A,
                LEGACY_RELATIONSHIP_VERSION_ID));

    statement.execute(
        """
        INSERT INTO employee_payroll.pay_group_assignment(
          id,
          tenant_id,
          payroll_assignment_id,
          pay_group_id,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '2026-01-01',
          '2030-01-01',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_GROUP_ASSIGNMENT_ID,
                TENANT_A,
                LEGACY_ASSIGNMENT_VERSION_ID,
                PAY_GROUP_VERSION_ID));

    statement.execute(
        """
        INSERT INTO employee_payroll.salary_assignment(
          id,
          tenant_id,
          payroll_assignment_id,
          salary_structure_version_id,
          monthly_amount,
          currency,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          50000.0000,
          'INR',
          '2026-01-01',
          '2030-01-01',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_SALARY_ASSIGNMENT_ID,
                TENANT_A,
                LEGACY_ASSIGNMENT_VERSION_ID,
                STRUCTURE_VERSION_ID));

    statement.execute(
        """
        INSERT INTO payroll_ops.payroll_cycle(
          id,
          tenant_id,
          pay_group_id,
          pay_period_id,
          cycle_type,
          status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          'REGULAR',
          'DRAFT',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_CYCLE_ID,
                TENANT_A,
                PAY_GROUP_VERSION_ID,
                PERIOD_ID));

    statement.execute(
        """
        INSERT INTO payroll_ops.population_member(
          id,
          tenant_id,
          payroll_cycle_id,
          payroll_assignment_id,
          inclusion_reason,
          status,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          'SYNTHETIC',
          'INCLUDED',
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_POPULATION_ID,
                TENANT_A,
                LEGACY_CYCLE_ID,
                LEGACY_ASSIGNMENT_VERSION_ID));

    statement.execute(
        """
        INSERT INTO payroll_ops.input_snapshot(
          id,
          tenant_id,
          payroll_cycle_id,
          payroll_assignment_id,
          snapshot_hash,
          snapshot_payload,
          sealed_at,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          repeat('a', 64),
          '{}',
          clock_timestamp(),
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_SNAPSHOT_ID,
                TENANT_A,
                LEGACY_CYCLE_ID,
                LEGACY_ASSIGNMENT_VERSION_ID));

    statement.execute(
        """
        INSERT INTO payroll_calc.calculation_request(
          id,
          tenant_id,
          payroll_cycle_id,
          idempotency_key,
          request_hash,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          'legacy-employee-request',
          repeat('b', 64),
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_REQUEST_ID,
                TENANT_A,
                LEGACY_CYCLE_ID));

    statement.execute(
        """
        INSERT INTO payroll_calc.payroll_result(
          id,
          tenant_id,
          calculation_request_id,
          payroll_cycle_id,
          payroll_assignment_id,
          input_snapshot_id,
          result_hash,
          currency,
          gross_amount,
          net_amount,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          repeat('c', 64),
          'INR',
          50000.0000,
          50000.0000,
          'test',
          'test'
        )
        """
            .formatted(
                LEGACY_RESULT_ID,
                TENANT_A,
                LEGACY_REQUEST_ID,
                LEGACY_CYCLE_ID,
                LEGACY_ASSIGNMENT_VERSION_ID,
                LEGACY_SNAPSHOT_ID));
  }

  private static void assertFunctionResult(
      Statement statement,
      String sql,
      long expected)
      throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(expected);
    }
  }

  private static void assertCount(
      Statement statement,
      String table,
      long expected)
      throws Exception {
    try (ResultSet result =
        statement.executeQuery("SELECT count(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(expected);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "postgres",
        "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "payroll_app",
        APP_PASSWORD);
  }
}
