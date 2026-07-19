package com.acme.hrms.payroll.integrations.internal.infrastructure;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.DomainEvent;
import java.util.Map;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class JdbcOutboxWriter implements OutboxWriter {
  private final JdbcTemplate jdbc;
  private final CanonicalJsonHasher canonical;

  JdbcOutboxWriter(JdbcTemplate jdbc, CanonicalJsonHasher canonical) {
    this.jdbc = jdbc;
    this.canonical = canonical;
  }

  @Override
  public void append(DomainEvent event) {
    String payload = canonical.json(event.payload());
    jdbc.update("insert into integration.outbox_event(id,tenant_id,aggregate_type,aggregate_id,aggregate_version,event_type,event_version,occurred_at,correlation_id,causation_id,payload,headers,partition_key,payload_hash) "
            + "values (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        event.eventId(), event.tenantId(), event.aggregateType(), event.aggregateId(),
        event.aggregateVersion(), event.eventType(), event.eventVersion(), Timestamp.from(event.occurredAt()),
        event.correlationId(), event.causationId(), payload,
        canonical.json(Map.of("eventId", event.eventId(), "tenantId", event.tenantId(),
            "correlationId", event.correlationId())),
        event.tenantId() + ":" + event.aggregateType() + ":" + event.aggregateId(),
        canonical.hash(event.payload()));
  }
}
