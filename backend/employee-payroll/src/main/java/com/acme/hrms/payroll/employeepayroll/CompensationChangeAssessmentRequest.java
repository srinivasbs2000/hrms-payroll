package com.acme.hrms.payroll.employeepayroll;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CompensationChangeAssessmentRequest(
    @NotNull LocalDate assessmentThrough) {}
