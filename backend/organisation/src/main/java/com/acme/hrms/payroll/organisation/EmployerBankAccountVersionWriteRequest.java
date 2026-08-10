package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EmployerBankAccountVersionWriteRequest(
    @NotBlank @Size(max = 160) String bankName,
    @Size(max = 160) String branchName,
    @Size(max = 80) String routingCode,
    @NotBlank @Size(max = 160) String accountHolderName,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
    @NotBlank String accountNumber,
    boolean defaultAccount,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public void validate() {
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException(
          "effectiveTo must be after effectiveFrom");
    }
  }
}
