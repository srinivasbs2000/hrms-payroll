-- S2-04 salary-structure identity/version foundation.
--
-- Preserve the Sprint 0 salary_structure UUID as the exact historical version
-- identifier. Existing structure lines and employee salary assignments continue
-- to reference that UUID while a new stable salary-structure identity is added.

-- V011 forced RLS on all existing payroll tables. Temporarily allow the
-- migration owner to validate and backfill all tenants. Policies remain present
-- and FORCE RLS is restored before runtime grants are finalized.
ALTER TABLE compensation.salary_structure
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment
  NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM compensation.salary_structure
    WHERE btrim(name) = ''
       OR currency <> 'INR'
  ) THEN
    RAISE EXCEPTION
      'existing salary structures require a non-blank name and INR currency';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM compensation.salary_structure_line line
    JOIN compensation.salary_structure structure
      ON structure.tenant_id = line.tenant_id
     AND structure.id = line.structure_id
    WHERE line.sequence_no < 1
       OR line.effective_from < structure.effective_from
       OR (
         structure.effective_to IS NOT NULL
         AND (
           line.effective_to IS NULL
           OR line.effective_to > structure.effective_to
         )
       )
       OR NOT (
         (
           line.target_amount IS NOT NULL
           AND line.target_amount >= 0
           AND line.target_percentage IS NULL
           AND line.percentage_base_code IS NULL
         )
         OR (
           line.target_amount IS NULL
           AND line.target_percentage IS NOT NULL
           AND line.target_percentage > 0
           AND line.target_percentage <= 100
           AND line.percentage_base_code IS NOT NULL
         )
         OR (
           line.target_amount IS NULL
           AND line.target_percentage IS NULL
           AND line.percentage_base_code IS NULL
         )
       )
       OR NOT EXISTS (
         SELECT 1
         FROM compensation.pay_component_version component_version
         WHERE component_version.tenant_id = line.tenant_id
           AND component_version.id = line.component_version_id
           AND component_version.approval_status = 'APPROVED'
           AND line.effective_from >= component_version.effective_from
           AND (
             component_version.effective_to IS NULL
             OR (
               line.effective_to IS NOT NULL
               AND line.effective_to <= component_version.effective_to
             )
           )
       )
       OR (
         line.percentage_base_code IS NOT NULL
         AND (
           NOT EXISTS (
             SELECT 1
             FROM compensation.salary_structure_line base_line
             JOIN compensation.pay_component_version base_version
               ON base_version.tenant_id = base_line.tenant_id
              AND base_version.id = base_line.component_version_id
             JOIN compensation.pay_component base_component
               ON base_component.tenant_id = base_version.tenant_id
              AND base_component.id = base_version.component_id
             WHERE base_line.tenant_id = line.tenant_id
               AND base_line.structure_id = line.structure_id
               AND base_line.sequence_no < line.sequence_no
               AND base_component.code = line.percentage_base_code
           )
           OR EXISTS (
             SELECT 1
             FROM compensation.pay_component_version current_version
             JOIN compensation.pay_component current_component
               ON current_component.tenant_id = current_version.tenant_id
              AND current_component.id = current_version.component_id
             WHERE current_version.tenant_id = line.tenant_id
               AND current_version.id = line.component_version_id
               AND current_component.code = line.percentage_base_code
           )
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing salary-structure lines do not satisfy S2-04 invariants';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.salary_assignment assignment
    JOIN compensation.salary_structure structure
      ON structure.tenant_id = assignment.tenant_id
     AND structure.id = assignment.salary_structure_id
    WHERE assignment.currency <> structure.currency
       OR assignment.effective_from < structure.effective_from
       OR (
         structure.effective_to IS NOT NULL
         AND (
           assignment.effective_to IS NULL
           OR assignment.effective_to > structure.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing salary assignments exceed their salary-structure range or currency';
  END IF;
END $$;

ALTER TABLE compensation.salary_structure
  RENAME TO salary_structure_version;

ALTER TABLE compensation.salary_structure_line
  RENAME COLUMN structure_id TO salary_structure_version_id;

ALTER TABLE employee_payroll.salary_assignment
  RENAME COLUMN salary_structure_id TO salary_structure_version_id;

CREATE TABLE compensation.salary_structure (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

ALTER TABLE compensation.salary_structure_version
  ADD COLUMN salary_structure_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO compensation.salary_structure(
  id,
  tenant_id,
  code,
  created_at,
  created_by,
  updated_at,
  updated_by
)
SELECT
  gen_random_uuid(),
  tenant_id,
  code,
  created_at,
  created_by,
  updated_at,
  updated_by
FROM compensation.salary_structure_version;

UPDATE compensation.salary_structure_version version
SET salary_structure_id = identity.id,
    approved_at = version.created_at,
    approved_by = version.created_by
FROM compensation.salary_structure identity
WHERE identity.tenant_id = version.tenant_id
  AND identity.code = version.code;

ALTER TABLE compensation.salary_structure_version
  ALTER COLUMN salary_structure_id SET NOT NULL,
  DROP CONSTRAINT salary_structure_tenant_id_code_key,
  DROP COLUMN code,
  ADD CONSTRAINT salary_structure_version_identity_fk
    FOREIGN KEY (tenant_id, salary_structure_id)
    REFERENCES compensation.salary_structure(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_identity_sequence_uk
    UNIQUE (tenant_id, salary_structure_id, version_sequence),
  ADD CONSTRAINT salary_structure_version_identity_id_uk
    UNIQUE (tenant_id, id, salary_structure_id),
  ADD CONSTRAINT salary_structure_version_sequence_ck
    CHECK (version_sequence > 0),
  ADD CONSTRAINT salary_structure_version_name_not_blank_ck
    CHECK (btrim(name) <> ''),
  ADD CONSTRAINT salary_structure_version_currency_ck
    CHECK (currency = 'INR'),
  ADD CONSTRAINT salary_structure_version_approval_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT salary_structure_version_approval_metadata_ck
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
  ADD CONSTRAINT salary_structure_version_supersedes_self_ck
    CHECK (
      supersedes_version_id IS NULL
      OR supersedes_version_id <> id
    ),
  ADD CONSTRAINT salary_structure_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      salary_structure_id
    )
    REFERENCES compensation.salary_structure_version(
      tenant_id,
      id,
      salary_structure_id
    );

ALTER TABLE compensation.salary_structure_version
  ADD CONSTRAINT salary_structure_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    salary_structure_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

ALTER TABLE compensation.salary_structure_line
  ADD CONSTRAINT salary_structure_line_sequence_ck
    CHECK (sequence_no > 0),
  ADD CONSTRAINT salary_structure_line_target_shape_ck
    CHECK (
      (
        target_amount IS NOT NULL
        AND target_amount >= 0
        AND target_percentage IS NULL
        AND percentage_base_code IS NULL
      )
      OR (
        target_amount IS NULL
        AND target_percentage IS NOT NULL
        AND target_percentage > 0
        AND target_percentage <= 100
        AND percentage_base_code IS NOT NULL
      )
      OR (
        target_amount IS NULL
        AND target_percentage IS NULL
        AND percentage_base_code IS NULL
      )
    ),
  ADD CONSTRAINT salary_structure_line_base_component_fk
    FOREIGN KEY (tenant_id, percentage_base_code)
    REFERENCES compensation.pay_component(tenant_id, code);

CREATE INDEX salary_structure_version_current_ix
  ON compensation.salary_structure_version(
    tenant_id,
    salary_structure_id,
    effective_from DESC
  );

CREATE INDEX salary_structure_line_version_ix
  ON compensation.salary_structure_line(
    tenant_id,
    salary_structure_version_id,
    sequence_no
  );

CREATE INDEX salary_structure_line_component_ix
  ON compensation.salary_structure_line(
    tenant_id,
    component_version_id,
    effective_from
  );

CREATE INDEX salary_assignment_structure_version_ix
  ON employee_payroll.salary_assignment(
    tenant_id,
    salary_structure_version_id,
    effective_from
  );

CREATE OR REPLACE FUNCTION
  compensation.assert_salary_structure_line_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  structure_from date;
  structure_to date;
  structure_status varchar;
  component_from date;
  component_to date;
  component_status varchar;
  component_code varchar;
BEGIN
  SELECT
    version.effective_from,
    version.effective_to,
    version.approval_status
  INTO
    structure_from,
    structure_to,
    structure_status
  FROM compensation.salary_structure_version version
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.salary_structure_version_id;

  IF structure_from IS NULL THEN
    RAISE EXCEPTION
      'salary-structure version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF NEW.effective_from < structure_from
     OR (
       structure_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > structure_to
       )
     ) THEN
    RAISE EXCEPTION
      'salary-structure line range must be contained by its version'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT'
     AND structure_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'salary-structure lines can be inserted only into a draft version'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT'
     AND EXISTS (
       SELECT 1
       FROM compensation.salary_structure_version successor
       WHERE successor.tenant_id = NEW.tenant_id
         AND successor.supersedes_version_id =
           NEW.salary_structure_version_id
     ) THEN
    RAISE EXCEPTION
      'salary-structure lines cannot be added to a superseded draft'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    component_version.effective_from,
    component_version.effective_to,
    component_version.approval_status,
    component.code
  INTO
    component_from,
    component_to,
    component_status,
    component_code
  FROM compensation.pay_component_version component_version
  JOIN compensation.pay_component component
    ON component.tenant_id = component_version.tenant_id
   AND component.id = component_version.component_id
  WHERE component_version.tenant_id = NEW.tenant_id
    AND component_version.id = NEW.component_version_id;

  IF component_from IS NULL THEN
    RAISE EXCEPTION
      'pay-component version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF component_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'salary-structure lines require an approved pay-component version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < component_from
     OR (
       component_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > component_to
       )
     ) THEN
    RAISE EXCEPTION
      'salary-structure line range must be contained by its component version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.percentage_base_code = component_code THEN
    RAISE EXCEPTION
      'percentage base component must differ from the line component'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_line_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    salary_structure_version_id,
    component_version_id,
    percentage_base_code,
    effective_from,
    effective_to
  ON compensation.salary_structure_line
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.assert_salary_structure_line_dependencies();

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_salary_assignment_structure_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  structure_from date;
  structure_to date;
  structure_currency char(3);
  structure_status varchar;
BEGIN
  SELECT
    version.effective_from,
    version.effective_to,
    version.currency,
    version.approval_status
  INTO
    structure_from,
    structure_to,
    structure_currency,
    structure_status
  FROM compensation.salary_structure_version version
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.salary_structure_version_id;

  IF structure_from IS NULL THEN
    RAISE EXCEPTION
      'salary-structure version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF structure_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'salary assignments require an approved salary-structure version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.currency <> structure_currency THEN
    RAISE EXCEPTION
      'salary-assignment currency must match the salary-structure version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < structure_from
     OR (
       structure_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > structure_to
       )
     ) THEN
    RAISE EXCEPTION
      'salary-assignment range must be contained by its structure version'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_assignment_structure_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    salary_structure_version_id,
    currency,
    effective_from,
    effective_to
  ON employee_payroll.salary_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.assert_salary_assignment_structure_dependencies();

CREATE OR REPLACE FUNCTION
  compensation.require_salary_structure_draft_insert()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner'
     AND NEW.approval_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'runtime salary-structure versions must be created as drafts'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_version_draft_insert
  BEFORE INSERT
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.require_salary_structure_draft_insert();

CREATE OR REPLACE FUNCTION
  compensation.reject_uncontrolled_salary_structure_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION
      'immutable salary-structure configuration: %.%',
      TG_TABLE_SCHEMA,
      TG_TABLE_NAME;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION
      'salary-structure versions and lines cannot be deleted';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_version_immutable
  BEFORE UPDATE OR DELETE
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.reject_uncontrolled_salary_structure_mutation();

CREATE TRIGGER salary_structure_line_immutable
  BEFORE UPDATE OR DELETE
  ON compensation.salary_structure_line
  FOR EACH ROW
  EXECUTE FUNCTION
    compensation.reject_uncontrolled_salary_structure_mutation();

CREATE OR REPLACE FUNCTION compensation.approve_salary_structure_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
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

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_approved_at IS NULL THEN
    RAISE EXCEPTION 'approval timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  UPDATE compensation.salary_structure_version version
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
      FROM compensation.salary_structure_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
    AND EXISTS (
      SELECT 1
      FROM compensation.salary_structure_line line
      WHERE line.tenant_id = version.tenant_id
        AND line.salary_structure_version_id = version.id
    )
    AND NOT EXISTS (
      SELECT 1
      FROM compensation.salary_structure_line line
      LEFT JOIN compensation.pay_component_version component_version
        ON component_version.tenant_id = line.tenant_id
       AND component_version.id = line.component_version_id
      WHERE line.tenant_id = version.tenant_id
        AND line.salary_structure_version_id = version.id
        AND (
          component_version.id IS NULL
          OR component_version.approval_status <> 'APPROVED'
          OR line.effective_from < version.effective_from
          OR (
            version.effective_to IS NOT NULL
            AND (
              line.effective_to IS NULL
              OR line.effective_to > version.effective_to
            )
          )
          OR line.effective_from < component_version.effective_from
          OR (
            component_version.effective_to IS NOT NULL
            AND (
              line.effective_to IS NULL
              OR line.effective_to > component_version.effective_to
            )
          )
          OR (
            line.percentage_base_code IS NOT NULL
            AND NOT EXISTS (
              SELECT 1
              FROM compensation.salary_structure_line base_line
              JOIN compensation.pay_component_version base_version
                ON base_version.tenant_id = base_line.tenant_id
               AND base_version.id = base_line.component_version_id
              JOIN compensation.pay_component base_component
                ON base_component.tenant_id = base_version.tenant_id
               AND base_component.id = base_version.component_id
              WHERE base_line.tenant_id = line.tenant_id
                AND base_line.salary_structure_version_id =
                  line.salary_structure_version_id
                AND base_line.sequence_no < line.sequence_no
                AND base_component.code = line.percentage_base_code
            )
          )
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.end_date_salary_structure_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_effective_to date,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  compensation,
  employee_payroll,
  platform AS $$
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

  UPDATE compensation.salary_structure_version version
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.version_no = p_expected_version
    AND version.effective_from < p_effective_to
    AND (
      version.effective_to IS NULL
      OR version.effective_to > p_effective_to
    )
    AND NOT EXISTS (
      SELECT 1
      FROM compensation.salary_structure_line line
      WHERE line.tenant_id = version.tenant_id
        AND line.salary_structure_version_id = version.id
        AND line.effective_from >= p_effective_to
    )
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.salary_assignment assignment
      WHERE assignment.tenant_id = version.tenant_id
        AND assignment.salary_structure_version_id = version.id
        AND (
          assignment.effective_from >= p_effective_to
          OR assignment.effective_to IS NULL
          OR assignment.effective_to > p_effective_to
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE compensation.salary_structure_line
    SET effective_to = p_effective_to,
        updated_at = p_changed_at,
        updated_by = p_actor,
        version_no = version_no + 1
    WHERE tenant_id = p_tenant_id
      AND salary_structure_version_id = p_version_id
      AND (
        effective_to IS NULL
        OR effective_to > p_effective_to
      );
  END IF;

  RETURN affected;
END $$;

-- Component versions cannot be shortened beneath an approved or draft
-- salary-structure line that preserves their exact calculation lineage.
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
SET search_path = pg_catalog, compensation, platform AS $$
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

  UPDATE compensation.pay_component_version component_version
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE component_version.tenant_id = p_tenant_id
    AND component_version.id = p_version_id
    AND component_version.version_no = p_expected_version
    AND component_version.effective_from < p_effective_to
    AND (
      component_version.effective_to IS NULL
      OR component_version.effective_to > p_effective_to
    )
    AND NOT EXISTS (
      SELECT 1
      FROM compensation.salary_structure_line line
      WHERE line.tenant_id = component_version.tenant_id
        AND line.component_version_id = component_version.id
        AND (
          line.effective_from >= p_effective_to
          OR line.effective_to IS NULL
          OR line.effective_to > p_effective_to
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.approve_salary_structure_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION compensation.end_date_salary_structure_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.approve_salary_structure_version(
  uuid,
  uuid,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION compensation.end_date_salary_structure_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

ALTER TABLE compensation.salary_structure
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure
  FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
  ON compensation.salary_structure;
CREATE POLICY tenant_isolation
  ON compensation.salary_structure
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE compensation.salary_structure_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line
  FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component
  FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment
  FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT
  ON compensation.salary_structure,
     compensation.salary_structure_version,
     compensation.salary_structure_line
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON compensation.salary_structure,
     compensation.salary_structure_version,
     compensation.salary_structure_line
  FROM payroll_app;

REVOKE CREATE ON SCHEMA compensation FROM payroll_app;

COMMENT ON TABLE compensation.salary_structure IS
  'Stable tenant-scoped salary-structure identity.';

COMMENT ON TABLE compensation.salary_structure_version IS
  'Immutable effective-dated salary-structure header and approval state.';

COMMENT ON TABLE compensation.salary_structure_line IS
  'Immutable ordered component lines belonging to an exact structure version.';

COMMENT ON COLUMN
  compensation.salary_structure_line.salary_structure_version_id IS
  'Exact salary-structure version used for calculation lineage.';

COMMENT ON COLUMN
  employee_payroll.salary_assignment.salary_structure_version_id IS
  'Exact approved salary-structure version assigned to the employee.';