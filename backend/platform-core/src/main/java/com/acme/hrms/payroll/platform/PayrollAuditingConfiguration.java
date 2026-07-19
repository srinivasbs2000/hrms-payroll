package com.acme.hrms.payroll.platform;

import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class PayrollAuditingConfiguration {
  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  @Bean
  DateTimeProvider auditDateTimeProvider(Clock clock) {
    return () -> Optional.of(clock.instant());
  }

  @Bean
  AuditorAware<String> payrollAuditor(
      @Value("${payroll.audit.service-identity:service:local-payroll}") String serviceIdentity) {
    return () -> {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || !authentication.isAuthenticated()) return Optional.of(serviceIdentity);
      if (authentication.getPrincipal() instanceof Jwt jwt) {
        return Optional.of(jwt.getIssuer() + "|" + jwt.getSubject());
      }
      return Optional.of(authentication.getName());
    };
  }
}
