package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyCreateRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.ProrationPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateCellRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateDimensionRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RateTableVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.RoundingPolicyVersionWriteRequest;
import com.acme.hrms.payroll.compensation.ComponentCatalogueControls.StatutoryWageReferenceRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class ComponentCatalogueControlContractTest {

  @Test
  void minimumComponentControlEndpointsAndPermissionsAreExposed() {
    Map<String, String> paths = Arrays.stream(ComponentCatalogueControlController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            java.lang.reflect.Method::getName,
            method -> {
              PostMapping post = method.getAnnotation(PostMapping.class);
              if (post != null) {
                return post.value()[0];
              }
              GetMapping get = method.getAnnotation(GetMapping.class);
              return get.value()[0];
            }));

    assertThat(paths)
        .containsEntry("validateFormula", "/pay-components/formula-validation")
        .containsEntry("dependencies", "/pay-components/{identityId}/dependencies")
        .containsEntry("impact", "/pay-components/{identityId}/impact")
        .containsEntry(
            "statutoryWageReferences",
            "/pay-components/{identityId}/statutory-wage-references")
        .containsEntry("createRateTable", "/component-rate-tables")
        .containsEntry("createRoundingPolicy", "/component-rounding-policies")
        .containsEntry("createProrationPolicy", "/component-proration-policies")
        .containsEntry("correctRateTableVersion", "/component-rate-tables/{identityId}/versions/{versionId}/corrections")
        .containsEntry("retireRateTable", "/component-rate-tables/{identityId}/retirement")
        .containsEntry("correctRoundingPolicyVersion", "/component-rounding-policies/{identityId}/versions/{versionId}/corrections")
        .containsEntry("retireRoundingPolicy", "/component-rounding-policies/{identityId}/retirement")
        .containsEntry("correctProrationPolicyVersion", "/component-proration-policies/{identityId}/versions/{versionId}/corrections")
        .containsEntry("retireProrationPolicy", "/component-proration-policies/{identityId}/retirement");

    Map<String, String> permissions = Arrays.stream(
            ComponentCatalogueControlController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            java.lang.reflect.Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));
    assertThat(permissions)
        .containsEntry("validateFormula", "hasAuthority('compensation.component.read')")
        .containsEntry("createRateTable", "hasAuthority('compensation.component.create')")
        .containsEntry("approveRateTable", "hasAuthority('compensation.component.approve')")
        .containsEntry("createRoundingPolicy", "hasAuthority('compensation.component.create')")
        .containsEntry("approveRoundingPolicy", "hasAuthority('compensation.component.approve')")
        .containsEntry("createProrationPolicy", "hasAuthority('compensation.component.create')")
        .containsEntry("approveProrationPolicy", "hasAuthority('compensation.component.approve')")
        .containsEntry(
            "endDateRateTable",
            "hasAuthority('compensation.component.version.end-date')")
        .containsEntry(
            "endDateRoundingPolicy",
            "hasAuthority('compensation.component.version.end-date')")
        .containsEntry(
            "endDateProrationPolicy",
            "hasAuthority('compensation.component.version.end-date')")
        .containsEntry("correctRateTableVersion", "hasAuthority('compensation.component.version.correct')")
        .containsEntry("correctRoundingPolicyVersion", "hasAuthority('compensation.component.version.correct')")
        .containsEntry("correctProrationPolicyVersion", "hasAuthority('compensation.component.version.correct')")
        .containsEntry("retireRateTable", "hasAuthority('compensation.component.retire')")
        .containsEntry("retireRoundingPolicy", "hasAuthority('compensation.component.retire')")
        .containsEntry("retireProrationPolicy", "hasAuthority('compensation.component.retire')");
  }

  @Test
  void previousPayComponentVersionConstructorRemainsSourceCompatible() {
    PayComponentVersionWriteRequest legacyConstructor = new PayComponentVersionWriteRequest(
        "FIXED", null, new BigDecimal("1000.00"), 2,
        "CASH_EARNING", "BASIC_PAY", "INCREASE", "EMPLOYEE",
        "PAYROLL_BANK", "CURRENT_PERIOD", "SHOW", "SUPPRESS",
        "PROHIBIT", "MONTHLY", "FIXED", "MONTHLY_AMOUNT",
        "DELEGATED", "REGULAR", LocalDate.of(2027, 1, 1), null);

    legacyConstructor.validate();
    assertThat(legacyConstructor.resolvedCalculationPhase()).isEqualTo("INPUT");
    assertThat(legacyConstructor.resolvedResultContract()).isEqualTo("DECIMAL");
  }

  @Test
  void statutoryWageClassificationUsesExactRuleAndRuleVersionPairs() {
    StatutoryWageReferenceRequest valid =
        new StatutoryWageReferenceRequest(UUID.randomUUID(), UUID.randomUUID());
    valid.validate();

    assertThatThrownBy(() -> new StatutoryWageReferenceRequest(null, UUID.randomUUID()).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("statutoryRuleId");
  }

  @Test
  void rateTableRequiresExactDeterministicDimensionKeys() {
    RateTableVersionWriteRequest valid = new RateTableVersionWriteRequest(
        "AMOUNT", "USD", LocalDate.of(2027, 1, 1),
        null,
        List.of(
            new RateDimensionRequest("GRADE", "Grade", "TEXT"),
            new RateDimensionRequest("LEVEL", "Level", "NUMBER")),
        List.of(new RateCellRequest(
            Map.of("GRADE", "A", "LEVEL", "1"), new BigDecimal("12.3456789012"))));
    valid.validate();

    RateTableVersionWriteRequest missingDimension = new RateTableVersionWriteRequest(
        "AMOUNT", "USD", LocalDate.of(2027, 1, 1),
        null,
        valid.dimensions(),
        List.of(new RateCellRequest(Map.of("GRADE", "A"), BigDecimal.ONE)));
    assertThatThrownBy(missingDimension::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly the configured dimensions");

    RateTableVersionWriteRequest nonCanonicalNumber = new RateTableVersionWriteRequest(
        "PERCENTAGE", "PERCENT", LocalDate.of(2027, 1, 1), null,
        valid.dimensions(),
        List.of(new RateCellRequest(
            Map.of("GRADE", "A", "LEVEL", "01"), new BigDecimal("12.5"))));
    assertThatThrownBy(nonCanonicalNumber::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("canonical decimal");

    RateTableVersionWriteRequest badUnit = new RateTableVersionWriteRequest(
        "AMOUNT", "PERCENT", LocalDate.of(2027, 1, 1), null,
        valid.dimensions(), valid.cells());
    assertThatThrownBy(badUnit::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inconsistent");
  }

  @Test
  void roundingEvidenceIsVersionableAndValidated() {
    RoundingPolicyVersionWriteRequest valid = new RoundingPolicyVersionWriteRequest(
        "HALF_EVEN", 2, "FINAL", "SYMMETRIC", LocalDate.of(2027, 1, 1), null);
    valid.validate();

    RoundingPolicyVersionWriteRequest invalid = new RoundingPolicyVersionWriteRequest(
        "UNKNOWN", 2, "FINAL", "SYMMETRIC", LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  void allFiveProrationEventsAreIndependentlyAcceptedAndUnknownEventFailsClosed() {
    Set<String> events = Set.of(
        "JOINING", "EXIT", "UNPAID_LEAVE", "TRANSFER", "SALARY_REVISION");
    for (String event : events) {
      ProrationPolicyCreateRequest request = new ProrationPolicyCreateRequest(
          UUID.randomUUID(),
          event,
          new ProrationPolicyVersionWriteRequest(
              "CALENDAR_DAYS", "PAY_PERIOD", LocalDate.of(2027, 1, 1), null));
      request.validate();
    }

    ProrationPolicyCreateRequest invalid = new ProrationPolicyCreateRequest(
        UUID.randomUUID(),
        "BONUS",
        new ProrationPolicyVersionWriteRequest(
            "CALENDAR_DAYS", "PAY_PERIOD", LocalDate.of(2027, 1, 1), null));
    assertThatThrownBy(invalid::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventType");
  }
}
