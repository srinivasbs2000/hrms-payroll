-- S4-02 employee statutory profile, eligibility and exemption assignment foundation.
--
-- V028 links an employee payroll relationship and exact payroll-assignment
-- version to approved statutory-rule versions. It remains jurisdiction-neutral:
-- no country rates, tax-base mappings, calculations, balances or remittance
-- data are introduced here.

CREATE TABLE statutory.employee_statutory_profile (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    payroll_relationship_id,
    jurisdiction_code,
    authority_code
  ),
  CHECK (jurisdiction_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CONSTRAINT employee_statutory_profile_relationship_fk
    FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE statutory.employee_statutory_profile_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  employee_statutory_profile_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  registration_status varchar(20) NOT NULL,
  classification_code varchar(60),
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
  UNIQUE (tenant_id, id, employee_statutory_profile_id),
  UNIQUE (
    tenant_id,
    employee_statutory_profile_id,
    version_sequence
  ),
  CHECK (version_sequence > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (registration_status IN (
    'NOT_REQUIRED',
    'PENDING',
    'REGISTERED',
    'EXEMPT'
  )),
  CHECK (
    classification_code IS NULL
    OR classification_code ~ '^[A-Z][A-Z0-9_]{1,59}$'
  ),
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
  CONSTRAINT employee_statutory_profile_version_identity_fk
    FOREIGN KEY (tenant_id, employee_statutory_profile_id)
    REFERENCES statutory.employee_statutory_profile(tenant_id, id),
  CONSTRAINT employee_statutory_profile_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      employee_statutory_profile_id
    ) REFERENCES statutory.employee_statutory_profile_version(
    tenant_id,
    id,
    employee_statutory_profile_id
  )
);

CREATE TABLE statutory.employee_statutory_rule_assignment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  employee_statutory_profile_id uuid NOT NULL,
  employee_statutory_profile_version_id uuid NOT NULL,
  payroll_assignment_id uuid NOT NULL,
  payroll_assignment_version_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  assignment_sequence integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  eligibility_status varchar(20) NOT NULL,
  exemption_status varchar(20) NOT NULL DEFAULT 'NONE',
  exemption_reason_code varchar(60),
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_assignment_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    employee_statutory_profile_id,
    payroll_assignment_id,
    statutory_rule_id
  ),
  UNIQUE (
    tenant_id,
    employee_statutory_profile_id,
    payroll_assignment_id,
    statutory_rule_id,
    assignment_sequence
  ),
  CHECK (assignment_sequence > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (eligibility_status IN (
    'ELIGIBLE',
    'INELIGIBLE',
    'CONDITIONAL'
  )),
  CHECK (exemption_status IN ('NONE', 'PARTIAL', 'FULL')),
  CHECK (
    (
      exemption_status = 'NONE'
      AND exemption_reason_code IS NULL
    )
    OR (
      exemption_status IN ('PARTIAL', 'FULL')
      AND exemption_reason_code IS NOT NULL
      AND exemption_reason_code ~ '^[A-Z][A-Z0-9_]{1,59}$'
    )
  ),
  CHECK (
    eligibility_status <> 'INELIGIBLE'
    OR exemption_status = 'NONE'
  ),
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
    supersedes_assignment_id IS NULL
    OR supersedes_assignment_id <> id
  ),
  CONSTRAINT employee_statutory_assignment_profile_version_fk
    FOREIGN KEY (
      tenant_id,
      employee_statutory_profile_version_id,
      employee_statutory_profile_id
    ) REFERENCES statutory.employee_statutory_profile_version(
    tenant_id,
    id,
    employee_statutory_profile_id
  ),
  CONSTRAINT employee_statutory_assignment_payroll_identity_fk
    FOREIGN KEY (tenant_id, payroll_assignment_id)
    REFERENCES employee_payroll.payroll_assignment(tenant_id, id),
  CONSTRAINT employee_statutory_assignment_payroll_version_fk
    FOREIGN KEY (
      tenant_id,
      payroll_assignment_version_id,
      payroll_assignment_id
    ) REFERENCES employee_payroll.payroll_assignment_version(
    tenant_id,
    id,
    payroll_assignment_id
  ),
  CONSTRAINT employee_statutory_assignment_rule_identity_fk
    FOREIGN KEY (tenant_id, statutory_rule_id)
    REFERENCES statutory.statutory_rule(tenant_id, id),
  CONSTRAINT employee_statutory_assignment_rule_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    ) REFERENCES statutory.statutory_rule_version(
    tenant_id,
    id,
    statutory_rule_id
  ),
  CONSTRAINT employee_statutory_assignment_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_assignment_id,
      employee_statutory_profile_id,
      payroll_assignment_id,
      statutory_rule_id
    ) REFERENCES statutory.employee_statutory_rule_assignment(
    tenant_id,
    id,
    employee_statutory_profile_id,
    payroll_assignment_id,
    statutory_rule_id
  )
);

ALTER TABLE statutory.employee_statutory_profile_version
  ADD CONSTRAINT employee_statutory_profile_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    employee_statutory_profile_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

ALTER TABLE statutory.employee_statutory_rule_assignment
  ADD CONSTRAINT employee_statutory_rule_assignment_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_assignment_id WITH =,
    statutory_rule_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX employee_statutory_profile_version_one_successor_uk
  ON statutory.employee_statutory_profile_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE UNIQUE INDEX employee_statutory_rule_assignment_one_successor_uk
  ON statutory.employee_statutory_rule_assignment(
    tenant_id,
    supersedes_assignment_id
  )
  WHERE supersedes_assignment_id IS NOT NULL;

CREATE INDEX employee_statutory_profile_relationship_ix
  ON statutory.employee_statutory_profile(
    tenant_id,
    payroll_relationship_id,
    jurisdiction_code,
    authority_code
  );

CREATE INDEX employee_statutory_profile_version_current_ix
  ON statutory.employee_statutory_profile_version(
    tenant_id,
    employee_statutory_profile_id,
    effective_from DESC
  );

CREATE INDEX employee_statutory_rule_assignment_lookup_ix
  ON statutory.employee_statutory_rule_assignment(
    tenant_id,
    payroll_assignment_id,
    statutory_rule_id,
    effective_from,
    effective_to
  );

CREATE OR REPLACE FUNCTION
  statutory.assert_employee_statutory_profile_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  identity_status varchar(20);
  parent_sequence integer;
BEGIN
  SELECT profile.status
  INTO identity_status
  FROM statutory.employee_statutory_profile profile
  WHERE profile.tenant_id = NEW.tenant_id
    AND profile.id = NEW.employee_statutory_profile_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION
      'employee statutory profile does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status <> 'ACTIVE' THEN
    RAISE EXCEPTION
      'employee statutory profile versions require an active identity'
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
      'employee statutory profile versions must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
         FROM statutory.employee_statutory_profile_version existing
         WHERE existing.tenant_id = NEW.tenant_id
           AND existing.employee_statutory_profile_id =
               NEW.employee_statutory_profile_id
       ) THEN
      RAISE EXCEPTION
        'first employee statutory profile version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION
        'later employee statutory profile versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;

    SELECT parent.version_sequence
    INTO parent_sequence
    FROM statutory.employee_statutory_profile_version parent
    WHERE parent.tenant_id = NEW.tenant_id
      AND parent.id = NEW.supersedes_version_id
      AND parent.employee_statutory_profile_id =
          NEW.employee_statutory_profile_id
    FOR UPDATE OF parent;

    IF parent_sequence IS NULL THEN
      RAISE EXCEPTION
        'superseded employee statutory profile version does not exist'
        USING ERRCODE = '23503';
    END IF;

    IF NEW.version_sequence <> parent_sequence + 1 THEN
      RAISE EXCEPTION
        'employee statutory profile version sequence must follow its parent'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER employee_statutory_profile_version_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    employee_statutory_profile_id,
    version_sequence,
    supersedes_version_id
  ON statutory.employee_statutory_profile_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.assert_employee_statutory_profile_version_dependencies();

CREATE OR REPLACE FUNCTION
  statutory.assert_employee_statutory_rule_assignment_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, employee_payroll, platform AS $$
DECLARE
  profile_status varchar(20);
  profile_approval varchar(20);
  profile_from date;
  profile_to date;
  profile_relationship_id uuid;
  profile_jurisdiction varchar(40);
  profile_authority varchar(60);
  assignment_status varchar(20);
  assignment_from date;
  assignment_to date;
  assignment_relationship_id uuid;
  rule_status varchar(20);
  rule_approval varchar(20);
  rule_from date;
  rule_to date;
  rule_jurisdiction varchar(40);
  rule_authority varchar(60);
  parent_sequence integer;
BEGIN
  SELECT
    profile.status,
    version.approval_status,
    version.effective_from,
    version.effective_to,
    profile.payroll_relationship_id,
    profile.jurisdiction_code,
    profile.authority_code
  INTO
    profile_status,
    profile_approval,
    profile_from,
    profile_to,
    profile_relationship_id,
    profile_jurisdiction,
    profile_authority
  FROM statutory.employee_statutory_profile_version version
  JOIN statutory.employee_statutory_profile profile
    ON profile.tenant_id = version.tenant_id
   AND profile.id = version.employee_statutory_profile_id
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.employee_statutory_profile_version_id
    AND version.employee_statutory_profile_id =
        NEW.employee_statutory_profile_id
  FOR UPDATE OF version;

  SELECT
    version.approval_status,
    version.assignment_start,
    version.assignment_end,
    identity.payroll_relationship_id
  INTO
    assignment_status,
    assignment_from,
    assignment_to,
    assignment_relationship_id
  FROM employee_payroll.payroll_assignment_version version
  JOIN employee_payroll.payroll_assignment identity
    ON identity.tenant_id = version.tenant_id
   AND identity.id = version.payroll_assignment_id
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.payroll_assignment_version_id
    AND version.payroll_assignment_id = NEW.payroll_assignment_id
  FOR UPDATE OF version;

  SELECT
    identity.status,
    version.approval_status,
    version.effective_from,
    version.effective_to,
    identity.jurisdiction_code,
    identity.authority_code
  INTO
    rule_status,
    rule_approval,
    rule_from,
    rule_to,
    rule_jurisdiction,
    rule_authority
  FROM statutory.statutory_rule_version version
  JOIN statutory.statutory_rule identity
    ON identity.tenant_id = version.tenant_id
   AND identity.id = version.statutory_rule_id
  WHERE version.tenant_id = NEW.tenant_id
    AND version.id = NEW.statutory_rule_version_id
    AND version.statutory_rule_id = NEW.statutory_rule_id
  FOR UPDATE OF version;

  IF profile_status IS NULL
     OR assignment_status IS NULL
     OR rule_status IS NULL THEN
    RAISE EXCEPTION
      'statutory assignment parent lineage is incomplete'
      USING ERRCODE = '23503';
  END IF;

  IF profile_status <> 'ACTIVE'
     OR profile_approval <> 'APPROVED'
     OR assignment_status <> 'APPROVED'
     OR rule_status <> 'ACTIVE'
     OR rule_approval <> 'APPROVED' THEN
    RAISE EXCEPTION
      'statutory assignments require active approved parent configuration'
      USING ERRCODE = '23514';
  END IF;

  IF profile_relationship_id <> assignment_relationship_id THEN
    RAISE EXCEPTION
      'statutory profile and payroll assignment must belong to the same relationship'
      USING ERRCODE = '23514';
  END IF;

  IF profile_jurisdiction <> rule_jurisdiction
     OR profile_authority <> rule_authority THEN
    RAISE EXCEPTION
      'statutory profile and rule jurisdiction/authority must match'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < profile_from
     OR NEW.effective_from < assignment_from
     OR NEW.effective_from < rule_from
     OR (
       profile_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > profile_to
       )
     )
     OR (
       assignment_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > assignment_to
       )
     )
     OR (
       rule_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > rule_to
       )
     ) THEN
    RAISE EXCEPTION
      'statutory assignment range must be contained by every exact parent version'
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
      'employee statutory rule assignments must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.assignment_sequence = 1 THEN
      IF NEW.supersedes_assignment_id IS NOT NULL
         OR EXISTS (
           SELECT 1
           FROM statutory.employee_statutory_rule_assignment existing
           WHERE existing.tenant_id = NEW.tenant_id
             AND existing.employee_statutory_profile_id =
                 NEW.employee_statutory_profile_id
             AND existing.payroll_assignment_id = NEW.payroll_assignment_id
             AND existing.statutory_rule_id = NEW.statutory_rule_id
         ) THEN
        RAISE EXCEPTION
          'first employee statutory rule assignment must start a new chain'
          USING ERRCODE = '23514';
      END IF;
    ELSE
      IF NEW.supersedes_assignment_id IS NULL THEN
        RAISE EXCEPTION
          'later employee statutory rule assignments must supersede the prior assignment'
          USING ERRCODE = '23514';
      END IF;

      SELECT parent.assignment_sequence
      INTO parent_sequence
      FROM statutory.employee_statutory_rule_assignment parent
      WHERE parent.tenant_id = NEW.tenant_id
        AND parent.id = NEW.supersedes_assignment_id
        AND parent.employee_statutory_profile_id =
            NEW.employee_statutory_profile_id
        AND parent.payroll_assignment_id = NEW.payroll_assignment_id
        AND parent.statutory_rule_id = NEW.statutory_rule_id
      FOR UPDATE OF parent;

      IF parent_sequence IS NULL THEN
        RAISE EXCEPTION
          'superseded employee statutory rule assignment does not exist'
          USING ERRCODE = '23503';
      END IF;

      IF NEW.assignment_sequence <> parent_sequence + 1 THEN
        RAISE EXCEPTION
          'employee statutory assignment sequence must follow its parent'
          USING ERRCODE = '23514';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER employee_statutory_rule_assignment_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    employee_statutory_profile_id,
    employee_statutory_profile_version_id,
    payroll_assignment_id,
    payroll_assignment_version_id,
    statutory_rule_id,
    statutory_rule_version_id,
    assignment_sequence,
    effective_from,
    effective_to,
    supersedes_assignment_id
  ON statutory.employee_statutory_rule_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.assert_employee_statutory_rule_assignment_dependencies();

REVOKE ALL ON FUNCTION
  statutory.assert_employee_statutory_profile_version_dependencies()
  FROM PUBLIC;

REVOKE ALL ON FUNCTION
  statutory.assert_employee_statutory_rule_assignment_dependencies()
  FROM PUBLIC;

CREATE TRIGGER employee_statutory_profile_version_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.employee_statutory_profile_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE TRIGGER employee_statutory_rule_assignment_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.employee_statutory_rule_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE OR REPLACE FUNCTION
  statutory.guard_employee_statutory_profile_version_end_date()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
BEGIN
  IF NEW.effective_to IS DISTINCT FROM OLD.effective_to
     AND NEW.effective_to IS NOT NULL
     AND EXISTS (
       SELECT 1
       FROM statutory.employee_statutory_rule_assignment assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.employee_statutory_profile_version_id = OLD.id
         AND assignment.approval_status = 'APPROVED'
         AND (
           assignment.effective_to IS NULL
           OR assignment.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'employee statutory profile version has assignments beyond the requested end date'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER employee_statutory_profile_version_assignment_guard
  BEFORE UPDATE OF effective_to
  ON statutory.employee_statutory_profile_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.guard_employee_statutory_profile_version_end_date();

CREATE OR REPLACE FUNCTION
  statutory.guard_statutory_rule_version_assignment_end_date()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
BEGIN
  IF NEW.effective_to IS DISTINCT FROM OLD.effective_to
     AND NEW.effective_to IS NOT NULL
     AND EXISTS (
       SELECT 1
       FROM statutory.employee_statutory_rule_assignment assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.statutory_rule_version_id = OLD.id
         AND assignment.approval_status = 'APPROVED'
         AND (
           assignment.effective_to IS NULL
           OR assignment.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'statutory rule version has employee assignments beyond the requested end date'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER statutory_rule_version_employee_assignment_guard
  BEFORE UPDATE OF effective_to
  ON statutory.statutory_rule_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.guard_statutory_rule_version_assignment_end_date();

CREATE OR REPLACE FUNCTION
  statutory.guard_payroll_assignment_version_statutory_end_date()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, employee_payroll, platform AS $$
BEGIN
  IF NEW.assignment_end IS DISTINCT FROM OLD.assignment_end
     AND NEW.assignment_end IS NOT NULL
     AND EXISTS (
       SELECT 1
       FROM statutory.employee_statutory_rule_assignment assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.payroll_assignment_version_id = OLD.id
         AND assignment.approval_status = 'APPROVED'
         AND (
           assignment.effective_to IS NULL
           OR assignment.effective_to > NEW.assignment_end
         )
     ) THEN
    RAISE EXCEPTION
      'payroll assignment version has statutory assignments beyond the requested end date'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_assignment_version_statutory_guard
  BEFORE UPDATE OF assignment_end
  ON employee_payroll.payroll_assignment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.guard_payroll_assignment_version_statutory_end_date();

REVOKE ALL ON FUNCTION
  statutory.guard_employee_statutory_profile_version_end_date()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  statutory.guard_statutory_rule_version_assignment_end_date()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  statutory.guard_payroll_assignment_version_statutory_end_date()
  FROM PUBLIC;

CREATE OR REPLACE FUNCTION
  statutory.approve_employee_statutory_profile_version(
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

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.employee_statutory_profile_version version
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = version.version_no + 1
  FROM statutory.employee_statutory_profile profile
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.employee_statutory_profile_id = profile.id
    AND profile.tenant_id = version.tenant_id
    AND profile.status = 'ACTIVE'
    AND version.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.employee_statutory_profile_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  statutory.end_date_employee_statutory_profile_version(
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

  UPDATE statutory.employee_statutory_profile_version version
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version.version_no + 1
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

CREATE OR REPLACE FUNCTION
  statutory.approve_employee_statutory_rule_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, employee_payroll, platform AS $$
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
  FROM statutory.employee_statutory_rule_assignment assignment
  JOIN statutory.employee_statutory_profile_version profile_version
    ON profile_version.tenant_id = assignment.tenant_id
   AND profile_version.id =
       assignment.employee_statutory_profile_version_id
   AND profile_version.employee_statutory_profile_id =
       assignment.employee_statutory_profile_id
  JOIN statutory.employee_statutory_profile profile
    ON profile.tenant_id = profile_version.tenant_id
   AND profile.id = profile_version.employee_statutory_profile_id
  JOIN employee_payroll.payroll_assignment_version payroll_version
    ON payroll_version.tenant_id = assignment.tenant_id
   AND payroll_version.id = assignment.payroll_assignment_version_id
   AND payroll_version.payroll_assignment_id =
       assignment.payroll_assignment_id
  JOIN employee_payroll.payroll_assignment payroll_identity
    ON payroll_identity.tenant_id = payroll_version.tenant_id
   AND payroll_identity.id = payroll_version.payroll_assignment_id
  JOIN statutory.statutory_rule_version rule_version
    ON rule_version.tenant_id = assignment.tenant_id
   AND rule_version.id = assignment.statutory_rule_version_id
   AND rule_version.statutory_rule_id = assignment.statutory_rule_id
  JOIN statutory.statutory_rule rule_identity
    ON rule_identity.tenant_id = rule_version.tenant_id
   AND rule_identity.id = rule_version.statutory_rule_id
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.approval_status = 'DRAFT'
    AND profile.status = 'ACTIVE'
    AND profile_version.approval_status = 'APPROVED'
    AND payroll_version.approval_status = 'APPROVED'
    AND rule_identity.status = 'ACTIVE'
    AND rule_version.approval_status = 'APPROVED'
    AND profile.payroll_relationship_id =
        payroll_identity.payroll_relationship_id
    AND profile.jurisdiction_code = rule_identity.jurisdiction_code
    AND profile.authority_code = rule_identity.authority_code
    AND assignment.effective_from >= profile_version.effective_from
    AND assignment.effective_from >= payroll_version.assignment_start
    AND assignment.effective_from >= rule_version.effective_from
    AND (
      profile_version.effective_to IS NULL
      OR (
        assignment.effective_to IS NOT NULL
        AND assignment.effective_to <= profile_version.effective_to
      )
    )
    AND (
      payroll_version.assignment_end IS NULL
      OR (
        assignment.effective_to IS NOT NULL
        AND assignment.effective_to <= payroll_version.assignment_end
      )
    )
    AND (
      rule_version.effective_to IS NULL
      OR (
        assignment.effective_to IS NOT NULL
        AND assignment.effective_to <= rule_version.effective_to
      )
    )
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.employee_statutory_rule_assignment successor
      WHERE successor.tenant_id = assignment.tenant_id
        AND successor.supersedes_assignment_id = assignment.id
    )
  FOR UPDATE OF assignment;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.employee_statutory_rule_assignment assignment
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = assignment.version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.employee_statutory_rule_assignment successor
      WHERE successor.tenant_id = assignment.tenant_id
        AND successor.supersedes_assignment_id = assignment.id
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  statutory.end_date_employee_statutory_rule_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
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

  UPDATE statutory.employee_statutory_rule_assignment assignment
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = assignment.version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.approval_status = 'APPROVED'
    AND assignment.version_no = p_expected_version
    AND assignment.effective_from < p_effective_to
    AND (
      assignment.effective_to IS NULL
      OR assignment.effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'employee_statutory_profile',
    'employee_statutory_profile_version',
    'employee_statutory_rule_assignment'
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

REVOKE ALL ON FUNCTION
  statutory.approve_employee_statutory_profile_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) FROM PUBLIC;
REVOKE ALL ON FUNCTION
  statutory.end_date_employee_statutory_profile_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) FROM PUBLIC;
REVOKE ALL ON FUNCTION
  statutory.approve_employee_statutory_rule_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) FROM PUBLIC;
REVOKE ALL ON FUNCTION
  statutory.end_date_employee_statutory_rule_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) FROM PUBLIC;

GRANT SELECT, INSERT
  ON statutory.employee_statutory_profile,
     statutory.employee_statutory_profile_version,
     statutory.employee_statutory_rule_assignment
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON statutory.employee_statutory_profile,
     statutory.employee_statutory_profile_version,
     statutory.employee_statutory_rule_assignment
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION
  statutory.approve_employee_statutory_profile_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) TO payroll_app;
GRANT EXECUTE ON FUNCTION
  statutory.end_date_employee_statutory_profile_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) TO payroll_app;
GRANT EXECUTE ON FUNCTION
  statutory.approve_employee_statutory_rule_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  ) TO payroll_app;
GRANT EXECUTE ON FUNCTION
  statutory.end_date_employee_statutory_rule_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  ) TO payroll_app;

COMMENT ON TABLE statutory.employee_statutory_profile IS
  'Stable employee statutory identity for one relationship, jurisdiction and authority.';
COMMENT ON TABLE statutory.employee_statutory_profile_version IS
  'Approved effective-dated registration and classification state for an employee statutory profile.';
COMMENT ON TABLE statutory.employee_statutory_rule_assignment IS
  'Exact employee profile, payroll assignment and statutory-rule version lineage with rule-specific eligibility and exemption state.';
