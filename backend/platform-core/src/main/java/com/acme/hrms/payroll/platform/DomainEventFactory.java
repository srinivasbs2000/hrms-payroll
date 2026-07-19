package com.acme.hrms.payroll.platform;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class DomainEventFactory {
  private final Clock clock;

  public DomainEventFactory(Clock clock) {
    this.clock = clock;
  }

  public DomainEvent create(
      String eventType,
      int eventVersion,
      UUID tenantId,
      UUID causationId,
      String aggregateType,
      UUID aggregateId,
      Map<String, Object> payload) {
    return new DomainEvent(UUID.randomUUID(), eventType, eventVersion, tenantId, clock.instant(),
        CorrelationContext.require(), causationId, aggregateType, aggregateId, Map.copyOf(payload));
  }
}
