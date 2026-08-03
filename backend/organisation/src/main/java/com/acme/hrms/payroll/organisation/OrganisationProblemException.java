package com.acme.hrms.payroll.organisation;

import org.springframework.http.HttpStatus;

public final class OrganisationProblemException extends RuntimeException {
  private final HttpStatus status;
  private final String type;
  private final String title;

  public OrganisationProblemException(
      HttpStatus status,
      String type,
      String title,
      String detail,
      Throwable cause) {
    super(detail, cause);
    this.status = status;
    this.type = type;
    this.title = title;
  }

  public OrganisationProblemException(
      HttpStatus status,
      String type,
      String title,
      String detail) {
    this(status, type, title, detail, null);
  }

  public HttpStatus status() {
    return status;
  }

  public String type() {
    return type;
  }

  public String title() {
    return title;
  }
}
