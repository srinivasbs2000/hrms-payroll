-- S3-01A controlled payroll-cycle and population-resolution foundation.
--
-- V007 introduced payroll cycles, active population members and input snapshots.
-- V021 repointed population and downstream result lineage to exact payroll-
-- assignment version identifiers. This migration adds the remaining exact
-- configuration evidence, immutable population-resolution attempts and
-- controlled cycle/population commands without replacing the existing tables.

ALTER TABLE organisation.pay_group_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_period NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.employee_payroll_profile NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.pay_group_assignment NO FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.payroll_cycle NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_member NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle
    WHERE cycle_type <> 'REGULAR'
  ) THEN
    RAISE EXCEPTION
      'V023 supports regular payroll cycles only';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.population_member
    WHERE status <> 'INCLUDED'
  ) THEN
    RAISE EXCEPTION
      'existing population members must use INCLUDED status before V023';
  END IF;
END $$;

CREATE TABLE payroll_ops.population_resolution (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  attempt_no integer NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'BUILDING',
  included_count integer NOT NULL DEFAULT 0,
  excluded_count integer NOT NULL DEFAULT 0,
  resolved_at timestamptz NOT NULL,
  resolved_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_cycle_id),
  UNIQUE (tenant_id, payroll_cycle_id, attempt_no),
  CHECK (attempt_no > 0),
  CHECK (status IN ('BUILDING', 'COMPLETED', 'FAILED')),
  CHECK (included_count >= 0),
  CHECK (excluded_count >= 0),
  CHECK (btrim(resolved_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_cycle_id)
    REFERENCES payroll_ops.payroll_cycle(tenant_id, id)
);

ALTER TABLE payroll_ops.payroll_cycle
  ADD COLUMN active_population_resolution_id uuid;

CREATE TABLE payroll_ops.population_decision (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  population_resolution_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  payroll_assignment_version_id uuid NOT NULL,
  payroll_relationship_version_id uuid NOT NULL,
  employee_payroll_profile_id uuid,
  pay_group_assignment_id uuid,
  salary_assignment_id uuid,
  salary_structure_version_id uuid,
  decision varchar(20) NOT NULL,
  reason_code varchar(80) NOT NULL,
  reason_detail varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    population_resolution_id,
    payroll_assignment_version_id
  ),
  CHECK (decision IN ('INCLUDED', 'EXCLUDED')),
  CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
  CHECK (btrim(reason_detail) <> ''),
  CHECK (
    (decision = 'INCLUDED' AND reason_code = 'INCLUDED')
    OR (decision = 'EXCLUDED' AND reason_code <> 'INCLUDED')
  ),
  CHECK (
    decision <> 'INCLUDED'
    OR (
      employee_payroll_profile_id IS NOT NULL
      AND pay_group_assignment_id IS NOT NULL
      AND salary_assignment_id IS NOT NULL
      AND salary_structure_version_id IS NOT NULL
    )
  ),
  FOREIGN KEY (
    tenant_id,
    population_resolution_id,
    payroll_cycle_id
  ) REFERENCES payroll_ops.population_resolution(
    tenant_id,
    id,
    payroll_cycle_id
  ),
  FOREIGN KEY (tenant_id, payroll_cycle_id)
    REFERENCES payroll_ops.payroll_cycle(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_assignment_version_id)
    REFERENCES employee_payroll.payroll_assignment_version(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_relationship_version_id)
    REFERENCES employee_payroll.payroll_relationship_version(tenant_id, id),
  FOREIGN KEY (tenant_id, employee_payroll_profile_id)
    REFERENCES employee_payroll.employee_payroll_profile(tenant_id, id),
  FOREIGN KEY (tenant_id, pay_group_assignment_id)
    REFERENCES employee_payroll.pay_group_assignment(tenant_id, id),
  FOREIGN KEY (tenant_id, salary_assignment_id)
    REFERENCES employee_payroll.salary_assignment(tenant_id, id),
  FOREIGN KEY (tenant_id, salary_structure_version_id)
    REFERENCES compensation.salary_structure_version(tenant_id, id)
);

ALTER TABLE payroll_ops.population_member
  ADD COLUMN population_resolution_id uuid,
  ADD COLUMN payroll_relationship_version_id uuid,
  ADD COLUMN employee_payroll_profile_id uuid,
  ADD COLUMN pay_group_assignment_id uuid,
  ADD COLUMN salary_assignment_id uuid;

-- Preserve any pre-Sprint-3 synthetic/legacy population by creating one
-- immutable resolution attempt and deriving the exact approved lineage that
-- was effective for the cycle period.
INSERT INTO payroll_ops.population_resolution(
  id,
  tenant_id,
  payroll_cycle_id,
  attempt_no,
  status,
  included_count,
  excluded_count,
  resolved_at,
  resolved_by,
  created_at,
  created_by,
  updated_at,
  updated_by
)
SELECT
  gen_random_uuid(),
  cycle.tenant_id,
  cycle.id,
  1,
  'COMPLETED',
  count(member.id)::integer,
  0,
  cycle.updated_at,
  'v023-backfill',
  cycle.updated_at,
  'v023-backfill',
  cycle.updated_at,
  'v023-backfill'
FROM payroll_ops.payroll_cycle cycle
JOIN payroll_ops.population_member member
  ON member.tenant_id = cycle.tenant_id
 AND member.payroll_cycle_id = cycle.id
GROUP BY
  cycle.tenant_id,
  cycle.id,
  cycle.updated_at;

UPDATE payroll_ops.payroll_cycle cycle
SET active_population_resolution_id = resolution.id,
    status = CASE
      WHEN cycle.status = 'DRAFT'
        THEN 'POPULATION_RESOLVED'::payroll_ops.cycle_status
      ELSE cycle.status
    END,
    updated_at = resolution.resolved_at,
    updated_by = 'v023-backfill',
    version_no = cycle.version_no + CASE
      WHEN cycle.status = 'DRAFT' THEN 1 ELSE 0
    END
FROM payroll_ops.population_resolution resolution
WHERE resolution.tenant_id = cycle.tenant_id
  AND resolution.payroll_cycle_id = cycle.id
  AND resolution.attempt_no = 1;

WITH resolved AS (
  SELECT
    member.id AS member_id,
    resolution.id AS resolution_id,
    assignment_version.payroll_relationship_version_id,
    profile.id AS profile_id,
    group_assignment.id AS group_assignment_id,
    salary_assignment.id AS salary_assignment_id
  FROM payroll_ops.population_member member
  JOIN payroll_ops.payroll_cycle cycle
    ON cycle.tenant_id = member.tenant_id
   AND cycle.id = member.payroll_cycle_id
  JOIN payroll_ops.population_resolution resolution
    ON resolution.tenant_id = cycle.tenant_id
   AND resolution.id = cycle.active_population_resolution_id
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
  JOIN employee_payroll.payroll_assignment_version assignment_version
    ON assignment_version.tenant_id = member.tenant_id
   AND assignment_version.id = member.payroll_assignment_version_id
  JOIN employee_payroll.payroll_relationship_version relationship_version
    ON relationship_version.tenant_id = assignment_version.tenant_id
   AND relationship_version.id =
       assignment_version.payroll_relationship_version_id
  JOIN employee_payroll.employee_payroll_profile profile
    ON profile.tenant_id = relationship_version.tenant_id
   AND profile.payroll_relationship_id =
       relationship_version.payroll_relationship_id
  JOIN LATERAL (
    SELECT candidate.id
    FROM employee_payroll.pay_group_assignment candidate
    WHERE candidate.tenant_id = member.tenant_id
      AND candidate.payroll_assignment_version_id =
          member.payroll_assignment_version_id
      AND candidate.pay_group_version_id = cycle.pay_group_id
      AND candidate.approval_status = 'APPROVED'
      AND candidate.effective_from <= period.period_start
      AND (
        candidate.effective_to IS NULL
        OR candidate.effective_to > period.period_end
      )
      AND NOT EXISTS (
        SELECT 1
        FROM employee_payroll.pay_group_assignment successor
        WHERE successor.tenant_id = candidate.tenant_id
          AND successor.supersedes_assignment_id = candidate.id
      )
    ORDER BY candidate.effective_from DESC, candidate.id DESC
    LIMIT 1
  ) group_assignment ON true
  JOIN LATERAL (
    SELECT candidate.id
    FROM employee_payroll.salary_assignment candidate
    JOIN compensation.salary_structure_version structure_version
      ON structure_version.tenant_id = candidate.tenant_id
     AND structure_version.id = candidate.salary_structure_version_id
    WHERE candidate.tenant_id = member.tenant_id
      AND candidate.payroll_assignment_version_id =
          member.payroll_assignment_version_id
      AND candidate.approval_status = 'APPROVED'
      AND structure_version.approval_status = 'APPROVED'
      AND candidate.effective_from <= period.period_start
      AND (
        candidate.effective_to IS NULL
        OR candidate.effective_to > period.period_end
      )
      AND NOT EXISTS (
        SELECT 1
        FROM employee_payroll.salary_assignment successor
        WHERE successor.tenant_id = candidate.tenant_id
          AND successor.supersedes_assignment_id = candidate.id
      )
    ORDER BY candidate.effective_from DESC, candidate.id DESC
    LIMIT 1
  ) salary_assignment ON true
)
UPDATE payroll_ops.population_member member
SET population_resolution_id = resolved.resolution_id,
    payroll_relationship_version_id =
      resolved.payroll_relationship_version_id,
    employee_payroll_profile_id = resolved.profile_id,
    pay_group_assignment_id = resolved.group_assignment_id,
    salary_assignment_id = resolved.salary_assignment_id
FROM resolved
WHERE resolved.member_id = member.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.population_member
    WHERE population_resolution_id IS NULL
       OR payroll_relationship_version_id IS NULL
       OR employee_payroll_profile_id IS NULL
       OR pay_group_assignment_id IS NULL
       OR salary_assignment_id IS NULL
  ) THEN
    RAISE EXCEPTION
      'existing population members cannot be backfilled with exact approved lineage';
  END IF;
END $$;

ALTER TABLE payroll_ops.population_member
  ADD CONSTRAINT population_member_lineage_consistency_ck
    CHECK (
      (
        population_resolution_id IS NULL
        AND payroll_relationship_version_id IS NULL
        AND employee_payroll_profile_id IS NULL
        AND pay_group_assignment_id IS NULL
        AND salary_assignment_id IS NULL
      )
      OR (
        population_resolution_id IS NOT NULL
        AND payroll_relationship_version_id IS NOT NULL
        AND employee_payroll_profile_id IS NOT NULL
        AND pay_group_assignment_id IS NOT NULL
        AND salary_assignment_id IS NOT NULL
      )
    ),
  ADD CONSTRAINT population_member_resolution_fk
    FOREIGN KEY (
      tenant_id,
      population_resolution_id,
      payroll_cycle_id
    ) REFERENCES payroll_ops.population_resolution(
      tenant_id,
      id,
      payroll_cycle_id
    ),
  ADD CONSTRAINT population_member_relationship_version_fk
    FOREIGN KEY (tenant_id, payroll_relationship_version_id)
    REFERENCES employee_payroll.payroll_relationship_version(tenant_id, id),
  ADD CONSTRAINT population_member_profile_fk
    FOREIGN KEY (tenant_id, employee_payroll_profile_id)
    REFERENCES employee_payroll.employee_payroll_profile(tenant_id, id),
  ADD CONSTRAINT population_member_group_assignment_fk
    FOREIGN KEY (tenant_id, pay_group_assignment_id)
    REFERENCES employee_payroll.pay_group_assignment(tenant_id, id),
  ADD CONSTRAINT population_member_salary_assignment_fk
    FOREIGN KEY (tenant_id, salary_assignment_id)
    REFERENCES employee_payroll.salary_assignment(tenant_id, id),
  ADD CONSTRAINT population_member_included_status_ck
    CHECK (status = 'INCLUDED');

ALTER TABLE payroll_ops.payroll_cycle
  ADD CONSTRAINT payroll_cycle_active_resolution_fk
    FOREIGN KEY (
      tenant_id,
      active_population_resolution_id,
      id
    ) REFERENCES payroll_ops.population_resolution(
      tenant_id,
      id,
      payroll_cycle_id
    );

INSERT INTO payroll_ops.population_decision(
  tenant_id,
  population_resolution_id,
  payroll_cycle_id,
  payroll_assignment_version_id,
  payroll_relationship_version_id,
  employee_payroll_profile_id,
  pay_group_assignment_id,
  salary_assignment_id,
  salary_structure_version_id,
  decision,
  reason_code,
  reason_detail,
  created_at,
  created_by
)
SELECT
  member.tenant_id,
  member.population_resolution_id,
  member.payroll_cycle_id,
  member.payroll_assignment_version_id,
  member.payroll_relationship_version_id,
  member.employee_payroll_profile_id,
  member.pay_group_assignment_id,
  member.salary_assignment_id,
  salary_assignment.salary_structure_version_id,
  'INCLUDED',
  'INCLUDED',
  'Legacy population member preserved with exact approved configuration lineage',
  member.created_at,
  'v023-backfill'
FROM payroll_ops.population_member member
JOIN employee_payroll.salary_assignment salary_assignment
  ON salary_assignment.tenant_id = member.tenant_id
 AND salary_assignment.id = member.salary_assignment_id;

CREATE INDEX population_resolution_cycle_ix
  ON payroll_ops.population_resolution(
    tenant_id,
    payroll_cycle_id,
    attempt_no DESC
  );

CREATE INDEX population_decision_cycle_ix
  ON payroll_ops.population_decision(
    tenant_id,
    payroll_cycle_id,
    decision,
    reason_code
  );

CREATE INDEX population_member_resolution_ix
  ON payroll_ops.population_member(
    tenant_id,
    population_resolution_id,
    payroll_assignment_version_id
  );

CREATE OR REPLACE FUNCTION payroll_ops.reject_uncontrolled_population_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, payroll_ops AS $$
BEGIN
  IF current_setting('payroll_ops.population_mutation', true)
       IS DISTINCT FROM 'allowed' THEN
    RAISE EXCEPTION
      'payroll cycle and population state may change only through controlled commands'
      USING ERRCODE = '42501';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_cycle_controlled_mutation
  BEFORE UPDATE OR DELETE ON payroll_ops.payroll_cycle
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.reject_uncontrolled_population_mutation();

CREATE TRIGGER population_member_controlled_mutation
  BEFORE UPDATE OR DELETE ON payroll_ops.population_member
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.reject_uncontrolled_population_mutation();

CREATE TRIGGER population_resolution_controlled_mutation
  BEFORE UPDATE OR DELETE ON payroll_ops.population_resolution
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.reject_uncontrolled_population_mutation();

CREATE TRIGGER population_decision_immutable
  BEFORE UPDATE OR DELETE ON payroll_ops.population_decision
  FOR EACH ROW
  EXECUTE FUNCTION platform.reject_mutation();

CREATE OR REPLACE FUNCTION payroll_ops.create_regular_payroll_cycle(
  p_tenant_id uuid,
  p_pay_group_version_id uuid,
  p_pay_period_id uuid,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  platform AS $$
DECLARE
  v_cycle_id uuid := gen_random_uuid();
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_created_at IS NULL THEN
    RAISE EXCEPTION 'creation timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.pay_group_version group_version
    JOIN organisation.pay_period period
      ON period.tenant_id = group_version.tenant_id
     AND period.id = p_pay_period_id
     AND period.calendar_id = group_version.calendar_id
    WHERE group_version.tenant_id = p_tenant_id
      AND group_version.id = p_pay_group_version_id
      AND group_version.approval_status = 'APPROVED'
      AND group_version.effective_from <= period.period_start
      AND (
        group_version.effective_to IS NULL
        OR group_version.effective_to > period.period_end
      )
      AND period.status = 'OPEN'
      AND NOT EXISTS (
        SELECT 1
        FROM organisation.pay_group_version successor
        WHERE successor.tenant_id = group_version.tenant_id
          AND successor.supersedes_version_id = group_version.id
      )
  ) THEN
    RAISE EXCEPTION
      'cycle requires an approved current pay-group version and open compatible period'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO payroll_ops.payroll_cycle(
    id,
    tenant_id,
    pay_group_id,
    pay_period_id,
    cycle_type,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_cycle_id,
    p_tenant_id,
    p_pay_group_version_id,
    p_pay_period_id,
    'REGULAR',
    'DRAFT',
    p_created_at,
    p_actor,
    p_created_at,
    p_actor
  );

  RETURN v_cycle_id;
END $$;

CREATE OR REPLACE FUNCTION payroll_ops.resolve_payroll_population(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_resolved_at timestamptz
) RETURNS TABLE (
  resolution_id uuid,
  attempt_no integer,
  included_count integer,
  excluded_count integer,
  cycle_version_no bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  compensation,
  employee_payroll,
  platform AS $$
DECLARE
  v_status payroll_ops.cycle_status;
  v_version_no bigint;
  v_pay_group_version_id uuid;
  v_period_start date;
  v_period_end date;
  v_group_currency char(3);
  v_resolution_id uuid := gen_random_uuid();
  v_attempt_no integer;
  v_included integer;
  v_excluded integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_resolved_at IS NULL THEN
    RAISE EXCEPTION 'resolution timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    cycle.status,
    cycle.version_no,
    cycle.pay_group_id,
    period.period_start,
    period.period_end,
    group_version.currency
  INTO
    v_status,
    v_version_no,
    v_pay_group_version_id,
    v_period_start,
    v_period_end,
    v_group_currency
  FROM payroll_ops.payroll_cycle cycle
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = cycle.tenant_id
   AND group_version.id = cycle.pay_group_id
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
  FOR UPDATE OF cycle;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll cycle does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_version_no <> p_expected_version THEN
    RAISE EXCEPTION 'payroll cycle changed since it was read'
      USING ERRCODE = '40001';
  END IF;

  IF v_status NOT IN ('DRAFT', 'POPULATION_RESOLVED') THEN
    RAISE EXCEPTION
      'population can be resolved only before input sealing'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    WHERE snapshot.tenant_id = p_tenant_id
      AND snapshot.payroll_cycle_id = p_payroll_cycle_id
  ) THEN
    RAISE EXCEPTION
      'population cannot be resolved after any input snapshot exists'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.pay_group_version group_version
    WHERE group_version.tenant_id = p_tenant_id
      AND group_version.id = v_pay_group_version_id
      AND group_version.approval_status = 'APPROVED'
      AND group_version.effective_from <= v_period_start
      AND (
        group_version.effective_to IS NULL
        OR group_version.effective_to > v_period_end
      )
      AND NOT EXISTS (
        SELECT 1
        FROM organisation.pay_group_version successor
        WHERE successor.tenant_id = group_version.tenant_id
          AND successor.supersedes_version_id = group_version.id
      )
  ) THEN
    RAISE EXCEPTION
      'cycle pay-group version is no longer executable'
      USING ERRCODE = '23514';
  END IF;

  SELECT coalesce(max(existing.attempt_no), 0) + 1
  INTO v_attempt_no
  FROM payroll_ops.population_resolution existing
  WHERE existing.tenant_id = p_tenant_id
    AND existing.payroll_cycle_id = p_payroll_cycle_id;

  PERFORM set_config(
    'payroll_ops.population_mutation',
    'allowed',
    true
  );

  INSERT INTO payroll_ops.population_resolution(
    id,
    tenant_id,
    payroll_cycle_id,
    attempt_no,
    status,
    included_count,
    excluded_count,
    resolved_at,
    resolved_by,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_resolution_id,
    p_tenant_id,
    p_payroll_cycle_id,
    v_attempt_no,
    'BUILDING',
    0,
    0,
    p_resolved_at,
    p_actor,
    p_resolved_at,
    p_actor,
    p_resolved_at,
    p_actor
  );

  DELETE FROM payroll_ops.population_member member
  WHERE member.tenant_id = p_tenant_id
    AND member.payroll_cycle_id = p_payroll_cycle_id;

  WITH ranked_group_assignment AS (
    SELECT
      group_assignment.*,
      row_number() OVER (
        PARTITION BY group_assignment.payroll_assignment_version_id
        ORDER BY
          (
            group_assignment.approval_status = 'APPROVED'
            AND group_assignment.effective_from <= v_period_start
            AND (
              group_assignment.effective_to IS NULL
              OR group_assignment.effective_to > v_period_end
            )
          ) DESC,
          group_assignment.effective_from DESC,
          group_assignment.created_at DESC,
          group_assignment.id DESC
      ) AS choice_rank
    FROM employee_payroll.pay_group_assignment group_assignment
    WHERE group_assignment.tenant_id = p_tenant_id
      AND group_assignment.pay_group_version_id =
          v_pay_group_version_id
      AND NOT EXISTS (
        SELECT 1
        FROM employee_payroll.pay_group_assignment successor
        WHERE successor.tenant_id = group_assignment.tenant_id
          AND successor.supersedes_assignment_id = group_assignment.id
      )
  ),
  candidates AS (
    SELECT
      assignment_version.id AS payroll_assignment_version_id,
      assignment_identity.status AS assignment_identity_status,
      assignment_version.approval_status AS assignment_approval_status,
      assignment_version.assignment_start,
      assignment_version.assignment_end,
      EXISTS (
        SELECT 1
        FROM employee_payroll.payroll_assignment_version successor
        WHERE successor.tenant_id = assignment_version.tenant_id
          AND successor.supersedes_version_id = assignment_version.id
      ) AS assignment_superseded,
      relationship_version.id AS payroll_relationship_version_id,
      relationship_identity.status AS relationship_identity_status,
      relationship_version.approval_status AS relationship_approval_status,
      relationship_version.relationship_start,
      relationship_version.relationship_end,
      EXISTS (
        SELECT 1
        FROM employee_payroll.payroll_relationship_version successor
        WHERE successor.tenant_id = relationship_version.tenant_id
          AND successor.supersedes_version_id = relationship_version.id
      ) AS relationship_superseded,
      profile.id AS employee_payroll_profile_id,
      profile.payroll_status,
      profile.currency AS profile_currency,
      group_assignment.id AS pay_group_assignment_id,
      group_assignment.approval_status AS group_assignment_status,
      group_assignment.effective_from AS group_effective_from,
      group_assignment.effective_to AS group_effective_to,
      salary_assignment.id AS salary_assignment_id,
      salary_assignment.approval_status AS salary_assignment_status,
      salary_assignment.effective_from AS salary_effective_from,
      salary_assignment.effective_to AS salary_effective_to,
      salary_assignment.currency AS salary_currency,
      salary_assignment.salary_structure_version_id,
      salary_assignment.structure_approval_status,
      salary_assignment.structure_effective_from,
      salary_assignment.structure_effective_to,
      salary_assignment.structure_currency,
      salary_assignment.structure_has_lines
    FROM ranked_group_assignment group_assignment
    JOIN employee_payroll.payroll_assignment_version assignment_version
      ON assignment_version.tenant_id = group_assignment.tenant_id
     AND assignment_version.id =
         group_assignment.payroll_assignment_version_id
    JOIN employee_payroll.payroll_assignment assignment_identity
      ON assignment_identity.tenant_id = assignment_version.tenant_id
     AND assignment_identity.id = assignment_version.payroll_assignment_id
    JOIN employee_payroll.payroll_relationship_version relationship_version
      ON relationship_version.tenant_id = assignment_version.tenant_id
     AND relationship_version.id =
         assignment_version.payroll_relationship_version_id
    JOIN employee_payroll.payroll_relationship relationship_identity
      ON relationship_identity.tenant_id = relationship_version.tenant_id
     AND relationship_identity.id =
         relationship_version.payroll_relationship_id
    LEFT JOIN employee_payroll.employee_payroll_profile profile
      ON profile.tenant_id = relationship_identity.tenant_id
     AND profile.payroll_relationship_id = relationship_identity.id
    LEFT JOIN LATERAL (
      SELECT
        candidate.id,
        candidate.approval_status,
        candidate.effective_from,
        candidate.effective_to,
        candidate.currency,
        candidate.salary_structure_version_id,
        structure_version.approval_status AS structure_approval_status,
        structure_version.effective_from AS structure_effective_from,
        structure_version.effective_to AS structure_effective_to,
        structure_version.currency AS structure_currency,
        EXISTS (
          SELECT 1
          FROM compensation.salary_structure_line line
          WHERE line.tenant_id = structure_version.tenant_id
            AND line.salary_structure_version_id = structure_version.id
        ) AS structure_has_lines
      FROM employee_payroll.salary_assignment candidate
      LEFT JOIN compensation.salary_structure_version structure_version
        ON structure_version.tenant_id = candidate.tenant_id
       AND structure_version.id = candidate.salary_structure_version_id
      WHERE candidate.tenant_id = assignment_version.tenant_id
        AND candidate.payroll_assignment_version_id = assignment_version.id
        AND NOT EXISTS (
          SELECT 1
          FROM employee_payroll.salary_assignment successor
          WHERE successor.tenant_id = candidate.tenant_id
            AND successor.supersedes_assignment_id = candidate.id
        )
      ORDER BY
        (
          candidate.approval_status = 'APPROVED'
          AND candidate.effective_from <= v_period_start
          AND (
            candidate.effective_to IS NULL
            OR candidate.effective_to > v_period_end
          )
        ) DESC,
        candidate.effective_from DESC,
        candidate.created_at DESC,
        candidate.id DESC
      LIMIT 1
    ) salary_assignment ON true
    WHERE group_assignment.choice_rank = 1
  ),
  evaluated AS (
    SELECT
      candidate.*,
      CASE
        WHEN candidate.assignment_identity_status <> 'ACTIVE'
          THEN 'ASSIGNMENT_INACTIVE'
        WHEN candidate.assignment_approval_status <> 'APPROVED'
          THEN 'ASSIGNMENT_NOT_APPROVED'
        WHEN candidate.assignment_superseded
          THEN 'ASSIGNMENT_SUPERSEDED'
        WHEN candidate.assignment_start > v_period_start
          OR (
            candidate.assignment_end IS NOT NULL
            AND candidate.assignment_end <= v_period_end
          ) THEN 'ASSIGNMENT_OUT_OF_RANGE'
        WHEN candidate.relationship_identity_status <> 'ACTIVE'
          THEN 'RELATIONSHIP_INACTIVE'
        WHEN candidate.relationship_approval_status <> 'APPROVED'
          THEN 'RELATIONSHIP_NOT_APPROVED'
        WHEN candidate.relationship_superseded
          THEN 'RELATIONSHIP_SUPERSEDED'
        WHEN candidate.relationship_start > v_period_start
          OR (
            candidate.relationship_end IS NOT NULL
            AND candidate.relationship_end <= v_period_end
          ) THEN 'RELATIONSHIP_OUT_OF_RANGE'
        WHEN candidate.employee_payroll_profile_id IS NULL
          THEN 'PROFILE_MISSING'
        WHEN candidate.payroll_status <> 'READY'
          THEN 'PROFILE_NOT_READY'
        WHEN candidate.group_assignment_status <> 'APPROVED'
          THEN 'PAY_GROUP_ASSIGNMENT_NOT_APPROVED'
        WHEN candidate.group_effective_from > v_period_start
          OR (
            candidate.group_effective_to IS NOT NULL
            AND candidate.group_effective_to <= v_period_end
          ) THEN 'PAY_GROUP_ASSIGNMENT_OUT_OF_RANGE'
        WHEN candidate.salary_assignment_id IS NULL
          THEN 'SALARY_ASSIGNMENT_MISSING'
        WHEN candidate.salary_assignment_status <> 'APPROVED'
          THEN 'SALARY_ASSIGNMENT_NOT_APPROVED'
        WHEN candidate.salary_effective_from > v_period_start
          OR (
            candidate.salary_effective_to IS NOT NULL
            AND candidate.salary_effective_to <= v_period_end
          ) THEN 'SALARY_ASSIGNMENT_OUT_OF_RANGE'
        WHEN candidate.salary_structure_version_id IS NULL
          OR candidate.structure_approval_status IS NULL
          THEN 'SALARY_STRUCTURE_MISSING'
        WHEN candidate.structure_approval_status <> 'APPROVED'
          THEN 'SALARY_STRUCTURE_NOT_APPROVED'
        WHEN candidate.structure_effective_from > v_period_start
          OR (
            candidate.structure_effective_to IS NOT NULL
            AND candidate.structure_effective_to <= v_period_end
          ) THEN 'SALARY_STRUCTURE_OUT_OF_RANGE'
        WHEN NOT candidate.structure_has_lines
          THEN 'SALARY_STRUCTURE_EMPTY'
        WHEN candidate.profile_currency <> v_group_currency
          OR candidate.salary_currency <> v_group_currency
          OR candidate.structure_currency <> v_group_currency
          THEN 'CURRENCY_MISMATCH'
        ELSE 'INCLUDED'
      END AS reason_code
    FROM candidates candidate
  )
  INSERT INTO payroll_ops.population_decision(
    tenant_id,
    population_resolution_id,
    payroll_cycle_id,
    payroll_assignment_version_id,
    payroll_relationship_version_id,
    employee_payroll_profile_id,
    pay_group_assignment_id,
    salary_assignment_id,
    salary_structure_version_id,
    decision,
    reason_code,
    reason_detail,
    created_at,
    created_by
  )
  SELECT
    p_tenant_id,
    v_resolution_id,
    p_payroll_cycle_id,
    evaluated.payroll_assignment_version_id,
    evaluated.payroll_relationship_version_id,
    evaluated.employee_payroll_profile_id,
    evaluated.pay_group_assignment_id,
    evaluated.salary_assignment_id,
    evaluated.salary_structure_version_id,
    CASE
      WHEN evaluated.reason_code = 'INCLUDED'
        THEN 'INCLUDED'
      ELSE 'EXCLUDED'
    END,
    evaluated.reason_code,
    CASE
      WHEN evaluated.reason_code = 'INCLUDED'
        THEN 'All required approved configuration covers the complete payroll period'
      ELSE initcap(replace(lower(evaluated.reason_code), '_', ' '))
    END,
    p_resolved_at,
    p_actor
  FROM evaluated;

  INSERT INTO payroll_ops.population_member(
    id,
    tenant_id,
    payroll_cycle_id,
    payroll_assignment_version_id,
    population_resolution_id,
    payroll_relationship_version_id,
    employee_payroll_profile_id,
    pay_group_assignment_id,
    salary_assignment_id,
    inclusion_reason,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
  )
  SELECT
    gen_random_uuid(),
    decision.tenant_id,
    decision.payroll_cycle_id,
    decision.payroll_assignment_version_id,
    decision.population_resolution_id,
    decision.payroll_relationship_version_id,
    decision.employee_payroll_profile_id,
    decision.pay_group_assignment_id,
    decision.salary_assignment_id,
    'READY_CONFIGURATION',
    'INCLUDED',
    p_resolved_at,
    p_actor,
    p_resolved_at,
    p_actor
  FROM payroll_ops.population_decision decision
  WHERE decision.tenant_id = p_tenant_id
    AND decision.population_resolution_id = v_resolution_id
    AND decision.decision = 'INCLUDED';

  SELECT
    count(*) FILTER (WHERE decision = 'INCLUDED')::integer,
    count(*) FILTER (WHERE decision = 'EXCLUDED')::integer
  INTO v_included, v_excluded
  FROM payroll_ops.population_decision
  WHERE tenant_id = p_tenant_id
    AND population_resolution_id = v_resolution_id;

  UPDATE payroll_ops.population_resolution
  SET status = 'COMPLETED',
      included_count = v_included,
      excluded_count = v_excluded,
      updated_at = p_resolved_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE tenant_id = p_tenant_id
    AND id = v_resolution_id;

  UPDATE payroll_ops.payroll_cycle
  SET active_population_resolution_id = v_resolution_id,
      status = 'POPULATION_RESOLVED',
      updated_at = p_resolved_at,
      updated_by = p_actor,
      version_no = version_no + 1
  WHERE tenant_id = p_tenant_id
    AND id = p_payroll_cycle_id;

  RETURN QUERY
  SELECT
    v_resolution_id,
    v_attempt_no,
    v_included,
    v_excluded,
    v_version_no + 1;
END $$;

ALTER TABLE payroll_ops.population_resolution
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_resolution
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON payroll_ops.population_resolution
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE payroll_ops.population_decision
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_decision
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON payroll_ops.population_decision
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

REVOKE ALL ON FUNCTION payroll_ops.create_regular_payroll_cycle(
  uuid,
  uuid,
  uuid,
  varchar,
  timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_ops.resolve_payroll_population(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION payroll_ops.create_regular_payroll_cycle(
  uuid,
  uuid,
  uuid,
  varchar,
  timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION payroll_ops.resolve_payroll_population(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

GRANT SELECT ON
  payroll_ops.payroll_cycle,
  payroll_ops.population_member,
  payroll_ops.population_resolution,
  payroll_ops.population_decision
TO payroll_app;

REVOKE INSERT, UPDATE, DELETE ON
  payroll_ops.payroll_cycle,
  payroll_ops.population_member,
  payroll_ops.population_resolution,
  payroll_ops.population_decision
FROM payroll_app;

REVOKE CREATE ON SCHEMA payroll_ops FROM payroll_app;

ALTER TABLE organisation.pay_group_version FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_period FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_relationship_version FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.payroll_assignment_version FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.employee_payroll_profile FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.pay_group_assignment FORCE ROW LEVEL SECURITY;
ALTER TABLE employee_payroll.salary_assignment FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.payroll_cycle FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_member FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE payroll_ops.population_resolution IS
  'Immutable evidence header for one deterministic payroll population-resolution attempt.';
COMMENT ON TABLE payroll_ops.population_decision IS
  'Immutable inclusion/exclusion decision and exact configuration lineage for one payroll assignment version.';
COMMENT ON FUNCTION payroll_ops.create_regular_payroll_cycle(
  uuid,
  uuid,
  uuid,
  varchar,
  timestamptz
) IS
  'Creates one tenant-scoped regular payroll cycle for an approved exact pay-group version and open compatible period.';
COMMENT ON FUNCTION payroll_ops.resolve_payroll_population(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) IS
  'Deterministically resolves pre-seal payroll population, preserves immutable decision evidence and replaces only the active included-member set.';
