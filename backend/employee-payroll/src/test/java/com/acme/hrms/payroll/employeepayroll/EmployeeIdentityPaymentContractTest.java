package com.acme.hrms.payroll.employeepayroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.hrms.payroll.employeepayroll.internal.application.EmployeeIdentityPaymentService;
import com.acme.hrms.payroll.employeepayroll.internal.infrastructure.EmployeeIdentityPaymentRepository;
import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCryptoProvider;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class EmployeeIdentityPaymentContractTest {
  @Test
  void everyEipEndpointCarriesLeastPrivilegeAuthority() {
    Map<String, String> authorities =
        java.util.Arrays.stream(EmployeeIdentityPaymentController.class.getDeclaredMethods())
            .filter(this::isEndpoint)
            .collect(
                Collectors.toMap(
                    Method::getName,
                    method -> {
                      PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
                      assertThat(guard)
                          .as(method.getName() + " must be permission guarded")
                          .isNotNull();
                      return guard.value();
                    }));

    assertThat(authorities.get("revealIdentifier"))
        .contains("employee-payroll.identifier.reveal");
    assertThat(authorities.get("revealBank"))
        .contains("employee-payroll.bank-account.reveal");
    assertThat(authorities.get("resolveMismatch"))
        .contains("employee-payroll.identity-mismatch.resolve");
    assertThat(authorities.get("approveInstruction"))
        .contains("employee-payroll.payment-instruction.approve");
    assertThat(authorities.get("clearRestriction"))
        .contains("employee-payroll.payment-restriction.clear");
    assertThat(authorities.get("readiness"))
        .contains("employee-payroll.payment-readiness.read");
  }

  @Test
  void springManagedEipTypesRemainProxyable() {
    assertThat(Modifier.isFinal(EmployeeIdentityPaymentController.class.getModifiers()))
        .as("PreAuthorize controller must remain CGLIB-proxyable")
        .isFalse();
    assertThat(Modifier.isFinal(EmployeeIdentityPaymentService.class.getModifiers()))
        .as("Spring service must remain proxyable")
        .isFalse();
    assertThat(Modifier.isFinal(EmployeeIdentityPaymentRepository.class.getModifiers()))
        .as("Repository exception-translation proxy must remain proxyable")
        .isFalse();
    assertThat(Modifier.isFinal(EmployeeSensitiveCryptoProvider.class.getModifiers()))
        .as("Spring component must remain proxyable")
        .isFalse();
  }

  private boolean isEndpoint(Method method) {
    return method.getAnnotation(GetMapping.class) != null
        || method.getAnnotation(PostMapping.class) != null;
  }
}
