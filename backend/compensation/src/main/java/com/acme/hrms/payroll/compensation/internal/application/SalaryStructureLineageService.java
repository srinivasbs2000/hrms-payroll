package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls;
import com.acme.hrms.payroll.compensation.SalaryStructureLineageControls.LineageView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureLineageRepository;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureLineageRepository.Snapshot;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalaryStructureLineageService {
  private final SalaryStructureLineageRepository repository;
  private final TenantTransactionExecutor transactions;
  private final CanonicalJsonHasher canonical;

  public SalaryStructureLineageService(
      SalaryStructureLineageRepository repository,
      TenantTransactionExecutor transactions,
      CanonicalJsonHasher canonical) {
    this.repository = repository;
    this.transactions = transactions;
    this.canonical = canonical;
  }

  public LineageView lineage(UUID identityId, UUID versionId) {
    return transactions.read(() -> assemble(repository.snapshot(
        identityId,
        versionId)));
  }

  private LineageView assemble(Snapshot snapshot) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("identityId", snapshot.identityId());
    evidence.put("versionId", snapshot.versionId());
    evidence.put("versionNo", snapshot.versionNo());
    evidence.put("structureSchemaVersion", snapshot.structureSchemaVersion());
    evidence.put("workflowStatus", snapshot.workflowStatus());
    evidence.put("approvalStatus", snapshot.approvalStatus());
    evidence.put("effectiveFrom", snapshot.effectiveFrom());
    evidence.put("effectiveTo", snapshot.effectiveTo());
    evidence.put("configurationHash", snapshot.configurationHash());
    evidence.put("validationFingerprint", snapshot.validationFingerprint());
    evidence.put(
        "statutoryBindingRevision",
        snapshot.statutoryBindingRevision());
    evidence.put(
        "currentStatutoryEvidenceHash",
        snapshot.currentStatutoryEvidenceHash());
    evidence.put("validations", snapshot.validations());
    evidence.put(
        "statutoryEvaluations",
        snapshot.statutoryEvaluations());
    evidence.put("workflowActions", snapshot.workflowActions());
    evidence.put("auditEvents", snapshot.auditEvents());
    evidence.put("domainEvents", snapshot.domainEvents());

    return new LineageView(
        snapshot.identityId(),
        snapshot.versionId(),
        snapshot.versionNo(),
        snapshot.structureSchemaVersion(),
        snapshot.workflowStatus(),
        snapshot.approvalStatus(),
        snapshot.effectiveFrom(),
        snapshot.effectiveTo(),
        snapshot.configurationHash(),
        snapshot.validationFingerprint(),
        snapshot.statutoryBindingRevision(),
        snapshot.currentStatutoryEvidenceHash(),
        snapshot.validations(),
        snapshot.statutoryEvaluations(),
        snapshot.workflowActions(),
        snapshot.auditEvents(),
        snapshot.domainEvents(),
        canonical.hash(evidence),
        SalaryStructureLineageControls.DISCLAIMER);
  }
}
