CREATE TABLE compensation.pay_component (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, code platform.component_code NOT NULL, name varchar(160) NOT NULL,
 component_type varchar(20) NOT NULL CHECK(component_type IN ('EARNING','DEDUCTION','INFORMATION')), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE TABLE compensation.pay_component_version (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, component_id uuid NOT NULL, version_no_business integer NOT NULL,
 formula_type varchar(30) NOT NULL, formula_expression varchar(1000), fixed_amount numeric(19,4), rounding_scale integer NOT NULL DEFAULT 2,
 effective_from date NOT NULL, effective_to date, status varchar(20) NOT NULL DEFAULT 'APPROVED', created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,component_id,version_no_business), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,component_id) REFERENCES compensation.pay_component(tenant_id,id));
CREATE TABLE compensation.salary_structure (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, code varchar(40) NOT NULL, name varchar(160) NOT NULL, currency platform.currency_code NOT NULL DEFAULT 'INR',
 effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE TABLE compensation.salary_structure_line (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, structure_id uuid NOT NULL, component_version_id uuid NOT NULL,
 sequence_no integer NOT NULL, target_amount numeric(19,4), target_percentage numeric(9,6), percentage_base_code platform.component_code,
 effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,structure_id,sequence_no),
 CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,structure_id) REFERENCES compensation.salary_structure(tenant_id,id),
 FOREIGN KEY(tenant_id,component_version_id) REFERENCES compensation.pay_component_version(tenant_id,id));
