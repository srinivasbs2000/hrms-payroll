package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.LineageView;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureLineageService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructureLineageController {
  private final SalaryStructureLineageService service;

  public SalaryStructureLineageController(
      SalaryStructureLineageService service) {
    this.service = service;
  }

  @GetMapping("/{identityId}/versions/{versionId}/lineage")
  @PreAuthorize("hasAuthority('audit.read')")
  public LineageView lineage(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId) {
    return service.lineage(identityId, versionId);
  }
}
