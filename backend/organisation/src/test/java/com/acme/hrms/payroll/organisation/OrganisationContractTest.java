package com.acme.hrms.payroll.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class OrganisationContractTest {
  @Test
  void controllerMethodsEnforceTheApprovedPermissionVocabulary() {
    Map<String, String> permissions = Arrays.stream(OrganisationController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(PreAuthorize.class).value()));
    assertThat(permissions).containsEntry("create", "hasAuthority('organisation.create')")
        .containsEntry("list", "hasAuthority('organisation.read')")
        .containsEntry("current", "hasAuthority('organisation.read')")
        .containsEntry("history", "hasAuthority('organisation.read')")
        .containsEntry("addVersion", "hasAuthority('organisation.version.create')")
        .containsEntry("correct", "hasAuthority('organisation.version.correct')")
        .containsEntry("endDate", "hasAuthority('organisation.version.end-date')")
        .containsEntry("approve", "hasAuthority('organisation.approve')")
        .containsEntry("audit", "hasAuthority('audit.read')");
  }

  @Test
  void effectiveRangesAndHierarchyFieldsAreValidatedBeforePersistence() {
    var invalidRange = new OrganisationWriteRequest("LE", "Example", "IN", "INR", null,
        null, LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1));
    assertThatThrownBy(() -> invalidRange.validateFor(OrganisationKind.LEGAL_ENTITY, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("effectiveTo");

    var missingParent = new OrganisationWriteRequest("PSU", "Example", null, null, null,
        null, LocalDate.of(2027, 1, 1), null);
    assertThatThrownBy(() -> missingParent.validateFor(OrganisationKind.PAYROLL_STATUTORY_UNIT, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parentVersionId");
  }
}
