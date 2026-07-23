package com.acme.hrms.payroll.payrolloperations;

import java.util.UUID;

public record PopulationDecisionView(
    UUID id,
    UUID populationResolutionId,
    UUID cycleId,
    UUID payrollAssignmentVersionId,
    String assignmentNumber,
    UUID payrollRelationshipVersionId,
    String employeeNumber,
    UUID employeePayrollProfileId,
    UUID payGroupAssignmentId,
    UUID salaryAssignmentId,
    UUID salaryStructureVersionId,
    String decision,
    String reasonCode,
    String reasonDetail) {}
