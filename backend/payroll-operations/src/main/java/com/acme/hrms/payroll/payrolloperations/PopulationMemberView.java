package com.acme.hrms.payroll.payrolloperations;

import java.util.UUID;

public record PopulationMemberView(
    UUID id,
    UUID cycleId,
    UUID populationResolutionId,
    UUID payrollAssignmentVersionId,
    String assignmentNumber,
    UUID payrollRelationshipVersionId,
    String employeeNumber,
    UUID employeePayrollProfileId,
    UUID payGroupAssignmentId,
    UUID salaryAssignmentId,
    String inclusionReason,
    String status) {}
