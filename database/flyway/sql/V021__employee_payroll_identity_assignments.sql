-- S2-05A employee-payroll identity, profile and assignment foundation.
--
-- Preserve the Sprint 0 payroll_relationship and payroll_assignment UUIDs as
-- exact historical version identifiers. Downstream payroll population,
-- snapshots and results continue to point at those UUIDs while stable
-- relationship and assignment identities are introduced.

-- V011 forced RLS on all existing payroll tables. Temporarily allow the
-- migration owner to validate and backfill every tenant. Existing policies
-- remain installed and FORCE RLS is restored before runtime grants are set.
ALTER TABLE organisation.legal_entity_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.establishment_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.employee_payroll_profile
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.pay_group_assignment
  NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment
  NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM employee_payroll.payroll_relationship relationship
    LEFT JOIN organisation.legal_entity_version legal_version
      ON legal_version.tenant_id = relationship.tenant_id
     AND legal_version.id = relationship.legal_entity_id
    WHERE btrim(relationship.external_employee_id) = ''
       OR btrim(relationship.employee_number) = ''
       OR (
         relationship.relationship_end IS NOT NULL
         AND relationship.relationship_end <=
           relationship.relationship_start
       )
       OR legal_version.id IS NULL
       OR legal_version.approval_status <> 'APPROVED'
       OR relationship.relationship_start < legal_version.effective_from
       OR (
         legal_version.effective_to IS NOT NULL
         AND (
           relationship.relationship_end IS NULL
           OR relationship.relationship_end > legal_version.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing payroll relationships do not satisfy S2-05A lineage invariants';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.payroll_assignment assignment
    JOIN employee_payroll.payroll_relationship relationship
      ON relationship.tenant_id = assignment.tenant_id
     AND relationship.id = assignment.payroll_relationship_id
    LEFT JOIN organisation.establishment_version establishment
      ON establishment.tenant_id = assignment.tenant_id
     AND establishment.id = assignment.establishment_id
    LEFT JOIN organisation.payroll_statutory_unit_version psu
      ON psu.tenant_id = establishment.tenant_id
     AND psu.id = establishment.payroll_statutory_unit_version_id
    WHERE btrim(assignment.assignment_number) = ''
       OR (
         assignment.assignment_end IS NOT NULL
         AND assignment.assignment_end <= assignment.assignment_start
       )
       OR establishment.id IS NULL
       OR establishment.approval_status <> 'APPROVED'
       OR psu.id IS NULL
       OR psu.legal_entity_version_id <> relationship.legal_entity_id
       OR assignment.assignment_start < relationship.relationship_start
       OR (
         relationship.relationship_end IS NOT NULL
         AND (
           assignment.assignment_end IS NULL
           OR assignment.assignment_end > relationship.relationship_end
         )
       )
       OR assignment.assignment_start < establishment.effective_from
       OR (
         establishment.effective_to IS NOT NULL
         AND (
           assignment.assignment_end IS NULL
           OR assignment.assignment_end > establishment.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing payroll assignments do not satisfy S2-05A lineage invariants';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.employee_payroll_profile
    WHERE currency <> 'INR'
       OR payroll_status NOT IN (
         'INCOMPLETE',
         'READY',
         'ON_HOLD',
         'INACTIVE'
       )
  ) THEN
    RAISE EXCEPTION
      'existing employee payroll profiles require INR and a supported status';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.pay_group_assignment group_assignment
    JOIN employee_payroll.payroll_assignment assignment
      ON assignment.tenant_id = group_assignment.tenant_id
     AND assignment.id = group_assignment.payroll_assignment_id
    JOIN organisation.establishment_version establishment
      ON establishment.tenant_id = assignment.tenant_id
     AND establishment.id = assignment.establishment_id
    LEFT JOIN organisation.pay_group_version group_version
      ON group_version.tenant_id = group_assignment.tenant_id
     AND group_version.id = group_assignment.pay_group_id
    WHERE group_version.id IS NULL
       OR group_version.approval_status <> 'APPROVED'
       OR group_version.payroll_statutory_unit_version_id <>
          establishment.payroll_statutory_unit_version_id
       OR group_assignment.effective_from < assignment.assignment_start
       OR (
         assignment.assignment_end IS NOT NULL
         AND (
           group_assignment.effective_to IS NULL
           OR group_assignment.effective_to > assignment.assignment_end
         )
       )
       OR group_assignment.effective_from < group_version.effective_from
       OR (
         group_version.effective_to IS NOT NULL
         AND (
           group_assignment.effective_to IS NULL
           OR group_assignment.effective_to > group_version.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing pay-group assignments do not satisfy S2-05A lineage invariants';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.salary_assignment salary_assignment
    JOIN employee_payroll.payroll_assignment assignment
      ON assignment.tenant_id = salary_assignment.tenant_id
     AND assignment.id = salary_assignment.payroll_assignment_id
    LEFT JOIN compensation.salary_structure_version structure_version
      ON structure_version.tenant_id = salary_assignment.tenant_id
     AND structure_version.id =
       salary_assignment.salary_structure_version_id
    WHERE structure_version.id IS NULL
       OR structure_version.approval_status <> 'APPROVED'
       OR salary_assignment.currency <> 'INR'
       OR salary_assignment.currency <> structure_version.currency
       OR salary_assignment.effective_from < assignment.assignment_start
       OR (
         assignment.assignment_end IS NOT NULL
         AND (
           salary_assignment.effective_to IS NULL
           OR salary_assignment.effective_to > assignment.assignment_end
         )
       )
       OR salary_assignment.effective_from <
          structure_version.effective_from
       OR (
         structure_version.effective_to IS NOT NULL
         AND (
           salary_assignment.effective_to IS NULL
           OR salary_assignment.effective_to >
             structure_version.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'existing salary assignments do not satisfy S2-05A lineage invariants';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.pay_group_assignment left_assignment
    JOIN employee_payroll.pay_group_assignment right_assignment
      ON right_assignment.tenant_id = left_assignment.tenant_id
     AND right_assignment.payroll_assignment_id =
       left_assignment.payroll_assignment_id
     AND right_assignment.id > left_assignment.id
     AND daterange(
       right_assignment.effective_from,
       right_assignment.effective_to,
       '[)'
     ) && daterange(
       left_assignment.effective_from,
       left_assignment.effective_to,
       '[)'
     )
  ) THEN
    RAISE EXCEPTION
      'existing pay-group assignments contain overlapping effective ranges';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.salary_assignment left_assignment
    JOIN employee_payroll.salary_assignment right_assignment
      ON right_assignment.tenant_id = left_assignment.tenant_id
     AND right_assignment.payroll_assignment_id =
       left_assignment.payroll_assignment_id
     AND right_assignment.id > left_assignment.id
     AND daterange(
       right_assignment.effective_from,
       right_assignment.effective_to,
       '[)'
     ) && daterange(
       left_assignment.effective_from,
       left_assignment.effective_to,
       '[)'
     )
  ) THEN
    RAISE EXCEPTION
      'existing salary assignments contain overlapping effective ranges';
  END IF;
END $$;

ALTER TABLE employee_payroll.payroll_relationship
  RENAME TO payroll_relationship_version;
ALTER TABLE employee_payroll.payroll_assignment
  RENAME TO payroll_assignment_version;

ALTER TABLE employee_payroll.payroll_relationship_version
  RENAME COLUMN legal_entity_id TO legal_entity_version_id;
ALTER TABLE employee_payroll.payroll_assignment_version
  RENAME COLUMN payroll_relationship_id
  TO payroll_relationship_version_id;
ALTER TABLE employee_payroll.payroll_assignment_version
  RENAME COLUMN establishment_id TO establishment_version_id;

ALTER TABLE employee_payroll.pay_group_assignment
  RENAME COLUMN payroll_assignment_id
  TO payroll_assignment_version_id;
ALTER TABLE employee_payroll.pay_group_assignment
  RENAME COLUMN pay_group_id TO pay_group_version_id;
ALTER TABLE employee_payroll.salary_assignment
  RENAME COLUMN payroll_assignment_id
  TO payroll_assignment_version_id;

ALTER TABLE payroll_ops.population_member
  RENAME COLUMN payroll_assignment_id
  TO payroll_assignment_version_id;
ALTER TABLE payroll_ops.input_snapshot
  RENAME COLUMN payroll_assignment_id
  TO payroll_assignment_version_id;
ALTER TABLE payroll_calc.payroll_result
  RENAME COLUMN payroll_assignment_id
  TO payroll_assignment_version_id;

CREATE TABLE employee_payroll.payroll_relationship (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  external_employee_id varchar(100) NOT NULL,
  employee_number varchar(60) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, employee_number),
  CHECK (btrim(external_employee_id) <> ''),
  CHECK (btrim(employee_number) <> ''),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

ALTER TABLE employee_payroll.payroll_relationship_version
  ADD COLUMN payroll_relationship_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO employee_payroll.payroll_relationship(
  id,
  tenant_id,
  external_employee_id,
  employee_number,
  status,
  created_at,
  created_by,
  updated_at,
  updated_by,
  version_no
)
SELECT
  gen_random_uuid(),
  tenant_id,
  external_employee_id,
  employee_number,
  'ACTIVE',
  created_at,
  created_by,
  updated_at,
  updated_by,
  0
FROM employee_payroll.payroll_relationship_version;

UPDATE employee_payroll.payroll_relationship_version version
SET payroll_relationship_id = identity.id,
    approved_at = version.created_at,
    approved_by = version.created_by
FROM employee_payroll.payroll_relationship identity
WHERE identity.tenant_id = version.tenant_id
  AND identity.employee_number = version.employee_number;

ALTER TABLE employee_payroll.payroll_relationship_version
  ALTER COLUMN payroll_relationship_id SET NOT NULL,
  DROP CONSTRAINT payroll_relationship_tenant_id_employee_number_key,
  DROP COLUMN external_employee_id,
  DROP COLUMN employee_number,
  ADD CONSTRAINT payroll_relationship_version_identity_fk
    FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id),
  ADD CONSTRAINT payroll_relationship_version_identity_sequence_uk
    UNIQUE (tenant_id, payroll_relationship_id, version_sequence),
  ADD CONSTRAINT payroll_relationship_version_identity_id_uk
    UNIQUE (tenant_id, id, payroll_relationship_id),
  ADD CONSTRAINT payroll_relationship_version_sequence_ck
    CHECK (version_sequence > 0),
  ADD CONSTRAINT payroll_relationship_version_range_ck
    CHECK (
      relationship_end IS NULL
      OR relationship_end > relationship_start
    ),
  ADD CONSTRAINT payroll_relationship_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT payroll_relationship_version_approval_metadata_ck
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
  ADD CONSTRAINT payroll_relationship_version_supersedes_self_ck
    CHECK (
      supersedes_version_id IS NULL
      OR supersedes_version_id <> id
    ),
  ADD CONSTRAINT payroll_relationship_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      payroll_relationship_id
    )
    REFERENCES employee_payroll.payroll_relationship_version(
      tenant_id,
      id,
      payroll_relationship_id
    );

ALTER TABLE employee_payroll.payroll_relationship_version
  ADD CONSTRAINT payroll_relationship_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_relationship_id WITH =,
    daterange(relationship_start, relationship_end, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX payroll_relationship_version_one_successor_uk
  ON employee_payroll.payroll_relationship_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

ALTER TABLE employee_payroll.employee_payroll_profile
  RENAME COLUMN payroll_relationship_id
  TO payroll_relationship_version_id;
ALTER TABLE employee_payroll.employee_payroll_profile
  ADD COLUMN payroll_relationship_id uuid;

UPDATE employee_payroll.employee_payroll_profile profile
SET payroll_relationship_id = version.payroll_relationship_id
FROM employee_payroll.payroll_relationship_version version
WHERE version.tenant_id = profile.tenant_id
  AND version.id = profile.payroll_relationship_version_id;

ALTER TABLE employee_payroll.employee_payroll_profile
  DROP CONSTRAINT
    employee_payroll_profile_tenant_id_payroll_relationship_id_key,
  DROP CONSTRAINT
    employee_payroll_profile_tenant_id_payroll_relationship_id_fkey,
  ALTER COLUMN payroll_relationship_id SET NOT NULL,
  DROP COLUMN payroll_relationship_version_id,
  ADD CONSTRAINT employee_payroll_profile_relationship_uk
    UNIQUE (tenant_id, payroll_relationship_id),
  ADD CONSTRAINT employee_payroll_profile_relationship_fk
    FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id),
  ADD CONSTRAINT employee_payroll_profile_currency_ck
    CHECK (currency = 'INR'),
  ADD CONSTRAINT employee_payroll_profile_status_ck
    CHECK (
      payroll_status IN (
        'INCOMPLETE',
        'READY',
        'ON_HOLD',
        'INACTIVE'
      )
    );

ALTER TABLE employee_payroll.employee_payroll_profile
  ALTER COLUMN payroll_status SET DEFAULT 'INCOMPLETE';

CREATE TABLE employee_payroll.payroll_assignment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  assignment_number varchar(60) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, assignment_number),
  CHECK (btrim(assignment_number) <> ''),
  CHECK (status IN ('ACTIVE', 'INACTIVE')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

ALTER TABLE employee_payroll.payroll_assignment_version
  ADD COLUMN payroll_assignment_id uuid,
  ADD COLUMN version_sequence integer NOT NULL DEFAULT 1,
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_version_id uuid;

INSERT INTO employee_payroll.payroll_assignment(
  id,
  tenant_id,
  payroll_relationship_id,
  assignment_number,
  status,
  created_at,
  created_by,
  updated_at,
  updated_by,
  version_no
)
SELECT
  gen_random_uuid(),
  assignment_version.tenant_id,
  relationship_version.payroll_relationship_id,
  assignment_version.assignment_number,
  'ACTIVE',
  assignment_version.created_at,
  assignment_version.created_by,
  assignment_version.updated_at,
  assignment_version.updated_by,
  0
FROM employee_payroll.payroll_assignment_version assignment_version
JOIN employee_payroll.payroll_relationship_version relationship_version
  ON relationship_version.tenant_id = assignment_version.tenant_id
 AND relationship_version.id =
   assignment_version.payroll_relationship_version_id;

UPDATE employee_payroll.payroll_assignment_version version
SET payroll_assignment_id = identity.id,
    approved_at = version.created_at,
    approved_by = version.created_by
FROM employee_payroll.payroll_assignment identity
WHERE identity.tenant_id = version.tenant_id
  AND identity.assignment_number = version.assignment_number;

ALTER TABLE employee_payroll.payroll_assignment_version
  ALTER COLUMN payroll_assignment_id SET NOT NULL,
  DROP CONSTRAINT payroll_assignment_tenant_id_assignment_number_key,
  DROP COLUMN assignment_number,
  ADD CONSTRAINT payroll_assignment_version_identity_fk
    FOREIGN KEY (tenant_id, payroll_assignment_id)
    REFERENCES employee_payroll.payroll_assignment(tenant_id, id),
  ADD CONSTRAINT payroll_assignment_version_identity_sequence_uk
    UNIQUE (tenant_id, payroll_assignment_id, version_sequence),
  ADD CONSTRAINT payroll_assignment_version_identity_id_uk
    UNIQUE (tenant_id, id, payroll_assignment_id),
  ADD CONSTRAINT payroll_assignment_version_sequence_ck
    CHECK (version_sequence > 0),
  ADD CONSTRAINT payroll_assignment_version_range_ck
    CHECK (
      assignment_end IS NULL
      OR assignment_end > assignment_start
    ),
  ADD CONSTRAINT payroll_assignment_version_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT payroll_assignment_version_approval_metadata_ck
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
  ADD CONSTRAINT payroll_assignment_version_supersedes_self_ck
    CHECK (
      supersedes_version_id IS NULL
      OR supersedes_version_id <> id
    ),
  ADD CONSTRAINT payroll_assignment_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      payroll_assignment_id
    )
    REFERENCES employee_payroll.payroll_assignment_version(
      tenant_id,
      id,
      payroll_assignment_id
    );

ALTER TABLE employee_payroll.payroll_assignment_version
  ADD CONSTRAINT payroll_assignment_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_assignment_id WITH =,
    daterange(assignment_start, assignment_end, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX payroll_assignment_version_one_successor_uk
  ON employee_payroll.payroll_assignment_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

ALTER TABLE employee_payroll.pay_group_assignment
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_assignment_id uuid;

UPDATE employee_payroll.pay_group_assignment
SET approved_at = created_at,
    approved_by = created_by;

ALTER TABLE employee_payroll.pay_group_assignment
  ADD CONSTRAINT pay_group_assignment_version_id_uk
    UNIQUE (tenant_id, id, payroll_assignment_version_id),
  ADD CONSTRAINT pay_group_assignment_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT pay_group_assignment_approval_metadata_ck
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
  ADD CONSTRAINT pay_group_assignment_supersedes_self_ck
    CHECK (
      supersedes_assignment_id IS NULL
      OR supersedes_assignment_id <> id
    ),
  ADD CONSTRAINT pay_group_assignment_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_assignment_id,
      payroll_assignment_version_id
    )
    REFERENCES employee_payroll.pay_group_assignment(
      tenant_id,
      id,
      payroll_assignment_version_id
    );

ALTER TABLE employee_payroll.pay_group_assignment
  ADD CONSTRAINT pay_group_assignment_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_assignment_version_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX pay_group_assignment_one_successor_uk
  ON employee_payroll.pay_group_assignment(
    tenant_id,
    supersedes_assignment_id
  )
  WHERE supersedes_assignment_id IS NOT NULL;

ALTER TABLE employee_payroll.salary_assignment
  ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN approved_at timestamptz,
  ADD COLUMN approved_by varchar(160),
  ADD COLUMN supersedes_assignment_id uuid;

UPDATE employee_payroll.salary_assignment
SET approved_at = created_at,
    approved_by = created_by;

ALTER TABLE employee_payroll.salary_assignment
  ADD CONSTRAINT salary_assignment_version_id_uk
    UNIQUE (tenant_id, id, payroll_assignment_version_id),
  ADD CONSTRAINT salary_assignment_currency_ck
    CHECK (currency = 'INR'),
  ADD CONSTRAINT salary_assignment_status_ck
    CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  ADD CONSTRAINT salary_assignment_approval_metadata_ck
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
  ADD CONSTRAINT salary_assignment_supersedes_self_ck
    CHECK (
      supersedes_assignment_id IS NULL
      OR supersedes_assignment_id <> id
    ),
  ADD CONSTRAINT salary_assignment_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_assignment_id,
      payroll_assignment_version_id
    )
    REFERENCES employee_payroll.salary_assignment(
      tenant_id,
      id,
      payroll_assignment_version_id
    );

ALTER TABLE employee_payroll.salary_assignment
  ADD CONSTRAINT salary_assignment_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_assignment_version_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX salary_assignment_one_successor_uk
  ON employee_payroll.salary_assignment(
    tenant_id,
    supersedes_assignment_id
  )
  WHERE supersedes_assignment_id IS NOT NULL;

ALTER TABLE employee_payroll.payroll_relationship_version
  ALTER COLUMN approval_status SET DEFAULT 'DRAFT';
ALTER TABLE employee_payroll.payroll_assignment_version
  ALTER COLUMN approval_status SET DEFAULT 'DRAFT';
ALTER TABLE employee_payroll.pay_group_assignment
  ALTER COLUMN approval_status SET DEFAULT 'DRAFT';
ALTER TABLE employee_payroll.salary_assignment
  ALTER COLUMN approval_status SET DEFAULT 'DRAFT';

CREATE INDEX payroll_relationship_version_current_ix
  ON employee_payroll.payroll_relationship_version(
    tenant_id,
    payroll_relationship_id,
    relationship_start DESC
  );

CREATE INDEX payroll_assignment_version_current_ix
  ON employee_payroll.payroll_assignment_version(
    tenant_id,
    payroll_assignment_id,
    assignment_start DESC
  );

CREATE INDEX payroll_assignment_version_relationship_ix
  ON employee_payroll.payroll_assignment_version(
    tenant_id,
    payroll_relationship_version_id,
    assignment_start
  );

CREATE INDEX employee_payroll_profile_relationship_ix
  ON employee_payroll.employee_payroll_profile(
    tenant_id,
    payroll_relationship_id
  );

CREATE INDEX pay_group_assignment_version_lookup_ix
  ON employee_payroll.pay_group_assignment(
    tenant_id,
    payroll_assignment_version_id,
    effective_from,
    effective_to
  );

DROP INDEX employee_payroll.salary_assignment_lookup_ix;

CREATE INDEX salary_assignment_version_lookup_ix
  ON employee_payroll.salary_assignment(
    tenant_id,
    payroll_assignment_version_id,
    effective_from,
    effective_to
  );

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_payroll_relationship_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  parent_from date;
  parent_to date;
  parent_status varchar;
BEGIN
  SELECT
    legal_version.effective_from,
    legal_version.effective_to,
    legal_version.approval_status
  INTO
    parent_from,
    parent_to,
    parent_status
  FROM organisation.legal_entity_version legal_version
  WHERE legal_version.tenant_id = NEW.tenant_id
    AND legal_version.id = NEW.legal_entity_version_id;

  IF parent_from IS NULL THEN
    RAISE EXCEPTION
      'legal-entity version does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF parent_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'payroll relationships require an approved legal-entity version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.relationship_start < parent_from
     OR (
       parent_to IS NOT NULL
       AND (
         NEW.relationship_end IS NULL
         OR NEW.relationship_end > parent_to
       )
     ) THEN
    RAISE EXCEPTION
      'payroll-relationship range must be contained by its legal-entity version'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_relationship_version_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    legal_entity_version_id,
    relationship_start,
    relationship_end
  ON employee_payroll.payroll_relationship_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.assert_payroll_relationship_version_dependencies();

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_payroll_assignment_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  identity_relationship_id uuid;
  relationship_identity_id uuid;
  relationship_from date;
  relationship_to date;
  relationship_status varchar;
  relationship_legal_version_id uuid;
  establishment_from date;
  establishment_to date;
  establishment_status varchar;
  establishment_psu_version_id uuid;
  establishment_legal_version_id uuid;
BEGIN
  SELECT identity.payroll_relationship_id
  INTO identity_relationship_id
  FROM employee_payroll.payroll_assignment identity
  WHERE identity.tenant_id = NEW.tenant_id
    AND identity.id = NEW.payroll_assignment_id;

  SELECT
    relationship.payroll_relationship_id,
    relationship.relationship_start,
    relationship.relationship_end,
    relationship.approval_status,
    relationship.legal_entity_version_id
  INTO
    relationship_identity_id,
    relationship_from,
    relationship_to,
    relationship_status,
    relationship_legal_version_id
  FROM employee_payroll.payroll_relationship_version relationship
  WHERE relationship.tenant_id = NEW.tenant_id
    AND relationship.id = NEW.payroll_relationship_version_id;

  SELECT
    establishment.effective_from,
    establishment.effective_to,
    establishment.approval_status,
    establishment.payroll_statutory_unit_version_id,
    psu.legal_entity_version_id
  INTO
    establishment_from,
    establishment_to,
    establishment_status,
    establishment_psu_version_id,
    establishment_legal_version_id
  FROM organisation.establishment_version establishment
  JOIN organisation.payroll_statutory_unit_version psu
    ON psu.tenant_id = establishment.tenant_id
   AND psu.id = establishment.payroll_statutory_unit_version_id
  WHERE establishment.tenant_id = NEW.tenant_id
    AND establishment.id = NEW.establishment_version_id;

  IF identity_relationship_id IS NULL
     OR relationship_identity_id IS NULL
     OR establishment_from IS NULL THEN
    RAISE EXCEPTION
      'payroll-assignment dependencies do not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF identity_relationship_id <> relationship_identity_id THEN
    RAISE EXCEPTION
      'payroll-assignment identity and relationship version do not match'
      USING ERRCODE = '23514';
  END IF;

  IF relationship_status <> 'APPROVED'
     OR establishment_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'payroll assignments require approved relationship and establishment versions'
      USING ERRCODE = '23514';
  END IF;

  IF relationship_legal_version_id <>
     establishment_legal_version_id THEN
    RAISE EXCEPTION
      'assignment establishment must belong to the relationship legal entity'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.assignment_start < relationship_from
     OR (
       relationship_to IS NOT NULL
       AND (
         NEW.assignment_end IS NULL
         OR NEW.assignment_end > relationship_to
       )
     )
     OR NEW.assignment_start < establishment_from
     OR (
       establishment_to IS NOT NULL
       AND (
         NEW.assignment_end IS NULL
         OR NEW.assignment_end > establishment_to
       )
     ) THEN
    RAISE EXCEPTION
      'payroll-assignment range must be contained by its parent versions'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_assignment_version_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    payroll_assignment_id,
    payroll_relationship_version_id,
    establishment_version_id,
    assignment_start,
    assignment_end
  ON employee_payroll.payroll_assignment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.assert_payroll_assignment_version_dependencies();

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_pay_group_assignment_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  assignment_from date;
  assignment_to date;
  assignment_status varchar;
  assignment_psu_version_id uuid;
  group_from date;
  group_to date;
  group_status varchar;
  group_psu_version_id uuid;
BEGIN
  SELECT
    assignment.assignment_start,
    assignment.assignment_end,
    assignment.approval_status,
    establishment.payroll_statutory_unit_version_id
  INTO
    assignment_from,
    assignment_to,
    assignment_status,
    assignment_psu_version_id
  FROM employee_payroll.payroll_assignment_version assignment
  JOIN organisation.establishment_version establishment
    ON establishment.tenant_id = assignment.tenant_id
   AND establishment.id = assignment.establishment_version_id
  WHERE assignment.tenant_id = NEW.tenant_id
    AND assignment.id = NEW.payroll_assignment_version_id;

  SELECT
    group_version.effective_from,
    group_version.effective_to,
    group_version.approval_status,
    group_version.payroll_statutory_unit_version_id
  INTO
    group_from,
    group_to,
    group_status,
    group_psu_version_id
  FROM organisation.pay_group_version group_version
  WHERE group_version.tenant_id = NEW.tenant_id
    AND group_version.id = NEW.pay_group_version_id;

  IF assignment_from IS NULL OR group_from IS NULL THEN
    RAISE EXCEPTION
      'pay-group assignment dependencies do not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF assignment_status <> 'APPROVED'
     OR group_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'pay-group assignments require approved assignment and pay-group versions'
      USING ERRCODE = '23514';
  END IF;

  IF assignment_psu_version_id <> group_psu_version_id THEN
    RAISE EXCEPTION
      'pay-group and assignment establishment must use the same payroll statutory unit version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < assignment_from
     OR (
       assignment_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > assignment_to
       )
     )
     OR NEW.effective_from < group_from
     OR (
       group_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > group_to
       )
     ) THEN
    RAISE EXCEPTION
      'pay-group assignment range must be contained by its parent versions'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_assignment_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    payroll_assignment_version_id,
    pay_group_version_id,
    effective_from,
    effective_to
  ON employee_payroll.pay_group_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.assert_pay_group_assignment_dependencies();

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_salary_assignment_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  assignment_from date;
  assignment_to date;
  assignment_status varchar;
  structure_from date;
  structure_to date;
  structure_status varchar;
  structure_currency char(3);
BEGIN
  SELECT
    assignment.assignment_start,
    assignment.assignment_end,
    assignment.approval_status
  INTO
    assignment_from,
    assignment_to,
    assignment_status
  FROM employee_payroll.payroll_assignment_version assignment
  WHERE assignment.tenant_id = NEW.tenant_id
    AND assignment.id = NEW.payroll_assignment_version_id;

  SELECT
    structure.effective_from,
    structure.effective_to,
    structure.approval_status,
    structure.currency
  INTO
    structure_from,
    structure_to,
    structure_status,
    structure_currency
  FROM compensation.salary_structure_version structure
  WHERE structure.tenant_id = NEW.tenant_id
    AND structure.id = NEW.salary_structure_version_id;

  IF assignment_from IS NULL OR structure_from IS NULL THEN
    RAISE EXCEPTION
      'salary-assignment dependencies do not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF assignment_status <> 'APPROVED'
     OR structure_status <> 'APPROVED' THEN
    RAISE EXCEPTION
      'salary assignments require approved assignment and structure versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.currency <> 'INR'
     OR NEW.currency <> structure_currency THEN
    RAISE EXCEPTION
      'salary-assignment currency must be INR and match the structure version'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.effective_from < assignment_from
     OR (
       assignment_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > assignment_to
       )
     )
     OR NEW.effective_from < structure_from
     OR (
       structure_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR NEW.effective_to > structure_to
       )
     ) THEN
    RAISE EXCEPTION
      'salary-assignment range must be contained by its parent versions'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS salary_assignment_structure_dependencies
  ON employee_payroll.salary_assignment;

CREATE TRIGGER salary_assignment_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    payroll_assignment_version_id,
    salary_structure_version_id,
    monthly_amount,
    currency,
    effective_from,
    effective_to
  ON employee_payroll.salary_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.assert_salary_assignment_dependencies();

CREATE OR REPLACE FUNCTION
  employee_payroll.require_draft_configuration_insert()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner'
     AND NEW.approval_status <> 'DRAFT' THEN
    RAISE EXCEPTION
      'runtime employee-payroll configuration must be created as a draft'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_relationship_version_draft_insert
  BEFORE INSERT
  ON employee_payroll.payroll_relationship_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.require_draft_configuration_insert();

CREATE TRIGGER payroll_assignment_version_draft_insert
  BEFORE INSERT
  ON employee_payroll.payroll_assignment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.require_draft_configuration_insert();

CREATE TRIGGER pay_group_assignment_draft_insert
  BEFORE INSERT
  ON employee_payroll.pay_group_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.require_draft_configuration_insert();

CREATE TRIGGER salary_assignment_draft_insert
  BEFORE INSERT
  ON employee_payroll.salary_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.require_draft_configuration_insert();

CREATE OR REPLACE FUNCTION
  employee_payroll.require_incomplete_profile_insert()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner'
     AND NEW.payroll_status <> 'INCOMPLETE' THEN
    RAISE EXCEPTION
      'runtime employee payroll profiles must be created as INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER employee_payroll_profile_incomplete_insert
  BEFORE INSERT
  ON employee_payroll.employee_payroll_profile
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.require_incomplete_profile_insert();

CREATE OR REPLACE FUNCTION
  employee_payroll.reject_uncontrolled_configuration_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION
      'immutable employee-payroll configuration: %.%',
      TG_TABLE_SCHEMA,
      TG_TABLE_NAME;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION
      'employee-payroll configuration records cannot be deleted';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_relationship_version_immutable
  BEFORE UPDATE OR DELETE
  ON employee_payroll.payroll_relationship_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.reject_uncontrolled_configuration_mutation();

CREATE TRIGGER payroll_assignment_version_immutable
  BEFORE UPDATE OR DELETE
  ON employee_payroll.payroll_assignment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.reject_uncontrolled_configuration_mutation();

CREATE TRIGGER pay_group_assignment_immutable
  BEFORE UPDATE OR DELETE
  ON employee_payroll.pay_group_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.reject_uncontrolled_configuration_mutation();

CREATE TRIGGER salary_assignment_immutable
  BEFORE UPDATE OR DELETE
  ON employee_payroll.salary_assignment
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.reject_uncontrolled_configuration_mutation();

CREATE OR REPLACE FUNCTION
  employee_payroll.guard_parent_version_end_date()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.effective_to IS NULL
     OR (
       OLD.effective_to IS NOT NULL
       AND NEW.effective_to >= OLD.effective_to
     ) THEN
    RETURN NEW;
  END IF;

  IF TG_TABLE_SCHEMA = 'organisation'
     AND TG_TABLE_NAME = 'legal_entity_version'
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.payroll_relationship_version relationship
       WHERE relationship.tenant_id = OLD.tenant_id
         AND relationship.legal_entity_version_id = OLD.id
         AND (
           relationship.relationship_start >= NEW.effective_to
           OR relationship.relationship_end IS NULL
           OR relationship.relationship_end > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'legal-entity version cannot end before dependent payroll relationships'
      USING ERRCODE = '23514';
  END IF;

  IF TG_TABLE_SCHEMA = 'organisation'
     AND TG_TABLE_NAME = 'establishment_version'
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.payroll_assignment_version assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.establishment_version_id = OLD.id
         AND (
           assignment.assignment_start >= NEW.effective_to
           OR assignment.assignment_end IS NULL
           OR assignment.assignment_end > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'establishment version cannot end before dependent payroll assignments'
      USING ERRCODE = '23514';
  END IF;

  IF TG_TABLE_SCHEMA = 'organisation'
     AND TG_TABLE_NAME = 'pay_group_version'
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.pay_group_assignment assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.pay_group_version_id = OLD.id
         AND (
           assignment.effective_from >= NEW.effective_to
           OR assignment.effective_to IS NULL
           OR assignment.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'pay-group version cannot end before dependent employee assignments'
      USING ERRCODE = '23514';
  END IF;

  IF TG_TABLE_SCHEMA = 'compensation'
     AND TG_TABLE_NAME = 'salary_structure_version'
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.salary_assignment assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.salary_structure_version_id = OLD.id
         AND (
           assignment.effective_from >= NEW.effective_to
           OR assignment.effective_to IS NULL
           OR assignment.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'salary-structure version cannot end before dependent salary assignments'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER legal_entity_version_employee_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.legal_entity_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_parent_version_end_date();

CREATE TRIGGER establishment_version_employee_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.establishment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_parent_version_end_date();

CREATE TRIGGER pay_group_version_employee_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.pay_group_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_parent_version_end_date();

CREATE TRIGGER salary_structure_version_employee_dependents
  BEFORE UPDATE OF effective_to
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_parent_version_end_date();

CREATE OR REPLACE FUNCTION
  employee_payroll.guard_relationship_version_end_date()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.relationship_end IS NOT NULL
     AND (
       OLD.relationship_end IS NULL
       OR NEW.relationship_end < OLD.relationship_end
     )
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.payroll_assignment_version assignment
       WHERE assignment.tenant_id = OLD.tenant_id
         AND assignment.payroll_relationship_version_id = OLD.id
         AND (
           assignment.assignment_start >= NEW.relationship_end
           OR assignment.assignment_end IS NULL
           OR assignment.assignment_end > NEW.relationship_end
         )
     ) THEN
    RAISE EXCEPTION
      'payroll relationship cannot end before dependent assignments'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_relationship_version_assignment_dependents
  BEFORE UPDATE OF relationship_end
  ON employee_payroll.payroll_relationship_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_relationship_version_end_date();

CREATE OR REPLACE FUNCTION
  employee_payroll.guard_assignment_version_end_date()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.assignment_end IS NULL
     OR (
       OLD.assignment_end IS NOT NULL
       AND NEW.assignment_end >= OLD.assignment_end
     ) THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.pay_group_assignment assignment
    WHERE assignment.tenant_id = OLD.tenant_id
      AND assignment.payroll_assignment_version_id = OLD.id
      AND (
        assignment.effective_from >= NEW.assignment_end
        OR assignment.effective_to IS NULL
        OR assignment.effective_to > NEW.assignment_end
      )
  ) THEN
    RAISE EXCEPTION
      'payroll assignment cannot end before its pay-group assignment'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM employee_payroll.salary_assignment assignment
    WHERE assignment.tenant_id = OLD.tenant_id
      AND assignment.payroll_assignment_version_id = OLD.id
      AND (
        assignment.effective_from >= NEW.assignment_end
        OR assignment.effective_to IS NULL
        OR assignment.effective_to > NEW.assignment_end
      )
  ) THEN
    RAISE EXCEPTION
      'payroll assignment cannot end before its salary assignment'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.population_member member
    JOIN payroll_ops.payroll_cycle cycle
      ON cycle.tenant_id = member.tenant_id
     AND cycle.id = member.payroll_cycle_id
    JOIN organisation.pay_period period
      ON period.tenant_id = cycle.tenant_id
     AND period.id = cycle.pay_period_id
    WHERE member.tenant_id = OLD.tenant_id
      AND member.payroll_assignment_version_id = OLD.id
      AND period.period_end >= NEW.assignment_end
  ) THEN
    RAISE EXCEPTION
      'payroll assignment cannot end before a populated payroll period'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_assignment_version_dependents
  BEFORE UPDATE OF assignment_end
  ON employee_payroll.payroll_assignment_version
  FOR EACH ROW
  EXECUTE FUNCTION
    employee_payroll.guard_assignment_version_end_date();

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_payroll_relationship_version(
    p_tenant_id uuid,
    p_version_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  organisation,
  platform AS $$
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

  UPDATE employee_payroll.payroll_relationship_version version
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
      FROM employee_payroll.payroll_relationship_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
    AND EXISTS (
      SELECT 1
      FROM organisation.legal_entity_version parent
      WHERE parent.tenant_id = version.tenant_id
        AND parent.id = version.legal_entity_version_id
        AND parent.approval_status = 'APPROVED'
        AND version.relationship_start >= parent.effective_from
        AND (
          parent.effective_to IS NULL
          OR (
            version.relationship_end IS NOT NULL
            AND version.relationship_end <= parent.effective_to
          )
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.end_date_payroll_relationship_version(
    p_tenant_id uuid,
    p_version_id uuid,
    p_relationship_end date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  organisation,
  platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_relationship_end IS NULL THEN
    RAISE EXCEPTION 'relationship-end date is required'
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

  UPDATE employee_payroll.payroll_relationship_version version
  SET relationship_end = p_relationship_end,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.version_no = p_expected_version
    AND version.relationship_start < p_relationship_end
    AND (
      version.relationship_end IS NULL
      OR version.relationship_end > p_relationship_end
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_payroll_assignment_version(
    p_tenant_id uuid,
    p_version_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  organisation,
  platform AS $$
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

  UPDATE employee_payroll.payroll_assignment_version version
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
      FROM employee_payroll.payroll_assignment_version successor
      WHERE successor.tenant_id = version.tenant_id
        AND successor.supersedes_version_id = version.id
    )
    AND EXISTS (
      SELECT 1
      FROM employee_payroll.payroll_assignment identity
      JOIN employee_payroll.payroll_relationship_version relationship
        ON relationship.tenant_id = version.tenant_id
       AND relationship.id =
         version.payroll_relationship_version_id
      JOIN organisation.establishment_version establishment
        ON establishment.tenant_id = version.tenant_id
       AND establishment.id = version.establishment_version_id
      JOIN organisation.payroll_statutory_unit_version psu
        ON psu.tenant_id = establishment.tenant_id
       AND psu.id =
         establishment.payroll_statutory_unit_version_id
      WHERE identity.tenant_id = version.tenant_id
        AND identity.id = version.payroll_assignment_id
        AND identity.payroll_relationship_id =
          relationship.payroll_relationship_id
        AND relationship.approval_status = 'APPROVED'
        AND establishment.approval_status = 'APPROVED'
        AND psu.legal_entity_version_id =
          relationship.legal_entity_version_id
        AND version.assignment_start >=
          relationship.relationship_start
        AND (
          relationship.relationship_end IS NULL
          OR (
            version.assignment_end IS NOT NULL
            AND version.assignment_end <=
              relationship.relationship_end
          )
        )
        AND version.assignment_start >=
          establishment.effective_from
        AND (
          establishment.effective_to IS NULL
          OR (
            version.assignment_end IS NOT NULL
            AND version.assignment_end <= establishment.effective_to
          )
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.end_date_payroll_assignment_version(
    p_tenant_id uuid,
    p_version_id uuid,
    p_assignment_end date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  payroll_ops,
  organisation,
  platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_assignment_end IS NULL THEN
    RAISE EXCEPTION 'assignment-end date is required'
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

  UPDATE employee_payroll.payroll_assignment_version version
  SET assignment_end = p_assignment_end,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE version.tenant_id = p_tenant_id
    AND version.id = p_version_id
    AND version.version_no = p_expected_version
    AND version.assignment_start < p_assignment_end
    AND (
      version.assignment_end IS NULL
      OR version.assignment_end > p_assignment_end
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_pay_group_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  organisation,
  platform AS $$
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

  UPDATE employee_payroll.pay_group_assignment assignment
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.pay_group_assignment successor
      WHERE successor.tenant_id = assignment.tenant_id
        AND successor.supersedes_assignment_id = assignment.id
    )
    AND EXISTS (
      SELECT 1
      FROM employee_payroll.payroll_assignment_version assignment_version
      JOIN organisation.establishment_version establishment
        ON establishment.tenant_id = assignment_version.tenant_id
       AND establishment.id =
         assignment_version.establishment_version_id
      JOIN organisation.pay_group_version group_version
        ON group_version.tenant_id = assignment.tenant_id
       AND group_version.id = assignment.pay_group_version_id
      WHERE assignment_version.tenant_id = assignment.tenant_id
        AND assignment_version.id =
          assignment.payroll_assignment_version_id
        AND assignment_version.approval_status = 'APPROVED'
        AND group_version.approval_status = 'APPROVED'
        AND establishment.payroll_statutory_unit_version_id =
          group_version.payroll_statutory_unit_version_id
        AND assignment.effective_from >=
          assignment_version.assignment_start
        AND (
          assignment_version.assignment_end IS NULL
          OR (
            assignment.effective_to IS NOT NULL
            AND assignment.effective_to <=
              assignment_version.assignment_end
          )
        )
        AND assignment.effective_from >=
          group_version.effective_from
        AND (
          group_version.effective_to IS NULL
          OR (
            assignment.effective_to IS NOT NULL
            AND assignment.effective_to <= group_version.effective_to
          )
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.end_date_pay_group_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
    p_effective_to date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  organisation,
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

  UPDATE employee_payroll.pay_group_assignment assignment
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.version_no = p_expected_version
    AND assignment.effective_from < p_effective_to
    AND (
      assignment.effective_to IS NULL
      OR assignment.effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_salary_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  compensation,
  platform AS $$
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

  UPDATE employee_payroll.salary_assignment assignment
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.salary_assignment successor
      WHERE successor.tenant_id = assignment.tenant_id
        AND successor.supersedes_assignment_id = assignment.id
    )
    AND EXISTS (
      SELECT 1
      FROM employee_payroll.payroll_assignment_version assignment_version
      JOIN compensation.salary_structure_version structure
        ON structure.tenant_id = assignment.tenant_id
       AND structure.id = assignment.salary_structure_version_id
      WHERE assignment_version.tenant_id = assignment.tenant_id
        AND assignment_version.id =
          assignment.payroll_assignment_version_id
        AND assignment_version.approval_status = 'APPROVED'
        AND structure.approval_status = 'APPROVED'
        AND assignment.currency = 'INR'
        AND assignment.currency = structure.currency
        AND assignment.effective_from >=
          assignment_version.assignment_start
        AND (
          assignment_version.assignment_end IS NULL
          OR (
            assignment.effective_to IS NOT NULL
            AND assignment.effective_to <=
              assignment_version.assignment_end
          )
        )
        AND assignment.effective_from >= structure.effective_from
        AND (
          structure.effective_to IS NULL
          OR (
            assignment.effective_to IS NOT NULL
            AND assignment.effective_to <= structure.effective_to
          )
        )
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.end_date_salary_assignment(
    p_tenant_id uuid,
    p_assignment_id uuid,
    p_effective_to date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  compensation,
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

  UPDATE employee_payroll.salary_assignment assignment
  SET effective_to = p_effective_to,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE assignment.tenant_id = p_tenant_id
    AND assignment.id = p_assignment_id
    AND assignment.version_no = p_expected_version
    AND assignment.effective_from < p_effective_to
    AND (
      assignment.effective_to IS NULL
      OR assignment.effective_to > p_effective_to
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.update_employee_payroll_profile_status(
    p_tenant_id uuid,
    p_profile_id uuid,
    p_payroll_status varchar,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  )
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  employee_payroll,
  platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_payroll_status NOT IN (
    'INCOMPLETE',
    'READY',
    'ON_HOLD',
    'INACTIVE'
  ) THEN
    RAISE EXCEPTION 'unsupported payroll profile status'
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

  IF p_payroll_status = 'READY'
     AND EXISTS (
       SELECT 1
       FROM employee_payroll.employee_payroll_profile profile
       WHERE profile.tenant_id = p_tenant_id
         AND profile.id = p_profile_id
     )
     AND NOT EXISTS (
       SELECT 1
       FROM employee_payroll.employee_payroll_profile profile
       JOIN employee_payroll.payroll_relationship relationship_identity
         ON relationship_identity.tenant_id = profile.tenant_id
        AND relationship_identity.id = profile.payroll_relationship_id
       JOIN employee_payroll.payroll_relationship_version
         relationship_version
         ON relationship_version.tenant_id =
           relationship_identity.tenant_id
        AND relationship_version.payroll_relationship_id =
           relationship_identity.id
       JOIN employee_payroll.payroll_assignment assignment_identity
         ON assignment_identity.tenant_id =
           relationship_identity.tenant_id
        AND assignment_identity.payroll_relationship_id =
           relationship_identity.id
       JOIN employee_payroll.payroll_assignment_version
         assignment_version
         ON assignment_version.tenant_id = assignment_identity.tenant_id
        AND assignment_version.payroll_assignment_id =
           assignment_identity.id
        AND assignment_version.payroll_relationship_version_id =
           relationship_version.id
       JOIN employee_payroll.pay_group_assignment group_assignment
         ON group_assignment.tenant_id = assignment_version.tenant_id
        AND group_assignment.payroll_assignment_version_id =
           assignment_version.id
       JOIN organisation.pay_group_version group_version
         ON group_version.tenant_id = group_assignment.tenant_id
        AND group_version.id = group_assignment.pay_group_version_id
       JOIN organisation.pay_group group_identity
         ON group_identity.tenant_id = group_version.tenant_id
        AND group_identity.id = group_version.pay_group_id
       JOIN employee_payroll.salary_assignment salary_assignment
         ON salary_assignment.tenant_id = assignment_version.tenant_id
        AND salary_assignment.payroll_assignment_version_id =
           assignment_version.id
       JOIN compensation.salary_structure_version structure_version
         ON structure_version.tenant_id = salary_assignment.tenant_id
        AND structure_version.id =
           salary_assignment.salary_structure_version_id
       JOIN compensation.salary_structure structure_identity
         ON structure_identity.tenant_id = structure_version.tenant_id
        AND structure_identity.id =
           structure_version.salary_structure_id
       WHERE profile.tenant_id = p_tenant_id
         AND profile.id = p_profile_id
         AND relationship_identity.status = 'ACTIVE'
         AND relationship_version.approval_status = 'APPROVED'
         AND assignment_identity.status = 'ACTIVE'
         AND assignment_version.approval_status = 'APPROVED'
         AND group_identity.status = 'ACTIVE'
         AND group_version.approval_status = 'APPROVED'
         AND group_assignment.approval_status = 'APPROVED'
         AND structure_identity.status = 'ACTIVE'
         AND structure_version.approval_status = 'APPROVED'
         AND salary_assignment.approval_status = 'APPROVED'
         AND daterange(
           group_assignment.effective_from,
           group_assignment.effective_to,
           '[)'
         ) && daterange(
           salary_assignment.effective_from,
           salary_assignment.effective_to,
           '[)'
         )
     ) THEN
    RAISE EXCEPTION
      'READY requires an active relationship with overlapping approved assignment, pay-group and salary configuration'
      USING ERRCODE = '23514';
  END IF;

  UPDATE employee_payroll.employee_payroll_profile profile
  SET payroll_status = p_payroll_status,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE profile.tenant_id = p_tenant_id
    AND profile.id = p_profile_id
    AND profile.version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION
  employee_payroll.approve_payroll_relationship_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.end_date_payroll_relationship_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.approve_payroll_assignment_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.end_date_payroll_assignment_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.approve_pay_group_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.end_date_pay_group_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.approve_salary_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.end_date_salary_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  FROM PUBLIC;
REVOKE ALL ON FUNCTION
  employee_payroll.update_employee_payroll_profile_status(
    uuid,
    uuid,
    varchar,
    bigint,
    varchar,
    timestamptz
  )
  FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
  employee_payroll.approve_payroll_relationship_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.end_date_payroll_relationship_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.approve_payroll_assignment_version(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.end_date_payroll_assignment_version(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.approve_pay_group_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.end_date_pay_group_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.approve_salary_assignment(
    uuid,
    uuid,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.end_date_salary_assignment(
    uuid,
    uuid,
    date,
    bigint,
    varchar,
    timestamptz
  )
  TO payroll_app;
GRANT EXECUTE ON FUNCTION
  employee_payroll.update_employee_payroll_profile_status(
    uuid,
    uuid,
    varchar,
    bigint,
    varchar,
    timestamptz
  )
  TO payroll_app;

ALTER TABLE employee_payroll.payroll_relationship
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship
  FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation
  ON employee_payroll.payroll_relationship;
CREATE POLICY tenant_isolation
  ON employee_payroll.payroll_relationship
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE employee_payroll.payroll_assignment
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment
  FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation
  ON employee_payroll.payroll_assignment;
CREATE POLICY tenant_isolation
  ON employee_payroll.payroll_assignment
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE organisation.legal_entity_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.establishment_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment_version
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.employee_payroll_profile
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.pay_group_assignment
  FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment
  FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT
  ON employee_payroll.payroll_relationship,
     employee_payroll.payroll_relationship_version,
     employee_payroll.payroll_assignment,
     employee_payroll.payroll_assignment_version,
     employee_payroll.employee_payroll_profile,
     employee_payroll.pay_group_assignment,
     employee_payroll.salary_assignment
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON employee_payroll.payroll_relationship,
     employee_payroll.payroll_relationship_version,
     employee_payroll.payroll_assignment,
     employee_payroll.payroll_assignment_version,
     employee_payroll.employee_payroll_profile,
     employee_payroll.pay_group_assignment,
     employee_payroll.salary_assignment
  FROM payroll_app;

REVOKE CREATE ON SCHEMA employee_payroll FROM payroll_app;

COMMENT ON TABLE employee_payroll.payroll_relationship IS
  'Stable tenant-scoped employee payroll relationship identity.';
COMMENT ON TABLE employee_payroll.payroll_relationship_version IS
  'Immutable effective-dated payroll relationship version with exact legal-entity lineage.';
COMMENT ON TABLE employee_payroll.payroll_assignment IS
  'Stable tenant-scoped employee payroll assignment identity.';
COMMENT ON TABLE employee_payroll.payroll_assignment_version IS
  'Immutable effective-dated payroll assignment version with exact relationship and establishment lineage.';
COMMENT ON TABLE employee_payroll.employee_payroll_profile IS
  'One controlled INR payroll-readiness profile per stable payroll relationship.';
COMMENT ON TABLE employee_payroll.pay_group_assignment IS
  'Immutable approved effective-dated link to an exact pay-group version.';
COMMENT ON TABLE employee_payroll.salary_assignment IS
  'Immutable approved effective-dated link to an exact salary-structure version.';
COMMENT ON COLUMN
  payroll_ops.population_member.payroll_assignment_version_id IS
  'Exact payroll-assignment version included in the payroll cycle.';
COMMENT ON COLUMN
  payroll_ops.input_snapshot.payroll_assignment_version_id IS
  'Exact payroll-assignment version captured by the sealed input snapshot.';
COMMENT ON COLUMN
  payroll_calc.payroll_result.payroll_assignment_version_id IS
  'Exact payroll-assignment version used for deterministic calculation lineage.';
