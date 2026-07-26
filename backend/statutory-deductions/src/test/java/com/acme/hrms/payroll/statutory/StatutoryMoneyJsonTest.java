package com.acme.hrms.payroll.statutory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatutoryMoneyJsonTest {
  private static final UUID CYCLE =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID RESULT =
      UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID BATCH =
      UUID.fromString("10000000-0000-0000-0000-000000000003");

  private final JsonMapper mapper =
      JsonMapper.builder().findAndAddModules().build();

  @Test
  void correctionCommandRequiresExactDecimalStringTokens() throws Exception {
    String json =
        """
        {
          "statutoryResultId":"10000000-0000-0000-0000-000000000002",
          "employeeAmountDelta":"-10.1250",
          "employerAmountDelta":"0.1000",
          "reason":"Approved statutory correction"
        }
        """;

    StatutoryCorrectionCommand command =
        mapper.readValue(json, StatutoryCorrectionCommand.class);

    assertThat(command.employeeAmountDelta())
        .isEqualTo(new BigDecimal("-10.1250"));
    assertThat(command.employeeAmountDelta().scale()).isEqualTo(4);
    assertThat(command.employerAmountDelta())
        .isEqualTo(new BigDecimal("0.1000"));
    assertThat(command.employerAmountDelta().scale()).isEqualTo(4);

    String numericJson = json
        .replace("\"-10.1250\"", "-10.1250")
        .replace("\"0.1000\"", "0.1000");

    assertThatThrownBy(() ->
        mapper.readValue(numericJson, StatutoryCorrectionCommand.class))
        .isInstanceOf(MismatchedInputException.class)
        .hasMessageContaining("JSON strings");
  }

  @Test
  void correctionCommandRejectsOutOfContractPrecision() {
    String json =
        """
        {
          "statutoryResultId":"10000000-0000-0000-0000-000000000002",
          "employeeAmountDelta":"0.00001",
          "employerAmountDelta":"0.0000",
          "reason":"Approved statutory correction"
        }
        """;

    assertThatThrownBy(() ->
        mapper.readValue(json, StatutoryCorrectionCommand.class))
        .isInstanceOf(InvalidFormatException.class)
        .hasMessageContaining("4 fraction digits");
  }

  @Test
  void executionSerializesMoneyAsPlainDecimalStrings() throws Exception {
    StatutoryCorrectionExecution execution =
        new StatutoryCorrectionExecution(
            CYCLE,
            RESULT,
            BATCH,
            2,
            2,
            new BigDecimal("-10.1250"),
            new BigDecimal("0.1000"),
            new BigDecimal("1234567890123.4567"),
            new BigDecimal("1800.0000"),
            "a".repeat(64),
            7,
            Instant.parse("2026-07-26T10:00:00Z"),
            "tester");

    JsonNode json = mapper.readTree(mapper.writeValueAsString(execution));

    assertText(json, "employeeDeltaTotal", "-10.1250");
    assertText(json, "employerDeltaTotal", "0.1000");
    assertText(json, "cycleEmployeeTotal", "1234567890123.4567");
    assertText(json, "cycleEmployerTotal", "1800.0000");
  }

  @Test
  void everyPublicStatutoryMoneyRecordUsesDecimalString() {
    List<Class<?>> records = List.of(
        StatutoryBalanceSnapshotView.class,
        StatutoryCorrectionCommand.class,
        StatutoryCorrectionExecution.class,
        StatutoryEvaluationExecution.class,
        StatutoryEvaluationRequestView.class,
        StatutoryLedgerBatchView.class,
        StatutoryLedgerEntryView.class,
        StatutoryLedgerPostingExecution.class,
        StatutoryReconciliationView.class,
        StatutoryRemittanceSummaryView.class,
        StatutoryResultView.class);

    records.forEach(type -> {
      var monetaryComponents = List.of(type.getRecordComponents()).stream()
          .filter(component -> component.getType().equals(BigDecimal.class))
          .toList();

      assertThat(monetaryComponents)
          .as("BigDecimal components for %s", type.getSimpleName())
          .isNotEmpty()
          .allSatisfy(component ->
              assertThat(component.getAnnotation(DecimalString.class))
                  .as("%s.%s", type.getSimpleName(), component.getName())
                  .isNotNull());
    });
  }

  private static void assertText(
      JsonNode json,
      String property,
      String expected) {
    assertThat(json.path(property).isTextual()).isTrue();
    assertThat(json.path(property).textValue()).isEqualTo(expected);
  }
}
