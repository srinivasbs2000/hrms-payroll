package com.acme.hrms.payroll.employeepayroll;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmployeePayrollWorkbenchModels {
  private EmployeePayrollWorkbenchModels() {}

  public record WorkbenchItemView(
      UUID payrollRelationshipId,
      UUID onboardingCaseId,
      String onboardingStatus,
      LocalDate asOf,
      boolean payrollReady,
      long blockingFindingCount,
      List<String> blockingDimensions,
      long activeHoldCount,
      List<String> activeHoldScopes) {}

  public record WorkbenchView(
      LocalDate asOf,
      int total,
      List<WorkbenchItemView> items) {}
}
