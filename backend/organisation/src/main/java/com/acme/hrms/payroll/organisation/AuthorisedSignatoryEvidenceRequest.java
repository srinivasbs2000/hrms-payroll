package com.acme.hrms.payroll.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorisedSignatoryEvidenceRequest(
    @NotBlank @Size(max = 240) String evidenceRef) {}
