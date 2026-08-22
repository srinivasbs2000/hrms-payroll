package com.acme.hrms.payroll.employeepayroll.internal.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.employeepayroll.internal.security.EmployeeSensitiveCrypto.Domain;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmployeeSensitiveCryptoTest {
  @Test
  void encryptsMasksFingerprintsAndDomainSeparates() {
    byte[] aes = new byte[32];
    byte[] hmac = new byte[32];
    java.util.Arrays.fill(aes, (byte) 7);
    java.util.Arrays.fill(hmac, (byte) 11);
    EmployeeSensitiveCrypto crypto =
        new EmployeeSensitiveCrypto("v1", Map.of("v1", aes), hmac);

    var encrypted = crypto.encrypt(Domain.IDENTIFIER, "ABCDE1234F");

    assertThat(encrypted.ciphertext())
        .isNotEqualTo("ABCDE1234F".getBytes(UTF_8));
    assertThat(encrypted.iv()).hasSize(12);
    assertThat(encrypted.fingerprint()).hasSize(64);
    assertThat(encrypted.maskedValue()).endsWith("234F");
    assertThat(
        crypto.decrypt(
            Domain.IDENTIFIER,
            encrypted.ciphertext(),
            encrypted.iv(),
            encrypted.keyVersion()))
        .isEqualTo("ABCDE1234F");
    assertThat(crypto.fingerprint(Domain.IDENTIFIER, "ABCDE1234F"))
        .isNotEqualTo(
            crypto.fingerprint(Domain.BANK_ACCOUNT, "ABCDE1234F"));

    assertThatThrownBy(
            () ->
                crypto.decrypt(
                    Domain.BANK_ACCOUNT,
                    encrypted.ciphertext(),
                    encrypted.iv(),
                    encrypted.keyVersion()))
        .isInstanceOf(IllegalStateException.class);
  }
}
