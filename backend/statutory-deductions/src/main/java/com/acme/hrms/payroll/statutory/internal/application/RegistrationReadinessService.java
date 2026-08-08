package com.acme.hrms.payroll.statutory.internal.application;

import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.acme.hrms.payroll.statutory.RegistrationReadinessFindingView;
import com.acme.hrms.payroll.statutory.RegistrationReadinessRequest;
import com.acme.hrms.payroll.statutory.RegistrationReadinessView;
import com.acme.hrms.payroll.statutory.internal.infrastructure.RegistrationReadinessRepository;
import com.acme.hrms.payroll.statutory.internal.infrastructure.RegistrationReadinessRepository.Candidate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistrationReadinessService {
  private final RegistrationReadinessRepository repository;
  private final TenantTransactionExecutor transactions;

  public RegistrationReadinessService(
      RegistrationReadinessRepository repository,
      TenantTransactionExecutor transactions) {
    this.repository = repository;
    this.transactions = transactions;
  }

  public RegistrationReadinessView evaluate(
      RegistrationReadinessRequest request) {
    return transactions.read(
        () -> evaluateInsideTransaction(request));
  }

  private RegistrationReadinessView evaluateInsideTransaction(
      RegistrationReadinessRequest request) {
    List<Candidate> candidates =
        repository.effectiveCandidates(request);
    List<RegistrationReadinessFindingView> findings =
        new ArrayList<>();

    UUID selectedVersion = null;

    if (candidates.isEmpty()) {
      if (repository.expiredRegistrationExists(request)) {
        findings.add(
            blocker(
                "REGISTRATION_EXPIRED",
                "The applicable registration has expired"));
      } else {
        findings.add(
            blocker(
                "REGISTRATION_MISSING",
                "No effective active registration exists"));
      }
    } else if (candidates.size() > 1) {
      findings.add(
          blocker(
              "REGISTRATION_MULTIPLE",
              "Multiple effective registrations match the same owner, type and jurisdiction"));
    } else {
      Candidate candidate = candidates.get(0);
      selectedVersion = candidate.versionId();

      if ("SUSPENDED".equals(candidate.status())) {
        findings.add(
            blocker(
                "REGISTRATION_SUSPENDED",
                "The applicable registration is suspended"));
      }

      if (!parentEffective(candidate, request.asOf())) {
        findings.add(
            blocker(
                "PARENT_REGISTRATION_INVALID",
                "The required parent registration is not active for the readiness date"));
      }

      LocalDate effectiveTo = candidate.effectiveTo();
      if (effectiveTo != null
          && !effectiveTo.isAfter(
              request.asOf().plusDays(
                  request.warningHorizonDays()))) {
        findings.add(
            warning(
                "REGISTRATION_EXPIRING_SOON",
                "The registration expires within the configured warning horizon"));
      }
    }

    if (repository.renewalDraftExists(request)) {
      findings.add(
          warning(
              "REGISTRATION_RENEWAL_DRAFT",
              "A future registration renewal is in progress"));
    }

    boolean ready =
        findings.stream()
            .noneMatch(
                finding ->
                    "BLOCKER".equals(finding.severity()));

    return new RegistrationReadinessView(
        request.registrationTypeId(),
        request.ownerKind(),
        request.ownerId(),
        request.payrollJurisdictionId(),
        request.asOf(),
        ready,
        selectedVersion,
        List.copyOf(findings));
  }

  private boolean parentEffective(
      Candidate candidate,
      LocalDate asOf) {
    if (candidate.parentVersionId() == null) {
      return true;
    }
    if (!"ACTIVE".equals(candidate.parentStatus())) {
      return false;
    }
    if (candidate.parentFrom() == null
        || candidate.parentFrom().isAfter(asOf)) {
      return false;
    }
    return candidate.parentTo() == null
        || candidate.parentTo().isAfter(asOf);
  }

  private RegistrationReadinessFindingView blocker(
      String code,
      String message) {
    return new RegistrationReadinessFindingView(
        code,
        "BLOCKER",
        message);
  }

  private RegistrationReadinessFindingView warning(
      String code,
      String message) {
    return new RegistrationReadinessFindingView(
        code,
        "WARNING",
        message);
  }
}
