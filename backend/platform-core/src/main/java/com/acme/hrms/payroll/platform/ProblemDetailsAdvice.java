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
}
