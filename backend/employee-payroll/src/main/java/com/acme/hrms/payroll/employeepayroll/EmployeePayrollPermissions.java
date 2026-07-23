package com.acme.hrms.payroll.employeepayroll;

public final class EmployeePayrollPermissions {
  public static final String RELATIONSHIP_READ =
      "employee-payroll.relationship.read";
  public static final String RELATIONSHIP_CREATE =
      "employee-payroll.relationship.create";
  public static final String RELATIONSHIP_VERSION_CREATE =
      "employee-payroll.relationship.version.create";
  public static final String RELATIONSHIP_VERSION_CORRECT =
      "employee-payroll.relationship.version.correct";
  public static final String RELATIONSHIP_VERSION_END_DATE =
      "employee-payroll.relationship.version.end-date";
  public static final String RELATIONSHIP_APPROVE =
      "employee-payroll.relationship.approve";

  public static final String ASSIGNMENT_READ =
      "employee-payroll.assignment.read";
  public static final String ASSIGNMENT_CREATE =
      "employee-payroll.assignment.create";
  public static final String ASSIGNMENT_VERSION_CREATE =
      "employee-payroll.assignment.version.create";
  public static final String ASSIGNMENT_VERSION_CORRECT =
      "employee-payroll.assignment.version.correct";
  public static final String ASSIGNMENT_VERSION_END_DATE =
      "employee-payroll.assignment.version.end-date";
  public static final String ASSIGNMENT_APPROVE =
      "employee-payroll.assignment.approve";

  public static final String PROFILE_READ =
      "employee-payroll.profile.read";
  public static final String PROFILE_CREATE =
      "employee-payroll.profile.create";
  public static final String PROFILE_STATUS_UPDATE =
      "employee-payroll.profile.status.update";

  public static final String PAY_GROUP_ASSIGNMENT_READ =
      "employee-payroll.pay-group-assignment.read";
  public static final String PAY_GROUP_ASSIGNMENT_CREATE =
      "employee-payroll.pay-group-assignment.create";
  public static final String PAY_GROUP_ASSIGNMENT_CORRECT =
      "employee-payroll.pay-group-assignment.correct";
  public static final String PAY_GROUP_ASSIGNMENT_END_DATE =
      "employee-payroll.pay-group-assignment.end-date";
  public static final String PAY_GROUP_ASSIGNMENT_APPROVE =
      "employee-payroll.pay-group-assignment.approve";

  public static final String SALARY_ASSIGNMENT_READ =
      "employee-payroll.salary-assignment.read";
  public static final String SALARY_ASSIGNMENT_CREATE =
      "employee-payroll.salary-assignment.create";
  public static final String SALARY_ASSIGNMENT_CORRECT =
      "employee-payroll.salary-assignment.correct";
  public static final String SALARY_ASSIGNMENT_END_DATE =
      "employee-payroll.salary-assignment.end-date";
  public static final String SALARY_ASSIGNMENT_APPROVE =
      "employee-payroll.salary-assignment.approve";

  private EmployeePayrollPermissions() {}
}
