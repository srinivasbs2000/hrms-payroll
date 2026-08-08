package com.acme.hrms.payroll.statutory;

public record RegistrationReadinessFindingView(
    String code,
    String severity,
    String message) {}
