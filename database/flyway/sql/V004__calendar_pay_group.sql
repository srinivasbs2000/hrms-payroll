CREATE TABLE organisation.payroll_calendar (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, code varchar(40) NOT NULL, name varchar(160) NOT NULL,
 frequency varchar(20) NOT NULL CHECK(frequency='MONTHLY'), timezone varchar(80) NOT NULL DEFAULT 'Asia/Kolkata', created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,code), FOREIGN KEY(tenant_id) REFERENCES platform.tenant(id));
CREATE TABLE organisation.pay_period (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, calendar_id uuid NOT NULL, period_code varchar(20) NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, payment_date date NOT NULL, status varchar(20) NOT NULL DEFAULT 'OPEN', created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,calendar_id,period_code), CHECK(period_end>=period_start),
 FOREIGN KEY(tenant_id,calendar_id) REFERENCES organisation.payroll_calendar(tenant_id,id));
CREATE TABLE organisation.pay_group (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, statutory_unit_id uuid NOT NULL, calendar_id uuid NOT NULL,
 code varchar(40) NOT NULL, name varchar(160) NOT NULL, currency platform.currency_code NOT NULL DEFAULT 'INR', proration_method varchar(40) NOT NULL DEFAULT 'CALENDAR_DAYS',
 effective_from date NOT NULL, effective_to date, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), created_by varchar(160) NOT NULL, updated_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_by varchar(160) NOT NULL, version_no bigint NOT NULL DEFAULT 0, UNIQUE(tenant_id,id), UNIQUE(tenant_id,code),
 CHECK(effective_to IS NULL OR effective_to>effective_from),
 FOREIGN KEY(tenant_id,statutory_unit_id) REFERENCES organisation.payroll_statutory_unit(tenant_id,id),
 FOREIGN KEY(tenant_id,calendar_id) REFERENCES organisation.payroll_calendar(tenant_id,id));
