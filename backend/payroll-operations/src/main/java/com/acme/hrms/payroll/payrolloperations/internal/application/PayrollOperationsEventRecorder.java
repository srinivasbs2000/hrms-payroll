package com.acme.hrms.payroll.payrolloperations.internal.application;

import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class PayrollOperationsEventRecorder {
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final AuthenticatedActor actor;

  public PayrollOperationsEventRecorder(
      AuditWriter audit,
      DomainEventFactory events,
      OutboxWriter outbox,
      AuthenticatedActor actor) {
    this.audit = audit;
    this.events = events;
    this.outbox = outbox;
    this.actor = actor;
  }

  public void record(
      String action,
      String eventType,
      UUID cycleId,
      long aggregateVersion,
      Map<String, Object> before,
      Map<String, Object> after,
      Map<String, Object> metadata) {
    String principal = actor.require();
    audit.append(
        action,
        PayrollCycleService.OBJECT_TYPE,
        cycleId,
        before,
        after,
        metadata,
        principal);
    outbox.append(events.create(
        eventType,
        1,
        TenantContext.require(),
        null,
        PayrollCycleService.OBJECT_TYPE,
        cycleId,
        aggregateVersion,
        after));
  }
}
