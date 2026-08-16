package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FlexBenefitPlanMigrationIT {
  private static final String APP_PASSWORD="synthetic-app-password";
  private static final String MIGRATOR_PASSWORD="synthetic-migrator-password";

  @Container
  static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migrate() throws Exception {
    try(Connection connection=admin();Statement statement=connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '"+MIGRATOR_PASSWORD+"' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app LOGIN PASSWORD '"+APP_PASSWORD+"' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),"payroll_migrator",MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load().migrate();
  }

  @Test
  void flexBenefitTablesAreTenantIsolatedAndForcedRls() throws Exception {
    try(Connection c=admin();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
        select relname,relrowsecurity,relforcerowsecurity
          from pg_class
         where relnamespace='compensation'::regnamespace
           and relname in ('flex_benefit_plan','flex_benefit_plan_version','flex_benefit_option')
         order by relname
        """)) {
      int count=0;
      while(r.next()) {count++;assertThat(r.getBoolean("relrowsecurity")).isTrue();assertThat(r.getBoolean("relforcerowsecurity")).isTrue();}
      assertThat(count).isEqualTo(3);
    }
  }

  @Test
  void appUsesControlledApprovalWithoutDirectMutation() throws Exception {
    try(Connection c=admin();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
        select
          has_function_privilege('payroll_app','compensation.lock_flex_benefit_plan(uuid,uuid)','EXECUTE'),
          has_function_privilege('payroll_app','compensation.approve_flex_benefit_plan_version(uuid,uuid,varchar,timestamptz)','EXECUTE'),
          has_table_privilege('payroll_app','compensation.flex_benefit_plan','UPDATE'),
          has_table_privilege('payroll_app','compensation.flex_benefit_plan_version','UPDATE'),
          has_table_privilege('payroll_app','compensation.flex_benefit_option','DELETE')
        """)) {
      assertThat(r.next()).isTrue();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();
      assertThat(r.getBoolean(3)).isFalse();assertThat(r.getBoolean(4)).isFalse();assertThat(r.getBoolean(5)).isFalse();
    }
  }

  @Test
  void runtimeDefaultsAndApprovedParentOptionBoundaryAreDatabaseEnforced() throws Exception {
    try(Connection c=admin();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
        select p1.prosrc,p2.prosrc,
               exists(select 1 from pg_trigger where tgrelid='compensation.flex_benefit_plan'::regclass and tgname='flex_benefit_plan_runtime_default' and not tgisinternal),
               exists(select 1 from pg_trigger where tgrelid='compensation.flex_benefit_plan_version'::regclass and tgname='flex_benefit_plan_version_runtime_default' and not tgisinternal),
               exists(select 1 from pg_trigger where tgrelid='compensation.flex_benefit_option'::regclass and tgname='flex_benefit_option_parent_draft' and not tgisinternal)
          from pg_proc p1,pg_proc p2
         where p1.oid='compensation.require_flex_benefit_runtime_defaults()'::regprocedure
           and p2.oid='compensation.assert_flex_benefit_option_parent_draft()'::regprocedure
        """)) {
      assertThat(r.next()).isTrue();
      assertThat(r.getString(1)).contains("PENDING_APPROVAL").contains("DRAFT");
      assertThat(r.getString(2)).contains("parent_status<>'DRAFT'");
      assertThat(r.getBoolean(3)).isTrue();assertThat(r.getBoolean(4)).isTrue();assertThat(r.getBoolean(5)).isTrue();
    }
  }

  @Test
  void approvalFunctionPinsBenefitPlanEligibilityAndExactOptionComponents() throws Exception {
    try(Connection c=admin();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
        select prosrc from pg_proc
         where oid='compensation.approve_flex_benefit_plan_version(uuid,uuid,varchar,timestamptz)'::regprocedure
        """)) {
      assertThat(r.next()).isTrue();String definition=r.getString(1);
      assertThat(definition).contains("spv.plan_type='BENEFIT'").contains("spv.approval_status='APPROVED'")
          .contains("spl.component_version_id=o.component_version_id")
          .contains("erv.approval_status<>'APPROVED'")
          .contains("sum(o.minimum_annual_amount)")
          .contains("sum(o.default_annual_amount)")
          .contains("spl.effective_from>version.effective_from")
          .contains("version.created_by<>p_actor");
    }
  }

  private static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(),"postgres","postgres");
  }
}
