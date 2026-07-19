package com.acme.hrms.payroll.security;

import com.acme.hrms.payroll.platform.CorrelationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper objectMapper;

  public SecurityProblemWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    write(request, response, 401, "Authentication required", "A valid access token is required.");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    write(request, response, 403, "Permission denied", "The authenticated principal lacks the required permission.");
  }

  private void write(HttpServletRequest request, HttpServletResponse response, int status, String title, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var body = new LinkedHashMap<String, Object>();
    body.put("type", URI.create("urn:problem:http:" + status));
    body.put("title", title);
    body.put("status", status);
    body.put("detail", detail);
    body.put("instance", request.getRequestURI());
    body.put("correlationId", CorrelationContext.require());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
