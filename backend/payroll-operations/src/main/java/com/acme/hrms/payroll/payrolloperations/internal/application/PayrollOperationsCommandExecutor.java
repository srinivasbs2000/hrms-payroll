package com.acme.hrms.payroll.payrolloperations.internal.application;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public final class PayrollOperationsCommandExecutor {
  private final TenantTransactionExecutor transactions;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PayrollOperationsCommandExecutor(
      TenantTransactionExecutor transactions,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper,
      Clock clock) {
    this.transactions = transactions;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public <T> T execute(
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<T> work) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }

    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        if (!saved.get().requestHash().equals(requestHash)) {
          throw new ConflictException(
              "Idempotency-Key was already used with a different request");
        }
        if (!saved.get().completed()) {
          throw new ConflictException(
              "Idempotent operation is still in progress");
        }
        try {
          return objectMapper.readValue(saved.get().body(), responseType);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored idempotent response is invalid", exception);
        }
      }

      try {
        idempotency.reserve(
            operation,
            key,
            requestHash,
            clock.instant().plus(Duration.ofHours(24)));
      } catch (IllegalStateException exception) {
        throw new ConflictException(
            "Idempotency-Key is already in use", exception);
      }

      T response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }
}
