package com.acme.hrms.payroll.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class AuditReader {
  private final JdbcTemplate jdbc;

  public AuditReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public List<AuditEventView> forObject(String objectType, UUID objectId) {
    return jdbc.query("select id,occurred_at,actor,action,object_type,object_id,correlation_id,before_state::text,after_state::text,metadata::text "
            + "from audit.audit_event where tenant_id=? and object_type=? and object_id=? order by occurred_at,id",
        (rs, row) -> new AuditEventView(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
            rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class),
            rs.getObject(7, UUID.class), rs.getString(8), rs.getString(9), rs.getString(10)),
        TenantContext.require(), objectType, objectId);
  }

  public record AuditEventView(UUID id, Instant occurredAt, String actor, String action,
                               String objectType, UUID objectId, UUID correlationId,
                               String beforeState, String afterState, String metadata) {}
}
