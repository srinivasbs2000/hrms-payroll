package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmployeePayrollProfileStatusRequest(
    @NotBlank
    @Pattern(regexp = "INCOMPLETE|READY|ON_HOLD|INACTIVE")
    String payrollStatus) {

  public void validate() {
    if (!"INCOMPLETE".equals(payrollStatus)
        && !"READY".equals(payrollStatus)
        && !"ON_HOLD".equals(payrollStatus)
        && !"INACTIVE".equals(payrollStatus)) {
      throw new IllegalArgumentException(
          "Unsupported employee payroll profile status");
    }
  }
}
