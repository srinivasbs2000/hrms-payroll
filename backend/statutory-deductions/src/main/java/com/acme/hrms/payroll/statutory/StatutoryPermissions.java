package com.acme.hrms.payroll.statutory;

public final class StatutoryPermissions {
  public static final String EVALUATION_EXECUTE = "statutory-evaluation.execute";
  public static final String EVALUATION_READ = "statutory-evaluation.read";
  public static final String LEDGER_POST = "statutory-ledger.post";
  public static final String LEDGER_CORRECT = "statutory-ledger.correct";
  public static final String LEDGER_READ = "statutory-ledger.read";
  public static final String BALANCE_READ = "statutory-balance.read";
  public static final String RECONCILIATION_READ = "statutory-reconciliation.read";
  public static final String REMITTANCE_READ = "statutory-remittance.read";
  public static final String REGISTRATION_READ = "statutory-registration.read";
  public static final String REGISTRATION_IDENTIFIER_READ =
      "statutory-registration.identifier.read";
  public static final String REGISTRATION_WRITE = "statutory-registration.write";
  public static final String REGISTRATION_TYPE_WRITE =
      "statutory-registration-type.write";
  public static final String REGISTRATION_VERIFY =
      "statutory-registration.verify";
  public static final String REGISTRATION_APPROVE =
      "statutory-registration.approve";

  private StatutoryPermissions() {}
}
