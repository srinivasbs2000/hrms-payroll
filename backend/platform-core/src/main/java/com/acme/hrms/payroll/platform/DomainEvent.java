package com.acme.hrms.payroll.platform;
import java.time.Instant; import java.util.Map; import java.util.UUID;
public record DomainEvent(UUID eventId,String eventType,int eventVersion,UUID tenantId,Instant occurredAt,UUID correlationId,UUID causationId,String aggregateType,UUID aggregateId,Map<String,Object> payload){}
