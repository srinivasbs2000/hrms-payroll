package com.acme.hrms.payroll.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DomainEventFactoryTest {
  @AfterEach void clear() { CorrelationContext.clear(); }

  @Test
  void usesInjectedClockAndRequestCorrelationId() {
    var now = Instant.parse("2026-07-19T10:15:30Z");
    var correlationId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    CorrelationContext.set(correlationId);
    var event = new DomainEventFactory(Clock.fixed(now, ZoneOffset.UTC)).create(
        "payroll.test", 1, UUID.randomUUID(), null, "Test", UUID.randomUUID(), Map.of("kind", "synthetic"));
    assertThat(event.occurredAt()).isEqualTo(now);
    assertThat(event.correlationId()).isEqualTo(correlationId);
  }
}
