package com.acme.hrms.payroll.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.hrms.payroll.platform.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SecurityBaselineTest {
  private static final String TENANT_A = "00000000-0000-0000-0000-000000000001";

  @Autowired MockMvc mvc;
  @Autowired FilterChainProxy filterChainProxy;
  @MockBean JwtDecoder jwtDecoder;

  @Test
  void validTenantAndPermissionReachRepositoryBoundary() throws Exception {
    mvc.perform(get("/test/repository-access").with(jwt().jwt(jwt -> jwt.subject("operator")
            .claim("tenant_id", TENANT_A)).authorities(() -> "payroll.read")))
        .andExpect(status().isOk()).andExpect(content().string(TENANT_A));
  }

  @Test
  void missingTenantIsRejected() throws Exception {
    String correlationId = "20000000-0000-0000-0000-000000000001";
    mvc.perform(get("/test/repository-access").header("X-Correlation-ID", correlationId)
            .with(jwt().jwt(jwt -> jwt.subject("operator"))))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Correlation-ID", correlationId))
        .andExpect(jsonPath("$.correlationId").value(correlationId));
  }

  @Test
  void malformedTenantIsRejected() throws Exception {
    mvc.perform(get("/test/repository-access").with(jwt().jwt(jwt -> jwt.subject("operator")
            .claim("tenant_id", "not-a-uuid"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    when(jwtDecoder.decode("expired")).thenThrow(new JwtValidationException("expired",
        List.of(new OAuth2Error("invalid_token", "JWT expired", null))));
    mvc.perform(get("/test/repository-access").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingPermissionIsRejected() throws Exception {
    mvc.perform(get("/test/repository-access").with(jwt().jwt(jwt -> jwt.subject("operator")
            .claim("tenant_id", TENANT_A))))
        .andExpect(status().isForbidden());
  }

  @Test
  void realmRolesAndPermissionsAreMapped() {
    Jwt token = Jwt.withTokenValue("synthetic").header("alg", "none")
        .issuer("http://localhost:8081/realms/payroll").subject("operator")
        .audience(List.of("payroll-api")).issuedAt(Instant.parse("2026-07-19T00:00:00Z"))
        .expiresAt(Instant.parse("2026-07-19T01:00:00Z"))
        .claim("tenant_id", TENANT_A)
        .claim("realm_access", Map.of("roles", List.of("PAYROLL_OPERATOR")))
        .claim("permissions", List.of("payroll.read", "organisation.read", "organisation.create",
            "organisation.version.create", "organisation.version.correct",
            "organisation.version.end-date", "organisation.approve", "audit.read")).build();
    var authentication = new PayrollJwtAuthenticationConverter().convert(token);
    assertThat(authentication.getAuthorities()).extracting("authority")
        .containsExactlyInAnyOrder("ROLE_PAYROLL_OPERATOR", "payroll.read", "organisation.read",
            "organisation.create", "organisation.version.create", "organisation.version.correct",
            "organisation.version.end-date", "organisation.approve", "audit.read");
  }

  @Test
  void missingAudienceFailsClosed() {
    Jwt token = Jwt.withTokenValue("synthetic").header("alg", "none").subject("operator")
        .issuedAt(Instant.parse("2026-07-19T00:00:00Z"))
        .expiresAt(Instant.parse("2026-07-19T01:00:00Z")).build();
    assertThat(new AudienceValidator("payroll-api").validate(token).hasErrors()).isTrue();
  }

  @Test
  void tenantFilterIsAfterJwtAndBeforeAuthorizationAndRepositoryAccess() {
    var filters = filterChainProxy.getFilters("/test/repository-access");
    assertThat(indexOf(filters, BearerTokenAuthenticationFilter.class))
        .isLessThan(indexOf(filters, TenantClaimFilter.class));
    assertThat(indexOf(filters, TenantClaimFilter.class))
        .isLessThan(indexOf(filters, AuthorizationFilter.class));
  }

  private int indexOf(List<jakarta.servlet.Filter> filters, Class<?> type) {
    for (int i = 0; i < filters.size(); i++) if (type.isInstance(filters.get(i))) return i;
    return -1;
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
  @Import({SecurityConfiguration.class, TestController.class})
  static class TestApplication {}

  @RestController
  static class TestController {
    @GetMapping("/test/repository-access")
    @PreAuthorize("hasAuthority('payroll.read')")
    String repositoryBoundary() {
      return TenantContext.require().toString();
    }
  }
}
