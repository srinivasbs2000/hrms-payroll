package com.acme.hrms.payroll.integrations;

import com.acme.hrms.payroll.platform.DomainEvent;

public interface OutboxWriter {
  void append(DomainEvent event);
}
