package com.acme.hrms.payroll.platform;

import java.util.Optional;
import java.util.UUID;

public final class CorrelationContext {
  public static final String HEADER_NAME = "X-Correlation-ID";
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  public static UUID require() {
    UUID id = CURRENT.get();
    if (id == null) throw new IllegalStateException("Correlation context missing");
    return id;
  }

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static void set(UUID id) {
    CURRENT.set(id);
  }

  public static void clear() {
    CURRENT.remove();
  }
}
