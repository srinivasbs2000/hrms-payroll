package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.platform.CorrelationContext;
import com.acme.hrms.payroll.platform.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

public final class TenantClaimFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper;

  public TenantClaimFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
        String claim = jwt.getClaimAsString("tenant_id");
        try {
          if (claim == null || claim.isBlank()) throw new IllegalArgumentException("missing");
          TenantContext.set(UUID.fromString(claim));
        } catch (IllegalArgumentException invalidTenant) {
          reject(response, "The access token must contain a valid tenant_id UUID claim.");
          return;
        }
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private void reject(HttpServletResponse response, String detail) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var body = new LinkedHashMap<String, Object>();
    body.put("type", URI.create("urn:problem:invalid-tenant-claim"));
    body.put("title", "Invalid tenant claim");
    body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
    body.put("detail", detail);
    body.put("instance", "tenant-claim");
    body.put("correlationId", CorrelationContext.require());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
