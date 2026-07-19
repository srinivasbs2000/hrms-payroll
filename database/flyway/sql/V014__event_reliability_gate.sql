ALTER TABLE integration.outbox_event
  ADD COLUMN aggregate_version bigint NOT NULL DEFAULT 1,
  ADD COLUMN partition_key varchar(200),
  ADD COLUMN payload_hash char(64),
  ADD COLUMN published_at timestamptz,
  ADD COLUMN claimed_at timestamptz,
  ADD COLUMN claimed_by varchar(120),
  ADD COLUMN last_error varchar(2000),
  ADD CONSTRAINT outbox_event_attempts_ck CHECK (attempts >= 0),
  ADD CONSTRAINT outbox_event_status_ck CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'DEAD'));

UPDATE integration.outbox_event
SET partition_key = tenant_id::text || ':' || aggregate_type || ':' || aggregate_id::text,
    payload_hash = encode(digest(convert_to(payload::text, 'UTF8'), 'sha256'), 'hex')
WHERE partition_key IS NULL OR payload_hash IS NULL;

ALTER TABLE integration.outbox_event
  ALTER COLUMN partition_key SET NOT NULL,
  ALTER COLUMN payload_hash SET NOT NULL;

CREATE UNIQUE INDEX outbox_aggregate_version_uk
  ON integration.outbox_event(tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type);

ALTER TABLE integration.inbox_message
  ADD COLUMN attempts integer NOT NULL DEFAULT 0,
  ADD COLUMN last_error varchar(2000),
  ADD CONSTRAINT inbox_message_attempts_ck CHECK (attempts >= 0),
  ADD CONSTRAINT inbox_message_status_ck CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED'));

ALTER TABLE integration.dead_letter
  ADD CONSTRAINT dead_letter_source_event_fk
  FOREIGN KEY (tenant_id, source_event_id)
  REFERENCES integration.outbox_event(tenant_id, id);

CREATE TABLE integration.idempotency_record (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  operation varchar(160) NOT NULL,
  idempotency_key varchar(200) NOT NULL,
  request_hash char(64) NOT NULL,
  response_status integer,
  response_body jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  expires_at timestamptz NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, operation, idempotency_key),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CHECK (expires_at > created_at)
);

ALTER TABLE integration.idempotency_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration.idempotency_record FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON integration.idempotency_record
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.idempotency_record TO payroll_app;
REVOKE CREATE ON SCHEMA integration FROM payroll_app;
