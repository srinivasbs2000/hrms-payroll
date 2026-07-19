CREATE TABLE platform.tenant (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(40) NOT NULL UNIQUE,
  name varchar(200) NOT NULL, status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0
);
CREATE TABLE security.principal (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
  issuer varchar(500) NOT NULL, subject varchar(255) NOT NULL, display_name varchar(200), active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,issuer,subject),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);
CREATE TABLE security.role (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, code varchar(80) NOT NULL, name varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);
CREATE TABLE security.principal_role (
  tenant_id uuid NOT NULL, principal_id uuid NOT NULL, role_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL,
  PRIMARY KEY(tenant_id,principal_id,role_id),
  FOREIGN KEY(tenant_id,principal_id) REFERENCES security.principal(tenant_id,id),
  FOREIGN KEY(tenant_id,role_id) REFERENCES security.role(tenant_id,id)
);
CREATE TABLE audit.audit_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  actor varchar(160) NOT NULL, action varchar(120) NOT NULL, object_type varchar(120) NOT NULL, object_id uuid,
  correlation_id uuid NOT NULL, before_state jsonb, after_state jsonb, metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(tenant_id,id), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id)
);
CREATE INDEX audit_event_lookup_ix ON audit.audit_event(tenant_id,object_type,object_id,occurred_at DESC);
