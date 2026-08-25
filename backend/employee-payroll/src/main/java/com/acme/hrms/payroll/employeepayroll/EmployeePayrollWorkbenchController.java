package com.acme.hrms.payroll.employeepayroll;

import static com.acme.hrms.payroll.employeepayroll.EmployeePayrollPermissions.WORKBENCH_READ;

import com.acme.hrms.payroll.employeepayroll.EmployeePayrollWorkbenchModels.WorkbenchView;
import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeePayrollWorkbenchService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employee-payroll/workbench")
public class EmployeePayrollWorkbenchController {
  private final EmployeePayrollWorkbenchService service;

  public EmployeePayrollWorkbenchController(EmployeePayrollWorkbenchService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('" + WORKBENCH_READ + "')")
  public WorkbenchView view(
      @RequestParam(required = false) String onboardingStatus,
      @RequestParam(required = false) String holdScope,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    return service.view(onboardingStatus, holdScope, asOf);
  }
}
