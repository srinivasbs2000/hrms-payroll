package com.acme.hrms.payroll.platform;

import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class TenantTransactionExecutor {
  private final JdbcTemplate jdbc;
  private final PlatformTransactionManager transactionManager;

  public TenantTransactionExecutor(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactionManager = transactionManager;
  }

  public <T> T read(Supplier<T> work) {
    return execute(work, true);
  }

  public <T> T write(Supplier<T> work) {
    return execute(work, false);
  }

  private <T> T execute(Supplier<T> work, boolean readOnly) {
    var transactions = new TransactionTemplate(transactionManager);
    transactions.setReadOnly(readOnly);
    return transactions.execute(status -> {
      jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class,
          TenantContext.require().toString());
      return work.get();
    });
  }
}
