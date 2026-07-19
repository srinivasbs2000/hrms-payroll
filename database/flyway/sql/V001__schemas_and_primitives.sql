CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA platform AUTHORIZATION payroll_owner;
CREATE SCHEMA security AUTHORIZATION payroll_owner;
CREATE SCHEMA audit AUTHORIZATION payroll_owner;
CREATE SCHEMA organisation AUTHORIZATION payroll_owner;
CREATE SCHEMA compensation AUTHORIZATION payroll_owner;
CREATE SCHEMA employee_payroll AUTHORIZATION payroll_owner;
CREATE SCHEMA payroll_ops AUTHORIZATION payroll_owner;
CREATE SCHEMA payroll_calc AUTHORIZATION payroll_owner;
CREATE SCHEMA documents AUTHORIZATION payroll_owner;
CREATE SCHEMA integration AUTHORIZATION payroll_owner;

CREATE DOMAIN platform.currency_code AS char(3) CHECK (VALUE ~ '^[A-Z]{3}$');
CREATE DOMAIN platform.component_code AS varchar(40) CHECK (VALUE ~ '^[A-Z][A-Z0-9_]{1,39}$');
CREATE TYPE payroll_ops.cycle_status AS ENUM ('DRAFT','POPULATION_RESOLVED','INPUTS_SEALED','CALCULATING','CALCULATED','FAILED');
CREATE TYPE payroll_calc.result_status AS ENUM ('DRAFT','CALCULATED','SUPERSEDED');
