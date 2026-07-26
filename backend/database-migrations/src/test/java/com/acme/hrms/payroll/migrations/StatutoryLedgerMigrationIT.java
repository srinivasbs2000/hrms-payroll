package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class StatutoryLedgerMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_VERSION =
      "11000000-0000-0000-0000-00000000000a";
  private static final String PSU_VERSION =
      "20000000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_VERSION =
      "21000000-0000-0000-0000-000000000001";
  private static final String CALENDAR =
      "30000000-0000-0000-0000-000000000001";
  private static final String PERIOD =
      "31000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP =
      "32000000-0000-0000-0000-000000000011";
  private static final String PAY_GROUP_VERSION =
      "32000000-0000-0000-0000-000000000001";
  private static final String COMPONENT =
      "33000000-0000-0000-0000-000000000011";
  private static final String COMPONENT_VERSION =
      "33000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE =
      "34000000-0000-0000-0000-000000000011";
  private static final String STRUCTURE_VERSION =
      "34000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_LINE =
      "34000000-0000-0000-0000-000000000002";
  private static final String RELATIONSHIP =
      "40000000-0000-0000-0000-000000000011";
  private static final String RELATIONSHIP_VERSION =
      "40000000-0000-0000-0000-000000000001";
  private static final String PAYROLL_PROFILE =
      "41000000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT =
      "50000000-0000-0000-0000-000000000011";
  private static final String ASSIGNMENT_VERSION =
      "50000000-0000-0000-0000-000000000001";
  private static final String GROUP_ASSIGNMENT =
      "51000000-0000-0000-0000-000000000001";
  private static final String SALARY_ASSIGNMENT =
      "52000000-0000-0000-0000-000000000001";

  private static final String SOCIAL_RULE =
      "a1000000-0000-0000-0000-000000000001";
  private static final String SOCIAL_VERSION =
      "a1100000-0000-0000-0000-000000000001";
  private static final String SOCIAL_EMPLOYEE_PORTION =
      "a1200000-0000-0000-0000-000000000001";
  private static final String SOCIAL_EMPLOYER_PORTION =
      "a1200000-0000-0000-0000-000000000002";
  private static final String TAX_RULE =
      "a1000000-0000-0000-0000-000000000002";
  private static final String TAX_VERSION =
      "a1100000-0000-0000-0000-000000000002";
  private static final String TAX_PORTION =
      "a1200000-0000-0000-0000-000000000003";
  private static final String TAX_SLAB_ONE =
      "a1300000-0000-0000-0000-000000000001";
  private static final String TAX_SLAB_TWO =
      "a1300000-0000-0000-0000-000000000002";
  private static final String STATUTORY_PROFILE =
      "b1000000-0000-0000-0000-000000000001";
  private static final String STATUTORY_PROFILE_VERSION =
      "b1100000-0000-0000-0000-000000000001";
  private static final String SOCIAL_ASSIGNMENT =
      "b1200000-0000-0000-0000-000000000001";
  private static final String TAX_ASSIGNMENT =
      "b1200000-0000-0000-0000-000000000002";
  private static final String CLASSIFICATION =
      "c1000000-0000-0000-0000-000000000001";
  private static final String BALANCE_YEAR =
      "d1000000-0000-0000-0000-000000000001";

  private static String cycleId;
  private static String calculationRequestId;
  private static String evaluationRequestId;
  private static String ledgerBatchId;
  private static long calculatedCycleVersion;
  private static long postedCycleVersion;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateSeedCalculateAndEvaluate() throws Exception {
    createRoles();
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

    seedPayrollConfiguration();
    seedStatutoryConfiguration();
    calculatePayroll();
    evaluateStatutory();
    seedBalanceYear();
    postStatutoryEvaluation();
  }

  @Test
  void deterministicPostingPersistsLedgerBalancesAndRemittanceEvidence()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT batch_kind,attempt_no,status,entry_count,"
                      + "balance_snapshot_count,remittance_summary_count,"
                      + "employee_delta_total,employer_delta_total,"
                      + "cycle_employee_total,cycle_employer_total,"
                      + "ledger_set_hash,reconciliation_hash "
                      + "FROM statutory.statutory_ledger_batch WHERE id='"
                      + ledgerBatchId
                      + "'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("batch_kind")).isEqualTo("INITIAL");
        assertThat(result.getInt("attempt_no")).isOne();
        assertThat(result.getString("status")).isEqualTo("COMPLETED");
        assertThat(result.getInt("entry_count")).isEqualTo(2);
        assertThat(result.getInt("balance_snapshot_count")).isEqualTo(2);
        assertThat(result.getInt("remittance_summary_count")).isEqualTo(2);
        assertThat(result.getBigDecimal("employee_delta_total"))
            .isEqualByComparingTo("17000.0000");
        assertThat(result.getBigDecimal("employer_delta_total"))
            .isEqualByComparingTo("500.0000");
        assertThat(result.getBigDecimal("cycle_employee_total"))
            .isEqualByComparingTo("17000.0000");
        assertThat(result.getBigDecimal("cycle_employer_total"))
            .isEqualByComparingTo("500.0000");
        assertThat(result.getString("ledger_set_hash"))
            .matches("[0-9a-f]{64}");
        assertThat(result.getString("reconciliation_hash"))
            .matches("[0-9a-f]{64}");
      }

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "' AND entry_kind='EVALUATION'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_balance_snapshot "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "' AND period_employee_amount=year_employee_amount "
                      + "AND period_employer_amount=year_employer_amount"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_reconciliation "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "' AND reconciliation_status='MATCHED' "
                      + "AND employee_variance=0 AND employer_variance=0"))
          .isOne();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_remittance_summary "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "' AND remittance_position='PAYABLE'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM payroll_ops.payroll_cycle "
                      + "WHERE id='"
                      + cycleId
                      + "' AND active_statutory_ledger_batch_id='"
                      + ledgerBatchId
                      + "' AND statutory_employee_total=17000 "
                      + "AND statutory_employer_total=500"))
          .isOne();

      connection.rollback();
    }
  }

  @Test
  void idempotentPostingReplayReturnsTheOriginalBatch() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      String replayed =
          queryString(
              connection,
              "SELECT ledger_batch_id::text FROM "
                  + "statutory.post_statutory_evaluation('"
                  + TENANT_A
                  + "','"
                  + evaluationRequestId
                  + "',"
                  + postedCycleVersion
                  + ",'statutory-posting-001','"
                  + "1".repeat(64)
                  + "','test','2026-07-31T09:05:00Z')");

      assertThat(replayed).isEqualTo(ledgerBatchId);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch"))
          .isOne();
      connection.rollback();
    }
  }

  @Test
  void correctionCreatesSignedEntryAndZeroVarianceEvidence()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      String socialResultId =
          queryString(
              connection,
              "SELECT result.id::text "
                  + "FROM statutory.statutory_result result "
                  + "JOIN statutory.statutory_input_snapshot snapshot "
                  + "ON snapshot.tenant_id=result.tenant_id "
                  + "AND snapshot.id=result.statutory_input_snapshot_id "
                  + "WHERE result.evaluation_request_id='"
                  + evaluationRequestId
                  + "' AND snapshot.statutory_rule_id='"
                  + SOCIAL_RULE
                  + "'");

      String correctionBatchId;
      long correctedVersion;
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT ledger_batch_id::text,cycle_version_no "
                      + "FROM statutory.post_statutory_correction('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "','"
                      + socialResultId
                      + "',-100,25,'Correct synthetic statutory result',"
                      + postedCycleVersion
                      + ",'statutory-correction-001','"
                      + "2".repeat(64)
                      + "','test','2026-07-31T09:06:00Z')")) {
        assertThat(result.next()).isTrue();
        correctionBatchId = result.getString(1);
        correctedVersion = result.getLong(2);
      }

      assertThat(correctedVersion).isEqualTo(postedCycleVersion + 1);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch "
                      + "WHERE id='"
                      + correctionBatchId
                      + "' AND batch_kind='CORRECTION' AND attempt_no=2 "
                      + "AND supersedes_batch_id='"
                      + ledgerBatchId
                      + "' AND employee_delta_total=-100 "
                      + "AND employer_delta_total=25 "
                      + "AND cycle_employee_total=16900 "
                      + "AND cycle_employer_total=525"))
          .isOne();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + correctionBatchId
                      + "' AND entry_kind='CORRECTION' "
                      + "AND employee_amount_delta=-100 "
                      + "AND employer_amount_delta=25"))
          .isOne();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_reconciliation "
                      + "WHERE ledger_batch_id='"
                      + correctionBatchId
                      + "' AND source_employee_total=17000 "
                      + "AND source_employer_total=500 "
                      + "AND correction_employee_total=-100 "
                      + "AND correction_employer_total=25 "
                      + "AND ledger_employee_total=16900 "
                      + "AND ledger_employer_total=525 "
                      + "AND employee_variance=0 AND employer_variance=0"))
          .isOne();

      connection.rollback();
    }
  }

  @Test
  void recalculatedEvaluationReversesPriorLedgerBeforeReplacement()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      String replacementCalculationId;
      long replacementCalculationVersion;
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT calculation_request_id::text,cycle_version_no "
                      + "FROM payroll_calc.recalculate_sealed_payroll('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "',"
                      + postedCycleVersion
                      + ",'payroll-recalculate-ledger-001','"
                      + "3".repeat(64)
                      + "','Synthetic ledger replacement verification',"
                      + "'test','2026-07-31T09:07:00Z')")) {
        assertThat(result.next()).isTrue();
        replacementCalculationId = result.getString(1);
        replacementCalculationVersion = result.getLong(2);
      }

      String replacementEvaluationId =
          queryString(
              connection,
              "SELECT evaluation_request_id::text "
                  + "FROM statutory.evaluate_calculated_payroll('"
                  + TENANT_A
                  + "','"
                  + cycleId
                  + "','"
                  + replacementCalculationId
                  + "',"
                  + replacementCalculationVersion
                  + ",'statutory-evaluation-replacement-001','"
                  + "4".repeat(64)
                  + "','test','2026-07-31T09:08:00Z')");

      String replacementBatchId;
      long replacementPostingVersion;
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT ledger_batch_id::text,cycle_version_no "
                      + "FROM statutory.post_statutory_evaluation('"
                      + TENANT_A
                      + "','"
                      + replacementEvaluationId
                      + "',"
                      + replacementCalculationVersion
                      + ",'statutory-posting-replacement-001','"
                      + "5".repeat(64)
                      + "','test','2026-07-31T09:09:00Z')")) {
        assertThat(result.next()).isTrue();
        replacementBatchId = result.getString(1);
        replacementPostingVersion = result.getLong(2);
      }

      assertThat(replacementPostingVersion)
          .isEqualTo(replacementCalculationVersion + 1);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch "
                      + "WHERE id='"
                      + replacementBatchId
                      + "' AND batch_kind='REPLACEMENT' AND attempt_no=2 "
                      + "AND supersedes_batch_id='"
                      + ledgerBatchId
                      + "' AND entry_count=4 "
                      + "AND cycle_employee_total=17000 "
                      + "AND cycle_employer_total=500"))
          .isOne();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + replacementBatchId
                      + "' AND entry_kind='REVERSAL'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + replacementBatchId
                      + "' AND entry_kind='EVALUATION'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_reconciliation "
                      + "WHERE ledger_batch_id='"
                      + replacementBatchId
                      + "' AND employee_variance=0 "
                      + "AND employer_variance=0"))
          .isOne();

      String secondCalculationId;
      long secondCalculationVersion;
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT calculation_request_id::text,cycle_version_no "
                      + "FROM payroll_calc.recalculate_sealed_payroll('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "',"
                      + replacementPostingVersion
                      + ",'payroll-recalculate-ledger-002','"
                      + "7".repeat(64)
                      + "','Second ledger replacement verification',"
                      + "'test','2026-07-31T09:10:00Z')")) {
        assertThat(result.next()).isTrue();
        secondCalculationId = result.getString(1);
        secondCalculationVersion = result.getLong(2);
      }

      String secondEvaluationId =
          queryString(
              connection,
              "SELECT evaluation_request_id::text "
                  + "FROM statutory.evaluate_calculated_payroll('"
                  + TENANT_A
                  + "','"
                  + cycleId
                  + "','"
                  + secondCalculationId
                  + "',"
                  + secondCalculationVersion
                  + ",'statutory-evaluation-replacement-002','"
                  + "8".repeat(64)
                  + "','test','2026-07-31T09:11:00Z')");

      String secondBatchId;
      long secondPostingVersion;
      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT ledger_batch_id::text,cycle_version_no "
                      + "FROM statutory.post_statutory_evaluation('"
                      + TENANT_A
                      + "','"
                      + secondEvaluationId
                      + "',"
                      + secondCalculationVersion
                      + ",'statutory-posting-replacement-002','"
                      + "9".repeat(64)
                      + "','test','2026-07-31T09:12:00Z')")) {
        assertThat(result.next()).isTrue();
        secondBatchId = result.getString(1);
        secondPostingVersion = result.getLong(2);
      }

      assertThat(secondPostingVersion).isEqualTo(secondCalculationVersion + 1);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch "
                      + "WHERE id='"
                      + secondBatchId
                      + "' AND batch_kind='REPLACEMENT' AND attempt_no=3 "
                      + "AND supersedes_batch_id='"
                      + replacementBatchId
                      + "' AND entry_count=4 "
                      + "AND cycle_employee_total=17000 "
                      + "AND cycle_employer_total=500"))
          .isOne();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + secondBatchId
                      + "' AND entry_kind='REVERSAL'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + secondBatchId
                      + "' AND entry_kind='EVALUATION'"))
          .isEqualTo(2);

      connection.rollback();
    }
  }

  @Test
  void stalePostingVersionIsRejectedBeforeAnyLedgerEvidence()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "40001",
          () ->
              execute(
                  connection,
                  "SELECT * FROM statutory.post_statutory_evaluation('"
                      + TENANT_A
                      + "','"
                      + evaluationRequestId
                      + "',"
                      + (postedCycleVersion - 1)
                      + ",'statutory-posting-stale-001','"
                      + "6".repeat(64)
                      + "','test',clock_timestamp())"));

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch"))
          .isOne();
      connection.rollback();
    }
  }

  @Test
  void appCannotRewriteImmutableLedgerEvidence() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "UPDATE statutory.statutory_ledger_batch "
                      + "SET posted_by='rewritten' WHERE id='"
                      + ledgerBatchId
                      + "'"));
      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "DELETE FROM statutory.statutory_ledger_entry "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "'"));
      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "UPDATE statutory.statutory_balance_snapshot "
                      + "SET period_employee_amount=0 "
                      + "WHERE ledger_batch_id='"
                      + ledgerBatchId
                      + "'"));

      connection.rollback();
    }
  }

  @Test
  void overlappingApprovedBalanceYearIsRejected() throws Exception {
    String overlappingYear =
        "d1000000-0000-0000-0000-000000000002";
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      execute(
          connection,
          "INSERT INTO statutory.statutory_balance_year("
              + "id,tenant_id,jurisdiction_code,authority_code,"
              + "balance_year_code,version_sequence,period_start,"
              + "period_end,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + overlappingYear
              + "','"
              + TENANT_A
              + "','IN','CENTRAL','IN_CENTRAL_ALT_2026',1,"
              + "'2026-06-01','2027-06-01','DRAFT','test','test')");

      assertSqlState(
          connection,
          "23P01",
          () ->
              execute(
                  connection,
                  "SELECT statutory.approve_statutory_balance_year('"
                      + TENANT_A
                      + "','"
                      + overlappingYear
                      + "','test',clock_timestamp())"));
      connection.rollback();
    }
  }

  @Test
  void rowLevelSecurityHidesTenantALedgerEvidenceFromTenantB()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_B);

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_balance_year"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_batch"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_ledger_entry"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_balance_snapshot"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_reconciliation"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_remittance_summary"))
          .isZero();

      connection.rollback();
    }
  }

  @Test
  void deterministicEvaluationPersistsExactImmutableEvidence()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT engine_version,payroll_result_count,"
                      + "statutory_result_count,employee_total,employer_total,"
                      + "post_statutory_net_total,evidence_set_hash "
                      + "FROM statutory.statutory_evaluation_request "
                      + "WHERE id='"
                      + evaluationRequestId
                      + "'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("engine_version"))
            .isEqualTo("STATUTORY_NEUTRAL_V1");
        assertThat(result.getInt("payroll_result_count")).isOne();
        assertThat(result.getInt("statutory_result_count")).isEqualTo(2);
        assertThat(result.getBigDecimal("employee_total"))
            .isEqualByComparingTo("17000.0000");
        assertThat(result.getBigDecimal("employer_total"))
            .isEqualByComparingTo("500.0000");
        assertThat(result.getBigDecimal("post_statutory_net_total"))
            .isEqualByComparingTo("73000.0000");
        assertThat(result.getString("evidence_set_hash"))
            .matches("[0-9a-f]{64}");
      }

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_input_snapshot "
                      + "WHERE evaluation_request_id='"
                      + evaluationRequestId
                      + "'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_result "
                      + "WHERE evaluation_request_id='"
                      + evaluationRequestId
                      + "'"))
          .isEqualTo(2);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_portion_result"))
          .isEqualTo(3);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.payroll_statutory_summary "
                      + "WHERE employee_statutory_amount=17000 "
                      + "AND employer_statutory_amount=500 "
                      + "AND post_statutory_net_amount=73000"))
          .isOne();

      connection.rollback();
    }
  }

  @Test
  void idempotentReplayReturnsTheOriginalEvaluation() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      String replayed =
          queryString(
              connection,
              "SELECT evaluation_request_id::text FROM "
                  + "statutory.evaluate_calculated_payroll('"
                  + TENANT_A
                  + "','"
                  + cycleId
                  + "','"
                  + calculationRequestId
                  + "',"
                  + calculatedCycleVersion
                  + ",'statutory-evaluation-001','"
                  + "e".repeat(64)
                  + "','test','2026-07-31T12:00:00Z')");

      assertThat(replayed).isEqualTo(evaluationRequestId);
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_evaluation_request"))
          .isOne();
      connection.rollback();
    }
  }

  @Test
  void staleCycleVersionIsRejectedBeforeAnyEvidenceIsWritten()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "40001",
          () ->
              execute(
                  connection,
                  "SELECT * FROM statutory.evaluate_calculated_payroll('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "','"
                      + calculationRequestId
                      + "',"
                      + (calculatedCycleVersion - 1)
                      + ",'statutory-stale-001','"
                      + "f".repeat(64)
                      + "','test',clock_timestamp())"));

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_evaluation_request"))
          .isOne();
      connection.rollback();
    }
  }

  @Test
  void appCannotRewriteImmutableStatutoryEvidence() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "UPDATE statutory.statutory_evaluation_request "
                      + "SET completed_by='rewritten' WHERE id='"
                      + evaluationRequestId
                      + "'"));
      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "DELETE FROM statutory.statutory_result "
                      + "WHERE evaluation_request_id='"
                      + evaluationRequestId
                      + "'"));
      assertSqlState(
          connection,
          "42501",
          () ->
              execute(
                  connection,
                  "UPDATE statutory.payroll_statutory_summary "
                      + "SET employee_statutory_amount=0 "
                      + "WHERE evaluation_request_id='"
                      + evaluationRequestId
                      + "'"));

      connection.rollback();
    }
  }

  @Test
  void overlappingApprovedClassificationIsRejected() throws Exception {
    String successor = "c1000000-0000-0000-0000-000000000002";
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      execute(
          connection,
          "INSERT INTO statutory.statutory_component_classification("
              + "id,tenant_id,jurisdiction_code,authority_code,"
              + "assessment_base_code,component_id,component_version_id,"
              + "classification_sequence,inclusion_percent,effective_from,"
              + "approval_status,supersedes_classification_id,"
              + "created_by,updated_by) VALUES ('"
              + successor
              + "','"
              + TENANT_A
              + "','IN','CENTRAL','GROSS','"
              + COMPONENT
              + "','"
              + COMPONENT_VERSION
              + "',2,100,'2026-07-01','DRAFT','"
              + CLASSIFICATION
              + "','test','test')");

      assertSqlState(
          connection,
          "23P01",
          () ->
              execute(
                  connection,
                  "SELECT statutory."
                      + "approve_statutory_component_classification('"
                      + TENANT_A
                      + "','"
                      + successor
                      + "','test',clock_timestamp())"));
      connection.rollback();
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAEvidenceFromTenantB()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_B);

      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_evaluation_request"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_input_snapshot"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.statutory_result"))
          .isZero();
      assertThat(
              queryLong(
                  connection,
                  "SELECT count(*) FROM statutory.payroll_statutory_summary"))
          .isZero();

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
      statement.execute(
          "REVOKE CREATE ON DATABASE payroll FROM payroll_app");
      statement.execute(
          "REVOKE CREATE ON SCHEMA public FROM payroll_app");
    }
  }

  private static void seedPayrollConfiguration() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) "
              + "VALUES ('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");

      statement.execute(
          "INSERT INTO organisation.legal_entity("
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('10000000-0000-0000-0000-00000000000a','"
              + TENANT_A
              + "','A_LE','test','test')");
      statement.execute(
          "INSERT INTO organisation.legal_entity_version("
              + "id,tenant_id,legal_entity_id,name,effective_from,"
              + "created_by,updated_by) VALUES ('"
              + LEGAL_VERSION
              + "','"
              + TENANT_A
              + "','10000000-0000-0000-0000-00000000000a',"
              + "'Synthetic A','2026-01-01','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit("
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('20000000-0000-0000-0000-000000000011','"
              + TENANT_A
              + "','PSU','test','test')");
      statement.execute(
          "INSERT INTO organisation.payroll_statutory_unit_version("
              + "id,tenant_id,payroll_statutory_unit_id,"
              + "legal_entity_version_id,name,effective_from,"
              + "created_by,updated_by) VALUES ('"
              + PSU_VERSION
              + "','"
              + TENANT_A
              + "','20000000-0000-0000-0000-000000000011','"
              + LEGAL_VERSION
              + "','Synthetic','2026-01-01','test','test')");
      statement.execute(
          "INSERT INTO organisation.establishment("
              + "id,tenant_id,code,created_by,updated_by) VALUES "
              + "('21000000-0000-0000-0000-000000000011','"
              + TENANT_A
              + "','EST','test','test')");
      statement.execute(
          "INSERT INTO organisation.establishment_version("
              + "id,tenant_id,establishment_id,"
              + "payroll_statutory_unit_version_id,name,state_code,"
              + "effective_from,created_by,updated_by) VALUES ('"
              + ESTABLISHMENT_VERSION
              + "','"
              + TENANT_A
              + "','21000000-0000-0000-0000-000000000011','"
              + PSU_VERSION
              + "','Synthetic','KA','2026-01-01','test','test')");

      statement.execute(
          "INSERT INTO organisation.payroll_calendar("
              + "id,tenant_id,code,name,frequency,created_by,updated_by) "
              + "VALUES ('"
              + CALENDAR
              + "','"
              + TENANT_A
              + "','CAL','Synthetic','MONTHLY','test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_period("
              + "id,tenant_id,calendar_id,period_code,period_start,"
              + "period_end,payment_date,created_by,updated_by) VALUES ('"
              + PERIOD
              + "','"
              + TENANT_A
              + "','"
              + CALENDAR
              + "','2026-07','2026-07-01','2026-07-31',"
              + "'2026-07-31','test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_group("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + PAY_GROUP
              + "','"
              + TENANT_A
              + "','PG','test','test')");
      statement.execute(
          "INSERT INTO organisation.pay_group_version("
              + "id,tenant_id,pay_group_id,"
              + "payroll_statutory_unit_version_id,calendar_id,"
              + "version_sequence,name,currency,proration_method,"
              + "effective_from,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + PAY_GROUP_VERSION
              + "','"
              + TENANT_A
              + "','"
              + PAY_GROUP
              + "','"
              + PSU_VERSION
              + "','"
              + CALENDAR
              + "',1,'Synthetic','INR','CALENDAR_DAYS',"
              + "'2026-01-01','DRAFT','test','test')");
      statement.execute(
          "SELECT organisation.approve_pay_group_version('"
              + TENANT_A
              + "','"
              + PAY_GROUP_VERSION
              + "','test',clock_timestamp())");

      statement.execute(
          "INSERT INTO compensation.pay_component("
              + "id,tenant_id,code,name,component_type,"
              + "created_by,updated_by) VALUES ('"
              + COMPONENT
              + "','"
              + TENANT_A
              + "','BASIC','Basic Pay','EARNING','test','test')");
      statement.execute(
          "INSERT INTO compensation.pay_component_version("
              + "id,tenant_id,component_id,version_sequence,formula_type,"
              + "fixed_amount,rounding_scale,effective_from,approval_status,"
              + "created_by,updated_by) VALUES ('"
              + COMPONENT_VERSION
              + "','"
              + TENANT_A
              + "','"
              + COMPONENT
              + "',1,'FIXED',90000,2,'2026-01-01','DRAFT',"
              + "'test','test')");
      statement.execute(
          "SELECT compensation.approve_pay_component_version('"
              + TENANT_A
              + "','"
              + COMPONENT_VERSION
              + "','test',clock_timestamp())");
      statement.execute(
          "INSERT INTO compensation.salary_structure("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + STRUCTURE
              + "','"
              + TENANT_A
              + "','SALARY','test','test')");
      statement.execute(
          "INSERT INTO compensation.salary_structure_version("
              + "id,tenant_id,salary_structure_id,version_sequence,name,"
              + "currency,effective_from,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + STRUCTURE_VERSION
              + "','"
              + TENANT_A
              + "','"
              + STRUCTURE
              + "',1,'Synthetic Salary','INR','2026-01-01','DRAFT',"
              + "'test','test')");
      statement.execute(
          "INSERT INTO compensation.salary_structure_line("
              + "id,tenant_id,salary_structure_version_id,"
              + "component_version_id,sequence_no,target_amount,"
              + "effective_from,created_by,updated_by) VALUES ('"
              + STRUCTURE_LINE
              + "','"
              + TENANT_A
              + "','"
              + STRUCTURE_VERSION
              + "','"
              + COMPONENT_VERSION
              + "',1,90000,'2026-01-01','test','test')");
      statement.execute(
          "SELECT compensation.approve_salary_structure_version('"
              + TENANT_A
              + "','"
              + STRUCTURE_VERSION
              + "','test',clock_timestamp())");

      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "created_by,updated_by) VALUES ('"
              + RELATIONSHIP
              + "','"
              + TENANT_A
              + "','SYNTHETIC','SYN001','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship_version("
              + "id,tenant_id,payroll_relationship_id,"
              + "legal_entity_version_id,version_sequence,relationship_start,"
              + "approval_status,approved_at,approved_by,"
              + "created_by,updated_by) VALUES ('"
              + RELATIONSHIP_VERSION
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP
              + "','"
              + LEGAL_VERSION
              + "',1,'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.employee_payroll_profile("
              + "id,tenant_id,payroll_relationship_id,currency,"
              + "payroll_status,created_by,updated_by) VALUES ('"
              + PAYROLL_PROFILE
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP
              + "','INR','INCOMPLETE','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment("
              + "id,tenant_id,payroll_relationship_id,assignment_number,"
              + "created_by,updated_by) VALUES ('"
              + ASSIGNMENT
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP
              + "','ASN001','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment_version("
              + "id,tenant_id,payroll_assignment_id,"
              + "payroll_relationship_version_id,establishment_version_id,"
              + "version_sequence,assignment_start,approval_status,"
              + "approved_at,approved_by,created_by,updated_by) VALUES ('"
              + ASSIGNMENT_VERSION
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT
              + "','"
              + RELATIONSHIP_VERSION
              + "','"
              + ESTABLISHMENT_VERSION
              + "',1,'2026-01-01','APPROVED',clock_timestamp(),"
              + "'test','test','test')");
      statement.execute(
          "INSERT INTO employee_payroll.pay_group_assignment("
              + "id,tenant_id,payroll_assignment_version_id,"
              + "pay_group_version_id,effective_from,approval_status,"
              + "created_by,updated_by) VALUES ('"
              + GROUP_ASSIGNMENT
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT_VERSION
              + "','"
              + PAY_GROUP_VERSION
              + "','2026-01-01','DRAFT','test','test')");
      statement.execute(
          "SELECT employee_payroll.approve_pay_group_assignment('"
              + TENANT_A
              + "','"
              + GROUP_ASSIGNMENT
              + "','test',clock_timestamp())");
      statement.execute(
          "INSERT INTO employee_payroll.salary_assignment("
              + "id,tenant_id,payroll_assignment_version_id,"
              + "salary_structure_version_id,monthly_amount,currency,"
              + "effective_from,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + SALARY_ASSIGNMENT
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT_VERSION
              + "','"
              + STRUCTURE_VERSION
              + "',90000,'INR','2026-01-01','DRAFT','test','test')");
      statement.execute(
          "SELECT employee_payroll.approve_salary_assignment('"
              + TENANT_A
              + "','"
              + SALARY_ASSIGNMENT
              + "','test',clock_timestamp())");
      statement.execute(
          "SELECT employee_payroll.update_employee_payroll_profile_status('"
              + TENANT_A
              + "','"
              + PAYROLL_PROFILE
              + "','READY',0,'test',clock_timestamp())");
    }
  }

  private static void seedStatutoryConfiguration() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      insertRule(
          connection,
          SOCIAL_RULE,
          "SOCIAL",
          "SOCIAL_INSURANCE");
      insertRuleVersion(connection, SOCIAL_VERSION, SOCIAL_RULE);
      execute(
          connection,
          "INSERT INTO statutory.statutory_rule_portion("
              + "id,tenant_id,statutory_rule_version_id,liable_party,"
              + "sequence_no,calculation_method,assessment_base_code,"
              + "rate_percent,created_by,updated_by) VALUES ('"
              + SOCIAL_EMPLOYEE_PORTION
              + "','"
              + TENANT_A
              + "','"
              + SOCIAL_VERSION
              + "','EMPLOYEE',1,'PERCENTAGE','GROSS',10,'test','test')");
      execute(
          connection,
          "INSERT INTO statutory.statutory_rule_portion("
              + "id,tenant_id,statutory_rule_version_id,liable_party,"
              + "sequence_no,calculation_method,fixed_amount,"
              + "created_by,updated_by) VALUES ('"
              + SOCIAL_EMPLOYER_PORTION
              + "','"
              + TENANT_A
              + "','"
              + SOCIAL_VERSION
              + "','EMPLOYER',2,'FIXED',500,'test','test')");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory.approve_statutory_rule_version('"
                      + TENANT_A
                      + "','"
                      + SOCIAL_VERSION
                      + "','test',clock_timestamp())"))
          .isOne();

      insertRule(connection, TAX_RULE, "INCOME_TAX", "INCOME_TAX");
      insertRuleVersion(connection, TAX_VERSION, TAX_RULE);
      execute(
          connection,
          "INSERT INTO statutory.statutory_rule_portion("
              + "id,tenant_id,statutory_rule_version_id,liable_party,"
              + "sequence_no,calculation_method,assessment_base_code,"
              + "created_by,updated_by) VALUES ('"
              + TAX_PORTION
              + "','"
              + TENANT_A
              + "','"
              + TAX_VERSION
              + "','EMPLOYEE',1,'SLAB','GROSS','test','test')");
      execute(
          connection,
          "INSERT INTO statutory.statutory_rule_slab("
              + "id,tenant_id,statutory_rule_version_id,"
              + "statutory_rule_portion_id,sequence_no,lower_bound,"
              + "upper_bound,fixed_amount,rate_percent,"
              + "created_by,updated_by) VALUES ('"
              + TAX_SLAB_ONE
              + "','"
              + TENANT_A
              + "','"
              + TAX_VERSION
              + "','"
              + TAX_PORTION
              + "',1,0,50000,0,0,'test','test')");
      execute(
          connection,
          "INSERT INTO statutory.statutory_rule_slab("
              + "id,tenant_id,statutory_rule_version_id,"
              + "statutory_rule_portion_id,sequence_no,lower_bound,"
              + "upper_bound,fixed_amount,rate_percent,"
              + "created_by,updated_by) VALUES ('"
              + TAX_SLAB_TWO
              + "','"
              + TENANT_A
              + "','"
              + TAX_VERSION
              + "','"
              + TAX_PORTION
              + "',2,50000,NULL,0,20,'test','test')");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory.approve_statutory_rule_version('"
                      + TENANT_A
                      + "','"
                      + TAX_VERSION
                      + "','test',clock_timestamp())"))
          .isOne();

      execute(
          connection,
          "INSERT INTO statutory.employee_statutory_profile("
              + "id,tenant_id,payroll_relationship_id,jurisdiction_code,"
              + "authority_code,created_by,updated_by) VALUES ('"
              + STATUTORY_PROFILE
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP
              + "','IN','CENTRAL','test','test')");
      execute(
          connection,
          "INSERT INTO statutory.employee_statutory_profile_version("
              + "id,tenant_id,employee_statutory_profile_id,"
              + "version_sequence,effective_from,registration_status,"
              + "classification_code,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + STATUTORY_PROFILE_VERSION
              + "','"
              + TENANT_A
              + "','"
              + STATUTORY_PROFILE
              + "',1,'2026-01-01','REGISTERED','STANDARD','DRAFT',"
              + "'test','test')");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "approve_employee_statutory_profile_version('"
                      + TENANT_A
                      + "','"
                      + STATUTORY_PROFILE_VERSION
                      + "','test',clock_timestamp())"))
          .isOne();
      insertRuleAssignment(
          connection,
          SOCIAL_ASSIGNMENT,
          SOCIAL_RULE,
          SOCIAL_VERSION);
      insertRuleAssignment(connection, TAX_ASSIGNMENT, TAX_RULE, TAX_VERSION);

      execute(
          connection,
          "INSERT INTO statutory.statutory_component_classification("
              + "id,tenant_id,jurisdiction_code,authority_code,"
              + "assessment_base_code,component_id,component_version_id,"
              + "classification_sequence,inclusion_percent,effective_from,"
              + "approval_status,created_by,updated_by) VALUES ('"
              + CLASSIFICATION
              + "','"
              + TENANT_A
              + "','IN','CENTRAL','GROSS','"
              + COMPONENT
              + "','"
              + COMPONENT_VERSION
              + "',1,100,'2026-01-01','DRAFT','test','test')");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory."
                      + "approve_statutory_component_classification('"
                      + TENANT_A
                      + "','"
                      + CLASSIFICATION
                      + "','test',clock_timestamp())"))
          .isOne();
      connection.commit();
    }
  }

  private static void calculatePayroll() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      cycleId =
          queryString(
              connection,
              "SELECT payroll_ops.create_regular_payroll_cycle('"
                  + TENANT_A
                  + "','"
                  + PAY_GROUP_VERSION
                  + "','"
                  + PERIOD
                  + "','test','2026-07-31T09:00:00Z')::text");
      long resolvedVersion =
          queryLong(
              connection,
              "SELECT cycle_version_no FROM "
                  + "payroll_ops.resolve_payroll_population('"
                  + TENANT_A
                  + "','"
                  + cycleId
                  + "',0,'test','2026-07-31T09:01:00Z')");
      long sealedVersion =
          queryLong(
              connection,
              "SELECT cycle_version_no FROM payroll_ops.seal_payroll_inputs('"
                  + TENANT_A
                  + "','"
                  + cycleId
                  + "',"
                  + resolvedVersion
                  + ",'test','2026-07-31T09:02:00Z')");

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT calculation_request_id::text,cycle_version_no "
                      + "FROM payroll_calc.calculate_sealed_payroll('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "',"
                      + sealedVersion
                      + ",'payroll-calculate-001','"
                      + "d".repeat(64)
                      + "','test','2026-07-31T09:03:00Z')")) {
        assertThat(result.next()).isTrue();
        calculationRequestId = result.getString(1);
        calculatedCycleVersion = result.getLong(2);
      }
      connection.commit();
    }
  }

  private static void evaluateStatutory() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT evaluation_request_id::text,"
                      + "payroll_result_count,statutory_result_count,"
                      + "employee_total,employer_total,"
                      + "post_statutory_net_total "
                      + "FROM statutory.evaluate_calculated_payroll('"
                      + TENANT_A
                      + "','"
                      + cycleId
                      + "','"
                      + calculationRequestId
                      + "',"
                      + calculatedCycleVersion
                      + ",'statutory-evaluation-001','"
                      + "e".repeat(64)
                      + "','test','2026-07-31T09:04:00Z')")) {
        assertThat(result.next()).isTrue();
        evaluationRequestId = result.getString(1);
        assertThat(result.getInt(2)).isOne();
        assertThat(result.getInt(3)).isEqualTo(2);
        assertThat(result.getBigDecimal(4)).isEqualByComparingTo("17000");
        assertThat(result.getBigDecimal(5)).isEqualByComparingTo("500");
        assertThat(result.getBigDecimal(6)).isEqualByComparingTo("73000");
      }
      connection.commit();
    }
  }

  private static void seedBalanceYear() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      execute(
          connection,
          "INSERT INTO statutory.statutory_balance_year("
              + "id,tenant_id,jurisdiction_code,authority_code,"
              + "balance_year_code,version_sequence,period_start,"
              + "period_end,approval_status,created_by,updated_by) "
              + "VALUES ('"
              + BALANCE_YEAR
              + "','"
              + TENANT_A
              + "','IN','CENTRAL','IN_CENTRAL_2026',1,"
              + "'2026-01-01','2027-01-01','DRAFT','test','test')");
      assertThat(
              queryLong(
                  connection,
                  "SELECT statutory.approve_statutory_balance_year('"
                      + TENANT_A
                      + "','"
                      + BALANCE_YEAR
                      + "','test',clock_timestamp())"))
          .isOne();
      connection.commit();
    }
  }

  private static void postStatutoryEvaluation() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      try (Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT ledger_batch_id::text,posted_entry_count,"
                      + "employee_delta_total,employer_delta_total,"
                      + "cycle_employee_total,cycle_employer_total,"
                      + "cycle_version_no "
                      + "FROM statutory.post_statutory_evaluation('"
                      + TENANT_A
                      + "','"
                      + evaluationRequestId
                      + "',"
                      + calculatedCycleVersion
                      + ",'statutory-posting-001','"
                      + "1".repeat(64)
                      + "','test','2026-07-31T09:05:00Z')")) {
        assertThat(result.next()).isTrue();
        ledgerBatchId = result.getString(1);
        assertThat(result.getInt(2)).isEqualTo(2);
        assertThat(result.getBigDecimal(3)).isEqualByComparingTo("17000");
        assertThat(result.getBigDecimal(4)).isEqualByComparingTo("500");
        assertThat(result.getBigDecimal(5)).isEqualByComparingTo("17000");
        assertThat(result.getBigDecimal(6)).isEqualByComparingTo("500");
        postedCycleVersion = result.getLong(7);
      }
      assertThat(postedCycleVersion).isEqualTo(calculatedCycleVersion + 1);
      connection.commit();
    }
  }

  private static void insertRule(
      Connection connection,
      String ruleId,
      String code,
      String category) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule("
            + "id,tenant_id,jurisdiction_code,authority_code,code,name,"
            + "rule_category,created_by,updated_by) VALUES ('"
            + ruleId
            + "','"
            + TENANT_A
            + "','IN','CENTRAL','"
            + code
            + "','"
            + code
            + "','"
            + category
            + "','test','test')");
  }

  private static void insertRuleVersion(
      Connection connection,
      String versionId,
      String ruleId) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.statutory_rule_version("
            + "id,tenant_id,statutory_rule_id,version_sequence,"
            + "effective_from,currency,rounding_scale,rounding_mode,"
            + "approval_status,created_by,updated_by) VALUES ('"
            + versionId
            + "','"
            + TENANT_A
            + "','"
            + ruleId
            + "',1,'2026-01-01','INR',2,'HALF_UP','DRAFT',"
            + "'test','test')");
  }

  private static void insertRuleAssignment(
      Connection connection,
      String assignmentId,
      String ruleId,
      String ruleVersionId) throws SQLException {
    execute(
        connection,
        "INSERT INTO statutory.employee_statutory_rule_assignment("
            + "id,tenant_id,employee_statutory_profile_id,"
            + "employee_statutory_profile_version_id,payroll_assignment_id,"
            + "payroll_assignment_version_id,statutory_rule_id,"
            + "statutory_rule_version_id,assignment_sequence,effective_from,"
            + "eligibility_status,exemption_status,approval_status,"
            + "created_by,updated_by) VALUES ('"
            + assignmentId
            + "','"
            + TENANT_A
            + "','"
            + STATUTORY_PROFILE
            + "','"
            + STATUTORY_PROFILE_VERSION
            + "','"
            + ASSIGNMENT
            + "','"
            + ASSIGNMENT_VERSION
            + "','"
            + ruleId
            + "','"
            + ruleVersionId
            + "',1,'2026-01-01','ELIGIBLE','NONE','DRAFT',"
            + "'test','test')");
    assertThat(
            queryLong(
                connection,
                "SELECT statutory."
                    + "approve_employee_statutory_rule_assignment('"
                    + TENANT_A
                    + "','"
                    + assignmentId
                    + "','test',clock_timestamp())"))
        .isOne();
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void setTenant(Connection connection, String tenant)
      throws SQLException {
    execute(
        connection,
        "SELECT set_config('app.tenant_id','" + tenant + "',false)");
  }

  private static long queryLong(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String queryString(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static void execute(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void assertSqlState(
      Connection connection,
      String expectedState,
      SqlAction action) throws SQLException {
    Savepoint savepoint = connection.setSavepoint();
    try {
      action.execute();
      fail("Expected SQLSTATE " + expectedState);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(expectedState);
    } finally {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    }
  }

  @FunctionalInterface
  private interface SqlAction {
    void execute() throws SQLException;
  }
}
