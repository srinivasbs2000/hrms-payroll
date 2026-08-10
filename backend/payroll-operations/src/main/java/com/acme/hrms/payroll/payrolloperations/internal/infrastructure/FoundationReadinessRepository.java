package com.acme.hrms.payroll.payrolloperations.internal.infrastructure;

import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FoundationReadinessRepository {
  private final JdbcTemplate jdbc;

  public FoundationReadinessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public FoundationContext context(UUID cycleId) {
    return jdbc.query(
            """
            select cycle.id,
                   cycle.status::text cycle_status,
                   cycle.pay_group_id pay_group_version_id,
                   period.period_start,
                   period.period_end,
                   period.payment_date,
                   group_version.payroll_statutory_unit_version_id,
                   psu_version.payroll_statutory_unit_id,
                   psu_version.legal_entity_version_id,
                   legal_version.legal_entity_id,
                   cycle.foundation_config_snapshot_id,
                   cycle.foundation_config_snapshot_hash,
                   cycle.foundation_config_count,
                   cycle.foundation_config_sealed_at
            from payroll_ops.payroll_cycle cycle
            join organisation.pay_group_version group_version
              on group_version.tenant_id=cycle.tenant_id
             and group_version.id=cycle.pay_group_id
            join organisation.pay_period period
              on period.tenant_id=cycle.tenant_id
             and period.id=cycle.pay_period_id
            join organisation.payroll_statutory_unit_version psu_version
              on psu_version.tenant_id=group_version.tenant_id
             and psu_version.id=group_version.payroll_statutory_unit_version_id
            join organisation.legal_entity_version legal_version
              on legal_version.tenant_id=psu_version.tenant_id
             and legal_version.id=psu_version.legal_entity_version_id
            where cycle.tenant_id=?
              and cycle.id=?
            """,
            this::map,
            TenantContext.require(),
            cycleId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Payroll cycle was not found"));
  }

  private FoundationContext map(ResultSet result, int row) throws SQLException {
    var sealed = result.getTimestamp("foundation_config_sealed_at");
    return new FoundationContext(
        result.getObject("id", UUID.class),
        result.getString("cycle_status"),
        result.getObject("pay_group_version_id", UUID.class),
        result.getObject("period_start", LocalDate.class),
        result.getObject("period_end", LocalDate.class),
        result.getObject("payment_date", LocalDate.class),
        result.getObject("payroll_statutory_unit_version_id", UUID.class),
        result.getObject("payroll_statutory_unit_id", UUID.class),
        result.getObject("legal_entity_version_id", UUID.class),
        result.getObject("legal_entity_id", UUID.class),
        result.getObject("foundation_config_snapshot_id", UUID.class),
        result.getString("foundation_config_snapshot_hash"),
        result.getObject("foundation_config_count", Integer.class),
        sealed == null ? null : sealed.toInstant());
  }

  public record FoundationContext(
      UUID payrollCycleId,
      String cycleStatus,
      UUID payGroupVersionId,
      LocalDate periodStart,
      LocalDate periodEnd,
      LocalDate paymentDate,
      UUID payrollStatutoryUnitVersionId,
      UUID payrollStatutoryUnitId,
      UUID legalEntityVersionId,
      UUID legalEntityId,
      UUID snapshotId,
      String snapshotHash,
      Integer snapshotCount,
      Instant snapshotSealedAt) {}
}
