package com.acme.hrms.payroll.compensation.internal.formula;

/** One formula in deterministic dependency-safe execution order. */
public record ComponentFormulaPlan(
    String componentCode,
    CalculationPhase phase,
    CompiledFormula formula) {}
