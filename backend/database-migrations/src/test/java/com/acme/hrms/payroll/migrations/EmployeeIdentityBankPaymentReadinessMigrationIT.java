package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class EmployeeIdentityBankPaymentReadinessMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final String TENANT =
      "00000000-0000-0000-0000-0000000000e1";
  private static final String RELATIONSHIP =
      "51000000-0000-0000-0000-000000000001";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
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

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "insert into platform.tenant(id,code,name,created_by,updated_by) "
              + "values ('" + TENANT + "','EIP','EIP Synthetic','test','test')");
      statement.execute("set role payroll_owner");
      statement.execute(
          "select set_config('app.tenant_id','" + TENANT + "',false)");
      statement.execute(
          "insert into employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "created_by,updated_by) values ('" + RELATIONSHIP + "','"
              + TENANT + "','EIP-EXT-1','EIP-001','test','test')");
      statement.execute("reset role");
    }
  }

  @Test
  void v051TablesAreForcedRlsAndContainNoPlaintextSecretColumns()
      throws Exception {
    Set<String> expected =
        Set.of(
            "payroll_identifier",
            "payroll_identifier_version",
            "identity_mismatch_case",
            "identity_mismatch_resolution",
            "employee_bank_account",
            "employee_bank_account_version",
            "payment_instruction_set",
            "payment_instruction_set_version",
            "payment_instruction_line",
            "payment_restriction",
            "payment_restriction_event");

    Set<String> actual = new HashSet<>();
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                select relname, relrowsecurity, relforcerowsecurity
                  from pg_class
                 where relnamespace='employee_payroll'::regnamespace
                   and relname in (
                     'payroll_identifier',
                     'payroll_identifier_version',
                     'identity_mismatch_case',
                     'identity_mismatch_resolution',
                     'employee_bank_account',
                     'employee_bank_account_version',
                     'payment_instruction_set',
                     'payment_instruction_set_version',
                     'payment_instruction_line',
                     'payment_restriction',
                     'payment_restriction_event')
                """)) {
      while (result.next()) {
        actual.add(result.getString("relname"));
        assertThat(result.getBoolean("relrowsecurity")).isTrue();
        assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
      }
    }
    assertThat(actual).isEqualTo(expected);

    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                select count(*)
                  from information_schema.columns
                 where table_schema='employee_payroll'
                   and table_name in (
                     'payroll_identifier_version',
                     'identity_mismatch_case',
                     'employee_bank_account_version')
                   and column_name in (
                     'identifier_value',
                     'identifier_plaintext',
                     'account_number',
                     'account_number_plaintext',
                     'account_holder_name',
                     'observed_value',
                     'authoritative_value')
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isZero();
    }
  }

  @Test
  void v051LifecycleAllocationRestrictionsAndReadinessAreFailClosed()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "set local app.tenant_id='" + TENANT + "'");

        statement.execute(
            """
            insert into employee_payroll.payroll_identifier(
              id,tenant_id,payroll_relationship_id,scheme_code,
              created_by,updated_by
            ) values (
              '51100000-0000-0000-0000-000000000001',
              '%s','%s','PAN','maker','maker')
            """.formatted(TENANT, RELATIONSHIP));
        statement.execute(
            """
            insert into employee_payroll.payroll_identifier_version(
              id,tenant_id,payroll_identifier_id,payroll_relationship_id,
              scheme_code,version_sequence,identifier_ciphertext,identifier_iv,
              encryption_key_version,identifier_fingerprint,masked_identifier,
              effective_from,effective_to,created_by,updated_by
            ) values (
              '51200000-0000-0000-0000-000000000001',
              '%s','51100000-0000-0000-0000-000000000001','%s',
              'PAN',1,decode(repeat('ab',32),'hex'),
              decode(repeat('cd',12),'hex'),'v1',repeat('a',64),
              '******234F','2026-01-01','2030-01-01','maker','maker')
            """.formatted(TENANT, RELATIONSHIP));

        assertSqlRejected(
            connection,
            statement,
            "identifier_maker_cannot_verify",
            """
            select employee_payroll.verify_payroll_identifier_version(
              '%s','51200000-0000-0000-0000-000000000001',
              0,'maker','forged-maker-verification',clock_timestamp())
            """.formatted(TENANT));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.verify_payroll_identifier_version(
                  '%s','51200000-0000-0000-0000-000000000001',
                  0,'verifier','verify-1',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);
        assertSqlRejected(
            connection,
            statement,
            "identifier_verifier_cannot_approve",
            """
            select employee_payroll.activate_payroll_identifier_version(
              '%s','51200000-0000-0000-0000-000000000001',
              1,'verifier','forged-verifier-approval',clock_timestamp())
            """.formatted(TENANT));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.activate_payroll_identifier_version(
                  '%s','51200000-0000-0000-0000-000000000001',
                  1,'approver','approve-1',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);

        statement.execute(
            """
            insert into employee_payroll.employee_bank_account(
              id,tenant_id,payroll_relationship_id,code,created_by,updated_by
            ) values (
              '51300000-0000-0000-0000-000000000001',
              '%s','%s','PRIMARY','maker','maker')
            """.formatted(TENANT, RELATIONSHIP));
        statement.execute(
            """
            insert into employee_payroll.employee_bank_account_version(
              id,tenant_id,employee_bank_account_id,payroll_relationship_id,
              version_sequence,bank_name,account_holder_fingerprint,
              masked_account_holder_name,currency_code,
              account_number_ciphertext,account_number_iv,
              encryption_key_version,account_number_fingerprint,
              account_number_last4,effective_from,effective_to,
              created_by,updated_by
            ) values (
              '51400000-0000-0000-0000-000000000001',
              '%s','51300000-0000-0000-0000-000000000001','%s',
              1,'Synthetic Bank',repeat('b',64),'Sy*******','INR',
              decode(repeat('ef',32),'hex'),decode(repeat('12',12),'hex'),
              'v1',repeat('c',64),'7890',
              '2026-01-01','2030-01-01','maker','maker')
            """.formatted(TENANT, RELATIONSHIP));
        assertSqlRejected(
            connection,
            statement,
            "bank_maker_cannot_verify",
            """
            select employee_payroll.verify_employee_bank_account_version(
              '%s','51400000-0000-0000-0000-000000000001',
              0,'maker','forged-bank-maker-verification',clock_timestamp())
            """.formatted(TENANT));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.verify_employee_bank_account_version(
                  '%s','51400000-0000-0000-0000-000000000001',
                  0,'verifier','bank-verify',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);
        assertSqlRejected(
            connection,
            statement,
            "bank_verifier_cannot_approve",
            """
            select employee_payroll.activate_employee_bank_account_version(
              '%s','51400000-0000-0000-0000-000000000001',
              1,'verifier','forged-bank-verifier-approval',clock_timestamp())
            """.formatted(TENANT));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.activate_employee_bank_account_version(
                  '%s','51400000-0000-0000-0000-000000000001',
                  1,'approver','bank-approve',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);

        statement.execute(
            """
            insert into employee_payroll.payment_instruction_set(
              id,tenant_id,payroll_relationship_id,code,created_by,updated_by
            ) values (
              '51500000-0000-0000-0000-000000000001',
              '%s','%s','PRIMARY','maker','maker')
            """.formatted(TENANT, RELATIONSHIP));
        statement.execute(
            """
            insert into employee_payroll.payment_instruction_set_version(
              id,tenant_id,payment_instruction_set_id,payroll_relationship_id,
              version_sequence,currency_code,allocation_mode,
              effective_from,effective_to,created_by,updated_by
            ) values (
              '51600000-0000-0000-0000-000000000001',
              '%s','51500000-0000-0000-0000-000000000001','%s',
              1,'INR','PERCENTAGE','2026-01-01','2030-01-01',
              'maker','maker')
            """.formatted(TENANT, RELATIONSHIP));
        statement.execute(
            """
            insert into employee_payroll.payment_instruction_line(
              tenant_id,payment_instruction_set_version_id,
              payroll_relationship_id,line_sequence,
              employee_bank_account_version_id,line_type,percentage,created_by
            ) values (
              '%s','51600000-0000-0000-0000-000000000001','%s',
              1,'51400000-0000-0000-0000-000000000001',
              'PERCENTAGE',100,'maker')
            """.formatted(TENANT, RELATIONSHIP));

        assertSqlRejected(
            connection,
            statement,
            "instruction_maker_cannot_approve",
            """
            select employee_payroll.activate_payment_instruction_version(
              '%s','51600000-0000-0000-0000-000000000001',
              0,'maker','forged-instruction-maker-approval',clock_timestamp())
            """.formatted(TENANT));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.activate_payment_instruction_version(
                  '%s','51600000-0000-0000-0000-000000000001',
                  0,'approver','instruction-approve',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);

        Set<String> baselineFindings =
            findings(statement, "2026-06-01");
        assertThat(baselineFindings)
            .contains("COMPENSATION_BINDING_MISSING")
            .doesNotContain(
                "PAYMENT_INSTRUCTION_MISSING",
                "BANK_ACCOUNT_UNAVAILABLE");

        statement.execute(
            """
            insert into employee_payroll.identity_mismatch_case(
              id,tenant_id,payroll_relationship_id,affected_field,
              source_kind,classification,payment_impact,correction_owner,
              detected_at,created_by
            ) values (
              '51700000-0000-0000-0000-000000000001','%s','%s',
              'BANK_BENEFICIARY_NAME','BANK','NAME_DIFFERENCE',
              'BLOCKING','HR_SOURCE',clock_timestamp(),'maker')
            """.formatted(TENANT, RELATIONSHIP));

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.create_payment_restriction(
                  '%s','51800000-0000-0000-0000-000000000001','%s',
                  'SECURITY','SEC-001','BENEFICIARY_REVIEW','sec-imposed',
                  '2026-01-01',null,'maker','2026-01-02T00:00:00Z')
                """.formatted(TENANT, RELATIONSHIP)))
            .isEqualTo(1L);

        assertThat(findings(statement, "2026-06-01"))
            .contains(
                "IDENTITY_MISMATCH_BANK_BENEFICIARY_NAME",
                "PAYMENT_RESTRICTION_SECURITY");

        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.resolve_identity_mismatch(
                  '%s','51700000-0000-0000-0000-000000000001',
                  0,'CORRECTED_AT_SOURCE','source corrected',
                  'mismatch-closed','resolver',clock_timestamp())
                """.formatted(TENANT)))
            .isEqualTo(1L);
        assertThat(
            scalar(
                statement,
                """
                select employee_payroll.clear_payment_restriction(
                  '%s','51800000-0000-0000-0000-000000000001',
                  0,'resolver','restriction-cleared',
                  '2026-05-01T00:00:00Z')
                """.formatted(TENANT)))
            .isEqualTo(1L);

        assertThat(findings(statement, "2026-06-01"))
            .doesNotContain(
                "IDENTITY_MISMATCH_BANK_BENEFICIARY_NAME",
                "PAYMENT_RESTRICTION_SECURITY");

        connection.commit();
      }
    }
  }

  @Test
  void runtimeCannotUpdateOrDeleteV051TablesDirectly() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "set local app.tenant_id='" + TENANT + "'");
        var deleteDenied = connection.setSavepoint("delete_denied");
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        "delete from employee_payroll.payroll_identifier"))
            .isInstanceOf(SQLException.class);
        connection.rollback(deleteDenied);
        connection.releaseSavepoint(deleteDenied);

        var restrictionEventInsertDenied =
            connection.setSavepoint("restriction_event_insert_denied");
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        """
                        insert into employee_payroll.payment_restriction_event(
                          tenant_id,payment_restriction_id,event_sequence,event_type,
                          evidence_ref,occurred_at,actor
                        ) values (
                          '%s','51800000-0000-0000-0000-000000000001',
                          99,'CLEARED','forged-clear',clock_timestamp(),'runtime')
                        """.formatted(TENANT)))
            .isInstanceOf(SQLException.class);
        connection.rollback(restrictionEventInsertDenied);
        connection.releaseSavepoint(restrictionEventInsertDenied);

        statement.execute(
            """
            insert into employee_payroll.payroll_identifier(
              id,tenant_id,payroll_relationship_id,scheme_code,
              created_by,updated_by
            ) values (
              '51900000-0000-0000-0000-000000000001','%s','%s',
              'TEST_ID','runtime','runtime')
            """.formatted(TENANT, RELATIONSHIP));

        var activeVersionInsertDenied =
            connection.setSavepoint("active_version_insert_denied");
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        """
                        insert into employee_payroll.payroll_identifier_version(
                          tenant_id,payroll_identifier_id,payroll_relationship_id,
                          scheme_code,version_sequence,identifier_ciphertext,identifier_iv,
                          encryption_key_version,identifier_fingerprint,masked_identifier,
                          effective_from,lifecycle_status,verification_evidence_ref,
                          verified_at,verified_by,approved_at,approved_by,
                          approval_evidence_ref,created_by,updated_by
                        ) values (
                          '%s','51900000-0000-0000-0000-000000000001','%s',
                          'TEST_ID',1,decode(repeat('ab',32),'hex'),
                          decode(repeat('cd',12),'hex'),'v1',repeat('d',64),
                          '******9999','2030-01-01','ACTIVE','forged',
                          clock_timestamp(),'runtime',clock_timestamp(),'runtime',
                          'forged','runtime','runtime')
                        """.formatted(TENANT, RELATIONSHIP)))
            .isInstanceOf(SQLException.class);
        connection.rollback(activeVersionInsertDenied);
        connection.releaseSavepoint(activeVersionInsertDenied);

        assertThat(scalar(statement, "select 1")).isEqualTo(1L);
      } finally {
        connection.rollback();
      }
    }
  }

  private static void assertSqlRejected(
      Connection connection,
      Statement statement,
      String savepointName,
      String sql) throws Exception {
    var savepoint = connection.setSavepoint(savepointName);
    try {
      assertThatThrownBy(() -> scalar(statement, sql))
          .isInstanceOf(SQLException.class);
    } finally {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    }
  }

  private static long scalar(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static Set<String> findings(Statement statement, String asOf)
      throws SQLException {
    Set<String> values = new HashSet<>();
    try (ResultSet result =
        statement.executeQuery(
            """
            select finding_code
              from employee_payroll.payment_readiness_findings(
                '%s','%s','INR','%s')
            """.formatted(TENANT, RELATIONSHIP, asOf))) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}
