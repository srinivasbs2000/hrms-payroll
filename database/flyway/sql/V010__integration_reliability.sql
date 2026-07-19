CREATE TABLE integration.outbox_event (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, aggregate_type varchar(120) NOT NULL, aggregate_id uuid NOT NULL,
 event_type varchar(160) NOT NULL, event_version integer NOT NULL DEFAULT 1, occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 correlation_id uuid NOT NULL, causation_id uuid, payload jsonb NOT NULL, headers jsonb NOT NULL DEFAULT '{}'::jsonb,
 status varchar(20) NOT NULL DEFAULT 'PENDING', attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz,
 UNIQUE(tenant_id,id), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE INDEX outbox_dispatch_ix ON integration.outbox_event(status,next_attempt_at,occurred_at);
CREATE TABLE integration.inbox_message (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, message_id uuid NOT NULL, consumer_name varchar(120) NOT NULL,
 received_at timestamptz NOT NULL DEFAULT clock_timestamp(), processed_at timestamptz, payload_hash char(64) NOT NULL, status varchar(20) NOT NULL DEFAULT 'RECEIVED',
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,message_id,consumer_name), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE TABLE integration.dead_letter (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, source_event_id uuid NOT NULL, failure_code varchar(80) NOT NULL,
 failure_detail varchar(2000), failed_at timestamptz NOT NULL DEFAULT clock_timestamp(), replay_status varchar(20) NOT NULL DEFAULT 'OPEN',
 UNIQUE(tenant_id,id), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
