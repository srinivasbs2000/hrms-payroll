package com.acme.hrms.payroll.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FixedGrossToNetCalculator {
  public CalculationResult calculate(
      BigDecimal basic, BigDecimal hraPercent, BigDecimal targetMonthly, BigDecimal factor) {
    BigDecimal unroundedBasic = basic;
    BigDecimal unroundedHra = basic.multiply(hraPercent);
    BigDecimal unroundedSpecial = targetMonthly.subtract(unroundedBasic).subtract(unroundedHra);
    BigDecimal roundedBasic = unroundedBasic.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    BigDecimal roundedHra = unroundedHra.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    BigDecimal roundedSpecial = unroundedSpecial.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    var components = new LinkedHashMap<String, BigDecimal>();
    components.put("BASIC", roundedBasic);
    components.put("HRA", roundedHra);
    components.put("SPECIAL_ALLOWANCE", roundedSpecial);
    BigDecimal gross = components.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CalculationResult(Collections.unmodifiableMap(components), gross, BigDecimal.ZERO, gross);
  }

  public record CalculationResult(
      Map<String, BigDecimal> components, BigDecimal gross, BigDecimal deductions, BigDecimal net) {}
}
