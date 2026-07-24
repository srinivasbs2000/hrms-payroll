package com.acme.hrms.payroll.calculation.internal.infrastructure;

import com.acme.hrms.payroll.calculation.PayrollCalculationRequestView;
import com.acme.hrms.payroll.calculation.PayrollCalculationResult;
import com.acme.hrms.payroll.calculation.PayrollCalculationTraceView;
import com.acme.hrms.payroll.calculation.PayrollComponentResultView;
import com.acme.hrms.payroll.calculation.PayrollRecalculationResult;
import com.acme.hrms.payroll.calculation.PayrollResultDetailView;
import com.acme.hrms.payroll.calculation.PayrollResultSummaryView;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PayrollCalculationRepository {
  private static final String RESULT_SELECT = """
      select result.id,
             result.calculation_request_id,
             result.payroll_cycle_id,
             result.payroll_assignment_version_id,
             assignment_identity.assignment_number,
             relationship_identity.employee_number,
             result.input_snapshot_id,
             result.result_status::text result_status,
             result.currency::text currency,
             result.gross_amount,
             result.deduction_amount,
             result.net_amount,
             result.component_count,
             result.result_hash,
             result.calculated_at,
             result.result_schema_version,
             result.input_snapshot_hash,
             result.salary_structure_version_id,
             result.result_payload::text result_payload
      from payroll_calc.payroll_result result
      join payroll_ops.input_snapshot snapshot
        on snapshot.tenant_id = result.tenant_id
       and snapshot.id = result.input_snapshot_id
      join employee_payroll.payroll_assignment_version assignment_version
        on assignment_version.tenant_id = result.tenant_id
       and assignment_version.id = result.payroll_assignment_version_id
      join employee_payroll.payroll_assignment assignment_identity
        on assignment_identity.tenant_id = assignment_version.tenant_id
       and assignment_identity.id = assignment_version.payroll_assignment_id
      join employee_payroll.payroll_relationship_version relationship_version
        on relationship_version.tenant_id = snapshot.tenant_id
       and relationship_version.id = snapshot.payroll_relationship_version_id
      join employee_payroll.payroll_relationship relationship_identity
        on relationship_identity.tenant_id = relationship_version.tenant_id
       and relationship_identity.id =
           relationship_version.payroll_relationship_id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public PayrollCalculationRepository(
      JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public PayrollCalculationResult calculate(
      UUID cycleId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String actor,
      Instant calculatedAt) {
    try {
      CalculationFunctionResult functionResult = jdbc.query(
              """
              select calculation_request_id,
                     result_count,
                     gross_total,
                     deduction_total,
                     net_total,
                     result_set_hash,
                     cycle_version_no
              from payroll_calc.calculate_sealed_payroll(?,?,?,?,?,?,?)
              """,
              (result, row) -> new CalculationFunctionResult(
                  result.getObject("calculation_request_id", UUID.class),
                  result.getInt("result_count"),
                  result.getBigDecimal("gross_total"),
                  result.getBigDecimal("deduction_total"),
                  result.getBigDecimal("net_total"),
                  result.getString("result_set_hash"),
                  result.getLong("cycle_version_no")),
              TenantContext.require(),
              cycleId,
              expectedVersion,
              idempotencyKey,
              requestHash,
              actor,
              Timestamp.from(calculatedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Payroll calculation returned no result"));

      return jdbc.query(
              """
              select completed_at,completed_by
              from payroll_calc.calculation_request
              where tenant_id=? and id=?
              """,
              (result, row) -> new PayrollCalculationResult(
                  cycleId,
                  functionResult.requestId(),
                  functionResult.resultCount(),
                  functionResult.grossTotal(),
                  functionResult.deductionTotal(),
                  functionResult.netTotal(),
                  functionResult.resultSetHash(),
                  functionResult.cycleVersionNo(),
                  instant(result, "completed_at"),
                  result.getString("completed_by")),
              TenantContext.require(),
              functionResult.requestId())
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Completed calculation request could not be read"));
    } catch (DataAccessException exception) {
      throw translate("Payroll could not be calculated", exception);
    }
  }

  public PayrollRecalculationResult recalculate(
      UUID cycleId,
      long expectedVersion,
      String idempotencyKey,
      String requestHash,
      String reason,
      String actor,
      Instant recalculatedAt) {
    try {
      RecalculationFunctionResult functionResult = jdbc.query(
              """
              select calculation_request_id,
                     superseded_request_id,
                     attempt_no,
                     result_count,
                     gross_total,
                     deduction_total,
                     net_total,
                     result_set_hash,
                     cycle_version_no
              from payroll_calc.recalculate_sealed_payroll(
                ?,?,?,?,?,?,?,?
              )
              """,
              (result, row) -> new RecalculationFunctionResult(
                  result.getObject("calculation_request_id", UUID.class),
                  result.getObject("superseded_request_id", UUID.class),
                  result.getInt("attempt_no"),
                  result.getInt("result_count"),
                  result.getBigDecimal("gross_total"),
                  result.getBigDecimal("deduction_total"),
                  result.getBigDecimal("net_total"),
                  result.getString("result_set_hash"),
                  result.getLong("cycle_version_no")),
              TenantContext.require(),
              cycleId,
              expectedVersion,
              idempotencyKey,
              requestHash,
              reason,
              actor,
              Timestamp.from(recalculatedAt))
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Payroll recalculation returned no result"));

      return jdbc.query(
              """
              select completed_at,completed_by
              from payroll_calc.calculation_request
              where tenant_id=? and id=?
              """,
              (result, row) -> new PayrollRecalculationResult(
                  cycleId,
                  functionResult.requestId(),
                  functionResult.supersededRequestId(),
                  functionResult.attemptNo(),
                  functionResult.resultCount(),
                  functionResult.grossTotal(),
                  functionResult.deductionTotal(),
                  functionResult.netTotal(),
                  functionResult.resultSetHash(),
                  functionResult.cycleVersionNo(),
                  instant(result, "completed_at"),
                  result.getString("completed_by")),
              TenantContext.require(),
              functionResult.requestId())
          .stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Completed recalculation request could not be read"));
    } catch (DataAccessException exception) {
      throw translate("Payroll could not be recalculated", exception);
    }
  }

  public CalculationCycleState cycle(UUID cycleId) {
    return jdbc.query(
            """
            select id,status::text status,
                   active_calculation_request_id,
                   calculated_at,calculated_by,
                   calculation_result_count,
                   calculation_result_set_hash,
                   gross_total,deduction_total,net_total,
                   control_total,version_no
            from payroll_ops.payroll_cycle
            where tenant_id=? and id=?
            """,
            (result, row) -> new CalculationCycleState(
                result.getObject("id", UUID.class),
                result.getString("status"),
                result.getObject(
                    "active_calculation_request_id",
                    UUID.class),
                instant(result, "calculated_at"),
                result.getString("calculated_by"),
                result.getObject(
                    "calculation_result_count",
                    Integer.class),
                result.getString("calculation_result_set_hash"),
                result.getBigDecimal("gross_total"),
                result.getBigDecimal("deduction_total"),
                result.getBigDecimal("net_total"),
                result.getBigDecimal("control_total"),
                result.getLong("version_no")),
            TenantContext.require(),
            cycleId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll cycle was not found"));
  }

  public List<PayrollCalculationRequestView> requests(UUID cycleId) {
    return jdbc.query(
        """
        select id,payroll_cycle_id,status,
               calculation_kind,attempt_no,supersedes_request_id,
               recalculation_reason,engine_version,
               request_schema_version,expected_cycle_version,
               input_snapshot_set_hash,requested_at,started_at,
               completed_at,completed_by,completed_cycle_version,
               result_count,gross_total,deduction_total,net_total,
               result_set_hash,version_no
        from payroll_calc.calculation_request
        where tenant_id=? and payroll_cycle_id=?
        order by attempt_no desc,requested_at desc,id desc
        """,
        (result, row) -> new PayrollCalculationRequestView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_cycle_id", UUID.class),
            result.getString("status"),
            result.getString("calculation_kind"),
            result.getInt("attempt_no"),
            result.getObject("supersedes_request_id", UUID.class),
            result.getString("recalculation_reason"),
            result.getString("engine_version"),
            result.getShort("request_schema_version"),
            result.getLong("expected_cycle_version"),
            result.getString("input_snapshot_set_hash"),
            instant(result, "requested_at"),
            instant(result, "started_at"),
            instant(result, "completed_at"),
            result.getString("completed_by"),
            result.getObject("completed_cycle_version", Long.class),
            result.getObject("result_count", Integer.class),
            result.getBigDecimal("gross_total"),
            result.getBigDecimal("deduction_total"),
            result.getBigDecimal("net_total"),
            result.getString("result_set_hash"),
            result.getLong("version_no")),
        TenantContext.require(),
        cycleId);
  }

  public List<PayrollResultSummaryView> results(UUID cycleId) {
    return jdbc.query(
        RESULT_SELECT
            + " where result.tenant_id=?"
            + " and result.payroll_cycle_id=?"
            + " order by result.calculated_at desc,"
            + " result.calculation_request_id desc,"
            + " relationship_identity.employee_number,"
            + " assignment_identity.assignment_number",
        this::mapSummary,
        TenantContext.require(),
        cycleId);
  }

  public PayrollResultDetailView result(UUID cycleId, UUID resultId) {
    ResultRow row = jdbc.query(
            RESULT_SELECT
                + " where result.tenant_id=?"
                + " and result.payroll_cycle_id=?"
                + " and result.id=?",
            this::mapRow,
            TenantContext.require(),
            cycleId,
            resultId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll result was not found"));

    return new PayrollResultDetailView(
        row.id(),
        row.calculationRequestId(),
        row.cycleId(),
        row.payrollAssignmentVersionId(),
        row.assignmentNumber(),
        row.employeeNumber(),
        row.inputSnapshotId(),
        row.resultStatus(),
        row.currency(),
        row.grossAmount(),
        row.deductionAmount(),
        row.netAmount(),
        row.componentCount(),
        row.resultHash(),
        row.calculatedAt(),
        row.resultSchemaVersion(),
        row.inputSnapshotHash(),
        row.salaryStructureVersionId(),
        row.resultPayload(),
        components(resultId));
  }

  public void requireResult(UUID cycleId, UUID resultId) {
    result(cycleId, resultId);
  }

  public List<PayrollCalculationTraceView> trace(
      UUID cycleId, UUID resultId) {
    return jdbc.query(
        """
        select trace.id,
               trace.payroll_result_id,
               trace.component_result_id,
               component.component_code,
               trace.step_no,
               trace.step_type,
               trace.inputs::text inputs,
               trace.output_value,
               trace.message,
               trace.trace_schema_version,
               trace.input_snapshot_id,
               trace.component_version_id,
               trace.trace_payload::text trace_payload,
               trace.trace_hash,
               trace.created_at
        from payroll_calc.calculation_trace trace
        join payroll_calc.payroll_result result
          on result.tenant_id = trace.tenant_id
         and result.id = trace.payroll_result_id
        left join payroll_calc.component_result component
          on component.tenant_id = trace.tenant_id
         and component.id = trace.component_result_id
        where trace.tenant_id=?
          and result.payroll_cycle_id=?
          and trace.payroll_result_id=?
        order by trace.step_no,trace.id
        """,
        (result, row) -> new PayrollCalculationTraceView(
            result.getObject("id", UUID.class),
            result.getObject("payroll_result_id", UUID.class),
            result.getObject("component_result_id", UUID.class),
            result.getString("component_code"),
            result.getInt("step_no"),
            result.getString("step_type"),
            json(result, "inputs"),
            result.getBigDecimal("output_value"),
            result.getString("message"),
            result.getShort("trace_schema_version"),
            result.getObject("input_snapshot_id", UUID.class),
            result.getObject("component_version_id", UUID.class),
            json(result, "trace_payload"),
            result.getString("trace_hash"),
            instant(result, "created_at")),
        TenantContext.require(),
        cycleId,
        resultId);
  }

  private List<PayrollComponentResultView> components(UUID resultId) {
    return jdbc.query(
        """
        select id,component_code,sequence_no,
               component_type,formula_type,rounding_scale,
               unprorated_amount,proration_factor,calculated_amount,
               currency::text currency,component_version_id,
               salary_structure_line_id,salary_structure_version_id,
               component_payload::text component_payload,
               component_hash
        from payroll_calc.component_result
        where tenant_id=? and payroll_result_id=?
        order by sequence_no,id
        """,
        (result, row) -> new PayrollComponentResultView(
            result.getObject("id", UUID.class),
            result.getString("component_code"),
            result.getInt("sequence_no"),
            result.getString("component_type"),
            result.getString("formula_type"),
            result.getObject("rounding_scale", Short.class),
            result.getBigDecimal("unprorated_amount"),
            result.getBigDecimal("proration_factor"),
            result.getBigDecimal("calculated_amount"),
            result.getString("currency"),
            result.getObject("component_version_id", UUID.class),
            result.getObject("salary_structure_line_id", UUID.class),
            result.getObject("salary_structure_version_id", UUID.class),
            json(result, "component_payload"),
            result.getString("component_hash")),
        TenantContext.require(),
        resultId);
  }

  private PayrollResultSummaryView mapSummary(ResultSet result, int row)
      throws SQLException {
    ResultRow value = mapRow(result, row);
    return new PayrollResultSummaryView(
        value.id(),
        value.calculationRequestId(),
        value.cycleId(),
        value.payrollAssignmentVersionId(),
        value.assignmentNumber(),
        value.employeeNumber(),
        value.inputSnapshotId(),
        value.resultStatus(),
        value.currency(),
        value.grossAmount(),
        value.deductionAmount(),
        value.netAmount(),
        value.componentCount(),
        value.resultHash(),
        value.calculatedAt());
  }

  private ResultRow mapRow(ResultSet result, int row)
      throws SQLException {
    return new ResultRow(
        result.getObject("id", UUID.class),
        result.getObject("calculation_request_id", UUID.class),
        result.getObject("payroll_cycle_id", UUID.class),
        result.getObject(
            "payroll_assignment_version_id",
            UUID.class),
        result.getString("assignment_number"),
        result.getString("employee_number"),
        result.getObject("input_snapshot_id", UUID.class),
        result.getString("result_status"),
        result.getString("currency"),
        result.getBigDecimal("gross_amount"),
        result.getBigDecimal("deduction_amount"),
        result.getBigDecimal("net_amount"),
        result.getObject("component_count", Integer.class),
        result.getString("result_hash"),
        instant(result, "calculated_at"),
        result.getShort("result_schema_version"),
        result.getString("input_snapshot_hash"),
        result.getObject("salary_structure_version_id", UUID.class),
        json(result, "result_payload"));
  }

  private JsonNode json(ResultSet result, String column)
      throws SQLException {
    try {
      return objectMapper.readTree(result.getString(column));
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "Stored payroll calculation JSON is invalid", exception);
    }
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

  public record CalculationCycleState(
      UUID id,
      String status,
      UUID activeCalculationRequestId,
      Instant calculatedAt,
      String calculatedBy,
      Integer calculationResultCount,
      String calculationResultSetHash,
      BigDecimal grossTotal,
      BigDecimal deductionTotal,
      BigDecimal netTotal,
      BigDecimal controlTotal,
      long versionNo) {}

  private record CalculationFunctionResult(
      UUID requestId,
      int resultCount,
      BigDecimal grossTotal,
      BigDecimal deductionTotal,
      BigDecimal netTotal,
      String resultSetHash,
      long cycleVersionNo) {}

  private record RecalculationFunctionResult(
      UUID requestId,
      UUID supersededRequestId,
      int attemptNo,
      int resultCount,
      BigDecimal grossTotal,
      BigDecimal deductionTotal,
      BigDecimal netTotal,
      String resultSetHash,
      long cycleVersionNo) {}

  private record ResultRow(
      UUID id,
      UUID calculationRequestId,
      UUID cycleId,
      UUID payrollAssignmentVersionId,
      String assignmentNumber,
      String employeeNumber,
      UUID inputSnapshotId,
      String resultStatus,
      String currency,
      BigDecimal grossAmount,
      BigDecimal deductionAmount,
      BigDecimal netAmount,
      Integer componentCount,
      String resultHash,
      Instant calculatedAt,
      short resultSchemaVersion,
      String inputSnapshotHash,
      UUID salaryStructureVersionId,
      JsonNode resultPayload) {}
}
