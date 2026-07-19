package com.acme.hrms.payroll.platform;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsAdvice {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
    var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    problem.setType(URI.create("urn:problem:validation"));
    problem.setTitle("Validation failed");
    problem.setDetail("One or more fields are invalid");
    problem.setProperty("correlationId", CorrelationContext.require());
    return ResponseEntity.unprocessableEntity().body(problem);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "urn:problem:not-found", "Resource not found", exception.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ProblemDetail> conflict(ConflictException exception) {
    return problem(HttpStatus.CONFLICT, "urn:problem:conflict", "Conflict", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> unprocessable(IllegalArgumentException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "urn:problem:unprocessable-entity",
        "Request cannot be processed", exception.getMessage());
  }

  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail) {
    var problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create(type));
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setProperty("correlationId", CorrelationContext.require());
    return ResponseEntity.status(status).body(problem);
  }
}
