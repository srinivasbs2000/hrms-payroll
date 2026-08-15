package com.acme.hrms.payroll.compensation.internal.formula;

/** A fail-closed formula contract violation with a stable machine-readable code. */
public final class FormulaCompilationException extends IllegalArgumentException {
  private final String code;
  private final int position;

  public FormulaCompilationException(String code, int position, String detail) {
    super(code + " at position " + position + ": " + detail);
    this.code = code;
    this.position = position;
  }

  public String code() {
    return code;
  }

  public int position() {
    return position;
  }
}
