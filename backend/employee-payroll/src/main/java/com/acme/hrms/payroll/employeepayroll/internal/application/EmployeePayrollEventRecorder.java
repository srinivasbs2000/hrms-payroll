package com.acme.hrms.payroll.employeepayroll.internal.application;

import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class EmployeePayrollEventRecorder {
  private final AuditWriter audit;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final AuthenticatedActor actor;

  public EmployeePayrollEventRecorder(
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
      String objectType,
      String eventType,
      UUID objectId,
      long aggregateVersion,
      Map<String, Object> before,
      Map<String, Object> after,
      Map<String, Object> metadata) {
    String principal = actor.require();
    audit.append(
        action,
        objectType,
        objectId,
        before,
        after,
        metadata,
        principal);
    outbox.append(events.create(
        eventType,
        1,
        TenantContext.require(),
        null,
        objectType,
        objectId,
        aggregateVersion,
        after));
  }
}
