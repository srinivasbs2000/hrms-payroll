package com.acme.hrms.payroll.employeepayroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EmployeePayrollHoldContractTest {
  @Test
  void holdPermissionsAndScopesRemainBounded() {
    assertThat(Set.of(
        EmployeePayrollPermissions.HOLD_READ,
        EmployeePayrollPermissions.HOLD_WRITE,
        EmployeePayrollPermissions.HOLD_APPROVE,
        EmployeePayrollPermissions.HOLD_RELEASE))
        .containsExactlyInAnyOrder(
            "employee-payroll.hold.read",
            "employee-payroll.hold.write",
            "employee-payroll.hold.approve",
            "employee-payroll.hold.release");
    assertThat(Set.of(
        "CALCULATION", "PAYMENT", "DOCUMENT_PUBLICATION", "STATUTORY_SUBMISSION"))
        .hasSize(4);
  }
}
