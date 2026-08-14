package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PayGroupRoutingFoundationMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final UUID LEGAL_ID =
      UUID.fromString("71000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID =
      UUID.fromString("71100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID =
      UUID.fromString("72000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID =
      UUID.fromString("72100000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_ID =
      UUID.fromString("73000000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_VERSION_ID =
      UUID.fromString("73100000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID =
      UUID.fromString("74000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_ID =
      UUID.fromString("75000000-0000-0000-0000-000000000001");
  private static final UUID PAY_GROUP_VERSION_ID =
      UUID.fromString("75100000-0000-0000-0000-000000000001");
  private static final UUID RELATIONSHIP_ID =
      UUID.fromString("76000000-0000-0000-0000-000000000001");
  private static final UUID RELATIONSHIP_VERSION_ID =
      UUID.fromString("76100000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNMENT_ID =
      UUID.fromString("77000000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNMENT_VERSION_ID =
      UUID.fromString("77100000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migratePopulatedV037ToV038() throws Exception {
    createRoles();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("37"))
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO platform.tenant(
            id, code, name, created_by, updated_by
          ) VALUES (
            '00000000-0000-0000-0000-0000000000f8',
            'PRE38',
            'Pre V038 tenant',
            'test',
            'test'
          )
          """);
    }

    Flyway flyway =
        Flyway.configure()
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
  void reset() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      seedTenant(statement, TENANT_A, "A");
      seedTenant(statement, TENANT_B, "B");
      seedOrganisationFoundation(statement);
    }
    seedEmployeePayrollFoundation();
  }

  @Test
  void v038CreatesForcedRlsRoutingRuleTable() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT relation.relrowsecurity, relation.relforcerowsecurity
                FROM pg_class relation
                JOIN pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'organisation'
                  AND relation.relname = 'pay_group_routing_rule'
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean(1)).isTrue();
      assertThat(result.getBoolean(2)).isTrue();
    }
  }

  @Test
  void establishmentRuleResolvesDeterministicallyAndIsTenantIsolated()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        UUID ruleId =
            scalarUuid(
                statement,
                """
                SELECT organisation.create_pay_group_routing_rule(
                  '%s','%s','%s','%s',10,
                  DATE '2026-01-01',DATE '2027-01-01','issuer|routing-admin'
                )
                """
                    .formatted(
                        TENANT_A,
                        PAY_GROUP_VERSION_ID,
                        PSU_VERSION_ID,
                        ESTABLISHMENT_VERSION_ID));

        assertThat(ruleId).isNotNull();

        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT pay_group_version_id, resolution_source, routing_rule_id
                FROM organisation.resolve_pay_group_version_for_assignment(
                  '%s','%s',DATE '2026-08-12'
                )
                """
                    .formatted(TENANT_A, ASSIGNMENT_VERSION_ID))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getObject(1, UUID.class))
              .isEqualTo(PAY_GROUP_VERSION_ID);
          assertThat(result.getString(2)).isEqualTo("ESTABLISHMENT_RULE");
          assertThat(result.getObject(3, UUID.class)).isEqualTo(ruleId);
          assertThat(result.next()).isFalse();
        }

        assertThat(count(statement, "organisation.pay_group_routing_rule"))
            .isEqualTo(1);
        connection.commit();

        connection.setAutoCommit(false);
        setTenant(statement, TENANT_B);
        assertThat(count(statement, "organisation.pay_group_routing_rule"))
            .isZero();
      }
    }
  }

  @Test
  void approvedExplicitAssignmentOverridesDefaultRouting()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        assertThat(
                scalarUuid(
                    statement,
                    """
                    SELECT organisation.create_pay_group_routing_rule(
                      '%s','%s','%s','%s',10,
                      DATE '2026-01-01',DATE '2027-01-01',
                      'issuer|routing-admin'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            PAY_GROUP_VERSION_ID,
                            PSU_VERSION_ID,
                            ESTABLISHMENT_VERSION_ID)))
            .isNotNull();
        connection.commit();
      }
    }

    UUID explicitId =
        UUID.fromString("78000000-0000-0000-0000-000000000001");
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        statement.execute(
            """
            INSERT INTO employee_payroll.pay_group_assignment(
              id,tenant_id,payroll_assignment_version_id,pay_group_version_id,
              effective_from,effective_to,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',
              DATE '2026-06-01',DATE '2026-12-01',
              'issuer|admin','issuer|admin'
            )
            """
                .formatted(
                    explicitId,
                    TENANT_A,
                    ASSIGNMENT_VERSION_ID,
                    PAY_GROUP_VERSION_ID));
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT employee_payroll.approve_pay_group_assignment(
                      '%s','%s','issuer|approver',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, explicitId)))
            .isEqualTo(1);
        connection.commit();
      }
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT pay_group_version_id, resolution_source, routing_rule_id
                FROM organisation.resolve_pay_group_version_for_assignment(
                  '%s','%s',DATE '2026-08-12'
                )
                """
                    .formatted(TENANT_A, ASSIGNMENT_VERSION_ID))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getObject(1, UUID.class))
              .isEqualTo(PAY_GROUP_VERSION_ID);
          assertThat(result.getString(2)).isEqualTo("EXPLICIT_ASSIGNMENT");
          assertThat(result.getObject(3)).isNull();
          assertThat(result.next()).isFalse();
        }
      }
    }
  }

  @Test
  void v039GrantsOnlyTheBoundedEffectiveEndFunction() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT
                  has_function_privilege(
                    current_user,
                    'organisation.end_date_pay_group_routing_rule(uuid,uuid,date,bigint,varchar,timestamptz)',
                    'EXECUTE'
                  ),
                  has_table_privilege(
                    current_user,
                    'organisation.pay_group_routing_rule',
                    'INSERT'
                  ) OR
                  has_table_privilege(
                    current_user,
                    'organisation.pay_group_routing_rule',
                    'UPDATE'
                  ) OR
                  has_table_privilege(
                    current_user,
                    'organisation.pay_group_routing_rule',
                    'DELETE'
                  )
                """)) {
          assertThat(result.next()).isTrue();
          assertThat(result.getBoolean(1)).isTrue();
          assertThat(result.getBoolean(2)).isFalse();
        }

        Savepoint directUpdate = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    "UPDATE organisation.pay_group_routing_rule "
                        + "SET effective_to=DATE '2026-07-01'"));
        connection.rollback(directUpdate);
      }
    }
  }

  @Test
  void v039EffectiveEndsAnActiveRuleWithOptimisticConcurrency()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        UUID ruleId = createBoundedRoutingRule(statement);

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-07-01',0,
                      'issuer|routing-admin',TIMESTAMPTZ '2026-06-15 10:00:00Z'
                    )
                    """
                        .formatted(TENANT_A, ruleId)))
            .isEqualTo(1);

        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT effective_to, status, updated_by, version_no,
                       updated_at = TIMESTAMPTZ '2026-06-15 10:00:00Z'
                FROM organisation.pay_group_routing_rule
                WHERE tenant_id='%s' AND id='%s'
                """
                    .formatted(TENANT_A, ruleId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getDate(1).toLocalDate().toString())
              .isEqualTo("2026-07-01");
          assertThat(result.getString(2)).isEqualTo("ACTIVE");
          assertThat(result.getString(3)).isEqualTo("issuer|routing-admin");
          assertThat(result.getLong(4)).isEqualTo(1);
          assertThat(result.getBoolean(5)).isTrue();
        }

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-06-01',0,
                      'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)))
            .isZero();

        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT routing_rule_id
                FROM organisation.resolve_pay_group_version_for_assignment(
                  '%s','%s',DATE '2026-07-01'
                )
                """
                    .formatted(TENANT_A, ASSIGNMENT_VERSION_ID))) {
          assertThat(result.next()).isFalse();
        }

        assertThat(
                scalarUuid(
                    statement,
                    """
                    SELECT organisation.create_pay_group_routing_rule(
                      '%s','%s','%s','%s',10,
                      DATE '2026-07-01',DATE '2027-01-01',
                      'issuer|routing-admin'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            PAY_GROUP_VERSION_ID,
                            PSU_VERSION_ID,
                            ESTABLISHMENT_VERSION_ID)))
            .isNotNull();
      }
    }
  }

  @Test
  void v039RejectsInvalidOrCrossTenantRequestsAndIgnoresInactiveRules()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);
        UUID ruleId = createBoundedRoutingRule(statement);

        Savepoint invalidRange = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-01-01',0,
                      'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)));
        connection.rollback(invalidRange);

        Savepoint blankActor = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-07-01',0,' ',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)));
        connection.rollback(blankActor);

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.retire_pay_group_routing_rule(
                      '%s','%s',0,'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)))
            .isEqualTo(1);
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-07-01',1,
                      'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)))
            .isZero();

        setTenant(statement, TENANT_B);
        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-07-01',1,
                      'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_B, ruleId)))
            .isZero();

        Savepoint tenantMismatch = connection.setSavepoint();
        assertSqlState(
            "42501",
            () ->
                statement.execute(
                    """
                    SELECT organisation.end_date_pay_group_routing_rule(
                      '%s','%s',DATE '2026-07-01',1,
                      'issuer|routing-admin',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ruleId)));
        connection.rollback(tenantMismatch);
      }
    }
  }

  @Test
  void wrongPsuRoutingAndAssignmentCompatibilityFailClosed()
      throws Exception {
    UUID otherPsuId =
        UUID.fromString("72000000-0000-0000-0000-000000000002");
    UUID otherPsuVersionId =
        UUID.fromString("72100000-0000-0000-0000-000000000002");
    UUID otherGroupId =
        UUID.fromString("75000000-0000-0000-0000-000000000002");
    UUID otherGroupVersionId =
        UUID.fromString("75100000-0000-0000-0000-000000000002");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO organisation.payroll_statutory_unit(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','OTHER_PSU','test','test')
          """
              .formatted(otherPsuId, TENANT_A));

      statement.execute(
          """
          INSERT INTO organisation.payroll_statutory_unit_version(
            id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,
            version_sequence,name,effective_from,effective_to,
            approval_status,approved_at,approved_by,created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s',1,'Other PSU',
            DATE '2026-01-01',DATE '2027-01-01',
            'APPROVED',clock_timestamp(),'test','test','test'
          )
          """
              .formatted(
                  otherPsuVersionId,
                  TENANT_A,
                  otherPsuId,
                  LEGAL_VERSION_ID));

      statement.execute(
          """
          INSERT INTO organisation.pay_group(
            id,tenant_id,code,created_by,updated_by
          ) VALUES ('%s','%s','OTHER_MONTHLY','test','test')
          """
              .formatted(otherGroupId, TENANT_A));

      statement.execute(
          """
          INSERT INTO organisation.pay_group_version(
            id,tenant_id,pay_group_id,payroll_statutory_unit_version_id,
            calendar_id,version_sequence,name,currency,proration_method,
            effective_from,effective_to,approval_status,approved_at,approved_by,
            created_by,updated_by
          ) VALUES (
            '%s','%s','%s','%s','%s',1,'Other Monthly',
            'INR','CALENDAR_DAYS',
            DATE '2026-01-01',DATE '2027-01-01',
            'APPROVED',clock_timestamp(),'test','test','test'
          )
          """
              .formatted(
                  otherGroupVersionId,
                  TENANT_A,
                  otherGroupId,
                  otherPsuVersionId,
                  CALENDAR_ID));
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        Savepoint wrongScope = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    SELECT organisation.create_pay_group_routing_rule(
                      '%s','%s','%s',NULL,100,
                      DATE '2026-01-01',DATE '2027-01-01',
                      'issuer|routing-admin'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            PAY_GROUP_VERSION_ID,
                            otherPsuVersionId)));
        connection.rollback(wrongScope);

        try (ResultSet result =
            statement.executeQuery(
                """
                SELECT issue_code
                FROM organisation.pay_group_assignment_compatibility_issues(
                  '%s','%s','%s',DATE '2026-01-01',DATE '2027-01-01'
                )
                ORDER BY issue_code
                """
                    .formatted(
                        TENANT_A,
                        ASSIGNMENT_VERSION_ID,
                        otherGroupVersionId))) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("PSU_MISMATCH");
        }

        Savepoint wrongAssignment = connection.setSavepoint();
        assertSqlState(
            "23514",
            () ->
                statement.execute(
                    """
                    INSERT INTO employee_payroll.pay_group_assignment(
                      id,tenant_id,payroll_assignment_version_id,
                      pay_group_version_id,effective_from,effective_to,
                      approval_status,created_by,updated_by
                    ) VALUES (
                      gen_random_uuid(),'%s','%s','%s',
                      DATE '2026-01-01',DATE '2027-01-01',
                      'DRAFT','issuer|admin','issuer|admin'
                    )
                    """
                        .formatted(
                            TENANT_A,
                            ASSIGNMENT_VERSION_ID,
                            otherGroupVersionId)));
        connection.rollback(wrongAssignment);
      }
    }
  }

  private static void seedTenant(
      Statement statement, UUID tenantId, String code) throws SQLException {
    statement.execute(
        """
        INSERT INTO platform.tenant(
          id,code,name,created_by,updated_by
        ) VALUES ('%s','%s','Synthetic Tenant %s','test','test')
        """
            .formatted(tenantId, code, code));
  }

  private static void seedOrganisationFoundation(Statement statement) throws SQLException {
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
          id,tenant_id,legal_entity_id,version_sequence,name,
          country_code,currency,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s',1,'Acme India','IN','INR',
          DATE '2026-01-01',DATE '2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
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
          id,tenant_id,payroll_statutory_unit_id,legal_entity_version_id,
          version_sequence,name,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Acme PSU',
          DATE '2026-01-01',DATE '2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
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
          id,tenant_id,establishment_id,payroll_statutory_unit_version_id,
          version_sequence,name,state_code,effective_from,effective_to,
          approval_status,approved_at,approved_by,created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s',1,'Bengaluru','KA',
          DATE '2026-01-01',DATE '2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
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
          id,tenant_id,code,name,frequency,timezone,created_by,updated_by
        ) VALUES (
          '%s','%s','MONTHLY_IN','Monthly India','MONTHLY',
          'Asia/Kolkata','test','test'
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
          id,tenant_id,pay_group_id,payroll_statutory_unit_version_id,
          calendar_id,version_sequence,name,currency,proration_method,
          effective_from,effective_to,approval_status,approved_at,approved_by,
          created_by,updated_by
        ) VALUES (
          '%s','%s','%s','%s','%s',1,'Monthly India',
          'INR','CALENDAR_DAYS',
          DATE '2026-01-01',DATE '2027-01-01',
          'APPROVED',clock_timestamp(),'test','test','test'
        )
        """
            .formatted(
                PAY_GROUP_VERSION_ID,
                TENANT_A,
                PAY_GROUP_ID,
                PSU_VERSION_ID,
                CALENDAR_ID));

  }

  private static void seedEmployeePayrollFoundation()
      throws SQLException {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        setTenant(statement, TENANT_A);

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship(
              id,tenant_id,external_employee_id,employee_number,
              created_by,updated_by
            ) VALUES ('%s','%s','EMP-EXT-1','E0001','test','test')
            """
                .formatted(RELATIONSHIP_ID, TENANT_A));

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_relationship_version(
              id,tenant_id,payroll_relationship_id,legal_entity_version_id,
              version_sequence,relationship_start,relationship_end,
              created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s',1,
              DATE '2026-01-01',DATE '2027-01-01',
              'test','test'
            )
            """
                .formatted(
                    RELATIONSHIP_VERSION_ID,
                    TENANT_A,
                    RELATIONSHIP_ID,
                    LEGAL_VERSION_ID));

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT employee_payroll.approve_payroll_relationship_version(
                      '%s','%s','test',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, RELATIONSHIP_VERSION_ID)))
            .isEqualTo(1);

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_assignment(
              id,tenant_id,payroll_relationship_id,assignment_number,
              created_by,updated_by
            ) VALUES ('%s','%s','%s','A0001','test','test')
            """
                .formatted(ASSIGNMENT_ID, TENANT_A, RELATIONSHIP_ID));

        statement.execute(
            """
            INSERT INTO employee_payroll.payroll_assignment_version(
              id,tenant_id,payroll_assignment_id,payroll_relationship_version_id,
              establishment_version_id,version_sequence,
              assignment_start,assignment_end,created_by,updated_by
            ) VALUES (
              '%s','%s','%s','%s','%s',1,
              DATE '2026-01-01',DATE '2027-01-01',
              'test','test'
            )
            """
                .formatted(
                    ASSIGNMENT_VERSION_ID,
                    TENANT_A,
                    ASSIGNMENT_ID,
                    RELATIONSHIP_VERSION_ID,
                    ESTABLISHMENT_VERSION_ID));

        assertThat(
                scalarLong(
                    statement,
                    """
                    SELECT employee_payroll.approve_payroll_assignment_version(
                      '%s','%s','test',clock_timestamp()
                    )
                    """
                        .formatted(TENANT_A, ASSIGNMENT_VERSION_ID)))
            .isEqualTo(1);

        connection.commit();
      }
    }
  }

  private static long count(Statement statement, String relation)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery("SELECT count(*) FROM " + relation)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long scalarLong(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static UUID scalarUuid(Statement statement, String sql)
      throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static UUID createBoundedRoutingRule(Statement statement)
      throws SQLException {
    return scalarUuid(
        statement,
        """
        SELECT organisation.create_pay_group_routing_rule(
          '%s','%s','%s','%s',10,
          DATE '2026-01-01',DATE '2027-01-01','issuer|routing-admin'
        )
        """
            .formatted(
                TENANT_A,
                PAY_GROUP_VERSION_ID,
                PSU_VERSION_ID,
                ESTABLISHMENT_VERSION_ID));
  }

  private static void setTenant(Statement statement, UUID tenant)
      throws SQLException {
    statement.execute("SET LOCAL app.tenant_id='" + tenant + "'");
  }

  private static void assertSqlState(String state, SqlWork work) {
    try {
      work.run();
      throw new AssertionError("Expected SQL state " + state);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo(state);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private static void createRoles() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB "
              + "NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
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
    }
  }

  @FunctionalInterface
  private interface SqlWork {
    void run() throws SQLException;
  }
}
