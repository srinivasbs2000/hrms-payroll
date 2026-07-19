package com.acme.hrms.payroll.integrations;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStore {
  Optional<SavedResponse> find(String operation, String key);
  void reserve(String operation, String key, String requestHash, Instant expiresAt);
  void complete(String operation, String key, int status, Object response);

  record SavedResponse(String requestHash, Integer status, String body) {
    public boolean completed() { return status != null && body != null; }
  }
}
