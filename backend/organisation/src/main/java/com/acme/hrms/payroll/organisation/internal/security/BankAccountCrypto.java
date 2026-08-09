package com.acme.hrms.payroll.organisation.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
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

/**
 * Pure application cryptography for employer bank-account numbers.
 *
 * <p>This class intentionally has no persistence or logging dependency. Runtime
 * key material is supplied by the composition root in G02; G01 proves the
 * cryptographic contract without committing secrets or framework binding.
 */
public final class BankAccountCrypto {
  static final int AES_256_KEY_BYTES = 32;
  static final int GCM_IV_BYTES = 12;
  static final int GCM_TAG_BITS = 128;
  static final int FINGERPRINT_BYTES = 32;

  private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String AAD_PREFIX = "hrms-payroll:employer-bank-account:";

  private final String activeKeyVersion;
  private final Map<String, SecretKey> encryptionKeys;
  private final SecretKey fingerprintKey;
  private final SecureRandom secureRandom;

  public BankAccountCrypto(
      String activeKeyVersion,
      Map<String, byte[]> encryptionKeys,
      byte[] fingerprintKey) {
    this(activeKeyVersion, encryptionKeys, fingerprintKey, new SecureRandom());
  }

  BankAccountCrypto(
      String activeKeyVersion,
      Map<String, byte[]> encryptionKeys,
      byte[] fingerprintKey,
      SecureRandom secureRandom) {
    this.activeKeyVersion = requireKeyVersion(activeKeyVersion);
    this.encryptionKeys = immutableEncryptionKeys(encryptionKeys);
    this.fingerprintKey =
        new SecretKeySpec(
            requireFingerprintKey(fingerprintKey),
            HMAC_ALGORITHM);
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");

    if (!this.encryptionKeys.containsKey(this.activeKeyVersion)) {
      throw new IllegalArgumentException(
          "Active bank encryption key version is not configured");
    }
  }

  public static BankAccountCrypto fromBase64(
      String activeKeyVersion,
      Map<String, String> base64EncryptionKeys,
      String base64FingerprintKey) {
    Objects.requireNonNull(base64EncryptionKeys, "base64EncryptionKeys");
    Map<String, byte[]> decoded = new LinkedHashMap<>();
    base64EncryptionKeys.forEach(
        (version, value) ->
            decoded.put(
                version,
                java.util.Base64.getDecoder().decode(
                    Objects.requireNonNull(value, "bank encryption key"))));

    return new BankAccountCrypto(
        activeKeyVersion,
        decoded,
        java.util.Base64.getDecoder().decode(
            Objects.requireNonNull(
                base64FingerprintKey,
                "base64FingerprintKey")));
  }

  public EncryptedValue encrypt(String accountNumber) {
    String plaintext = requirePlaintext(accountNumber);
    String canonical = canonicalize(accountNumber);
    byte[] iv = new byte[GCM_IV_BYTES];
    secureRandom.nextBytes(iv);

    try {
      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          encryptionKeys.get(activeKeyVersion),
          new GCMParameterSpec(GCM_TAG_BITS, iv));
      cipher.updateAAD(aad(activeKeyVersion));

      byte[] ciphertext =
          cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      return new EncryptedValue(
          ciphertext,
          iv,
          activeKeyVersion,
          fingerprintHex(canonical),
          canonical.substring(canonical.length() - 4));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to encrypt employer bank-account data",
          exception);
    }
  }

  public String decrypt(
      byte[] ciphertext,
      byte[] iv,
      String keyVersion) {
    byte[] safeCiphertext =
        requireBytes(ciphertext, 1, "ciphertext");
    byte[] safeIv =
        requireBytes(iv, GCM_IV_BYTES, "iv");
    if (safeIv.length != GCM_IV_BYTES) {
      throw new IllegalArgumentException(
          "Bank-account AES-GCM IV must contain exactly 12 bytes");
    }

    String version = requireKeyVersion(keyVersion);
    SecretKey key = encryptionKeys.get(version);
    if (key == null) {
      throw new IllegalStateException(
          "Bank-account encryption key version is not configured");
    }

    try {
      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(GCM_TAG_BITS, safeIv));
      cipher.updateAAD(aad(version));
      return new String(
          cipher.doFinal(safeCiphertext),
          StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to decrypt employer bank-account data",
          exception);
    }
  }

  public String fingerprint(String accountNumber) {
    return fingerprintHex(canonicalize(accountNumber));
  }

  public boolean fingerprintMatches(
      String accountNumber,
      String expectedLowercaseHex) {
    Objects.requireNonNull(expectedLowercaseHex, "expectedLowercaseHex");
    byte[] expected;
    try {
      expected = HexFormat.of().parseHex(expectedLowercaseHex);
    } catch (IllegalArgumentException exception) {
      return false;
    }
    byte[] actual =
        HexFormat.of().parseHex(fingerprint(accountNumber));
    return MessageDigest.isEqual(actual, expected);
  }

  static String canonicalize(String accountNumber) {
    Objects.requireNonNull(accountNumber, "accountNumber");

    StringBuilder canonical = new StringBuilder();
    accountNumber
        .strip()
        .chars()
        .forEach(
            codePoint -> {
              char value = (char) codePoint;
              if (!Character.isWhitespace(value) && value != '-') {
                canonical.append(value);
              }
            });

    String normalized =
        canonical.toString().toUpperCase(Locale.ROOT);

    if (normalized.length() < 4 || normalized.length() > 64) {
      throw new IllegalArgumentException(
          "Bank-account number must contain between 4 and 64 canonical characters");
    }

    if (!normalized.matches("[A-Z0-9]+")) {
      throw new IllegalArgumentException(
          "Bank-account number may contain only letters, digits, spaces or hyphens");
    }

    return normalized;
  }

  private String fingerprintHex(String canonicalAccountNumber) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(fingerprintKey);
      return HexFormat.of().formatHex(
          mac.doFinal(
              canonicalAccountNumber.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to fingerprint employer bank-account data",
          exception);
    }
  }

  private static byte[] aad(String keyVersion) {
    return (AAD_PREFIX + keyVersion).getBytes(StandardCharsets.UTF_8);
  }

  private static Map<String, SecretKey> immutableEncryptionKeys(
      Map<String, byte[]> source) {
    Objects.requireNonNull(source, "encryptionKeys");
    if (source.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one bank encryption key is required");
    }

    Map<String, SecretKey> keys = new LinkedHashMap<>();
    source.forEach(
        (version, keyBytes) -> {
          String safeVersion = requireKeyVersion(version);
          byte[] safeKey =
              requireBytes(
                  keyBytes,
                  AES_256_KEY_BYTES,
                  "encryptionKey");
          if (safeKey.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException(
                "Every bank encryption key must contain exactly 32 bytes");
          }
          keys.put(
              safeVersion,
              new SecretKeySpec(safeKey, "AES"));
        });

    return Map.copyOf(keys);
  }

  private static byte[] requireFingerprintKey(byte[] key) {
    byte[] safe =
        requireBytes(
            key,
            FINGERPRINT_BYTES,
            "fingerprintKey");
    if (safe.length < FINGERPRINT_BYTES) {
      throw new IllegalArgumentException(
          "Bank-account fingerprint key must contain at least 32 bytes");
    }
    return safe;
  }

  private static byte[] requireBytes(
      byte[] value,
      int minimumLength,
      String name) {
    Objects.requireNonNull(value, name);
    if (value.length < minimumLength) {
      throw new IllegalArgumentException(
          name + " must contain at least " + minimumLength + " bytes");
    }
    return value.clone();
  }

  private static String requireKeyVersion(String value) {
    if (value == null || value.isBlank() || value.length() > 40) {
      throw new IllegalArgumentException(
          "Bank encryption key version is required and must be at most 40 characters");
    }
    return value.trim();
  }

  private static String requirePlaintext(String accountNumber) {
    Objects.requireNonNull(accountNumber, "accountNumber");
    String value = accountNumber.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          "Bank-account number is required");
    }
    canonicalize(value);
    return value;
  }

  public record EncryptedValue(
      byte[] ciphertext,
      byte[] iv,
      String keyVersion,
      String fingerprintHex,
      String lastFour) {
    public EncryptedValue {
      ciphertext =
          Objects.requireNonNull(ciphertext, "ciphertext").clone();
      iv = Objects.requireNonNull(iv, "iv").clone();
      keyVersion =
          Objects.requireNonNull(keyVersion, "keyVersion");
      fingerprintHex =
          Objects.requireNonNull(fingerprintHex, "fingerprintHex");
      lastFour =
          Objects.requireNonNull(lastFour, "lastFour");
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
}
