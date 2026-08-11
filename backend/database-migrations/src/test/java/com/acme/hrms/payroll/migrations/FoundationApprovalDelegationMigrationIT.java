package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FoundationApprovalDelegationMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID LEGAL_A = UUID.fromString("97000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_B = UUID.fromString("97000000-0000-0000-0000-000000000002");
  private static final UUID AUTHORITY_A = UUID.fromString("97100000-0000-0000-0000-000000000001");
  private static final UUID DELEGATION_A = UUID.fromString("97200000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV036ToV037() throws Exception {
    createRoles();
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").target(MigrationVersion.fromVersion("36"))
        .load().migrate();
    try (Connection c = admin(); Statement s = c.createStatement()) {
      s.execute("""
          INSERT INTO platform.tenant(id, code, name, created_by, updated_by)
          VALUES ('%s', 'PRE37', 'Pre V037 tenant', 'test', 'test')
          """.formatted(TENANT_A));
    }
    Flyway flyway = Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load();
    flyway.migrate();
    flyway.validate();
  }

  @BeforeEach
  void reset() throws Exception {
    try (Connection c = admin(); Statement s = c.createStatement()) {
      s.execute("TRUNCATE platform.tenant CASCADE");
      s.execute("""
          INSERT INTO platform.tenant(id, code, name, created_by, updated_by) VALUES
            ('%s','A','Synthetic Tenant A','test','test'),
            ('%s','B','Synthetic Tenant B','test','test')
          """.formatted(TENANT_A, TENANT_B));
      s.execute("""
          INSERT INTO organisation.legal_entity(
            id, tenant_id, code, status, created_by, updated_by
          ) VALUES
            ('%s','%s','LEGAL_A','ACTIVE','test','test'),
            ('%s','%s','LEGAL_B','ACTIVE','test','test')
          """.formatted(LEGAL_A, TENANT_A, LEGAL_B, TENANT_B));
    }
  }

  @Test
  void v037CreatesTwoForcedRlsTables() throws Exception {
    try (Connection c = admin(); Statement s = c.createStatement();
         ResultSet r = s.executeQuery("""
            SELECT count(*) FROM pg_class relation
            JOIN pg_namespace namespace ON namespace.oid=relation.relnamespace
            WHERE namespace.nspname='security'
              AND relation.relname IN ('approval_authority_assignment','approval_delegation')
              AND relation.relrowsecurity AND relation.relforcerowsecurity
            """)) {
      assertThat(r.next()).isTrue();
      assertThat(r.getLong(1)).isEqualTo(2);
    }
  }

  @Test
  void tenantIsolationAndOwnerGuardFailClosed() throws Exception {
    try (Connection c = app()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        setTenant(s, TENANT_A);
        seedAuthority(s, AUTHORITY_A, TENANT_A, LEGAL_A);
        assertThat(count(s, "security.approval_authority_assignment")).isEqualTo(1);
        c.commit();
        c.setAutoCommit(false);
        setTenant(s, TENANT_B);
        assertThat(count(s, "security.approval_authority_assignment")).isZero();

        Savepoint wrongOwner = c.setSavepoint();
        assertSqlState("23503", () -> s.execute("""
            INSERT INTO security.approval_authority_assignment(
              id,tenant_id,owner_kind,owner_id,approval_role,domain_code,action_code,
              actor_id,effective_from,effective_to,created_by,updated_by
            ) VALUES (
              gen_random_uuid(),'%s','LEGAL_ENTITY','%s','FINAL_APPROVER',
              'ORGANISATION_CONFIG','APPROVE','issuer|wrong-owner',
              DATE '2026-01-01',NULL,'issuer|admin','issuer|admin'
            )
            """.formatted(TENANT_B, LEGAL_A)));
        c.rollback(wrongOwner);
      }
    }
  }

  @Test
  void delegationIsBoundedAndSourceSuspensionInvalidatesResolution() throws Exception {
    try (Connection c = app()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        setTenant(s, TENANT_A);
        seedAuthority(s, AUTHORITY_A, TENANT_A, LEGAL_A);
        s.execute("""
            INSERT INTO security.approval_delegation(
              id,tenant_id,source_authority_id,delegator_actor_id,delegate_actor_id,
              effective_from,effective_to,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','issuer|approver','issuer|delegate',
              DATE '2026-06-01',DATE '2026-07-01','issuer|approver','issuer|approver'
            )
            """.formatted(DELEGATION_A, TENANT_A, AUTHORITY_A));

        assertThat(resolutionCount(s, "issuer|delegate", "2026-06-15")).isEqualTo(1);
        assertThat(resolutionCount(s, "issuer|delegate", "2026-07-01")).isZero();

        Savepoint self = c.setSavepoint();
        assertSqlState("23514", () -> s.execute("""
            INSERT INTO security.approval_delegation(
              id,tenant_id,source_authority_id,delegator_actor_id,delegate_actor_id,
              effective_from,effective_to,created_by,updated_by
            ) VALUES (
              gen_random_uuid(),'%s','%s','issuer|approver','issuer|approver',
              DATE '2026-06-01',DATE '2026-06-20','issuer|approver','issuer|approver'
            )
            """.formatted(TENANT_A, AUTHORITY_A)));
        c.rollback(self);

        Savepoint wider = c.setSavepoint();
        assertSqlState("23514", () -> s.execute("""
            INSERT INTO security.approval_delegation(
              id,tenant_id,source_authority_id,delegator_actor_id,delegate_actor_id,
              effective_from,effective_to,created_by,updated_by
            ) VALUES (
              gen_random_uuid(),'%s','%s','issuer|approver','issuer|wide',
              DATE '2025-12-31',DATE '2026-06-20','issuer|approver','issuer|approver'
            )
            """.formatted(TENANT_A, AUTHORITY_A)));
        c.rollback(wider);

        assertThat(scalarLong(s, """
            SELECT security.suspend_approval_authority(
              '%s','%s',0,'issuer|admin','temporary suspension','%s'
            )
            """.formatted(TENANT_A, AUTHORITY_A,
                Instant.parse("2026-06-16T00:00:00Z")))).isEqualTo(1);
        assertThat(resolutionCount(s, "issuer|delegate", "2026-06-16")).isZero();
      }
    }
  }

  @Test
  void serviceIdentityCannotReceiveFinalApprovalAuthority() throws Exception {
    try (Connection c = app()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        setTenant(s, TENANT_A);
        assertSqlState("23514", () -> s.execute("""
            INSERT INTO security.approval_authority_assignment(
              id,tenant_id,owner_kind,owner_id,approval_role,domain_code,action_code,
              actor_id,effective_from,effective_to,created_by,updated_by
            ) VALUES (
              gen_random_uuid(),'%s','LEGAL_ENTITY','%s','FINAL_APPROVER',
              'ORGANISATION_CONFIG','APPROVE','service:batch',
              DATE '2026-01-01',NULL,'issuer|admin','issuer|admin'
            )
            """.formatted(TENANT_A, LEGAL_A)));
      }
    }
  }

  private static void seedAuthority(Statement s, UUID id, UUID tenant, UUID owner)
      throws SQLException {
    s.execute("""
        INSERT INTO security.approval_authority_assignment(
          id,tenant_id,owner_kind,owner_id,approval_role,domain_code,action_code,
          actor_id,effective_from,effective_to,created_by,updated_by
        ) VALUES (
          '%s','%s','LEGAL_ENTITY','%s','FINAL_APPROVER',
          'ORGANISATION_CONFIG','APPROVE','issuer|approver',
          DATE '2026-01-01',DATE '2027-01-01','issuer|admin','issuer|admin'
        )
        """.formatted(id, tenant, owner));
  }

  private static long resolutionCount(Statement s, String actor, String asOf)
      throws SQLException {
    return scalarLong(s, """
        SELECT count(*) FROM security.resolve_approval_authority(
          '%s','%s','LEGAL_ENTITY','%s','FINAL_APPROVER',
          'ORGANISATION_CONFIG','APPROVE',DATE '%s'
        )
        """.formatted(TENANT_A, actor, LEGAL_A, asOf));
  }

  private static long count(Statement s, String relation) throws SQLException {
    return scalarLong(s, "SELECT count(*) FROM " + relation);
  }
  private static long scalarLong(Statement s, String sql) throws SQLException {
    try (ResultSet r = s.executeQuery(sql)) {
      assertThat(r.next()).isTrue();
      return r.getLong(1);
    }
  }
  private static void setTenant(Statement s, UUID tenant) throws SQLException {
    s.execute("SET LOCAL app.tenant_id='" + tenant + "'");
  }
  private static void assertSqlState(String state, SqlWork work) {
    try {
      work.run();
      throw new AssertionError("Expected SQL state " + state);
    } catch (SQLException e) {
      assertThat(e.getSQLState()).isEqualTo(state);
    }
  }
  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
  private static Connection app() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
  private static void createRoles() throws Exception {
    try (Connection c = admin(); Statement s = c.createStatement()) {
      s.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
          + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      s.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
          + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      s.execute("CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD
          + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      s.execute("GRANT payroll_owner TO payroll_migrator");
      s.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      s.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      s.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
  }
  @FunctionalInterface private interface SqlWork { void run() throws SQLException; }
}
