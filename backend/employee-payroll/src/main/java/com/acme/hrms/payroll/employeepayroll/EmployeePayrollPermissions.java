package com.acme.hrms.payroll.employeepayroll;

public final class EmployeePayrollPermissions {
  public static final String RELATIONSHIP_READ = "employee-payroll.relationship.read";
  public static final String RELATIONSHIP_CREATE = "employee-payroll.relationship.create";
  public static final String RELATIONSHIP_VERSION_CREATE = "employee-payroll.relationship.version.create";
  public static final String RELATIONSHIP_VERSION_CORRECT = "employee-payroll.relationship.version.correct";
  public static final String RELATIONSHIP_APPROVE = "employee-payroll.relationship.approve";
  public static final String RELATIONSHIP_VERSION_END_DATE = "employee-payroll.relationship.version.end-date";
  public static final String ASSIGNMENT_READ = "employee-payroll.assignment.read";
  public static final String ASSIGNMENT_CREATE = "employee-payroll.assignment.create";
  public static final String ASSIGNMENT_VERSION_CREATE = "employee-payroll.assignment.version.create";
  public static final String ASSIGNMENT_VERSION_CORRECT = "employee-payroll.assignment.version.correct";
  public static final String ASSIGNMENT_APPROVE = "employee-payroll.assignment.approve";
  public static final String ASSIGNMENT_VERSION_END_DATE = "employee-payroll.assignment.version.end-date";
  public static final String PROFILE_READ = "employee-payroll.profile.read";
  public static final String PROFILE_CREATE = "employee-payroll.profile.create";
  public static final String PROFILE_STATUS_UPDATE = "employee-payroll.profile.status.update";
  public static final String PAY_GROUP_ASSIGNMENT_READ = "employee-payroll.pay-group-assignment.read";
  public static final String PAY_GROUP_ASSIGNMENT_CREATE = "employee-payroll.pay-group-assignment.create";
  public static final String PAY_GROUP_ASSIGNMENT_CORRECT = "employee-payroll.pay-group-assignment.correct";
  public static final String PAY_GROUP_ASSIGNMENT_APPROVE = "employee-payroll.pay-group-assignment.approve";
  public static final String PAY_GROUP_ASSIGNMENT_END_DATE = "employee-payroll.pay-group-assignment.end-date";
  public static final String SALARY_ASSIGNMENT_READ = "employee-payroll.salary-assignment.read";
  public static final String SALARY_ASSIGNMENT_CREATE = "employee-payroll.salary-assignment.create";
  public static final String SALARY_ASSIGNMENT_CORRECT = "employee-payroll.salary-assignment.correct";
  public static final String SALARY_ASSIGNMENT_APPROVE = "employee-payroll.salary-assignment.approve";
  public static final String SALARY_ASSIGNMENT_END_DATE = "employee-payroll.salary-assignment.end-date";

  public static final String COMPONENT_OVERRIDE_READ = "employee-payroll.component-override.read";
  public static final String COMPONENT_OVERRIDE_CREATE = "employee-payroll.component-override.create";
  public static final String COMPONENT_OVERRIDE_CORRECT = "employee-payroll.component-override.correct";
  public static final String COMPONENT_OVERRIDE_APPROVE = "employee-payroll.component-override.approve";
  public static final String COMPENSATION_CHANGE_READ = "employee-payroll.compensation-change.read";
  public static final String COMPENSATION_CHANGE_CREATE = "employee-payroll.compensation-change.create";
  public static final String COMPENSATION_CHANGE_ASSESS = "employee-payroll.compensation-change.assess";
  public static final String COMPENSATION_CHANGE_APPROVE = "employee-payroll.compensation-change.approve";
  public static final String LIFECYCLE_LINEAGE_READ = "employee-payroll.lifecycle-lineage.read";
  public static final String LIFECYCLE_LINEAGE_CREATE = "employee-payroll.lifecycle-lineage.create";
  public static final String LIFECYCLE_LINEAGE_APPROVE = "employee-payroll.lifecycle-lineage.approve";


  public static final String IDENTIFIER_READ = "employee-payroll.identifier.read";
  public static final String IDENTIFIER_WRITE = "employee-payroll.identifier.write";
  public static final String IDENTIFIER_VERIFY = "employee-payroll.identifier.verify";
  public static final String IDENTIFIER_APPROVE = "employee-payroll.identifier.approve";
  public static final String IDENTIFIER_REVEAL = "employee-payroll.identifier.reveal";
  public static final String IDENTITY_MISMATCH_READ = "employee-payroll.identity-mismatch.read";
  public static final String IDENTITY_MISMATCH_WRITE = "employee-payroll.identity-mismatch.write";
  public static final String IDENTITY_MISMATCH_RESOLVE = "employee-payroll.identity-mismatch.resolve";
  public static final String BANK_ACCOUNT_READ = "employee-payroll.bank-account.read";
  public static final String BANK_ACCOUNT_WRITE = "employee-payroll.bank-account.write";
  public static final String BANK_ACCOUNT_VERIFY = "employee-payroll.bank-account.verify";
  public static final String BANK_ACCOUNT_APPROVE = "employee-payroll.bank-account.approve";
  public static final String BANK_ACCOUNT_REVEAL = "employee-payroll.bank-account.reveal";
  public static final String PAYMENT_INSTRUCTION_READ = "employee-payroll.payment-instruction.read";
  public static final String PAYMENT_INSTRUCTION_WRITE = "employee-payroll.payment-instruction.write";
  public static final String PAYMENT_INSTRUCTION_APPROVE = "employee-payroll.payment-instruction.approve";
  public static final String PAYMENT_RESTRICTION_READ = "employee-payroll.payment-restriction.read";
  public static final String PAYMENT_RESTRICTION_WRITE = "employee-payroll.payment-restriction.write";
  public static final String PAYMENT_RESTRICTION_CLEAR = "employee-payroll.payment-restriction.clear";
  public static final String PAYMENT_READINESS_READ = "employee-payroll.payment-readiness.read";

  public static final String ONBOARDING_READ = "employee-payroll.onboarding.read";
  public static final String ONBOARDING_WRITE = "employee-payroll.onboarding.write";
  public static final String ONBOARDING_APPROVE = "employee-payroll.onboarding.approve";
  public static final String READINESS_READ = "employee-payroll.readiness.read";
  public static final String READINESS_POLICY_READ = "employee-payroll.readiness-policy.read";
  public static final String READINESS_POLICY_WRITE = "employee-payroll.readiness-policy.write";
  public static final String HOLD_READ = "employee-payroll.hold.read";
  public static final String HOLD_WRITE = "employee-payroll.hold.write";
  public static final String HOLD_APPROVE = "employee-payroll.hold.approve";
  public static final String HOLD_RELEASE = "employee-payroll.hold.release";
  public static final String WORKBENCH_READ = "employee-payroll.workbench.read";

  private EmployeePayrollPermissions() {}
}
