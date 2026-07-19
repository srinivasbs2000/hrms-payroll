package com.acme.hrms.payroll;

import com.acme.hrms.payroll.platform.TenantContext;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/baseline")
@ConditionalOnProperty(name = "payroll.baseline-smoke.enabled", havingValue = "true")
public class BaselineAuthSmokeController {
  @GetMapping("/auth-smoke")
  @PreAuthorize("hasAuthority('payroll.read')")
  public Map<String, String> verifyAuthenticationBoundary() {
    return Map.of("status", "ok", "tenantId", TenantContext.require().toString());
  }
}
