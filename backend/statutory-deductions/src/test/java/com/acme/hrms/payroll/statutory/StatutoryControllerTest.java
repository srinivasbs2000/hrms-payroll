package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.hrms.payroll.statutory.internal.application.StatutoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

class StatutoryControllerTest {
  private static final UUID CYCLE =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID CALCULATION =
      UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID EVALUATION =
      UUID.fromString("10000000-0000-0000-0000-000000000003");
  private static final UUID BATCH =
      UUID.fromString("10000000-0000-0000-0000-000000000004");

  @Test
  void evaluationReturnsCycleEtagAndDelegatesExactInputs() {
    StatutoryService service = mock(StatutoryService.class);
    StatutoryController controller = new StatutoryController(service);
    StatutoryEvaluationCommand command =
        new StatutoryEvaluationCommand(CALCULATION);
    StatutoryEvaluationExecution expected =
        new StatutoryEvaluationExecution(
            CYCLE,
            CALCULATION,
            EVALUATION,
            1,
            2,
            new BigDecimal("100.00"),
            new BigDecimal("200.00"),
            new BigDecimal("8900.00"),
            "a".repeat(64),
            4,
            Instant.parse("2026-07-26T00:00:00Z"),
            "tester");
    when(service.evaluate(CYCLE, "evaluate-001", 4, command))
        .thenReturn(expected);

    ResponseEntity<StatutoryEvaluationExecution> response =
        controller.evaluate(CYCLE, "evaluate-001", "\"4\"", command);

    assertThat(response.getBody()).isEqualTo(expected);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"4\"");
    verify(service).evaluate(CYCLE, "evaluate-001", 4, command);
  }

  @Test
  void postingReturnsAdvancedCycleEtag() {
    StatutoryService service = mock(StatutoryService.class);
    StatutoryController controller = new StatutoryController(service);
    StatutoryLedgerPostingCommand command =
        new StatutoryLedgerPostingCommand(EVALUATION);
    StatutoryLedgerPostingExecution expected =
        new StatutoryLedgerPostingExecution(
            CYCLE,
            EVALUATION,
            BATCH,
            1,
            "INITIAL",
            2,
            new BigDecimal("100.00"),
            new BigDecimal("200.00"),
            new BigDecimal("100.00"),
            new BigDecimal("200.00"),
            "b".repeat(64),
            5,
            Instant.parse("2026-07-26T00:01:00Z"),
            "tester");
    when(service.post(CYCLE, "posting-001", 4, command))
        .thenReturn(expected);

    ResponseEntity<StatutoryLedgerPostingExecution> response =
        controller.post(CYCLE, "posting-001", "W/\"4\"", command);

    assertThat(response.getHeaders().getETag()).isEqualTo("\"5\"");
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  void commandMethodsCarryDedicatedAuthorities() throws Exception {
    assertPermission(
        "evaluate",
        StatutoryPermissions.EVALUATION_EXECUTE,
        UUID.class,
        String.class,
        String.class,
        StatutoryEvaluationCommand.class);
    assertPermission(
        "post",
        StatutoryPermissions.LEDGER_POST,
        UUID.class,
        String.class,
        String.class,
        StatutoryLedgerPostingCommand.class);
    assertPermission(
        "correct",
        StatutoryPermissions.LEDGER_CORRECT,
        UUID.class,
        String.class,
        String.class,
        StatutoryCorrectionCommand.class);
  }

  private static void assertPermission(
      String method, String permission, Class<?>... parameterTypes)
      throws Exception {
    PreAuthorize annotation = StatutoryController.class
        .getMethod(method, parameterTypes)
        .getAnnotation(PreAuthorize.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo(
        "hasAuthority('" + permission + "')");
  }
}
