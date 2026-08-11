package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.platform.TenantContext;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrganisationApprovalScopeFacade {
  private final JdbcTemplate jdbc;

  public OrganisationApprovalScopeFacade(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ApprovalScope> findForEstablishmentVersion(UUID establishmentVersionId) {
    if (establishmentVersionId == null) {
      return Optional.empty();
    }
    String sql =
        "select p.payroll_statutory_unit_id "
            + "from organisation.establishment_version e "
            + "join organisation.payroll_statutory_unit_version p "
            + "on p.tenant_id=e.tenant_id "
            + "and p.id=e.payroll_statutory_unit_version_id "
            + "where e.tenant_id=? and e.id=?";
    return first(sql, TenantContext.require(), establishmentVersionId);
  }

  public Optional<ApprovalScope> findForWorkLocationVersion(UUID workLocationVersionId) {
    if (workLocationVersionId == null) {
      return Optional.empty();
    }
    String sql =
        "select p.payroll_statutory_unit_id "
            + "from organisation.work_location_version w "
            + "join organisation.establishment_version e "
            + "on e.tenant_id=w.tenant_id "
            + "and e.id=w.establishment_version_id "
            + "join organisation.payroll_statutory_unit_version p "
            + "on p.tenant_id=e.tenant_id "
            + "and p.id=e.payroll_statutory_unit_version_id "
            + "where w.tenant_id=? and w.id=?";
    return first(sql, TenantContext.require(), workLocationVersionId);
  }

  public Optional<ApprovalScope> findForEstablishmentIdentity(
      UUID establishmentId, LocalDate asOf) {
    if (establishmentId == null || asOf == null) {
      return Optional.empty();
    }
    String sql =
        "select p.payroll_statutory_unit_id "
            + "from organisation.establishment_version e "
            + "join organisation.payroll_statutory_unit_version p "
            + "on p.tenant_id=e.tenant_id "
            + "and p.id=e.payroll_statutory_unit_version_id "
            + "where e.tenant_id=? "
            + "and e.establishment_id=? "
            + "and e.approval_status='APPROVED' "
            + "and e.effective_from<=? "
            + "and (e.effective_to is null or e.effective_to>?) "
            + "order by e.version_sequence desc limit 1";
    return first(
        sql,
        TenantContext.require(),
        establishmentId,
        Date.valueOf(asOf),
        Date.valueOf(asOf));
  }

  private Optional<ApprovalScope> first(String sql, Object... arguments) {
    return jdbc.query(
            sql,
            (resultSet, rowNum) ->
                new ApprovalScope(resultSet.getObject(1, UUID.class)),
            arguments)
        .stream()
        .findFirst();
  }

  public record ApprovalScope(UUID payrollStatutoryUnitId) {
    public ApprovalScope {
      if (payrollStatutoryUnitId == null) {
        throw new IllegalArgumentException("payrollStatutoryUnitId is required");
      }
    }
  }
}
