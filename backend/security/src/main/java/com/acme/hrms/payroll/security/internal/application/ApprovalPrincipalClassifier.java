package com.acme.hrms.payroll.security.internal.application;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalPrincipalClassifier {
  public boolean isServiceIdentity(String actorId) {
    if (actorId != null && actorId.startsWith("service:")) {
      return true;
    }
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      String clientId = jwtAuthentication.getToken().getClaimAsString("client_id");
      return clientId != null && !clientId.isBlank();
    }
    return false;
  }
}
