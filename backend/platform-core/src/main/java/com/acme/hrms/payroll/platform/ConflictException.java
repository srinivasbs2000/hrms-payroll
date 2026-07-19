package com.acme.hrms.payroll.platform;

public final class ConflictException extends RuntimeException {
  public ConflictException(String message) { super(message); }
  public ConflictException(String message, Throwable cause) { super(message, cause); }
}
