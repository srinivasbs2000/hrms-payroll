package com.acme.hrms.payroll.compensation.internal.formula;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable result of parsing and validating a restricted payroll formula. */
public final class CompiledFormula {
  private final String canonicalExpression;
  private final Set<String> dependencies;
  private final RestrictedFormulaCompiler.Node root;

  CompiledFormula(
      String canonicalExpression,
      Set<String> dependencies,
      RestrictedFormulaCompiler.Node root) {
    this.canonicalExpression = canonicalExpression;
    this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
    this.root = root;
  }

  public String canonicalExpression() {
    return canonicalExpression;
  }

  public Set<String> dependencies() {
    return dependencies;
  }

  public BigDecimal evaluate(Map<String, BigDecimal> componentValues) {
    if (componentValues == null) {
      throw new IllegalArgumentException("componentValues is required");
    }
    return root.evaluate(componentValues, MathContext.DECIMAL128);
  }
}
