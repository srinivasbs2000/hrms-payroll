package com.acme.hrms.payroll.statutory.internal.infrastructure;

import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.statutory.StatutoryBalanceSnapshotView;
import com.acme.hrms.payroll.statutory.StatutoryCorrectionExecution;
import com.acme.hrms.payroll.statutory.StatutoryEvaluationExecution;
import com.acme.hrms.payroll.statutory.StatutoryEvaluationRequestView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerBatchView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerEntryView;
import com.acme.hrms.payroll.statutory.StatutoryLedgerPostingExecution;
import com.acme.hrms.payroll.statutory.StatutoryReconciliationView;
import com.acme.hrms.payroll.statutory.StatutoryRemittanceSummaryView;
import com.acme.hrms.payroll.statutory.StatutoryResultView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class StatutoryRepository {
  private final JdbcTemplate jdbc;

  public StatutoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public StatutoryEvaluationExecution evaluate(
      UUID cycleId,
      UUID calculationRequestId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String actor,
      Instant evaluatedAt) {
    try {
      EvaluationFunctionResult value = jdbc.query(
              """
              select evaluation_request_id,
                     payroll_result_count,
                     statutory_result_count,
                     employee_total,
                     employer_total,
                     post_statutory_net_total,
                     evidence_set_hash
              from statutory.evaluate_calculated_payroll(
                ?,?,?,?,?,?,?,?
              )
              """,
              (result, row) -> new EvaluationFunctionResult(
                  result.getObject("evaluation_request_id", UUID.class),
                  result.getInt("payroll_result_count"),
                  result.getInt("statutory_result_count"),
                  result.getBigDecimal("employee_total"),
                  result.getBigDecimal("employer_total"),
                  result.getBigDecimal("post_statutory_net_total"),
                  result.getString("evidence_set_hash")),
              TenantContext.require(),
              cycleId,
              calculationRequestId,
              expectedVersion,
              idempotencyKey,
              requestHash,
              actor,
              Timestamp.from(evaluatedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Statutory evaluation returned no result"));

      return jdbc.query(
              """
              select completed_at,completed_by
              from statutory.statutory_evaluation_request
              where tenant_id=? and id=?
              """,
              (result, row) -> new StatutoryEvaluationExecution(
                  cycleId,
                  calculationRequestId,
                  value.evaluationRequestId(),
                  value.payrollResultCount(),
                  value.statutoryResultCount(),
                  value.employeeTotal(),
                  value.employerTotal(),
                  value.postStatutoryNetTotal(),
                  value.evidenceSetHash(),
                  expectedVersion,
                  instant(result, "completed_at"),
                  result.getString("completed_by")),
              TenantContext.require(),
              value.evaluationRequestId())
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Completed statutory evaluation could not be read"));
    } catch (DataAccessException exception) {
      throw translate("Statutory payroll could not be evaluated", exception);
    }
  }

  public StatutoryLedgerPostingExecution post(
      UUID cycleId,
      UUID evaluationRequestId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String actor,
      Instant postedAt) {
    try {
      PostingFunctionResult value = jdbc.query(
              """
              select ledger_batch_id,attempt_no,batch_kind,
                     posted_entry_count,employee_delta_total,
                     employer_delta_total,cycle_employee_total,
                     cycle_employer_total,ledger_set_hash,
                     cycle_version_no
              from statutory.post_statutory_evaluation(
                ?,?,?,?,?,?,?
              )
              """,
              (result, row) -> new PostingFunctionResult(
                  result.getObject("ledger_batch_id", UUID.class),
                  result.getInt("attempt_no"),
                  result.getString("batch_kind"),
                  result.getInt("posted_entry_count"),
                  result.getBigDecimal("employee_delta_total"),
                  result.getBigDecimal("employer_delta_total"),
                  result.getBigDecimal("cycle_employee_total"),
                  result.getBigDecimal("cycle_employer_total"),
                  result.getString("ledger_set_hash"),
                  result.getLong("cycle_version_no")),
              TenantContext.require(),
              evaluationRequestId,
              expectedVersion,
              idempotencyKey,
              requestHash,
              actor,
              Timestamp.from(postedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Statutory ledger posting returned no result"));

      return completedPosting(cycleId, evaluationRequestId, value);
    } catch (DataAccessException exception) {
      throw translate("Statutory evaluation could not be posted", exception);
    }
  }

  public StatutoryCorrectionExecution correct(
      UUID cycleId,
      UUID statutoryResultId,
      BigDecimal employeeDelta,
      BigDecimal employerDelta,
      String reason,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String actor,
      Instant postedAt) {
    try {
      CorrectionFunctionResult value = jdbc.query(
              """
              select ledger_batch_id,attempt_no,posted_entry_count,
                     employee_delta_total,employer_delta_total,
                     cycle_employee_total,cycle_employer_total,
                     ledger_set_hash,cycle_version_no
              from statutory.post_statutory_correction(
                ?,?,?,?,?,?,?,?,?,?,?
              )
              """,
              (result, row) -> new CorrectionFunctionResult(
                  result.getObject("ledger_batch_id", UUID.class),
                  result.getInt("attempt_no"),
                  result.getInt("posted_entry_count"),
                  result.getBigDecimal("employee_delta_total"),
                  result.getBigDecimal("employer_delta_total"),
                  result.getBigDecimal("cycle_employee_total"),
                  result.getBigDecimal("cycle_employer_total"),
                  result.getString("ledger_set_hash"),
                  result.getLong("cycle_version_no")),
              TenantContext.require(),
              cycleId,
              statutoryResultId,
              employeeDelta,
              employerDelta,
              reason,
              expectedVersion,
              idempotencyKey,
              requestHash,
              actor,
              Timestamp.from(postedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Statutory correction returned no result"));

      return jdbc.query(
              """
              select completed_at,completed_by
              from statutory.statutory_ledger_batch
              where tenant_id=? and id=?
              """,
              (result, row) -> new StatutoryCorrectionExecution(
                  cycleId,
                  statutoryResultId,
                  value.ledgerBatchId(),
                  value.attemptNo(),
                  value.postedEntryCount(),
                  value.employeeDeltaTotal(),
                  value.employerDeltaTotal(),
                  value.cycleEmployeeTotal(),
                  value.cycleEmployerTotal(),
                  value.ledgerSetHash(),
                  value.cycleVersionNo(),
                  instant(result, "completed_at"),
                  result.getString("completed_by")),
              TenantContext.require(),
              value.ledgerBatchId())
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Completed statutory correction could not be read"));
    } catch (DataAccessException exception) {
      throw translate("Statutory correction could not be posted", exception);
    }
  }

  public void requireCycle(UUID cycleId) {
    Integer found = jdbc.query(
            """
            select 1
            from payroll_ops.payroll_cycle
            where tenant_id=? and id=?
            """,
            (result, row) -> result.getInt(1),
            TenantContext.require(),
            cycleId)
        .stream()
        .findFirst()
        .orElse(null);
    if (found == null) {
      throw new ResourceNotFoundException("Payroll cycle was not found");
    }
  }

  public boolean eventExists(
      String aggregateType, UUID aggregateId, String eventType) {
    Long count = jdbc.queryForObject(
        """
        select count(*)
        from integration.outbox_event
        where tenant_id=?
          and aggregate_type=?
          and aggregate_id=?
          and event_type=?
        """,
        Long.class,
        TenantContext.require(),
        aggregateType,
        aggregateId,
        eventType);
    return count != null && count > 0;
  }

  public List<StatutoryEvaluationRequestView> evaluations(UUID cycleId) {
    return jdbc.query(
        """
        select id,payroll_cycle_id,calculation_request_id,status,
               engine_version,expected_cycle_version,
               calculation_result_set_hash,started_at,completed_at,
               completed_by,payroll_result_count,statutory_result_count,
               employee_total,employer_total,post_statutory_net_total,
               evidence_set_hash,version_no
        from statutory.statutory_evaluation_request
        where tenant_id=? and payroll_cycle_id=?
        order by started_at desc,id desc
        """,
        (result, row) -> new StatutoryEvaluationRequestView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("calculation_request_id", UUID.class),
            result.getString("status"),
            result.getString("engine_version"),
            result.getLong("expected_cycle_version"),
            result.getString("calculation_result_set_hash"),
            instant(result, "started_at"),
            instant(result, "completed_at"),
            result.getString("completed_by"),
            result.getObject("payroll_result_count", Integer.class),
            result.getObject("statutory_result_count", Integer.class),
            result.getBigDecimal("employee_total"),
            result.getBigDecimal("employer_total"),
            result.getBigDecimal("post_statutory_net_total"),
            result.getString("evidence_set_hash"),
            result.getLong("version_no")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryResultView> results(UUID cycleId) {
    return jdbc.query(
        """
        select statutory_result.id,
               statutory_result.evaluation_request_id,
               statutory_result.payroll_result_id,
               statutory_result.statutory_input_snapshot_id,
               statutory_snapshot.employee_statutory_profile_id,
               statutory_result.employee_statutory_rule_assignment_id,
               statutory_snapshot.statutory_rule_id,
               statutory_result.statutory_rule_version_id,
               statutory_result.currency::text currency,
               statutory_result.employee_amount,
               statutory_result.employer_amount,
               statutory_result.result_hash,
               statutory_result.created_at
        from statutory.statutory_result statutory_result
        join statutory.statutory_evaluation_request evaluation
          on evaluation.tenant_id=statutory_result.tenant_id
         and evaluation.id=statutory_result.evaluation_request_id
        join statutory.statutory_input_snapshot statutory_snapshot
          on statutory_snapshot.tenant_id=statutory_result.tenant_id
         and statutory_snapshot.id=
             statutory_result.statutory_input_snapshot_id
        where statutory_result.tenant_id=?
          and evaluation.payroll_cycle_id=?
        order by evaluation.completed_at desc,
                 statutory_snapshot.employee_statutory_profile_id,
                 statutory_snapshot.statutory_rule_id,
                 statutory_result.id
        """,
        (result, row) -> new StatutoryResultView(
            result.getObject("id", UUID.class),
            result.getObject("evaluation_request_id", UUID.class),
            result.getObject("payroll_result_id", UUID.class),
            result.getObject("statutory_input_snapshot_id", UUID.class),
            result.getObject("employee_statutory_profile_id", UUID.class),
            result.getObject(
                "employee_statutory_rule_assignment_id", UUID.class),
            result.getObject("statutory_rule_id", UUID.class),
            result.getObject("statutory_rule_version_id", UUID.class),
            result.getString("currency"),
            result.getBigDecimal("employee_amount"),
            result.getBigDecimal("employer_amount"),
            result.getString("result_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryLedgerBatchView> ledgerBatches(UUID cycleId) {
    return jdbc.query(
        """
        select id,payroll_cycle_id,pay_period_id,evaluation_request_id,
               calculation_request_id,batch_kind,attempt_no,
               supersedes_batch_id,status,posted_at,posted_by,
               completed_at,completed_by,entry_count,
               balance_snapshot_count,remittance_summary_count,
               employee_delta_total,employer_delta_total,
               cycle_employee_total,cycle_employer_total,
               ledger_set_hash,reconciliation_hash,version_no
        from statutory.statutory_ledger_batch
        where tenant_id=? and payroll_cycle_id=?
        order by attempt_no desc,posted_at desc,id desc
        """,
        (result, row) -> new StatutoryLedgerBatchView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("pay_period_id", UUID.class),
            result.getObject("evaluation_request_id", UUID.class),
            result.getObject("calculation_request_id", UUID.class),
            result.getString("batch_kind"),
            result.getInt("attempt_no"),
            result.getObject("supersedes_batch_id", UUID.class),
            result.getString("status"),
            instant(result, "posted_at"),
            result.getString("posted_by"),
            instant(result, "completed_at"),
            result.getString("completed_by"),
            result.getObject("entry_count", Integer.class),
            result.getObject("balance_snapshot_count", Integer.class),
            result.getObject("remittance_summary_count", Integer.class),
            result.getBigDecimal("employee_delta_total"),
            result.getBigDecimal("employer_delta_total"),
            result.getBigDecimal("cycle_employee_total"),
            result.getBigDecimal("cycle_employer_total"),
            result.getString("ledger_set_hash"),
            result.getString("reconciliation_hash"),
            result.getLong("version_no")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryLedgerEntryView> ledgerEntries(UUID cycleId) {
    return jdbc.query(
        """
        select id,ledger_batch_id,payroll_cycle_id,pay_period_id,
               evaluation_request_id,source_evaluation_request_id,
               statutory_result_id,employee_statutory_profile_id,
               statutory_rule_id,statutory_rule_version_id,balance_year_id,
               jurisdiction_code,authority_code,sequence_no,entry_kind,
               source_entry_id,currency::text currency,
               employee_amount_delta,employer_amount_delta,
               reason_code,reason_detail,entry_hash,created_at
        from statutory.statutory_ledger_entry
        where tenant_id=? and payroll_cycle_id=?
        order by created_at,ledger_batch_id,sequence_no,id
        """,
        (result, row) -> new StatutoryLedgerEntryView(
            result.getObject("id", UUID.class),
            result.getObject("ledger_batch_id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("pay_period_id", UUID.class),
            result.getObject("evaluation_request_id", UUID.class),
            result.getObject("source_evaluation_request_id", UUID.class),
            result.getObject("statutory_result_id", UUID.class),
            result.getObject("employee_statutory_profile_id", UUID.class),
            result.getObject("statutory_rule_id", UUID.class),
            result.getObject("statutory_rule_version_id", UUID.class),
            result.getObject("balance_year_id", UUID.class),
            result.getString("jurisdiction_code"),
            result.getString("authority_code"),
            result.getInt("sequence_no"),
            result.getString("entry_kind"),
            result.getObject("source_entry_id", UUID.class),
            result.getString("currency"),
            result.getBigDecimal("employee_amount_delta"),
            result.getBigDecimal("employer_amount_delta"),
            result.getString("reason_code"),
            result.getString("reason_detail"),
            result.getString("entry_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryBalanceSnapshotView> balances(UUID cycleId) {
    return jdbc.query(
        """
        select id,ledger_batch_id,payroll_cycle_id,pay_period_id,
               employee_statutory_profile_id,statutory_rule_id,
               statutory_rule_version_id,balance_year_id,
               jurisdiction_code,authority_code,currency::text currency,
               period_employee_amount,period_employer_amount,
               cycle_employee_amount,cycle_employer_amount,
               year_employee_amount,year_employer_amount,
               snapshot_hash,created_at
        from statutory.statutory_balance_snapshot
        where tenant_id=? and payroll_cycle_id=?
        order by created_at desc,employee_statutory_profile_id,
                 statutory_rule_id,id
        """,
        (result, row) -> new StatutoryBalanceSnapshotView(
            result.getObject("id", UUID.class),
            result.getObject("ledger_batch_id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("pay_period_id", UUID.class),
            result.getObject("employee_statutory_profile_id", UUID.class),
            result.getObject("statutory_rule_id", UUID.class),
            result.getObject("statutory_rule_version_id", UUID.class),
            result.getObject("balance_year_id", UUID.class),
            result.getString("jurisdiction_code"),
            result.getString("authority_code"),
            result.getString("currency"),
            result.getBigDecimal("period_employee_amount"),
            result.getBigDecimal("period_employer_amount"),
            result.getBigDecimal("cycle_employee_amount"),
            result.getBigDecimal("cycle_employer_amount"),
            result.getBigDecimal("year_employee_amount"),
            result.getBigDecimal("year_employer_amount"),
            result.getString("snapshot_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryReconciliationView> reconciliations(UUID cycleId) {
    return jdbc.query(
        """
        select id,ledger_batch_id,payroll_cycle_id,evaluation_request_id,
               currency::text currency,source_employee_total,
               source_employer_total,correction_employee_total,
               correction_employer_total,expected_employee_total,
               expected_employer_total,ledger_employee_total,
               ledger_employer_total,employee_variance,
               employer_variance,reconciliation_status,
               reconciliation_hash,created_at
        from statutory.statutory_reconciliation
        where tenant_id=? and payroll_cycle_id=?
        order by created_at desc,id desc
        """,
        (result, row) -> new StatutoryReconciliationView(
            result.getObject("id", UUID.class),
            result.getObject("ledger_batch_id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("evaluation_request_id", UUID.class),
            result.getString("currency"),
            result.getBigDecimal("source_employee_total"),
            result.getBigDecimal("source_employer_total"),
            result.getBigDecimal("correction_employee_total"),
            result.getBigDecimal("correction_employer_total"),
            result.getBigDecimal("expected_employee_total"),
            result.getBigDecimal("expected_employer_total"),
            result.getBigDecimal("ledger_employee_total"),
            result.getBigDecimal("ledger_employer_total"),
            result.getBigDecimal("employee_variance"),
            result.getBigDecimal("employer_variance"),
            result.getString("reconciliation_status"),
            result.getString("reconciliation_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId);
  }

  public List<StatutoryRemittanceSummaryView> remittances(UUID cycleId) {
    return jdbc.query(
        """
        select id,ledger_batch_id,payroll_cycle_id,pay_period_id,
               balance_year_id,jurisdiction_code,authority_code,
               statutory_rule_id,statutory_rule_version_id,
               currency::text currency,batch_employee_delta,
               batch_employer_delta,period_employee_total,
               period_employer_total,year_employee_total,
               year_employer_total,remittance_amount,
               remittance_position,summary_hash,created_at
        from statutory.statutory_remittance_summary
        where tenant_id=? and payroll_cycle_id=?
        order by created_at desc,jurisdiction_code,authority_code,
                 statutory_rule_id,id
        """,
        (result, row) -> new StatutoryRemittanceSummaryView(
            result.getObject("id", UUID.class),
            result.getObject("ledger_batch_id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getObject("pay_period_id", UUID.class),
            result.getObject("balance_year_id", UUID.class),
            result.getString("jurisdiction_code"),
            result.getString("authority_code"),
            result.getObject("statutory_rule_id", UUID.class),
            result.getObject("statutory_rule_version_id", UUID.class),
            result.getString("currency"),
            result.getBigDecimal("batch_employee_delta"),
            result.getBigDecimal("batch_employer_delta"),
            result.getBigDecimal("period_employee_total"),
            result.getBigDecimal("period_employer_total"),
            result.getBigDecimal("year_employee_total"),
            result.getBigDecimal("year_employer_total"),
            result.getBigDecimal("remittance_amount"),
            result.getString("remittance_position"),
            result.getString("summary_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId);
  }

  private StatutoryLedgerPostingExecution completedPosting(
      UUID cycleId,
      UUID evaluationRequestId,
      PostingFunctionResult value) {
    return jdbc.query(
            """
            select completed_at,completed_by
            from statutory.statutory_ledger_batch
            where tenant_id=? and id=?
            """,
            (result, row) -> new StatutoryLedgerPostingExecution(
                cycleId,
                evaluationRequestId,
                value.ledgerBatchId(),
                value.attemptNo(),
                value.batchKind(),
                value.postedEntryCount(),
                value.employeeDeltaTotal(),
                value.employerDeltaTotal(),
                value.cycleEmployeeTotal(),
                value.cycleEmployerTotal(),
                value.ledgerSetHash(),
                value.cycleVersionNo(),
                instant(result, "completed_at"),
                result.getString("completed_by")),
            TenantContext.require(),
            value.ledgerBatchId())
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Completed statutory ledger posting could not be read"));
  }

  private static Instant instant(ResultSet result, String column)
      throws SQLException {
    Timestamp timestamp = result.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private RuntimeException translate(
      String operation, DataAccessException exception) {
    SQLException sql = sqlException(exception);
    if (sql == null || sql.getSQLState() == null) {
      return exception;
    }
    String message = databaseMessage(operation, sql);
    return switch (sql.getSQLState()) {
      case "23505", "40001" ->
          new ConflictException(message, exception);
      case "23503" -> new ResourceNotFoundException(message);
      case "23514" ->
          new IllegalArgumentException(message, exception);
      case "42501" ->
          new AccessDeniedException(message, exception);
      default -> exception;
    };
  }

  private SQLException sqlException(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof SQLException sql) {
        return sql;
      }
      current = current.getCause();
    }
    return null;
  }

  private String databaseMessage(
      String fallback, SQLException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return fallback;
    }
    int lineBreak = message.indexOf('\n');
    return lineBreak < 0 ? message : message.substring(0, lineBreak);
  }

  private record EvaluationFunctionResult(
      UUID evaluationRequestId,
      int payrollResultCount,
      int statutoryResultCount,
      BigDecimal employeeTotal,
      BigDecimal employerTotal,
      BigDecimal postStatutoryNetTotal,
      String evidenceSetHash) {}

  private record PostingFunctionResult(
      UUID ledgerBatchId,
      int attemptNo,
      String batchKind,
      int postedEntryCount,
      BigDecimal employeeDeltaTotal,
      BigDecimal employerDeltaTotal,
      BigDecimal cycleEmployeeTotal,
      BigDecimal cycleEmployerTotal,
      String ledgerSetHash,
      long cycleVersionNo) {}

  private record CorrectionFunctionResult(
      UUID ledgerBatchId,
      int attemptNo,
      int postedEntryCount,
      BigDecimal employeeDeltaTotal,
      BigDecimal employerDeltaTotal,
      BigDecimal cycleEmployeeTotal,
      BigDecimal cycleEmployerTotal,
      String ledgerSetHash,
      long cycleVersionNo) {}
}
