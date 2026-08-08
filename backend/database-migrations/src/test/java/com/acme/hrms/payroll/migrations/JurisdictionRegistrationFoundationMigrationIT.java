package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class JurisdictionRegistrationFoundationMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final UUID LEGAL_ID =
      UUID.fromString("81000000-0000-0000-0000-000000000001");
  private static final UUID COUNTRY_ID =
      UUID.fromString("82000000-0000-0000-0000-000000000001");
  private static final UUID COUNTRY_VERSION_ID =
      UUID.fromString("82100000-0000-0000-0000-000000000001");
  private static final UUID WORK_LOCATION_ID =
      UUID.fromString("83000000-0000-0000-0000-000000000001");
  private static final UUID WORK_LOCATION_VERSION_ID =
      UUID.fromString("83100000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_TYPE_ID =
      UUID.fromString("84000000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_TYPE_VERSION_ID =
      UUID.fromString("84100000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_ID =
      UUID.fromString("85000000-0000-0000-0000-000000000001");
  private static final UUID REGISTRATION_VERSION_ID =
      UUID.fromString("85100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV033ToV034() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("33"))
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES (
            '%s', 'PRE34', 'Pre V034 tenant', 'test', 'test'
          )
          """
              .formatted(TENANT_A));
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
  }

  @BeforeEach
  void reset() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES
            ('%s', 'A', 'Synthetic Tenant A', 'test', 'test'),
            ('%s', 'B', 'Synthetic Tenant B', 'test', 'test')
          """
              .formatted(TENANT_A, TENANT_B));

      statement.execute(
          """
          INSERT INTO organisation.legal_entity(
            id, tenant_id, code, status, created_by, updated_by
          ) VALUES (
            '%s', '%s', 'LEGAL_A', 'ACTIVE', 'test', 'test'
          )
          """
              .formatted(LEGAL_ID, TENANT_A));
    }
  }

  @Test
  void registrationIdentifierPatternDialectIsExplicitAndDatabaseBounded()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema='statutory'
                  AND table_name='registration_type_version'
                  AND column_name='identifier_pattern_dialect'
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).contains("JAVA_REGEX_V1");
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid='statutory.registration_type_version'::regclass
                  AND contype='c'
                """)) {
      boolean dialectConstraint = false;
      while (result.next()) {
        if (result.getString(1).contains("identifier_pattern_dialect")
            && result.getString(1).contains("JAVA_REGEX_V1")) {
          dialectConstraint = true;
        }
      }
      assertThat(dialectConstraint).isTrue();
    }
  }

  @Test
  void v034CreatesElevenForcedRlsFoundationTables() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT count(*)
                FROM pg_class relation
                JOIN pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE (
                  (
                    namespace.nspname = 'organisation'
                    AND relation.relname IN (
                      'payroll_jurisdiction',
                      'payroll_jurisdiction_version',
                      'work_location',
                      'work_location_version',
                      'jurisdiction_resolution_override',
                      'jurisdiction_resolution_evidence'
                    )
                  ) OR (
                    namespace.nspname = 'statutory'
                    AND relation.relname IN (
                      'registration_type',
                      'registration_type_version',
                      'registration_type_owner_kind',
                      'registration',
                      'registration_version'
                    )
                  )
                )
                AND relation.relrowsecurity
                AND relation.relforcerowsecurity
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(11);
    }
  }

  @Test
  void makerCheckerApprovesJurisdictionAndWorkLocation() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'IN', 'maker', 'maker'
            )
            """
                .formatted(COUNTRY_ID, TENANT_A));

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction_version(
              id, tenant_id, payroll_jurisdiction_id, version_sequence,
              name, country_code, level_code, level_rank,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'India', 'IN', 'COUNTRY', 1,
              '2026-01-01', NULL, 'maker', 'maker'
            )
            """
                .formatted(COUNTRY_VERSION_ID, TENANT_A, COUNTRY_ID));

        Savepoint makerCheck = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT organisation.approve_payroll_jurisdiction_version(
                      '%s', '%s', 0, 'maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            COUNTRY_VERSION_ID,
                            Instant.parse("2026-08-07T12:00:00Z"))));
        connection.rollback(makerCheck);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.approve_payroll_jurisdiction_version(
                      '%s', '%s', 0, 'checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            COUNTRY_VERSION_ID,
                            Instant.parse("2026-08-07T12:01:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO organisation.work_location(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'BLR_REMOTE', 'maker', 'maker'
            )
            """
                .formatted(WORK_LOCATION_ID, TENANT_A));

        statement.execute(
            """
            INSERT INTO organisation.work_location_version(
              id, tenant_id, work_location_id, version_sequence,
              name, payroll_jurisdiction_id, payroll_jurisdiction_version_id,
              locality, state_code, country_code,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Bengaluru Remote Work Location', '%s', '%s',
              'Bengaluru', 'KA', 'IN',
              '2026-01-01', NULL, 'maker', 'maker'
            )
            """
                .formatted(
                    WORK_LOCATION_VERSION_ID,
                    TENANT_A,
                    WORK_LOCATION_ID,
                    COUNTRY_ID,
                    COUNTRY_VERSION_ID));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.approve_work_location_version(
                      '%s', '%s', 0, 'checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            WORK_LOCATION_VERSION_ID,
                            Instant.parse("2026-08-07T12:02:00Z"))))
            .isEqualTo(1);

        assertThat(
                scalarString(
                    statement,
                    """
                    SELECT approval_status
                    FROM organisation.work_location_version
                    WHERE id = '%s'
                    """
                        .formatted(WORK_LOCATION_VERSION_ID)))
            .isEqualTo("APPROVED");
      }

      connection.rollback();
    }
  }

  @Test
  void registrationLifecycleEnforcesThreePartyApprovalAndDuplicateProtection()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        seedApprovedCountry(statement);

        statement.execute(
            """
            INSERT INTO statutory.registration_type(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'GENERIC_REG', 'type-maker', 'type-maker'
            )
            """
                .formatted(REGISTRATION_TYPE_ID, TENANT_A));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_version(
              id, tenant_id, registration_type_id, version_sequence,
              name, obligation_code, authority_code, jurisdiction_level_code,
              identifier_case_policy, parent_required,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Generic registration', 'GENERIC', 'GENERIC_AUTHORITY', 'COUNTRY',
              'UPPER', false,
              '2026-01-01', NULL, 'type-maker', 'type-maker'
            )
            """
                .formatted(
                    REGISTRATION_TYPE_VERSION_ID,
                    TENANT_A,
                    REGISTRATION_TYPE_ID));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_owner_kind(
              tenant_id,
              registration_type_id,
              registration_type_version_id,
              owner_kind,
              created_by
            ) VALUES (
              '%s', '%s', '%s', 'LEGAL_ENTITY', 'type-maker'
            )
            """
                .formatted(
                    TENANT_A,
                    REGISTRATION_TYPE_ID,
                    REGISTRATION_TYPE_VERSION_ID));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.approve_registration_type_version(
                      '%s', '%s', 0, 'type-checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_TYPE_VERSION_ID,
                            Instant.parse("2026-08-07T13:00:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO statutory.registration(
              id, tenant_id, registration_type_id,
              reference_code, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              'REG_A', 'reg-maker', 'reg-maker'
            )
            """
                .formatted(REGISTRATION_ID, TENANT_A, REGISTRATION_TYPE_ID));

        statement.execute(
            registrationVersionInsert(
                REGISTRATION_VERSION_ID,
                REGISTRATION_ID,
                "REG-001"));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.submit_registration_version(
                      '%s', '%s', 0, 'reg-maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T13:01:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.verify_registration_version(
                      '%s', '%s', 1, 'verifier', 'VERIFY:EVIDENCE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T13:02:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.request_registration_approval(
                      '%s', '%s', 2, 'verifier', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T13:03:00Z"))))
            .isEqualTo(1);

        Savepoint verifierCannotApprove = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT statutory.activate_registration_version(
                      '%s', '%s', 3, 'verifier', 'APPROVAL:EVIDENCE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T13:04:00Z"))));
        connection.rollback(verifierCannotApprove);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.activate_registration_version(
                      '%s', '%s', 3, 'approver', 'APPROVAL:EVIDENCE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T13:05:00Z"))))
            .isEqualTo(1);

        UUID duplicateRegistrationId =
            UUID.fromString("85000000-0000-0000-0000-000000000002");
        UUID duplicateVersionId =
            UUID.fromString("85100000-0000-0000-0000-000000000002");

        statement.execute(
            """
            INSERT INTO statutory.registration(
              id, tenant_id, registration_type_id,
              reference_code, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              'REG_B', 'second-maker', 'second-maker'
            )
            """
                .formatted(
                    duplicateRegistrationId,
                    TENANT_A,
                    REGISTRATION_TYPE_ID));

        statement.execute(
            registrationVersionInsert(
                duplicateVersionId,
                duplicateRegistrationId,
                "REG-001"));

        functionResult(
            statement,
            """
            SELECT statutory.submit_registration_version(
              '%s', '%s', 0, 'second-maker', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    duplicateVersionId,
                    Instant.parse("2026-08-07T13:06:00Z")));
        functionResult(
            statement,
            """
            SELECT statutory.verify_registration_version(
              '%s', '%s', 1, 'second-verifier', 'VERIFY:SECOND', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    duplicateVersionId,
                    Instant.parse("2026-08-07T13:07:00Z")));
        functionResult(
            statement,
            """
            SELECT statutory.request_registration_approval(
              '%s', '%s', 2, 'second-verifier', '%s'
            )
            """
                .formatted(
                    TENANT_A,
                    duplicateVersionId,
                    Instant.parse("2026-08-07T13:08:00Z")));

        Savepoint duplicateProtection = connection.setSavepoint();
        assertSqlState(
            "23P01",
            () ->
                statement.execute(
                    """
                    SELECT statutory.activate_registration_version(
                      '%s', '%s', 3, 'second-approver', 'APPROVAL:SECOND', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            duplicateVersionId,
                            Instant.parse("2026-08-07T13:09:00Z"))));
        connection.rollback(duplicateProtection);
      }

      connection.rollback();
    }
  }

  @Test
  void parentJurisdictionMustBeSameOrAncestorAndSuspensionIsMakerChecked()
      throws Exception {
    UUID stateId =
        UUID.fromString("82000000-0000-0000-0000-000000000002");
    UUID stateVersionId =
        UUID.fromString("82100000-0000-0000-0000-000000000002");
    UUID unrelatedCountryId =
        UUID.fromString("82000000-0000-0000-0000-000000000003");
    UUID unrelatedCountryVersionId =
        UUID.fromString("82100000-0000-0000-0000-000000000003");
    UUID childTypeId =
        UUID.fromString("84000000-0000-0000-0000-000000000002");
    UUID childTypeVersionId =
        UUID.fromString("84100000-0000-0000-0000-000000000002");
    UUID childRegistrationId =
        UUID.fromString("85000000-0000-0000-0000-000000000003");
    UUID childRegistrationVersionId =
        UUID.fromString("85100000-0000-0000-0000-000000000003");
    UUID invalidRegistrationId =
        UUID.fromString("85000000-0000-0000-0000-000000000004");
    UUID invalidRegistrationVersionId =
        UUID.fromString("85100000-0000-0000-0000-000000000004");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        seedApprovedCountry(statement);

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'KA', 'state-maker', 'state-maker'
            )
            """
                .formatted(stateId, TENANT_A));

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction_version(
              id, tenant_id, payroll_jurisdiction_id, version_sequence,
              name, country_code, level_code, level_rank,
              parent_jurisdiction_id, parent_jurisdiction_version_id,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Karnataka', 'IN', 'STATE', 2,
              '%s', '%s',
              '2026-01-01', NULL, 'state-maker', 'state-maker'
            )
            """
                .formatted(
                    stateVersionId,
                    TENANT_A,
                    stateId,
                    COUNTRY_ID,
                    COUNTRY_VERSION_ID));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.approve_payroll_jurisdiction_version(
                      '%s', '%s', 0, 'state-checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            stateVersionId,
                            Instant.parse("2026-08-07T14:00:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'OTHER_COUNTRY',
              'other-maker', 'other-maker'
            )
            """
                .formatted(unrelatedCountryId, TENANT_A));

        statement.execute(
            """
            INSERT INTO organisation.payroll_jurisdiction_version(
              id, tenant_id, payroll_jurisdiction_id, version_sequence,
              name, country_code, level_code, level_rank,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Other Country', 'US', 'COUNTRY', 1,
              '2026-01-01', NULL, 'other-maker', 'other-maker'
            )
            """
                .formatted(
                    unrelatedCountryVersionId,
                    TENANT_A,
                    unrelatedCountryId));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.approve_payroll_jurisdiction_version(
                      '%s', '%s', 0, 'other-checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            unrelatedCountryVersionId,
                            Instant.parse("2026-08-07T14:01:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO statutory.registration_type(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'PARENT_REG',
              'type-maker', 'type-maker'
            )
            """
                .formatted(REGISTRATION_TYPE_ID, TENANT_A));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_version(
              id, tenant_id, registration_type_id, version_sequence,
              name, obligation_code, authority_code,
              jurisdiction_level_code, identifier_case_policy,
              parent_required, effective_from,
              created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Parent registration', 'PARENT', 'GENERIC_AUTHORITY',
              'COUNTRY', 'UPPER',
              false, '2026-01-01',
              'type-maker', 'type-maker'
            )
            """
                .formatted(
                    REGISTRATION_TYPE_VERSION_ID,
                    TENANT_A,
                    REGISTRATION_TYPE_ID));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_owner_kind(
              tenant_id, registration_type_id,
              registration_type_version_id, owner_kind, created_by
            ) VALUES (
              '%s', '%s', '%s', 'LEGAL_ENTITY', 'type-maker'
            )
            """
                .formatted(
                    TENANT_A,
                    REGISTRATION_TYPE_ID,
                    REGISTRATION_TYPE_VERSION_ID));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.approve_registration_type_version(
                      '%s', '%s', 0, 'type-checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_TYPE_VERSION_ID,
                            Instant.parse("2026-08-07T14:02:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO statutory.registration_type(
              id, tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', '%s', 'CHILD_REG',
              'child-type-maker', 'child-type-maker'
            )
            """
                .formatted(childTypeId, TENANT_A));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_version(
              id, tenant_id, registration_type_id, version_sequence,
              name, obligation_code, authority_code,
              jurisdiction_level_code, identifier_case_policy,
              parent_required, parent_registration_type_id,
              effective_from, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 1,
              'Child registration', 'CHILD', 'GENERIC_AUTHORITY',
              'STATE', 'UPPER',
              true, '%s',
              '2026-01-01', 'child-type-maker', 'child-type-maker'
            )
            """
                .formatted(
                    childTypeVersionId,
                    TENANT_A,
                    childTypeId,
                    REGISTRATION_TYPE_ID));

        statement.execute(
            """
            INSERT INTO statutory.registration_type_owner_kind(
              tenant_id, registration_type_id,
              registration_type_version_id, owner_kind, created_by
            ) VALUES (
              '%s', '%s', '%s', 'LEGAL_ENTITY', 'child-type-maker'
            )
            """
                .formatted(
                    TENANT_A,
                    childTypeId,
                    childTypeVersionId));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.approve_registration_type_version(
                      '%s', '%s', 0, 'child-type-checker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            childTypeVersionId,
                            Instant.parse("2026-08-07T14:03:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO statutory.registration(
              id, tenant_id, registration_type_id,
              reference_code, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              'PARENT_INSTANCE', 'reg-maker', 'reg-maker'
            )
            """
                .formatted(
                    REGISTRATION_ID,
                    TENANT_A,
                    REGISTRATION_TYPE_ID));

        statement.execute(
            registrationVersionInsert(
                REGISTRATION_VERSION_ID,
                REGISTRATION_ID,
                "PARENT-001"));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.submit_registration_version(
                      '%s', '%s', 0, 'reg-maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:04:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.verify_registration_version(
                      '%s', '%s', 1, 'verifier', 'VERIFY:PARENT', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:05:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.request_registration_approval(
                      '%s', '%s', 2, 'verifier', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:06:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.activate_registration_version(
                      '%s', '%s', 3, 'approver', 'APPROVAL:PARENT', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:07:00Z"))))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO statutory.registration(
              id, tenant_id, registration_type_id,
              reference_code, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              'CHILD_INSTANCE', 'child-maker', 'child-maker'
            )
            """
                .formatted(
                    childRegistrationId,
                    TENANT_A,
                    childTypeId));

        statement.execute(
            """
            INSERT INTO statutory.registration_version(
              id, tenant_id, registration_id,
              registration_type_id, registration_type_version_id,
              version_sequence,
              identifier_raw, identifier_normalized,
              owner_kind, legal_entity_id,
              payroll_jurisdiction_id, payroll_jurisdiction_version_id,
              parent_registration_id, parent_registration_version_id,
              effective_from, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              '%s', '%s',
              1,
              'CHILD-001', 'CHILD-001',
              'LEGAL_ENTITY', '%s',
              '%s', '%s',
              '%s', '%s',
              '2026-01-01', 'child-maker', 'child-maker'
            )
            """
                .formatted(
                    childRegistrationVersionId,
                    TENANT_A,
                    childRegistrationId,
                    childTypeId,
                    childTypeVersionId,
                    LEGAL_ID,
                    stateId,
                    stateVersionId,
                    REGISTRATION_ID,
                    REGISTRATION_VERSION_ID));

        assertThat(
                count(
                    statement,
                    "statutory.registration_version"))
            .isEqualTo(2);

        statement.execute(
            """
            INSERT INTO statutory.registration(
              id, tenant_id, registration_type_id,
              reference_code, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s',
              'INVALID_CHILD', 'invalid-maker', 'invalid-maker'
            )
            """
                .formatted(
                    invalidRegistrationId,
                    TENANT_A,
                    childTypeId));

        Savepoint unrelatedJurisdiction = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    INSERT INTO statutory.registration_version(
                      id, tenant_id, registration_id,
                      registration_type_id, registration_type_version_id,
                      version_sequence,
                      identifier_raw, identifier_normalized,
                      owner_kind, legal_entity_id,
                      payroll_jurisdiction_id,
                      payroll_jurisdiction_version_id,
                      parent_registration_id,
                      parent_registration_version_id,
                      effective_from, created_by, updated_by
                    ) VALUES (
                      '%s', '%s', '%s',
                      '%s', '%s',
                      1,
                      'INVALID-001', 'INVALID-001',
                      'LEGAL_ENTITY', '%s',
                      '%s', '%s',
                      '%s', '%s',
                      '2026-01-01', 'invalid-maker', 'invalid-maker'
                    )
                    """
                        .formatted(
                            invalidRegistrationVersionId,
                            TENANT_A,
                            invalidRegistrationId,
                            childTypeId,
                            childTypeVersionId,
                            LEGAL_ID,
                            unrelatedCountryId,
                            unrelatedCountryVersionId,
                            REGISTRATION_ID,
                            REGISTRATION_VERSION_ID)));
        connection.rollback(unrelatedJurisdiction);

        Savepoint makerCannotSuspend = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT statutory.suspend_registration_version(
                      '%s', '%s', 4, 'reg-maker',
                      'manual suspension', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:08:00Z"))));
        connection.rollback(makerCannotSuspend);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT statutory.suspend_registration_version(
                      '%s', '%s', 4, 'independent-suspender',
                      'manual suspension', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            REGISTRATION_VERSION_ID,
                            Instant.parse("2026-08-07T14:09:00Z"))))
            .isEqualTo(1);
      }

      connection.rollback();
    }
  }

  @Test
  void registrationOwnerIsExclusiveAndCrossTenantRowsAreHidden()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);

      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        seedApprovedCountry(statement);

        statement.execute(
            """
            INSERT INTO organisation.work_location(
              tenant_id, code, created_by, updated_by
            ) VALUES (
              '%s', 'TENANT_A_ONLY', 'maker', 'maker'
            )
            """
                .formatted(TENANT_A));
      }
      connection.commit();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_B);
        assertThat(
                count(
                    statement,
                    "organisation.payroll_jurisdiction"))
            .isZero();
        assertThat(
                count(
                    statement,
                    "organisation.work_location"))
            .isZero();
        assertThat(
                count(
                    statement,
                    "statutory.registration"))
            .isZero();

        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    INSERT INTO organisation.work_location(
                      tenant_id, code, created_by, updated_by
                    ) VALUES (
                      '%s', 'CROSS_TENANT_WRITE', 'test', 'test'
                    )
                    """
                        .formatted(TENANT_A)));
      }
      connection.rollback();
    }
  }

  private static void seedApprovedCountry(Statement statement)
      throws SQLException {
    statement.execute(
        """
        INSERT INTO organisation.payroll_jurisdiction(
          id, tenant_id, code, created_by, updated_by
        ) VALUES (
          '%s', '%s', 'IN', 'maker', 'maker'
        )
        """
            .formatted(COUNTRY_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.payroll_jurisdiction_version(
          id, tenant_id, payroll_jurisdiction_id, version_sequence,
          name, country_code, level_code, level_rank,
          effective_from, effective_to, created_by, updated_by
        ) VALUES (
          '%s', '%s', '%s', 1,
          'India', 'IN', 'COUNTRY', 1,
          '2026-01-01', NULL, 'maker', 'maker'
        )
        """
            .formatted(COUNTRY_VERSION_ID, TENANT_A, COUNTRY_ID));

    assertThat(
            functionResult(
                statement,
                """
                SELECT organisation.approve_payroll_jurisdiction_version(
                  '%s', '%s', 0, 'checker', '%s'
                )
                """
                    .formatted(
                        TENANT_A,
                        COUNTRY_VERSION_ID,
                        Instant.parse("2026-08-07T11:00:00Z"))))
        .isEqualTo(1);
  }

  private static String registrationVersionInsert(
      UUID versionId,
      UUID registrationId,
      String identifier) {
    return """
        INSERT INTO statutory.registration_version(
          id,
          tenant_id,
          registration_id,
          registration_type_id,
          registration_type_version_id,
          version_sequence,
          identifier_raw,
          identifier_normalized,
          owner_kind,
          legal_entity_id,
          payroll_jurisdiction_id,
          payroll_jurisdiction_version_id,
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
          1,
          '%s',
          '%s',
          'LEGAL_ENTITY',
          '%s',
          '%s',
          '%s',
          '2026-01-01',
          NULL,
          '%s',
          '%s'
        )
        """
        .formatted(
            versionId,
            TENANT_A,
            registrationId,
            REGISTRATION_TYPE_ID,
            REGISTRATION_TYPE_VERSION_ID,
            identifier,
            identifier,
            LEGAL_ID,
            COUNTRY_ID,
            COUNTRY_VERSION_ID,
            registrationId.equals(REGISTRATION_ID)
                ? "reg-maker"
                : "second-maker",
            registrationId.equals(REGISTRATION_ID)
                ? "reg-maker"
                : "second-maker");
  }

  private static long functionResult(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String scalarString(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static long count(Statement statement, String table)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery("SELECT count(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static void setTenant(Statement statement, UUID tenant)
      throws SQLException {
    statement.execute("SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static void assertSqlState(
      String expected,
      SqlAction action) throws Exception {
    try {
      action.run();
      throw new AssertionError(
          "Expected SQLSTATE " + expected + " but statement succeeded");
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expected);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "payroll_app",
        APP_PASSWORD);
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
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }
}
