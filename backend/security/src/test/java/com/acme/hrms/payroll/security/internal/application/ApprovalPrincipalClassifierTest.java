package com.acme.hrms.payroll.security.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ApprovalPrincipalClassifierTest {
  private final ApprovalPrincipalClassifier classifier = new ApprovalPrincipalClassifier();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void detectsLocalServiceIdentityPrefix() {
    assertThat(classifier.isServiceIdentity("service:local-payroll")).isTrue();
  }

  @Test
  void detectsKeycloakServiceAccountClientIdClaim() {
    authenticate("service-subject", "payroll-batch");
    assertThat(classifier.isServiceIdentity("https://issuer.example.test|service-subject"))
        .isTrue();
  }

  @Test
  void interactiveJwtWithoutClientIdRemainsHuman() {
    authenticate("human-approver", null);
    assertThat(classifier.isServiceIdentity("https://issuer.example.test|human-approver"))
        .isFalse();
  }

  private void authenticate(String subject, String clientId) {
    Jwt.Builder builder =
        Jwt.withTokenValue("synthetic")
            .header("alg", "none")
            .issuer("https://issuer.example.test")
            .subject(subject)
            .audience(List.of("payroll-api"))
            .issuedAt(Instant.parse("2026-08-11T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-11T01:00:00Z"));
    if (clientId != null) {
      builder.claim("client_id", clientId);
    }
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(builder.build(), List.of()));
  }
}
