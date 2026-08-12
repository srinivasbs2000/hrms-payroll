package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.GeneratePeriodsRequest;
import com.acme.hrms.payroll.compensation.PayPeriodOperationalView;
import com.acme.hrms.payroll.compensation.PayPeriodView;
import com.acme.hrms.payroll.compensation.PayrollCalendarOperationalView;
import com.acme.hrms.payroll.compensation.PayrollCalendarView;
import com.acme.hrms.payroll.compensation.PayrollCalendarWriteRequest;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollCalendarRepository {
  private final JdbcTemplate jdbc;

  public PayrollCalendarRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollCalendarView create(
      PayrollCalendarWriteRequest request, String actor, Instant createdAt) {
    UUID id = jdbc.queryForObject(
        """
        select organisation.create_governed_payroll_calendar(
          ?,?,?,?,?,?,?,?::smallint[],?,?
        )
        """,
        UUID.class,
        TenantContext.require(),
        request.code(),
        request.name(),
        request.resolvedFrequency(),
        request.resolvedTimezone(),
        request.customPeriodDays(),
        request.resolvedCustomFrequencyAuthorised(),
        weekendLiteral(request.resolvedWeekendIsoDays()),
        actor,
        Timestamp.from(createdAt));

    if (id == null) {
      throw new IllegalStateException("Calendar creation returned no identifier");
    }
    return calendar(id);
  }

  public List<PayrollCalendarView> list() {
    return jdbc.query(
        """
        select id,calendar_series_id,calendar_version,supersedes_calendar_id,
               code,name,frequency,timezone
        from organisation.payroll_calendar
        where tenant_id=?
        order by code,calendar_version
        """,
        this::mapCalendar,
        TenantContext.require());
  }

  public PayrollCalendarView calendar(UUID calendarId) {
    return jdbc.query(
            """
            select id,calendar_series_id,calendar_version,supersedes_calendar_id,
                   code,name,frequency,timezone
            from organisation.payroll_calendar
            where tenant_id=? and id=?
            """,
            this::mapCalendar,
            TenantContext.require(),
            calendarId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll calendar was not found"));
  }

  public List<PayPeriodView> generate(
      UUID calendarId, GeneratePeriodsRequest request, String actor, Instant generatedAt) {
    if (request.usesLegacyMonthlyMode()) {
      return jdbc.query(
          """
          select id, ?::uuid calendar_id, period_code, period_start, period_end,
                 payment_date, status
          from organisation.generate_monthly_pay_periods(?,?,?,?,?,?)
          order by period_start
          """,
          this::mapPeriod,
          calendarId, TenantContext.require(), calendarId, request.year(),
          request.resolvedPaymentDay(), actor, Timestamp.from(generatedAt));
    }

    return jdbc.query(
        """
        select id, ?::uuid calendar_id, period_code, period_start, period_end,
               payment_date, status
        from organisation.generate_pay_periods(?,?,?,?,?,?)
        order by period_start
        """,
        this::mapPeriod,
        calendarId, TenantContext.require(), calendarId, request.startDate(),
        request.periodCount(), actor, Timestamp.from(generatedAt));
  }

  public List<PayPeriodView> periods(UUID calendarId, Integer year) {
    calendar(calendarId);
    if (year == null) {
      return jdbc.query(
          """
          select id,calendar_id,period_code,period_start,period_end,payment_date,status
          from organisation.pay_period
          where tenant_id=? and calendar_id=?
          order by period_start
          """,
          this::mapPeriod, TenantContext.require(), calendarId);
    }
    return jdbc.query(
        """
        select id,calendar_id,period_code,period_start,period_end,payment_date,status
        from organisation.pay_period
        where tenant_id=? and calendar_id=? and period_start>=? and period_start<?
        order by period_start
        """,
        this::mapPeriod, TenantContext.require(), calendarId,
        Date.valueOf(year + "-01-01"), Date.valueOf((year + 1) + "-01-01"));
  }

  public UUID publish(UUID calendarId, String reason, String actor, Instant at) {
    return requiredUuid(jdbc.queryForObject(
        "select organisation.publish_payroll_calendar(?,?,?,?,?)", UUID.class,
        TenantContext.require(), calendarId, reason, actor, Timestamp.from(at)),
        "Calendar publication returned no event identifier");
  }

  public PayrollCalendarView amend(UUID calendarId, String actor, Instant at) {
    UUID id = requiredUuid(jdbc.queryForObject(
        "select organisation.amend_payroll_calendar(?,?,?,?)", UUID.class,
        TenantContext.require(), calendarId, actor, Timestamp.from(at)),
        "Calendar amendment returned no identifier");
    return calendar(id);
  }

  public UUID retire(UUID calendarId, String reason, String actor, Instant at) {
    return requiredUuid(jdbc.queryForObject(
        "select organisation.retire_payroll_calendar(?,?,?,?,?)", UUID.class,
        TenantContext.require(), calendarId, reason, actor, Timestamp.from(at)),
        "Calendar retirement returned no event identifier");
  }

  public PayrollCalendarOperationalView operations(UUID calendarId) {
    return jdbc.query(
            """
            select * from organisation.payroll_calendar_operational_v
            where tenant_id=? and id=?
            """,
            this::mapOperationalCalendar, TenantContext.require(), calendarId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "Payroll calendar was not found"));
  }

  public List<PayPeriodOperationalView> periodOperations(UUID calendarId, Integer year) {
    calendar(calendarId);
    if (year == null) {
      return jdbc.query(
          """
          select * from organisation.pay_period_operational_v
          where tenant_id=? and calendar_id=? order by period_start
          """,
          this::mapOperationalPeriod, TenantContext.require(), calendarId);
    }
    return jdbc.query(
        """
        select * from organisation.pay_period_operational_v
        where tenant_id=? and calendar_id=? and period_start>=? and period_start<?
        order by period_start
        """,
        this::mapOperationalPeriod, TenantContext.require(), calendarId,
        Date.valueOf(year + "-01-01"), Date.valueOf((year + 1) + "-01-01"));
  }

  private PayrollCalendarView mapCalendar(ResultSet result, int row) throws SQLException {
    return new PayrollCalendarView(
        result.getObject("id", UUID.class),
        result.getObject("calendar_series_id", UUID.class),
        result.getInt("calendar_version"),
        result.getObject("supersedes_calendar_id", UUID.class),
        result.getString("code"), result.getString("name"),
        result.getString("frequency"), result.getString("timezone"));
  }

  private PayPeriodView mapPeriod(ResultSet result, int row) throws SQLException {
    return new PayPeriodView(
        result.getObject("id", UUID.class),
        result.getObject("calendar_id", UUID.class),
        result.getString("period_code"),
        result.getObject("period_start", java.time.LocalDate.class),
        result.getObject("period_end", java.time.LocalDate.class),
        result.getObject("payment_date", java.time.LocalDate.class),
        result.getString("status"));
  }

  private PayrollCalendarOperationalView mapOperationalCalendar(
      ResultSet result, int row) throws SQLException {
    return new PayrollCalendarOperationalView(
        result.getObject("id", UUID.class),
        result.getObject("calendar_series_id", UUID.class),
        result.getInt("calendar_version"),
        result.getObject("supersedes_calendar_id", UUID.class),
        result.getString("code"), result.getString("name"),
        result.getString("frequency"), result.getString("timezone"),
        (Integer) result.getObject("custom_period_days"),
        result.getBoolean("custom_frequency_authorised"),
        result.getBoolean("publication_required"),
        result.getString("lifecycle_status"),
        result.getObject("latest_lifecycle_event_id", UUID.class),
        result.getTimestamp("lifecycle_changed_at") == null ? null
            : result.getTimestamp("lifecycle_changed_at").toInstant(),
        result.getString("lifecycle_changed_by"),
        result.getString("lifecycle_reason"),
        result.getInt("milestone_rule_count"),
        result.getInt("holiday_count"), result.getInt("period_count"),
        result.getObject("first_period_start", java.time.LocalDate.class),
        result.getObject("last_period_end", java.time.LocalDate.class));
  }

  private PayPeriodOperationalView mapOperationalPeriod(
      ResultSet result, int row) throws SQLException {
    return new PayPeriodOperationalView(
        result.getObject("id", UUID.class), result.getObject("calendar_id", UUID.class),
        result.getString("period_code"),
        result.getObject("period_start", java.time.LocalDate.class),
        result.getObject("period_end", java.time.LocalDate.class),
        result.getObject("payment_date", java.time.LocalDate.class), result.getString("status"),
        result.getObject("input_cutoff_original_date", java.time.LocalDate.class),
        result.getObject("input_cutoff_adjusted_date", java.time.LocalDate.class),
        result.getObject("calculation_original_date", java.time.LocalDate.class),
        result.getObject("calculation_adjusted_date", java.time.LocalDate.class),
        result.getObject("approval_original_date", java.time.LocalDate.class),
        result.getObject("approval_adjusted_date", java.time.LocalDate.class),
        result.getObject("release_original_date", java.time.LocalDate.class),
        result.getObject("release_adjusted_date", java.time.LocalDate.class),
        result.getObject("payment_original_date", java.time.LocalDate.class),
        result.getObject("payment_adjusted_date", java.time.LocalDate.class));
  }

  private UUID requiredUuid(UUID value, String message) {
    if (value == null) {
      throw new IllegalStateException(message);
    }
    return value;
  }

  private String weekendLiteral(List<Integer> days) {
    return days.stream().map(String::valueOf)
        .collect(Collectors.joining(",", "{", "}"));
  }
}
