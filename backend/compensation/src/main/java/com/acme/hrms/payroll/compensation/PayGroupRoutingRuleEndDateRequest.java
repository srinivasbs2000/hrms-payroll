package com.acme.hrms.payroll.compensation;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PayGroupRoutingRuleEndDateRequest(@NotNull LocalDate effectiveTo) {}
