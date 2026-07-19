package com.acme.hrms.payroll.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public final class AuthenticatedActor {
  private final String serviceIdentity;

  public AuthenticatedActor(
      @Value("${payroll.audit.service-identity:service:local-payroll}") String serviceIdentity) {
    this.serviceIdentity = serviceIdentity;
  }

  public String require() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) return serviceIdentity;
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      return jwt.getIssuer() + "|" + jwt.getSubject();
    }
    return authentication.getName();
  }
}
