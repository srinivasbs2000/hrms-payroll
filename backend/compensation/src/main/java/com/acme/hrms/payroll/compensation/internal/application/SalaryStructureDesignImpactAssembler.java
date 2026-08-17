package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.ChangeView;
import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DependencyView;
import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DesignImpactView;
import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.DownstreamImpactView;
import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls.VersionEvidence;
import com.acme.hrms.payroll.compensation.SalaryStructureDesignImpactControls;
import com.acme.hrms.payroll.compensation.SalaryStructureLineView;
import com.acme.hrms.payroll.compensation.SalaryStructureView;
import com.acme.hrms.payroll.compensation.internal.infrastructure.SalaryStructureDesignImpactRepository.Evidence;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SalaryStructureDesignImpactAssembler {

  public DesignImpactView assemble(
      SalaryStructureView baseline,
      Evidence baselineEvidence,
      List<DependencyView> baselineDependencies,
      SalaryStructureView proposed,
      Evidence proposedEvidence,
      List<DependencyView> proposedDependencies) {
    List<ChangeView> changes = new ArrayList<>();

    scalar(changes, "STRUCTURE", "NAME", baseline.name(), proposed.name());
    scalar(changes, "STRUCTURE", "STRUCTURE_TYPE",
        baseline.structureType(), proposed.structureType());
    scalar(changes, "STRUCTURE", "PAY_FREQUENCY",
        baseline.payFrequency(), proposed.payFrequency());
    scalar(changes, "STRUCTURE", "CURRENCY",
        baseline.currency(), proposed.currency());
    scalar(changes, "STRUCTURE", "CONFIDENTIALITY_LEVEL",
        baseline.confidentialityLevel(), proposed.confidentialityLevel());
    scalar(changes, "TARGET", "TARGET_TYPE",
        baseline.targetType(), proposed.targetType());
    scalar(changes, "TARGET", "TARGET_ANNUAL_AMOUNT",
        baseline.targetAnnualAmount(), proposed.targetAnnualAmount());
    scalar(changes, "TARGET", "TOLERANCE_AMOUNT",
        baseline.toleranceAmount(), proposed.toleranceAmount());
    scalar(changes, "CTC", "CTC_POLICY_VERSION",
        baseline.ctcPolicyVersionId(), proposed.ctcPolicyVersionId());
    scalar(changes, "ELIGIBILITY", "ELIGIBILITY_RULE_VERSION",
        baseline.eligibilityRuleVersionId(), proposed.eligibilityRuleVersionId());
    scalar(changes, "STRUCTURE", "EFFECTIVE_FROM",
        baseline.effectiveFrom(), proposed.effectiveFrom());
    scalar(changes, "STRUCTURE", "EFFECTIVE_TO",
        baseline.effectiveTo(), proposed.effectiveTo());
    scalar(changes, "EVIDENCE", "CONFIGURATION_HASH",
        baseline.configurationHash(), proposed.configurationHash());
    scalar(changes, "EVIDENCE", "VALIDATION_FINGERPRINT",
        baseline.validationFingerprint(), proposed.validationFingerprint());
    scalar(changes, "STATUTORY", "BINDING_REVISION",
        baselineEvidence.statutoryBindingRevision(),
        proposedEvidence.statutoryBindingRevision());
    scalar(changes, "STATUTORY", "EVIDENCE_HASH",
        baselineEvidence.statutoryEvidenceHash(),
        proposedEvidence.statutoryEvidenceHash());
    scalar(changes, "GOVERNANCE", "WORKFLOW_STATUS",
        baselineEvidence.workflowStatus(), proposedEvidence.workflowStatus());
    scalar(changes, "GOVERNANCE", "APPROVAL_STATUS",
        baseline.approvalStatus(), proposed.approvalStatus());

    compareLines(changes, baseline.lines(), proposed.lines());
    compareDependencies(
        changes,
        baselineDependencies,
        proposedDependencies);

    List<DownstreamImpactView> impacts = impacts(
        changes,
        proposedEvidence.workflowStatus());

    VersionEvidence baselineView = evidence(baseline, baselineEvidence);
    VersionEvidence proposedView = evidence(proposed, proposedEvidence);
    String comparisonHash = hash(
        baselineView,
        proposedView,
        changes,
        baselineDependencies,
        proposedDependencies,
        impacts);

    return new DesignImpactView(
        baseline.identityId(),
        baselineView,
        proposedView,
        List.copyOf(changes),
        List.copyOf(baselineDependencies),
        List.copyOf(proposedDependencies),
        impacts,
        comparisonHash,
        SalaryStructureDesignImpactControls.DISCLAIMER);
  }

  private VersionEvidence evidence(
      SalaryStructureView structure,
      Evidence evidence) {
    return new VersionEvidence(
        structure.identityId(),
        structure.versionId(),
        structure.versionSequence(),
        structure.name(),
        evidence.workflowStatus(),
        structure.approvalStatus(),
        structure.configurationHash(),
        structure.validationFingerprint(),
        evidence.statutoryBindingRevision(),
        evidence.statutoryEvidenceHash(),
        structure.effectiveFrom(),
        structure.effectiveTo());
  }

  private void compareLines(
      List<ChangeView> changes,
      List<SalaryStructureLineView> baseline,
      List<SalaryStructureLineView> proposed) {
    Map<String, SalaryStructureLineView> before = lineMap(baseline);
    Map<String, SalaryStructureLineView> after = lineMap(proposed);
    List<String> keys = new ArrayList<>();
    keys.addAll(before.keySet());
    for (String key : after.keySet()) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    keys.sort(String::compareTo);

    for (String key : keys) {
      SalaryStructureLineView oldLine = before.get(key);
      SalaryStructureLineView newLine = after.get(key);
      String oldValue = oldLine == null ? null : lineValue(oldLine);
      String newValue = newLine == null ? null : lineValue(newLine);
      change(changes, "COMPONENT", key, oldValue, newValue);
    }
  }

  private Map<String, SalaryStructureLineView> lineMap(
      List<SalaryStructureLineView> lines) {
    Map<String, SalaryStructureLineView> result = new LinkedHashMap<>();
    lines.stream()
        .sorted(Comparator.comparing(line -> line.componentId().toString()))
        .forEach(line -> result.put(line.componentId().toString(), line));
    return result;
  }

  private String lineValue(SalaryStructureLineView line) {
    return String.join(
        "|",
        safe(line.componentCode()),
        "version=" + line.componentVersionId(),
        "sequence=" + line.sequenceNo(),
        "type=" + safe(line.lineType()),
        "amount=" + value(line.targetAmount()),
        "percentage=" + value(line.targetPercentage()),
        "base=" + safe(line.percentageBaseCode()),
        "minimum=" + value(line.minimumAmount()),
        "maximum=" + value(line.maximumAmount()),
        "mandatory=" + line.mandatory(),
        "override=" + safe(line.overridePolicy()),
        "ctcOrder=" + line.ctcDisplayOrder(),
        "payslipOrder=" + line.payslipDisplayOrder());
  }

  private void compareDependencies(
      List<ChangeView> changes,
      List<DependencyView> baseline,
      List<DependencyView> proposed) {
    Map<String, DependencyView> before = dependencyMap(baseline);
    Map<String, DependencyView> after = dependencyMap(proposed);
    List<String> keys = new ArrayList<>();
    keys.addAll(before.keySet());
    for (String key : after.keySet()) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    keys.sort(String::compareTo);

    for (String key : keys) {
      DependencyView oldDependency = before.get(key);
      DependencyView newDependency = after.get(key);
      String oldValue = oldDependency == null
          ? null : dependencyValue(oldDependency);
      String newValue = newDependency == null
          ? null : dependencyValue(newDependency);
      change(changes, "DEPENDENCY", key, oldValue, newValue);
    }
  }

  private Map<String, DependencyView> dependencyMap(
      List<DependencyView> dependencies) {
    Map<String, DependencyView> result = new LinkedHashMap<>();
    dependencies.stream()
        .sorted(Comparator.comparing(this::dependencyKey))
        .forEach(dependency -> result.put(
            dependencyKey(dependency), dependency));
    return result;
  }

  private String dependencyKey(DependencyView dependency) {
    return dependency.dependencyType()
        + "|" + dependency.objectId()
        + "|" + dependency.role();
  }

  private String dependencyValue(DependencyView dependency) {
    return String.join(
        "|",
        dependency.dependencyType(),
        dependency.objectId().toString(),
        dependency.versionId().toString(),
        safe(dependency.code()),
        safe(dependency.role()),
        safe(dependency.status()));
  }

  private List<DownstreamImpactView> impacts(
      List<ChangeView> changes,
      String proposedWorkflowStatus) {
    List<DownstreamImpactView> impacts = new ArrayList<>();
    boolean designChanged = changes.stream()
        .anyMatch(change -> !"GOVERNANCE".equals(change.area()));
    boolean componentChanged = changes.stream()
        .anyMatch(change -> "COMPONENT".equals(change.area()));
    boolean statutoryChanged = changes.stream()
        .anyMatch(change -> "STATUTORY".equals(change.area())
            || ("DEPENDENCY".equals(change.area())
                && change.key().startsWith("STATUTORY_RULE|")));
    boolean flexChanged = changes.stream()
        .anyMatch(change -> "DEPENDENCY".equals(change.area())
            && (change.key().startsWith("SUPPLEMENTAL_PLAN|")
                || change.key().startsWith("FLEX_BENEFIT_PLAN|")));
    boolean eligibilityChanged = changes.stream()
        .anyMatch(change -> "ELIGIBILITY".equals(change.area()));

    if (designChanged) {
      impacts.add(new DownstreamImpactView(
          "CONFIGURATION_VALIDATION_REQUIRED",
          "REQUIRED",
          "Re-run governed salary-structure validation before approval or publication."));
      impacts.add(new DownstreamImpactView(
          "APPROVAL_REVIEW_REQUIRED",
          "REQUIRED",
          "Maker-checker review must use this comparison evidence before publication."));
    }
    if (componentChanged || statutoryChanged) {
      impacts.add(new DownstreamImpactView(
          "STATUTORY_REEVALUATION_REQUIRED",
          "REQUIRED",
          "Re-evaluate bound statutory and minimum-wage compatibility evidence."));
    }
    if (flexChanged) {
      impacts.add(new DownstreamImpactView(
          "FLEX_POLICY_REVIEW_REQUIRED",
          "REQUIRED",
          "Review dependent supplemental and flexible-benefit policy versions."));
    }
    if (eligibilityChanged) {
      impacts.add(new DownstreamImpactView(
          "ELIGIBILITY_REVIEW_REQUIRED",
          "REQUIRED",
          "Review the changed eligibility dependency before publication."));
    }
    if (!"PUBLISHED".equals(proposedWorkflowStatus)) {
      impacts.add(new DownstreamImpactView(
          "PUBLICATION_REQUIRED",
          "INFO",
          "The proposed version remains design-time configuration until it is published."));
    }
    if (changes.isEmpty()) {
      impacts.add(new DownstreamImpactView(
          "NO_DESIGN_CHANGE",
          "INFO",
          "No configuration or dependency difference was detected."));
    }
    return List.copyOf(impacts);
  }

  private void scalar(
      List<ChangeView> changes,
      String area,
      String key,
      Object before,
      Object after) {
    change(changes, area, key, value(before), value(after));
  }

  private void change(
      List<ChangeView> changes,
      String area,
      String key,
      String before,
      String after) {
    if (Objects.equals(before, after)) {
      return;
    }
    String type;
    if (before == null) {
      type = "ADDED";
    } else if (after == null) {
      type = "REMOVED";
    } else {
      type = "MODIFIED";
    }
    changes.add(new ChangeView(area, key, type, before, after));
  }

  private String value(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal.stripTrailingZeros().toPlainString();
    }
    return value.toString();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String hash(
      VersionEvidence baseline,
      VersionEvidence proposed,
      List<ChangeView> changes,
      List<DependencyView> baselineDependencies,
      List<DependencyView> proposedDependencies,
      List<DownstreamImpactView> impacts) {
    StringBuilder canonical = new StringBuilder();
    canonical.append("baseline=").append(baseline).append('\n');
    canonical.append("proposed=").append(proposed).append('\n');
    changes.forEach(change ->
        canonical.append("change=").append(change).append('\n'));
    baselineDependencies.forEach(dependency ->
        canonical.append("baselineDependency=")
            .append(dependency).append('\n'));
    proposedDependencies.forEach(dependency ->
        canonical.append("proposedDependency=")
            .append(dependency).append('\n'));
    impacts.forEach(impact ->
        canonical.append("impact=").append(impact).append('\n'));

    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
