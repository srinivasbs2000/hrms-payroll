package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.LifecycleCommentRequest;
import com.acme.hrms.payroll.compensation.SalaryStructureLifecycleControls.RejectionRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SalaryStructureLifecycleContractTest {
  @Test
  void controllerSeparatesMakerCheckerPublisherAndReadAuthorities() {
    Map<String, String> permissions = Arrays.stream(
            SalaryStructureLifecycleController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(
            Method::getName,
            method -> method.getAnnotation(PreAuthorize.class).value()));

    assertThat(permissions)
        .containsEntry("lifecycle", "hasAuthority('compensation.structure.read')")
        .containsEntry("submit", "hasAuthority('compensation.structure.submit')")
        .containsEntry("reject", "hasAuthority('compensation.structure.approve')")
        .containsEntry("publish", "hasAuthority('compensation.structure.publish')");
  }

  @Test
  void rejectionRequiresAnExplicitBoundedReason() {
    new RejectionRequest("Validation impact needs correction").validate();
    assertThatThrownBy(() -> new RejectionRequest(" ").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejection reason");
  }

  @Test
  void optionalLifecycleCommentsAreBounded() {
    new LifecycleCommentRequest(null).validate();
    new LifecycleCommentRequest("Ready for controlled review").validate();
    assertThatThrownBy(() -> new LifecycleCommentRequest("x".repeat(1001)).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1000");
  }
}
