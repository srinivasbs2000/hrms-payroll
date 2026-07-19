package com.acme.hrms.payroll.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class TenantTransactionExecutorTest {
  @AfterEach void clear() { TenantContext.clear(); }

  @Test
  void installsTransactionLocalTenantBeforeRepositoryWork() {
    UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    TenantContext.set(tenant);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var executor = new TenantTransactionExecutor(jdbc, new NoOpTransactionManager());

    String result = executor.write(() -> {
      verify(jdbc).queryForObject(eq("select set_config('app.tenant_id', ?, true)"),
          eq(String.class), eq(tenant.toString()));
      return "repository-result";
    });

    assertThat(result).isEqualTo("repository-result");
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
