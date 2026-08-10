package com.acme.hrms.payroll.organisation.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BankAccountCryptoTest {
  private static final byte[] KEY_V1 = sequence(1);
  private static final byte[] KEY_V2 = sequence(33);
  private static final byte[] FINGERPRINT_KEY = sequence(65);

  @Test
  void encryptsWithRandomIvAndDecryptsWithoutExposingPlaintext() {
    BankAccountCrypto crypto =
        new BankAccountCrypto(
            "v2",
            Map.of("v1", KEY_V1, "v2", KEY_V2),
            FINGERPRINT_KEY);

    var first = crypto.encrypt("0012-3456 7890");
    var second = crypto.encrypt("0012-3456 7890");

    assertThat(first.keyVersion()).isEqualTo("v2");
    assertThat(first.iv()).hasSize(12);
    assertThat(first.lastFour()).isEqualTo("7890");
    assertThat(first.fingerprintHex())
        .hasSize(64)
        .isEqualTo(second.fingerprintHex());
    assertThat(first.iv()).isNotEqualTo(second.iv());
    assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    assertThat(new String(first.ciphertext(), StandardCharsets.UTF_8))
        .doesNotContain("0012")
        .doesNotContain("7890");
    assertThat(
            crypto.decrypt(
                first.ciphertext(),
                first.iv(),
                first.keyVersion()))
        .isEqualTo("0012-3456 7890");
  }

  @Test
  void fingerprintCanonicalizesFormattingAndUsesConstantTimeComparison() {
    BankAccountCrypto crypto =
        new BankAccountCrypto(
            "v1",
            Map.of("v1", KEY_V1),
            FINGERPRINT_KEY);

    String fingerprint = crypto.fingerprint("ab12-34 56");

    assertThat(fingerprint)
        .isEqualTo(crypto.fingerprint("AB123456"));
    assertThat(
            crypto.fingerprintMatches(
                "a b 1 2 3 4 5 6",
                fingerprint))
        .isTrue();
    assertThat(
            crypto.fingerprintMatches(
                "AB123457",
                fingerprint))
        .isFalse();
  }

  @Test
  void keepsPriorDecryptKeyDuringRotation() {
    BankAccountCrypto oldCrypto =
        new BankAccountCrypto(
            "v1",
            Map.of("v1", KEY_V1),
            FINGERPRINT_KEY);
    var encrypted = oldCrypto.encrypt("9999000011112222");

    Map<String, byte[]> rotatedKeys = new LinkedHashMap<>();
    rotatedKeys.put("v1", KEY_V1);
    rotatedKeys.put("v2", KEY_V2);
    BankAccountCrypto rotated =
        new BankAccountCrypto(
            "v2",
            rotatedKeys,
            FINGERPRINT_KEY);

    assertThat(
            rotated.decrypt(
                encrypted.ciphertext(),
                encrypted.iv(),
                "v1"))
        .isEqualTo("9999000011112222");
    assertThat(rotated.encrypt("12345678").keyVersion())
        .isEqualTo("v2");
  }

  @Test
  void rejectsUnknownKeyVersionAndTamperedCiphertext() {
    BankAccountCrypto crypto =
        new BankAccountCrypto(
            "v1",
            Map.of("v1", KEY_V1),
            FINGERPRINT_KEY);
    var encrypted = crypto.encrypt("1234567890");

    assertThatThrownBy(
            () ->
                crypto.decrypt(
                    encrypted.ciphertext(),
                    encrypted.iv(),
                    "missing"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("key version");

    byte[] tampered = encrypted.ciphertext();
    tampered[0] ^= 0x01;

    assertThatThrownBy(
            () ->
                crypto.decrypt(
                    tampered,
                    encrypted.iv(),
                    encrypted.keyVersion()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to decrypt");
  }

  @Test
  void defensivelyCopiesSensitiveByteArrays() {
    byte[] mutableKey = Arrays.copyOf(KEY_V1, KEY_V1.length);
    byte[] mutableFingerprint =
        Arrays.copyOf(
            FINGERPRINT_KEY,
            FINGERPRINT_KEY.length);

    BankAccountCrypto crypto =
        new BankAccountCrypto(
            "v1",
            Map.of("v1", mutableKey),
            mutableFingerprint);

    Arrays.fill(mutableKey, (byte) 0);
    Arrays.fill(mutableFingerprint, (byte) 0);

    var encrypted = crypto.encrypt("1234567890");
    byte[] exposedCiphertext = encrypted.ciphertext();
    byte[] exposedIv = encrypted.iv();
    exposedCiphertext[0] ^= 0x01;
    exposedIv[0] ^= 0x01;

    assertThat(
            crypto.decrypt(
                encrypted.ciphertext(),
                encrypted.iv(),
                encrypted.keyVersion()))
        .isEqualTo("1234567890");
  }

  @Test
  void validatesKeyAndAccountNumberMaterial() {
    assertThatThrownBy(
            () ->
                new BankAccountCrypto(
                    "v1",
                    Map.of("v1", new byte[16]),
                    FINGERPRINT_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");

    assertThatThrownBy(
            () ->
                new BankAccountCrypto(
                    "v1",
                    Map.of("v1", KEY_V1),
                    new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");

    BankAccountCrypto crypto =
        new BankAccountCrypto(
            "v1",
            Map.of("v1", KEY_V1),
            FINGERPRINT_KEY);

    assertThatThrownBy(() -> crypto.encrypt("12"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> crypto.encrypt("1234/5678"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] sequence(int start) {
    byte[] value = new byte[32];
    for (int i = 0; i < value.length; i++) {
      value[i] = (byte) (start + i);
    }
    return value;
  }
}
