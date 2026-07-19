package com.acme.hrms.payroll.calculation;
import java.math.BigDecimal; import java.util.Map;
public record CalculationTraceStep(int stepNo,String componentCode,String stepType,Map<String,Object> inputs,String expression,BigDecimal output,String message){}
