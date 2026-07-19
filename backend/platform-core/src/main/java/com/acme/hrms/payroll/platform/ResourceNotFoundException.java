package com.acme.hrms.payroll.platform;

public final class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) { super(message); }
}
