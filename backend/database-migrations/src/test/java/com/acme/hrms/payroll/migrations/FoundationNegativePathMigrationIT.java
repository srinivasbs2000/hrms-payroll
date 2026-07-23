package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FoundationNegativePathMigrationIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final UUID LEGAL_ID =
      UUID.fromString("61000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID =
      UUID.fromString("61100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID =
      UUID.fromString("62000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID =
      UUID.fromString("62100000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_VERSION_ID =
      UUID.fromString("63100000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID =
      UUID.fromString("64000000-0000-0000-0000-000000000001");
  private static final UUID PERIOD_ID =
      UUID.fromString("64100000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_ID =
      UUID.fromString("65000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION_ID =
      UUID.fromString("65100000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_ID =
      UUID.fromString("66000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID =
      UUID.fromString("66100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_ID =
      UUID.fromString("67000000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_VERSION_ID =
      UUID.fromString("67100000-0000-0000-0000-000000000001");
  private static final UUID STRUCTURE_LINE_ID =
      UUID.fromString("67200000-0000-0000-0000-000000000001");
  private static final UUID RELATIONSHIP_ID =
      UUID.fromString("68000000-0000-0000-0000-000000000001");
  private static final UUID RELATIONSHIP_VERSION_ID =
      UUID.fromString("68100000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNMENT_ID =
      UUID.fromString("69000000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNMENT_VERSION_ID =
      UUID.fromString("69100000-0000-0000-0000-000000000001");
  private static final UUID GROUP_ASSIGNMENT_ID =
      UUID.fromString("6a000000-0000-0000-0000-000000000001");
  private static final UUID SALARY_ASSIGNMENT_ID =
      UUID.fromString("6b000000-0000-0000-0000-000000000001");
  private static final UUID CYCLE_ID =
      UUID.fromString("6c000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromZero() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '"
              + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '"
              + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT "
              + "NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute(
          "ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute(
          "GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute(
          "GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }

    Flyway flyway = Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load();
    flyway.migrate();
    flyway.validate();
  }

  @BeforeEach
  void resetAndSeed() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id,
            code,
            name,
            created_by,
            updated_by
          ) VALUES
            ('%s','A','Synthetic Tenant A','test','test'),
            ('%s','B','Synthetic Tenant B','test','test')
          """
              .formatted(TENANT_A, TENANT_B));
    }

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET ROLE payroll_owner");
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");
        seedApprovedFoundation(statement);
      }
      connection.commit();
    }
  }

  @Test
  void childOrganisationApprovalRequiresApprovedParent()
      throws Exception {
    UUID draftLegalId =
        UUID.fromString("71000000-0000-0000-0000-000000000001");
    UUID draftLegalVersionId =
        UUID.fromString("71100000-0000-0000-0000-000000000001");
    UUID draftPsuId =
        UUID.fromString("72000000-0000-0000-0000-000000000001");
    UUID draftPsuVersionId =
        UUID.fromString("72100000-0000-0000-0000-000000000001");
    UUID secondPsuId =
        UUID.fromString("72000000-0000-0000-0000-000000000002");
    UUID secondPsuVersionId =
        UUID.fromString("72100000-0000-0000-0000-000000000002");
    UUID draftEstablishmentId =
        UUID.fromString("73000000-0000-0000-0000-000000000001");
    UUID draftEstablishmentVersionId =
        UUID.fromString("73100000-0000-0000-0000-000000000001");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        statement.execute(
            """
            INSERT INTO organisation.legal_entity(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','DRAFT_LE','test','test')
            """
                .formatted(draftLegalId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.legal_entity_version(
              id,tenant_id,legal_entity_id,version_sequence,
              name,country_code,currency,effective_from,effective_to,
              approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s',1,'Draft Legal','IN','INR',
              '2027-01-01','2029-01-01','DRAFT','test','test'
            )
            """
                .formatted(
                    draftLegalVersionId,
                    TENANT_A,
                    draftLegalId));

        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','DRAFT_PSU','test','test')
            """
                .formatted(draftPsuId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit_version(
              id,tenant_id,payroll_statutory_unit_id,
              legal_entity_version_id,version_sequence,name,
              effective_from,effective_to,approval_status,
              created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,'Draft PSU',
              '2027-01-01','2029-01-01','DRAFT','test','test'
            )
            """
                .formatted(
                    draftPsuVersionId,
                    TENANT_A,
                    draftPsuId,
                    draftLegalVersionId));

        assertFunctionResult(
            statement,
            """
            SELECT organisation.approve_version(
              'PAYROLL_STATUTORY_UNIT','%s','%s','test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    draftPsuVersionId,
                    Instant.parse("2026-07-23T00:00:00Z")),
            0);

        assertFunctionResult(
            statement,
            """
            SELECT organisation.approve_version(
              'LEGAL_ENTITY','%s','%s','test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    draftLegalVersionId,
                    Instant.parse("2026-07-23T00:01:00Z")),
            1);

        assertFunctionResult(
            statement,
            """
            SELECT organisation.approve_version(
              'PAYROLL_STATUTORY_UNIT','%s','%s','test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    draftPsuVersionId,
                    Instant.parse("2026-07-23T00:02:00Z")),
            1);

        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','SECOND_DRAFT_PSU','test','test')
            """
                .formatted(secondPsuId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.payroll_statutory_unit_version(
              id,tenant_id,payroll_statutory_unit_id,
              legal_entity_version_id,version_sequence,name,
              effective_from,effective_to,approval_status,
              created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,'Second Draft PSU',
              '2027-01-01','2029-01-01','DRAFT','test','test'
            )
            """
                .formatted(
                    secondPsuVersionId,
                    TENANT_A,
                    secondPsuId,
                    draftLegalVersionId));

        statement.execute(
            """
            INSERT INTO organisation.establishment(
              id,tenant_id,code,created_by,updated_by
            ) VALUES ('%s','%s','DRAFT_EST','test','test')
            """
                .formatted(draftEstablishmentId, TENANT_A));
        statement.execute(
            """
            INSERT INTO organisation.establishment_version(
              id,tenant_id,establishment_id,
              payroll_statutory_unit_version_id,version_sequence,
              name,state_code,effective_from,effective_to,
              approval_status,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,'Draft Establishment','KA',
              '2027-01-01','2029-01-01','DRAFT','test','test'
            )
            """
                .formatted(
                    draftEstablishmentVersionId,
                    TENANT_A,
                    draftEstablishmentId,
                    secondPsuVersionId));

        assertFunctionResult(
            statement,
            """
            SELECT organisation.approve_version(
              'ESTABLISHMENT','%s','%s','test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    draftEstablishmentVersionId,
                    Instant.parse("2026-07-23T00:03:00Z")),
            0);
      }
      connection.rollback();
    }
  }

  @Test
  void parentEndDatesCannotTruncateExactDependents()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        assertFunctionResult(
            statement,
            """
            SELECT organisation.end_date_version(
              'LEGAL_ENTITY','%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    LEGAL_VERSION_ID,
                    Instant.parse("2026-07-23T01:00:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT organisation.end_date_version(
              'PAYROLL_STATUTORY_UNIT','%s','%s',
              '2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    PSU_VERSION_ID,
                    Instant.parse("2026-07-23T01:01:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT organisation.end_date_version(
              'ESTABLISHMENT','%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    ESTABLISHMENT_VERSION_ID,
                    Instant.parse("2026-07-23T01:02:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT organisation.end_date_pay_group_version(
              '%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    PAY_GROUP_VERSION_ID,
                    Instant.parse("2026-07-23T01:03:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_payroll_relationship_version(
              '%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    RELATIONSHIP_VERSION_ID,
                    Instant.parse("2026-07-23T01:04:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_payroll_assignment_version(
              '%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    ASSIGNMENT_VERSION_ID,
                    Instant.parse("2026-07-23T01:05:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT compensation.end_date_salary_structure_version(
              '%s','%s','2029-01-01',0,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    STRUCTURE_VERSION_ID,
                    Instant.parse("2026-07-23T01:06:00Z")),
            0);
      }
      connection.rollback();
    }
  }

  @Test
  void payrollCyclePeriodMustFitExactPayGroupVersion()
      throws Exception {
    UUID outsidePeriod =
        UUID.fromString("74100000-0000-0000-0000-000000000001");
    UUID outsideCycle =
        UUID.fromString("74200000-0000-0000-0000-000000000001");

    assertSqlState(
        "23514",
        () -> {
          try (Connection connection = admin()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              statement.execute("SET ROLE payroll_owner");
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO organisation.pay_period(
                    id,tenant_id,calendar_id,period_code,
                    period_start,period_end,payment_date,status,
                    created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','2030-01',
                    '2030-01-01','2030-01-31','2030-01-31',
                    'OPEN','test','test'
                  )
                  """
                      .formatted(
                          outsidePeriod,
                          TENANT_A,
                          CALENDAR_ID));
              statement.execute(
                  """
                  INSERT INTO payroll_ops.payroll_cycle(
                    id,tenant_id,pay_group_id,pay_period_id,
                    cycle_type,status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',
                    'REGULAR','DRAFT','test','test'
                  )
                  """
                      .formatted(
                          outsideCycle,
                          TENANT_A,
                          PAY_GROUP_VERSION_ID,
                          outsidePeriod));
            }
          }
        });
  }

  @Test
  void crossTenantEmployeeDependenciesAreRejected()
      throws Exception {
    UUID relationshipId =
        UUID.fromString("75000000-0000-0000-0000-000000000001");
    UUID relationshipVersionId =
        UUID.fromString("75100000-0000-0000-0000-000000000001");

    assertSqlState(
        "23503",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_B);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship(
                    id,tenant_id,external_employee_id,employee_number,
                    created_by,updated_by
                  ) VALUES (
                    '%s','%s','EMP-EXT-B','EMP-B','test','test'
                  )
                  """
                      .formatted(relationshipId, TENANT_B));
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship_version(
                    id,tenant_id,payroll_relationship_id,
                    legal_entity_version_id,version_sequence,
                    relationship_start,relationship_end,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',1,
                    '2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          relationshipVersionId,
                          TENANT_B,
                          relationshipId,
                          LEGAL_VERSION_ID));
            }
          }
        });
  }

  @Test
  void employeeConfigurationRejectsInvalidParentStateRangeAndCurrency()
      throws Exception {
    UUID outsideRelationshipId =
        UUID.fromString("76000000-0000-0000-0000-000000000001");
    UUID outsideRelationshipVersionId =
        UUID.fromString("76100000-0000-0000-0000-000000000001");

    assertSqlState(
        "23514",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship(
                    id,tenant_id,external_employee_id,employee_number,
                    created_by,updated_by
                  ) VALUES (
                    '%s','%s','EMP-EXT-OUTSIDE',
                    'EMP-OUTSIDE','test','test'
                  )
                  """
                      .formatted(outsideRelationshipId, TENANT_A));
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship_version(
                    id,tenant_id,payroll_relationship_id,
                    legal_entity_version_id,version_sequence,
                    relationship_start,relationship_end,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',1,
                    '2025-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          outsideRelationshipVersionId,
                          TENANT_A,
                          outsideRelationshipId,
                          LEGAL_VERSION_ID));
            }
          }
        });

    UUID draftRelationshipId =
        UUID.fromString("76000000-0000-0000-0000-000000000002");
    UUID draftRelationshipVersionId =
        UUID.fromString("76100000-0000-0000-0000-000000000002");
    UUID draftAssignmentId =
        UUID.fromString("76200000-0000-0000-0000-000000000002");
    UUID draftAssignmentVersionId =
        UUID.fromString("76300000-0000-0000-0000-000000000002");

    assertSqlState(
        "23514",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship(
                    id,tenant_id,external_employee_id,employee_number,
                    created_by,updated_by
                  ) VALUES (
                    '%s','%s','EMP-EXT-DRAFT',
                    'EMP-DRAFT','test','test'
                  )
                  """
                      .formatted(draftRelationshipId, TENANT_A));
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_relationship_version(
                    id,tenant_id,payroll_relationship_id,
                    legal_entity_version_id,version_sequence,
                    relationship_start,relationship_end,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',1,
                    '2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          draftRelationshipVersionId,
                          TENANT_A,
                          draftRelationshipId,
                          LEGAL_VERSION_ID));
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_assignment(
                    id,tenant_id,payroll_relationship_id,
                    assignment_number,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','ASN-DRAFT','test','test'
                  )
                  """
                      .formatted(
                          draftAssignmentId,
                          TENANT_A,
                          draftRelationshipId));
              statement.execute(
                  """
                  INSERT INTO employee_payroll.payroll_assignment_version(
                    id,tenant_id,payroll_assignment_id,
                    payroll_relationship_version_id,
                    establishment_version_id,version_sequence,
                    assignment_start,assignment_end,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s','%s',1,
                    '2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          draftAssignmentVersionId,
                          TENANT_A,
                          draftAssignmentId,
                          draftRelationshipVersionId,
                          ESTABLISHMENT_VERSION_ID));
            }
          }
        });

    UUID invalidSalaryAssignmentId =
        UUID.fromString("76400000-0000-0000-0000-000000000001");

    assertSqlState(
        "23514",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.salary_assignment(
                    id,tenant_id,payroll_assignment_version_id,
                    salary_structure_version_id,monthly_amount,
                    currency,effective_from,effective_to,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',70000.0000,
                    'USD','2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          invalidSalaryAssignmentId,
                          TENANT_A,
                          ASSIGNMENT_VERSION_ID,
                          STRUCTURE_VERSION_ID));
            }
          }
        });
  }

  @Test
  void approvedEmployeeAssignmentsCannotOverlap()
      throws Exception {
    UUID groupAssignmentId =
        UUID.fromString("77000000-0000-0000-0000-000000000001");

    assertSqlState(
        "23P01",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.pay_group_assignment(
                    id,tenant_id,payroll_assignment_version_id,
                    pay_group_version_id,effective_from,effective_to,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',
                    '2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          groupAssignmentId,
                          TENANT_A,
                          ASSIGNMENT_VERSION_ID,
                          PAY_GROUP_VERSION_ID));
              statement.executeQuery(
                  """
                  SELECT employee_payroll.approve_pay_group_assignment(
                    '%s','%s','test','%s'
                  )
                  """
                      .formatted(
                          TENANT_A,
                          groupAssignmentId,
                          Instant.parse("2026-07-23T02:00:00Z")));
            }
          }
        });

    UUID salaryAssignmentId =
        UUID.fromString("77100000-0000-0000-0000-000000000001");

    assertSqlState(
        "23P01",
        () -> {
          try (Connection connection = app()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
              setTenant(statement, TENANT_A);
              statement.execute(
                  """
                  INSERT INTO employee_payroll.salary_assignment(
                    id,tenant_id,payroll_assignment_version_id,
                    salary_structure_version_id,monthly_amount,
                    currency,effective_from,effective_to,
                    approval_status,created_by,updated_by
                  ) VALUES (
                    '%s','%s','%s','%s',80000.0000,
                    'INR','2027-01-01','2029-01-01',
                    'DRAFT','test','test'
                  )
                  """
                      .formatted(
                          salaryAssignmentId,
                          TENANT_A,
                          ASSIGNMENT_VERSION_ID,
                          STRUCTURE_VERSION_ID));
              statement.executeQuery(
                  """
                  SELECT employee_payroll.approve_salary_assignment(
                    '%s','%s','test','%s'
                  )
                  """
                      .formatted(
                          TENANT_A,
                          salaryAssignmentId,
                          Instant.parse("2026-07-23T02:01:00Z")));
            }
          }
        });
  }

  @Test
  void historyMutationAndStaleVersionWritesAreRejected()
      throws Exception {
    assertSqlState(
        "P0001",
        () -> directAdminUpdate(
            "employee_payroll.payroll_relationship_version",
            RELATIONSHIP_VERSION_ID));
    assertSqlState(
        "P0001",
        () -> directAdminUpdate(
            "employee_payroll.payroll_assignment_version",
            ASSIGNMENT_VERSION_ID));
    assertSqlState(
        "P0001",
        () -> directAdminUpdate(
            "employee_payroll.pay_group_assignment",
            GROUP_ASSIGNMENT_ID));
    assertSqlState(
        "P0001",
        () -> directAdminUpdate(
            "employee_payroll.salary_assignment",
            SALARY_ASSIGNMENT_ID));

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_payroll_relationship_version(
              '%s','%s','2029-01-01',99,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    RELATIONSHIP_VERSION_ID,
                    Instant.parse("2026-07-23T03:00:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_payroll_assignment_version(
              '%s','%s','2029-01-01',99,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    ASSIGNMENT_VERSION_ID,
                    Instant.parse("2026-07-23T03:01:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_pay_group_assignment(
              '%s','%s','2029-01-01',99,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    GROUP_ASSIGNMENT_ID,
                    Instant.parse("2026-07-23T03:02:00Z")),
            0);
        assertFunctionResult(
            statement,
            """
            SELECT employee_payroll.end_date_salary_assignment(
              '%s','%s','2029-01-01',99,'test','%s'
            )
            """
                .formatted(
                    TENANT_A,
                    SALARY_ASSIGNMENT_ID,
                    Instant.parse("2026-07-23T03:03:00Z")),
            0);
      }
      connection.rollback();
    }
  }

  private static void seedApprovedFoundation(Statement statement)
      throws Exception {
    statement.execute(
        """
        INSERT INTO organisation.legal_entity(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_IN','test','test')
        """
            .formatted(LEGAL_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.legal_entity_version(
          id,tenant_id,legal_entity_id,version_sequence,
          name,country_code,currency,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Acme India','IN','INR',
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(LEGAL_VERSION_ID, TENANT_A, LEGAL_ID));

    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','ACME_PSU','test','test')
        """
            .formatted(PSU_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO organisation.payroll_statutory_unit_version(
          id,tenant_id,payroll_statutory_unit_id,
          legal_entity_version_id,version_sequence,name,
          effective_from,effective_to,approval_status,
          approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Acme PSU',
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                PSU_VERSION_ID,
                TENANT_A,
                PSU_ID,
                LEGAL_VERSION_ID));

    statement.execute(
        """
        INSERT INTO organisation.establishment(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','BLR','test','test')
        """
            .formatted(ESTABLISHMENT_ID, TENANT_A));
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                ESTABLISHMENT_VERSION_ID,
                TENANT_A,
                ESTABLISHMENT_ID,
                PSU_VERSION_ID));

    statement.execute(
        """
        INSERT INTO organisation.payroll_calendar(
          id,tenant_id,code,name,frequency,timezone,
          created_by,updated_by
        ) VALUES (
          '%s','%s','MONTHLY_IN','Monthly India',
          'MONTHLY','Asia/Kolkata','test','test'
        )
        """
            .formatted(CALENDAR_ID, TENANT_A));

    statement.execute(
        """
        INSERT INTO organisation.pay_group(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','MONTHLY_IN','test','test')
        """
            .formatted(PAY_GROUP_ID, TENANT_A));
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                PAY_GROUP_VERSION_ID,
                TENANT_A,
                PAY_GROUP_ID,
                PSU_VERSION_ID,
                CALENDAR_ID));

    statement.execute(
        """
        INSERT INTO organisation.pay_period(
          id,tenant_id,calendar_id,period_code,
          period_start,period_end,payment_date,status,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','2027-01',
          '2027-01-01','2027-01-31','2027-01-31',
          'OPEN','test','test'
        )
        """
            .formatted(PERIOD_ID, TENANT_A, CALENDAR_ID));

    statement.execute(
        """
        INSERT INTO compensation.pay_component(
          id,tenant_id,code,name,component_type,
          created_by,updated_by
        ) VALUES (
          '%s','%s','BASIC','Basic Pay','EARNING',
          'test','test'
        )
        """
            .formatted(COMPONENT_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.pay_component_version(
          id,tenant_id,component_id,version_sequence,
          formula_type,formula_expression,fixed_amount,
          rounding_scale,effective_from,effective_to,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'FIXED',NULL,75000.0000,2,
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                COMPONENT_VERSION_ID,
                TENANT_A,
                COMPONENT_ID));

    statement.execute(
        """
        INSERT INTO compensation.salary_structure(
          id,tenant_id,code,created_by,updated_by
        ) VALUES ('%s','%s','DEFAULT','test','test')
        """
            .formatted(STRUCTURE_ID, TENANT_A));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_version(
          id,tenant_id,salary_structure_id,version_sequence,
          name,currency,effective_from,effective_to,
          approval_status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Default Structure','INR',
          '2026-01-01','2030-01-01','DRAFT','test','test'
        )
        """
            .formatted(
                STRUCTURE_VERSION_ID,
                TENANT_A,
                STRUCTURE_ID));
    statement.execute(
        """
        INSERT INTO compensation.salary_structure_line(
          id,tenant_id,salary_structure_version_id,
          component_version_id,sequence_no,target_amount,
          effective_from,effective_to,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,75000.0000,
          '2026-01-01','2030-01-01','test','test'
        )
        """
            .formatted(
                STRUCTURE_LINE_ID,
                TENANT_A,
                STRUCTURE_VERSION_ID,
                COMPONENT_VERSION_ID));

    assertFunctionResult(
        statement,
        """
        SELECT compensation.approve_salary_structure_version(
          '%s','%s','test','%s'
        )
        """
            .formatted(
                TENANT_A,
                STRUCTURE_VERSION_ID,
                Instant.parse("2026-07-23T00:30:00Z")),
        1);

    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_relationship(
          id,tenant_id,external_employee_id,employee_number,
          created_by,updated_by
        ) VALUES (
          '%s','%s','EMP-EXT-1','EMP-1','test','test'
        )
        """
            .formatted(RELATIONSHIP_ID, TENANT_A));
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
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                RELATIONSHIP_VERSION_ID,
                TENANT_A,
                RELATIONSHIP_ID,
                LEGAL_VERSION_ID));

    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment(
          id,tenant_id,payroll_relationship_id,
          assignment_number,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','ASN-1','test','test'
        )
        """
            .formatted(
                ASSIGNMENT_ID,
                TENANT_A,
                RELATIONSHIP_ID));
    statement.execute(
        """
        INSERT INTO employee_payroll.payroll_assignment_version(
          id,tenant_id,payroll_assignment_id,
          payroll_relationship_version_id,
          establishment_version_id,version_sequence,
          assignment_start,assignment_end,
          approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
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
          '%s','%s','%s','%s',
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
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
          '%s','%s','%s','%s',75000.0000,'INR',
          '2026-01-01','2030-01-01','APPROVED',
          clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                SALARY_ASSIGNMENT_ID,
                TENANT_A,
                ASSIGNMENT_VERSION_ID,
                STRUCTURE_VERSION_ID));

    statement.execute(
        """
        INSERT INTO payroll_ops.payroll_cycle(
          id,tenant_id,pay_group_id,pay_period_id,
          cycle_type,status,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',
          'REGULAR','DRAFT','test','test'
        )
        """
            .formatted(
                CYCLE_ID,
                TENANT_A,
                PAY_GROUP_VERSION_ID,
                PERIOD_ID));
  }

  private static void directAdminUpdate(
      String table, UUID id) throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "UPDATE "
              + table
              + " SET updated_by='forbidden' WHERE id='"
              + id
              + "'");
    }
  }

  private static void assertFunctionResult(
      Statement statement, String sql, long expected)
      throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getLong(1)).isEqualTo(expected);
      assertThat(result.next()).isFalse();
    }
  }

  private static void assertSqlState(
      String expected, SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .satisfies(
            exception ->
                assertThat(((SQLException) exception).getSQLState())
                    .isEqualTo(expected));
  }

  private static void setTenant(
      Statement statement, UUID tenantId) throws Exception {
    statement.execute(
        "SET LOCAL app.tenant_id='" + tenantId + "'");
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws Exception;
  }
}
