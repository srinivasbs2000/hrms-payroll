package com.acme.hrms.payroll.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean CorrelationIdFilter correlationIdFilter() { return new CorrelationIdFilter(); }
  @Bean TenantClaimFilter tenantClaimFilter(ObjectMapper objectMapper) { return new TenantClaimFilter(objectMapper); }
  @Bean PayrollJwtAuthenticationConverter payrollJwtAuthenticationConverter() { return new PayrollJwtAuthenticationConverter(); }
  @Bean SecurityProblemWriter securityProblemWriter(ObjectMapper objectMapper) { return new SecurityProblemWriter(objectMapper); }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
      @Value("${payroll.security.audience}") String audience) {
    NimbusJwtDecoder decoder = jwkSetUri == null || jwkSetUri.isBlank()
        ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer)
        : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)));
    return decoder;
  }

  @Bean
  SecurityFilterChain api(
      HttpSecurity http,
      CorrelationIdFilter correlationIdFilter,
      TenantClaimFilter tenantClaimFilter,
      SecurityProblemWriter securityProblemWriter,
      PayrollJwtAuthenticationConverter authenticationConverter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
        .exceptionHandling(errors -> errors.authenticationEntryPoint(securityProblemWriter)
            .accessDeniedHandler(securityProblemWriter))
        .addFilterBefore(correlationIdFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(tenantClaimFilter, BearerTokenAuthenticationFilter.class)
        .build();
  }
}
