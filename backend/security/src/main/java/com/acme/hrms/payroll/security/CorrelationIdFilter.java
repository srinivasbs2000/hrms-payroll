package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.platform.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CorrelationIdFilter extends OncePerRequestFilter {
  static final String MDC_KEY = "correlation_id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID correlationId = parseOrCreate(request.getHeader(CorrelationContext.HEADER_NAME));
    try {
      CorrelationContext.set(correlationId);
      MDC.put(MDC_KEY, correlationId.toString());
      response.setHeader(CorrelationContext.HEADER_NAME, correlationId.toString());
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      CorrelationContext.clear();
    }
  }

  private UUID parseOrCreate(String candidate) {
    if (candidate == null || candidate.isBlank()) return UUID.randomUUID();
    try {
      return UUID.fromString(candidate);
    } catch (IllegalArgumentException ignored) {
      return UUID.randomUUID();
    }
  }
}
