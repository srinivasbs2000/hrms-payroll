package com.acme.hrms.payroll.compensation.internal.formula;

/** Ordered component-calculation phases. Dependencies may only point to the same or an earlier phase. */
public enum CalculationPhase {
  INPUT,
  PRE_TAX,
  TAX,
  POST_TAX,
  NET
}
