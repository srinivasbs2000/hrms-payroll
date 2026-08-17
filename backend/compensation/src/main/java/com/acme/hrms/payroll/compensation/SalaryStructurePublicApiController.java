package com.acme.hrms.payroll.compensation;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DesignImpactView;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructureDesignImpactService;
import com.acme.hrms.payroll.compensation.internal.application.SalaryStructurePublicApiService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructurePublicApiController {
  private final SalaryStructurePublicApiService structures;
  private final SalaryStructureDesignImpactService designImpact;

  public SalaryStructurePublicApiController(
      SalaryStructurePublicApiService structures,
      SalaryStructureDesignImpactService designImpact) {
    this.structures = structures;
    this.designImpact = designImpact;
  }

  @GetMapping("/{identityId}/versions/{versionId}")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public ResponseEntity<SalaryStructureView> version(
      @PathVariable UUID identityId,
      @PathVariable UUID versionId) {
    SalaryStructureView result = structures.version(identityId, versionId);
    return ResponseEntity.ok()
        .eTag(Long.toString(result.versionNo()))
        .body(result);
  }

  @GetMapping("/{identityId}/dependency-impact")
  @PreAuthorize("hasAuthority('compensation.structure.read')")
  public DesignImpactView dependencyImpact(
      @PathVariable UUID identityId,
      @RequestParam UUID baselineVersionId,
      @RequestParam UUID proposedVersionId) {
    return designImpact.compare(
        identityId,
        baselineVersionId,
        proposedVersionId);
  }
}
