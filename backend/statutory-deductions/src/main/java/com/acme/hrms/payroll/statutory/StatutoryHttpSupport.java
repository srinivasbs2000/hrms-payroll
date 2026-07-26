package com.acme.hrms.payroll.statutory;

final class StatutoryHttpSupport {
  private StatutoryHttpSupport() {}

  static long expectedVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version");
    }

    String value = ifMatch.trim();
    if (value.startsWith("W/")) {
      value = value.substring(2).trim();
    }
    if (value.length() >= 2
        && value.startsWith("\"")
        && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    if (!value.matches("[0-9]+")) {
      throw new IllegalArgumentException(
          "If-Match must contain a numeric version");
    }

    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "If-Match version is outside the supported range", exception);
    }
  }

  static void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null
        || idempotencyKey.trim().length() < 8
        || idempotencyKey.trim().length() > 120) {
      throw new IllegalArgumentException(
          "Idempotency-Key must contain between 8 and 120 characters");
    }
  }
}
