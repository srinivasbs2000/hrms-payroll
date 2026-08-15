package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ComponentCatalogueFormulaRateControlsMigrationIT {
  private static final String APP_PASSWORD = "synthetic-app-password";
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("payroll")
          .withUsername("postgres")
          .withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
              + "NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute(
          "CREATE ROLE payroll_app LOGIN PASSWORD '" + APP_PASSWORD
              + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES "
              + "('" + TENANT_A + "','A','Synthetic Tenant A','test','test'),"
              + "('" + TENANT_B + "','B','Synthetic Tenant B','test','test')");
    }
  }

  @Test
  void formulaMetadataUsesExactVersionsAndRejectsSelfDependency() throws Exception {
    Component component = seedApprovedComponent("FORMULA_A");
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID metadataId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_formula_metadata("
                + "id,tenant_id,component_id,component_version_id,formula_type,calculation_phase,"
                + "result_contract,canonical_expression,formula_fingerprint,dependency_count,created_by) VALUES ('"
                + metadataId + "','" + TENANT_A + "','" + component.identityId + "','"
                + component.versionId + "','FIXED','INPUT','DECIMAL','FIXED(1000)',"
                + "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',0,'maker')");

        assertThatThrownBy(() -> statement.execute(
            "INSERT INTO compensation.component_formula_dependency("
                + "tenant_id,formula_metadata_id,component_id,component_version_id,"
                + "dependency_component_id,dependency_component_version_id,dependency_code,"
                + "dependency_order,dependency_phase,created_by) VALUES ('" + TENANT_A + "','"
                + metadataId + "','" + component.identityId + "','" + component.versionId + "','"
                + component.identityId + "','" + component.versionId
                + "','FORMULA_A',1,'INPUT','maker')"))
            .isInstanceOf(SQLException.class);
      }
      connection.rollback();
    }
  }

  @Test
  void wageClassificationReferencesApprovedExactStatutoryRuleVersions() throws Exception {
    Component component = seedApprovedComponent("WAGE_REF_A");
    StatutoryRule rule = seedApprovedStatutoryRule();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID referenceId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_statutory_wage_reference("
                + "id,tenant_id,component_id,component_version_id,statutory_rule_id,"
                + "statutory_rule_version_id,created_by) VALUES ('" + referenceId + "','"
                + TENANT_A + "','" + component.identityId + "','" + component.versionId + "','"
                + rule.identityId + "','" + rule.versionId + "','component-maker')");
        try (ResultSet result = statement.executeQuery(
            "SELECT statutory_rule_id,statutory_rule_version_id "
                + "FROM compensation.component_statutory_wage_reference WHERE id='"
                + referenceId + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getObject("statutory_rule_id", UUID.class)).isEqualTo(rule.identityId);
          assertThat(result.getObject("statutory_rule_version_id", UUID.class)).isEqualTo(rule.versionId);
        }
      }
      connection.rollback();
    }
  }

  @Test
  void rateTablesEnforceMakerCheckerOptimisticVersionAndApprovedOverlap() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID tableId = UUID.randomUUID();
        UUID version1 = UUID.randomUUID();
        UUID version2 = UUID.randomUUID();
        UUID version3 = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_rate_table(id,tenant_id,code,name,created_by,updated_by) "
                + "VALUES ('" + tableId + "','" + TENANT_A + "','GENERIC_RATE','Generic rate','maker','maker')");
        insertRateVersion(statement, tableId, version1, 1, "2027-01-01", null, "maker");

        assertThat(approve(statement, "approve_component_rate_table_version", version1, 0, "maker"))
            .isZero();
        assertThat(approve(statement, "approve_component_rate_table_version", version1, 99, "checker"))
            .isZero();
        assertThat(approve(statement, "approve_component_rate_table_version", version1, 0, "checker"))
            .isOne();

        insertRateVersion(statement, tableId, version2, 2, "2027-06-01", null, "maker-2");
        statement.execute("SAVEPOINT before_overlap");
        assertThatThrownBy(() -> approve(
            statement, "approve_component_rate_table_version", version2, 0, "checker-2"))
            .isInstanceOf(SQLException.class);
        statement.execute("ROLLBACK TO SAVEPOINT before_overlap");

        assertThat(endDate(
            statement, "end_date_component_rate_table_version", version1, "2027-06-01", 1,
            "end-checker")).isOne();
        assertThat(approve(
            statement, "approve_component_rate_table_version", version2, 0, "checker-2"))
            .isOne();
        assertThat(endDate(
            statement, "end_date_component_rate_table_version", version2, "2027-12-31", 1,
            "end-checker-2")).isOne();
        insertRateVersion(statement, tableId, version3, 3, "2028-02-01", null, "maker-3");
        assertThat(retire(
            statement, "retire_component_rate_table", tableId, "2028-01-01", 1,
            "retire-checker")).isOne();
        assertThat(approve(
            statement, "approve_component_rate_table_version", version3, 0,
            "checker-3")).isZero();
        try (ResultSet retired = statement.executeQuery(
            "SELECT lifecycle_status,retirement_effective_date FROM compensation.component_rate_table "
                + "WHERE id='" + tableId + "'")) {
          assertThat(retired.next()).isTrue();
          assertThat(retired.getString("lifecycle_status")).isEqualTo("RETIRED");
          assertThat(retired.getDate("retirement_effective_date").toLocalDate())
              .isEqualTo(java.time.LocalDate.of(2028, 1, 1));
        }
      }
      connection.rollback();
    }
  }

  @Test
  void roundingPolicyCarriesFullVersionedEvidence() throws Exception {
    Component component = seedApprovedComponent("ROUND_A");
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID policyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_rounding_policy("
                + "id,tenant_id,component_id,created_by,updated_by) VALUES ('" + policyId + "','"
                + TENANT_A + "','" + component.identityId + "','round-maker','round-maker')");
        statement.execute(
            "INSERT INTO compensation.component_rounding_policy_version("
                + "id,tenant_id,policy_id,version_sequence,rounding_method,rounding_scale,"
                + "rounding_stage,negative_treatment,effective_from,created_by,updated_by) VALUES ('"
                + versionId + "','" + TENANT_A + "','" + policyId
                + "',1,'HALF_EVEN',2,'FINAL','SYMMETRIC','2027-01-01','round-maker','round-maker')");
        assertThat(approve(
            statement, "approve_component_rounding_policy_version", versionId, 0, "round-maker"))
            .isZero();
        assertThat(approve(
            statement, "approve_component_rounding_policy_version", versionId, 0, "round-checker"))
            .isOne();
        try (ResultSet result = statement.executeQuery(
            "SELECT rounding_method,rounding_scale,rounding_stage,negative_treatment "
                + "FROM compensation.component_rounding_policy_version WHERE id='" + versionId + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString("rounding_method")).isEqualTo("HALF_EVEN");
          assertThat(result.getInt("rounding_scale")).isEqualTo(2);
          assertThat(result.getString("rounding_stage")).isEqualTo("FINAL");
          assertThat(result.getString("negative_treatment")).isEqualTo("SYMMETRIC");
        }
      }
      connection.rollback();
    }
  }

  @Test
  void allFiveProrationEventsAreIndependentMakerCheckerPolicies() throws Exception {
    Component component = seedApprovedComponent("PRORATE_A");
    List<String> events = List.of(
        "JOINING", "EXIT", "UNPAID_LEAVE", "TRANSFER", "SALARY_REVISION");
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        int index = 0;
        for (String event : events) {
          UUID policyId = UUID.randomUUID();
          UUID versionId = UUID.randomUUID();
          String maker = "proration-maker-" + index;
          statement.execute(
              "INSERT INTO compensation.component_proration_policy("
                  + "id,tenant_id,component_id,event_type,created_by,updated_by) VALUES ('"
                  + policyId + "','" + TENANT_A + "','" + component.identityId + "','"
                  + event + "','" + maker + "','" + maker + "')");
          statement.execute(
              "INSERT INTO compensation.component_proration_policy_version("
                  + "id,tenant_id,policy_id,version_sequence,proration_method,proration_basis,"
                  + "effective_from,created_by,updated_by) VALUES ('" + versionId + "','"
                  + TENANT_A + "','" + policyId
                  + "',1,'CALENDAR_DAYS','PAY_PERIOD','2027-01-01','" + maker + "','" + maker + "')");
          assertThat(approve(
              statement, "approve_component_proration_policy_version", versionId, 0,
              "proration-checker-" + index)).isOne();
          index++;
        }
        try (ResultSet result = statement.executeQuery(
            "SELECT count(*) FROM compensation.component_proration_policy "
                + "WHERE component_id='" + component.identityId + "' AND lifecycle_status='ACTIVE'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isEqualTo(5);
        }
      }
      connection.rollback();
    }
  }

  @Test
  void typedRateDimensionsRejectNonCanonicalDatabaseValues() throws Exception {
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID tableId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_rate_table(id,tenant_id,code,name,created_by,updated_by) "
                + "VALUES ('" + tableId + "','" + TENANT_A + "','TYPED_RATE','Typed rate','maker','maker')");
        statement.execute(
            "INSERT INTO compensation.component_rate_table_version("
                + "id,tenant_id,rate_table_id,version_sequence,value_type,unit_code,effective_from,created_by,updated_by) VALUES ('"
                + versionId + "','" + TENANT_A + "','" + tableId
                + "',1,'PERCENTAGE','PERCENT','2027-01-01','maker','maker')");
        statement.execute(
            "INSERT INTO compensation.component_rate_dimension("
                + "tenant_id,rate_table_version_id,dimension_sequence,code,name,data_type,created_by) VALUES ('"
                + TENANT_A + "','" + versionId + "',1,'LEVEL','Level','NUMBER','maker')");
        assertThatThrownBy(() -> statement.execute(
            "INSERT INTO compensation.component_rate_cell("
                + "tenant_id,rate_table_version_id,cell_sequence,dimension_values,rate_value,created_by) VALUES ('"
                + TENANT_A + "','" + versionId
                + "',1,'{\"LEVEL\":\"01\"}'::jsonb,12.5,'maker')"))
            .isInstanceOf(SQLException.class);
      }
      connection.rollback();
    }
  }

  @Test
  void dependencyVersionCannotBeShortenedBelowDependantFormulaRange() throws Exception {
    Component dependency = seedApprovedComponent("DEP_TARGET");
    Component source = seedApprovedComponent("DEP_SOURCE");
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        UUID metadataId = UUID.randomUUID();
        statement.execute(
            "INSERT INTO compensation.component_formula_metadata("
                + "id,tenant_id,component_id,component_version_id,formula_type,calculation_phase,"
                + "result_contract,canonical_expression,formula_fingerprint,dependency_count,created_by) VALUES ('"
                + metadataId + "','" + TENANT_A + "','" + source.identityId + "','" + source.versionId
                + "','PERCENTAGE_OF_COMPONENT','PRE_TAX','DECIMAL','DEP_TARGET*0.1',"
                + "'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',1,'maker')");
        statement.execute(
            "INSERT INTO compensation.component_formula_dependency("
                + "tenant_id,formula_metadata_id,component_id,component_version_id,dependency_component_id,"
                + "dependency_component_version_id,dependency_code,dependency_order,dependency_phase,created_by) VALUES ('"
                + TENANT_A + "','" + metadataId + "','" + source.identityId + "','" + source.versionId
                + "','" + dependency.identityId + "','" + dependency.versionId
                + "','DEP_TARGET',1,'INPUT','maker')");
        assertThatThrownBy(() -> endDate(
            statement, "end_date_pay_component_version", dependency.versionId,
            "2028-01-01", 1, "end-checker"))
            .isInstanceOf(SQLException.class);
      }
      connection.rollback();
    }
  }

  @Test
  void newControlTablesAreForcedRlsTenantIsolatedAndNotDirectlyMutable() throws Exception {
    UUID tenantARateTable = UUID.randomUUID();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            "INSERT INTO compensation.component_rate_table(id,tenant_id,code,name,created_by,updated_by) "
                + "VALUES ('" + tenantARateTable + "','" + TENANT_A
                + "','RLS_RATE','RLS rate','rls-maker','rls-maker')");
      }
      connection.commit();
    }

    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_B + "'");
        assertThat(count(statement, "compensation.component_rate_table")).isZero();
        assertThat(count(statement, "compensation.component_rounding_policy")).isZero();
        assertThat(count(statement, "compensation.component_proration_policy")).isZero();
        try (ResultSet privilege = statement.executeQuery(
            "SELECT has_table_privilege(current_user,'compensation.component_rate_table_version','UPDATE')")) {
          assertThat(privilege.next()).isTrue();
          assertThat(privilege.getBoolean(1)).isFalse();
        }
      }
      connection.rollback();
    }

    try (Connection connection = admin(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                + "WHERE n.nspname='compensation' AND c.relname IN ("
                + "'component_formula_metadata','component_formula_dependency','component_statutory_wage_reference',"
                + "'component_rate_table',"
                + "'component_rate_table_version','component_rate_dimension','component_rate_cell',"
                + "'component_rounding_policy','component_rounding_policy_version',"
                + "'component_proration_policy','component_proration_policy_version') "
                + "AND c.relrowsecurity AND c.relforcerowsecurity")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isEqualTo(11);
    }
  }

  private static Component seedApprovedComponent(String code) throws Exception {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        statement.execute(
            "INSERT INTO compensation.pay_component(id,tenant_id,code,name,component_type,"
                + "created_by,updated_by) VALUES ('" + identityId + "','" + TENANT_A + "','"
                + code + "','Component','EARNING','component-maker','component-maker')");
        statement.execute(
            "INSERT INTO compensation.pay_component_version("
                + "id,tenant_id,component_id,version_sequence,formula_type,fixed_amount,rounding_scale,"
                + "catalogue_schema_version,component_category,component_subcategory,cash_impact,payee_type,"
                + "payment_channel,settlement_timing,payslip_visibility,zero_value_visibility,negative_value_policy,"
                + "frequency,value_nature,amount_representation,tax_treatment,payroll_timing,effective_from,"
                + "approval_status,created_by,updated_by) VALUES ('" + versionId + "','" + TENANT_A + "','"
                + identityId + "',1,'FIXED',1000,2,1,'CASH_EARNING','BASIC_PAY','INCREASE','EMPLOYEE',"
                + "'PAYROLL_BANK','CURRENT_PERIOD','SHOW','SUPPRESS','PROHIBIT','MONTHLY','FIXED',"
                + "'MONTHLY_AMOUNT','DELEGATED','REGULAR','2027-01-01','DRAFT','component-maker','component-maker')");
        try (ResultSet result = statement.executeQuery(
            "SELECT compensation.approve_pay_component_version('" + TENANT_A + "','" + versionId
                + "','component-checker','2026-08-16T00:00:00Z')")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isOne();
        }
      }
      connection.commit();
    }
    return new Component(identityId, versionId);
  }

  private static StatutoryRule seedApprovedStatutoryRule() throws Exception {
    UUID identityId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID portionId = UUID.randomUUID();
    try (Connection connection = app()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL app.tenant_id='" + TENANT_A + "'");
        String suffix = identityId.toString().substring(0, 8).toUpperCase();
        statement.execute(
            "INSERT INTO statutory.statutory_rule("
                + "id,tenant_id,jurisdiction_code,authority_code,code,name,rule_category,"
                + "created_by,updated_by) VALUES ('" + identityId + "','" + TENANT_A
                + "','TEST_JURISDICTION','TEST_AUTH','WAGE_" + suffix
                + "','Synthetic wage classification rule','OTHER','rule-maker','rule-maker')");
        statement.execute(
            "INSERT INTO statutory.statutory_rule_version("
                + "id,tenant_id,statutory_rule_id,version_sequence,effective_from,currency,"
                + "created_by,updated_by) VALUES ('" + versionId + "','" + TENANT_A + "','"
                + identityId + "',1,'2027-01-01','USD','rule-maker','rule-maker')");
        statement.execute(
            "INSERT INTO statutory.statutory_rule_portion("
                + "id,tenant_id,statutory_rule_version_id,liable_party,sequence_no,"
                + "calculation_method,fixed_amount,created_by,updated_by) VALUES ('" + portionId
                + "','" + TENANT_A + "','" + versionId
                + "','EMPLOYEE',1,'FIXED',1,'rule-maker','rule-maker')");
        try (ResultSet result = statement.executeQuery(
            "SELECT statutory.approve_statutory_rule_version('" + TENANT_A + "','" + versionId
                + "','rule-checker','2026-08-16T00:00:00Z')")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isOne();
        }
      }
      connection.commit();
    }
    return new StatutoryRule(identityId, versionId);
  }

  private static void insertRateVersion(
      Statement statement,
      UUID tableId,
      UUID versionId,
      int sequence,
      String from,
      String to,
      String maker) throws Exception {
    String toSql = to == null ? "NULL" : "'" + to + "'";
    statement.execute(
        "INSERT INTO compensation.component_rate_table_version("
            + "id,tenant_id,rate_table_id,version_sequence,effective_from,effective_to,created_by,updated_by) VALUES ('"
            + versionId + "','" + TENANT_A + "','" + tableId + "'," + sequence + ",'" + from + "',"
            + toSql + ",'" + maker + "','" + maker + "')");
    statement.execute(
        "INSERT INTO compensation.component_rate_dimension("
            + "tenant_id,rate_table_version_id,dimension_sequence,code,name,data_type,created_by) VALUES ('"
            + TENANT_A + "','" + versionId + "',1,'GRADE','Grade','TEXT','" + maker + "')");
    statement.execute(
        "INSERT INTO compensation.component_rate_cell("
            + "tenant_id,rate_table_version_id,cell_sequence,dimension_values,rate_value,created_by) VALUES ('"
            + TENANT_A + "','" + versionId + "',1,'{\"GRADE\":\"A\"}'::jsonb,12.3456789012,'"
            + maker + "')");
  }

  private static long approve(
      Statement statement, String function, UUID versionId, long expected, String actor) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "SELECT compensation." + function + "('" + TENANT_A + "','" + versionId + "',"
            + expected + ",'" + actor + "','2026-08-16T00:05:00Z')")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long endDate(
      Statement statement,
      String function,
      UUID versionId,
      String effectiveTo,
      long expected,
      String actor) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "SELECT compensation." + function + "('" + TENANT_A + "','" + versionId + "','"
            + effectiveTo + "'," + expected + ",'" + actor + "','2026-08-16T00:06:00Z')")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long retire(
      Statement statement,
      String function,
      UUID identityId,
      String effectiveDate,
      long expected,
      String actor) throws Exception {
    try (ResultSet result = statement.executeQuery(
        "SELECT compensation." + function + "('" + TENANT_A + "','" + identityId + "','"
            + effectiveDate + "'," + expected + ",'test retirement','" + actor
            + "','2026-08-16T00:07:00Z')")) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static long count(Statement statement, String table) throws Exception {
    try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }

  private static Connection app() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payroll_app", APP_PASSWORD);
  }

  private record Component(UUID identityId, UUID versionId) {}

  private record StatutoryRule(UUID identityId, UUID versionId) {}
}
