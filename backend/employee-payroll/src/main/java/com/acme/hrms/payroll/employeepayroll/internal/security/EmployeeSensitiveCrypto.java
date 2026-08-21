package com.acme.hrms.payroll.employeepayroll.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Pure application cryptography for employee payroll secrets. */
public final class EmployeeSensitiveCrypto {
  private static final int AES_256_KEY_BYTES = 32;
  private static final int GCM_IV_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final int FINGERPRINT_BYTES = 32;
  private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String AAD_PREFIX = "hrms-payroll:employee-sensitive:";

  public enum Domain {
    IDENTIFIER,
    BANK_ACCOUNT,
    IDENTITY_VALUE
  }

  public record EncryptedValue(
      byte[] ciphertext,
      byte[] iv,
      String keyVersion,
      String fingerprint,
      String last4,
      String maskedValue) {
    public EncryptedValue {
      ciphertext = ciphertext.clone();
      iv = iv.clone();
    }

    @Override
    public byte[] ciphertext() {
      return ciphertext.clone();
    }

    @Override
    public byte[] iv() {
      return iv.clone();
    }
  }

  private final String activeKeyVersion;
  private final Map<String, SecretKey> encryptionKeys;
  private final SecretKey fingerprintKey;
  private final SecureRandom secureRandom;

  public EmployeeSensitiveCrypto(
      String activeKeyVersion,
      Map<String, byte[]> encryptionKeys,
      byte[] fingerprintKey) {
    this(activeKeyVersion, encryptionKeys, fingerprintKey, new SecureRandom());
  }

  EmployeeSensitiveCrypto(
      String activeKeyVersion,
      Map<String, byte[]> encryptionKeys,
      byte[] fingerprintKey,
      SecureRandom secureRandom) {
    this.activeKeyVersion = requireText(activeKeyVersion, "activeKeyVersion");
    this.encryptionKeys = keys(encryptionKeys);
    this.fingerprintKey =
        new SecretKeySpec(requireBytes(fingerprintKey, FINGERPRINT_BYTES,
            "fingerprintKey"), HMAC_ALGORITHM);
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    if (!this.encryptionKeys.containsKey(this.activeKeyVersion)) {
      throw new IllegalArgumentException(
          "Active employee-sensitive encryption key version is not configured");
    }
  }

  public static EmployeeSensitiveCrypto fromBase64(
      String activeKeyVersion,
      Map<String, String> base64EncryptionKeys,
      String base64FingerprintKey) {
    Objects.requireNonNull(base64EncryptionKeys, "base64EncryptionKeys");
    Map<String, byte[]> decoded = new LinkedHashMap<>();
    base64EncryptionKeys.forEach(
        (version, value) ->
            decoded.put(
                version,
                Base64.getDecoder().decode(
                    Objects.requireNonNull(value, "encryption key"))));
    return new EmployeeSensitiveCrypto(
        activeKeyVersion,
        decoded,
        Base64.getDecoder().decode(
            Objects.requireNonNull(base64FingerprintKey,
                "base64FingerprintKey")));
  }

  public EncryptedValue encrypt(Domain domain, String value) {
    Objects.requireNonNull(domain, "domain");
    String plaintext = requirePlaintext(value);
    String canonical = canonical(domain, value);
    byte[] iv = new byte[GCM_IV_BYTES];
    secureRandom.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          encryptionKeys.get(activeKeyVersion),
          new GCMParameterSpec(GCM_TAG_BITS, iv));
      cipher.updateAAD(aad(domain, activeKeyVersion));
      byte[] ciphertext =
          cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return new EncryptedValue(
          ciphertext,
          iv,
          activeKeyVersion,
          fingerprintHex(domain, canonical),
          last4(canonical),
          mask(domain, canonical));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to encrypt employee-sensitive data", exception);
    }
  }

  public String decrypt(
      Domain domain,
      byte[] ciphertext,
      byte[] iv,
      String keyVersion) {
    Objects.requireNonNull(domain, "domain");
    byte[] safeCiphertext = requireBytes(ciphertext, 1, "ciphertext");
    byte[] safeIv = requireBytes(iv, GCM_IV_BYTES, "iv");
    if (safeIv.length != GCM_IV_BYTES) {
      throw new IllegalArgumentException(
          "Employee-sensitive AES-GCM IV must contain exactly 12 bytes");
    }
    String version = requireText(keyVersion, "keyVersion");
    SecretKey key = encryptionKeys.get(version);
    if (key == null) {
      throw new IllegalStateException(
          "Employee-sensitive encryption key version is not configured");
    }
    try {
      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(GCM_TAG_BITS, safeIv));
      cipher.updateAAD(aad(domain, version));
      return new String(
          cipher.doFinal(safeCiphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to decrypt employee-sensitive data", exception);
    }
  }

  public String fingerprint(Domain domain, String value) {
    Objects.requireNonNull(domain, "domain");
    return fingerprintHex(domain, canonical(domain, value));
  }

  public boolean fingerprintMatches(
      Domain domain,
      String value,
      String expectedLowercaseHex) {
    if (expectedLowercaseHex == null) {
      return false;
    }
    try {
      return MessageDigest.isEqual(
          HexFormat.of().parseHex(fingerprint(domain, value)),
          HexFormat.of().parseHex(expectedLowercaseHex));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public String maskName(String value) {
    String v = requirePlaintext(value).strip();
    if (v.length() == 1) {
      return "*";
    }
    int visible = Math.min(2, v.length());
    return v.substring(0, visible) + "*".repeat(Math.max(1, v.length() - visible));
  }

  private String fingerprintHex(Domain domain, String canonical) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(fingerprintKey);
      mac.update((domain.name() + "\0").getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(
          mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to fingerprint employee-sensitive data", exception);
    }
  }

  private static byte[] aad(Domain domain, String keyVersion) {
    return (AAD_PREFIX + domain.name() + ":" + keyVersion)
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String canonical(Domain domain, String value) {
    String stripped = requirePlaintext(value).strip();
    String normalized;
    if (domain == Domain.IDENTITY_VALUE) {
      normalized = stripped.replaceAll("\\s+", " ")
          .toUpperCase(Locale.ROOT);
    } else {
      normalized = stripped.replaceAll("[\\s-]", "")
          .toUpperCase(Locale.ROOT);
      if (!normalized.matches("[A-Z0-9]+")) {
        throw new IllegalArgumentException(
            "Identifier/account value contains unsupported characters");
      }
    }
    if (normalized.length() < 2 || normalized.length() > 128) {
      throw new IllegalArgumentException(
          "Employee-sensitive value must contain 2 to 128 canonical characters");
    }
    return normalized;
  }

  private static String mask(Domain domain, String canonical) {
    if (domain == Domain.IDENTITY_VALUE) {
      int visible = Math.min(2, canonical.length());
      return canonical.substring(0, visible)
          + "*".repeat(Math.max(1, canonical.length() - visible));
    }
    String suffix = last4(canonical);
    return "*".repeat(Math.max(4, canonical.length() - suffix.length())) + suffix;
  }

  private static String last4(String canonical) {
    return canonical.substring(Math.max(0, canonical.length() - 4));
  }

  private static Map<String, SecretKey> keys(Map<String, byte[]> raw) {
    Objects.requireNonNull(raw, "encryptionKeys");
    if (raw.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one employee-sensitive encryption key is required");
    }
    Map<String, SecretKey> result = new LinkedHashMap<>();
    raw.forEach(
        (version, bytes) -> {
          String safeVersion = requireText(version, "key version");
          byte[] safe = requireBytes(bytes, AES_256_KEY_BYTES, "encryptionKey");
          if (safe.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException(
                "Employee-sensitive AES key must contain exactly 32 bytes");
          }
          result.put(safeVersion, new SecretKeySpec(safe, "AES"));
        });
    return Map.copyOf(result);
  }

  private static String requirePlaintext(String value) {
    return requireText(value, "plaintext");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.strip();
  }

  private static byte[] requireBytes(byte[] value, int min, String name) {
    Objects.requireNonNull(value, name);
    if (value.length < min) {
      throw new IllegalArgumentException(name + " is too short");
    }
    return value.clone();
  }
}
