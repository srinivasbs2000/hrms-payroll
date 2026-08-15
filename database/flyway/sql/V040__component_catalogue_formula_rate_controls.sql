-- P5-CCF-01 G02-B: component formula, dependency, rate, rounding and proration controls.
-- Forward-only from V039. V001-V039 are immutable.
-- This migration stores jurisdiction-neutral payroll configuration only; it encodes
-- no statutory rate, legal threshold or country-specific legal conclusion.

CREATE TABLE compensation.component_formula_metadata (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  formula_type varchar(30) NOT NULL,
  calculation_phase varchar(20) NOT NULL,
  result_contract varchar(20) NOT NULL,
  canonical_expression varchar(1200) NOT NULL,
  formula_fingerprint varchar(64) NOT NULL,
  dependency_count smallint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, component_version_id),
  CHECK (formula_type IN ('FIXED','PERCENTAGE_OF_COMPONENT','RESIDUAL')),
  CHECK (calculation_phase IN ('INPUT','PRE_TAX','TAX','POST_TAX','NET')),
  CHECK (result_contract = 'DECIMAL'),
  CHECK (length(canonical_expression) BETWEEN 1 AND 1200),
  CHECK (formula_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (dependency_count BETWEEN 0 AND 64),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CONSTRAINT component_formula_metadata_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id)
);

CREATE TABLE compensation.component_formula_dependency (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  formula_metadata_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  dependency_component_id uuid NOT NULL,
  dependency_component_version_id uuid NOT NULL,
  dependency_code varchar(40) NOT NULL,
  dependency_order smallint NOT NULL,
  dependency_phase varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, formula_metadata_id, dependency_component_id),
  UNIQUE (tenant_id, formula_metadata_id, dependency_order),
  CHECK (component_id <> dependency_component_id),
  CHECK (dependency_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (dependency_order BETWEEN 1 AND 64),
  CHECK (dependency_phase IN ('INPUT','PRE_TAX','TAX','POST_TAX','NET')),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CONSTRAINT component_formula_dependency_metadata_fk
    FOREIGN KEY (tenant_id, formula_metadata_id)
    REFERENCES compensation.component_formula_metadata(tenant_id, id),
  CONSTRAINT component_formula_dependency_source_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id),
  CONSTRAINT component_formula_dependency_target_fk
    FOREIGN KEY (tenant_id, dependency_component_version_id, dependency_component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id)
);

CREATE INDEX component_formula_dependency_source_ix
  ON compensation.component_formula_dependency(tenant_id, component_id, component_version_id);
CREATE INDEX component_formula_dependency_target_ix
  ON compensation.component_formula_dependency(
    tenant_id, dependency_component_id, dependency_component_version_id);

CREATE TABLE compensation.component_statutory_wage_reference (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, component_version_id, statutory_rule_id),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CONSTRAINT component_statutory_wage_reference_component_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id),
  CONSTRAINT component_statutory_wage_reference_rule_fk
    FOREIGN KEY (tenant_id, statutory_rule_version_id, statutory_rule_id)
    REFERENCES statutory.statutory_rule_version(tenant_id, id, statutory_rule_id)
);

CREATE INDEX component_statutory_wage_reference_rule_ix
  ON compensation.component_statutory_wage_reference(
    tenant_id, statutory_rule_id, statutory_rule_version_id);

CREATE TABLE compensation.component_rate_table (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(160) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (btrim(name) <> ''),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE compensation.component_rate_table_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  rate_table_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, rate_table_id),
  UNIQUE (tenant_id, rate_table_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK ((approval_status='APPROVED' AND approved_at IS NOT NULL AND approved_by IS NOT NULL)
      OR (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, rate_table_id)
    REFERENCES compensation.component_rate_table(tenant_id, id),
  CONSTRAINT component_rate_table_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, rate_table_id)
    REFERENCES compensation.component_rate_table_version(tenant_id, id, rate_table_id)
);

ALTER TABLE compensation.component_rate_table_version
  ADD CONSTRAINT component_rate_table_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    rate_table_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX component_rate_table_version_one_successor_uk
  ON compensation.component_rate_table_version(tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE TABLE compensation.component_rate_dimension (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  rate_table_version_id uuid NOT NULL,
  dimension_sequence smallint NOT NULL,
  code varchar(40) NOT NULL,
  name varchar(120) NOT NULL,
  data_type varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, rate_table_version_id, dimension_sequence),
  UNIQUE (tenant_id, rate_table_version_id, code),
  CHECK (dimension_sequence BETWEEN 1 AND 8),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (btrim(name) <> ''),
  CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','DATE')),
  FOREIGN KEY (tenant_id, rate_table_version_id)
    REFERENCES compensation.component_rate_table_version(tenant_id, id)
);

CREATE TABLE compensation.component_rate_cell (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  rate_table_version_id uuid NOT NULL,
  cell_sequence integer NOT NULL,
  dimension_values jsonb NOT NULL,
  rate_value numeric(29,10) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, rate_table_version_id, cell_sequence),
  UNIQUE (tenant_id, rate_table_version_id, dimension_values),
  CHECK (cell_sequence > 0),
  CHECK (jsonb_typeof(dimension_values)='object'),
  FOREIGN KEY (tenant_id, rate_table_version_id)
    REFERENCES compensation.component_rate_table_version(tenant_id, id)
);

CREATE FUNCTION compensation.assert_component_rate_cell_dimensions()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF EXISTS (
    SELECT d.code
      FROM compensation.component_rate_dimension d
     WHERE d.tenant_id=NEW.tenant_id AND d.rate_table_version_id=NEW.rate_table_version_id
    EXCEPT
    SELECT jsonb_object_keys(NEW.dimension_values)
  ) OR EXISTS (
    SELECT jsonb_object_keys(NEW.dimension_values)
    EXCEPT
    SELECT d.code
      FROM compensation.component_rate_dimension d
     WHERE d.tenant_id=NEW.tenant_id AND d.rate_table_version_id=NEW.rate_table_version_id
  ) THEN
    RAISE EXCEPTION 'rate-table cell must provide exactly the configured dimensions'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER component_rate_cell_dimension_shape
  BEFORE INSERT ON compensation.component_rate_cell
  FOR EACH ROW EXECUTE FUNCTION compensation.assert_component_rate_cell_dimensions();

CREATE INDEX component_rate_table_effective_ix
  ON compensation.component_rate_table_version(tenant_id,rate_table_id,effective_from DESC);
CREATE INDEX component_rate_cell_lookup_ix
  ON compensation.component_rate_cell USING gin(dimension_values);

CREATE TABLE compensation.component_rounding_policy (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  component_id uuid NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, component_id),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, component_id)
    REFERENCES compensation.pay_component(tenant_id, id)
);

CREATE TABLE compensation.component_rounding_policy_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  policy_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  rounding_method varchar(20) NOT NULL,
  rounding_scale smallint NOT NULL,
  rounding_stage varchar(20) NOT NULL,
  negative_treatment varchar(24) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, policy_id),
  UNIQUE (tenant_id, policy_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (rounding_method IN ('HALF_UP','HALF_EVEN','HALF_DOWN','UP','DOWN','CEILING','FLOOR')),
  CHECK (rounding_scale BETWEEN 0 AND 10),
  CHECK (rounding_stage IN ('COMPONENT','INTERMEDIATE','FINAL')),
  CHECK (negative_treatment IN ('SYMMETRIC','TOWARD_ZERO','AWAY_FROM_ZERO','PROHIBIT')),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK ((approval_status='APPROVED' AND approved_at IS NOT NULL AND approved_by IS NOT NULL)
      OR (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, policy_id)
    REFERENCES compensation.component_rounding_policy(tenant_id, id),
  CONSTRAINT component_rounding_policy_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, policy_id)
    REFERENCES compensation.component_rounding_policy_version(tenant_id, id, policy_id)
);

ALTER TABLE compensation.component_rounding_policy_version
  ADD CONSTRAINT component_rounding_policy_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    policy_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX component_rounding_policy_version_one_successor_uk
  ON compensation.component_rounding_policy_version(tenant_id,supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE TABLE compensation.component_proration_policy (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  component_id uuid NOT NULL,
  event_type varchar(30) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, component_id, event_type),
  CHECK (event_type IN ('JOINING','EXIT','UNPAID_LEAVE','TRANSFER','SALARY_REVISION')),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, component_id)
    REFERENCES compensation.pay_component(tenant_id, id)
);

CREATE TABLE compensation.component_proration_policy_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  policy_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  proration_method varchar(24) NOT NULL,
  proration_basis varchar(24) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, policy_id),
  UNIQUE (tenant_id, policy_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (proration_method IN ('CALENDAR_DAYS','WORKING_DAYS','ACTUAL_DAYS','NONE')),
  CHECK (proration_basis IN ('PAY_PERIOD','MONTH','ANNUAL','DAILY_RATE')),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK ((approval_status='APPROVED' AND approved_at IS NOT NULL AND approved_by IS NOT NULL)
      OR (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, policy_id)
    REFERENCES compensation.component_proration_policy(tenant_id, id),
  CONSTRAINT component_proration_policy_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, policy_id)
    REFERENCES compensation.component_proration_policy_version(tenant_id, id, policy_id)
);

ALTER TABLE compensation.component_proration_policy_version
  ADD CONSTRAINT component_proration_policy_version_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    policy_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX component_proration_policy_version_one_successor_uk
  ON compensation.component_proration_policy_version(tenant_id,supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

-- Tenant isolation is forced for every new persisted aggregate and immutable child.
ALTER TABLE compensation.component_formula_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_formula_dependency ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_statutory_wage_reference ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_table ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_table_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_dimension ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_cell ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rounding_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rounding_policy_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_proration_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_proration_policy_version ENABLE ROW LEVEL SECURITY;

CREATE POLICY component_formula_metadata_tenant_policy ON compensation.component_formula_metadata
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_formula_dependency_tenant_policy ON compensation.component_formula_dependency
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_statutory_wage_reference_tenant_policy ON compensation.component_statutory_wage_reference
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rate_table_tenant_policy ON compensation.component_rate_table
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rate_table_version_tenant_policy ON compensation.component_rate_table_version
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rate_dimension_tenant_policy ON compensation.component_rate_dimension
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rate_cell_tenant_policy ON compensation.component_rate_cell
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rounding_policy_tenant_policy ON compensation.component_rounding_policy
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_rounding_policy_version_tenant_policy ON compensation.component_rounding_policy_version
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_proration_policy_tenant_policy ON compensation.component_proration_policy
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY component_proration_policy_version_tenant_policy ON compensation.component_proration_policy_version
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());

ALTER TABLE compensation.component_formula_metadata FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_formula_dependency FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_statutory_wage_reference FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_table FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_table_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_dimension FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rate_cell FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rounding_policy FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_rounding_policy_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_proration_policy FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.component_proration_policy_version FORCE ROW LEVEL SECURITY;

CREATE FUNCTION compensation.reject_component_control_child_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable component control row: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
  END IF;
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'component control history cannot be deleted';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER component_formula_metadata_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_formula_metadata
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_formula_dependency_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_formula_dependency
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_statutory_wage_reference_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_statutory_wage_reference
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_rate_table_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_rate_table_version
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_rate_dimension_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_rate_dimension
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_rate_cell_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_rate_cell
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_rounding_policy_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_rounding_policy_version
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();
CREATE TRIGGER component_proration_policy_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.component_proration_policy_version
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_component_control_child_mutation();

-- Pay-component approval remains backward compatible; the G02-B application service
-- captures and validates formula metadata transactionally before invoking the existing approval function.

CREATE FUNCTION compensation.approve_component_rate_table_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  affected bigint;
  target_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rate_table_version v
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE v.tenant_id=p_tenant_id AND v.id=p_version_id
     AND v.version_no=p_expected_version AND v.approval_status='DRAFT'
     AND v.created_by<>p_actor
     AND EXISTS (SELECT 1 FROM compensation.component_rate_table i
                  WHERE i.tenant_id=v.tenant_id AND i.id=v.rate_table_id
                    AND i.lifecycle_status<>'RETIRED')
     AND EXISTS (SELECT 1 FROM compensation.component_rate_dimension d
                  WHERE d.tenant_id=v.tenant_id AND d.rate_table_version_id=v.id)
     AND EXISTS (SELECT 1 FROM compensation.component_rate_cell c
                  WHERE c.tenant_id=v.tenant_id AND c.rate_table_version_id=v.id)
     AND NOT EXISTS (SELECT 1 FROM compensation.component_rate_table_version s
                      WHERE s.tenant_id=v.tenant_id AND s.supersedes_version_id=v.id)
  RETURNING v.rate_table_id INTO target_id;
  GET DIAGNOSTICS affected=ROW_COUNT;
  IF affected=1 THEN
    UPDATE compensation.component_rate_table
       SET lifecycle_status='ACTIVE',updated_at=p_approved_at,updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=target_id AND lifecycle_status='PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.approve_component_rounding_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  affected bigint;
  target_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rounding_policy_version v
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE v.tenant_id=p_tenant_id AND v.id=p_version_id
     AND v.version_no=p_expected_version AND v.approval_status='DRAFT'
     AND v.created_by<>p_actor
     AND EXISTS (SELECT 1 FROM compensation.component_rounding_policy i
                  WHERE i.tenant_id=v.tenant_id AND i.id=v.policy_id
                    AND i.lifecycle_status<>'RETIRED')
     AND NOT EXISTS (SELECT 1 FROM compensation.component_rounding_policy_version s
                      WHERE s.tenant_id=v.tenant_id AND s.supersedes_version_id=v.id)
  RETURNING v.policy_id INTO target_id;
  GET DIAGNOSTICS affected=ROW_COUNT;
  IF affected=1 THEN
    UPDATE compensation.component_rounding_policy
       SET lifecycle_status='ACTIVE',updated_at=p_approved_at,updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=target_id AND lifecycle_status='PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.approve_component_proration_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  affected bigint;
  target_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_proration_policy_version v
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE v.tenant_id=p_tenant_id AND v.id=p_version_id
     AND v.version_no=p_expected_version AND v.approval_status='DRAFT'
     AND v.created_by<>p_actor
     AND EXISTS (SELECT 1 FROM compensation.component_proration_policy i
                  WHERE i.tenant_id=v.tenant_id AND i.id=v.policy_id
                    AND i.lifecycle_status<>'RETIRED')
     AND NOT EXISTS (SELECT 1 FROM compensation.component_proration_policy_version s
                      WHERE s.tenant_id=v.tenant_id AND s.supersedes_version_id=v.id)
  RETURNING v.policy_id INTO target_id;
  GET DIAGNOSTICS affected=ROW_COUNT;
  IF affected=1 THEN
    UPDATE compensation.component_proration_policy
       SET lifecycle_status='ACTIVE',updated_at=p_approved_at,updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=target_id AND lifecycle_status='PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.end_date_component_rate_table_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  v_from date;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_to IS NULL OR p_actor IS NULL OR btrim(p_actor)='' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required' USING ERRCODE='23514';
  END IF;
  SELECT effective_from INTO v_from
    FROM compensation.component_rate_table_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_effective_to<=v_from THEN
    RAISE EXCEPTION 'rate-table effective-to must be after effective-from' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rate_table_version
     SET effective_to=p_effective_to,updated_at=p_changed_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
     AND (effective_to IS NULL OR effective_to>p_effective_to);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.end_date_component_rounding_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  v_from date;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_to IS NULL OR p_actor IS NULL OR btrim(p_actor)='' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required' USING ERRCODE='23514';
  END IF;
  SELECT effective_from INTO v_from
    FROM compensation.component_rounding_policy_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_effective_to<=v_from THEN
    RAISE EXCEPTION 'rounding-policy effective-to must be after effective-from' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rounding_policy_version
     SET effective_to=p_effective_to,updated_at=p_changed_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
     AND (effective_to IS NULL OR effective_to>p_effective_to);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.end_date_component_proration_policy_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  v_from date;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_to IS NULL OR p_actor IS NULL OR btrim(p_actor)='' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required' USING ERRCODE='23514';
  END IF;
  SELECT effective_from INTO v_from
    FROM compensation.component_proration_policy_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_effective_to<=v_from THEN
    RAISE EXCEPTION 'proration-policy effective-to must be after effective-from' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_proration_policy_version
     SET effective_to=p_effective_to,updated_at=p_changed_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
     AND (effective_to IS NULL OR effective_to>p_effective_to);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

-- G02 critical-review hardening: complete lifecycle, typed rates and dependency effectivity.
ALTER TABLE compensation.component_rate_table
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160),
  ADD CONSTRAINT component_rate_table_retirement_metadata_ck CHECK (
    (lifecycle_status='RETIRED'
      AND retirement_effective_date IS NOT NULL
      AND retirement_reason IS NOT NULL AND btrim(retirement_reason)<>''
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL AND btrim(retired_by)<>'')
    OR
    (lifecycle_status<>'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL)
  );

ALTER TABLE compensation.component_rounding_policy
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160),
  ADD CONSTRAINT component_rounding_policy_retirement_metadata_ck CHECK (
    (lifecycle_status='RETIRED'
      AND retirement_effective_date IS NOT NULL
      AND retirement_reason IS NOT NULL AND btrim(retirement_reason)<>''
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL AND btrim(retired_by)<>'')
    OR
    (lifecycle_status<>'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL)
  );

ALTER TABLE compensation.component_proration_policy
  ADD COLUMN retirement_effective_date date,
  ADD COLUMN retirement_reason varchar(500),
  ADD COLUMN retired_at timestamptz,
  ADD COLUMN retired_by varchar(160),
  ADD CONSTRAINT component_proration_policy_retirement_metadata_ck CHECK (
    (lifecycle_status='RETIRED'
      AND retirement_effective_date IS NOT NULL
      AND retirement_reason IS NOT NULL AND btrim(retirement_reason)<>''
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL AND btrim(retired_by)<>'')
    OR
    (lifecycle_status<>'RETIRED'
      AND retirement_effective_date IS NULL
      AND retirement_reason IS NULL
      AND retired_at IS NULL
      AND retired_by IS NULL)
  );

ALTER TABLE compensation.component_rate_table_version
  ADD COLUMN value_type varchar(20) NOT NULL DEFAULT 'FACTOR',
  ADD COLUMN unit_code varchar(20) NOT NULL DEFAULT 'FACTOR',
  ADD CONSTRAINT component_rate_table_value_type_ck
    CHECK (value_type IN ('AMOUNT','PERCENTAGE','FACTOR','QUANTITY')),
  ADD CONSTRAINT component_rate_table_unit_code_ck
    CHECK (unit_code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
  ADD CONSTRAINT component_rate_table_value_unit_ck CHECK (
    (value_type='AMOUNT' AND unit_code ~ '^[A-Z]{3}$')
    OR (value_type='PERCENTAGE' AND unit_code='PERCENT')
    OR (value_type='FACTOR' AND unit_code='FACTOR')
    OR (value_type='QUANTITY' AND unit_code NOT IN ('PERCENT','FACTOR'))
  );

CREATE OR REPLACE FUNCTION compensation.assert_component_rate_cell_dimensions()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  dimension_record record;
  dimension_value text;
  parsed_date date;
BEGIN
  IF EXISTS (
    SELECT d.code
      FROM compensation.component_rate_dimension d
     WHERE d.tenant_id=NEW.tenant_id AND d.rate_table_version_id=NEW.rate_table_version_id
    EXCEPT
    SELECT jsonb_object_keys(NEW.dimension_values)
  ) OR EXISTS (
    SELECT jsonb_object_keys(NEW.dimension_values)
    EXCEPT
    SELECT d.code
      FROM compensation.component_rate_dimension d
     WHERE d.tenant_id=NEW.tenant_id AND d.rate_table_version_id=NEW.rate_table_version_id
  ) THEN
    RAISE EXCEPTION 'rate-table cell must provide exactly the configured dimensions'
      USING ERRCODE='23514';
  END IF;

  FOR dimension_record IN
    SELECT code,data_type
      FROM compensation.component_rate_dimension
     WHERE tenant_id=NEW.tenant_id AND rate_table_version_id=NEW.rate_table_version_id
     ORDER BY dimension_sequence
  LOOP
    dimension_value := NEW.dimension_values ->> dimension_record.code;
    IF dimension_value IS NULL OR btrim(dimension_value)='' OR btrim(dimension_value)<>dimension_value THEN
      RAISE EXCEPTION 'rate-table dimension values must be non-blank canonical strings'
        USING ERRCODE='23514';
    END IF;
    IF dimension_record.data_type='NUMBER' THEN
      IF dimension_value !~ '^-?(0|[1-9][0-9]*)(\.[0-9]+)?$'
         OR ((dimension_value::numeric)=0 AND dimension_value<>'0')
         OR ((dimension_value::numeric)<>0 AND (dimension_value::numeric)::text<>dimension_value) THEN
        RAISE EXCEPTION 'NUMBER rate-table dimensions require canonical decimal values'
          USING ERRCODE='23514';
      END IF;
    ELSIF dimension_record.data_type='BOOLEAN' THEN
      IF dimension_value NOT IN ('true','false') THEN
        RAISE EXCEPTION 'BOOLEAN rate-table dimensions require true or false'
          USING ERRCODE='23514';
      END IF;
    ELSIF dimension_record.data_type='DATE' THEN
      BEGIN
        parsed_date := dimension_value::date;
        IF to_char(parsed_date,'YYYY-MM-DD')<>dimension_value THEN
          RAISE EXCEPTION 'DATE rate-table dimensions require ISO dates' USING ERRCODE='23514';
        END IF;
      EXCEPTION WHEN others THEN
        RAISE EXCEPTION 'DATE rate-table dimensions require ISO dates' USING ERRCODE='23514';
      END;
    END IF;
  END LOOP;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION compensation.end_date_pay_component_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  v_from date;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_to IS NULL OR p_actor IS NULL OR btrim(p_actor)='' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'effective-to date, actor and change timestamp are required' USING ERRCODE='23514';
  END IF;
  SELECT effective_from INTO v_from
    FROM compensation.pay_component_version
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_effective_to<=v_from THEN
    RAISE EXCEPTION 'pay-component effective-to must be after effective-from' USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.component_formula_dependency d
      JOIN compensation.pay_component_version source
        ON source.tenant_id=d.tenant_id AND source.id=d.component_version_id
     WHERE d.tenant_id=p_tenant_id
       AND d.dependency_component_version_id=p_version_id
       AND source.approval_status<>'REJECTED'
       AND (source.effective_to IS NULL OR source.effective_to>p_effective_to)
       AND NOT EXISTS (
         SELECT 1 FROM compensation.pay_component_version successor
          WHERE successor.tenant_id=source.tenant_id
            AND successor.supersedes_version_id=source.id)
  ) THEN
    RAISE EXCEPTION 'pay-component dependency version cannot end before a dependant formula range'
      USING ERRCODE='23514';
  END IF;
  UPDATE compensation.pay_component_version
     SET effective_to=p_effective_to,updated_at=p_changed_at,updated_by=p_actor,
         version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id AND version_no=p_expected_version
     AND (effective_to IS NULL OR effective_to>p_effective_to);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE FUNCTION compensation.retire_component_rate_table(
  p_tenant_id uuid,p_identity_id uuid,p_effective_date date,p_expected_version bigint,
  p_reason varchar,p_actor varchar,p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_date IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
     OR p_actor IS NULL OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160 OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement date, reason, actor and timestamp are required' USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM compensation.component_rate_table_version v
              WHERE v.tenant_id=p_tenant_id AND v.rate_table_id=p_identity_id
                AND v.approval_status='APPROVED'
                AND (v.effective_to IS NULL OR v.effective_to>p_effective_date)) THEN
    RAISE EXCEPTION 'rate table has active or future approved versions' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rate_table
     SET lifecycle_status='RETIRED',retirement_effective_date=p_effective_date,
         retirement_reason=btrim(p_reason),retired_at=p_changed_at,retired_by=p_actor,
         updated_at=p_changed_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_identity_id AND lifecycle_status<>'RETIRED'
     AND version_no=p_expected_version;
  GET DIAGNOSTICS affected=ROW_COUNT; RETURN affected;
END $$;

CREATE FUNCTION compensation.retire_component_rounding_policy(
  p_tenant_id uuid,p_identity_id uuid,p_effective_date date,p_expected_version bigint,
  p_reason varchar,p_actor varchar,p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_date IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
     OR p_actor IS NULL OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160 OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement date, reason, actor and timestamp are required' USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM compensation.component_rounding_policy_version v
              WHERE v.tenant_id=p_tenant_id AND v.policy_id=p_identity_id
                AND v.approval_status='APPROVED'
                AND (v.effective_to IS NULL OR v.effective_to>p_effective_date)) THEN
    RAISE EXCEPTION 'rounding policy has active or future approved versions' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_rounding_policy
     SET lifecycle_status='RETIRED',retirement_effective_date=p_effective_date,
         retirement_reason=btrim(p_reason),retired_at=p_changed_at,retired_by=p_actor,
         updated_at=p_changed_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_identity_id AND lifecycle_status<>'RETIRED'
     AND version_no=p_expected_version;
  GET DIAGNOSTICS affected=ROW_COUNT; RETURN affected;
END $$;

CREATE FUNCTION compensation.retire_component_proration_policy(
  p_tenant_id uuid,p_identity_id uuid,p_effective_date date,p_expected_version bigint,
  p_reason varchar,p_actor varchar,p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_effective_date IS NULL OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
     OR p_actor IS NULL OR length(btrim(p_actor)) NOT BETWEEN 1 AND 160 OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'retirement date, reason, actor and timestamp are required' USING ERRCODE='23514';
  END IF;
  IF EXISTS (SELECT 1 FROM compensation.component_proration_policy_version v
              WHERE v.tenant_id=p_tenant_id AND v.policy_id=p_identity_id
                AND v.approval_status='APPROVED'
                AND (v.effective_to IS NULL OR v.effective_to>p_effective_date)) THEN
    RAISE EXCEPTION 'proration policy has active or future approved versions' USING ERRCODE='23514';
  END IF;
  UPDATE compensation.component_proration_policy
     SET lifecycle_status='RETIRED',retirement_effective_date=p_effective_date,
         retirement_reason=btrim(p_reason),retired_at=p_changed_at,retired_by=p_actor,
         updated_at=p_changed_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_identity_id AND lifecycle_status<>'RETIRED'
     AND version_no=p_expected_version;
  GET DIAGNOSTICS affected=ROW_COUNT; RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.retire_component_rate_table(uuid,uuid,date,bigint,varchar,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_component_rounding_policy(uuid,uuid,date,bigint,varchar,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.retire_component_proration_policy(uuid,uuid,date,bigint,varchar,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compensation.retire_component_rate_table(uuid,uuid,date,bigint,varchar,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_component_rounding_policy(uuid,uuid,date,bigint,varchar,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.retire_component_proration_policy(uuid,uuid,date,bigint,varchar,varchar,timestamptz) TO payroll_app;

REVOKE ALL ON FUNCTION compensation.reject_component_control_child_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_component_rate_cell_dimensions() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_component_rate_table_version(uuid,uuid,bigint,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_component_rounding_policy_version(uuid,uuid,bigint,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_component_proration_policy_version(uuid,uuid,bigint,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_component_rate_table_version(uuid,uuid,date,bigint,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_component_rounding_policy_version(uuid,uuid,date,bigint,varchar,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.end_date_component_proration_policy_version(uuid,uuid,date,bigint,varchar,timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.approve_component_rate_table_version(uuid,uuid,bigint,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_component_rounding_policy_version(uuid,uuid,bigint,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_component_proration_policy_version(uuid,uuid,bigint,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_component_rate_table_version(uuid,uuid,date,bigint,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_component_rounding_policy_version(uuid,uuid,date,bigint,varchar,timestamptz) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.end_date_component_proration_policy_version(uuid,uuid,date,bigint,varchar,timestamptz) TO payroll_app;

GRANT SELECT,INSERT ON
  compensation.component_formula_metadata,
  compensation.component_formula_dependency,
  compensation.component_statutory_wage_reference,
  compensation.component_rate_table,
  compensation.component_rate_table_version,
  compensation.component_rate_dimension,
  compensation.component_rate_cell,
  compensation.component_rounding_policy,
  compensation.component_rounding_policy_version,
  compensation.component_proration_policy,
  compensation.component_proration_policy_version
TO payroll_app;

REVOKE UPDATE,DELETE ON
  compensation.component_formula_metadata,
  compensation.component_formula_dependency,
  compensation.component_statutory_wage_reference,
  compensation.component_rate_table,
  compensation.component_rate_table_version,
  compensation.component_rate_dimension,
  compensation.component_rate_cell,
  compensation.component_rounding_policy,
  compensation.component_rounding_policy_version,
  compensation.component_proration_policy,
  compensation.component_proration_policy_version
FROM payroll_app;

COMMENT ON TABLE compensation.component_formula_metadata IS
  'Immutable canonical restricted-formula evidence for an exact pay-component version.';
COMMENT ON TABLE compensation.component_formula_dependency IS
  'Immutable exact-version component dependency edges used for payroll calculation planning.';
COMMENT ON TABLE compensation.component_statutory_wage_reference IS
  'Exact approved statutory rule-version references for component wage classification; legal interpretation remains in the statutory context.';
COMMENT ON TABLE compensation.component_rate_table IS
  'Stable tenant-owned identity for jurisdiction-neutral multidimensional payroll rate configuration.';
COMMENT ON TABLE compensation.component_rate_table_version IS
  'Effective-dated immutable rate-table version using half-open [from,to) effectivity.';
COMMENT ON TABLE compensation.component_rounding_policy_version IS
  'Versioned rounding method, precision, stage and negative-value treatment evidence.';
COMMENT ON TABLE compensation.component_proration_policy_version IS
  'Versioned event-specific proration method and basis for joining, exit, unpaid leave, transfer or salary revision.';
