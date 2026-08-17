package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureRepository;
import com.acme.hrms.payroll.platform.ResourceNotFoundException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalaryStructurePublicApiService {
  private final SalaryStructureRepository structures;
  private final TenantTransactionExecutor transactions;

  public SalaryStructurePublicApiService(
      SalaryStructureRepository structures,
      TenantTransactionExecutor transactions) {
    this.structures = structures;
    this.transactions = transactions;
  }

  public SalaryStructureView version(
      UUID identityId,
      UUID versionId) {
    return transactions.read(() -> {
      SalaryStructureView version = structures.version(versionId);
      if (!identityId.equals(version.identityId())) {
        throw new ResourceNotFoundException(
            "Salary-structure version does not belong to the requested identity");
      }
      return version;
    });
  }
}
