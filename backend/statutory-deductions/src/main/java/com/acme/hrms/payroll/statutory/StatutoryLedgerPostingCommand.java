package com.acme.hrms.payroll.statutory;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StatutoryLedgerPostingCommand(
    @NotNull UUID evaluationRequestId) {}
