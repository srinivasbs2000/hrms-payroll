package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RowLevelSecurityIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT_A = "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B = "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_A = "10000000-0000-0000-0000-00000000000a";
  private static final String LEGAL_B = "10000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_VERSION_A = "11000000-0000-0000-0000-00000000000a";
  private static final String LEGAL_VERSION_B = "11000000-0000-0000-0000-00000000000b";

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migrateFromZero() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
      statement.execute("REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute("REVOKE CREATE ON SCHEMA public FROM payroll_app");
    }
    var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load();
    flyway.migrate();
    flyway.validate();
  }

  @BeforeEach
  void seedTenants() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute("INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
          + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
          + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
      statement.execute("INSERT INTO organisation.legal_entity(id,tenant_id,code,created_by,updated_by) VALUES "
          + "('" + LEGAL_A + "','" + TENANT_A + "','A_LE','test','test'),"
          + "('" + LEGAL_B + "','" + TENANT_B + "','B_LE','test','test')");
      statement.execute("INSERT INTO organisation.legal_entity_version(id,tenant_id,legal_entity_id,name,effective_from,created_by,updated_by) VALUES "
          + "('" + LEGAL_VERSION_A + "','" + TENANT_A + "','" + LEGAL_A + "','Synthetic A','2026-01-01','test','test'),"
          + "('" + LEGAL_VERSION_B + "','" + TENANT_B + "','" + LEGAL_B + "','Synthetic B','2026-01-01','test','test')");
    }
  }

  @Test
  void rlsCoversNoTenantBothTenantsAndCrossTenantDmlAndFkAttempts() throws Exception {
    try (Connection noTenant = app()) {
      assertThat(count(noTenant, "SELECT count(*) FROM organisation.legal_entity")).isZero();
      assertSqlState("42501", () -> execute(noTenant,
          "INSERT INTO organisation.legal_entity(tenant_id,code,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','NO_TENANT','test','test')"));
    }

    try (Connection tenantA = app()) {
      setTenant(tenantA, TENANT_A);
      assertThat(count(tenantA, "SELECT count(*) FROM organisation.legal_entity")).isEqualTo(1);
      assertThat(count(tenantA, "SELECT count(*) FROM organisation.legal_entity WHERE id='" + LEGAL_B + "'"))
          .isZero();
      assertSqlState("42501", () -> execute(tenantA,
          "INSERT INTO organisation.legal_entity(tenant_id,code,created_by,updated_by) VALUES "
              + "('" + TENANT_B + "','CROSS_INSERT','test','test')"));
      assertSqlState("42501", () -> execute(tenantA,
          "UPDATE organisation.legal_entity SET status='INACTIVE',updated_by='test' WHERE id='" + LEGAL_B + "'"));
      assertSqlState("42501", () -> execute(tenantA,
          "UPDATE organisation.legal_entity SET tenant_id='" + TENANT_B + "',updated_by='test' WHERE id='" + LEGAL_A + "'"));
      assertSqlState("42501", () -> execute(tenantA,
          "DELETE FROM organisation.legal_entity WHERE id='" + LEGAL_B + "'"));
      execute(tenantA,
          "INSERT INTO organisation.payroll_statutory_unit(id,tenant_id,code,created_by,updated_by) VALUES "
              + "('20000000-0000-0000-0000-000000000009','" + TENANT_A + "','BAD_FK','test','test')");
      assertSqlState("23503", () -> execute(tenantA,
          "INSERT INTO organisation.payroll_statutory_unit_version(tenant_id,payroll_statutory_unit_id,legal_entity_version_id,name,effective_from,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','20000000-0000-0000-0000-000000000009','" + LEGAL_VERSION_B + "','Synthetic','2026-01-01','test','test')"));
    }

    try (Connection tenantB = app()) {
      setTenant(tenantB, TENANT_B);
      assertThat(count(tenantB, "SELECT count(*) FROM organisation.legal_entity")).isEqualTo(1);
      assertThat(count(tenantB, "SELECT count(*) FROM organisation.legal_entity WHERE id='" + LEGAL_A + "'"))
          .isZero();
    }
  }

  @Test
  void runtimeRoleCannotDdlBypassRlsAssumeOwnerOrMutateImmutableRecords() throws Exception {
    seedImmutableChain();
    try (Connection runtime = app()) {
      setTenant(runtime, TENANT_A);
      assertSqlState("42501", () -> execute(runtime, "CREATE TABLE public.forbidden(id integer)"));
      assertSqlState("42501", () -> execute(runtime, "SET ROLE payroll_owner"));
      assertSqlState("42501", () -> execute(runtime,
          "UPDATE payroll_ops.input_snapshot SET updated_by='forbidden' WHERE id='70000000-0000-0000-0000-000000000001'"));
      assertSqlState("42501", () -> execute(runtime,
          "DELETE FROM documents.draft_payslip WHERE id='90000000-0000-0000-0000-000000000001'"));
    }
    try (Connection connection = admin()) {
      assertThat(queryBoolean(connection,
          "SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolbypassrls FROM pg_roles WHERE rolname='payroll_app'"))
          .isTrue();
      assertThat(queryBoolean(connection, "SELECT NOT pg_has_role('payroll_app','payroll_owner','MEMBER')"))
          .isTrue();
      assertSqlState("P0001", () -> execute(connection,
          "UPDATE payroll_ops.input_snapshot SET updated_by='forbidden' WHERE id='70000000-0000-0000-0000-000000000001'"));
      assertSqlState("P0001", () -> execute(connection,
          "DELETE FROM documents.draft_payslip WHERE id='90000000-0000-0000-0000-000000000001'"));
    }
  }

  @Test
  void compositeForeignKeysRejectInconsistentRequestCycleSnapshotAndAssignment() throws Exception {
    seedImmutableChain();
    try (Connection connection = admin()) {
      execute(connection, "INSERT INTO payroll_ops.payroll_cycle(id,tenant_id,pay_group_id,pay_period_id,cycle_type,created_by,updated_by) "
          + "SELECT '60000000-0000-0000-0000-000000000002',tenant_id,pay_group_id,pay_period_id,'OTHER','test','test' "
          + "FROM payroll_ops.payroll_cycle WHERE id='60000000-0000-0000-0000-000000000001'");
      execute(connection, "INSERT INTO payroll_calc.calculation_request(id,tenant_id,payroll_cycle_id,idempotency_key,request_hash,created_by,updated_by) VALUES "
          + "('80000000-0000-0000-0000-000000000002','" + TENANT_A + "','60000000-0000-0000-0000-000000000002','other-request',repeat('b',64),'test','test')");
      assertSqlState("23503", () -> execute(connection,
          "INSERT INTO payroll_calc.payroll_result(tenant_id,calculation_request_id,payroll_cycle_id,payroll_assignment_id,input_snapshot_id,result_hash,currency,gross_amount,net_amount,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','80000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000001',"
              + "'50000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000001',repeat('d',64),'INR',1,1,'test','test')"));
    }
  }

  @Test
  void inboxIdempotencyIsTenantAndConsumerScoped() throws Exception {
    String messageId = "a0000000-0000-0000-0000-000000000001";
    try (Connection connection = admin()) {
      execute(connection, inboxInsert(TENANT_A, messageId, "payroll-projection"));
      assertSqlState("23505", () -> execute(connection,
          inboxInsert(TENANT_A, messageId, "payroll-projection")));
      execute(connection, inboxInsert(TENANT_A, messageId, "audit-projection"));
      execute(connection, inboxInsert(TENANT_B, messageId, "payroll-projection"));
      assertThat(count(connection, "SELECT count(*) FROM integration.inbox_message WHERE message_id='" + messageId + "'"))
          .isEqualTo(3);
    }
  }

  @Test
  void approvedOrganisationVersionsCannotOverlapAndRuntimeCannotRewriteHistory() throws Exception {
    try (Connection connection = admin()) {
      assertSqlState("23P01", () -> execute(connection,
          "INSERT INTO organisation.legal_entity_version(tenant_id,legal_entity_id,name,effective_from,approval_status,version_sequence,created_by,updated_by) VALUES ('"
              + TENANT_A + "','" + LEGAL_A + "','Overlapping','2026-06-01','APPROVED',2,'test','test')"));
      execute(connection,
          "INSERT INTO organisation.legal_entity_version(tenant_id,legal_entity_id,name,effective_from,effective_to,approval_status,version_sequence,created_by,updated_by) VALUES ('"
              + TENANT_A + "','" + LEGAL_A + "','Future draft','2027-01-01','2028-01-01','DRAFT',3,'test','test')");
    }
    try (Connection runtime = app()) {
      setTenant(runtime, TENANT_A);
      assertSqlState("42501", () -> execute(runtime,
          "UPDATE organisation.legal_entity_version SET name='Rewritten' WHERE id='" + LEGAL_VERSION_A + "'"));
      assertSqlState("42501", () -> execute(runtime,
          "DELETE FROM organisation.legal_entity_version WHERE id='" + LEGAL_VERSION_A + "'"));
    }
  }

  private String inboxInsert(String tenantId, String messageId, String consumerName) {
    return "INSERT INTO integration.inbox_message(tenant_id,message_id,consumer_name,payload_hash) VALUES ('"
        + tenantId + "','" + messageId + "','" + consumerName + "',repeat('a',64))";
  }

  private void seedImmutableChain() throws Exception {
    try (Connection c = admin(); Statement s = c.createStatement()) {
      s.execute("INSERT INTO organisation.payroll_statutory_unit(id,tenant_id,code,created_by,updated_by) VALUES ('20000000-0000-0000-0000-000000000011','" + TENANT_A + "','PSU','test','test')");
      s.execute("INSERT INTO organisation.payroll_statutory_unit_version(id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,name,effective_from,created_by,updated_by) VALUES ('20000000-0000-0000-0000-000000000001','" + TENANT_A + "','20000000-0000-0000-0000-000000000011','" + LEGAL_VERSION_A + "','Synthetic','2026-01-01','test','test')");
      s.execute("INSERT INTO organisation.establishment(id,tenant_id,code,created_by,updated_by) VALUES ('21000000-0000-0000-0000-000000000011','" + TENANT_A + "','EST','test','test')");
      s.execute("INSERT INTO organisation.establishment_version(id,tenant_id,establishment_id,payroll_statutory_unit_version_id,name,state_code,effective_from,created_by,updated_by) VALUES ('21000000-0000-0000-0000-000000000001','" + TENANT_A + "','21000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000001','Synthetic','KA','2026-01-01','test','test')");
      s.execute("INSERT INTO organisation.payroll_calendar(id,tenant_id,code,name,frequency,created_by,updated_by) VALUES ('30000000-0000-0000-0000-000000000001','" + TENANT_A + "','CAL','Synthetic','MONTHLY','test','test')");
      s.execute("INSERT INTO organisation.pay_period(id,tenant_id,calendar_id,period_code,period_start,period_end,payment_date,created_by,updated_by) VALUES ('31000000-0000-0000-0000-000000000001','" + TENANT_A + "','30000000-0000-0000-0000-000000000001','2026-07','2026-07-01','2026-07-31','2026-07-31','test','test')");
      s.execute("INSERT INTO organisation.pay_group(id,tenant_id,statutory_unit_id,calendar_id,code,name,effective_from,created_by,updated_by) VALUES ('32000000-0000-0000-0000-000000000001','" + TENANT_A + "','20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','PG','Synthetic','2026-01-01','test','test')");
      s.execute("INSERT INTO employee_payroll.payroll_relationship(id,tenant_id,external_employee_id,employee_number,legal_entity_id,relationship_start,created_by,updated_by) VALUES ('40000000-0000-0000-0000-000000000001','" + TENANT_A + "','SYNTHETIC','SYN001','" + LEGAL_VERSION_A + "','2026-01-01','test','test')");
      s.execute("INSERT INTO employee_payroll.payroll_assignment(id,tenant_id,payroll_relationship_id,establishment_id,assignment_number,assignment_start,created_by,updated_by) VALUES ('50000000-0000-0000-0000-000000000001','" + TENANT_A + "','40000000-0000-0000-0000-000000000001','21000000-0000-0000-0000-000000000001','ASN001','2026-01-01','test','test')");
      s.execute("INSERT INTO payroll_ops.payroll_cycle(id,tenant_id,pay_group_id,pay_period_id,created_by,updated_by) VALUES ('60000000-0000-0000-0000-000000000001','" + TENANT_A + "','32000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000001','test','test')");
      s.execute("INSERT INTO payroll_ops.population_member(tenant_id,payroll_cycle_id,payroll_assignment_id,inclusion_reason,created_by,updated_by) VALUES ('" + TENANT_A + "','60000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','synthetic','test','test')");
      s.execute("INSERT INTO payroll_ops.input_snapshot(id,tenant_id,payroll_cycle_id,payroll_assignment_id,snapshot_hash,snapshot_payload,sealed_at,created_by,updated_by) VALUES ('70000000-0000-0000-0000-000000000001','" + TENANT_A + "','60000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001',repeat('a',64),'{}',clock_timestamp(),'test','test')");
      s.execute("INSERT INTO payroll_calc.calculation_request(id,tenant_id,payroll_cycle_id,idempotency_key,request_hash,created_by,updated_by) VALUES ('80000000-0000-0000-0000-000000000001','" + TENANT_A + "','60000000-0000-0000-0000-000000000001','synthetic-request',repeat('b',64),'test','test')");
      s.execute("INSERT INTO payroll_calc.payroll_result(id,tenant_id,calculation_request_id,payroll_cycle_id,payroll_assignment_id,input_snapshot_id,result_hash,currency,gross_amount,net_amount,created_by,updated_by) VALUES ('81000000-0000-0000-0000-000000000001','" + TENANT_A + "','80000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000001',repeat('c',64),'INR',90000,90000,'test','test')");
      s.execute("INSERT INTO documents.draft_payslip(id,tenant_id,payroll_result_id,snapshot_payload,rendered_html,created_by,updated_by) VALUES ('90000000-0000-0000-0000-000000000001','" + TENANT_A + "','81000000-0000-0000-0000-000000000001','{}','DRAFT - NOT FOR PAYMENT','test','test')");
    }
  }

  private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres"); }
  private static Connection app() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD); }
  private static void setTenant(Connection c, String tenant) throws SQLException { execute(c, "SELECT set_config('app.tenant_id','" + tenant + "',false)"); }
  private static void execute(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement()) { s.execute(sql); } }
  private static int executeUpdate(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement()) { return s.executeUpdate(sql); } }
  private static long count(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
  private static boolean queryBoolean(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getBoolean(1); } }
  private static void assertSqlState(String state, SqlAction action) { assertThatThrownBy(action::run).isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo(state); }
  @FunctionalInterface interface SqlAction { void run() throws SQLException; }
}
