package com.acme.hrms.payroll.employeepayroll;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionLineRequest;
import com.acme.hrms.payroll.employeepayroll.EmployeeIdentityPaymentModels.PaymentInstructionWriteRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeIdentityPaymentModelsTest {
  @Test
  void percentageAllocationMustTotalExactlyOneHundred() {
    PaymentInstructionWriteRequest request =
        new PaymentInstructionWriteRequest(
            null,
            "PRIMARY",
            "INR",
            "PERCENTAGE",
            LocalDate.of(2026, 1, 1),
            null,
            List.of(
                new PaymentInstructionLineRequest(
                    1, UUID.randomUUID(), "PERCENTAGE",
                    new BigDecimal("60"), null),
                new PaymentInstructionLineRequest(
                    2, UUID.randomUUID(), "PERCENTAGE",
                    new BigDecimal("39.99"), null)));

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly 100");
  }

  @Test
  void fixedModeRequiresExactlyOneRemainingBalance() {
    PaymentInstructionWriteRequest request =
        new PaymentInstructionWriteRequest(
            null,
            "PRIMARY",
            "INR",
            "FIXED_THEN_REMAINDER",
            LocalDate.of(2026, 1, 1),
            null,
            List.of(
                new PaymentInstructionLineRequest(
                    1, UUID.randomUUID(), "FIXED_AMOUNT",
                    null, new BigDecimal("1000")),
                new PaymentInstructionLineRequest(
                    2, UUID.randomUUID(), "REMAINING_BALANCE", null, null),
                new PaymentInstructionLineRequest(
                    3, UUID.randomUUID(), "REMAINING_BALANCE", null, null)));

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one remaining balance");
  }
}
