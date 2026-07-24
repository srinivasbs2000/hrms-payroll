package com.acme.hrms.payroll.calculation;

public final class PayrollCalculationPermissions {
  public static final String EXECUTE = "payroll-calculation.execute";
  public static final String RECALCULATE = "payroll-calculation.recalculate";
  public static final String RESULT_READ = "payroll-result.read";
  public static final String TRACE_READ = "payroll-result.trace.read";

  private PayrollCalculationPermissions() {}
}
