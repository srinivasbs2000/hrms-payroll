package com.acme.hrms.payroll.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class AuditWriter {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public AuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public UUID append(String action, String objectType, UUID objectId,
                     Map<String, ?> before, Map<String, ?> after, Map<String, ?> metadata,
                     String actor) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into audit.audit_event(id,tenant_id,actor,action,object_type,object_id,correlation_id,before_state,after_state,metadata) "
            + "values (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb)",
        id, TenantContext.require(), actor, action, objectType, objectId, CorrelationContext.require(),
        json(before), json(after), json(metadata));
    return id;
  }

  private String json(Map<String, ?> value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Audit state is not serializable", exception);
    }
  }
}
