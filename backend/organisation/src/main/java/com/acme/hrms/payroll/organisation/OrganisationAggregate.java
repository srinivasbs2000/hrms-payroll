package com.acme.hrms.payroll.organisation;

import java.util.UUID;

public sealed interface OrganisationAggregate permits OrganisationAggregate.LegalEntity,
    OrganisationAggregate.PayrollStatutoryUnit, OrganisationAggregate.Establishment {
  UUID id();
  String code();

  record LegalEntity(UUID id, String code) implements OrganisationAggregate {}
  record PayrollStatutoryUnit(UUID id, String code) implements OrganisationAggregate {}
  record Establishment(UUID id, String code) implements OrganisationAggregate {}
}
