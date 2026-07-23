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
    Set<String> values = new java.util.HashSet<>();
    for (var field : EmployeePayrollPermissions.class.getFields()) {
      assertThat(values.add((String) field.get(null))).isTrue();
    }
    assertThat(values).hasSize(25);
  }
}
