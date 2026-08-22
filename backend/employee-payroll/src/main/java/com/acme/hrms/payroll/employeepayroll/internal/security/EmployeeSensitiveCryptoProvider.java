package com.acme.hrms.payroll.employeepayroll.internal.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmployeeSensitiveCryptoProvider {
  private final EmployeeSensitiveCrypto crypto;

  public EmployeeSensitiveCryptoProvider() {
    this(System.getenv());
  }

  EmployeeSensitiveCryptoProvider(Map<String, String> environment) {
    String active =
        trim(environment.get("PAYROLL_EMPLOYEE_SENSITIVE_ACTIVE_KEY_VERSION"));
    String encodedKeys =
        trim(environment.get("PAYROLL_EMPLOYEE_SENSITIVE_ENCRYPTION_KEYS"));
    String fingerprint =
        trim(environment.get("PAYROLL_EMPLOYEE_SENSITIVE_FINGERPRINT_KEY"));

    if (active == null && encodedKeys == null && fingerprint == null) {
      this.crypto = null;
      return;
    }
    if (active == null || encodedKeys == null || fingerprint == null) {
      throw new IllegalStateException(
          "All PAYROLL_EMPLOYEE_SENSITIVE_* key settings must be supplied together");
    }

    Map<String, String> keys = new LinkedHashMap<>();
    for (String token : encodedKeys.split(",")) {
      String[] pair = token.trim().split(":", 2);
      if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
        throw new IllegalStateException(
            "PAYROLL_EMPLOYEE_SENSITIVE_ENCRYPTION_KEYS must use version:base64 entries");
      }
      if (keys.put(pair[0].trim(), pair[1].trim()) != null) {
        throw new IllegalStateException(
            "Duplicate employee-sensitive encryption key version");
      }
    }

    this.crypto =
        EmployeeSensitiveCrypto.fromBase64(active, keys, fingerprint);
  }

  public EmployeeSensitiveCrypto require() {
    if (crypto == null) {
      throw new IllegalStateException(
          "Employee-sensitive cryptography is not configured");
    }
    return crypto;
  }

  private static String trim(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
