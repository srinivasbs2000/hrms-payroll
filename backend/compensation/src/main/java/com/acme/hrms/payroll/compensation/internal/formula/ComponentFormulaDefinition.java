package com.acme.hrms.payroll.compensation.internal.formula;

/** Formula text and phase supplied for one stable component code. */
public record ComponentFormulaDefinition(String expression, CalculationPhase phase) {
  public ComponentFormulaDefinition {
    if (phase == null) {
      throw new IllegalArgumentException("calculation phase is required");
    }
  }
}
