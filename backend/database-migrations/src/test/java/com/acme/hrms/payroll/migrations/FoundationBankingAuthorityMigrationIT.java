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
class FoundationBankingAuthorityMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID LEGAL_A =
      UUID.fromString("91000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_B =
      UUID.fromString("91000000-0000-0000-0000-000000000002");
  private static final UUID BANK_A =
      UUID.fromString("92000000-0000-0000-0000-000000000001");
  private static final UUID BANK_A_VERSION =
      UUID.fromString("92100000-0000-0000-0000-000000000001");
  private static final UUID BANK_B =
      UUID.fromString("92000000-0000-0000-0000-000000000002");
  private static final UUID BANK_B_VERSION =
      UUID.fromString("92100000-0000-0000-0000-000000000002");
  private static final UUID SIGNATORY_A =
      UUID.fromString("93000000-0000-0000-0000-000000000001");
  private static final UUID SIGNATORY_A_VERSION =
      UUID.fromString("93100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV034ToV035() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("34"))
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES (
            '%s', 'PRE35', 'Pre V035 tenant', 'test', 'test'
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
          ) VALUES
            ('%s', '%s', 'LEGAL_A', 'ACTIVE', 'test', 'test'),
            ('%s', '%s', 'LEGAL_B', 'ACTIVE', 'test', 'test')
          """
              .formatted(
                  LEGAL_A,
                  TENANT_A,
                  LEGAL_B,
                  TENANT_B));
    }
  }

  @Test
  void v035CreatesFiveForcedRlsTablesAndNoPlaintextAccountColumn()
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
                WHERE namespace.nspname = 'organisation'
                  AND relation.relname IN (
                    'employer_bank_account',
                    'employer_bank_account_version',
                    'authorised_signatory',
                    'authorised_signatory_version',
                    'authorised_signatory_scope'
                  )
                  AND relation.relrowsecurity
                  AND relation.relforcerowsecurity
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(5);
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema='organisation'
                  AND table_name='employer_bank_account_version'
                ORDER BY ordinal_position
                """)) {
      StringBuilder columns = new StringBuilder();
      while (result.next()) {
        columns.append(result.getString(1)).append('\n');
      }
      assertThat(columns)
          .contains("account_number_ciphertext")
          .contains("account_number_iv")
          .contains("encryption_key_version")
          .contains("account_number_fingerprint")
          .contains("account_number_last4")
          .doesNotContain("account_number_raw")
          .doesNotContain("account_number_plain")
          .doesNotContain("account_number_clear");
    }
  }

  @Test
  void bankAccountLifecycleEnforcesThreePartyApprovalAndDefaultUniqueness()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        seedBankDraft(
            statement,
            BANK_A,
            BANK_A_VERSION,
            "BANK_A",
            "a".repeat(64),
            true);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.submit_employer_bank_account_version(
                      '%s', '%s', 0, 'maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:00:00Z"))))
            .isEqualTo(1);

        Savepoint makerCannotVerify = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT organisation.verify_employer_bank_account_version(
                      '%s', '%s', 1, 'maker', 'BANK:VERIFY', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:01:00Z"))));
        connection.rollback(makerCannotVerify);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.verify_employer_bank_account_version(
                      '%s', '%s', 1, 'verifier', 'BANK:VERIFY', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:02:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.request_employer_bank_account_approval(
                      '%s', '%s', 2, 'verifier', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:03:00Z"))))
            .isEqualTo(1);

        Savepoint verifierCannotApprove = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT organisation.activate_employer_bank_account_version(
                      '%s', '%s', 3, 'verifier', 'BANK:APPROVE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:04:00Z"))));
        connection.rollback(verifierCannotApprove);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.activate_employer_bank_account_version(
                      '%s', '%s', 3, 'approver', 'BANK:APPROVE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:05:00Z"))))
            .isEqualTo(1);

        assertThat(
                scalarString(
                    statement,
                    """
                    SELECT lifecycle_status
                    FROM organisation.employer_bank_account_version
                    WHERE id='%s'
                    """
                        .formatted(BANK_A_VERSION)))
            .isEqualTo("ACTIVE");

        Savepoint directUpdateDenied = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    UPDATE organisation.employer_bank_account_version
                    SET bank_name='Bypass'
                    WHERE id='%s'
                    """
                        .formatted(BANK_A_VERSION)));
        connection.rollback(directUpdateDenied);

        seedBankDraft(
            statement,
            BANK_B,
            BANK_B_VERSION,
            "BANK_B",
            "b".repeat(64),
            true);
        advanceBankToApprovalPending(
            statement,
            BANK_B_VERSION,
            "maker2",
            "verifier2");

        Savepoint duplicateDefault = connection.setSavepoint();
        assertSqlState(
            "23P01",
            () ->
                statement.execute(
                    """
                    SELECT organisation.activate_employer_bank_account_version(
                      '%s', '%s', 3, 'approver2', 'BANK:APPROVE:2', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_B_VERSION,
                            Instant.parse("2026-08-10T01:10:00Z"))));
        connection.rollback(duplicateDefault);
      }
      connection.rollback();
    }
  }

  @Test
  void activeFingerprintRejectsConcurrentDuplicateScopeAndRlsHidesOtherTenant()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        seedBankDraft(
            statement,
            BANK_A,
            BANK_A_VERSION,
            "BANK_A",
            "c".repeat(64),
            false);
        advanceBankToApprovalPending(
            statement,
            BANK_A_VERSION,
            "maker",
            "verifier");
        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.activate_employer_bank_account_version(
                      '%s', '%s', 3, 'approver', 'BANK:APPROVE:A', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_A_VERSION,
                            Instant.parse("2026-08-10T01:20:00Z"))))
            .isEqualTo(1);

        seedBankDraft(
            statement,
            BANK_B,
            BANK_B_VERSION,
            "BANK_B",
            "c".repeat(64),
            false);
        advanceBankToApprovalPending(
            statement,
            BANK_B_VERSION,
            "maker2",
            "verifier2");

        Savepoint duplicateFingerprint = connection.setSavepoint();
        assertSqlState(
            "23P01",
            () ->
                statement.execute(
                    """
                    SELECT organisation.activate_employer_bank_account_version(
                      '%s', '%s', 3, 'approver2', 'BANK:APPROVE:B', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            BANK_B_VERSION,
                            Instant.parse("2026-08-10T01:21:00Z"))));
        connection.rollback(duplicateFingerprint);

        assertThat(count(statement, "organisation.employer_bank_account"))
            .isEqualTo(2);

        setTenant(statement, TENANT_B);
        assertThat(count(statement, "organisation.employer_bank_account"))
            .isZero();
      }
      connection.rollback();
    }
  }


@Test
void futureDatedBankVersionCannotActivateBeforeItsEffectiveDate()
    throws Exception {
  try (Connection connection = app()) {
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      setTenant(statement, TENANT_A);
      seedBankDraft(
          statement,
          BANK_A,
          BANK_A_VERSION,
          "BANK_FUTURE",
          "d".repeat(64),
          false,
          "2099-01-01");
      advanceBankToApprovalPending(
          statement,
          BANK_A_VERSION,
          "maker",
          "verifier");

      Savepoint futureActivation = connection.setSavepoint();
      assertSqlState(
          "23514",
          () ->
              statement.execute(
                  """
                  SELECT organisation.activate_employer_bank_account_version(
                    '%s', '%s', 3, 'approver', 'BANK:APPROVE:FUTURE', '%s'
                  )
                  """
                      .formatted(
                          TENANT_A,
                          BANK_A_VERSION,
                          Instant.parse("2026-08-10T01:09:00Z"))));
      connection.rollback(futureActivation);

      assertThat(
              scalarString(
                  statement,
                  """
                  SELECT lifecycle_status
                  FROM organisation.employer_bank_account_version
                  WHERE id='%s'
                  """
                      .formatted(BANK_A_VERSION)))
          .isEqualTo("APPROVAL_PENDING");
    }
    connection.rollback();
  }
}

  @Test
  void signatoryLifecycleRequiresScopeAndThreeIndependentActors()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        statement.execute(
            """
            INSERT INTO organisation.authorised_signatory(
              id, tenant_id, code, owner_kind, legal_entity_id,
              created_by, updated_by
            ) VALUES (
              '%s', '%s', 'SIGNATORY_A', 'LEGAL_ENTITY', '%s',
              'maker', 'maker'
            )
            """
                .formatted(SIGNATORY_A, TENANT_A, LEGAL_A));

        statement.execute(
            """
            INSERT INTO organisation.authorised_signatory_version(
              id, tenant_id, authorised_signatory_id, owner_key,
              version_sequence, full_name, designation, authority_reference,
              effective_from, effective_to, created_by, updated_by
            ) VALUES (
              '%s', '%s', '%s', 'LEGAL_ENTITY:%s',
              1, 'Synthetic Signatory', 'Director', 'BOARD:2026:001',
              '2026-01-01', NULL, 'maker', 'maker'
            )
            """
                .formatted(
                    SIGNATORY_A_VERSION,
                    TENANT_A,
                    SIGNATORY_A,
                    LEGAL_A));

        Savepoint noScope = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    SELECT organisation.submit_authorised_signatory_version(
                      '%s', '%s', 0, 'maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:00:00Z"))));
        connection.rollback(noScope);

        statement.execute(
            """
            INSERT INTO organisation.authorised_signatory_scope(
              tenant_id, authorised_signatory_id,
              authorised_signatory_version_id,
              purpose_code, currency_code, maximum_amount, created_by
            ) VALUES (
              '%s', '%s', '%s',
              'PAYROLL_FUNDING', 'INR', 1000000.00, 'maker'
            )
            """
                .formatted(
                    TENANT_A,
                    SIGNATORY_A,
                    SIGNATORY_A_VERSION));

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.submit_authorised_signatory_version(
                      '%s', '%s', 0, 'maker', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:01:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.verify_authorised_signatory_version(
                      '%s', '%s', 1, 'verifier', 'SIGNATORY:VERIFY', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:02:00Z"))))
            .isEqualTo(1);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.request_authorised_signatory_approval(
                      '%s', '%s', 2, 'verifier', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:03:00Z"))))
            .isEqualTo(1);

        Savepoint verifierCannotApprove = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT organisation.activate_authorised_signatory_version(
                      '%s', '%s', 3, 'verifier', 'SIGNATORY:APPROVE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:04:00Z"))));
        connection.rollback(verifierCannotApprove);

        assertThat(
                functionResult(
                    statement,
                    """
                    SELECT organisation.activate_authorised_signatory_version(
                      '%s', '%s', 3, 'approver', 'SIGNATORY:APPROVE', '%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            SIGNATORY_A_VERSION,
                            Instant.parse("2026-08-10T02:05:00Z"))))
            .isEqualTo(1);

        assertThat(
                scalarString(
                    statement,
                    """
                    SELECT lifecycle_status
                    FROM organisation.authorised_signatory_version
                    WHERE id='%s'
                    """
                        .formatted(SIGNATORY_A_VERSION)))
            .isEqualTo("ACTIVE");

        Savepoint scopeImmutable = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    UPDATE organisation.authorised_signatory_scope
                    SET maximum_amount=2000000
                    WHERE authorised_signatory_version_id='%s'
                    """
                        .formatted(SIGNATORY_A_VERSION)));
        connection.rollback(scopeImmutable);
      }
      connection.rollback();
    }
  }


private static void seedBankDraft(
    Statement statement,
    UUID identityId,
    UUID versionId,
    String code,
    String fingerprint,
    boolean isDefault)
    throws SQLException {
  seedBankDraft(
      statement,
      identityId,
      versionId,
      code,
      fingerprint,
      isDefault,
      "2026-01-01");
}

private static void seedBankDraft(
    Statement statement,
    UUID identityId,
    UUID versionId,
    String code,
    String fingerprint,
    boolean isDefault,
    String effectiveFrom)
    throws SQLException {
  String maker =
      identityId.equals(BANK_B) ? "maker2" : "maker";

  statement.execute(
      """
      INSERT INTO organisation.employer_bank_account(
        id, tenant_id, code, owner_kind, legal_entity_id,
        created_by, updated_by
      ) VALUES (
        '%s', '%s', '%s', 'LEGAL_ENTITY', '%s',
        '%s', '%s'
      )
      """
          .formatted(
              identityId,
              TENANT_A,
              code,
              LEGAL_A,
              maker,
              maker));

  statement.execute(
      """
      INSERT INTO organisation.employer_bank_account_version(
        id, tenant_id, employer_bank_account_id, owner_key,
        version_sequence, bank_name, branch_name, routing_code,
        account_holder_name, currency_code,
        account_number_ciphertext, account_number_iv,
        encryption_key_version, account_number_fingerprint,
        account_number_last4, is_default,
        effective_from, effective_to,
        created_by, updated_by
      ) VALUES (
        '%s', '%s', '%s', 'LEGAL_ENTITY:%s',
        1, 'Synthetic Bank', 'Synthetic Branch', 'SYNTH0001',
        'Synthetic Employer', 'INR',
        decode('0102030405060708090a0b0c0d0e0f1011121314', 'hex'),
        decode('0102030405060708090a0b0c', 'hex'),
        'test-v1', '%s', '7890', %s,
        '%s', NULL,
        '%s', '%s'
      )
      """
          .formatted(
              versionId,
              TENANT_A,
              identityId,
              LEGAL_A,
              fingerprint,
              isDefault,
              effectiveFrom,
              maker,
              maker));
}

  private static void advanceBankToApprovalPending(
      Statement statement,
      UUID versionId,
      String maker,
      String verifier)
      throws SQLException {
    assertThat(
            functionResult(
                statement,
                """
                SELECT organisation.submit_employer_bank_account_version(
                  '%s', '%s', 0, '%s', '%s'
                )
                """
                    .formatted(
                        TENANT_A,
                        versionId,
                        maker,
                        Instant.parse("2026-08-10T01:06:00Z"))))
        .isEqualTo(1);
    assertThat(
            functionResult(
                statement,
                """
                SELECT organisation.verify_employer_bank_account_version(
                  '%s', '%s', 1, '%s', 'BANK:VERIFY:2', '%s'
                )
                """
                    .formatted(
                        TENANT_A,
                        versionId,
                        verifier,
                        Instant.parse("2026-08-10T01:07:00Z"))))
        .isEqualTo(1);
    assertThat(
            functionResult(
                statement,
                """
                SELECT organisation.request_employer_bank_account_approval(
                  '%s', '%s', 2, '%s', '%s'
                )
                """
                    .formatted(
                        TENANT_A,
                        versionId,
                        verifier,
                        Instant.parse("2026-08-10T01:08:00Z"))))
        .isEqualTo(1);
  }

  private static long count(
      Statement statement,
      String relation)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT count(*) FROM " + relation)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long functionResult(
      Statement statement,
      String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String scalarString(
      Statement statement,
      String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static void setTenant(
      Statement statement,
      UUID tenant)
      throws SQLException {
    statement.execute(
        "SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static void assertSqlState(
      String expectedState,
      SqlWork work) {
    try {
      work.run();
      throw new AssertionError(
          "Expected SQL state " + expectedState);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState())
          .isEqualTo(expectedState);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        "postgres",
        "postgres");
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
      statement.execute(
          "GRANT payroll_owner TO payroll_migrator");
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
