package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SalaryStructureMigrationIT {
  private static final String APP_PASSWORD =
      "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD =
      "synthetic-migrator-password";

  private static final UUID TENANT_A =
      UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B =
      UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final UUID COMPONENT_ID =
      UUID.fromString("21000000-0000-0000-0000-000000000001");
  private static final UUID COMPONENT_VERSION_ID =
      UUID.fromString("21100000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_STRUCTURE_VERSION_ID =
      UUID.fromString("22000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_LINE_ID =
      UUID.fromString("22100000-0000-0000-0000-000000000001");

  private static final UUID LEGAL_ID =
      UUID.fromString("11000000-0000-0000-0000-000000000001");
  private static final UUID LEGAL_VERSION_ID =
      UUID.fromString("11100000-0000-0000-0000-000000000001");
  private static final UUID PSU_ID =
      UUID.fromString("12000000-0000-0000-0000-000000000001");
  private static final UUID PSU_VERSION_ID =
      UUID.fromString("12100000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_ID =
      UUID.fromString("13000000-0000-0000-0000-000000000001");
  private static final UUID ESTABLISHMENT_VERSION_ID =
      UUID.fromString("13100000-0000-0000-0000-000000000001");

  private static final UUID RELATIONSHIP_ID =
      UUID.fromString("31000000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNMENT_ID =
      UUID.fromString("31100000-0000-0000-0000-000000000001");
  private static final UUID SALARY_ASSIGNMENT_ID =
      UUID.fromString("31200000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrateFromV019WithLegacySalaryStructureData()
      throws Exception {
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

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("19"))
        .load()
        .migrate();

    seedLegacyV019Data();

    Flyway.configure()
        .dataSource(
            POSTGRES.getJdbcUrl(),
            "payroll_migrator",
            MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES ('"
              + TENANT_B
              + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void legacyVersionLineAndSalaryAssignmentLineageArePreserved()
      throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT identity.code,version.id,"
                    + "version.version_sequence,"
                    + "version.approval_status,"
                    + "line.salary_structure_version_id line_version_id,"
                    + "assignment.salary_structure_version_id "
                    + "assignment_version_id "
                    + "FROM compensation.salary_structure identity "
                    + "JOIN compensation.salary_structure_version version "
                    + "ON version.tenant_id=identity.tenant_id "
                    + "AND version.salary_structure_id=identity.id "
                    + "JOIN compensation.salary_structure_line line "
                    + "ON line.tenant_id=version.tenant_id "
                    + "AND line.salary_structure_version_id=version.id "
                    + "JOIN employee_payroll.salary_assignment assignment "
                    + "ON assignment.tenant_id=version.tenant_id "
                    + "AND assignment.salary_structure_version_id=version.id "
                    + "WHERE identity.tenant_id='"
                    + TENANT_A
                    + "'")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("code")).isEqualTo("DEFAULT");
      assertThat(result.getObject("id", UUID.class))
          .isEqualTo(LEGACY_STRUCTURE_VERSION_ID);
      assertThat(result.getInt("version_sequence")).isOne();
      assertThat(result.getString("approval_status"))
          .isEqualTo("APPROVED");
      assertThat(
          result.getObject("line_version_id", UUID.class))
          .isEqualTo(LEGACY_STRUCTURE_VERSION_ID);
      assertThat(
          result.getObject("assignment_version_id", UUID.class))
          .isEqualTo(LEGACY_STRUCTURE_VERSION_ID);
      assertThat(result.next()).isFalse();
    }
  }

  @Test
  void appRoleCanApproveAndEndDateACompleteDraft()
      throws Exception {
    UUID identityId =
        UUID.fromString("22000000-0000-0000-0000-000000000002");
    UUID versionId =
        UUID.fromString("22200000-0000-0000-0000-000000000002");
    UUID lineId =
        UUID.fromString("22300000-0000-0000-0000-000000000002");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            "INSERT INTO compensation.salary_structure("
                + "id,tenant_id,code,created_by,updated_by) VALUES ('"
                + identityId
                + "','"
                + TENANT_A
                + "','EXECUTIVE','test','test')");

        statement.execute(
            "INSERT INTO compensation.salary_structure_version("
                + "id,tenant_id,salary_structure_id,version_sequence,"
                + "name,currency,effective_from,effective_to,"
                + "approval_status,created_by,updated_by) VALUES ('"
                + versionId
                + "','"
                + TENANT_A
                + "','"
                + identityId
                + "',1,'Executive Structure','INR',"
                + "'2027-01-01','2028-01-01','DRAFT',"
                + "'test','test')");

        statement.execute(
            "INSERT INTO compensation.salary_structure_line("
                + "id,tenant_id,salary_structure_version_id,"
                + "component_version_id,sequence_no,target_amount,"
                + "effective_from,effective_to,created_by,updated_by"
                + ") VALUES ('"
                + lineId
                + "','"
                + TENANT_A
                + "','"
                + versionId
                + "','"
                + COMPONENT_VERSION_ID
                + "',1,2000.0000,'2027-01-01','2028-01-01',"
                + "'test','test')");

        try (ResultSet approved =
            statement.executeQuery(
                "SELECT compensation.approve_salary_structure_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','test','"
                    + Instant.parse("2026-07-22T04:00:00Z")
                    + "')")) {
          assertThat(approved.next()).isTrue();
          assertThat(approved.getLong(1)).isOne();
        }

        try (ResultSet ended =
            statement.executeQuery(
                "SELECT compensation.end_date_salary_structure_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','2027-12-01',1,'test','"
                    + Instant.parse("2026-07-22T04:01:00Z")
                    + "')")) {
          assertThat(ended.next()).isTrue();
          assertThat(ended.getLong(1)).isOne();
        }

        try (ResultSet state =
            statement.executeQuery(
                "SELECT version.approval_status,"
                    + "version.effective_to,version.version_no,"
                    + "line.effective_to line_effective_to "
                    + "FROM compensation.salary_structure_version version "
                    + "JOIN compensation.salary_structure_line line "
                    + "ON line.tenant_id=version.tenant_id "
                    + "AND line.salary_structure_version_id=version.id "
                    + "WHERE version.id='"
                    + versionId
                    + "'")) {
          assertThat(state.next()).isTrue();
          assertThat(state.getString("approval_status"))
              .isEqualTo("APPROVED");
          assertThat(state.getString("effective_to"))
              .isEqualTo("2027-12-01");
          assertThat(state.getLong("version_no")).isEqualTo(2);
          assertThat(state.getString("line_effective_to"))
              .isEqualTo("2027-12-01");
        }

        try (ResultSet privilege =
            statement.executeQuery(
                "SELECT "
                    + "has_table_privilege(current_user,"
                    + "'compensation.salary_structure_version','UPDATE'),"
                    + "has_table_privilege(current_user,"
                    + "'compensation.salary_structure_line','UPDATE')")) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
          assertThat(privilege.getBoolean(2)).isFalse();
        }
      }
      connection.commit();
    }
  }

  @Test
  void approvalRequiresAtLeastOneStructureLine()
      throws Exception {
    UUID identityId =
        UUID.fromString("22000000-0000-0000-0000-000000000003");
    UUID versionId =
        UUID.fromString("22200000-0000-0000-0000-000000000003");

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_A + "'");

        statement.execute(
            "INSERT INTO compensation.salary_structure("
                + "id,tenant_id,code,created_by,updated_by) VALUES ('"
                + identityId
                + "','"
                + TENANT_A
                + "','EMPTY','test','test')");

        statement.execute(
            "INSERT INTO compensation.salary_structure_version("
                + "id,tenant_id,salary_structure_id,version_sequence,"
                + "name,currency,effective_from,effective_to,"
                + "approval_status,created_by,updated_by) VALUES ('"
                + versionId
                + "','"
                + TENANT_A
                + "','"
                + identityId
                + "',1,'Empty Structure','INR',"
                + "'2027-01-01','2028-01-01','DRAFT',"
                + "'test','test')");

        try (ResultSet approved =
            statement.executeQuery(
                "SELECT compensation.approve_salary_structure_version('"
                    + TENANT_A
                    + "','"
                    + versionId
                    + "','test','"
                    + Instant.parse("2026-07-22T04:02:00Z")
                    + "')")) {
          assertThat(approved.next()).isTrue();
          assertThat(approved.getLong(1)).isZero();
        }
      }
      connection.rollback();
    }
  }

  @Test
  void invalidLineTargetShapeIsRejected() throws Exception {
    UUID identityId =
        UUID.fromString("22000000-0000-0000-0000-000000000004");
    UUID versionId =
        UUID.fromString("22200000-0000-0000-0000-000000000004");
    UUID lineId =
        UUID.fromString("22300000-0000-0000-0000-000000000004");

    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO compensation.salary_structure("
              + "id,tenant_id,code,created_by,updated_by) VALUES ('"
              + identityId
              + "','"
              + TENANT_A
              + "','INVALID','test','test')");

      statement.execute(
          "INSERT INTO compensation.salary_structure_version("
              + "id,tenant_id,salary_structure_id,version_sequence,"
              + "name,currency,effective_from,effective_to,"
              + "approval_status,created_by,updated_by) VALUES ('"
              + versionId
              + "','"
              + TENANT_A
              + "','"
              + identityId
              + "',1,'Invalid Structure','INR',"
              + "'2027-01-01','2028-01-01','DRAFT',"
              + "'test','test')");

      assertThatThrownBy(
              () ->
                  statement.execute(
                      "INSERT INTO compensation.salary_structure_line("
                          + "id,tenant_id,salary_structure_version_id,"
                          + "component_version_id,sequence_no,"
                          + "target_amount,target_percentage,"
                          + "percentage_base_code,effective_from,"
                          + "effective_to,created_by,updated_by"
                          + ") VALUES ('"
                          + lineId
                          + "','"
                          + TENANT_A
                          + "','"
                          + versionId
                          + "','"
                          + COMPONENT_VERSION_ID
                          + "',1,1000.0000,10.000000,NULL,"
                          + "'2027-01-01','2028-01-01','test','test')"))
          .hasMessageContaining(
              "salary_structure_line_target_shape_ck");
    }
  }

  @Test
  void rowLevelSecurityHidesTenantAFromTenantB()
      throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "SET LOCAL app.tenant_id='" + TENANT_B + "'");

        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) "
                    + "FROM compensation.salary_structure")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) "
                    + "FROM compensation.salary_structure_version")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }

        try (ResultSet result =
            statement.executeQuery(
                "SELECT count(*) "
                    + "FROM compensation.salary_structure_line")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isZero();
        }
      }
      connection.rollback();
    }
  }

  private static void seedLegacyV019Data() throws Exception {
    try (Connection connection = admin();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant("
              + "id,code,name,created_by,updated_by) VALUES ('"
              + TENANT_A
              + "','A','Synthetic Tenant A','test','test')");

      seedOrganisation(statement);

      statement.execute(
          "INSERT INTO compensation.pay_component("
              + "id,tenant_id,code,name,component_type,"
              + "created_by,updated_by) VALUES ('"
              + COMPONENT_ID
              + "','"
              + TENANT_A
              + "','BASIC','Basic Pay','EARNING','test','test')");

      statement.execute(
          "INSERT INTO compensation.pay_component_version("
              + "id,tenant_id,component_id,version_sequence,"
              + "formula_type,formula_expression,fixed_amount,"
              + "rounding_scale,effective_from,effective_to,"
              + "approval_status,approved_at,approved_by,"
              + "created_by,updated_by) VALUES ('"
              + COMPONENT_VERSION_ID
              + "','"
              + TENANT_A
              + "','"
              + COMPONENT_ID
              + "',1,'FIXED',NULL,1000.0000,2,"
              + "'2026-01-01','2028-01-01','APPROVED',"
              + "clock_timestamp(),'test','test','test')");

      statement.execute(
          "INSERT INTO compensation.salary_structure("
              + "id,tenant_id,code,name,currency,"
              + "effective_from,effective_to,created_by,updated_by"
              + ") VALUES ('"
              + LEGACY_STRUCTURE_VERSION_ID
              + "','"
              + TENANT_A
              + "','DEFAULT','Default Structure','INR',"
              + "'2026-01-01','2028-01-01','test','test')");

      statement.execute(
          "INSERT INTO compensation.salary_structure_line("
              + "id,tenant_id,structure_id,component_version_id,"
              + "sequence_no,target_amount,effective_from,effective_to,"
              + "created_by,updated_by) VALUES ('"
              + LEGACY_LINE_ID
              + "','"
              + TENANT_A
              + "','"
              + LEGACY_STRUCTURE_VERSION_ID
              + "','"
              + COMPONENT_VERSION_ID
              + "',1,1000.0000,'2026-01-01','2028-01-01',"
              + "'test','test')");

      statement.execute(
          "INSERT INTO employee_payroll.payroll_relationship("
              + "id,tenant_id,external_employee_id,employee_number,"
              + "legal_entity_id,relationship_start,relationship_end,"
              + "created_by,updated_by) VALUES ('"
              + RELATIONSHIP_ID
              + "','"
              + TENANT_A
              + "','EMP-EXT-1','EMP-1','"
              + LEGAL_VERSION_ID
              + "','2026-01-01','2028-01-01','test','test')");

      statement.execute(
          "INSERT INTO employee_payroll.payroll_assignment("
              + "id,tenant_id,payroll_relationship_id,"
              + "establishment_id,assignment_number,"
              + "assignment_start,assignment_end,"
              + "created_by,updated_by) VALUES ('"
              + ASSIGNMENT_ID
              + "','"
              + TENANT_A
              + "','"
              + RELATIONSHIP_ID
              + "','"
              + ESTABLISHMENT_VERSION_ID
              + "','ASN-1','2026-01-01','2028-01-01',"
              + "'test','test')");

      statement.execute(
          "INSERT INTO employee_payroll.salary_assignment("
              + "id,tenant_id,payroll_assignment_id,"
              + "salary_structure_id,monthly_amount,currency,"
              + "effective_from,effective_to,created_by,updated_by"
              + ") VALUES ('"
              + SALARY_ASSIGNMENT_ID
              + "','"
              + TENANT_A
              + "','"
              + ASSIGNMENT_ID
              + "','"
              + LEGACY_STRUCTURE_VERSION_ID
              + "',50000.0000,'INR','2026-01-01','2028-01-01',"
              + "'test','test')");
    }
  }

  private static void seedOrganisation(Statement statement)
      throws Exception {
    statement.execute(
        "INSERT INTO organisation.legal_entity("
            + "id,tenant_id,code,created_by,updated_by) VALUES ('"
            + LEGAL_ID
            + "','"
            + TENANT_A
            + "','ACME_IN','test','test')");

    statement.execute(
        "INSERT INTO organisation.legal_entity_version("
            + "id,tenant_id,legal_entity_id,version_sequence,"
            + "name,country_code,currency,effective_from,effective_to,"
            + "approval_status,approved_at,approved_by,"
            + "created_by,updated_by) VALUES ('"
            + LEGAL_VERSION_ID
            + "','"
            + TENANT_A
            + "','"
            + LEGAL_ID
            + "',1,'Acme India','IN','INR',"
            + "'2026-01-01','2028-01-01','APPROVED',"
            + "clock_timestamp(),'test','test','test')");

    statement.execute(
        "INSERT INTO organisation.payroll_statutory_unit("
            + "id,tenant_id,code,created_by,updated_by) VALUES ('"
            + PSU_ID
            + "','"
            + TENANT_A
            + "','ACME_PSU','test','test')");

    statement.execute(
        "INSERT INTO organisation.payroll_statutory_unit_version("
            + "id,tenant_id,payroll_statutory_unit_id,"
            + "legal_entity_version_id,version_sequence,name,"
            + "effective_from,effective_to,approval_status,"
            + "approved_at,approved_by,created_by,updated_by"
            + ") VALUES ('"
            + PSU_VERSION_ID
            + "','"
            + TENANT_A
            + "','"
            + PSU_ID
            + "','"
            + LEGAL_VERSION_ID
            + "',1,'Acme PSU','2026-01-01','2028-01-01',"
            + "'APPROVED',clock_timestamp(),"
            + "'test','test','test')");

    statement.execute(
        "INSERT INTO organisation.establishment("
            + "id,tenant_id,code,created_by,updated_by) VALUES ('"
            + ESTABLISHMENT_ID
            + "','"
            + TENANT_A
            + "','BLR','test','test')");

    statement.execute(
        "INSERT INTO organisation.establishment_version("
            + "id,tenant_id,establishment_id,"
            + "payroll_statutory_unit_version_id,"
            + "version_sequence,name,state_code,effective_from,"
            + "effective_to,approval_status,approved_at,approved_by,"
            + "created_by,updated_by) VALUES ('"
            + ESTABLISHMENT_VERSION_ID
            + "','"
            + TENANT_A
            + "','"
            + ESTABLISHMENT_ID
            + "','"
            + PSU_VERSION_ID
            + "',1,'Bengaluru','KA','2026-01-01','2028-01-01',"
            + "'APPROVED',clock_timestamp(),"
            + "'test','test','test')");
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }
}