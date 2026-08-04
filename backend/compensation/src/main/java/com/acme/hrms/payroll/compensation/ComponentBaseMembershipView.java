package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ComponentBaseMembershipView(
    UUID membershipId,
    UUID payrollBaseId,
    UUID payrollBaseVersionId,
    String payrollBaseCode,
    int payrollBaseVersionSequence,
    UUID componentId,
    UUID componentVersionId,
    String componentCode,
    String componentName,
    int componentVersionSequence,
    int membershipSequence,
    long versionNo,
    String membershipType,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inclusionPercent,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String approvalStatus,
    UUID supersedesMembershipId,
    boolean superseded) {}
