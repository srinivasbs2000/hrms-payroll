package com.acme.hrms.payroll.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class OutboxInboxReliabilityIT {
  private static final String MIGRATOR_PASSWORD = "synthetic-migrator-password";
  private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID AGGREGATE = UUID.fromString("10000000-0000-0000-0000-00000000000a");
  private static final UUID EVENT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
  private static final UUID CORRELATION = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID CAUSATION = UUID.fromString("ca000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("payroll").withUsername("postgres").withPassword("postgres");

  @BeforeAll
  static void migrateFromZero() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_migrator LOGIN PASSWORD '" + MIGRATOR_PASSWORD
          + "' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("CREATE ROLE payroll_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
      statement.execute("GRANT payroll_owner TO payroll_migrator");
      statement.execute("ALTER ROLE payroll_migrator SET ROLE payroll_owner");
      statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner");
      statement.execute("GRANT CREATE ON DATABASE payroll TO payroll_owner");
    }
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), "payroll_migrator", MIGRATOR_PASSWORD)
        .locations("classpath:db/migration").load().migrate();
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE integration.consumer_effect(tenant_id uuid NOT NULL, event_id uuid NOT NULL, value varchar(80) NOT NULL, PRIMARY KEY(tenant_id,event_id))");
    }
  }

  @BeforeEach
  void reset() throws Exception {
    try (Connection connection = admin(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE platform.tenant CASCADE");
      statement.execute("TRUNCATE integration.consumer_effect");
      statement.execute("INSERT INTO platform.tenant(id,code,name,created_by,updated_by) VALUES ('" + TENANT
          + "','A','Synthetic Tenant A','test','test')");
    }
  }

  @Test
  void aggregateAndOutboxCommitAtomicallyWithStableEnvelopeIdentity() throws Exception {
    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      insertAggregateAndEvent(connection);
      connection.rollback();
    }
    assertThat(count("SELECT count(*) FROM organisation.legal_entity")).isZero();
    assertThat(count("SELECT count(*) FROM integration.outbox_event")).isZero();

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      insertAggregateAndEvent(connection);
      connection.commit();
    }
    assertThat(count("SELECT count(*) FROM organisation.legal_entity")).isOne();
    try (Connection connection = admin(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("SELECT id,tenant_id,aggregate_id,aggregate_version,correlation_id,causation_id,payload_hash FROM integration.outbox_event")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject("id", UUID.class)).isEqualTo(EVENT);
      assertThat(result.getObject("tenant_id", UUID.class)).isEqualTo(TENANT);
      assertThat(result.getObject("aggregate_id", UUID.class)).isEqualTo(AGGREGATE);
      assertThat(result.getLong("aggregate_version")).isOne();
      assertThat(result.getObject("correlation_id", UUID.class)).isEqualTo(CORRELATION);
      assertThat(result.getObject("causation_id", UUID.class)).isEqualTo(CAUSATION);
      assertThat(result.getString("payload_hash")).hasSize(64);
    }
  }

  @Test
  void consumerFailureRollsBackInboxAndRetriesWithoutDuplicateEffects() throws Exception {
    insertCommittedEvent();
    assertThatThrownBy(() -> consume(EVENT, true)).isInstanceOf(IllegalStateException.class);
    assertThat(count("SELECT count(*) FROM integration.inbox_message")).isZero();
    assertThat(count("SELECT count(*) FROM integration.consumer_effect")).isZero();

    assertThat(consume(EVENT, false)).isTrue();
    assertThat(consume(EVENT, false)).isFalse();
    assertThat(count("SELECT count(*) FROM integration.inbox_message WHERE status='PROCESSED'")).isOne();
    assertThat(count("SELECT count(*) FROM integration.consumer_effect")).isOne();
  }

  @Test
  void publishBeforeAckIsRetriedAndDuplicateDeliveryHasOneEffect() throws Exception {
    insertCommittedEvent();
    List<UUID> brokerDeliveries = new ArrayList<>();

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      UUID claimed = claimPending(connection);
      brokerDeliveries.add(claimed);
      connection.rollback(); // crash after publish, before acknowledgement
    }
    assertThat(count("SELECT count(*) FROM integration.outbox_event WHERE status='PENDING'")).isOne();

    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      UUID claimed = claimPending(connection);
      brokerDeliveries.add(claimed);
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE integration.outbox_event SET status='PUBLISHED',published_at=clock_timestamp() WHERE id='" + claimed + "'");
      }
      connection.commit();
    }
    assertThat(brokerDeliveries).containsExactly(EVENT, EVENT);
    brokerDeliveries.forEach(delivery -> {
      try {
        consume(delivery, false);
      } catch (Exception exception) {
        throw new AssertionError(exception);
      }
    });
    assertThat(count("SELECT count(*) FROM integration.consumer_effect")).isOne();
  }

  private static void insertAggregateAndEvent(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO organisation.legal_entity(id,tenant_id,code,created_by,updated_by) VALUES ('"
          + AGGREGATE + "','" + TENANT + "','REL_GATE','test','test')");
      statement.execute("INSERT INTO integration.outbox_event(id,tenant_id,aggregate_type,aggregate_id,aggregate_version,event_type,event_version,correlation_id,causation_id,payload,partition_key,payload_hash) VALUES ('"
          + EVENT + "','" + TENANT + "','LEGAL_ENTITY','" + AGGREGATE + "',1,'ReliabilityGateProved',1,'"
          + CORRELATION + "','" + CAUSATION + "','{\"value\":\"synthetic\"}','" + TENANT
          + ":LEGAL_ENTITY:" + AGGREGATE + "',repeat('a',64))");
    }
  }

  private static void insertCommittedEvent() throws Exception {
    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      insertAggregateAndEvent(connection);
      connection.commit();
    }
  }

  private static UUID claimPending(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
        "UPDATE integration.outbox_event SET status='CLAIMED',attempts=attempts+1,claimed_at=clock_timestamp(),claimed_by='test-dispatcher' WHERE id=(SELECT id FROM integration.outbox_event WHERE status='PENDING' FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING id")) {
      assertThat(result.next()).isTrue();
      return result.getObject(1, UUID.class);
    }
  }

  private static boolean consume(UUID eventId, boolean fail) throws Exception {
    try (Connection connection = admin()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        int inserted = statement.executeUpdate("INSERT INTO integration.inbox_message(tenant_id,message_id,consumer_name,payload_hash,status) VALUES ('"
            + TENANT + "','" + eventId + "','reliability-test',repeat('a',64),'PROCESSING') ON CONFLICT (tenant_id,message_id,consumer_name) DO NOTHING");
        if (inserted == 0) {
          connection.rollback();
          return false;
        }
        statement.execute("INSERT INTO integration.consumer_effect(tenant_id,event_id,value) VALUES ('" + TENANT + "','" + eventId + "','applied')");
        if (fail) {
          throw new IllegalStateException("synthetic consumer failure");
        }
        statement.executeUpdate("UPDATE integration.inbox_message SET status='PROCESSED',processed_at=clock_timestamp() WHERE tenant_id='"
            + TENANT + "' AND message_id='" + eventId + "' AND consumer_name='reliability-test'");
        connection.commit();
        return true;
      } catch (RuntimeException | SQLException exception) {
        connection.rollback();
        throw exception;
      }
    }
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = admin(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static Connection admin() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres", "postgres");
  }
}
