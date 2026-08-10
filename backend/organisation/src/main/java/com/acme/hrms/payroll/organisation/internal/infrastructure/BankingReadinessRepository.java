package com.acme.hrms.payroll.organisation.internal.infrastructure;

import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BankingReadinessRepository {
  private final JdbcTemplate jdbc;

  public BankingReadinessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public BankStatus bankStatus(
      String ownerKey,
      String currencyCode,
      LocalDate asOf) {
    return jdbc.queryForObject(
        """
        select
          count(*) > 0 configured,
          coalesce(
            bool_or(
              v.lifecycle_status='ACTIVE'
              and v.approved_at is not null
              and v.approved_at::date<=?
              and v.effective_from<=?
              and (v.effective_to is null or v.effective_to>?)
            ),
            false
          ) active,
          coalesce(
            bool_or(
              v.lifecycle_status='ACTIVE'
              and v.is_default
              and v.approved_at is not null
              and v.approved_at::date<=?
              and v.effective_from<=?
              and (v.effective_to is null or v.effective_to>?)
            ),
            false
          ) active_default
        from organisation.employer_bank_account i
        join organisation.employer_bank_account_version v
          on v.tenant_id=i.tenant_id
         and v.employer_bank_account_id=i.id
        where i.tenant_id=?
          and i.owner_key=?
          and v.currency_code=?
        """,
        (rs, row) ->
            new BankStatus(
                rs.getBoolean("configured"),
                rs.getBoolean("active"),
                rs.getBoolean("active_default")),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        Date.valueOf(asOf),
        TenantContext.require(),
        ownerKey,
        currencyCode);
  }

  public boolean signatoryConfigured(String ownerKey) {
    Boolean configured =
        jdbc.queryForObject(
            """
            select exists (
              select 1
              from organisation.authorised_signatory
              where tenant_id=? and owner_key=?
            )
            """,
            Boolean.class,
            TenantContext.require(),
            ownerKey);
    return Boolean.TRUE.equals(configured);
  }

  public record BankStatus(
      boolean configured,
      boolean active,
      boolean activeDefault) {}
}
