package com.acme.hrms.payroll.compensation.internal.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestrictedFormulaCompilerTest {
  private final RestrictedFormulaCompiler compiler = new RestrictedFormulaCompiler();

  @Test
  void compilesDependenciesAndEvaluatesDeterministically() {
    CompiledFormula formula = compiler.compile(
        "ROUND(MAX(BASIC * 0.40, MINIMUM_WAGE) + ALLOWANCE, 2)");

    assertThat(formula.dependencies())
        .containsExactly("BASIC", "MINIMUM_WAGE", "ALLOWANCE");
    assertThat(formula.canonicalExpression())
        .isEqualTo("ROUND((MAX((BASIC*0.4),MINIMUM_WAGE)+ALLOWANCE),2)");
    assertThat(formula.evaluate(Map.of(
        "BASIC", new BigDecimal("10000"),
        "MINIMUM_WAGE", new BigDecimal("4500"),
        "ALLOWANCE", new BigDecimal("125.555"))))
        .isEqualByComparingTo("4625.56");
  }

  @Test
  void rejectsCodeExecutionAndUnknownFunctions() {
    assertThatThrownBy(() -> compiler.compile("T(java.lang.Runtime).getRuntime()"))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("INVALID_IDENTIFIER");
    assertThatThrownBy(() -> compiler.compile("EVAL(BASIC)"))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("UNSUPPORTED_FUNCTION");
  }

  @Test
  void failsClosedForMissingInputsAndDivisionByZero() {
    CompiledFormula missing = compiler.compile("BASIC + HRA");
    assertThatThrownBy(() -> missing.evaluate(Map.of("BASIC", BigDecimal.ONE)))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("MISSING_COMPONENT_VALUE");

    CompiledFormula division = compiler.compile("BASIC / (HRA - HRA)");
    assertThatThrownBy(() -> division.evaluate(Map.of(
        "BASIC", BigDecimal.ONE, "HRA", BigDecimal.ONE)))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("DIVISION_BY_ZERO");
  }

  @Test
  void enforcesNumericAndComplexityBounds() {
    assertThatThrownBy(() -> compiler.compile("12345678901234567890 + BASIC"))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("NUMBER_OUT_OF_RANGE");
    assertThatThrownBy(() -> compiler.compile("ROUND(BASIC, 1.5)"))
        .isInstanceOf(FormulaCompilationException.class)
        .hasMessageContaining("INVALID_ROUND_SCALE");
  }
}
