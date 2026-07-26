-- S4-01B jurisdiction-neutral statutory rule identity/version foundation.
--
-- This migration introduces a separate statutory bounded context for rule
-- identities, immutable effective-dated versions, employee/employer portions
-- and deterministic slab bands. It does not add employee statutory profiles,
-- taxable-base mappings, calculations, balances or country-specific rule data.

CREATE SCHEMA statutory AUTHORIZATION payroll_owner;

CREATE TABLE statutory.statutory_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(160) NOT NULL,
  rule_category varchar(30) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, jurisdiction_code, authority_code, code),
  CHECK (jurisdiction_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (btrim(name) <> ''),
  CHECK (rule_category IN (
    'INCOME_TAX',
    'SOCIAL_INSURANCE',
    'PENSION',
    'HEALTH_INSURANCE',
    'EMPLOYMENT_INSURANCE',
    'LEVY',
    'OTHER'
  )),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE statutory.statutory_rule_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  currency platform.currency_code NOT NULL,
  rounding_scale smallint NOT NULL DEFAULT 2,
  rounding_mode varchar(20) NOT NULL DEFAULT 'HALF_UP',
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
  UNIQUE (tenant_id, id, statutory_rule_id),
  UNIQUE (tenant_id, statutory_rule_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (rounding_scale BETWEEN 0 AND 4),
  CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'UP', 'DOWN')),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  CHECK (
    supersedes_version_id IS NULL
    OR supersedes_version_id <> id
  ),
  FOREIGN KEY (tenant_id, statutory_rule_id)
    REFERENCES statutory.statutory_rule(tenant_id, id),
  FOREIGN KEY (
    tenant_id,
    supersedes_version_id,
    statutory_rule_id
  ) REFERENCES statutory.statutory_rule_version(
    tenant_id,
    id,
    statutory_rule_id
  )
);

CREATE TABLE statutory.statutory_rule_portion (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  liable_party varchar(20) NOT NULL,
  sequence_no integer NOT NULL,
  calculation_method varchar(20) NOT NULL,
  assessment_base_code varchar(60),
  fixed_amount numeric(19,4),
  rate_percent numeric(12,8),
  threshold_amount numeric(19,4),
  base_cap_amount numeric(19,4),
  result_minimum_amount numeric(19,4),
  result_cap_amount numeric(19,4),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, statutory_rule_version_id),
  UNIQUE (tenant_id, statutory_rule_version_id, liable_party),
  UNIQUE (tenant_id, statutory_rule_version_id, sequence_no),
  CHECK (liable_party IN ('EMPLOYEE', 'EMPLOYER')),
  CHECK (sequence_no > 0),
  CHECK (calculation_method IN ('FIXED', 'PERCENTAGE', 'SLAB')),
  CHECK (
    assessment_base_code IS NULL
    OR assessment_base_code ~ '^[A-Z][A-Z0-9_]{1,59}$'
  ),
  CHECK (threshold_amount IS NULL OR threshold_amount >= 0),
  CHECK (base_cap_amount IS NULL OR base_cap_amount >= 0),
  CHECK (
    threshold_amount IS NULL
    OR base_cap_amount IS NULL
    OR base_cap_amount > threshold_amount
  ),
  CHECK (
    result_minimum_amount IS NULL
    OR result_minimum_amount >= 0
  ),
  CHECK (result_cap_amount IS NULL OR result_cap_amount >= 0),
  CHECK (
    result_minimum_amount IS NULL
    OR result_cap_amount IS NULL
    OR result_cap_amount >= result_minimum_amount
  ),
  CHECK (
    (
      calculation_method = 'FIXED'
      AND assessment_base_code IS NULL
      AND fixed_amount IS NOT NULL
      AND fixed_amount >= 0
      AND rate_percent IS NULL
      AND threshold_amount IS NULL
      AND base_cap_amount IS NULL
    )
    OR (
      calculation_method = 'PERCENTAGE'
      AND assessment_base_code IS NOT NULL
      AND fixed_amount IS NULL
      AND rate_percent IS NOT NULL
      AND rate_percent > 0
      AND rate_percent <= 100
    )
    OR (
      calculation_method = 'SLAB'
      AND assessment_base_code IS NOT NULL
      AND fixed_amount IS NULL
      AND rate_percent IS NULL
    )
  ),
  FOREIGN KEY (tenant_id, statutory_rule_version_id)
    REFERENCES statutory.statutory_rule_version(tenant_id, id)
);

CREATE TABLE statutory.statutory_rule_slab (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  statutory_rule_portion_id uuid NOT NULL,
  sequence_no integer NOT NULL,
  lower_bound numeric(19,4) NOT NULL,
  upper_bound numeric(19,4),
  fixed_amount numeric(19,4) NOT NULL DEFAULT 0,
  rate_percent numeric(12,8) NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, statutory_rule_portion_id, sequence_no),
  CHECK (sequence_no > 0),
  CHECK (lower_bound >= 0),
  CHECK (upper_bound IS NULL OR upper_bound > lower_bound),
  CHECK (fixed_amount >= 0),
  CHECK (rate_percent >= 0 AND rate_percent <= 100),
  FOREIGN KEY (
    tenant_id,
    statutory_rule_portion_id,
    statutory_rule_version_id
  ) REFERENCES statutory.statutory_rule_portion(
    tenant_id,
    id,
    statutory_rule_version_id
  )
);

ALTER TABLE statutory.statutory_rule_version
  ADD CONSTRAINT statutory_rule_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    statutory_rule_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

ALTER TABLE statutory.statutory_rule_slab
  ADD CONSTRAINT statutory_rule_slab_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    statutory_rule_portion_id WITH =,
    numrange(lower_bound, upper_bound, '[)') WITH &&
  );

CREATE UNIQUE INDEX statutory_rule_version_one_successor_uk
  ON statutory.statutory_rule_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX statutory_rule_version_current_ix
  ON statutory.statutory_rule_version(
    tenant_id,
    statutory_rule_id,
    effective_from DESC
  );

CREATE INDEX statutory_rule_lookup_ix
  ON statutory.statutory_rule(
    tenant_id,
    jurisdiction_code,
    authority_code,
    rule_category,
    status
  );

CREATE INDEX statutory_rule_portion_version_ix
  ON statutory.statutory_rule_portion(
    tenant_id,
    statutory_rule_version_id,
    sequence_no
  );

CREATE INDEX statutory_rule_slab_portion_ix
  ON statutory.statutory_rule_slab(
    tenant_id,
    statutory_rule_portion_id,
    sequence_no
  );

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_rule_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  identity_status varchar(20);
  parent_sequence integer;
BEGIN
  SELECT rule.status
  INTO identity_status
  FROM statutory.statutory_rule rule
  WHERE rule.tenant_id = NEW.tenant_id
    AND rule.id = NEW.statutory_rule_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION
      'statutory-rule identity does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'statutory-rule versions require an active identity'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT'
     AND (
       NEW.approval_status <> 'DRAFT'
       OR NEW.approved_at IS NOT NULL
       OR NEW.approved_by IS NOT NULL
       OR NEW.version_no <> 0
     ) THEN
    RAISE EXCEPTION
      'statutory-rule versions must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
         FROM statutory.statutory_rule_version existing
         WHERE existing.tenant_id = NEW.tenant_id
           AND existing.statutory_rule_id = NEW.statutory_rule_id
       ) THEN
      RAISE EXCEPTION
        'first statutory-rule version must start a new version chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION
        'later statutory-rule versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;

    SELECT parent.version_sequence
    INTO parent_sequence
    FROM statutory.statutory_rule_version parent
    WHERE parent.tenant_id = NEW.tenant_id
      AND parent.id = NEW.supersedes_version_id
      AND parent.statutory_rule_id = NEW.statutory_rule_id
    FOR UPDATE OF parent;

    IF parent_sequence IS NULL THEN
      RAISE EXCEPTION
        'superseded statutory-rule version does not exist in the current tenant'
        USING ERRCODE = '23503';
    END IF;

    IF NEW.version_sequence <> parent_sequence + 1 THEN
      RAISE EXCEPTION
        'statutory-rule version sequence must follow its superseded version'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_version_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    statutory_rule_id,
    version_sequence,
    supersedes_version_id
  ON statutory.statutory_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION statutory.assert_statutory_rule_version_dependencies();

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_rule_portion_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  parent_status varchar(20);
BEGIN
  SELECT version.approval_status
  INTO parent_status
  FROM statutory.statutory_rule_version version
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.statutory_rule_version_id
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_rule_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
  FOR UPDATE OF version;

  IF parent_status IS NULL THEN
    RAISE EXCEPTION
      'statutory-rule version does not exist or has been superseded'
      USING ERRCODE = '23503';
  END IF;

  IF parent_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'statutory-rule portions can be inserted only into an unsuperseded draft'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_portion_dependencies
  BEFORE INSERT
  ON statutory.statutory_rule_portion
  FOR EACH ROW
  EXECUTE FUNCTION statutory.assert_statutory_rule_portion_dependencies();

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_rule_slab_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  parent_method varchar(20);
  version_status varchar(20);
BEGIN
  SELECT portion.calculation_method, version.approval_status
  INTO parent_method, version_status
  FROM statutory.statutory_rule_portion portion
  JOIN statutory.statutory_rule_version version
    ON version.tenant_id = portion.tenant_id
   AND version.id = portion.statutory_rule_version_id
  WHERE portion.tenant_id = NEW.tenant_id
    AND portion.id = NEW.statutory_rule_portion_id
    AND portion.statutory_rule_version_id =
        NEW.statutory_rule_version_id
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_rule_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
  FOR UPDATE OF version;

  IF parent_method IS NULL THEN
    RAISE EXCEPTION
      'statutory-rule portion does not exist or its version is superseded'
      USING ERRCODE = '23503';
  END IF;

  IF parent_method <> 'SLAB' THEN
    RAISE EXCEPTION 'statutory-rule slabs require a SLAB portion'
      USING ERRCODE = '23514';
  END IF;

  IF version_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'statutory-rule slabs can be inserted only into an unsuperseded draft'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_slab_dependencies
  BEFORE INSERT
  ON statutory.statutory_rule_slab
  FOR EACH ROW
  EXECUTE FUNCTION statutory.assert_statutory_rule_slab_dependencies();

REVOKE ALL ON FUNCTION
  statutory.assert_statutory_rule_version_dependencies()
  FROM PUBLIC;

REVOKE ALL ON FUNCTION
  statutory.assert_statutory_rule_portion_dependencies()
  FROM PUBLIC;

REVOKE ALL ON FUNCTION
  statutory.assert_statutory_rule_slab_dependencies()
  FROM PUBLIC;

CREATE OR REPLACE FUNCTION
  statutory.reject_uncontrolled_statutory_configuration_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting(
       'statutory.configuration_mutation',
       true
     ) IS DISTINCT FROM 'allowed' THEN
    RAISE EXCEPTION
      'statutory configuration may change only through controlled commands'
      USING ERRCODE = '42501';
  END IF;

  IF TG_TABLE_NAME = 'statutory_rule_version'
     AND TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'statutory-rule versions cannot be deleted'
      USING ERRCODE = '42501';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_version_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE TRIGGER statutory_rule_portion_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_rule_portion
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE TRIGGER statutory_rule_slab_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_rule_slab
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE OR REPLACE FUNCTION statutory.approve_statutory_rule_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_approved_at IS NULL THEN
    RAISE EXCEPTION 'approval timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  PERFORM 1
  FROM statutory.statutory_rule_version version
  JOIN statutory.statutory_rule rule
    ON rule.tenant_id = version.tenant_id
   AND rule.id = version.statutory_rule_id
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.approval_status = 'DRAFT'
    AND rule.status = 'ACTIVE'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_rule_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
  FOR UPDATE OF version;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM statutory.statutory_rule_portion portion
    WHERE portion.tenant_id = p_tenant_id
      AND portion.statutory_rule_version_id = p_version_id
  ) THEN
    RAISE EXCEPTION
      'statutory-rule approval requires at least one liability portion'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    WITH ordered AS (
      SELECT
        portion.sequence_no,
        row_number() OVER (
          ORDER BY portion.sequence_no, portion.id
        ) AS position
      FROM statutory.statutory_rule_portion portion
      WHERE portion.tenant_id = p_tenant_id
        AND portion.statutory_rule_version_id = p_version_id
    )
    SELECT 1
    FROM ordered
    WHERE sequence_no <> position
  ) THEN
    RAISE EXCEPTION
      'statutory-rule portions must use contiguous sequence numbers starting at one'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_rule_portion portion
    WHERE portion.tenant_id = p_tenant_id
      AND portion.statutory_rule_version_id = p_version_id
      AND portion.calculation_method <> 'SLAB'
      AND EXISTS (
        SELECT 1
        FROM statutory.statutory_rule_slab slab
        WHERE slab.tenant_id = portion.tenant_id
          AND slab.statutory_rule_portion_id = portion.id
      )
  ) THEN
    RAISE EXCEPTION
      'fixed and percentage statutory portions cannot contain slabs'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_rule_portion portion
    WHERE portion.tenant_id = p_tenant_id
      AND portion.statutory_rule_version_id = p_version_id
      AND portion.calculation_method = 'SLAB'
      AND NOT EXISTS (
        SELECT 1
        FROM statutory.statutory_rule_slab slab
        WHERE slab.tenant_id = portion.tenant_id
          AND slab.statutory_rule_portion_id = portion.id
      )
  ) THEN
    RAISE EXCEPTION 'SLAB statutory portions require at least one slab'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    WITH ordered AS (
      SELECT
        slab.statutory_rule_portion_id,
        slab.sequence_no,
        slab.lower_bound,
        slab.upper_bound,
        row_number() OVER (
          PARTITION BY slab.statutory_rule_portion_id
          ORDER BY slab.sequence_no
        ) AS position,
        count(*) OVER (
          PARTITION BY slab.statutory_rule_portion_id
        ) AS slab_count,
        lag(slab.upper_bound) OVER (
          PARTITION BY slab.statutory_rule_portion_id
          ORDER BY slab.sequence_no
        ) AS previous_upper
      FROM statutory.statutory_rule_slab slab
      JOIN statutory.statutory_rule_portion portion
        ON portion.tenant_id = slab.tenant_id
       AND portion.id = slab.statutory_rule_portion_id
      WHERE slab.tenant_id = p_tenant_id
        AND portion.statutory_rule_version_id = p_version_id
    )
    SELECT 1
    FROM ordered
    WHERE sequence_no <> position
       OR (position = 1 AND lower_bound <> 0)
       OR (
         position > 1
         AND previous_upper IS DISTINCT FROM lower_bound
       )
       OR (upper_bound IS NULL AND position <> slab_count)
  ) THEN
    RAISE EXCEPTION
      'statutory-rule slabs must be ordered, contiguous and open-ended only at the end'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_rule_version version
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_rule_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.end_date_statutory_rule_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_effective_to IS NULL THEN
    RAISE EXCEPTION 'effective-to date is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_changed_at IS NULL THEN
    RAISE EXCEPTION 'change timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_rule_version version
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.approval_status = 'APPROVED'
    AND version.version_no = p_expected_version
    AND version.effective_from < p_effective_to
    AND (
      version.effective_to IS NULL
      OR version.effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'statutory_rule',
    'statutory_rule_version',
    'statutory_rule_portion',
    'statutory_rule_slab'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE statutory.%I ENABLE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'ALTER TABLE statutory.%I FORCE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON statutory.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      table_name
    );
  END LOOP;
END $$;

REVOKE ALL ON FUNCTION statutory.approve_statutory_rule_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION statutory.end_date_statutory_rule_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT USAGE ON SCHEMA statutory TO payroll_app;

GRANT SELECT, INSERT
  ON statutory.statutory_rule,
     statutory.statutory_rule_version,
     statutory.statutory_rule_portion,
     statutory.statutory_rule_slab
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON statutory.statutory_rule,
     statutory.statutory_rule_version,
     statutory.statutory_rule_portion,
     statutory.statutory_rule_slab
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION statutory.approve_statutory_rule_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION statutory.end_date_statutory_rule_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

REVOKE CREATE ON SCHEMA statutory FROM payroll_app;

COMMENT ON SCHEMA statutory IS
  'Jurisdiction-neutral statutory rule configuration and later statutory payroll evidence.';

COMMENT ON TABLE statutory.statutory_rule IS
  'Stable tenant-scoped statutory rule identity classified by jurisdiction, authority and category.';

COMMENT ON TABLE statutory.statutory_rule_version IS
  'Immutable approved effective-dated statutory rule configuration.';

COMMENT ON TABLE statutory.statutory_rule_portion IS
  'Employee or employer liability portion belonging to an exact statutory rule version.';

COMMENT ON TABLE statutory.statutory_rule_slab IS
  'Ordered deterministic assessment bands for a SLAB statutory rule portion.';
