CREATE TABLE organisation.legal_entity (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, code varchar(40) NOT NULL, name varchar(200) NOT NULL,
 country_code char(2) NOT NULL DEFAULT 'IN', currency platform.currency_code NOT NULL DEFAULT 'INR', effective_from date NOT NULL,
 effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE TABLE organisation.payroll_statutory_unit (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, legal_entity_id uuid NOT NULL, code varchar(40) NOT NULL, name varchar(200) NOT NULL,
 effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,legal_entity_id) REFERENCES organisation.legal_entity(tenant_id,id));
CREATE TABLE organisation.establishment (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, statutory_unit_id uuid NOT NULL, code varchar(40) NOT NULL, name varchar(200) NOT NULL,
 state_code varchar(3) NOT NULL, effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,code),
 CHECK(effective_to IS NULL OR effective_to>effective_from), FOREIGN KEY(tenant_id,statutory_unit_id) REFERENCES organisation.payroll_statutory_unit(tenant_id,id));
