package com.acme.hrms.payroll.payrolloperations;

public final class PayrollOperationsPermissions {
  public static final String CYCLE_READ = "payroll-cycle.read";
  public static final String CYCLE_CREATE = "payroll-cycle.create";
  public static final String POPULATION_RESOLVE =
      "payroll-cycle.population.resolve";
  public static final String INPUTS_READ = "payroll-cycle.inputs.read";
  public static final String INPUTS_SEAL = "payroll-cycle.inputs.seal";

  private PayrollOperationsPermissions() {}
}
