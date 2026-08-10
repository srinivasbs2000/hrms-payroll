package com.acme.hrms.payroll.organisation.internal.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class BankAccountCryptoProvider {
  private static final String ACTIVE_KEY =
      "PAYROLL_BANK_ACTIVE_KEY_VERSION";
  private static final String ENCRYPTION_KEYS =
      "PAYROLL_BANK_ENCRYPTION_KEYS";
  private static final String FINGERPRINT_KEY =
      "PAYROLL_BANK_FINGERPRINT_KEY";

  private final Environment environment;

  public BankAccountCryptoProvider(Environment environment) {
    this.environment = environment;
  }

  public BankAccountCrypto require() {
    String active = required(ACTIVE_KEY);
    String keys = required(ENCRYPTION_KEYS);
    String fingerprint = required(FINGERPRINT_KEY);

    Map<String, String> parsed = new LinkedHashMap<>();
    for (String entry : keys.split(";")) {
      if (entry.isBlank()) {
        continue;
      }
      int separator = entry.indexOf('=');
      if (separator <= 0 || separator == entry.length() - 1) {
        throw new IllegalStateException(
            "PAYROLL_BANK_ENCRYPTION_KEYS must use version=base64 entries");
      }
      String version = entry.substring(0, separator).trim();
      String value = entry.substring(separator + 1).trim();
      if (version.isEmpty() || value.isEmpty()) {
        throw new IllegalStateException(
            "PAYROLL_BANK_ENCRYPTION_KEYS contains an empty version or value");
      }
      if (parsed.putIfAbsent(version, value) != null) {
        throw new IllegalStateException(
            "PAYROLL_BANK_ENCRYPTION_KEYS contains a duplicate version");
      }
    }
    if (parsed.isEmpty()) {
      throw new IllegalStateException(
          "PAYROLL_BANK_ENCRYPTION_KEYS must configure at least one key");
    }

    try {
      return BankAccountCrypto.fromBase64(active, parsed, fingerprint);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Employer bank-account cryptography is misconfigured",
          exception);
    }
  }

  private String required(String name) {
    String value = environment.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name + " is required for employer bank-account operations");
    }
    return value.trim();
  }
}
