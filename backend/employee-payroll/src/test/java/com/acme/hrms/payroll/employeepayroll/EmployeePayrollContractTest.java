package com.acme.hrms.payroll.employeepayroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeePayrollContractTest {
  private static final UUID ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void relationshipIdentityFieldsAreRequiredOnCreate() {
    PayrollRelationshipWriteRequest request =
        new PayrollRelationshipWriteRequest(
            null, null, ID, LocalDate.of(2027, 1, 1), null);

    assertThatThrownBy(() -> request.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("externalEmployeeId");
  }

  @Test
  void relationshipRangeMustBeHalfOpenAndIncreasing() {
    PayrollRelationshipWriteRequest request =
        new PayrollRelationshipWriteRequest(
            "EXT-1",
            "EMP-1",
            ID,
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 1));

    assertThatThrownBy(() -> request.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("relationshipEnd");
  }

  @Test
  void assignmentIdentityFieldsAreRequiredOnCreate() {
    PayrollAssignmentWriteRequest request =
        new PayrollAssignmentWriteRequest(
            null,
            null,
            ID,
            ID,
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(() -> request.validate(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payrollRelationshipId");
  }

  @Test
  void profilesAndSalaryAssignmentsSupportInrOnly() {
    EmployeePayrollProfileWriteRequest profile =
        new EmployeePayrollProfileWriteRequest(ID, "USD");
    SalaryAssignmentWriteRequest salary =
        new SalaryAssignmentWriteRequest(
            ID,
            ID,
            BigDecimal.valueOf(75000),
            "USD",
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(profile::validate)
        .hasMessageContaining("INR");
    assertThatThrownBy(salary::validate)
        .hasMessageContaining("INR");
  }

  @Test
  void salaryAmountCannotBeNegative() {
    SalaryAssignmentWriteRequest request =
        new SalaryAssignmentWriteRequest(
            ID,
            ID,
            BigDecimal.valueOf(-1),
            "INR",
            LocalDate.of(2027, 1, 1),
            null);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("monthlyAmount");
  }

  @Test
  void profileStatusIsRestrictedToSupportedLifecycle() {
    EmployeePayrollProfileStatusRequest request =
        new EmployeePayrollProfileStatusRequest("SUSPENDED");

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported");
  }

  @Test
  void permissionConstantsRemainUnique() throws Exception {
    Set<String> expected = Set.of(
            "employee-payroll.profile.read",
            "employee-payroll.profile.create",
            "employee-payroll.profile.status.update",
            "employee-payroll.relationship.read",
            "employee-payroll.relationship.create",
            "employee-payroll.relationship.approve",
            "employee-payroll.relationship.version.create",
            "employee-payroll.relationship.version.correct",
            "employee-payroll.relationship.version.end-date",
            "employee-payroll.assignment.read",
            "employee-payroll.assignment.create",
            "employee-payroll.assignment.approve",
            "employee-payroll.assignment.version.create",
            "employee-payroll.assignment.version.correct",
            "employee-payroll.assignment.version.end-date",
            "employee-payroll.salary-assignment.read",
            "employee-payroll.salary-assignment.create",
            "employee-payroll.salary-assignment.approve",
            "employee-payroll.salary-assignment.correct",
            "employee-payroll.salary-assignment.end-date",
            "employee-payroll.pay-group-assignment.read",
            "employee-payroll.pay-group-assignment.create",
            "employee-payroll.pay-group-assignment.approve",
            "employee-payroll.pay-group-assignment.correct",
            "employee-payroll.pay-group-assignment.end-date",
            "employee-payroll.component-override.read",
            "employee-payroll.component-override.create",
            "employee-payroll.component-override.approve",
            "employee-payroll.component-override.correct",
            "employee-payroll.compensation-change.read",
            "employee-payroll.compensation-change.create",
            "employee-payroll.compensation-change.assess",
            "employee-payroll.compensation-change.approve",
            "employee-payroll.lifecycle-lineage.read",
            "employee-payroll.lifecycle-lineage.create",
            "employee-payroll.lifecycle-lineage.approve",
            "employee-payroll.identifier.read",
            "employee-payroll.identifier.write",
            "employee-payroll.identifier.verify",
            "employee-payroll.identifier.approve",
            "employee-payroll.identifier.reveal",
            "employee-payroll.identity-mismatch.read",
            "employee-payroll.identity-mismatch.write",
            "employee-payroll.identity-mismatch.resolve",
            "employee-payroll.bank-account.read",
            "employee-payroll.bank-account.write",
            "employee-payroll.bank-account.verify",
            "employee-payroll.bank-account.approve",
            "employee-payroll.bank-account.reveal",
            "employee-payroll.payment-instruction.read",
            "employee-payroll.payment-instruction.write",
            "employee-payroll.payment-instruction.approve",
            "employee-payroll.payment-restriction.read",
            "employee-payroll.payment-restriction.write",
            "employee-payroll.payment-restriction.clear",
            "employee-payroll.payment-readiness.read"
        );
    Set<String> values = new java.util.HashSet<>();
    for (var field : EmployeePayrollPermissions.class.getFields()) {
      assertThat(field.getType()).isEqualTo(String.class);
      assertThat(values.add((String) field.get(null))).isTrue();
    }
    assertThat(values)
        .containsExactlyInAnyOrderElementsOf(expected);
  }
}
