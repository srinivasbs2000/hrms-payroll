package com.acme.hrms.payroll.integrations.internal.infrastructure;

import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.platform.TenantContext;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class JdbcIdempotencyStore implements IdempotencyStore {
  private final JdbcTemplate jdbc;
  private final CanonicalJsonHasher canonical;

  JdbcIdempotencyStore(JdbcTemplate jdbc, CanonicalJsonHasher canonical) {
    this.jdbc = jdbc;
    this.canonical = canonical;
  }

  @Override
  public Optional<SavedResponse> find(String operation, String key) {
    return jdbc.query("select request_hash,response_status,response_body::text from integration.idempotency_record "
            + "where tenant_id=? and operation=? and idempotency_key=? and expires_at>clock_timestamp()",
        rs -> rs.next() ? Optional.of(new SavedResponse(rs.getString(1),
            (Integer) rs.getObject(2), rs.getString(3))) : Optional.empty(),
        TenantContext.require(), operation, key);
  }

  @Override
  public void reserve(String operation, String key, String requestHash, Instant expiresAt) {
    try {
      jdbc.update("insert into integration.idempotency_record(tenant_id,operation,idempotency_key,request_hash,expires_at) values (?,?,?,?,?)",
          TenantContext.require(), operation, key, requestHash, Timestamp.from(expiresAt));
    } catch (DuplicateKeyException exception) {
      throw new IllegalStateException("Idempotency key is already in use", exception);
    }
  }

  @Override
  public void complete(String operation, String key, int status, Object response) {
    jdbc.update("update integration.idempotency_record set response_status=?,response_body=?::jsonb where tenant_id=? and operation=? and idempotency_key=?",
        status, canonical.json(response), TenantContext.require(), operation, key);
  }
}
