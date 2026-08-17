package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DesignImpactView;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureDesignImpactService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructureDesignImpactController {
  private final SalaryStructureDesignImpactService service;

  public SalaryStructureDesignImpactController(
      SalaryStructureDesignImpactService service) {
    this.service = service;
  }

  @GetMapping("/{identityId}/design-impact")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public DesignImpactView compare(
      @PathVariable UUID identityId,
      @RequestParam UUID baselineVersionId,
      @RequestParam UUID proposedVersionId) {
    return service.compare(identityId, baselineVersionId, proposedVersionId);
  }
}
