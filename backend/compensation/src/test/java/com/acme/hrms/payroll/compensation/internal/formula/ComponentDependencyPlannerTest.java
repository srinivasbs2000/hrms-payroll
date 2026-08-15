package com.acme.hrms.payroll.compensation.internal.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComponentDependencyPlannerTest {
  private final ComponentDependencyPlanner planner = new ComponentDependencyPlanner();

  @Test
  void producesDeterministicPhaseAndDependencyOrder() {
    Map<String, ComponentFormulaDefinition> definitions = new LinkedHashMap<>();
    definitions.put("NET_PAY", formula("GROSS_PAY-TAX_AMOUNT", CalculationPhase.NET));
    definitions.put("GROSS_PAY", formula("BASIC+HRA", CalculationPhase.PRE_TAX));
    definitions.put("TAX_AMOUNT", formula("GROSS_PAY*0.1", CalculationPhase.TAX));

    List<ComponentFormulaPlan> plan = planner.plan(definitions, Set.of("BASIC", "HRA"));

    assertThat(plan).extracting(ComponentFormulaPlan::componentCode)
        .containsExactly("GROSS_PAY", "TAX_AMOUNT", "NET_PAY");
  }

  @Test
  void rejectsUnknownSelfLaterPhaseAndCyclicDependencies() {
    assertThatThrownBy(() -> planner.plan(
        Map.of("HRA", formula("UNKNOWN*0.4", CalculationPhase.PRE_TAX)), Set.of()))
        .hasMessageContaining("UNKNOWN_DEPENDENCY");
    assertThatThrownBy(() -> planner.plan(
        Map.of("HRA", formula("HRA*0.4", CalculationPhase.PRE_TAX)), Set.of()))
        .hasMessageContaining("SELF_DEPENDENCY");
    assertThatThrownBy(() -> planner.plan(Map.of(
        "EARLY", formula("LATER+1", CalculationPhase.PRE_TAX),
        "LATER", formula("INPUT_VALUE+1", CalculationPhase.TAX)), Set.of("INPUT_VALUE")))
        .hasMessageContaining("LATER_PHASE_DEPENDENCY");
    assertThatThrownBy(() -> planner.plan(Map.of(
        "FIRST", formula("SECOND+1", CalculationPhase.PRE_TAX),
        "SECOND", formula("FIRST+1", CalculationPhase.PRE_TAX)), Set.of()))
        .hasMessageContaining("DEPENDENCY_CYCLE")
        .hasMessageContaining("FIRST,SECOND");
  }

  private ComponentFormulaDefinition formula(String expression, CalculationPhase phase) {
    return new ComponentFormulaDefinition(expression, phase);
  }
}
