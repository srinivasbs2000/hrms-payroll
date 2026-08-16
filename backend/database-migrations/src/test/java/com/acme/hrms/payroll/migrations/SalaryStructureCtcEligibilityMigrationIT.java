package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class SalaryStructureCtcEligibilityMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

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
  private static final UUID COMPONENT_ID =
      UUID.fromString("44000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID =
      UUID.fromString("44100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_STRUCTURE_ID =
      UUID.fromString("45000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_STRUCTURE_VERSION_ID =
      UUID.fromString("45100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_LINE_ID =
      UUID.fromString("45200000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_RELATIONSHIP_VERSION_ID =
      UUID.fromString("46000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_ASSIGNMENT_VERSION_ID =
      UUID.fromString("46100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_SALARY_ASSIGNMENT_ID =
      UUID.fromString("46200000-0000-0000-0000-000000000001");

  private static final String CONFIGURATION_HASH = "a".repeat(64);
  private static final String REQUEST_HASH = "b".repeat(64);
  private static final String RESULT_HASH = "c".repeat(64);
  private static final String FAILED_RESULT_HASH = "d".repeat(64);

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV020WithPopulatedLineage() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("20"))
        .load()
        .migrate();

    seedLegacyV020Data();

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES (
            '%s', 'B', 'Synthetic Tenant B', 'test', 'test'
          )
          """
              .formatted(TENANT_B));
    }
  }

  @Test
  void populatedUpgradePreservesStructureLineAndSalaryAssignmentIds()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT
                  version.id structure_version_id,
                  version.structure_schema_version,
                  line.id line_id,
                  line.line_schema_version,
                  assignment.id salary_assignment_id,
                  assignment.payroll_assignment_version_id,
                  assignment.salary_structure_version_id
                FROM compensation.salary_structure_version version
                JOIN compensation.salary_structure_line line
                  ON line.tenant_id = version.tenant_id
                 AND line.salary_structure_version_id = version.id
                JOIN employee_payroll.salary_assignment assignment
                  ON assignment.tenant_id = version.tenant_id
                 AND assignment.salary_structure_version_id = version.id
                WHERE version.tenant_id = '%s'
                  AND version.id = '%s'
                """
                    .formatted(TENANT_A, LEGACY_STRUCTURE_VERSION_ID))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject("structure_version_id", UUID.class))
          .isEqualTo(LEGACY_STRUCTURE_VERSION_ID);
      assertThat(result.getInt("structure_schema_version")).isZero();
      assertThat(result.getObject("line_id", UUID.class))
          .isEqualTo(LEGACY_LINE_ID);
      assertThat(result.getInt("line_schema_version")).isZero();
      assertThat(result.getObject("salary_assignment_id", UUID.class))
          .isEqualTo(LEGACY_SALARY_ASSIGNMENT_ID);
      assertThat(result.getObject("payroll_assignment_version_id", UUID.class))
          .isEqualTo(LEGACY_ASSIGNMENT_VERSION_ID);
      assertThat(result.getObject("salary_structure_version_id", UUID.class))
          .isEqualTo(LEGACY_STRUCTURE_VERSION_ID);
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void newConfigurationTablesAreForcedRlsAndLifecycleFunctionsAreRestricted()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT count(*)
                FROM pg_class relation
                JOIN pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'compensation'
                  AND relation.relname IN (
                    'ctc_policy',
                    'ctc_policy_version',
                    'ctc_policy_treatment',
                    'eligibility_rule',
                    'eligibility_rule_version',
                    'eligibility_rule_criterion',
                    'salary_structure_validation',
                    'salary_structure_validation_line'
                  )
                  AND relation.relrowsecurity
                  AND relation.relforcerowsecurity
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(8);
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_B + "'");
        assertThat(count(statement, "compensation.ctc_policy")).isZero();
        assertThat(count(statement, "compensation.eligibility_rule")).isZero();
        assertThat(count(statement, "compensation.salary_structure_validation"))
            .isZero();

        try (ResultSet privileges =
            statement.executeQuery(
                """
                SELECT
                  has_table_privilege(
                    current_user,
                    'compensation.ctc_policy_version',
                    'UPDATE'
                  ),
                  has_table_privilege(
                    current_user,
                    'compensation.salary_structure_validation',
                    'DELETE'
                  ),
                  has_function_privilege(
                    current_user,
                    'compensation.approve_ctc_policy_version('
                      || 'uuid,uuid,character varying,timestamp with time zone)',
                    'EXECUTE'
                  ),
                  has_function_privilege(
                    current_user,
                    'compensation.bind_salary_structure_validation('
                      || 'uuid,uuid,uuid,bigint,character varying,'
                      || 'timestamp with time zone)',
                    'EXECUTE'
                  )
                """)) {
          assertThat(privileges.next()).isTrue();
          assertThat(privileges.getBoolean(1)).isFalse();
          assertThat(privileges.getBoolean(2)).isFalse();
          assertThat(privileges.getBoolean(3)).isTrue();
          assertThat(privileges.getBoolean(4)).isTrue();
        }
      }
      connection.rollback();
    }
  }

  @Test
  void legacySchemaZeroRuntimeContractRemainsBackwardCompatible()
      throws Exception {
    UUID structureId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            """
            INSERT INTO compensation.salary_structure(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'RUNTIME_SCHEMA_ZERO', 'maker', 'maker'
            )
            """
                .formatted(structureId, TENANT_A));
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
              'Runtime Schema Zero',
              'INR',
              '2027-01-01',
              '2030-01-01',
              'DRAFT',
              'maker',
              'maker'
            )
            """
                .formatted(versionId, TENANT_A, structureId));
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
              '2027-01-01',
              '2030-01-01',
              'maker',
              'maker'
            )
            """
                .formatted(
                    lineId, TENANT_A, versionId, COMPONENT_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT compensation.approve_salary_structure_version(
              '%s', '%s', 'maker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    versionId,
                    Instant.parse("2026-08-05T18:30:00Z")),
            1);

        try (ResultSet state =
            statement.executeQuery(
                """
                SELECT
                  version.structure_schema_version,
                  version.approval_status,
                  line.line_schema_version
                FROM compensation.salary_structure_version version
                JOIN compensation.salary_structure_line line
                  ON line.tenant_id = version.tenant_id
                 AND line.salary_structure_version_id = version.id
                WHERE version.tenant_id = '%s'
                  AND version.id = '%s'
                """
                    .formatted(TENANT_A, versionId))) {
          assertThat(state.next()).isTrue();
          assertThat(state.getInt("structure_schema_version")).isZero();
          assertThat(state.getString("approval_status")).isEqualTo("APPROVED");
          assertThat(state.getInt("line_schema_version")).isZero();
          assertThat(state.next()).isFalse();
        }
      }
      connection.rollback();
    }
  }

  @Test
  void typedEligibilityCriteriaRejectInvalidFactAndValueShapes()
      throws Exception {
    UUID ruleId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            """
            INSERT INTO compensation.eligibility_rule(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'TYPED_NEGATIVE_PATH', 'maker', 'maker'
            )
            """
                .formatted(ruleId, TENANT_A));
        statement.execute(
            """
            INSERT INTO compensation.eligibility_rule_version(
              id,
              tenant_id,
              eligibility_rule_id,
              version_sequence,
              name,
              result_when_matched,
              result_when_not_matched,
              effective_from,
              created_by,
              updated_by
            ) VALUES (
              '%s',
              '%s',
              '%s',
              1,
              'Typed Negative Path',
              'ELIGIBLE',
              'NOT_ELIGIBLE',
              '2027-01-01',
              'maker',
              'maker'
            )
            """
                .formatted(versionId, TENANT_A, ruleId));
        statement.execute(
            criterionSql(
                UUID.randomUUID(),
                ruleId,
                versionId,
                1,
                "SERVICE_MONTHS",
                "NUMBER",
                "GTE",
                "12"));

        statement.execute("SAVEPOINT invalid_type");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        criterionSql(
                            UUID.randomUUID(),
                            ruleId,
                            versionId,
                            2,
                            "SERVICE_MONTHS",
                            "TEXT",
                            "GTE",
                            "\"12\"")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("eligibility_rule_criterion_key_type_ck");
        statement.execute("ROLLBACK TO SAVEPOINT invalid_type");

        statement.execute("SAVEPOINT invalid_value");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        criterionSql(
                            UUID.randomUUID(),
                            ruleId,
                            versionId,
                            3,
                            "EMPLOYMENT_TYPE",
                            "TEXT",
                            "IN",
                            "[1,2]")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("eligibility_rule_criterion_typed_value_ck");
        statement.execute("ROLLBACK TO SAVEPOINT invalid_value");

        statement.execute("SAVEPOINT invalid_date");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        criterionSql(
                            UUID.randomUUID(),
                            ruleId,
                            versionId,
                            4,
                            "EFFECTIVE_DATE",
                            "DATE",
                            "EQ",
                            "\"2027-02-30\"")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("eligibility_rule_criterion_typed_value_ck");
        statement.execute("ROLLBACK TO SAVEPOINT invalid_date");

        statement.execute("SAVEPOINT invalid_uuid");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        criterionSql(
                            UUID.randomUUID(),
                            ruleId,
                            versionId,
                            5,
                            "LEGAL_ENTITY_VERSION_ID",
                            "UUID",
                            "EQ",
                            "\"not-a-uuid\"")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("eligibility_rule_criterion_typed_value_ck");
      }
      connection.rollback();
    }
  }

  @Test
  void controlledLifecycleBindsLatestValidationAndApprovesSchemaOne()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        ConfigurationIds configuration =
            createApprovedConfiguration(statement, "CONTROLLED");

        UUID structureId = UUID.randomUUID();
        UUID structureVersionId = UUID.randomUUID();
        UUID structureLineId = UUID.randomUUID();
        UUID validationId = UUID.randomUUID();
        UUID validationLineId = UUID.randomUUID();

        statement.execute(
            """
            INSERT INTO compensation.salary_structure(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'CONTROLLED_STRUCTURE', 'maker', 'maker'
            )
            """
                .formatted(structureId, TENANT_A));
        statement.execute(
            schemaOneStructureSql(
                structureVersionId,
                structureId,
                configuration.ctcPolicyVersionId(),
                configuration.eligibilityRuleVersionId()));
        statement.execute(
            schemaOneResidualLineSql(structureLineId, structureVersionId));
        statement.execute(
            validationSql(
                validationId,
                structureId,
                structureVersionId,
                configuration.ctcPolicyVersionId(),
                configuration.eligibilityRuleVersionId()));
        statement.execute(
            validationLineSql(
                validationLineId,
                validationId,
                1,
                COMPONENT_ID,
                COMPONENT_VERSION_ID));

        assertFunctionResult(
            statement,
            """
            SELECT compensation.bind_salary_structure_validation(
              '%s', '%s', '%s', 0, 'maker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    structureVersionId,
                    validationId,
                    Instant.parse("2026-08-05T12:00:00Z")),
            1);

        statement.execute("SAVEPOINT immutable_validation_line");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        validationLineSql(
                            UUID.randomUUID(),
                            validationId,
                            2,
                            COMPONENT_ID,
                            COMPONENT_VERSION_ID)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining(
                "validation lines cannot be appended after evidence is bound");
        statement.execute("ROLLBACK TO SAVEPOINT immutable_validation_line");

        statement.execute("SAVEPOINT immutable_structure_line");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        schemaOneResidualLineSql(
                            UUID.randomUUID(), structureVersionId)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining(
                "salary-structure lines cannot be appended after validation is bound");
        statement.execute("ROLLBACK TO SAVEPOINT immutable_structure_line");

        assertFunctionResult(
            statement,
            """
            SELECT compensation.submit_salary_structure_version(
              '%s', '%s', '%s', 1,
              'Ready for governed review', 'maker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    structureId,
                    structureVersionId,
                    Instant.parse("2026-08-05T12:00:20Z")),
            1);

        statement.execute("SAVEPOINT stale_validation");
        statement.execute(
            failedValidationSql(
                UUID.randomUUID(),
                structureId,
                structureVersionId,
                configuration.ctcPolicyVersionId(),
                configuration.eligibilityRuleVersionId()));
        assertThatThrownBy(
                () ->
                    statement.executeQuery(
                        """
                        SELECT compensation.approve_salary_structure_version(
                          '%s', '%s', 'checker', '%s'
                        )
                        """
                            .formatted(
                                TENANT_A,
                                structureVersionId,
                                Instant.parse("2026-08-05T12:00:30Z"))))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining(
                "exact structural validation submitted for review");
        statement.execute("ROLLBACK TO SAVEPOINT stale_validation");

        assertFunctionResult(
            statement,
            """
            SELECT compensation.approve_salary_structure_version(
              '%s', '%s', 'maker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    structureVersionId,
                    Instant.parse("2026-08-05T12:01:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT compensation.approve_salary_structure_version(
              '%s', '%s', 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    structureVersionId,
                    Instant.parse("2026-08-05T12:02:00Z")),
            1);

        try (ResultSet state =
            statement.executeQuery(
                """
                SELECT
                  structure.approval_status,
                  structure.validation_fingerprint,
                  structure.version_no,
                  policy.lifecycle_status policy_status,
                  rule.lifecycle_status rule_status
                FROM compensation.salary_structure_version structure
                JOIN compensation.ctc_policy policy
                  ON policy.tenant_id = structure.tenant_id
                 AND policy.id = '%s'
                JOIN compensation.eligibility_rule rule
                  ON rule.tenant_id = structure.tenant_id
                 AND rule.id = '%s'
                WHERE structure.tenant_id = '%s'
                  AND structure.id = '%s'
                """
                    .formatted(
                        configuration.ctcPolicyId(),
                        configuration.eligibilityRuleId(),
                        TENANT_A,
                        structureVersionId))) {
          assertThat(state.next()).isTrue();
          assertThat(state.getString("approval_status")).isEqualTo("APPROVED");
          assertThat(state.getString("validation_fingerprint"))
              .isEqualTo(RESULT_HASH);
          assertThat(state.getLong("version_no")).isEqualTo(3);
          assertThat(state.getString("policy_status")).isEqualTo("ACTIVE");
          assertThat(state.getString("rule_status")).isEqualTo("ACTIVE");
        }

        assertFunctionResult(
            statement,
            """
            SELECT compensation.end_date_ctc_policy_version(
              '%s', '%s', '2028-01-01', 1, 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.ctcPolicyVersionId(),
                    Instant.parse("2026-08-05T12:03:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT compensation.end_date_eligibility_rule_version(
              '%s', '%s', '2028-01-01', 1, 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.eligibilityRuleVersionId(),
                    Instant.parse("2026-08-05T12:04:00Z")),
            0);

        assertThatThrownBy(
                () ->
                    statement.executeQuery(
                        """
                        SELECT compensation.retire_ctc_policy(
                          '%s', '%s', '2028-01-01', 1,
                          'Still in use', 'checker', '%s'
                        )
                        """
                            .formatted(
                                TENANT_A,
                                configuration.ctcPolicyId(),
                                Instant.parse("2026-08-05T12:05:00Z"))))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining(
                "CTC policy has active or future approved dependencies");
      }
      connection.rollback();
    }
  }

  @Test
  void controlledEndDatingCascadesTreatmentsAndAllowsRetirement()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        ConfigurationIds configuration =
            createApprovedConfiguration(statement, "END_DATE");

        assertFunctionResult(
            statement,
            """
            SELECT compensation.end_date_ctc_policy_version(
              '%s', '%s', '2028-01-01', 1, 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.ctcPolicyVersionId(),
                    Instant.parse("2026-08-05T12:30:00Z")),
            1);
        assertFunctionResult(
            statement,
            """
            SELECT compensation.end_date_eligibility_rule_version(
              '%s', '%s', '2028-01-01', 1, 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.eligibilityRuleVersionId(),
                    Instant.parse("2026-08-05T12:31:00Z")),
            1);

        try (ResultSet treatmentState =
            statement.executeQuery(
                """
                SELECT count(*)
                FROM compensation.ctc_policy_treatment
                WHERE tenant_id = '%s'
                  AND ctc_policy_version_id = '%s'
                  AND effective_to = '2028-01-01'
                  AND version_no = 1
                """
                    .formatted(
                        TENANT_A,
                        configuration.ctcPolicyVersionId()))) {
          assertThat(treatmentState.next()).isTrue();
          assertThat(treatmentState.getLong(1)).isEqualTo(4);
        }

        assertFunctionResult(
            statement,
            """
            SELECT compensation.retire_ctc_policy(
              '%s', '%s', '2028-01-01', 1,
              'No active dependencies', 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.ctcPolicyId(),
                    Instant.parse("2026-08-05T12:32:00Z")),
            1);
        assertFunctionResult(
            statement,
            """
            SELECT compensation.retire_eligibility_rule(
              '%s', '%s', '2028-01-01', 1,
              'No active dependencies', 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    configuration.eligibilityRuleId(),
                    Instant.parse("2026-08-05T12:33:00Z")),
            1);

        try (ResultSet identityState =
            statement.executeQuery(
                """
                SELECT policy.lifecycle_status, rule.lifecycle_status
                FROM compensation.ctc_policy policy
                JOIN compensation.eligibility_rule rule
                  ON rule.tenant_id = policy.tenant_id
                 AND rule.id = '%s'
                WHERE policy.tenant_id = '%s'
                  AND policy.id = '%s'
                """
                    .formatted(
                        configuration.eligibilityRuleId(),
                        TENANT_A,
                        configuration.ctcPolicyId()))) {
          assertThat(identityState.next()).isTrue();
          assertThat(identityState.getString(1)).isEqualTo("RETIRED");
          assertThat(identityState.getString(2)).isEqualTo("RETIRED");
        }
      }
      connection.rollback();
    }
  }

  @Test
  void incompletePolicyAndRuleCannotBeApproved() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");

        UUID policyId = UUID.randomUUID();
        UUID policyVersionId = UUID.randomUUID();
        statement.execute(
            policyIdentitySql(policyId, "INCOMPLETE_POLICY"));
        statement.execute(policyVersionSql(policyVersionId, policyId));
        statement.execute(
            treatmentSql(
                UUID.randomUUID(),
                policyId,
                policyVersionId,
                1,
                "OFFERED"));
        assertFunctionResult(
            statement,
            """
            SELECT compensation.approve_ctc_policy_version(
              '%s', '%s', 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    policyVersionId,
                    Instant.parse("2026-08-05T13:00:00Z")),
            0);

        UUID ruleId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        statement.execute(ruleIdentitySql(ruleId, "EMPTY_RULE"));
        statement.execute(ruleVersionSql(ruleVersionId, ruleId));
        assertFunctionResult(
            statement,
            """
            SELECT compensation.approve_eligibility_rule_version(
              '%s', '%s', 'checker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    ruleVersionId,
                    Instant.parse("2026-08-05T13:01:00Z")),
            0);
      }
      connection.rollback();
    }
  }

  private static ConfigurationIds createApprovedConfiguration(
      Statement statement, String suffix) throws Exception {
    UUID policyId = UUID.randomUUID();
    UUID policyVersionId = UUID.randomUUID();
    UUID ruleId = UUID.randomUUID();
    UUID ruleVersionId = UUID.randomUUID();

    statement.execute(policyIdentitySql(policyId, suffix + "_POLICY"));
    statement.execute(policyVersionSql(policyVersionId, policyId));
    statement.execute(
        treatmentSql(
            UUID.randomUUID(), policyId, policyVersionId, 1, "OFFERED"));
    statement.execute(
        treatmentSql(
            UUID.randomUUID(), policyId, policyVersionId, 2, "TARGET"));
    statement.execute(
        treatmentSql(
            UUID.randomUUID(), policyId, policyVersionId, 3, "ACCRUED"));
    statement.execute(
        treatmentSql(
            UUID.randomUUID(),
            policyId,
            policyVersionId,
            4,
            "ACTUAL_EMPLOYER_COST"));

    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_ctc_policy_version(
          '%s', '%s', 'maker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                policyVersionId,
                Instant.parse("2026-08-05T11:00:00Z")),
        0);
    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_ctc_policy_version(
          '%s', '%s', 'checker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                policyVersionId,
                Instant.parse("2026-08-05T11:01:00Z")),
        1);

    statement.execute(ruleIdentitySql(ruleId, suffix + "_RULE"));
    statement.execute(ruleVersionSql(ruleVersionId, ruleId));
    statement.execute(
        criterionSql(
            UUID.randomUUID(),
            ruleId,
            ruleVersionId,
            1,
            "EMPLOYMENT_TYPE",
            "TEXT",
            "EQ",
            "\"PERMANENT\""));

    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_eligibility_rule_version(
          '%s', '%s', 'maker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                ruleVersionId,
                Instant.parse("2026-08-05T11:02:00Z")),
        0);
    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_eligibility_rule_version(
          '%s', '%s', 'checker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                ruleVersionId,
                Instant.parse("2026-08-05T11:03:00Z")),
        1);

    return new ConfigurationIds(
        policyId, policyVersionId, ruleId, ruleVersionId);
  }

  private static String policyIdentitySql(UUID policyId, String code) {
    return """
        INSERT INTO compensation.ctc_policy(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', '%s', 'maker', 'maker'
        )
        """
        .formatted(policyId, TENANT_A, code);
  }

  private static String policyVersionSql(UUID versionId, UUID policyId) {
    return """
        INSERT INTO compensation.ctc_policy_version(
          id,
          tenant_id,
          ctc_policy_id,
          version_sequence,
          name,
          currency,
          annualisation_method,
          tolerance_amount,
          residual_component_id,
          residual_component_version_id,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'Synthetic CTC Policy',
          'INR',
          'MONTHLY_X_12',
          0.0100,
          '%s',
          '%s',
          '2027-01-01',
          '2029-01-01',
          'maker',
          'maker'
        )
        """
        .formatted(
            versionId,
            TENANT_A,
            policyId,
            COMPONENT_ID,
            COMPONENT_VERSION_ID);
  }

  private static String treatmentSql(
      UUID treatmentId,
      UUID policyId,
      UUID policyVersionId,
      int sequence,
      String costView) {
    return """
        INSERT INTO compensation.ctc_policy_treatment(
          id,
          tenant_id,
          ctc_policy_id,
          ctc_policy_version_id,
          component_id,
          component_version_id,
          treatment_sequence,
          cost_view,
          treatment_type,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          %s,
          '%s',
          'ACTUAL_VALUE',
          '2027-01-01',
          '2029-01-01',
          'maker',
          'maker'
        )
        """
        .formatted(
            treatmentId,
            TENANT_A,
            policyId,
            policyVersionId,
            COMPONENT_ID,
            COMPONENT_VERSION_ID,
            sequence,
            costView);
  }

  private static String ruleIdentitySql(UUID ruleId, String code) {
    return """
        INSERT INTO compensation.eligibility_rule(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', '%s', 'maker', 'maker'
        )
        """
        .formatted(ruleId, TENANT_A, code);
  }

  private static String ruleVersionSql(UUID versionId, UUID ruleId) {
    return """
        INSERT INTO compensation.eligibility_rule_version(
          id,
          tenant_id,
          eligibility_rule_id,
          version_sequence,
          name,
          result_when_matched,
          result_when_not_matched,
          effective_from,
          effective_to,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'Synthetic Eligibility Rule',
          'ELIGIBLE',
          'NOT_ELIGIBLE',
          '2027-01-01',
          '2029-01-01',
          'maker',
          'maker'
        )
        """
        .formatted(versionId, TENANT_A, ruleId);
  }

  private static String criterionSql(
      UUID criterionId,
      UUID ruleId,
      UUID versionId,
      int sequence,
      String factKey,
      String factType,
      String operator,
      String jsonValue) {
    return """
        INSERT INTO compensation.eligibility_rule_criterion(
          id,
          tenant_id,
          eligibility_rule_id,
          eligibility_rule_version_id,
          criterion_sequence,
          fact_key,
          fact_type,
          comparison_operator,
          value_json,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          %s,
          '%s',
          '%s',
          '%s',
          '%s'::jsonb,
          'maker',
          'maker'
        )
        """
        .formatted(
            criterionId,
            TENANT_A,
            ruleId,
            versionId,
            sequence,
            factKey,
            factType,
            operator,
            jsonValue);
  }

  private static String schemaOneStructureSql(
      UUID versionId,
      UUID structureId,
      UUID policyVersionId,
      UUID ruleVersionId) {
    return """
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
          structure_schema_version,
          structure_type,
          pay_frequency,
          confidentiality_level,
          ctc_policy_version_id,
          eligibility_rule_version_id,
          target_type,
          target_source_amount,
          target_frequency,
          target_annualization_factor,
          target_execution_mode,
          target_annual_amount,
          tolerance_amount,
          residual_component_version_id,
          configuration_hash,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          1,
          'Controlled Structure',
          'INR',
          '2027-01-01',
          '2029-01-01',
          'DRAFT',
          1,
          'STANDARD',
          'MONTHLY',
          'STANDARD',
          '%s',
          '%s',
          'ANNUAL_CTC',
          120000.0000,
          'ANNUAL',
          1.0000,
          'STRUCTURAL',
          120000.0000,
          0.0100,
          '%s',
          '%s',
          'maker',
          'maker'
        )
        """
        .formatted(
            versionId,
            TENANT_A,
            structureId,
            policyVersionId,
            ruleVersionId,
            COMPONENT_VERSION_ID,
            CONFIGURATION_HASH);
  }

  private static String schemaOneResidualLineSql(
      UUID lineId, UUID structureVersionId) {
    return """
        INSERT INTO compensation.salary_structure_line(
          id,
          tenant_id,
          salary_structure_version_id,
          component_version_id,
          sequence_no,
          target_amount,
          target_percentage,
          percentage_base_code,
          effective_from,
          effective_to,
          line_schema_version,
          line_type,
          minimum_amount,
          maximum_amount,
          mandatory_flag,
          override_policy,
          ctc_display_order,
          payslip_display_order,
          created_by,
          updated_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          1,
          NULL,
          NULL,
          NULL,
          '2027-01-01',
          '2029-01-01',
          1,
          'RESIDUAL',
          0.0000,
          120000.0000,
          TRUE,
          'PROHIBITED',
          1,
          1,
          'maker',
          'maker'
        )
        """
        .formatted(lineId, TENANT_A, structureVersionId, COMPONENT_VERSION_ID);
  }

  private static String validationSql(
      UUID validationId,
      UUID structureId,
      UUID structureVersionId,
      UUID policyVersionId,
      UUID ruleVersionId) {
    return """
        INSERT INTO compensation.salary_structure_validation(
          id,
          tenant_id,
          salary_structure_id,
          salary_structure_version_id,
          ctc_policy_version_id,
          eligibility_rule_version_id,
          effective_date,
          target_amount,
          validation_status,
          request_hash,
          configuration_hash,
          result_hash,
          blocking_error_count,
          warning_count,
          summary_json,
          created_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '2027-01-01',
          120000.0000,
          'PASS',
          '%s',
          '%s',
          '%s',
          0,
          0,
          '{"disclaimer":"DESIGN-TIME SIMULATION - NOT AN OFFICIAL PAYROLL RESULT"}',
          'validator'
        )
        """
        .formatted(
            validationId,
            TENANT_A,
            structureId,
            structureVersionId,
            policyVersionId,
            ruleVersionId,
            REQUEST_HASH,
            CONFIGURATION_HASH,
            RESULT_HASH);
  }

  private static String failedValidationSql(
      UUID validationId,
      UUID structureId,
      UUID structureVersionId,
      UUID policyVersionId,
      UUID ruleVersionId) {
    return """
        INSERT INTO compensation.salary_structure_validation(
          id,
          tenant_id,
          salary_structure_id,
          salary_structure_version_id,
          ctc_policy_version_id,
          eligibility_rule_version_id,
          effective_date,
          target_amount,
          validation_status,
          request_hash,
          configuration_hash,
          result_hash,
          blocking_error_count,
          warning_count,
          summary_json,
          created_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '%s',
          '2027-01-01',
          120000.0000,
          'FAIL',
          '%s',
          '%s',
          '%s',
          1,
          0,
          '{"disclaimer":"DESIGN-TIME SIMULATION - NOT AN OFFICIAL PAYROLL RESULT"}',
          'validator'
        )
        """
        .formatted(
            validationId,
            TENANT_A,
            structureId,
            structureVersionId,
            policyVersionId,
            ruleVersionId,
            REQUEST_HASH,
            CONFIGURATION_HASH,
            FAILED_RESULT_HASH);
  }

  private static String validationLineSql(
      UUID lineId,
      UUID validationId,
      int lineSequence,
      UUID componentId,
      UUID componentVersionId) {
    return """
        INSERT INTO compensation.salary_structure_validation_line(
          id,
          tenant_id,
          validation_id,
          line_sequence,
          component_id,
          component_version_id,
          annual_amount,
          monthly_amount,
          classification,
          evidence_json,
          created_by
        ) VALUES (
          '%s',
          '%s',
          '%s',
          %s,
          '%s',
          '%s',
          120000.0000,
          10000.0000,
          'RESIDUAL',
          '{}',
          'validator'
        )
        """
        .formatted(
            lineId,
            TENANT_A,
            validationId,
            lineSequence,
            componentId,
            componentVersionId);
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
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
  }

  private static void seedLegacyV020Data() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES (
            '%s', 'A', 'Synthetic Tenant A', 'seed', 'seed'
          )
          """
              .formatted(TENANT_A));
    }

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        seedOrganisation(statement);
        seedCompensation(statement);
        seedLegacyEmployeePayroll(statement);
      }
      connection.commit();
    }
  }

  private static void seedOrganisation(Statement statement) throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.legal_entity(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', 'ACME_IN', 'seed', 'seed'
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
          'seed-checker',
          'seed',
          'seed'
        )
        """
            .formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', 'ACME_PSU', 'seed', 'seed'
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
          'seed-checker',
          'seed',
          'seed'
        )
        """
            .formatted(PSU_VERSION_ID, TENANT_A, PSU_ID, LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO organisation.establishment(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', 'BLR', 'seed', 'seed'
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
          'seed-checker',
          'seed',
          'seed'
        )
        """
            .formatted(
                ESTABLISHMENT_VERSION_ID,
                TENANT_A,
                ESTABLISHMENT_ID,
                PSU_VERSION_ID));
  }

  private static void seedCompensation(Statement statement) throws Exception {
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
          'seed',
          'seed'
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
          'seed',
          'seed'
        )
        """
            .formatted(COMPONENT_VERSION_ID, TENANT_A, COMPONENT_ID));
    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_pay_component_version(
          '%s', '%s', 'seed-checker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                COMPONENT_VERSION_ID,
                Instant.parse("2026-08-05T00:00:00Z")),
        1);

    statement.execute(
        """
        INSERT INTO compensation.salary_structure(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', 'LEGACY_STRUCTURE', 'seed', 'seed'
        )
        """
            .formatted(LEGACY_STRUCTURE_ID, TENANT_A));
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
          'Legacy Structure',
          'INR',
          '2026-01-01',
          '2030-01-01',
          'DRAFT',
          'seed',
          'seed'
        )
        """
            .formatted(
                LEGACY_STRUCTURE_VERSION_ID,
                TENANT_A,
                LEGACY_STRUCTURE_ID));
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
          'seed',
          'seed'
        )
        """
            .formatted(
                LEGACY_LINE_ID,
                TENANT_A,
                LEGACY_STRUCTURE_VERSION_ID,
                COMPONENT_VERSION_ID));
    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_salary_structure_version(
          '%s', '%s', 'seed-checker', '%s'
        )
        """
            .formatted(
                TENANT_A,
                LEGACY_STRUCTURE_VERSION_ID,
                Instant.parse("2026-08-05T00:01:00Z")),
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
          'seed',
          'seed'
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
          'seed',
          'seed'
        )
        """
            .formatted(
                LEGACY_ASSIGNMENT_VERSION_ID,
                TENANT_A,
                LEGACY_RELATIONSHIP_VERSION_ID,
                ESTABLISHMENT_VERSION_ID));
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
          'seed',
          'seed'
        )
        """
            .formatted(
                LEGACY_SALARY_ASSIGNMENT_ID,
                TENANT_A,
                LEGACY_ASSIGNMENT_VERSION_ID,
                LEGACY_STRUCTURE_VERSION_ID));
  }

  private static void assertFunctionResult(
      Statement statement, String query, long expected) throws Exception {
    try (ResultSet result = statement.executeQuery(query)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(expected);
      assertThat(result.next()).isFalse();
    }
  }

  private static long count(Statement statement, String table) throws Exception {
    try (ResultSet result =
        statement.executeQuery("SELECT count(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
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

  private record ConfigurationIds(
      UUID ctcPolicyId,
      UUID ctcPolicyVersionId,
      UUID eligibilityRuleId,
      UUID eligibilityRuleVersionId) {}
}
