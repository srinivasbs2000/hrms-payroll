package com.acme.hrms.payroll.employeepayroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollReadinessModels.ReadinessFindingView;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmployeePayrollOnboardingReadinessContractTest {
  @Test
  void p5EorPermissionsAndReadinessDimensionsRemainExact() {
    assertThat(Set.of(
        EmployeePayrollPermissions.ONBOARDING_READ,
        EmployeePayrollPermissions.ONBOARDING_WRITE,
        EmployeePayrollPermissions.ONBOARDING_APPROVE,
        EmployeePayrollPermissions.READINESS_READ,
        EmployeePayrollPermissions.READINESS_POLICY_READ,
        EmployeePayrollPermissions.READINESS_POLICY_WRITE,
        EmployeePayrollPermissions.WORKBENCH_READ))
        .containsExactlyInAnyOrder(
            "employee-payroll.onboarding.read",
            "employee-payroll.onboarding.write",
            "employee-payroll.onboarding.approve",
            "employee-payroll.readiness.read",
            "employee-payroll.readiness-policy.read",
            "employee-payroll.readiness-policy.write",
            "employee-payroll.workbench.read");

    ReadinessFindingView failClosed = new ReadinessFindingView(
        "STATUTORY", "BLOCKING", "NOT_EVALUATED",
        "STATUTORY_PROVIDER_NOT_EVALUATED", "No provider evidence",
        "PROVIDER_OR_POLICY", "DEFAULT_REQUIRED_BLOCKING");
    assertThat(failClosed.status()).isEqualTo("NOT_EVALUATED");
    assertThat(failClosed.severity()).isEqualTo("BLOCKING");
  }
}
