package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimeTimeAuthorityApiIT extends JrfApiITSupport {
  private static final TimeZone ORIGINAL_TIME_ZONE = TimeZone.getDefault();
  private static final ZoneId NON_UTC_HOST_ZONE = ZoneId.of("Asia/Kolkata");
  private static final Instant MIDNIGHT_BOUNDARY_PROBE =
      Instant.parse("2026-08-10T20:56:18Z");

  static {
    TimeZone.setDefault(TimeZone.getTimeZone(NON_UTC_HOST_ZONE));
  }

  @Autowired DataSource dataSource;
  @Autowired ApplicationContext applicationContext;

  @DynamicPropertySource
  static void forceNonUtcJvm(DynamicPropertyRegistry registry) {
    TimeZone.setDefault(TimeZone.getTimeZone(NON_UTC_HOST_ZONE));
    registry.add("runtime.time-authority.test-host-zone", NON_UTC_HOST_ZONE::getId);
  }

  @AfterAll
  static void restoreJvmTimeZone() {
    TimeZone.setDefault(ORIGINAL_TIME_ZONE);
  }

  @Test
  void applicationClockAndDatabaseSessionsStayUtcAcrossHostLocalMidnight()
      throws Exception {
    assertThat(ZoneId.systemDefault()).isEqualTo(NON_UTC_HOST_ZONE);

    Clock applicationClock = applicationContext.getBean(Clock.class);
    assertThat(applicationClock.getZone()).isEqualTo(ZoneOffset.UTC);

    LocalDate applicationDate =
        LocalDate.ofInstant(MIDNIGHT_BOUNDARY_PROBE, applicationClock.getZone());
    LocalDate hostDate =
        LocalDate.ofInstant(MIDNIGHT_BOUNDARY_PROBE, ZoneId.systemDefault());
    assertThat(applicationDate).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(hostDate).isEqualTo(LocalDate.of(2026, 8, 11));
    assertThat(applicationDate).isNotEqualTo(hostDate);

    try (Connection first = dataSource.getConnection();
        Connection second = dataSource.getConnection()) {
      assertUtcSession(first, applicationDate);
      assertUtcSession(second, applicationDate);
    }
  }

  private static void assertUtcSession(Connection connection, LocalDate expectedUtcDate)
      throws Exception {
    try (PreparedStatement statement =
            connection.prepareStatement(
                "select current_setting('TimeZone'), cast(cast(? as timestamptz) as date)")) {
      statement.setString(1, MIDNIGHT_BOUNDARY_PROBE.toString());
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("UTC");
        assertThat(result.getObject(2, LocalDate.class)).isEqualTo(expectedUtcDate);
        assertThat(result.next()).isFalse();
      }
    }
  }
}
