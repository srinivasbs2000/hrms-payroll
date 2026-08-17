package com.acme.hrms.payroll.compensation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalaryStructureDesignImpactControls {
  public static final String DISCLAIMER =
      "DESIGN-TIME SALARY-STRUCTURE COMPARISON — "
          + "NOT AN EMPLOYEE PAYROLL, TAX OR STATUTORY RESULT";

  private SalaryStructureDesignImpactControls() {}

  public record VersionEvidence(
      UUID identityId,
      UUID versionId,
      int versionSequence,
      String name,
      String workflowStatus,
      String approvalStatus,
      String configurationHash,
      String validationFingerprint,
      long statutoryBindingRevision,
      String statutoryEvidenceHash,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {}

  public record DependencyView(
      String dependencyType,
      UUID objectId,
      UUID versionId,
      String code,
      String role,
      String status) {}

  public record ChangeView(
      String area,
      String key,
      String changeType,
      String beforeValue,
      String afterValue) {}

  public record DownstreamImpactView(
      String impactCode,
      String severity,
      String detail) {}

  public record DesignImpactView(
      UUID identityId,
      VersionEvidence baseline,
      VersionEvidence proposed,
      List<ChangeView> changes,
      List<DependencyView> baselineDependencies,
      List<DependencyView> proposedDependencies,
      List<DownstreamImpactView> downstreamImpacts,
      String comparisonHash,
      String disclaimer) {}
}
