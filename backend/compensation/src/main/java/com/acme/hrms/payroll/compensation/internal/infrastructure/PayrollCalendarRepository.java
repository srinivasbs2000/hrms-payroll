package com.acme.hrms.payroll.compensation.internal.infrastructure;

import com.acme.hrms.payroll.compensation.PayPeriodView;
import com.acme.hrms.payroll.compensation.PayrollCalendarView;
import com.acme.hrms.payroll.compensation
    .PayrollCalendarWriteRequest;
import com.acme.hrms.payroll.platform
    .ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayrollCalendarRepository {
  private final JdbcTemplate jdbc;

  public PayrollCalendarRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PayrollCalendarView create(
      PayrollCalendarWriteRequest request,
      String actor,
      Instant createdAt) {
    UUID id = jdbc.queryForObject(
        """
        select organisation
          .create_monthly_payroll_calendar(
            ?,?,?,?,?,?
          )
        """,
        UUID.class,
        TenantContext.require(),
        request.code(),
        request.name(),
        request.resolvedTimezone(),
        actor,
        Timestamp.from(createdAt));

    if (id == null) {
      throw new IllegalStateException(
          "Calendar creation returned no identifier");
    }

    return calendar(id);
  }

  public List<PayrollCalendarView> list() {
    return jdbc.query(
        """
        select id,code,name,frequency,timezone
        from organisation.payroll_calendar
        where tenant_id=?
        order by code
        """,
        this::mapCalendar,
        TenantContext.require());
  }

  public PayrollCalendarView calendar(UUID calendarId) {
    return jdbc.query(
            """
            select id,code,name,frequency,timezone
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
      UUID calendarId,
      int year,
      int paymentDay,
      String actor,
      Instant generatedAt) {
    return jdbc.query(
        """
        select id,
               ?::uuid calendar_id,
               period_code,
               period_start,
               period_end,
               payment_date,
               status
        from organisation.generate_monthly_pay_periods(
          ?,?,?,?,?,?
        )
        order by period_start
        """,
        this::mapPeriod,
        calendarId,
        TenantContext.require(),
        calendarId,
        year,
        paymentDay,
        actor,
        Timestamp.from(generatedAt));
  }

  public List<PayPeriodView> periods(
      UUID calendarId, Integer year) {
    calendar(calendarId);

    if (year == null) {
      return jdbc.query(
          """
          select id,
                 calendar_id,
                 period_code,
                 period_start,
                 period_end,
                 payment_date,
                 status
          from organisation.pay_period
          where tenant_id=? and calendar_id=?
          order by period_start
          """,
          this::mapPeriod,
          TenantContext.require(),
          calendarId);
    }

    return jdbc.query(
        """
        select id,
               calendar_id,
               period_code,
               period_start,
               period_end,
               payment_date,
               status
        from organisation.pay_period
        where tenant_id=?
          and calendar_id=?
          and period_start>=?
          and period_start<?
        order by period_start
        """,
        this::mapPeriod,
        TenantContext.require(),
        calendarId,
        Date.valueOf(year + "-01-01"),
        Date.valueOf((year + 1) + "-01-01"));
  }

  private PayrollCalendarView mapCalendar(
      ResultSet result, int row) throws SQLException {
    return new PayrollCalendarView(
        result.getObject("id", UUID.class),
        result.getString("code"),
        result.getString("name"),
        result.getString("frequency"),
        result.getString("timezone"));
  }

  private PayPeriodView mapPeriod(
      ResultSet result, int row) throws SQLException {
    return new PayPeriodView(
        result.getObject("id", UUID.class),
        result.getObject("calendar_id", UUID.class),
        result.getString("period_code"),
        result.getObject(
            "period_start", java.time.LocalDate.class),
        result.getObject(
            "period_end", java.time.LocalDate.class),
        result.getObject(
            "payment_date", java.time.LocalDate.class),
        result.getString("status"));
  }
}
