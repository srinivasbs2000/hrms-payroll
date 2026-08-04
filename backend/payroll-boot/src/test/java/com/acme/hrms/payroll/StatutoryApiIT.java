package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request
    .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class StatutoryApiIT {
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();

  private static final String TENANT_A =
      "00000000-0000-0000-0000-00000000000a";
  private static final String TENANT_B =
      "00000000-0000-0000-0000-00000000000b";
  private static final String LEGAL_ID =
      "31000000-0000-0000-0000-000000000001";
  private static final String LEGAL_VERSION_ID =
      "31100000-0000-0000-0000-000000000001";
  private static final String PSU_ID =
      "32000000-0000-0000-0000-000000000001";
  private static final String PSU_VERSION_ID =
      "32100000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_ID =
      "33000000-0000-0000-0000-000000000001";
  private static final String ESTABLISHMENT_VERSION_ID =
      "33100000-0000-0000-0000-000000000001";
  private static final String CALENDAR_ID =
      "34000000-0000-0000-0000-000000000001";
  private static final String PERIOD_ID =
      "34100000-0000-0000-0000-000000000001";
  private static final String SECOND_PERIOD_ID =
      "34100000-0000-0000-0000-000000000002";
  private static final String PAY_GROUP_ID =
      "35000000-0000-0000-0000-000000000001";
  private static final String PAY_GROUP_VERSION_ID =
      "35100000-0000-0000-0000-000000000001";
  private static final String COMPONENT_ID =
      "36000000-0000-0000-0000-000000000001";
  private static final String COMPONENT_VERSION_ID =
      "36100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_ID =
      "37000000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_VERSION_ID =
      "37100000-0000-0000-0000-000000000001";
  private static final String STRUCTURE_LINE_ID =
      "37200000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_ID =
      "38000000-0000-0000-0000-000000000001";
  private static final String RELATIONSHIP_VERSION_ID =
      "38100000-0000-0000-0000-000000000001";
  private static final String PROFILE_ID =
      "38200000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_ID =
      "39000000-0000-0000-0000-000000000001";
  private static final String ASSIGNMENT_VERSION_ID =
      "39100000-0000-0000-0000-000000000001";
  private static final String GROUP_ASSIGNMENT_ID =
      "39200000-0000-0000-0000-000000000001";
  private static final String SALARY_ASSIGNMENT_ID =
      "39300000-0000-0000-0000-000000000001";

  private static final String SOCIAL_RULE_ID =
      "a1000000-0000-0000-0000-000000000001";
  private static final String SOCIAL_RULE_VERSION_ID =
      "a1100000-0000-0000-0000-000000000001";
  private static final String SOCIAL_EMPLOYEE_PORTION_ID =
      "a1200000-0000-0000-0000-000000000001";
  private static final String SOCIAL_EMPLOYER_PORTION_ID =
      "a1200000-0000-0000-0000-000000000002";
  private static final String TAX_RULE_ID =
      "a1000000-0000-0000-0000-000000000002";
  private static final String TAX_RULE_VERSION_ID =
      "a1100000-0000-0000-0000-000000000002";
  private static final String TAX_PORTION_ID =
      "a1200000-0000-0000-0000-000000000003";
  private static final String TAX_SLAB_ONE_ID =
      "a1300000-0000-0000-0000-000000000001";
  private static final String TAX_SLAB_TWO_ID =
      "a1300000-0000-0000-0000-000000000002";
  private static final String STATUTORY_PROFILE_ID =
      "b1000000-0000-0000-0000-000000000001";
  private static final String STATUTORY_PROFILE_VERSION_ID =
      "b1100000-0000-0000-0000-000000000001";
  private static final String SOCIAL_ASSIGNMENT_ID =
      "b1200000-0000-0000-0000-000000000001";
  private static final String TAX_ASSIGNMENT_ID =
      "b1200000-0000-0000-0000-000000000002";
  private static final String CLASSIFICATION_ID =
      "c1000000-0000-0000-0000-000000000001";
  private static final String BALANCE_YEAR_ID =
      "d1000000-0000-0000-0000-000000000001";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  static {
    POSTGRES.start();
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER "
              + "NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "payroll_app");
    registry.add("spring.datasource.password", () -> APP_PASSWORD);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "https://issuer.example.test");
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "https://issuer.example.test/jwks");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean JwtDecoder jwtDecoder;

  @BeforeEach
  void seedReadyEmployeeConfiguration() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES "
              + "('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test'),"
              + "('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
      statement.execute("SET ROLE payroll_owner");
      statement.execute(
          "SELECT set_config('app.tenant_id','" + TENANT_A + "',false)");
      seedOrganisation(statement);
      seedPayrollConfiguration(statement);
      seedEmployee(statement);
      seedStatutoryConfiguration(statement);
      statement.execute("RESET ROLE");
    }
  }

  @Test
  void statutoryLifecycleIsSecuredExactIdempotentAndAudited()
      throws Exception {
    CalculatedCycle cycle = calculatedCycle("s4-06a-happy");

    MvcResult evaluated = evaluateStatutory(
            cycle,
            "s4-06a-evaluate-001",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"3\""))
        .andExpect(jsonPath("$.cycleId").value(cycle.cycleId()))
        .andExpect(jsonPath("$.calculationRequestId")
            .value(cycle.calculationRequestId()))
        .andExpect(jsonPath("$.payrollResultCount").value(1))
        .andExpect(jsonPath("$.statutoryResultCount").value(2))
        .andExpect(jsonPath("$.employeeTotal").value("17000.0000"))
        .andExpect(jsonPath("$.employerTotal").value("500.0000"))
        .andExpect(jsonPath("$.postStatutoryNetTotal")
            .value("73000.0000"))
        .andExpect(jsonPath("$.evidenceSetHash")
            .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
        .andReturn();

    JsonNode evaluation = json(evaluated);
    String evaluationRequestId =
        evaluation.path("evaluationRequestId").asText();

    evaluateStatutory(
            cycle,
            "s4-06a-evaluate-001",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.evaluationRequestId")
            .value(evaluationRequestId));

    evaluateStatutory(
            cycle,
            "s4-06a-evaluate-001",
            Long.toString(cycle.version() - 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/evaluations",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-evaluation.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(evaluationRequestId))
        .andExpect(jsonPath("$[0].employeeTotal").value("17000.0000"));

    MvcResult resultsResponse = mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/results",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-evaluation.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].employeeAmount").isString())
        .andExpect(jsonPath("$[0].employerAmount").isString())
        .andReturn();

    JsonNode results = json(resultsResponse);
    String socialResultId = null;
    for (JsonNode item : results) {
      if (SOCIAL_RULE_ID.equals(item.path("statutoryRuleId").asText())) {
        socialResultId = item.path("id").asText();
      }
    }
    assertThat(socialResultId).isNotBlank();

    MvcResult posted = postStatutory(
            cycle.cycleId(),
            evaluationRequestId,
            "s4-06a-post-001",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"4\""))
        .andExpect(jsonPath("$.evaluationRequestId")
            .value(evaluationRequestId))
        .andExpect(jsonPath("$.attemptNo").value(1))
        .andExpect(jsonPath("$.batchKind").value("INITIAL"))
        .andExpect(jsonPath("$.postedEntryCount").value(2))
        .andExpect(jsonPath("$.employeeDeltaTotal")
            .value("17000.0000"))
        .andExpect(jsonPath("$.employerDeltaTotal").value("500.0000"))
        .andExpect(jsonPath("$.cycleEmployeeTotal")
            .value("17000.0000"))
        .andExpect(jsonPath("$.cycleEmployerTotal").value("500.0000"))
        .andReturn();

    String initialBatchId = json(posted).path("ledgerBatchId").asText();

    postStatutory(
            cycle.cycleId(),
            evaluationRequestId,
            "s4-06a-post-001",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerBatchId").value(initialBatchId));

    postStatutory(
            cycle.cycleId(),
            evaluationRequestId,
            "s4-06a-post-stale",
            Long.toString(cycle.version()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/ledger-batches",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-ledger.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(initialBatchId))
        .andExpect(jsonPath("$[0].batchKind").value("INITIAL"));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/ledger-entries",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-ledger.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].employeeAmountDelta").isString());

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/balance-snapshots",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-balance.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].cycleEmployeeAmount").isString())
        .andExpect(jsonPath("$[0].yearEmployerAmount").isString());

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/reconciliations",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-reconciliation.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("MATCHED"))
        .andExpect(jsonPath("$[0].employeeVariance").value("0.0000"))
        .andExpect(jsonPath("$[0].employerVariance").value("0.0000"));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/remittance-summaries",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-remittance.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].remittanceAmount").isString())
        .andExpect(jsonPath("$[0].remittancePosition").value("PAYABLE"));

    MvcResult corrected = correctStatutory(
            cycle.cycleId(),
            socialResultId,
            "-10.1250",
            "0.1000",
            "Approved synthetic statutory correction",
            "s4-06a-correct-001",
            "4")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"5\""))
        .andExpect(jsonPath("$.attemptNo").value(2))
        .andExpect(jsonPath("$.employeeDeltaTotal").value("-10.1250"))
        .andExpect(jsonPath("$.employerDeltaTotal").value("0.1000"))
        .andExpect(jsonPath("$.cycleEmployeeTotal")
            .value("16989.8750"))
        .andExpect(jsonPath("$.cycleEmployerTotal").value("500.1000"))
        .andReturn();

    String correctionBatchId =
        json(corrected).path("ledgerBatchId").asText();

    correctStatutory(
            cycle.cycleId(),
            socialResultId,
            "-10.1250",
            "0.1000",
            "Approved synthetic statutory correction",
            "s4-06a-correct-001",
            "4")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerBatchId").value(correctionBatchId));

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/reconciliations",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-reconciliation.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ledgerBatchId")
            .value(correctionBatchId))
        .andExpect(jsonPath("$[0].status").value("MATCHED"))
        .andExpect(jsonPath("$[0].correctionEmployeeTotal")
            .value("-10.1250"))
        .andExpect(jsonPath("$[0].correctionEmployerTotal")
            .value("0.1000"))
        .andExpect(jsonPath("$[0].employeeVariance").value("0.0000"))
        .andExpect(jsonPath("$[0].employerVariance").value("0.0000"));

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);

      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM audit.audit_event "
              + "WHERE action='EVALUATED'"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM audit.audit_event "
              + "WHERE action='POSTED'"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM audit.audit_event "
              + "WHERE action='CORRECTED'"))
          .isOne();

      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM integration.outbox_event "
              + "WHERE event_type='StatutoryEvaluated'"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM integration.outbox_event "
              + "WHERE event_type='StatutoryLedgerPosted'"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM integration.outbox_event "
              + "WHERE event_type='StatutoryLedgerCorrected'"))
          .isOne();

      assertThat(queryLong(
          connection,
          """
          SELECT count(*)
          FROM statutory.statutory_ledger_entry
          WHERE ledger_batch_id='%s'
            AND statutory_result_id='%s'
            AND entry_kind='CORRECTION'
            AND source_entry_id IS NOT NULL
            AND employee_amount_delta=-10.1250
            AND employer_amount_delta=0.1000
            AND reason_code='CORRECTION'
          """.formatted(correctionBatchId, socialResultId)))
          .isOne();

      assertThat(queryLong(
          connection,
          """
          SELECT count(*)
          FROM statutory.statutory_reconciliation
          WHERE ledger_batch_id='%s'
            AND source_employee_total=17000.0000
            AND source_employer_total=500.0000
            AND correction_employee_total=-10.1250
            AND correction_employer_total=0.1000
            AND expected_employee_total=16989.8750
            AND expected_employer_total=500.1000
            AND ledger_employee_total=16989.8750
            AND ledger_employer_total=500.1000
            AND employee_variance=0.0000
            AND employer_variance=0.0000
            AND reconciliation_status='MATCHED'
          """.formatted(correctionBatchId)))
          .isOne();

      assertThat(queryLong(
          connection,
          """
          SELECT count(*)
          FROM (
            SELECT 1
            FROM (
              SELECT DISTINCT ON (
                snapshot.employee_statutory_profile_id,
                snapshot.statutory_rule_id,
                snapshot.balance_year_id
              )
                snapshot.cycle_employee_amount,
                snapshot.cycle_employer_amount,
                batch.attempt_no
              FROM statutory.statutory_balance_snapshot snapshot
              JOIN statutory.statutory_ledger_batch batch
                ON batch.tenant_id=snapshot.tenant_id
               AND batch.id=snapshot.ledger_batch_id
              WHERE snapshot.payroll_cycle_id='%s'
              ORDER BY
                snapshot.employee_statutory_profile_id,
                snapshot.statutory_rule_id,
                snapshot.balance_year_id,
                batch.attempt_no DESC
            ) latest
            HAVING sum(cycle_employee_amount)=16989.8750
               AND sum(cycle_employer_amount)=500.1000
          ) reconciled
          """.formatted(cycle.cycleId())))
          .isOne();

      assertThat(queryLong(
          connection,
          """
          SELECT count(*)
          FROM (
            SELECT 1
            FROM (
              SELECT DISTINCT ON (
                summary.statutory_rule_id,
                summary.balance_year_id
              )
                summary.period_employee_total,
                summary.period_employer_total,
                summary.year_employee_total,
                summary.year_employer_total,
                summary.remittance_amount,
                batch.attempt_no
              FROM statutory.statutory_remittance_summary summary
              JOIN statutory.statutory_ledger_batch batch
                ON batch.tenant_id=summary.tenant_id
               AND batch.id=summary.ledger_batch_id
              WHERE summary.payroll_cycle_id='%s'
              ORDER BY
                summary.statutory_rule_id,
                summary.balance_year_id,
                batch.attempt_no DESC
            ) latest
            HAVING sum(period_employee_total)=16989.8750
               AND sum(period_employer_total)=500.1000
               AND sum(year_employee_total)=16989.8750
               AND sum(year_employer_total)=500.1000
               AND sum(remittance_amount)=17489.9750
          ) reconciled
          """.formatted(cycle.cycleId())))
          .isOne();

      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_evaluation_request"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_ledger_batch"))
          .isEqualTo(2);
      connection.rollback();
    }
  }

  @Test
  void statutoryPermissionTenantAndValidationFailuresAreEnforced()
      throws Exception {
    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/results",
                    UUID.randomUUID()))
        .andExpect(status().isUnauthorized());

    CalculatedCycle cycle = calculatedCycle("s4-06a-negative");

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/statutory/evaluations",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-evaluation.read"))
                .header("Idempotency-Key", "s4-06a-forbidden-evaluate")
                .header("If-Match", Long.toString(cycle.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"calculationRequestId":"%s"}
                    """.formatted(cycle.calculationRequestId())))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/statutory/evaluations",
                    cycle.cycleId())
                .with(token(TENANT_B, "statutory-evaluation.execute"))
                .header("Idempotency-Key", "s4-06a-tenant-b-evaluate")
                .header("If-Match", Long.toString(cycle.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"calculationRequestId":"%s"}
                    """.formatted(cycle.calculationRequestId())))
        .andExpect(status().isNotFound());

    MvcResult evaluated = evaluateStatutory(
            cycle,
            "s4-06a-negative-evaluate",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andReturn();
    String evaluationRequestId =
        json(evaluated).path("evaluationRequestId").asText();

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/statutory/postings",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-ledger.read"))
                .header("Idempotency-Key", "s4-06a-forbidden-post")
                .header("If-Match", Long.toString(cycle.version()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"evaluationRequestId":"%s"}
                    """.formatted(evaluationRequestId)))
        .andExpect(status().isForbidden());

    MvcResult posted = postStatutory(
            cycle.cycleId(),
            evaluationRequestId,
            "s4-06a-negative-post",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andReturn();

    MvcResult resultsResponse = mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/results",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-evaluation.read")))
        .andExpect(status().isOk())
        .andReturn();
    String resultId = json(resultsResponse).get(0).path("id").asText();

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/results",
                    cycle.cycleId())
                .with(token(TENANT_B, "statutory-evaluation.read")))
        .andExpect(status().isNotFound());

    mvc.perform(
            get("/api/v1/payroll-cycles/{cycleId}/statutory/results",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-ledger.read")))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/payroll-cycles/{cycleId}/statutory/corrections",
                    cycle.cycleId())
                .with(token(TENANT_A, "statutory-ledger.correct"))
                .header("Idempotency-Key", "s4-06a-numeric-money")
                .header("If-Match", "4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "statutoryResultId":"%s",
                      "employeeAmountDelta":-10.1250,
                      "employerAmountDelta":0.1000,
                      "reason":"Approved numeric-token rejection"
                    }
                    """.formatted(resultId)))
        .andExpect(status().isBadRequest());

    correctStatutory(
            cycle.cycleId(),
            resultId,
            "0.0000",
            "0.0000",
            "Approved zero correction rejection",
            "s4-06a-zero-correction",
            "4")
        .andExpect(status().isUnprocessableEntity());

    correctStatutory(
            cycle.cycleId(),
            resultId,
            "-1.0000",
            "0.0000",
            "short",
            "s4-06a-short-reason",
            "4")
        .andExpect(status().isUnprocessableEntity());

    assertThat(json(posted).path("ledgerBatchId").asText()).isNotBlank();
  }

  @Test
  void statutoryPostingRaceAllowsOneWinnerAndNoDuplicateEvidence()
      throws Exception {
    CalculatedCycle cycle = calculatedCycle("s4-06a-race");
    MvcResult evaluated = evaluateStatutory(
            cycle,
            "s4-06a-race-evaluate",
            Long.toString(cycle.version()))
        .andExpect(status().isOk())
        .andReturn();
    String evaluationRequestId =
        json(evaluated).path("evaluationRequestId").asText();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Future<Integer>> futures = new ArrayList<>();
    try {
      for (int index = 1; index <= 2; index++) {
        final int requestIndex = index;
        futures.add(executor.submit(() -> {
          ready.countDown();
          start.await();
          return postStatutory(
                  cycle.cycleId(),
                  evaluationRequestId,
                  "s4-06a-race-post-" + requestIndex,
                  Long.toString(cycle.version()))
              .andReturn()
              .getResponse()
              .getStatus();
        }));
      }
      ready.await();
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get());
      }
      Collections.sort(statuses);
      assertThat(statuses).containsExactly(200, 409);
    } finally {
      executor.shutdownNow();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      setTenant(connection, TENANT_A);
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_ledger_batch"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM audit.audit_event "
              + "WHERE action='POSTED'"))
          .isOne();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM integration.outbox_event "
              + "WHERE event_type='StatutoryLedgerPosted'"))
          .isOne();
      connection.rollback();
    }
  }

  @Test
  void runtimeRoleAndRlsProtectStatutoryEvidence() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      assertThat(queryString(connection, "SELECT current_user"))
          .isEqualTo("payroll_app");
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM pg_roles "
              + "WHERE rolname='payroll_app' "
              + "AND NOT rolsuper AND NOT rolbypassrls"))
          .isOne();
      assertThat(queryLong(
          connection,
          """
          SELECT count(*)
          FROM pg_class relation
          JOIN pg_namespace namespace
            ON namespace.oid=relation.relnamespace
          WHERE namespace.nspname='statutory'
            AND relation.relkind='r'
            AND EXISTS (
              SELECT 1
              FROM pg_attribute attribute
              WHERE attribute.attrelid=relation.oid
                AND attribute.attname='tenant_id'
                AND NOT attribute.attisdropped
            )
            AND (
              NOT relation.relrowsecurity
              OR NOT relation.relforcerowsecurity
            )
          """))
          .isZero();

      setTenant(connection, TENANT_A);
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_rule"))
          .isEqualTo(2);

      setTenant(connection, TENANT_B);
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.statutory_rule"))
          .isZero();
      assertThat(queryLong(
          connection,
          "SELECT count(*) FROM statutory.employee_statutory_profile"))
          .isZero();
      connection.rollback();
    }
  }

  private CalculatedCycle calculatedCycle(String prefix) throws Exception {
    MvcResult created = createCycle(prefix + "-create", PERIOD_ID)
        .andExpect(status().isCreated())
        .andExpect(header().string("ETag", "\"0\""))
        .andReturn();
    String cycleId = json(created).path("id").asText();

    resolvePopulation(cycleId, prefix + "-resolve", "0")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""));
    sealInputs(cycleId, prefix + "-seal", "1")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"2\""));

    MvcResult calculated = calculatePayroll(
            cycleId,
            prefix + "-calculate",
            "2")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"3\""))
        .andExpect(jsonPath("$.grossTotal").value(90000.0))
        .andExpect(jsonPath("$.netTotal").value(90000.0))
        .andReturn();

    return new CalculatedCycle(
        cycleId,
        json(calculated).path("calculationRequestId").asText(),
        3);
  }

  private org.springframework.test.web.servlet.ResultActions evaluateStatutory(
      CalculatedCycle cycle,
      String key,
      String ifMatch) throws Exception {
    return mvc.perform(
        post("/api/v1/payroll-cycles/{cycleId}/statutory/evaluations",
                cycle.cycleId())
            .with(token(TENANT_A, "statutory-evaluation.execute"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"calculationRequestId":"%s"}
                """.formatted(cycle.calculationRequestId())));
  }

  private org.springframework.test.web.servlet.ResultActions postStatutory(
      String cycleId,
      String evaluationRequestId,
      String key,
      String ifMatch) throws Exception {
    return mvc.perform(
        post("/api/v1/payroll-cycles/{cycleId}/statutory/postings", cycleId)
            .with(token(TENANT_A, "statutory-ledger.post"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"evaluationRequestId":"%s"}
                """.formatted(evaluationRequestId)));
  }

  private org.springframework.test.web.servlet.ResultActions correctStatutory(
      String cycleId,
      String statutoryResultId,
      String employeeDelta,
      String employerDelta,
      String reason,
      String key,
      String ifMatch) throws Exception {
    return mvc.perform(
        post("/api/v1/payroll-cycles/{cycleId}/statutory/corrections",
                cycleId)
            .with(token(TENANT_A, "statutory-ledger.correct"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "statutoryResultId":"%s",
                  "employeeAmountDelta":"%s",
                  "employerAmountDelta":"%s",
                  "reason":"%s"
                }
                """.formatted(
                    statutoryResultId,
                    employeeDelta,
                    employerDelta,
                    reason)));
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private record CalculatedCycle(
      String cycleId,
      String calculationRequestId,
      long version) {}

  private org.springframework.test.web.servlet.ResultActions createCycle(
      String key, String periodId) throws Exception {
    return mvc.perform(
        post("/api/v1/payroll-cycles")
            .with(token(TENANT_A, "payroll-cycle.create"))
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "payGroupVersionId":"%s",
                  "payPeriodId":"%s"
                }
                """.formatted(PAY_GROUP_VERSION_ID, periodId)));
  }

  private org.springframework.test.web.servlet.ResultActions resolvePopulation(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/population-resolution",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-cycle.population.resolve"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private org.springframework.test.web.servlet.ResultActions sealInputs(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/seal-inputs",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-cycle.inputs.seal"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private org.springframework.test.web.servlet.ResultActions calculatePayroll(
      String cycleId, String key, String ifMatch) throws Exception {
    return mvc.perform(
        post(
                "/api/v1/payroll-cycles/{cycleId}/calculation",
                cycleId)
            .with(token(
                TENANT_A,
                "payroll-calculation.execute"))
            .header("Idempotency-Key", key)
            .header("If-Match", ifMatch));
  }

  private static org.springframework.security.test.web.servlet.request
      .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(
      String tenant, String permission) {
    return jwt().jwt(jwt -> jwt
        .issuer("https://issuer.example.test")
        .subject("synthetic-subject")
        .claim("tenant_id", tenant))
        .authorities(() -> permission);
  }

  private static void seedOrganisation(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.legal_entity(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_IN','test','test')
        """.formatted(LEGAL_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.legal_entity_version(
          id,tenant_id,legal_entity_id,version_sequence,
          name,country_code,currency,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Acme India','IN','INR',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_PSU','test','test')
        """.formatted(PSU_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit_version(
          id,tenant_id,payroll_statutory_unit_id,
          legal_entity_version_id,version_sequence,name,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Acme PSU',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PSU_VERSION_ID, TENANT_A, PSU_ID, LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO organisation.establishment(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','BLR','test','test')
        """.formatted(ESTABLISHMENT_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.establishment_version(
          id,tenant_id,establishment_id,
          payroll_statutory_unit_version_id,version_sequence,
          name,state_code,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Bengaluru','KA',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            ESTABLISHMENT_VERSION_ID,
            TENANT_A,
            ESTABLISHMENT_ID,
            PSU_VERSION_ID));
  }

  private static void seedPayrollConfiguration(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.payroll_calendar(
          id,tenant_id,code,name,frequency,timezone,
          created_by,updated_by
        ) VALUES (
          '%s','%s','MONTHLY_IN','Monthly India',
          'MONTHLY','Asia/Kolkata','test','test'
        )
        """.formatted(CALENDAR_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.pay_period(
          id,tenant_id,calendar_id,period_code,
          period_start,period_end,payment_date,status,
          created_by,updated_by
        ) VALUES
          ('%s','%s','%s','2026-07','2026-07-01',
           '2026-07-31','2026-07-31','OPEN','test','test'),
          ('%s','%s','%s','2026-08','2026-08-01',
           '2026-08-31','2026-08-31','OPEN','test','test')
        """.formatted(
            PERIOD_ID, TENANT_A, CALENDAR_ID,
            SECOND_PERIOD_ID, TENANT_A, CALENDAR_ID));
    statement.execute(
        """
        INSERT INTO organisation.pay_group(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','MONTHLY_IN','test','test')
        """.formatted(PAY_GROUP_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.pay_group_version(
          id,tenant_id,pay_group_id,
          payroll_statutory_unit_version_id,calendar_id,
          version_sequence,name,currency,proration_method,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,
          'Monthly India','INR','CALENDAR_DAYS',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            PAY_GROUP_VERSION_ID,
            TENANT_A,
            PAY_GROUP_ID,
            PSU_VERSION_ID,
            CALENDAR_ID));
    statement.execute(
        """
        INSERT INTO compensation.pay_component(
          id,tenant_id,code,name,component_type,
          created_by,updated_by
        ) VALUES (
          '%s','%s','BASIC','Basic Pay','EARNING','test','test'
        )
        """.formatted(COMPONENT_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.pay_component_version(
          id,tenant_id,component_id,version_sequence,
          formula_type,formula_expression,fixed_amount,
          rounding_scale,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'FIXED',NULL,90000.0000,2,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(COMPONENT_VERSION_ID, TENANT_A, COMPONENT_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','DEFAULT','test','test')
        """.formatted(STRUCTURE_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_version(
          id,tenant_id,salary_structure_id,version_sequence,
          name,currency,effective_from,effective_to,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Default Structure','INR',
          '2026-01-01','2027-01-01','DRAFT','test','test'
        )
        """.formatted(STRUCTURE_VERSION_ID, TENANT_A, STRUCTURE_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          id,tenant_id,salary_structure_version_id,
          component_version_id,sequence_no,target_amount,
          effective_from,effective_to,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,90000.0000,
          '2026-01-01','2027-01-01','test','test'
        )
        """.formatted(
            STRUCTURE_LINE_ID,
            TENANT_A,
            STRUCTURE_VERSION_ID,
            COMPONENT_VERSION_ID));
    statement.execute(
        "SELECT compensation.approve_salary_structure_version('"
            + TENANT_A
            + "','"
            + STRUCTURE_VERSION_ID
            + "','test',clock_timestamp())");
  }

  private static void seedEmployee(Statement statement) throws Exception {
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,
          status,created_by,updated_by
        ) VALUES (
          '%s','%s','EMP-EXT-001','EMP-001','ACTIVE','test','test'
        )
        """.formatted(RELATIONSHIP_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship_version(
          id,tenant_id,payroll_relationship_id,
          legal_entity_version_id,version_sequence,
          relationship_start,relationship_end,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            RELATIONSHIP_VERSION_ID,
            TENANT_A,
            RELATIONSHIP_ID,
            LEGAL_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.employee_payroll_profile(
          id,tenant_id,payroll_relationship_id,
          currency,payroll_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','INR','READY','test','test'
        )
        """.formatted(PROFILE_ID, TENANT_A, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,
          assignment_number,status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','ASN-001','ACTIVE','test','test'
        )
        """.formatted(ASSIGNMENT_ID, TENANT_A, RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,
          payroll_relationship_version_id,establishment_version_id,
          version_sequence,assignment_start,assignment_end,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            ASSIGNMENT_VERSION_ID,
            TENANT_A,
            ASSIGNMENT_ID,
            RELATIONSHIP_VERSION_ID,
            ESTABLISHMENT_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.pay_group_assignment(
          id,tenant_id,payroll_assignment_version_id,
          pay_group_version_id,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','2026-01-01','2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
        )
        """.formatted(
            GROUP_ASSIGNMENT_ID,
            TENANT_A,
            ASSIGNMENT_VERSION_ID,
            PAY_GROUP_VERSION_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.salary_assignment(
          id,tenant_id,payroll_assignment_version_id,
          salary_structure_version_id,monthly_amount,currency,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',90000.0000,'INR',
          '2026-01-01','2027-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """.formatted(
            SALARY_ASSIGNMENT_ID,
            TENANT_A,
            ASSIGNMENT_VERSION_ID,
            STRUCTURE_VERSION_ID));
  }

  private static void seedStatutoryConfiguration(Statement statement)
      throws Exception {
    insertRule(
        statement,
        SOCIAL_RULE_ID,
        "SOCIAL",
        "SOCIAL_INSURANCE");
    insertRuleVersion(
        statement,
        SOCIAL_RULE_VERSION_ID,
        SOCIAL_RULE_ID);
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_portion(
          id,tenant_id,statutory_rule_version_id,liable_party,
          sequence_no,calculation_method,assessment_base_code,
          rate_percent,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','EMPLOYEE',1,'PERCENTAGE','GROSS',
          10,'test','test'
        )
        """.formatted(
            SOCIAL_EMPLOYEE_PORTION_ID,
            TENANT_A,
            SOCIAL_RULE_VERSION_ID));
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_portion(
          id,tenant_id,statutory_rule_version_id,liable_party,
          sequence_no,calculation_method,fixed_amount,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','EMPLOYER',2,'FIXED',500,'test','test'
        )
        """.formatted(
            SOCIAL_EMPLOYER_PORTION_ID,
            TENANT_A,
            SOCIAL_RULE_VERSION_ID));
    statement.execute(
        "SELECT statutory.approve_statutory_rule_version('"
            + TENANT_A
            + "','"
            + SOCIAL_RULE_VERSION_ID
            + "','test',clock_timestamp())");

    insertRule(statement, TAX_RULE_ID, "INCOME_TAX", "INCOME_TAX");
    insertRuleVersion(statement, TAX_RULE_VERSION_ID, TAX_RULE_ID);
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_portion(
          id,tenant_id,statutory_rule_version_id,liable_party,
          sequence_no,calculation_method,assessment_base_code,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','EMPLOYEE',1,'SLAB','GROSS','test','test'
        )
        """.formatted(
            TAX_PORTION_ID,
            TENANT_A,
            TAX_RULE_VERSION_ID));
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_slab(
          id,tenant_id,statutory_rule_version_id,
          statutory_rule_portion_id,sequence_no,lower_bound,
          upper_bound,fixed_amount,rate_percent,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,0,50000,0,0,'test','test'
        )
        """.formatted(
            TAX_SLAB_ONE_ID,
            TENANT_A,
            TAX_RULE_VERSION_ID,
            TAX_PORTION_ID));
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_slab(
          id,tenant_id,statutory_rule_version_id,
          statutory_rule_portion_id,sequence_no,lower_bound,
          upper_bound,fixed_amount,rate_percent,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',2,50000,NULL,0,20,'test','test'
        )
        """.formatted(
            TAX_SLAB_TWO_ID,
            TENANT_A,
            TAX_RULE_VERSION_ID,
            TAX_PORTION_ID));
    statement.execute(
        "SELECT statutory.approve_statutory_rule_version('"
            + TENANT_A
            + "','"
            + TAX_RULE_VERSION_ID
            + "','test',clock_timestamp())");

    statement.execute(
        """
        INSERT INTO statutory.employee_statutory_profile(
          id,tenant_id,payroll_relationship_id,jurisdiction_code,
          authority_code,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','IN','CENTRAL','test','test'
        )
        """.formatted(
            STATUTORY_PROFILE_ID,
            TENANT_A,
            RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO statutory.employee_statutory_profile_version(
          id,tenant_id,employee_statutory_profile_id,
          version_sequence,effective_from,registration_status,
          classification_code,approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'2026-01-01','REGISTERED',
          'STANDARD','DRAFT','test','test'
        )
        """.formatted(
            STATUTORY_PROFILE_VERSION_ID,
            TENANT_A,
            STATUTORY_PROFILE_ID));
    statement.execute(
        "SELECT statutory.approve_employee_statutory_profile_version('"
            + TENANT_A
            + "','"
            + STATUTORY_PROFILE_VERSION_ID
            + "','test',clock_timestamp())");

    insertRuleAssignment(
        statement,
        SOCIAL_ASSIGNMENT_ID,
        SOCIAL_RULE_ID,
        SOCIAL_RULE_VERSION_ID);
    insertRuleAssignment(
        statement,
        TAX_ASSIGNMENT_ID,
        TAX_RULE_ID,
        TAX_RULE_VERSION_ID);

    statement.execute(
        """
        INSERT INTO statutory.statutory_component_classification(
          id,tenant_id,jurisdiction_code,authority_code,
          assessment_base_code,component_id,component_version_id,
          classification_sequence,inclusion_percent,effective_from,
          effective_to,approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','IN','CENTRAL','GROSS','%s','%s',
          1,100,'2026-01-01','2027-01-01',
          'DRAFT','test','test'
        )
        """.formatted(
            CLASSIFICATION_ID,
            TENANT_A,
            COMPONENT_ID,
            COMPONENT_VERSION_ID));
    statement.execute(
        "SELECT statutory.approve_statutory_component_classification('"
            + TENANT_A
            + "','"
            + CLASSIFICATION_ID
            + "','test',clock_timestamp())");

    statement.execute(
        """
        INSERT INTO statutory.statutory_balance_year(
          id,tenant_id,jurisdiction_code,authority_code,
          balance_year_code,version_sequence,period_start,
          period_end,approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','IN','CENTRAL','IN_CENTRAL_2026',1,
          '2026-01-01','2027-01-01','DRAFT','test','test'
        )
        """.formatted(BALANCE_YEAR_ID, TENANT_A));
    statement.execute(
        "SELECT statutory.approve_statutory_balance_year('"
            + TENANT_A
            + "','"
            + BALANCE_YEAR_ID
            + "','test',clock_timestamp())");
  }

  private static void insertRule(
      Statement statement,
      String ruleId,
      String code,
      String category) throws SQLException {
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule(
          id,tenant_id,jurisdiction_code,authority_code,
          code,name,rule_category,created_by,updated_by
        ) VALUES (
          '%s','%s','IN','CENTRAL','%s','%s','%s','test','test'
        )
        """.formatted(
            ruleId,
            TENANT_A,
            code,
            code,
            category));
  }

  private static void insertRuleVersion(
      Statement statement,
      String versionId,
      String ruleId) throws SQLException {
    statement.execute(
        """
        INSERT INTO statutory.statutory_rule_version(
          id,tenant_id,statutory_rule_id,version_sequence,
          effective_from,currency,rounding_scale,rounding_mode,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'2026-01-01','INR',2,'HALF_UP',
          'DRAFT','test','test'
        )
        """.formatted(versionId, TENANT_A, ruleId));
  }

  private static void insertRuleAssignment(
      Statement statement,
      String assignmentId,
      String ruleId,
      String ruleVersionId) throws SQLException {
    statement.execute(
        """
        INSERT INTO statutory.employee_statutory_rule_assignment(
          id,tenant_id,employee_statutory_profile_id,
          employee_statutory_profile_version_id,payroll_assignment_id,
          payroll_assignment_version_id,statutory_rule_id,
          statutory_rule_version_id,assignment_sequence,effective_from,
          effective_to,eligibility_status,exemption_status,approval_status,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s','%s','%s','%s',
          1,'2026-01-01','2027-01-01',
          'ELIGIBLE','NONE','DRAFT','test','test'
        )
        """.formatted(
            assignmentId,
            TENANT_A,
            STATUTORY_PROFILE_ID,
            STATUTORY_PROFILE_VERSION_ID,
            ASSIGNMENT_ID,
            ASSIGNMENT_VERSION_ID,
            ruleId,
            ruleVersionId));
    statement.execute(
        "SELECT statutory.approve_employee_statutory_rule_assignment('"
            + TENANT_A
            + "','"
            + assignmentId
            + "','test',clock_timestamp())");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void setTenant(Connection connection, String tenant)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "SELECT set_config('app.tenant_id','" + tenant + "',false)");
    }
  }

  private static long queryLong(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static String queryString(Connection connection, String sql)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
