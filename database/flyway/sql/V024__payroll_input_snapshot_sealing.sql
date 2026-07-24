-- S3-02A immutable payroll input-snapshot sealing foundation.
--
-- V007 introduced immutable JSON input snapshots. V021 repointed them to the
-- exact payroll-assignment version, and V023 established immutable population
-- resolution evidence. This migration links each snapshot to that evidence,
-- seals one canonical payload per included member, detects configuration drift
-- between population resolution and sealing, and advances the cycle only through
-- a controlled optimistic command.

ALTER TABLE organisation.pay_period NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure NO FORCE ROW LEVEL SECURITY;
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
ALTER TABLE payroll_ops.population_resolution NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_decision NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot NO FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    GROUP BY
      snapshot.tenant_id,
      snapshot.payroll_cycle_id,
      snapshot.payroll_assignment_version_id
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION
      'V024 requires at most one legacy input snapshot per cycle member';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    LEFT JOIN payroll_ops.payroll_cycle cycle
      ON cycle.tenant_id = snapshot.tenant_id
     AND cycle.id = snapshot.payroll_cycle_id
    LEFT JOIN payroll_ops.population_member member
      ON member.tenant_id = snapshot.tenant_id
     AND member.payroll_cycle_id = snapshot.payroll_cycle_id
     AND member.payroll_assignment_version_id =
         snapshot.payroll_assignment_version_id
    LEFT JOIN payroll_ops.population_decision decision
      ON decision.tenant_id = member.tenant_id
     AND decision.population_resolution_id = member.population_resolution_id
     AND decision.payroll_assignment_version_id =
         member.payroll_assignment_version_id
     AND decision.decision = 'INCLUDED'
    WHERE cycle.id IS NULL
       OR member.id IS NULL
       OR decision.id IS NULL
       OR cycle.active_population_resolution_id IS DISTINCT FROM
          member.population_resolution_id
  ) THEN
    RAISE EXCEPTION
      'legacy input snapshots cannot be linked to active included population evidence';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    WHERE EXISTS (
      SELECT 1
      FROM payroll_ops.input_snapshot snapshot
      WHERE snapshot.tenant_id = cycle.tenant_id
        AND snapshot.payroll_cycle_id = cycle.id
    )
      AND (
        SELECT count(*)
        FROM payroll_ops.input_snapshot snapshot
        WHERE snapshot.tenant_id = cycle.tenant_id
          AND snapshot.payroll_cycle_id = cycle.id
      ) <> (
        SELECT count(*)
        FROM payroll_ops.population_member member
        WHERE member.tenant_id = cycle.tenant_id
          AND member.payroll_cycle_id = cycle.id
          AND member.population_resolution_id =
              cycle.active_population_resolution_id
      )
  ) THEN
    RAISE EXCEPTION
      'legacy input snapshots must cover the complete active included population';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    WHERE cycle.status IN ('INPUTS_SEALED', 'CALCULATING', 'CALCULATED')
      AND NOT EXISTS (
        SELECT 1
        FROM payroll_ops.input_snapshot snapshot
        WHERE snapshot.tenant_id = cycle.tenant_id
          AND snapshot.payroll_cycle_id = cycle.id
      )
  ) THEN
    RAISE EXCEPTION
      'sealed or calculated legacy cycles require input snapshots before V024';
  END IF;
END $$;

ALTER TABLE payroll_ops.input_snapshot
  ADD COLUMN payload_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN population_resolution_id uuid,
  ADD COLUMN population_member_id uuid,
  ADD COLUMN population_decision_id uuid,
  ADD COLUMN payroll_relationship_version_id uuid,
  ADD COLUMN employee_payroll_profile_id uuid,
  ADD COLUMN pay_group_assignment_id uuid,
  ADD COLUMN salary_assignment_id uuid,
  ADD COLUMN salary_structure_version_id uuid;

ALTER TABLE payroll_ops.payroll_cycle
  ADD COLUMN input_sealed_at timestamptz,
  ADD COLUMN input_sealed_by varchar(160),
  ADD COLUMN input_snapshot_count integer,
  ADD COLUMN input_snapshot_set_hash char(64);

DROP TRIGGER input_snapshot_immutable
  ON payroll_ops.input_snapshot;

UPDATE payroll_ops.input_snapshot snapshot
SET population_resolution_id = member.population_resolution_id,
    population_member_id = member.id,
    population_decision_id = decision.id,
    payroll_relationship_version_id =
      member.payroll_relationship_version_id,
    employee_payroll_profile_id = member.employee_payroll_profile_id,
    pay_group_assignment_id = member.pay_group_assignment_id,
    salary_assignment_id = member.salary_assignment_id,
    salary_structure_version_id = decision.salary_structure_version_id,
    snapshot_hash = encode(
      public.digest(snapshot.snapshot_payload::text, 'sha256'::text),
      'hex'
    ),
    payload_schema_version = 0
FROM payroll_ops.population_member member
JOIN payroll_ops.population_decision decision
  ON decision.tenant_id = member.tenant_id
 AND decision.population_resolution_id = member.population_resolution_id
 AND decision.payroll_assignment_version_id =
     member.payroll_assignment_version_id
 AND decision.decision = 'INCLUDED'
WHERE member.tenant_id = snapshot.tenant_id
  AND member.payroll_cycle_id = snapshot.payroll_cycle_id
  AND member.payroll_assignment_version_id =
      snapshot.payroll_assignment_version_id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot
    WHERE population_resolution_id IS NULL
       OR population_member_id IS NULL
       OR population_decision_id IS NULL
       OR payroll_relationship_version_id IS NULL
       OR employee_payroll_profile_id IS NULL
       OR pay_group_assignment_id IS NULL
       OR salary_assignment_id IS NULL
       OR salary_structure_version_id IS NULL
  ) THEN
    RAISE EXCEPTION
      'legacy input snapshots could not be backfilled with exact population lineage';
  END IF;
END $$;

ALTER TABLE payroll_ops.population_member
  ADD CONSTRAINT population_member_snapshot_lineage_uk
    UNIQUE (
      tenant_id,
      id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      population_resolution_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id
    );

ALTER TABLE payroll_ops.population_decision
  ADD CONSTRAINT population_decision_snapshot_lineage_uk
    UNIQUE (
      tenant_id,
      id,
      payroll_cycle_id,
      population_resolution_id,
      payroll_assignment_version_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id,
      salary_structure_version_id
    );

ALTER TABLE payroll_ops.input_snapshot
  ALTER COLUMN population_resolution_id SET NOT NULL,
  ALTER COLUMN population_member_id SET NOT NULL,
  ALTER COLUMN population_decision_id SET NOT NULL,
  ALTER COLUMN payroll_relationship_version_id SET NOT NULL,
  ALTER COLUMN employee_payroll_profile_id SET NOT NULL,
  ALTER COLUMN pay_group_assignment_id SET NOT NULL,
  ALTER COLUMN salary_assignment_id SET NOT NULL,
  ALTER COLUMN salary_structure_version_id SET NOT NULL,
  ADD CONSTRAINT input_snapshot_payload_schema_ck
    CHECK (payload_schema_version IN (0, 1)),
  ADD CONSTRAINT input_snapshot_hash_shape_ck
    CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT input_snapshot_payload_hash_ck
    CHECK (
      snapshot_hash = encode(
        public.digest(snapshot_payload::text, 'sha256'::text),
        'hex'
      )
    ),
  ADD CONSTRAINT input_snapshot_one_per_member_uk
    UNIQUE (
      tenant_id,
      payroll_cycle_id,
      payroll_assignment_version_id
    ),
  ADD CONSTRAINT input_snapshot_member_lineage_fk
    FOREIGN KEY (
      tenant_id,
      population_member_id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      population_resolution_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id
    ) REFERENCES payroll_ops.population_member(
      tenant_id,
      id,
      payroll_cycle_id,
      payroll_assignment_version_id,
      population_resolution_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id
    ),
  ADD CONSTRAINT input_snapshot_decision_lineage_fk
    FOREIGN KEY (
      tenant_id,
      population_decision_id,
      payroll_cycle_id,
      population_resolution_id,
      payroll_assignment_version_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id,
      salary_structure_version_id
    ) REFERENCES payroll_ops.population_decision(
      tenant_id,
      id,
      payroll_cycle_id,
      population_resolution_id,
      payroll_assignment_version_id,
      payroll_relationship_version_id,
      employee_payroll_profile_id,
      pay_group_assignment_id,
      salary_assignment_id,
      salary_structure_version_id
    );


SELECT set_config(
  'payroll_ops.population_mutation',
  'allowed',
  true
);

WITH sealed AS (
  SELECT
    snapshot.tenant_id,
    snapshot.payroll_cycle_id,
    max(snapshot.sealed_at) AS sealed_at,
    count(*)::integer AS snapshot_count,
    encode(
      public.digest(
        string_agg(
          snapshot.payroll_assignment_version_id::text
            || ':' || snapshot.snapshot_hash,
          '|'
          ORDER BY snapshot.payroll_assignment_version_id
        ),
        'sha256'::text
      ),
      'hex'
    ) AS set_hash
  FROM payroll_ops.input_snapshot snapshot
  GROUP BY snapshot.tenant_id, snapshot.payroll_cycle_id
)
UPDATE payroll_ops.payroll_cycle cycle
SET status = CASE
      WHEN cycle.status IN ('DRAFT', 'POPULATION_RESOLVED')
        THEN 'INPUTS_SEALED'::payroll_ops.cycle_status
      ELSE cycle.status
    END,
    input_sealed_at = sealed.sealed_at,
    input_sealed_by = 'v024-backfill',
    input_snapshot_count = sealed.snapshot_count,
    input_snapshot_set_hash = sealed.set_hash,
    updated_at = greatest(cycle.updated_at, sealed.sealed_at),
    updated_by = 'v024-backfill',
    version_no = cycle.version_no + CASE
      WHEN cycle.status IN ('DRAFT', 'POPULATION_RESOLVED') THEN 1
      ELSE 0
    END
FROM sealed
WHERE sealed.tenant_id = cycle.tenant_id
  AND sealed.payroll_cycle_id = cycle.id;

ALTER TABLE payroll_ops.payroll_cycle
  ADD CONSTRAINT payroll_cycle_input_seal_shape_ck
    CHECK (
      (
        input_sealed_at IS NULL
        AND input_sealed_by IS NULL
        AND input_snapshot_count IS NULL
        AND input_snapshot_set_hash IS NULL
      )
      OR (
        input_sealed_at IS NOT NULL
        AND input_sealed_by IS NOT NULL
        AND btrim(input_sealed_by) <> ''
        AND input_snapshot_count IS NOT NULL
        AND input_snapshot_count > 0
        AND input_snapshot_set_hash IS NOT NULL
        AND input_snapshot_set_hash ~ '^[0-9a-f]{64}$'
      )
    ),
  ADD CONSTRAINT payroll_cycle_input_seal_status_ck
    CHECK (
      input_sealed_at IS NULL
      OR status IN ('INPUTS_SEALED', 'CALCULATING', 'CALCULATED', 'FAILED')
    ),
  ADD CONSTRAINT payroll_cycle_sealed_state_metadata_ck
    CHECK (
      status NOT IN ('INPUTS_SEALED', 'CALCULATING', 'CALCULATED')
      OR input_sealed_at IS NOT NULL
    );


CREATE TRIGGER input_snapshot_immutable
  BEFORE UPDATE OR DELETE ON payroll_ops.input_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE INDEX input_snapshot_resolution_ix
  ON payroll_ops.input_snapshot(
    tenant_id,
    population_resolution_id,
    payroll_assignment_version_id
  );

CREATE INDEX input_snapshot_structure_ix
  ON payroll_ops.input_snapshot(
    tenant_id,
    salary_structure_version_id,
    sealed_at
  );

CREATE OR REPLACE FUNCTION payroll_ops.seal_payroll_inputs(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_sealed_at timestamptz
) RETURNS TABLE (
  snapshot_count integer,
  combined_hash char(64),
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
  v_pay_period_id uuid;
  v_resolution_id uuid;
  v_period_start date;
  v_period_end date;
  v_payment_date date;
  v_period_status varchar(20);
  v_included_count integer;
  v_member_count integer;
  v_snapshot_count integer;
  v_combined_hash char(64);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_sealed_at IS NULL THEN
    RAISE EXCEPTION 'seal timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    cycle.status,
    cycle.version_no,
    cycle.pay_group_id,
    cycle.pay_period_id,
    cycle.active_population_resolution_id,
    period.period_start,
    period.period_end,
    period.payment_date,
    period.status
  INTO
    v_status,
    v_version_no,
    v_pay_group_version_id,
    v_pay_period_id,
    v_resolution_id,
    v_period_start,
    v_period_end,
    v_payment_date,
    v_period_status
  FROM payroll_ops.payroll_cycle cycle
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
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

  IF v_status <> 'POPULATION_RESOLVED' THEN
    RAISE EXCEPTION
      'inputs can be sealed only for a population-resolved cycle'
      USING ERRCODE = '23514';
  END IF;

  IF v_resolution_id IS NULL THEN
    RAISE EXCEPTION 'cycle has no active population resolution'
      USING ERRCODE = '23514';
  END IF;

  IF v_period_status <> 'OPEN' THEN
    RAISE EXCEPTION 'inputs require an open payroll period'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    WHERE snapshot.tenant_id = p_tenant_id
      AND snapshot.payroll_cycle_id = p_payroll_cycle_id
  ) THEN
    RAISE EXCEPTION 'payroll inputs are already sealed'
      USING ERRCODE = '23514';
  END IF;

  SELECT resolution.included_count
  INTO v_included_count
  FROM payroll_ops.population_resolution resolution
  WHERE resolution.tenant_id = p_tenant_id
    AND resolution.id = v_resolution_id
    AND resolution.payroll_cycle_id = p_payroll_cycle_id
    AND resolution.status = 'COMPLETED';

  IF NOT FOUND OR v_included_count < 1 THEN
    RAISE EXCEPTION
      'inputs cannot be sealed without a completed non-empty population'
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*)::integer
  INTO v_member_count
  FROM payroll_ops.population_member member
  WHERE member.tenant_id = p_tenant_id
    AND member.payroll_cycle_id = p_payroll_cycle_id
    AND member.population_resolution_id = v_resolution_id;

  IF v_member_count <> v_included_count THEN
    RAISE EXCEPTION
      'active population does not match its immutable resolution evidence'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.population_member member
    JOIN payroll_ops.population_decision decision
      ON decision.tenant_id = member.tenant_id
     AND decision.population_resolution_id =
         member.population_resolution_id
     AND decision.payroll_assignment_version_id =
         member.payroll_assignment_version_id
    JOIN employee_payroll.payroll_assignment_version assignment_version
      ON assignment_version.tenant_id = member.tenant_id
     AND assignment_version.id = member.payroll_assignment_version_id
    JOIN employee_payroll.payroll_assignment assignment_identity
      ON assignment_identity.tenant_id = assignment_version.tenant_id
     AND assignment_identity.id = assignment_version.payroll_assignment_id
    JOIN employee_payroll.payroll_relationship_version relationship_version
      ON relationship_version.tenant_id = member.tenant_id
     AND relationship_version.id = member.payroll_relationship_version_id
    JOIN employee_payroll.payroll_relationship relationship_identity
      ON relationship_identity.tenant_id = relationship_version.tenant_id
     AND relationship_identity.id =
         relationship_version.payroll_relationship_id
    JOIN employee_payroll.employee_payroll_profile profile
      ON profile.tenant_id = member.tenant_id
     AND profile.id = member.employee_payroll_profile_id
    JOIN employee_payroll.pay_group_assignment group_assignment
      ON group_assignment.tenant_id = member.tenant_id
     AND group_assignment.id = member.pay_group_assignment_id
    JOIN employee_payroll.salary_assignment salary_assignment
      ON salary_assignment.tenant_id = member.tenant_id
     AND salary_assignment.id = member.salary_assignment_id
    JOIN compensation.salary_structure_version structure_version
      ON structure_version.tenant_id = decision.tenant_id
     AND structure_version.id = decision.salary_structure_version_id
    JOIN compensation.salary_structure structure_identity
      ON structure_identity.tenant_id = structure_version.tenant_id
     AND structure_identity.id = structure_version.salary_structure_id
    JOIN organisation.pay_group_version group_version
      ON group_version.tenant_id = member.tenant_id
     AND group_version.id = v_pay_group_version_id
    JOIN organisation.pay_group group_identity
      ON group_identity.tenant_id = group_version.tenant_id
     AND group_identity.id = group_version.pay_group_id
    WHERE member.tenant_id = p_tenant_id
      AND member.payroll_cycle_id = p_payroll_cycle_id
      AND member.population_resolution_id = v_resolution_id
      AND (
        decision.decision <> 'INCLUDED'
        OR decision.reason_code <> 'INCLUDED'
        OR decision.payroll_cycle_id <> member.payroll_cycle_id
        OR decision.population_resolution_id <>
           member.population_resolution_id
        OR decision.payroll_relationship_version_id <>
           member.payroll_relationship_version_id
        OR decision.employee_payroll_profile_id <>
           member.employee_payroll_profile_id
        OR decision.pay_group_assignment_id <>
           member.pay_group_assignment_id
        OR decision.salary_assignment_id <>
           member.salary_assignment_id
        OR assignment_identity.status <> 'ACTIVE'
        OR assignment_version.approval_status <> 'APPROVED'
        OR assignment_version.assignment_start > v_period_start
        OR assignment_version.assignment_end IS NOT NULL
           AND assignment_version.assignment_end <= v_period_end
        OR EXISTS (
          SELECT 1
          FROM employee_payroll.payroll_assignment_version successor
          WHERE successor.tenant_id = assignment_version.tenant_id
            AND successor.supersedes_version_id = assignment_version.id
        )
        OR relationship_identity.status <> 'ACTIVE'
        OR relationship_version.approval_status <> 'APPROVED'
        OR relationship_version.relationship_start > v_period_start
        OR relationship_version.relationship_end IS NOT NULL
           AND relationship_version.relationship_end <= v_period_end
        OR EXISTS (
          SELECT 1
          FROM employee_payroll.payroll_relationship_version successor
          WHERE successor.tenant_id = relationship_version.tenant_id
            AND successor.supersedes_version_id = relationship_version.id
        )
        OR profile.payroll_status <> 'READY'
        OR profile.currency <> group_version.currency
        OR group_identity.status <> 'ACTIVE'
        OR group_version.approval_status <> 'APPROVED'
        OR group_version.effective_from > v_period_start
        OR group_version.effective_to IS NOT NULL
           AND group_version.effective_to <= v_period_end
        OR EXISTS (
          SELECT 1
          FROM organisation.pay_group_version successor
          WHERE successor.tenant_id = group_version.tenant_id
            AND successor.supersedes_version_id = group_version.id
        )
        OR group_assignment.pay_group_version_id <>
           v_pay_group_version_id
        OR group_assignment.approval_status <> 'APPROVED'
        OR group_assignment.effective_from > v_period_start
        OR group_assignment.effective_to IS NOT NULL
           AND group_assignment.effective_to <= v_period_end
        OR EXISTS (
          SELECT 1
          FROM employee_payroll.pay_group_assignment successor
          WHERE successor.tenant_id = group_assignment.tenant_id
            AND successor.supersedes_assignment_id = group_assignment.id
        )
        OR salary_assignment.approval_status <> 'APPROVED'
        OR salary_assignment.effective_from > v_period_start
        OR salary_assignment.effective_to IS NOT NULL
           AND salary_assignment.effective_to <= v_period_end
        OR salary_assignment.currency <> group_version.currency
        OR salary_assignment.salary_structure_version_id <>
           decision.salary_structure_version_id
        OR EXISTS (
          SELECT 1
          FROM employee_payroll.salary_assignment successor
          WHERE successor.tenant_id = salary_assignment.tenant_id
            AND successor.supersedes_assignment_id = salary_assignment.id
        )
        OR structure_identity.status <> 'ACTIVE'
        OR structure_version.approval_status <> 'APPROVED'
        OR structure_version.effective_from > v_period_start
        OR structure_version.effective_to IS NOT NULL
           AND structure_version.effective_to <= v_period_end
        OR structure_version.currency <> group_version.currency
        OR EXISTS (
          SELECT 1
          FROM compensation.salary_structure_version successor
          WHERE successor.tenant_id = structure_version.tenant_id
            AND successor.supersedes_version_id = structure_version.id
        )
        OR NOT EXISTS (
          SELECT 1
          FROM compensation.salary_structure_line line
          JOIN compensation.pay_component_version component_version
            ON component_version.tenant_id = line.tenant_id
           AND component_version.id = line.component_version_id
          WHERE line.tenant_id = structure_version.tenant_id
            AND line.salary_structure_version_id = structure_version.id
            AND line.effective_from <= v_period_start
            AND (
              line.effective_to IS NULL
              OR line.effective_to > v_period_end
            )
            AND component_version.approval_status = 'APPROVED'
            AND component_version.effective_from <= v_period_start
            AND (
              component_version.effective_to IS NULL
              OR component_version.effective_to > v_period_end
            )
            AND NOT EXISTS (
              SELECT 1
              FROM compensation.pay_component_version successor
              WHERE successor.tenant_id = component_version.tenant_id
                AND successor.supersedes_version_id = component_version.id
            )
        )
        OR EXISTS (
          SELECT 1
          FROM compensation.salary_structure_line line
          LEFT JOIN compensation.pay_component_version component_version
            ON component_version.tenant_id = line.tenant_id
           AND component_version.id = line.component_version_id
          WHERE line.tenant_id = structure_version.tenant_id
            AND line.salary_structure_version_id = structure_version.id
            AND (
              line.effective_from > v_period_start
              OR line.effective_to IS NOT NULL
                 AND line.effective_to <= v_period_end
              OR component_version.id IS NULL
              OR component_version.approval_status <> 'APPROVED'
              OR component_version.effective_from > v_period_start
              OR component_version.effective_to IS NOT NULL
                 AND component_version.effective_to <= v_period_end
              OR EXISTS (
                SELECT 1
                FROM compensation.pay_component_version successor
                WHERE successor.tenant_id = component_version.tenant_id
                  AND successor.supersedes_version_id = component_version.id
              )
            )
        )
      )
  ) THEN
    RAISE EXCEPTION
      'payroll configuration changed after population resolution'
      USING ERRCODE = '23514';
  END IF;

  WITH prepared AS (
    SELECT
      member.id AS population_member_id,
      decision.id AS population_decision_id,
      member.payroll_assignment_version_id,
      member.payroll_relationship_version_id,
      member.employee_payroll_profile_id,
      member.pay_group_assignment_id,
      member.salary_assignment_id,
      decision.salary_structure_version_id,
      jsonb_build_object(
        'schemaVersion', 1,
        'tenantId', p_tenant_id::text,
        'payrollCycleId', p_payroll_cycle_id::text,
        'populationResolutionId', v_resolution_id::text,
        'payPeriod', jsonb_build_object(
          'id', v_pay_period_id::text,
          'periodStart', v_period_start,
          'periodEnd', v_period_end,
          'paymentDate', v_payment_date
        ),
        'payGroup', jsonb_build_object(
          'versionId', group_version.id::text,
          'identityId', group_version.pay_group_id::text,
          'versionSequence', group_version.version_sequence,
          'versionNo', group_version.version_no,
          'currency', group_version.currency,
          'prorationMethod', group_version.proration_method
        ),
        'payrollRelationship', jsonb_build_object(
          'identityId', relationship_identity.id::text,
          'versionId', relationship_version.id::text,
          'employeeNumber', relationship_identity.employee_number,
          'versionSequence', relationship_version.version_sequence,
          'versionNo', relationship_version.version_no
        ),
        'payrollAssignment', jsonb_build_object(
          'identityId', assignment_identity.id::text,
          'versionId', assignment_version.id::text,
          'assignmentNumber', assignment_identity.assignment_number,
          'versionSequence', assignment_version.version_sequence,
          'versionNo', assignment_version.version_no,
          'establishmentVersionId',
            assignment_version.establishment_version_id::text
        ),
        'profile', jsonb_build_object(
          'id', profile.id::text,
          'versionNo', profile.version_no,
          'currency', profile.currency,
          'payrollStatus', profile.payroll_status
        ),
        'payGroupAssignment', jsonb_build_object(
          'id', group_assignment.id::text,
          'versionNo', group_assignment.version_no,
          'effectiveFrom', group_assignment.effective_from,
          'effectiveTo', group_assignment.effective_to
        ),
        'salaryAssignment', jsonb_build_object(
          'id', salary_assignment.id::text,
          'versionNo', salary_assignment.version_no,
          'monthlyAmount', salary_assignment.monthly_amount,
          'currency', salary_assignment.currency,
          'effectiveFrom', salary_assignment.effective_from,
          'effectiveTo', salary_assignment.effective_to
        ),
        'salaryStructure', jsonb_build_object(
          'identityId', structure_version.salary_structure_id::text,
          'versionId', structure_version.id::text,
          'versionSequence', structure_version.version_sequence,
          'versionNo', structure_version.version_no,
          'currency', structure_version.currency,
          'lines', (
            SELECT jsonb_agg(
              jsonb_build_object(
                'lineId', line.id::text,
                'sequenceNo', line.sequence_no,
                'targetAmount', line.target_amount,
                'targetPercentage', line.target_percentage,
                'percentageBaseCode', line.percentage_base_code,
                'component', jsonb_build_object(
                  'identityId', component.id::text,
                  'versionId', component_version.id::text,
                  'code', component.code,
                  'name', component.name,
                  'componentType', component.component_type,
                  'versionSequence', component_version.version_sequence,
                  'versionNo', component_version.version_no,
                  'formulaType', component_version.formula_type,
                  'formulaExpression', component_version.formula_expression,
                  'fixedAmount', component_version.fixed_amount,
                  'roundingScale', component_version.rounding_scale
                )
              )
              ORDER BY line.sequence_no, line.id
            )
            FROM compensation.salary_structure_line line
            JOIN compensation.pay_component_version component_version
              ON component_version.tenant_id = line.tenant_id
             AND component_version.id = line.component_version_id
            JOIN compensation.pay_component component
              ON component.tenant_id = component_version.tenant_id
             AND component.id = component_version.component_id
            WHERE line.tenant_id = structure_version.tenant_id
              AND line.salary_structure_version_id = structure_version.id
              AND line.effective_from <= v_period_start
              AND (
                line.effective_to IS NULL
                OR line.effective_to > v_period_end
              )
          )
        )
      ) AS payload
    FROM payroll_ops.population_member member
    JOIN payroll_ops.population_decision decision
      ON decision.tenant_id = member.tenant_id
     AND decision.population_resolution_id = member.population_resolution_id
     AND decision.payroll_assignment_version_id =
         member.payroll_assignment_version_id
     AND decision.decision = 'INCLUDED'
    JOIN employee_payroll.payroll_assignment_version assignment_version
      ON assignment_version.tenant_id = member.tenant_id
     AND assignment_version.id = member.payroll_assignment_version_id
    JOIN employee_payroll.payroll_assignment assignment_identity
      ON assignment_identity.tenant_id = assignment_version.tenant_id
     AND assignment_identity.id = assignment_version.payroll_assignment_id
    JOIN employee_payroll.payroll_relationship_version relationship_version
      ON relationship_version.tenant_id = member.tenant_id
     AND relationship_version.id = member.payroll_relationship_version_id
    JOIN employee_payroll.payroll_relationship relationship_identity
      ON relationship_identity.tenant_id = relationship_version.tenant_id
     AND relationship_identity.id =
         relationship_version.payroll_relationship_id
    JOIN employee_payroll.employee_payroll_profile profile
      ON profile.tenant_id = member.tenant_id
     AND profile.id = member.employee_payroll_profile_id
    JOIN employee_payroll.pay_group_assignment group_assignment
      ON group_assignment.tenant_id = member.tenant_id
     AND group_assignment.id = member.pay_group_assignment_id
    JOIN employee_payroll.salary_assignment salary_assignment
      ON salary_assignment.tenant_id = member.tenant_id
     AND salary_assignment.id = member.salary_assignment_id
    JOIN compensation.salary_structure_version structure_version
      ON structure_version.tenant_id = decision.tenant_id
     AND structure_version.id = decision.salary_structure_version_id
    JOIN organisation.pay_group_version group_version
      ON group_version.tenant_id = member.tenant_id
     AND group_version.id = v_pay_group_version_id
    WHERE member.tenant_id = p_tenant_id
      AND member.payroll_cycle_id = p_payroll_cycle_id
      AND member.population_resolution_id = v_resolution_id
  ),
  hashed AS (
    SELECT
      prepared.*,
      encode(public.digest(prepared.payload::text, 'sha256'::text), 'hex') AS payload_hash
    FROM prepared
  )
  INSERT INTO payroll_ops.input_snapshot(
    tenant_id,
    payroll_cycle_id,
    payroll_assignment_version_id,
    snapshot_hash,
    snapshot_payload,
    sealed_at,
    created_at,
    created_by,
    updated_at,
    updated_by,
    payload_schema_version,
    population_resolution_id,
    population_member_id,
    population_decision_id,
    payroll_relationship_version_id,
    employee_payroll_profile_id,
    pay_group_assignment_id,
    salary_assignment_id,
    salary_structure_version_id
  )
  SELECT
    p_tenant_id,
    p_payroll_cycle_id,
    hashed.payroll_assignment_version_id,
    hashed.payload_hash,
    hashed.payload,
    p_sealed_at,
    p_sealed_at,
    p_actor,
    p_sealed_at,
    p_actor,
    1,
    v_resolution_id,
    hashed.population_member_id,
    hashed.population_decision_id,
    hashed.payroll_relationship_version_id,
    hashed.employee_payroll_profile_id,
    hashed.pay_group_assignment_id,
    hashed.salary_assignment_id,
    hashed.salary_structure_version_id
  FROM hashed;

  GET DIAGNOSTICS v_snapshot_count = ROW_COUNT;

  IF v_snapshot_count <> v_included_count THEN
    RAISE EXCEPTION
      'sealed snapshot count does not match the active population'
      USING ERRCODE = '23514';
  END IF;

  SELECT encode(
      public.digest(
        string_agg(
          snapshot.payroll_assignment_version_id::text
            || ':' || snapshot.snapshot_hash,
          '|'
          ORDER BY snapshot.payroll_assignment_version_id
        ),
        'sha256'::text
      ),
      'hex'
    )
  INTO v_combined_hash
  FROM payroll_ops.input_snapshot snapshot
  WHERE snapshot.tenant_id = p_tenant_id
    AND snapshot.payroll_cycle_id = p_payroll_cycle_id;

  PERFORM set_config(
    'payroll_ops.population_mutation',
    'allowed',
    true
  );

  UPDATE payroll_ops.payroll_cycle cycle
  SET status = 'INPUTS_SEALED',
      input_sealed_at = p_sealed_at,
      input_sealed_by = p_actor,
      input_snapshot_count = v_snapshot_count,
      input_snapshot_set_hash = v_combined_hash,
      updated_at = p_sealed_at,
      updated_by = p_actor,
      version_no = cycle.version_no + 1
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.version_no = p_expected_version
    AND cycle.status = 'POPULATION_RESOLVED';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll cycle changed while inputs were being sealed'
      USING ERRCODE = '40001';
  END IF;

  RETURN QUERY
  SELECT
    v_snapshot_count,
    v_combined_hash,
    v_version_no + 1;
END $$;

REVOKE ALL ON FUNCTION payroll_ops.seal_payroll_inputs(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION payroll_ops.seal_payroll_inputs(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

GRANT SELECT ON payroll_ops.input_snapshot TO payroll_app;
REVOKE INSERT, UPDATE, DELETE
  ON payroll_ops.input_snapshot
  FROM payroll_app;

REVOKE CREATE ON SCHEMA payroll_ops FROM payroll_app;

ALTER TABLE organisation.pay_period FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_group_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure FORCE ROW LEVEL SECURITY;
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
ALTER TABLE payroll_ops.population_resolution FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_decision FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot FORCE ROW LEVEL SECURITY;

COMMENT ON COLUMN payroll_ops.input_snapshot.payload_schema_version IS
  'Canonical payroll-input payload schema. Version 0 marks preserved pre-V024 snapshots; version 1 is produced only by seal_payroll_inputs.';
COMMENT ON COLUMN payroll_ops.input_snapshot.population_resolution_id IS
  'Immutable population-resolution attempt whose INCLUDED decision produced the snapshot.';
COMMENT ON FUNCTION payroll_ops.seal_payroll_inputs(
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) IS
  'Validates active population configuration, rejects drift, writes one canonical immutable input snapshot per included assignment and advances the cycle to INPUTS_SEALED.';
