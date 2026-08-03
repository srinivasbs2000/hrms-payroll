package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
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
class OrganisationHierarchyClosureMigrationIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString(
          "00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString(
          "00000000-0000-0000-0000-00000000000b");
  private static final UUID EXISTING_LEGAL_ID =
      UUID.fromString(
          "81000000-0000-0000-0000-000000000001");
  private static final UUID EXISTING_LEGAL_VERSION_ID =
      UUID.fromString(
          "81100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV030ToV031() throws Exception {
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

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("30"))
        .load()
        .migrate();

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET ROLE payroll_owner");
        statement.execute(
            "INSERT INTO platform.tenant("
                + "id,code,name,created_by,updated_by) VALUES "
                + "('"
                + TENANT_A
                + "','A','Synthetic Tenant A','test','test'),"
                + "('"
                + TENANT_B
                + "','B','Synthetic Tenant B','test','test')");
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,status,created_by,updated_by
            ) VALUES (
              '%s','%s','EXISTING_LE','ACTIVE','v030','v030'
            )
            """
                .formatted(EXISTING_LEGAL_ID, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.legal_entity_version(
              id,tenant_id,legal_entity_id,version_sequence,
              name,country_code,currency,effective_from,effective_to,
              approval_status,approved_at,approved_by,
              created_by,updated_by
            ) VALUES (
              '%s','%s','%s',1,
              'Existing Legal Entity','IN','INR','2020-01-01',NULL,
              'APPROVED','2020-01-01T00:00:00Z','v030-approver',
              'v030-creator','v030-approver'
            )
            """
                .formatted(
                    EXISTING_LEGAL_VERSION_ID,
                    TENANT_A,
                    EXISTING_LEGAL_ID));
      }
      connection.commit();
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

  @Test
  void upgradePreservesIdentityAndVersionIdsAndBackfillsActive()
      throws Exception {
    try (Connection connection = admin();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT i.id,
                       i.status,
                       i.version_no,
                       v.id
                  FROM organisation.legal_entity i
                  JOIN organisation.legal_entity_version v
                    ON v.tenant_id=i.tenant_id
                   AND v.legal_entity_id=i.id
                 WHERE i.tenant_id=?
                   AND i.id=?
                """)) {
      statement.setObject(1, TENANT_A);
      statement.setObject(2, EXISTING_LEGAL_ID);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getObject(1, UUID.class))
            .isEqualTo(EXISTING_LEGAL_ID);
        assertThat(result.getString(2)).isEqualTo("ACTIVE");
        assertThat(result.getLong(3)).isZero();
        assertThat(result.getObject(4, UUID.class))
            .isEqualTo(EXISTING_LEGAL_VERSION_ID);
      }
    }

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertThat(
              scalarLong(
                  statement,
                  """
                  SELECT count(*)
                    FROM pg_class relation
                    JOIN pg_namespace namespace
                      ON namespace.oid=relation.relnamespace
                   WHERE namespace.nspname='organisation'
                     AND relation.relname IN (
                       'legal_entity',
                       'payroll_statutory_unit',
                       'establishment'
                     )
                     AND relation.relrowsecurity
                     AND relation.relforcerowsecurity
                  """))
          .isEqualTo(3L);
    }
  }

  @Test
  void lifecycleEnforcesMakerCheckerActivationAndRetirement()
      throws Exception {
    UUID identityId =
        UUID.fromString(
            "82000000-0000-0000-0000-000000000001");
    UUID versionId =
        UUID.fromString(
            "82100000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,created_by,updated_by
            ) VALUES (
              '%s','%s','LIFECYCLE_LE','issuer|creator','issuer|creator'
            )
            """
                .formatted(identityId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.legal_entity_version(
              id,tenant_id,legal_entity_id,version_sequence,
              name,country_code,currency,effective_from,
              approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s',1,
              'Lifecycle Legal Entity','IN','INR','2027-01-01',
              'DRAFT','issuer|creator','issuer|creator'
            )
            """
                .formatted(versionId, TENANT_A, identityId));

        assertThat(
                scalarText(
                    statement,
                    """
                    SELECT status
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(identityId)))
            .isEqualTo("PENDING_APPROVAL");

        Savepoint selfApproval = connection.setSavepoint();
        assertSqlState(
            () ->
                statement.executeQuery(
                    """
                    SELECT organisation.approve_version(
                      'LEGAL_ENTITY','%s','%s','issuer|creator','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            versionId,
                            Instant.parse(
                                "2026-08-02T12:00:00Z"))),
            "P5A01");
        connection.rollback(selfApproval);

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.approve_version(
                      'LEGAL_ENTITY','%s','%s','issuer|approver','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            versionId,
                            Instant.parse(
                                "2026-08-02T12:01:00Z"))))
            .isEqualTo(1L);

        assertThat(
                scalarText(
                    statement,
                    """
                    SELECT status
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(identityId)))
            .isEqualTo("ACTIVE");

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT version_no
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(identityId)))
            .isEqualTo(1L);

        assertThat(
                scalarUuid(
                    statement,
                    """
                    SELECT organisation.retire_identity(
                      'LEGAL_ENTITY','%s','%s','2028-01-01',1,
                      'Entity is no longer an employer',
                      'issuer|retirer','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            identityId,
                            Instant.parse(
                                "2026-08-02T12:02:00Z"))))
            .isEqualTo(versionId);

        assertThat(
                scalarText(
                    statement,
                    """
                    SELECT status
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(identityId)))
            .isEqualTo("RETIRED");

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT version_no
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(identityId)))
            .isEqualTo(2L);

        Savepoint retiredVersion = connection.setSavepoint();
        assertSqlState(
            () ->
                statement.execute(
                    """
                    INSERT INTO organisation.legal_entity_version(
                      id,tenant_id,legal_entity_id,version_sequence,
                      name,country_code,currency,effective_from,
                      approval_status,created_by,updated_by
                    ) VALUES (
                      gen_random_uuid(),'%s','%s',2,
                      'Forbidden Version','IN','INR','2029-01-01',
                      'DRAFT','issuer|creator','issuer|creator'
                    )
                    """
                        .formatted(TENANT_A, identityId)),
            "P5A02");
        connection.rollback(retiredVersion);
      }
      connection.rollback();
    }
  }

  @Test
  void retirementIsBlockedByExtendingDependantsThenSucceedsAfterEndDating()
      throws Exception {
    UUID legalId =
        UUID.fromString(
            "82500000-0000-0000-0000-000000000001");
    UUID legalVersionId =
        UUID.fromString(
            "82510000-0000-0000-0000-000000000001");
    UUID psuId =
        UUID.fromString(
            "82520000-0000-0000-0000-000000000001");
    UUID psuVersionId =
        UUID.fromString(
            "82530000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','BLOCKED_LE','maker','maker')
            """
                .formatted(legalId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.legal_entity_version(
              id,tenant_id,legal_entity_id,version_sequence,
              name,country_code,currency,effective_from,
              approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s',1,'Blocked Legal','IN','INR',
              '2027-01-01','DRAFT','maker','maker'
            )
            """
                .formatted(legalVersionId, TENANT_A, legalId));
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.approve_version(
                      'LEGAL_ENTITY','%s','%s','checker','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            legalVersionId,
                            Instant.parse(
                                "2026-08-02T13:00:00Z"))))
            .isEqualTo(1L);

        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','BLOCKED_PSU','maker','maker')
            """
                .formatted(psuId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit_version(
              id,tenant_id,payroll_statutory_unit_id,
              legal_entity_version_id,version_sequence,name,
              effective_from,approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,'Blocked PSU',
              '2027-01-01','DRAFT','maker','maker'
            )
            """
                .formatted(
                    psuVersionId,
                    TENANT_A,
                    psuId,
                    legalVersionId));
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.approve_version(
                      'PAYROLL_STATUTORY_UNIT','%s','%s',
                      'checker','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            psuVersionId,
                            Instant.parse(
                                "2026-08-02T13:01:00Z"))))
            .isEqualTo(1L);

        Savepoint blocked = connection.setSavepoint();
        assertSqlState(
            () ->
                statement.executeQuery(
                    """
                    SELECT organisation.retire_identity(
                      'LEGAL_ENTITY','%s','%s','2028-01-01',1,
                      'Blocked retirement','retirer','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            legalId,
                            Instant.parse(
                                "2026-08-02T13:02:00Z"))),
            "P5A03");
        connection.rollback(blocked);

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.end_date_version(
                      'PAYROLL_STATUTORY_UNIT','%s','%s',
                      '2028-01-01',1,'operator','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            psuVersionId,
                            Instant.parse(
                                "2026-08-02T13:03:00Z"))))
            .isEqualTo(1L);

        assertThat(
                scalarUuid(
                    statement,
                    """
                    SELECT organisation.retire_identity(
                      'LEGAL_ENTITY','%s','%s','2028-01-01',1,
                      'Dependencies closed','retirer','%s'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            legalId,
                            Instant.parse(
                                "2026-08-02T13:04:00Z"))))
            .isEqualTo(legalVersionId);
      }
      connection.rollback();
    }
  }

  @Test
  void classificationsHaveControlledDefaultsAndConstraints()
      throws Exception {
    UUID psuId =
        UUID.fromString(
            "83000000-0000-0000-0000-000000000001");
    UUID psuVersionId =
        UUID.fromString(
            "83100000-0000-0000-0000-000000000001");
    UUID establishmentId =
        UUID.fromString(
            "84000000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','DEFAULT_PSU','creator','creator')
            """
                .formatted(psuId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit_version(
              id,tenant_id,payroll_statutory_unit_id,
              legal_entity_version_id,version_sequence,name,
              effective_from,approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,'Default PSU',
              '2027-01-01','DRAFT','creator','creator'
            )
            """
                .formatted(
                    psuVersionId,
                    TENANT_A,
                    psuId,
                    EXISTING_LEGAL_VERSION_ID));

        assertThat(
                scalarText(
                    statement,
                    """
                    SELECT responsibility_scope
                      FROM organisation.payroll_statutory_unit_version
                     WHERE id='%s'
                    """
                        .formatted(psuVersionId)))
            .isEqualTo("TAX_AND_STATUTORY");

        statement.execute(
            """
            INSERT INTO organisation.establishment(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','DEFAULT_EST','creator','creator')
            """
                .formatted(establishmentId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.establishment_version(
              id,tenant_id,establishment_id,
              payroll_statutory_unit_version_id,version_sequence,
              name,state_code,effective_from,approval_status,
              created_by,updated_by
            ) VALUES (
              gen_random_uuid(),'%s','%s','%s',1,
              'Default Establishment','KA','2027-01-01','DRAFT',
              'creator','creator'
            )
            """
                .formatted(
                    TENANT_A,
                    establishmentId,
                    psuVersionId));

        assertThat(
                scalarText(
                    statement,
                    """
                    SELECT establishment_type
                      FROM organisation.establishment_version
                     WHERE establishment_id='%s'
                    """
                        .formatted(establishmentId)))
            .isEqualTo("OTHER");

        Savepoint invalidCode = connection.setSavepoint();
        assertSqlState(
            () ->
                statement.execute(
                    """
                    INSERT INTO organisation.legal_entity(
                      id,tenant_id,code,created_by,updated_by
                    ) VALUES (
                      gen_random_uuid(),'%s','bad-code','creator','creator'
                    )
                    """
                        .formatted(TENANT_A)),
            "23514");
        connection.rollback(invalidCode);
      }
      connection.rollback();
    }
  }

  @Test
  void uniquenessIsTenantSafeAndRlsDoesNotLeak()
      throws Exception {
    UUID tenantAIdentity =
        UUID.fromString(
            "85000000-0000-0000-0000-000000000001");
    UUID tenantBIdentity =
        UUID.fromString(
            "85000000-0000-0000-0000-000000000002");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','TENANT_SHARED','creator','creator')
            """
                .formatted(tenantAIdentity, TENANT_A));
        Savepoint duplicate = connection.setSavepoint();
        assertSqlState(
            () ->
                statement.execute(
                    """
                    INSERT INTO organisation.legal_entity(
                      id,tenant_id,code,created_by,updated_by
                    ) VALUES (
                      gen_random_uuid(),'%s','TENANT_SHARED',
                      'creator','creator'
                    )
                    """
                        .formatted(TENANT_A)),
            "23505");
        connection.rollback(duplicate);
      }
      connection.commit();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_B);
        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','TENANT_SHARED','creator','creator')
            """
                .formatted(tenantBIdentity, TENANT_B));
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT count(*)
                      FROM organisation.legal_entity
                     WHERE id='%s'
                    """
                        .formatted(tenantAIdentity)))
            .isZero();
      }
      connection.commit();
    }
  }

  @Test
  void controlledLifecycleFunctionsAreGrantedOnlyToTheApplicationRole()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      assertThat(
              scalarText(
                  statement,
                  """
                  SELECT has_function_privilege(
                    'payroll_app',
                    'organisation.retire_identity(
                      character varying,uuid,uuid,date,bigint,
                      character varying,character varying,
                      timestamp with time zone
                    )',
                    'EXECUTE'
                  )::text
                  """))
          .isEqualTo("true");
      assertThat(
              scalarText(
                  statement,
                  """
                  SELECT (NOT EXISTS (
                    SELECT 1
                      FROM pg_proc p
                      CROSS JOIN LATERAL aclexplode(
                        coalesce(
                          p.proacl,
                          acldefault('f', p.proowner)
                        )
                      ) privilege
                     WHERE p.oid = 'organisation.retire_identity(
                       character varying,uuid,uuid,date,bigint,
                       character varying,character varying,
                       timestamp with time zone
                     )'::regprocedure
                       AND privilege.grantee = 0
                       AND privilege.privilege_type = 'EXECUTE'
                  ))::text
                  """))
          .isEqualTo("true");
      assertThat(
              scalarText(
                  statement,
                  """
                  SELECT has_function_privilege(
                    'payroll_app',
                    'organisation.allocate_version_sequence(
                      character varying,uuid,uuid
                    )',
                    'EXECUTE'
                  )::text
                  """))
          .isEqualTo("true");
      assertThat(
              scalarText(
                  statement,
                  """
                  SELECT (NOT EXISTS (
                    SELECT 1
                      FROM pg_proc p
                      CROSS JOIN LATERAL aclexplode(
                        coalesce(
                          p.proacl,
                          acldefault('f', p.proowner)
                        )
                      ) privilege
                     WHERE p.oid = 'organisation.allocate_version_sequence(
                       character varying,uuid,uuid
                     )'::regprocedure
                       AND privilege.grantee = 0
                       AND privilege.privilege_type = 'EXECUTE'
                  ))::text
                  """))
          .isEqualTo("true");
    }
  }

  private static void setTenant(
      Statement statement, UUID tenant) throws SQLException {
    statement.execute(
        "SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static String scalarText(
      Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static long scalarLong(
      Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static UUID scalarUuid(
      Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static void assertSqlState(
      SqlAction action, String expected) throws Exception {
    try {
      action.run();
      throw new AssertionError(
          "Expected SQLSTATE " + expected + " but statement succeeded");
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expected);
    }
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }
}
