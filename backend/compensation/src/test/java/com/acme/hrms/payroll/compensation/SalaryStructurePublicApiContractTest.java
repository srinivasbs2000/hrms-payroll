package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class SalaryStructurePublicApiContractTest {
  @Test
  void publicCompletionAddsExactVersionAndCanonicalDependencyImpactReads()
      throws Exception {
    Method version = SalaryStructurePublicApiController.class.getDeclaredMethod(
        "version",
        java.util.UUID.class,
        java.util.UUID.class);
    Method dependencyImpact =
        SalaryStructurePublicApiController.class.getDeclaredMethod(
            "dependencyImpact",
            java.util.UUID.class,
            java.util.UUID.class,
            java.util.UUID.class);

    assertThat(version.getAnnotation(GetMapping.class).value())
        .containsExactly("/{identityId}/versions/{versionId}");
    assertThat(dependencyImpact.getAnnotation(GetMapping.class).value())
        .containsExactly("/{identityId}/dependency-impact");

    assertThat(version.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAuthority('compensation.structure.read')");
    assertThat(dependencyImpact.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAuthority('compensation.structure.read')");
  }

  @Test
  void completedSalaryStructureApiRetainsCoreIdentityVersionAndSimulationReads()
      throws Exception {
    Map<String, String> getRoutes = Arrays.stream(
            SalaryStructureController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(GetMapping.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> String.join(
                ",",
                method.getAnnotation(GetMapping.class).value())));

    Map<String, String> postRoutes = Arrays.stream(
            SalaryStructureController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> String.join(
                ",",
                method.getAnnotation(PostMapping.class).value())));

    assertThat(getRoutes)
        .containsEntry("list", "")
        .containsEntry("current", "/{identityId}")
        .containsEntry("history", "/{identityId}/versions")
        .containsEntry(
            "validations",
            "/{identityId}/versions/{versionId}/validations")
        .containsEntry("audit", "/{identityId}/audit");

    assertThat(postRoutes)
        .containsEntry("create", "")
        .containsEntry("addVersion", "/{identityId}/versions")
        .containsEntry(
            "correct",
            "/{identityId}/versions/{versionId}/corrections")
        .containsEntry(
            "simulate",
            "/{identityId}/versions/{versionId}/simulations")
        .containsEntry(
            "bindValidation",
            "/{identityId}/versions/{versionId}/validations/{validationId}/binding")
        .containsEntry(
            "endDate",
            "/{identityId}/versions/{versionId}/end-date")
        .containsEntry(
            "approve",
            "/{identityId}/versions/{versionId}/approval");
  }

  @Test
  void governedLifecycleAndComparisonRoutesRemainPartOfThePublicContract()
      throws Exception {
    Map<String, String> lifecycleRoutes = Arrays.stream(
            SalaryStructureLifecycleController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> String.join(
                ",",
                method.getAnnotation(PostMapping.class).value())));

    Method lifecycle =
        SalaryStructureLifecycleController.class.getDeclaredMethod(
            "lifecycle",
            java.util.UUID.class,
            java.util.UUID.class);
    Method designImpact =
        SalaryStructureDesignImpactController.class.getDeclaredMethod(
            "compare",
            java.util.UUID.class,
            java.util.UUID.class,
            java.util.UUID.class);

    assertThat(lifecycleRoutes)
        .containsEntry(
            "submit",
            "/{identityId}/versions/{versionId}/submission")
        .containsEntry(
            "reject",
            "/{identityId}/versions/{versionId}/rejection")
        .containsEntry(
            "publish",
            "/{identityId}/versions/{versionId}/publication");

    assertThat(lifecycle.getAnnotation(GetMapping.class).value())
        .containsExactly(
            "/{identityId}/versions/{versionId}/lifecycle");
    assertThat(designImpact.getAnnotation(GetMapping.class).value())
        .containsExactly("/{identityId}/design-impact");
  }

  @Test
  void flexBenefitPolicyApiRemainsExposedByTheCompletedStructureCapability()
      throws Exception {
    Map<String, String> getRoutes = Arrays.stream(
            FlexBenefitPlanController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(GetMapping.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> String.join(
                ",",
                method.getAnnotation(GetMapping.class).value())));

    Map<String, String> postRoutes = Arrays.stream(
            FlexBenefitPlanController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> String.join(
                ",",
                method.getAnnotation(PostMapping.class).value())));

    assertThat(getRoutes)
        .containsEntry("list", "")
        .containsEntry("current", "/{identityId}")
        .containsEntry("history", "/{identityId}/versions");

    assertThat(postRoutes)
        .containsEntry("create", "")
        .containsEntry("addVersion", "/{identityId}/versions")
        .containsEntry(
            "correct",
            "/{identityId}/versions/{versionId}/corrections")
        .containsEntry(
            "approve",
            "/{identityId}/versions/{versionId}/approval");
  }
}
