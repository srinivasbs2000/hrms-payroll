-- P5-SSC-01 G02G / E04-009 statutory and minimum-wage compatibility.
-- Legal values remain owned by the statutory bounded context. Compensation
-- stores only exact-version bindings and immutable design-time evidence.
-- No country-specific legal value is hardcoded in this migration.

ALTER TABLE statutory.statutory_rule
  DROP CONSTRAINT IF EXISTS statutory_rule_rule_category_check;

ALTER TABLE statutory.statutory_rule
  ADD CONSTRAINT statutory_rule_rule_category_check
  CHECK (rule_category IN (
    'INCOME_TAX',
    'SOCIAL_INSURANCE',
    'PENSION',
    'HEALTH_INSURANCE',
    'EMPLOYMENT_INSURANCE',
    'LEVY',
    'MINIMUM_WAGE',
    'OTHER'
  ));

CREATE TABLE statutory.statutory_rule_design_constraint (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  constraint_kind varchar(30) NOT NULL,
  period_basis varchar(20) NOT NULL,
  minimum_amount numeric(19,4) NOT NULL,
  source_reference varchar(500),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, statutory_rule_version_id, constraint_kind),
  CHECK (constraint_kind = 'MINIMUM_WAGE'),
  CHECK (period_basis IN ('ANNUAL', 'MONTHLY', 'DAILY', 'HOURLY')),
  CHECK (minimum_amount > 0),
  CHECK (
    source_reference IS NULL
    OR length(btrim(source_reference)) BETWEEN 1 AND 500
  ),
  CONSTRAINT statutory_rule_design_constraint_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    )
    REFERENCES statutory.statutory_rule_version(
      tenant_id,
      id,
      statutory_rule_id
    )
);

CREATE INDEX statutory_rule_design_constraint_lookup_ix
  ON statutory.statutory_rule_design_constraint(
    tenant_id,
    statutory_rule_version_id,
    constraint_kind
  );

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_rule_design_constraint_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  parent_status varchar;
  parent_category varchar;
BEGIN
  SELECT version.approval_status, rule.rule_category
    INTO parent_status, parent_category
    FROM statutory.statutory_rule_version version
    JOIN statutory.statutory_rule rule
      ON rule.tenant_id = version.tenant_id
     AND rule.id = version.statutory_rule_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.statutory_rule_version_id
     AND version.statutory_rule_id = NEW.statutory_rule_id
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
      'statutory design constraints require an unsuperseded draft rule version'
      USING ERRCODE = '23514';
  END IF;

  IF parent_category <> 'MINIMUM_WAGE' THEN
    RAISE EXCEPTION
      'minimum-wage design constraints require a MINIMUM_WAGE statutory rule'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_design_constraint_dependencies
  BEFORE INSERT
  ON statutory.statutory_rule_design_constraint
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.assert_statutory_rule_design_constraint_dependencies();

CREATE TRIGGER statutory_rule_design_constraint_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_rule_design_constraint
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

ALTER TABLE statutory.statutory_rule_design_constraint
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE statutory.statutory_rule_design_constraint
  FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation
  ON statutory.statutory_rule_design_constraint
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

GRANT SELECT, INSERT
  ON statutory.statutory_rule_design_constraint
  TO payroll_app;
REVOKE UPDATE, DELETE
  ON statutory.statutory_rule_design_constraint
  FROM payroll_app;

-- Preserve V027 approval semantics for all liability rule categories. The only
-- new branch is MINIMUM_WAGE, which requires a wage-floor design constraint
-- instead of fabricating an employee/employer liability portion.
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
  target_category varchar;
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

  SELECT rule.rule_category
    INTO target_category
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

  IF target_category = 'MINIMUM_WAGE' THEN
    IF NOT EXISTS (
      SELECT 1
        FROM statutory.statutory_rule_design_constraint constraint_row
       WHERE constraint_row.tenant_id = p_tenant_id
         AND constraint_row.statutory_rule_version_id = p_version_id
         AND constraint_row.constraint_kind = 'MINIMUM_WAGE'
    ) THEN
      RAISE EXCEPTION
        'MINIMUM_WAGE statutory-rule approval requires a wage-floor design constraint'
        USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
      SELECT 1
        FROM statutory.statutory_rule_portion portion
       WHERE portion.tenant_id = p_tenant_id
         AND portion.statutory_rule_version_id = p_version_id
    ) THEN
      RAISE EXCEPTION
        'MINIMUM_WAGE rules are design constraints and must not contain liability portions'
        USING ERRCODE = '23514';
    END IF;
  ELSE
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

CREATE TABLE compensation.salary_structure_statutory_state (
  tenant_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  binding_revision bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  PRIMARY KEY (tenant_id, salary_structure_version_id),
  CHECK (binding_revision >= 0),
  FOREIGN KEY (tenant_id, salary_structure_version_id)
    REFERENCES compensation.salary_structure_version(tenant_id, id)
);

CREATE TABLE compensation.salary_structure_statutory_binding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  binding_purpose varchar(30) NOT NULL,
  enforcement_level varchar(20) NOT NULL,
  component_version_id uuid,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  retired_at timestamptz,
  retired_by varchar(160),
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (binding_purpose IN ('MINIMUM_WAGE', 'STATUTORY_RULE')),
  CHECK (enforcement_level IN ('BLOCKING', 'ADVISORY')),
  CHECK (status IN ('ACTIVE', 'RETIRED')),
  CHECK (
    (binding_purpose = 'MINIMUM_WAGE' AND component_version_id IS NOT NULL)
    OR
    (binding_purpose = 'STATUTORY_RULE' AND component_version_id IS NULL)
  ),
  CHECK (
    (status = 'ACTIVE' AND retired_at IS NULL AND retired_by IS NULL)
    OR
    (
      status = 'RETIRED'
      AND retired_at IS NOT NULL
      AND retired_by IS NOT NULL
      AND btrim(retired_by) <> ''
    )
  ),
  FOREIGN KEY (tenant_id, salary_structure_version_id)
    REFERENCES compensation.salary_structure_version(tenant_id, id),
  CONSTRAINT salary_structure_statutory_binding_rule_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    )
    REFERENCES statutory.statutory_rule_version(
      tenant_id,
      id,
      statutory_rule_id
    ),
  FOREIGN KEY (tenant_id, component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id, id)
);

CREATE UNIQUE INDEX salary_structure_statutory_binding_active_uk
  ON compensation.salary_structure_statutory_binding(
    tenant_id,
    salary_structure_version_id,
    binding_purpose,
    statutory_rule_version_id
  )
  WHERE status = 'ACTIVE';

CREATE INDEX salary_structure_statutory_binding_lookup_ix
  ON compensation.salary_structure_statutory_binding(
    tenant_id,
    salary_structure_version_id,
    status,
    binding_purpose
  );

CREATE TABLE compensation.salary_structure_statutory_evaluation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  validation_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  statutory_binding_revision bigint NOT NULL,
  validation_status varchar(20) NOT NULL,
  blocking_issue_count integer NOT NULL,
  advisory_issue_count integer NOT NULL,
  evidence_hash varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  CHECK (statutory_binding_revision >= 0),
  CHECK (validation_status IN ('PASS', 'FAIL')),
  CHECK (blocking_issue_count >= 0),
  CHECK (advisory_issue_count >= 0),
  CHECK (evidence_hash ~ '^[0-9a-f]{64}$'),
  FOREIGN KEY (tenant_id, validation_id)
    REFERENCES compensation.salary_structure_validation(tenant_id, id),
  FOREIGN KEY (tenant_id, salary_structure_version_id)
    REFERENCES compensation.salary_structure_version(tenant_id, id)
);

CREATE INDEX salary_structure_statutory_evaluation_lookup_ix
  ON compensation.salary_structure_statutory_evaluation(
    tenant_id,
    validation_id,
    created_at DESC
  );

CREATE TABLE compensation.salary_structure_statutory_issue (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  evaluation_id uuid NOT NULL,
  binding_id uuid,
  issue_code varchar(80) NOT NULL,
  severity varchar(20) NOT NULL,
  statutory_rule_id uuid,
  statutory_rule_version_id uuid,
  component_version_id uuid,
  period_basis varchar(20),
  required_amount numeric(19,4),
  actual_amount numeric(19,4),
  issue_detail varchar(1000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (tenant_id, id),
  CHECK (issue_code ~ '^[A-Z][A-Z0-9_]{2,79}$'),
  CHECK (severity IN ('BLOCKING', 'ADVISORY')),
  CHECK (
    period_basis IS NULL
    OR period_basis IN ('ANNUAL', 'MONTHLY', 'DAILY', 'HOURLY')
  ),
  CHECK (required_amount IS NULL OR required_amount >= 0),
  CHECK (actual_amount IS NULL OR actual_amount >= 0),
  CHECK (btrim(issue_detail) <> ''),
  FOREIGN KEY (tenant_id, evaluation_id)
    REFERENCES compensation.salary_structure_statutory_evaluation(tenant_id, id),
  FOREIGN KEY (tenant_id, binding_id)
    REFERENCES compensation.salary_structure_statutory_binding(tenant_id, id)
);

CREATE INDEX salary_structure_statutory_issue_lookup_ix
  ON compensation.salary_structure_statutory_issue(
    tenant_id,
    evaluation_id,
    severity,
    issue_code
  );

CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_statutory_binding_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, statutory, platform AS $$
DECLARE
  structure_status varchar;
  structure_schema smallint;
  structure_from date;
  structure_to date;
  structure_currency varchar;
  rule_status varchar;
  rule_category varchar;
  rule_version_status varchar;
  rule_from date;
  rule_to date;
  rule_currency varchar;
BEGIN
  SELECT structure.approval_status,
         structure.structure_schema_version,
         structure.effective_from,
         structure.effective_to,
         structure.currency::text
    INTO structure_status,
         structure_schema,
         structure_from,
         structure_to,
         structure_currency
    FROM compensation.salary_structure_version structure
   WHERE structure.tenant_id = NEW.tenant_id
     AND structure.id = NEW.salary_structure_version_id
   FOR UPDATE OF structure;

  IF structure_status IS NULL THEN
    RAISE EXCEPTION
      'salary-structure version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF structure_schema <> 1 OR structure_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'statutory bindings require a schema-1 draft salary structure'
      USING ERRCODE = '23514';
  END IF;

  SELECT rule.status,
         rule.rule_category,
         version.approval_status,
         version.effective_from,
         version.effective_to,
         version.currency::text
    INTO rule_status,
         rule_category,
         rule_version_status,
         rule_from,
         rule_to,
         rule_currency
    FROM statutory.statutory_rule_version version
    JOIN statutory.statutory_rule rule
      ON rule.tenant_id = version.tenant_id
     AND rule.id = version.statutory_rule_id
   WHERE version.tenant_id = NEW.tenant_id
     AND version.id = NEW.statutory_rule_version_id
     AND version.statutory_rule_id = NEW.statutory_rule_id;

  IF rule_status IS NULL THEN
    RAISE EXCEPTION
      'statutory-rule version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF rule_status <> 'ACTIVE'
     OR rule_version_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'salary-structure statutory bindings require an active approved rule version'
      USING ERRCODE = '23514';
  END IF;

  IF structure_from < rule_from
     OR (
       rule_to IS NOT NULL
       AND (structure_to IS NULL OR structure_to > rule_to)
     ) THEN
    RAISE EXCEPTION
      'salary-structure range must be contained by its statutory-rule version'
      USING ERRCODE = '23514';
  END IF;

  IF structure_currency <> rule_currency THEN
    RAISE EXCEPTION
      'salary-structure and statutory-rule currencies must match'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.binding_purpose = 'MINIMUM_WAGE' THEN
    IF rule_category <> 'MINIMUM_WAGE' THEN
      RAISE EXCEPTION
        'MINIMUM_WAGE bindings require a MINIMUM_WAGE statutory rule'
        USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
      SELECT 1
        FROM statutory.statutory_rule_design_constraint constraint_row
       WHERE constraint_row.tenant_id = NEW.tenant_id
         AND constraint_row.statutory_rule_version_id =
           NEW.statutory_rule_version_id
         AND constraint_row.constraint_kind = 'MINIMUM_WAGE'
    ) THEN
      RAISE EXCEPTION
        'minimum-wage binding requires an approved wage-floor design constraint'
        USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
      SELECT 1
        FROM compensation.salary_structure_line line
       WHERE line.tenant_id = NEW.tenant_id
         AND line.salary_structure_version_id =
           NEW.salary_structure_version_id
         AND line.component_version_id = NEW.component_version_id
    ) THEN
      RAISE EXCEPTION
        'minimum-wage comparison component must belong to the exact salary structure'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF rule_category = 'MINIMUM_WAGE' THEN
      RAISE EXCEPTION
        'MINIMUM_WAGE statutory rules must use the MINIMUM_WAGE binding purpose'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_statutory_binding_dependencies
  BEFORE INSERT
  ON compensation.salary_structure_statutory_binding
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_statutory_binding_dependencies();

CREATE OR REPLACE FUNCTION compensation.bind_salary_structure_statutory_rule(
  p_tenant_id uuid,
  p_salary_structure_id uuid,
  p_salary_structure_version_id uuid,
  p_statutory_rule_version_id uuid,
  p_binding_purpose varchar,
  p_enforcement_level varchar,
  p_component_version_id uuid,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, statutory, platform AS $$
DECLARE
  new_binding_id uuid := gen_random_uuid();
  rule_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'actor and change timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version structure
     WHERE structure.tenant_id = p_tenant_id
       AND structure.id = p_salary_structure_version_id
       AND structure.salary_structure_id = p_salary_structure_id
       AND structure.structure_schema_version = 1
       AND structure.approval_status = 'DRAFT'
  ) THEN
    RAISE EXCEPTION
      'binding target must be the exact schema-1 draft salary-structure version'
      USING ERRCODE = '23514';
  END IF;

  SELECT version.statutory_rule_id
    INTO rule_id
    FROM statutory.statutory_rule_version version
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_statutory_rule_version_id;

  IF rule_id IS NULL THEN
    RAISE EXCEPTION
      'statutory-rule version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  INSERT INTO compensation.salary_structure_statutory_binding(
    id,
    tenant_id,
    salary_structure_version_id,
    statutory_rule_id,
    statutory_rule_version_id,
    binding_purpose,
    enforcement_level,
    component_version_id,
    status,
    created_at,
    created_by
  ) VALUES (
    new_binding_id,
    p_tenant_id,
    p_salary_structure_version_id,
    rule_id,
    p_statutory_rule_version_id,
    p_binding_purpose,
    p_enforcement_level,
    p_component_version_id,
    'ACTIVE',
    p_changed_at,
    p_actor
  );

  INSERT INTO compensation.salary_structure_statutory_state(
    tenant_id,
    salary_structure_version_id,
    binding_revision,
    updated_at,
    updated_by
  ) VALUES (
    p_tenant_id,
    p_salary_structure_version_id,
    1,
    p_changed_at,
    p_actor
  )
  ON CONFLICT (tenant_id, salary_structure_version_id)
  DO UPDATE
     SET binding_revision =
           compensation.salary_structure_statutory_state.binding_revision + 1,
         updated_at = excluded.updated_at,
         updated_by = excluded.updated_by;

  RETURN new_binding_id;
END $$;

CREATE OR REPLACE FUNCTION
  compensation.retire_salary_structure_statutory_binding(
    p_tenant_id uuid,
    p_salary_structure_id uuid,
    p_salary_structure_version_id uuid,
    p_binding_id uuid,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'actor and change timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version structure
     WHERE structure.tenant_id = p_tenant_id
       AND structure.id = p_salary_structure_version_id
       AND structure.salary_structure_id = p_salary_structure_id
       AND structure.structure_schema_version = 1
       AND structure.approval_status = 'DRAFT'
  ) THEN
    RETURN 0;
  END IF;

  UPDATE compensation.salary_structure_statutory_binding binding
     SET status = 'RETIRED',
         retired_at = p_changed_at,
         retired_by = p_actor,
         version_no = version_no + 1
   WHERE binding.tenant_id = p_tenant_id
     AND binding.id = p_binding_id
     AND binding.salary_structure_version_id =
       p_salary_structure_version_id
     AND binding.status = 'ACTIVE'
     AND binding.version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    INSERT INTO compensation.salary_structure_statutory_state(
      tenant_id,
      salary_structure_version_id,
      binding_revision,
      updated_at,
      updated_by
    ) VALUES (
      p_tenant_id,
      p_salary_structure_version_id,
      1,
      p_changed_at,
      p_actor
    )
    ON CONFLICT (tenant_id, salary_structure_version_id)
    DO UPDATE
       SET binding_revision =
             compensation.salary_structure_statutory_state.binding_revision + 1,
           updated_at = excluded.updated_at,
           updated_by = excluded.updated_by;
  END IF;

  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  compensation.salary_structure_statutory_compatibility_issues(
    p_tenant_id uuid,
    p_salary_structure_version_id uuid,
    p_validation_id uuid
  ) RETURNS TABLE (
    binding_id uuid,
    issue_code varchar,
    severity varchar,
    statutory_rule_id uuid,
    statutory_rule_version_id uuid,
    component_version_id uuid,
    period_basis varchar,
    required_amount numeric,
    actual_amount numeric,
    issue_detail varchar
  )
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, compensation, statutory, platform AS $$
DECLARE
  validation_date date;
  structure_currency varchar;
  structure_schema smallint;
  binding record;
  comparison_amount numeric;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT validation.effective_date,
         structure.currency::text,
         structure.structure_schema_version
    INTO validation_date,
         structure_currency,
         structure_schema
    FROM compensation.salary_structure_validation validation
    JOIN compensation.salary_structure_version structure
      ON structure.tenant_id = validation.tenant_id
     AND structure.id = validation.salary_structure_version_id
   WHERE validation.tenant_id = p_tenant_id
     AND validation.id = p_validation_id
     AND validation.salary_structure_version_id =
       p_salary_structure_version_id;

  IF validation_date IS NULL THEN
    RETURN QUERY SELECT
      NULL::uuid,
      'STATUTORY_VALIDATION_NOT_FOUND'::varchar,
      'BLOCKING'::varchar,
      NULL::uuid,
      NULL::uuid,
      NULL::uuid,
      NULL::varchar,
      NULL::numeric,
      NULL::numeric,
      'structural validation does not belong to this salary-structure version'::varchar;
    RETURN;
  END IF;

  IF structure_schema <> 1 THEN
    RETURN QUERY SELECT
      NULL::uuid,
      'STATUTORY_SCHEMA_NOT_SUPPORTED'::varchar,
      'BLOCKING'::varchar,
      NULL::uuid,
      NULL::uuid,
      NULL::uuid,
      NULL::varchar,
      NULL::numeric,
      NULL::numeric,
      'statutory design-time compatibility requires a schema-1 salary structure'::varchar;
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM compensation.salary_structure_statutory_binding existing
     WHERE existing.tenant_id = p_tenant_id
       AND existing.salary_structure_version_id =
         p_salary_structure_version_id
       AND existing.status = 'ACTIVE'
       AND existing.binding_purpose = 'MINIMUM_WAGE'
  ) THEN
    RETURN QUERY SELECT
      NULL::uuid,
      'MINIMUM_WAGE_AUTHORITY_NOT_BOUND'::varchar,
      'ADVISORY'::varchar,
      NULL::uuid,
      NULL::uuid,
      NULL::uuid,
      NULL::varchar,
      NULL::numeric,
      NULL::numeric,
      'no minimum-wage authority is bound; applicability cannot be inferred without jurisdiction policy'::varchar;
  END IF;

  FOR binding IN
    SELECT b.id,
           b.binding_purpose,
           b.enforcement_level,
           b.component_version_id,
           r.id rule_id,
           r.status rule_status,
           r.rule_category,
           rv.id rule_version_id,
           rv.approval_status rule_version_status,
           rv.currency::text rule_currency,
           rv.effective_from rule_from,
           rv.effective_to rule_to,
           constraint_row.period_basis,
           constraint_row.minimum_amount
      FROM compensation.salary_structure_statutory_binding b
      JOIN statutory.statutory_rule_version rv
        ON rv.tenant_id = b.tenant_id
       AND rv.id = b.statutory_rule_version_id
       AND rv.statutory_rule_id = b.statutory_rule_id
      JOIN statutory.statutory_rule r
        ON r.tenant_id = rv.tenant_id
       AND r.id = rv.statutory_rule_id
      LEFT JOIN statutory.statutory_rule_design_constraint constraint_row
        ON constraint_row.tenant_id = rv.tenant_id
       AND constraint_row.statutory_rule_version_id = rv.id
       AND constraint_row.constraint_kind = 'MINIMUM_WAGE'
     WHERE b.tenant_id = p_tenant_id
       AND b.salary_structure_version_id =
         p_salary_structure_version_id
       AND b.status = 'ACTIVE'
     ORDER BY b.created_at, b.id
  LOOP
    IF binding.rule_status <> 'ACTIVE'
       OR binding.rule_version_status <> 'APPROVED' THEN
      RETURN QUERY SELECT
        binding.id,
        'STATUTORY_RULE_NOT_APPROVED'::varchar,
        binding.enforcement_level::varchar,
        binding.rule_id,
        binding.rule_version_id,
        binding.component_version_id,
        binding.period_basis::varchar,
        binding.minimum_amount,
        NULL::numeric,
        'bound statutory-rule version is not active and approved'::varchar;
      CONTINUE;
    END IF;

    IF validation_date < binding.rule_from
       OR (
         binding.rule_to IS NOT NULL
         AND validation_date >= binding.rule_to
       ) THEN
      RETURN QUERY SELECT
        binding.id,
        'STATUTORY_RULE_NOT_EFFECTIVE'::varchar,
        binding.enforcement_level::varchar,
        binding.rule_id,
        binding.rule_version_id,
        binding.component_version_id,
        binding.period_basis::varchar,
        binding.minimum_amount,
        NULL::numeric,
        'bound statutory-rule version is not effective on the validation date'::varchar;
      CONTINUE;
    END IF;

    IF binding.rule_currency <> structure_currency THEN
      RETURN QUERY SELECT
        binding.id,
        'STATUTORY_RULE_CURRENCY_MISMATCH'::varchar,
        binding.enforcement_level::varchar,
        binding.rule_id,
        binding.rule_version_id,
        binding.component_version_id,
        binding.period_basis::varchar,
        binding.minimum_amount,
        NULL::numeric,
        'bound statutory-rule currency does not match the salary structure'::varchar;
      CONTINUE;
    END IF;

    IF binding.binding_purpose = 'MINIMUM_WAGE' THEN
      IF binding.rule_category <> 'MINIMUM_WAGE'
         OR binding.period_basis IS NULL
         OR binding.minimum_amount IS NULL THEN
        RETURN QUERY SELECT
          binding.id,
          'MINIMUM_WAGE_CONSTRAINT_INVALID'::varchar,
          binding.enforcement_level::varchar,
          binding.rule_id,
          binding.rule_version_id,
          binding.component_version_id,
          binding.period_basis::varchar,
          binding.minimum_amount,
          NULL::numeric,
          'minimum-wage binding does not resolve an approved wage-floor constraint'::varchar;
        CONTINUE;
      END IF;

      IF binding.period_basis IN ('DAILY', 'HOURLY') THEN
        RETURN QUERY SELECT
          binding.id,
          'MINIMUM_WAGE_RUNTIME_BASIS_UNRESOLVED'::varchar,
          binding.enforcement_level::varchar,
          binding.rule_id,
          binding.rule_version_id,
          binding.component_version_id,
          binding.period_basis::varchar,
          binding.minimum_amount,
          NULL::numeric,
          'daily/hourly wage-floor comparison requires downstream working-time context and is not fabricated at design time'::varchar;
        CONTINUE;
      END IF;

      SELECT CASE
               WHEN binding.period_basis = 'ANNUAL'
                 THEN line.annual_amount
               WHEN binding.period_basis = 'MONTHLY'
                 THEN line.monthly_amount
             END
        INTO comparison_amount
        FROM compensation.salary_structure_validation_line line
       WHERE line.tenant_id = p_tenant_id
         AND line.validation_id = p_validation_id
         AND line.component_version_id = binding.component_version_id;

      IF comparison_amount IS NULL THEN
        RETURN QUERY SELECT
          binding.id,
          'MINIMUM_WAGE_COMPONENT_EVIDENCE_MISSING'::varchar,
          binding.enforcement_level::varchar,
          binding.rule_id,
          binding.rule_version_id,
          binding.component_version_id,
          binding.period_basis::varchar,
          binding.minimum_amount,
          NULL::numeric,
          'structural validation does not contain the bound wage comparison component'::varchar;
        CONTINUE;
      END IF;

      IF comparison_amount < binding.minimum_amount THEN
        RETURN QUERY SELECT
          binding.id,
          'MINIMUM_WAGE_BELOW_THRESHOLD'::varchar,
          binding.enforcement_level::varchar,
          binding.rule_id,
          binding.rule_version_id,
          binding.component_version_id,
          binding.period_basis::varchar,
          binding.minimum_amount,
          comparison_amount,
          format(
            'design-time component amount %s is below bound minimum-wage threshold %s',
            comparison_amount,
            binding.minimum_amount
          )::varchar;
      END IF;
    END IF;
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION
  compensation.evaluate_salary_structure_statutory_compatibility(
    p_tenant_id uuid,
    p_salary_structure_id uuid,
    p_salary_structure_version_id uuid,
    p_validation_id uuid,
    p_actor varchar,
    p_evaluated_at timestamptz
  ) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, statutory, platform, public AS $$
DECLARE
  new_evaluation_id uuid := gen_random_uuid();
  current_revision bigint;
  validation_result_hash varchar;
  blocking_count integer;
  advisory_count integer;
  result_status varchar;
  result_hash varchar;
  binding_fingerprint text;
  issue_fingerprint text;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_evaluated_at IS NULL THEN
    RAISE EXCEPTION 'actor and evaluation timestamp are required'
      USING ERRCODE = '23514';
  END IF;

  SELECT validation.result_hash,
         coalesce(state.binding_revision, 0)
    INTO validation_result_hash,
         current_revision
    FROM compensation.salary_structure_validation validation
    JOIN compensation.salary_structure_version structure
      ON structure.tenant_id = validation.tenant_id
     AND structure.id = validation.salary_structure_version_id
    LEFT JOIN compensation.salary_structure_statutory_state state
      ON state.tenant_id = structure.tenant_id
     AND state.salary_structure_version_id = structure.id
   WHERE validation.tenant_id = p_tenant_id
     AND validation.id = p_validation_id
     AND validation.salary_structure_version_id =
       p_salary_structure_version_id
     AND structure.salary_structure_id = p_salary_structure_id
     AND structure.structure_schema_version = 1
     AND structure.approval_status = 'DRAFT';

  IF validation_result_hash IS NULL THEN
    RAISE EXCEPTION
      'compatibility evaluation requires an exact schema-1 draft salary-structure validation'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO compensation.salary_structure_statutory_evaluation(
    id,
    tenant_id,
    validation_id,
    salary_structure_version_id,
    statutory_binding_revision,
    validation_status,
    blocking_issue_count,
    advisory_issue_count,
    evidence_hash,
    created_at,
    created_by
  ) VALUES (
    new_evaluation_id,
    p_tenant_id,
    p_validation_id,
    p_salary_structure_version_id,
    current_revision,
    'PASS',
    0,
    0,
    repeat('0', 64),
    p_evaluated_at,
    p_actor
  );

  INSERT INTO compensation.salary_structure_statutory_issue(
    tenant_id,
    evaluation_id,
    binding_id,
    issue_code,
    severity,
    statutory_rule_id,
    statutory_rule_version_id,
    component_version_id,
    period_basis,
    required_amount,
    actual_amount,
    issue_detail,
    created_at
  )
  SELECT p_tenant_id,
         new_evaluation_id,
         issue.binding_id,
         issue.issue_code,
         issue.severity,
         issue.statutory_rule_id,
         issue.statutory_rule_version_id,
         issue.component_version_id,
         issue.period_basis,
         issue.required_amount,
         issue.actual_amount,
         issue.issue_detail,
         p_evaluated_at
    FROM compensation.salary_structure_statutory_compatibility_issues(
      p_tenant_id,
      p_salary_structure_version_id,
      p_validation_id
    ) issue;

  SELECT count(*) FILTER (WHERE severity = 'BLOCKING'),
         count(*) FILTER (WHERE severity = 'ADVISORY')
    INTO blocking_count, advisory_count
    FROM compensation.salary_structure_statutory_issue issue
   WHERE issue.tenant_id = p_tenant_id
     AND issue.evaluation_id = new_evaluation_id;

  result_status := CASE
    WHEN blocking_count = 0 THEN 'PASS'
    ELSE 'FAIL'
  END;

  SELECT coalesce(
    string_agg(
      concat_ws(
        '|',
        binding.id::text,
        binding.statutory_rule_version_id::text,
        binding.binding_purpose,
        binding.enforcement_level,
        coalesce(binding.component_version_id::text, '')
      ),
      ';'
      ORDER BY binding.id
    ),
    'NO_BINDINGS'
  )
    INTO binding_fingerprint
    FROM compensation.salary_structure_statutory_binding binding
   WHERE binding.tenant_id = p_tenant_id
     AND binding.salary_structure_version_id =
       p_salary_structure_version_id
     AND binding.status = 'ACTIVE';

  SELECT coalesce(
    string_agg(
      concat_ws(
        '|',
        issue.issue_code,
        issue.severity,
        coalesce(issue.binding_id::text, ''),
        coalesce(issue.required_amount::text, ''),
        coalesce(issue.actual_amount::text, '')
      ),
      ';'
      ORDER BY issue.issue_code, issue.id
    ),
    'NO_ISSUES'
  )
    INTO issue_fingerprint
    FROM compensation.salary_structure_statutory_issue issue
   WHERE issue.tenant_id = p_tenant_id
     AND issue.evaluation_id = new_evaluation_id;

  result_hash := encode(
    public.digest(
      concat_ws(
        ':',
        validation_result_hash,
        current_revision::text,
        binding_fingerprint,
        issue_fingerprint,
        result_status
      ),
      'sha256'
    ),
    'hex'
  );

  UPDATE compensation.salary_structure_statutory_evaluation evaluation
     SET validation_status = result_status,
         blocking_issue_count = blocking_count,
         advisory_issue_count = advisory_count,
         evidence_hash = result_hash
   WHERE evaluation.tenant_id = p_tenant_id
     AND evaluation.id = new_evaluation_id;

  RETURN new_evaluation_id;
END $$;

CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_statutory_approval()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, compensation, platform AS $$
DECLARE
  current_revision bigint;
  bound_validation_id uuid;
BEGIN
  IF NEW.structure_schema_version = 1
     AND OLD.approval_status <> 'APPROVED'
     AND NEW.approval_status = 'APPROVED'
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_structure_statutory_binding binding
        WHERE binding.tenant_id = NEW.tenant_id
          AND binding.salary_structure_version_id = NEW.id
          AND binding.status = 'ACTIVE'
     ) THEN

    current_revision := coalesce((
      SELECT state.binding_revision
        FROM compensation.salary_structure_statutory_state state
       WHERE state.tenant_id = NEW.tenant_id
         AND state.salary_structure_version_id = NEW.id
    ), 0);

    SELECT validation.id
      INTO bound_validation_id
      FROM compensation.salary_structure_validation validation
     WHERE validation.tenant_id = NEW.tenant_id
       AND validation.salary_structure_version_id = NEW.id
       AND validation.validation_status = 'PASS'
       AND validation.result_hash = NEW.validation_fingerprint
       AND validation.configuration_hash = NEW.configuration_hash
     ORDER BY validation.created_at DESC, validation.id DESC
     LIMIT 1;

    IF bound_validation_id IS NULL
       OR NOT EXISTS (
         SELECT 1
           FROM compensation.salary_structure_statutory_evaluation evaluation
          WHERE evaluation.tenant_id = NEW.tenant_id
            AND evaluation.validation_id = bound_validation_id
            AND evaluation.salary_structure_version_id = NEW.id
            AND evaluation.statutory_binding_revision = current_revision
            AND evaluation.validation_status = 'PASS'
            AND evaluation.blocking_issue_count = 0
            AND NOT EXISTS (
              SELECT 1
                FROM compensation.salary_structure_statutory_evaluation newer
               WHERE newer.tenant_id = evaluation.tenant_id
                 AND newer.validation_id = evaluation.validation_id
                 AND (newer.created_at, newer.id) >
                     (evaluation.created_at, evaluation.id)
            )
       ) THEN
      RAISE EXCEPTION
        'salary-structure approval requires current passing statutory compatibility evidence for active bindings'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_statutory_approval_guard
  BEFORE UPDATE OF approval_status
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_statutory_approval();

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'salary_structure_statutory_state',
    'salary_structure_statutory_binding',
    'salary_structure_statutory_evaluation',
    'salary_structure_statutory_issue'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE compensation.%I ENABLE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'ALTER TABLE compensation.%I FORCE ROW LEVEL SECURITY',
      table_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON compensation.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      table_name
    );
  END LOOP;
END $$;

GRANT SELECT
  ON compensation.salary_structure_statutory_binding,
     compensation.salary_structure_statutory_evaluation,
     compensation.salary_structure_statutory_issue
  TO payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON compensation.salary_structure_statutory_state,
     compensation.salary_structure_statutory_binding,
     compensation.salary_structure_statutory_evaluation,
     compensation.salary_structure_statutory_issue
  FROM payroll_app;

REVOKE ALL ON FUNCTION
  compensation.bind_salary_structure_statutory_rule(
    uuid, uuid, uuid, uuid, varchar, varchar, uuid, varchar, timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  compensation.retire_salary_structure_statutory_binding(
    uuid, uuid, uuid, uuid, bigint, varchar, timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  compensation.salary_structure_statutory_compatibility_issues(
    uuid, uuid, uuid
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  compensation.evaluate_salary_structure_statutory_compatibility(
    uuid, uuid, uuid, uuid, varchar, timestamptz
  )
  FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
  compensation.bind_salary_structure_statutory_rule(
    uuid, uuid, uuid, uuid, varchar, varchar, uuid, varchar, timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  compensation.retire_salary_structure_statutory_binding(
    uuid, uuid, uuid, uuid, bigint, varchar, timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  compensation.salary_structure_statutory_compatibility_issues(
    uuid, uuid, uuid
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  compensation.evaluate_salary_structure_statutory_compatibility(
    uuid, uuid, uuid, uuid, varchar, timestamptz
  )
  TO payroll_app;

COMMENT ON TABLE statutory.statutory_rule_design_constraint IS
  'Jurisdiction-neutral legal design constraint attached to an exact statutory-rule version; no country value is hardcoded by this migration.';
COMMENT ON TABLE compensation.salary_structure_statutory_binding IS
  'Exact statutory-rule-version bindings for salary-structure design-time compatibility. Official payroll statutory calculation remains outside this table.';
COMMENT ON TABLE compensation.salary_structure_statutory_evaluation IS
  'Immutable design-time compatibility evidence tied to structural validation and the current statutory binding revision.';
