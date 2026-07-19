package com.acme.hrms.payroll.organisation;

import java.time.LocalDate;
import java.util.List;

public record OrganisationHierarchy(LocalDate asOf, List<Node> legalEntities) {
  public record Node(OrganisationView value, List<Node> children) {}
}
