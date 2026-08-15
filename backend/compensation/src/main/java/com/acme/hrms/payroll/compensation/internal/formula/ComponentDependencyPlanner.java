package com.acme.hrms.payroll.compensation.internal.formula;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Compiles component formulas and returns a deterministic, phase-safe topological plan. */
public final class ComponentDependencyPlanner {
  private final RestrictedFormulaCompiler compiler;

  public ComponentDependencyPlanner() {
    this(new RestrictedFormulaCompiler());
  }

  ComponentDependencyPlanner(RestrictedFormulaCompiler compiler) {
    this.compiler = compiler;
  }

  public List<ComponentFormulaPlan> plan(
      Map<String, ComponentFormulaDefinition> definitions,
      Set<String> externalInputs) {
    if (definitions == null || definitions.isEmpty()) {
      return List.of();
    }
    Set<String> inputs = externalInputs == null ? Set.of() : Set.copyOf(externalInputs);
    Map<String, CompiledFormula> compiled = new LinkedHashMap<>();
    definitions.keySet().stream().sorted().forEach(code -> {
      validateCode(code);
      ComponentFormulaDefinition definition = definitions.get(code);
      if (definition == null) {
        throw new IllegalArgumentException("formula definition is required for " + code);
      }
      compiled.put(code, compiler.compile(definition.expression()));
    });

    Map<String, Integer> indegree = new HashMap<>();
    Map<String, Set<String>> dependants = new HashMap<>();
    definitions.keySet().forEach(code -> indegree.put(code, 0));
    for (Map.Entry<String, CompiledFormula> entry : compiled.entrySet()) {
      String code = entry.getKey();
      CalculationPhase phase = definitions.get(code).phase();
      for (String dependency : entry.getValue().dependencies()) {
        if (dependency.equals(code)) {
          throw new IllegalArgumentException("SELF_DEPENDENCY: " + code);
        }
        if (!definitions.containsKey(dependency)) {
          if (!inputs.contains(dependency)) {
            throw new IllegalArgumentException(
                "UNKNOWN_DEPENDENCY: " + code + " references " + dependency);
          }
          continue;
        }
        CalculationPhase dependencyPhase = definitions.get(dependency).phase();
        if (dependencyPhase.compareTo(phase) > 0) {
          throw new IllegalArgumentException(
              "LATER_PHASE_DEPENDENCY: " + code + " references " + dependency);
        }
        indegree.compute(code, (ignored, count) -> count + 1);
        dependants.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(code);
      }
    }

    Comparator<String> order = Comparator
        .comparing((String code) -> definitions.get(code).phase())
        .thenComparing(code -> code);
    PriorityQueue<String> ready = new PriorityQueue<>(order);
    indegree.forEach((code, count) -> {
      if (count == 0) {
        ready.add(code);
      }
    });
    List<ComponentFormulaPlan> result = new ArrayList<>();
    while (!ready.isEmpty()) {
      String code = ready.remove();
      result.add(new ComponentFormulaPlan(
          code, definitions.get(code).phase(), compiled.get(code)));
      for (String dependant : dependants.getOrDefault(code, Set.of())) {
        int remaining = indegree.compute(dependant, (ignored, count) -> count - 1);
        if (remaining == 0) {
          ready.add(dependant);
        }
      }
    }
    if (result.size() != definitions.size()) {
      List<String> cycleMembers = indegree.entrySet().stream()
          .filter(entry -> entry.getValue() > 0)
          .map(Map.Entry::getKey)
          .sorted()
          .toList();
      throw new IllegalArgumentException(
          "DEPENDENCY_CYCLE: " + String.join(",", cycleMembers));
    }
    return List.copyOf(result);
  }

  private void validateCode(String code) {
    if (code == null || !code.matches("^[A-Z][A-Z0-9_]{1,39}$")) {
      throw new IllegalArgumentException("invalid component code: " + code);
    }
  }
}
