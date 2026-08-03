package com.acme.hrms.payroll.organisation;

import com.acme.hrms.payroll.platform.CorrelationContext;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrganisationProblemAdvice {
  @ExceptionHandler(OrganisationProblemException.class)
  ResponseEntity<ProblemDetail> organisationProblem(
      OrganisationProblemException exception) {
    ProblemDetail problem = ProblemDetail.forStatus(exception.status());
    problem.setType(URI.create(exception.type()));
    problem.setTitle(exception.title());
    problem.setDetail(exception.getMessage());
    problem.setProperty("correlationId", CorrelationContext.require());
    return ResponseEntity.status(exception.status()).body(problem);
  }
}
