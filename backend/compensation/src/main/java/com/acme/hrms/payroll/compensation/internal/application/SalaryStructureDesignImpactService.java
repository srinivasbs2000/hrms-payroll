package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DesignImpactView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureDesignImpactRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureRepository;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalaryStructureDesignImpactService {
  private final SalaryStructureRepository structures;
  private final SalaryStructureDesignImpactRepository impacts;
  private final SalaryStructureDesignImpactAssembler assembler;
  private final TenantTransactionExecutor transactions;

  public SalaryStructureDesignImpactService(
      SalaryStructureRepository structures,
      SalaryStructureDesignImpactRepository impacts,
      SalaryStructureDesignImpactAssembler assembler,
      TenantTransactionExecutor transactions) {
    this.structures = structures;
    this.impacts = impacts;
    this.assembler = assembler;
    this.transactions = transactions;
  }

  public DesignImpactView compare(
      UUID identityId,
      UUID baselineVersionId,
      UUID proposedVersionId) {
    if (baselineVersionId.equals(proposedVersionId)) {
      throw new IllegalArgumentException(
          "Baseline and proposed salary-structure versions must differ");
    }

    return transactions.read(() -> {
      SalaryStructureView baseline = structures.version(baselineVersionId);
      SalaryStructureView proposed = structures.version(proposedVersionId);
      requireIdentity(identityId, baseline);
      requireIdentity(identityId, proposed);

      var baselineEvidence = impacts.evidence(baselineVersionId);
      var proposedEvidence = impacts.evidence(proposedVersionId);
      var baselineDependencies = impacts.dependencies(baseline);
      var proposedDependencies = impacts.dependencies(proposed);

      return assembler.assemble(
          baseline,
          baselineEvidence,
          baselineDependencies,
          proposed,
          proposedEvidence,
          proposedDependencies);
    });
  }

  private void requireIdentity(
      UUID expectedIdentityId,
      SalaryStructureView structure) {
    if (!expectedIdentityId.equals(structure.identityId())) {
      throw new ResourceNotFoundException(
          "Salary-structure version does not belong to the requested identity");
    }
  }
}
