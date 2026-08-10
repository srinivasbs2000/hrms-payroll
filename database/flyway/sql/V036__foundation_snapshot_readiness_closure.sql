-- P5-FSR-01 G01 immutable foundation-configuration snapshot and calculation binding.
--
-- Forward-only from V035. V001-V035 remain immutable.
-- This migration creates one immutable configuration snapshot per executable
-- payroll cycle, binds new input/calculation evidence to that snapshot, and
-- preserves existing historical hashes while backfilling exact snapshot lineage.
-- Composed foundation readiness is delivered in the subsequent bounded FSR
-- increment; this migration establishes the deterministic configuration base.

ALTER TABLE organisation.pay_group_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_calendar NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_period NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.legal_entity_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_jurisdiction_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE statutory.registration_type_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE statutory.registration_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line NO FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.payroll_cycle NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_resolution NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_decision NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_request NO FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.payroll_result NO FORCE ROW LEVEL SECURITY;

CREATE TABLE payroll_ops.foundation_configuration_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  population_resolution_id uuid NOT NULL,
  pay_group_version_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  payroll_calendar_id uuid NOT NULL,
  payroll_statutory_unit_version_id uuid NOT NULL,
  legal_entity_version_id uuid NOT NULL,
  snapshot_schema_version smallint NOT NULL DEFAULT 1,
  configuration_version_set jsonb NOT NULL,
  configuration_count integer NOT NULL,
  snapshot_payload jsonb NOT NULL,
  snapshot_hash char(64) NOT NULL,
  sealed_at timestamptz NOT NULL,
  sealed_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_cycle_id),
  UNIQUE (tenant_id, id, snapshot_hash),
  UNIQUE (tenant_id, payroll_cycle_id),
  CHECK (snapshot_schema_version = 1),
  CHECK (jsonb_typeof(configuration_version_set) = 'array'),
  CHECK (configuration_count = jsonb_array_length(configuration_version_set)),
  CHECK (configuration_count >= 6),
  CHECK (jsonb_typeof(snapshot_payload) = 'object'),
  CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    snapshot_hash = encode(
      public.digest(snapshot_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CHECK (btrim(sealed_by) <> ''),
  CHECK (btrim(created_by) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_cycle_id)
    REFERENCES payroll_ops.payroll_cycle(tenant_id, id),
  FOREIGN KEY (
    tenant_id,
    population_resolution_id,
    payroll_cycle_id
  ) REFERENCES payroll_ops.population_resolution(
    tenant_id,
    id,
    payroll_cycle_id
  ),
  FOREIGN KEY (tenant_id, pay_group_version_id)
    REFERENCES organisation.pay_group_version(tenant_id, id),
  FOREIGN KEY (tenant_id, pay_period_id)
    REFERENCES organisation.pay_period(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_calendar_id)
    REFERENCES organisation.payroll_calendar(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_statutory_unit_version_id)
    REFERENCES organisation.payroll_statutory_unit_version(tenant_id, id),
  FOREIGN KEY (tenant_id, legal_entity_version_id)
    REFERENCES organisation.legal_entity_version(tenant_id, id)
);

CREATE INDEX foundation_configuration_snapshot_lookup_ix
  ON payroll_ops.foundation_configuration_snapshot(
    tenant_id,
    payroll_cycle_id,
    sealed_at DESC
  );

CREATE TRIGGER foundation_configuration_snapshot_immutable
  BEFORE UPDATE OR DELETE
  ON payroll_ops.foundation_configuration_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE OR REPLACE FUNCTION payroll_ops.foundation_configuration_payload(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid
) RETURNS jsonb
LANGUAGE sql
STABLE
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  compensation,
  statutory AS $$
  SELECT jsonb_build_object(
    'schemaVersion', 1,
    'payrollCycleId', cycle.id::text,
    'populationResolutionId', resolution.id::text,
    'payGroupVersion', to_jsonb(group_version),
    'payrollCalendar', to_jsonb(calendar),
    'payPeriod', to_jsonb(period),
    'payrollStatutoryUnitVersion', to_jsonb(psu_version),
    'legalEntityVersion', to_jsonb(legal_version),
    'populationResolution', jsonb_build_object(
      'id', resolution.id::text,
      'attemptNo', resolution.attempt_no,
      'status', resolution.status,
      'includedCount', resolution.included_count,
      'excludedCount', resolution.excluded_count,
      'resolvedAt', resolution.resolved_at,
      'resolvedBy', resolution.resolved_by
    ),
    'registrationVersions', coalesce((
      SELECT jsonb_agg(
        jsonb_build_object(
          'registrationVersion', to_jsonb(registration_version),
          'registrationTypeVersion', to_jsonb(registration_type_version),
          'payrollJurisdictionVersion', to_jsonb(jurisdiction_version)
        )
        ORDER BY registration_version.registration_type_id,
                 registration_version.id
      )
      FROM statutory.registration_version registration_version
      JOIN statutory.registration_type_version registration_type_version
        ON registration_type_version.tenant_id = registration_version.tenant_id
       AND registration_type_version.id =
           registration_version.registration_type_version_id
      JOIN organisation.payroll_jurisdiction_version jurisdiction_version
        ON jurisdiction_version.tenant_id = registration_version.tenant_id
       AND jurisdiction_version.id =
           registration_version.payroll_jurisdiction_version_id
      WHERE registration_version.tenant_id = cycle.tenant_id
        AND registration_version.lifecycle_status = 'ACTIVE'
        AND registration_version.effective_from <= period.period_start
        AND (
          registration_version.effective_to IS NULL
          OR registration_version.effective_to > period.period_end
        )
        AND (
          (
            registration_version.owner_kind = 'LEGAL_ENTITY'
            AND registration_version.legal_entity_id = legal_version.legal_entity_id
          )
          OR (
            registration_version.owner_kind = 'PAYROLL_STATUTORY_UNIT'
            AND registration_version.payroll_statutory_unit_id =
                psu_version.payroll_statutory_unit_id
          )
        )
    ), '[]'::jsonb),
    'salaryStructures', coalesce((
      SELECT jsonb_agg(
        jsonb_build_object(
          'version', to_jsonb(structure_version),
          'lines', coalesce((
            SELECT jsonb_agg(
              jsonb_build_object(
                'line', to_jsonb(line),
                'componentVersion', to_jsonb(component_version)
              )
              ORDER BY line.sequence_no, line.id
            )
            FROM compensation.salary_structure_line line
            JOIN compensation.pay_component_version component_version
              ON component_version.tenant_id = line.tenant_id
             AND component_version.id = line.component_version_id
            WHERE line.tenant_id = structure_version.tenant_id
              AND line.salary_structure_version_id = structure_version.id
          ), '[]'::jsonb)
        )
        ORDER BY structure_version.id
      )
      FROM compensation.salary_structure_version structure_version
      WHERE structure_version.tenant_id = cycle.tenant_id
        AND structure_version.id IN (
          SELECT DISTINCT decision.salary_structure_version_id
          FROM payroll_ops.population_decision decision
          WHERE decision.tenant_id = cycle.tenant_id
            AND decision.payroll_cycle_id = cycle.id
            AND decision.population_resolution_id = resolution.id
            AND decision.decision = 'INCLUDED'
        )
    ), '[]'::jsonb)
  )
  FROM payroll_ops.payroll_cycle cycle
  JOIN payroll_ops.population_resolution resolution
    ON resolution.tenant_id = cycle.tenant_id
   AND resolution.id = cycle.active_population_resolution_id
   AND resolution.payroll_cycle_id = cycle.id
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = cycle.tenant_id
   AND group_version.id = cycle.pay_group_id
  JOIN organisation.payroll_calendar calendar
    ON calendar.tenant_id = group_version.tenant_id
   AND calendar.id = group_version.calendar_id
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
   AND period.calendar_id = calendar.id
  JOIN organisation.payroll_statutory_unit_version psu_version
    ON psu_version.tenant_id = group_version.tenant_id
   AND psu_version.id = group_version.payroll_statutory_unit_version_id
  JOIN organisation.legal_entity_version legal_version
    ON legal_version.tenant_id = psu_version.tenant_id
   AND legal_version.id = psu_version.legal_entity_version_id
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
$$;

CREATE OR REPLACE FUNCTION payroll_ops.foundation_configuration_version_set(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid
) RETURNS jsonb
LANGUAGE sql
STABLE
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  compensation,
  statutory AS $$
  SELECT
    jsonb_build_array(
      jsonb_build_object(
        'kind', 'PAY_GROUP_VERSION',
        'id', group_version.id::text
      ),
      jsonb_build_object(
        'kind', 'PAYROLL_CALENDAR',
        'id', calendar.id::text
      ),
      jsonb_build_object(
        'kind', 'PAY_PERIOD',
        'id', period.id::text
      ),
      jsonb_build_object(
        'kind', 'PAYROLL_STATUTORY_UNIT_VERSION',
        'id', psu_version.id::text
      ),
      jsonb_build_object(
        'kind', 'LEGAL_ENTITY_VERSION',
        'id', legal_version.id::text
      ),
      jsonb_build_object(
        'kind', 'POPULATION_RESOLUTION',
        'id', resolution.id::text
      )
    )
    || coalesce((
      SELECT jsonb_agg(
        jsonb_build_object(
          'kind', 'SALARY_STRUCTURE_VERSION',
          'id', ids.salary_structure_version_id::text
        )
        ORDER BY ids.salary_structure_version_id
      )
      FROM (
        SELECT DISTINCT decision.salary_structure_version_id
        FROM payroll_ops.population_decision decision
        WHERE decision.tenant_id = cycle.tenant_id
          AND decision.payroll_cycle_id = cycle.id
          AND decision.population_resolution_id = resolution.id
          AND decision.decision = 'INCLUDED'
      ) ids
    ), '[]'::jsonb)
    || coalesce((
      SELECT jsonb_agg(
        jsonb_build_object(
          'kind', 'PAY_COMPONENT_VERSION',
          'id', ids.component_version_id::text
        )
        ORDER BY ids.component_version_id
      )
      FROM (
        SELECT DISTINCT line.component_version_id
        FROM payroll_ops.population_decision decision
        JOIN compensation.salary_structure_line line
          ON line.tenant_id = decision.tenant_id
         AND line.salary_structure_version_id =
             decision.salary_structure_version_id
        WHERE decision.tenant_id = cycle.tenant_id
          AND decision.payroll_cycle_id = cycle.id
          AND decision.population_resolution_id = resolution.id
          AND decision.decision = 'INCLUDED'
      ) ids
    ), '[]'::jsonb)
    || coalesce((
      SELECT jsonb_agg(item ORDER BY kind, id)
      FROM (
        SELECT DISTINCT
          'REGISTRATION_VERSION'::text AS kind,
          registration_version.id::text AS id,
          jsonb_build_object(
            'kind', 'REGISTRATION_VERSION',
            'id', registration_version.id::text
          ) AS item
        FROM statutory.registration_version registration_version
        WHERE registration_version.tenant_id = cycle.tenant_id
          AND registration_version.lifecycle_status = 'ACTIVE'
          AND registration_version.effective_from <= period.period_start
          AND (
            registration_version.effective_to IS NULL
            OR registration_version.effective_to > period.period_end
          )
          AND (
            (registration_version.owner_kind = 'LEGAL_ENTITY'
             AND registration_version.legal_entity_id = legal_version.legal_entity_id)
            OR
            (registration_version.owner_kind = 'PAYROLL_STATUTORY_UNIT'
             AND registration_version.payroll_statutory_unit_id =
                 psu_version.payroll_statutory_unit_id)
          )
        UNION
        SELECT DISTINCT
          'REGISTRATION_TYPE_VERSION',
          registration_version.registration_type_version_id::text,
          jsonb_build_object(
            'kind', 'REGISTRATION_TYPE_VERSION',
            'id', registration_version.registration_type_version_id::text
          )
        FROM statutory.registration_version registration_version
        WHERE registration_version.tenant_id = cycle.tenant_id
          AND registration_version.lifecycle_status = 'ACTIVE'
          AND registration_version.effective_from <= period.period_start
          AND (
            registration_version.effective_to IS NULL
            OR registration_version.effective_to > period.period_end
          )
          AND (
            (registration_version.owner_kind = 'LEGAL_ENTITY'
             AND registration_version.legal_entity_id = legal_version.legal_entity_id)
            OR
            (registration_version.owner_kind = 'PAYROLL_STATUTORY_UNIT'
             AND registration_version.payroll_statutory_unit_id =
                 psu_version.payroll_statutory_unit_id)
          )
        UNION
        SELECT DISTINCT
          'PAYROLL_JURISDICTION_VERSION',
          registration_version.payroll_jurisdiction_version_id::text,
          jsonb_build_object(
            'kind', 'PAYROLL_JURISDICTION_VERSION',
            'id', registration_version.payroll_jurisdiction_version_id::text
          )
        FROM statutory.registration_version registration_version
        WHERE registration_version.tenant_id = cycle.tenant_id
          AND registration_version.lifecycle_status = 'ACTIVE'
          AND registration_version.effective_from <= period.period_start
          AND (
            registration_version.effective_to IS NULL
            OR registration_version.effective_to > period.period_end
          )
          AND (
            (registration_version.owner_kind = 'LEGAL_ENTITY'
             AND registration_version.legal_entity_id = legal_version.legal_entity_id)
            OR
            (registration_version.owner_kind = 'PAYROLL_STATUTORY_UNIT'
             AND registration_version.payroll_statutory_unit_id =
                 psu_version.payroll_statutory_unit_id)
          )
      ) registration_items
    ), '[]'::jsonb)
  FROM payroll_ops.payroll_cycle cycle
  JOIN payroll_ops.population_resolution resolution
    ON resolution.tenant_id = cycle.tenant_id
   AND resolution.id = cycle.active_population_resolution_id
   AND resolution.payroll_cycle_id = cycle.id
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = cycle.tenant_id
   AND group_version.id = cycle.pay_group_id
  JOIN organisation.payroll_calendar calendar
    ON calendar.tenant_id = group_version.tenant_id
   AND calendar.id = group_version.calendar_id
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
   AND period.calendar_id = calendar.id
  JOIN organisation.payroll_statutory_unit_version psu_version
    ON psu_version.tenant_id = group_version.tenant_id
   AND psu_version.id = group_version.payroll_statutory_unit_version_id
  JOIN organisation.legal_entity_version legal_version
    ON legal_version.tenant_id = psu_version.tenant_id
   AND legal_version.id = psu_version.legal_entity_version_id
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
$$;

ALTER TABLE payroll_ops.payroll_cycle
  ADD COLUMN foundation_config_snapshot_id uuid,
  ADD COLUMN foundation_config_snapshot_hash char(64),
  ADD COLUMN foundation_config_sealed_at timestamptz,
  ADD COLUMN foundation_config_sealed_by varchar(160),
  ADD COLUMN foundation_config_count integer;

ALTER TABLE payroll_ops.input_snapshot
  ADD COLUMN foundation_config_snapshot_id uuid,
  ADD COLUMN foundation_config_snapshot_hash char(64);

ALTER TABLE payroll_calc.calculation_request
  ADD COLUMN foundation_config_snapshot_id uuid,
  ADD COLUMN foundation_config_snapshot_hash char(64);

ALTER TABLE payroll_calc.payroll_result
  ADD COLUMN foundation_config_snapshot_id uuid,
  ADD COLUMN foundation_config_snapshot_hash char(64);

-- Existing sealed/calculated cycles are history-preservingly bound to a
-- deterministic V036 configuration snapshot without changing any pre-V036
-- input/result payload or hash.
WITH candidate AS (
  SELECT
    cycle.tenant_id,
    cycle.id AS payroll_cycle_id,
    cycle.active_population_resolution_id AS population_resolution_id,
    cycle.pay_group_id AS pay_group_version_id,
    cycle.pay_period_id,
    group_version.calendar_id AS payroll_calendar_id,
    group_version.payroll_statutory_unit_version_id,
    psu_version.legal_entity_version_id,
    payroll_ops.foundation_configuration_version_set(
      cycle.tenant_id,
      cycle.id
    ) AS version_set,
    payroll_ops.foundation_configuration_payload(
      cycle.tenant_id,
      cycle.id
    ) AS payload,
    coalesce(cycle.input_sealed_at, cycle.updated_at) AS sealed_at
  FROM payroll_ops.payroll_cycle cycle
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = cycle.tenant_id
   AND group_version.id = cycle.pay_group_id
  JOIN organisation.payroll_statutory_unit_version psu_version
    ON psu_version.tenant_id = group_version.tenant_id
   AND psu_version.id = group_version.payroll_statutory_unit_version_id
  WHERE EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot snapshot
    WHERE snapshot.tenant_id = cycle.tenant_id
      AND snapshot.payroll_cycle_id = cycle.id
  )
)
INSERT INTO payroll_ops.foundation_configuration_snapshot(
  id,
  tenant_id,
  payroll_cycle_id,
  population_resolution_id,
  pay_group_version_id,
  pay_period_id,
  payroll_calendar_id,
  payroll_statutory_unit_version_id,
  legal_entity_version_id,
  snapshot_schema_version,
  configuration_version_set,
  configuration_count,
  snapshot_payload,
  snapshot_hash,
  sealed_at,
  sealed_by,
  created_at,
  created_by
)
SELECT
  gen_random_uuid(),
  candidate.tenant_id,
  candidate.payroll_cycle_id,
  candidate.population_resolution_id,
  candidate.pay_group_version_id,
  candidate.pay_period_id,
  candidate.payroll_calendar_id,
  candidate.payroll_statutory_unit_version_id,
  candidate.legal_entity_version_id,
  1,
  candidate.version_set,
  jsonb_array_length(candidate.version_set),
  candidate.payload,
  encode(
    public.digest(candidate.payload::text, 'sha256'::text),
    'hex'
  ),
  candidate.sealed_at,
  'v036-backfill',
  candidate.sealed_at,
  'v036-backfill'
FROM candidate;

SELECT set_config(
  'payroll_ops.population_mutation',
  'allowed',
  true
);

UPDATE payroll_ops.payroll_cycle cycle
SET foundation_config_snapshot_id = snapshot.id,
    foundation_config_snapshot_hash = snapshot.snapshot_hash,
    foundation_config_sealed_at = snapshot.sealed_at,
    foundation_config_sealed_by = snapshot.sealed_by,
    foundation_config_count = snapshot.configuration_count
FROM payroll_ops.foundation_configuration_snapshot snapshot
WHERE snapshot.tenant_id = cycle.tenant_id
  AND snapshot.payroll_cycle_id = cycle.id;

ALTER TABLE payroll_ops.input_snapshot DISABLE TRIGGER USER;
UPDATE payroll_ops.input_snapshot input
SET foundation_config_snapshot_id = snapshot.id,
    foundation_config_snapshot_hash = snapshot.snapshot_hash
FROM payroll_ops.foundation_configuration_snapshot snapshot
WHERE snapshot.tenant_id = input.tenant_id
  AND snapshot.payroll_cycle_id = input.payroll_cycle_id;
ALTER TABLE payroll_ops.input_snapshot ENABLE TRIGGER USER;

ALTER TABLE payroll_calc.calculation_request DISABLE TRIGGER USER;
UPDATE payroll_calc.calculation_request request
SET foundation_config_snapshot_id = snapshot.id,
    foundation_config_snapshot_hash = snapshot.snapshot_hash
FROM payroll_ops.foundation_configuration_snapshot snapshot
WHERE snapshot.tenant_id = request.tenant_id
  AND snapshot.payroll_cycle_id = request.payroll_cycle_id;
ALTER TABLE payroll_calc.calculation_request ENABLE TRIGGER USER;

ALTER TABLE payroll_calc.payroll_result DISABLE TRIGGER USER;
UPDATE payroll_calc.payroll_result result
SET foundation_config_snapshot_id = snapshot.id,
    foundation_config_snapshot_hash = snapshot.snapshot_hash
FROM payroll_ops.foundation_configuration_snapshot snapshot
WHERE snapshot.tenant_id = result.tenant_id
  AND snapshot.payroll_cycle_id = result.payroll_cycle_id;
ALTER TABLE payroll_calc.payroll_result ENABLE TRIGGER USER;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot
    WHERE payload_schema_version = 1
      AND (
        foundation_config_snapshot_id IS NULL
        OR foundation_config_snapshot_hash IS NULL
      )
  ) THEN
    RAISE EXCEPTION
      'V036 could not bind every historical input snapshot to foundation configuration';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.calculation_request
    WHERE request_schema_version = 1
      AND (
        foundation_config_snapshot_id IS NULL
        OR foundation_config_snapshot_hash IS NULL
      )
  ) THEN
    RAISE EXCEPTION
      'V036 could not bind every historical calculation request to foundation configuration';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_calc.payroll_result
    WHERE result_schema_version = 1
      AND (
        foundation_config_snapshot_id IS NULL
        OR foundation_config_snapshot_hash IS NULL
      )
  ) THEN
    RAISE EXCEPTION
      'V036 could not bind every historical payroll result to foundation configuration';
  END IF;
END $$;

ALTER TABLE payroll_ops.input_snapshot
  ADD CONSTRAINT input_snapshot_foundation_shape_ck
    CHECK (
      payload_schema_version = 0
      OR (
        foundation_config_snapshot_id IS NOT NULL
        AND foundation_config_snapshot_hash IS NOT NULL
        AND foundation_config_snapshot_hash ~ '^[0-9a-f]{64}$'
      )
    ),
  ADD CONSTRAINT input_snapshot_foundation_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      foundation_config_snapshot_id,
      foundation_config_snapshot_hash
    ) REFERENCES payroll_ops.foundation_configuration_snapshot(
      tenant_id,
      id,
      snapshot_hash
    );

ALTER TABLE payroll_calc.calculation_request
  ADD CONSTRAINT calculation_request_foundation_shape_ck
    CHECK (
      request_schema_version = 0
      OR (
        foundation_config_snapshot_id IS NOT NULL
        AND foundation_config_snapshot_hash IS NOT NULL
        AND foundation_config_snapshot_hash ~ '^[0-9a-f]{64}$'
      )
    ),
  ADD CONSTRAINT calculation_request_foundation_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      foundation_config_snapshot_id,
      foundation_config_snapshot_hash
    ) REFERENCES payroll_ops.foundation_configuration_snapshot(
      tenant_id,
      id,
      snapshot_hash
    );

ALTER TABLE payroll_calc.payroll_result
  ADD CONSTRAINT payroll_result_foundation_shape_ck
    CHECK (
      result_schema_version = 0
      OR (
        foundation_config_snapshot_id IS NOT NULL
        AND foundation_config_snapshot_hash IS NOT NULL
        AND foundation_config_snapshot_hash ~ '^[0-9a-f]{64}$'
      )
    ),
  ADD CONSTRAINT payroll_result_foundation_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      foundation_config_snapshot_id,
      foundation_config_snapshot_hash
    ) REFERENCES payroll_ops.foundation_configuration_snapshot(
      tenant_id,
      id,
      snapshot_hash
    );

ALTER TABLE payroll_ops.foundation_configuration_snapshot
  ADD CONSTRAINT foundation_snapshot_cycle_lineage_uk
    UNIQUE (
      tenant_id,
      id,
      payroll_cycle_id,
      snapshot_hash
    );

ALTER TABLE payroll_ops.payroll_cycle
  ADD CONSTRAINT payroll_cycle_foundation_snapshot_shape_ck
    CHECK (
      (
        foundation_config_snapshot_id IS NULL
        AND foundation_config_snapshot_hash IS NULL
        AND foundation_config_sealed_at IS NULL
        AND foundation_config_sealed_by IS NULL
        AND foundation_config_count IS NULL
      )
      OR (
        foundation_config_snapshot_id IS NOT NULL
        AND foundation_config_snapshot_hash IS NOT NULL
        AND foundation_config_snapshot_hash ~ '^[0-9a-f]{64}$'
        AND foundation_config_sealed_at IS NOT NULL
        AND foundation_config_sealed_by IS NOT NULL
        AND btrim(foundation_config_sealed_by) <> ''
        AND foundation_config_count IS NOT NULL
        AND foundation_config_count >= 6
      )
    ),
  ADD CONSTRAINT payroll_cycle_foundation_snapshot_status_ck
    CHECK (
      foundation_config_snapshot_id IS NULL
      OR status IN (
        'POPULATION_RESOLVED',
        'INPUTS_SEALED',
        'CALCULATING',
        'CALCULATED',
        'FAILED'
      )
    ),
  ADD CONSTRAINT payroll_cycle_foundation_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      foundation_config_snapshot_id,
      id,
      foundation_config_snapshot_hash
    ) REFERENCES payroll_ops.foundation_configuration_snapshot(
      tenant_id,
      id,
      payroll_cycle_id,
      snapshot_hash
    );

CREATE OR REPLACE FUNCTION payroll_ops.ensure_foundation_configuration_snapshot(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_actor varchar,
  p_sealed_at timestamptz
) RETURNS TABLE (
  snapshot_id uuid,
  snapshot_hash char(64),
  configuration_count integer
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_ops,
  organisation,
  compensation,
  statutory,
  platform AS $$
DECLARE
  v_status payroll_ops.cycle_status;
  v_resolution_id uuid;
  v_pay_group_version_id uuid;
  v_pay_period_id uuid;
  v_calendar_id uuid;
  v_psu_version_id uuid;
  v_legal_version_id uuid;
  v_period_start date;
  v_period_end date;
  v_period_status varchar(20);
  v_resolution_status varchar(20);
  v_included_count integer;
  v_member_count integer;
  v_payload jsonb;
  v_version_set jsonb;
  v_snapshot_id uuid;
  v_snapshot_hash char(64);
  v_configuration_count integer;
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
    RAISE EXCEPTION 'configuration seal timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT
    cycle.foundation_config_snapshot_id,
    cycle.foundation_config_snapshot_hash,
    cycle.foundation_config_count
  INTO
    v_snapshot_id,
    v_snapshot_hash,
    v_configuration_count
  FROM payroll_ops.payroll_cycle cycle
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id;

  IF FOUND AND v_snapshot_id IS NOT NULL THEN
    RETURN QUERY
    SELECT v_snapshot_id, v_snapshot_hash, v_configuration_count;
    RETURN;
  END IF;

  SELECT
    cycle.status,
    cycle.active_population_resolution_id,
    cycle.pay_group_id,
    cycle.pay_period_id,
    group_version.calendar_id,
    group_version.payroll_statutory_unit_version_id,
    psu_version.legal_entity_version_id,
    period.period_start,
    period.period_end,
    period.status,
    resolution.status,
    resolution.included_count
  INTO
    v_status,
    v_resolution_id,
    v_pay_group_version_id,
    v_pay_period_id,
    v_calendar_id,
    v_psu_version_id,
    v_legal_version_id,
    v_period_start,
    v_period_end,
    v_period_status,
    v_resolution_status,
    v_included_count
  FROM payroll_ops.payroll_cycle cycle
  JOIN payroll_ops.population_resolution resolution
    ON resolution.tenant_id = cycle.tenant_id
   AND resolution.id = cycle.active_population_resolution_id
   AND resolution.payroll_cycle_id = cycle.id
  JOIN organisation.pay_group_version group_version
    ON group_version.tenant_id = cycle.tenant_id
   AND group_version.id = cycle.pay_group_id
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
   AND period.calendar_id = group_version.calendar_id
  JOIN organisation.payroll_statutory_unit_version psu_version
    ON psu_version.tenant_id = group_version.tenant_id
   AND psu_version.id = group_version.payroll_statutory_unit_version_id
  JOIN organisation.legal_entity_version legal_version
    ON legal_version.tenant_id = psu_version.tenant_id
   AND legal_version.id = psu_version.legal_entity_version_id
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payroll cycle does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_status <> 'POPULATION_RESOLVED' THEN
    RAISE EXCEPTION
      'foundation configuration can be sealed only after population resolution'
      USING ERRCODE = '23514';
  END IF;

  IF v_resolution_status <> 'COMPLETED' OR v_included_count < 1 THEN
    RAISE EXCEPTION
      'foundation configuration requires a completed non-empty population'
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
      'foundation configuration requires complete active population evidence'
      USING ERRCODE = '23514';
  END IF;

  IF v_period_status <> 'OPEN' THEN
    RAISE EXCEPTION
      'foundation configuration requires an open payroll period'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM organisation.pay_group_version group_version
    JOIN organisation.payroll_statutory_unit_version psu_version
      ON psu_version.tenant_id = group_version.tenant_id
     AND psu_version.id = group_version.payroll_statutory_unit_version_id
    JOIN organisation.legal_entity_version legal_version
      ON legal_version.tenant_id = psu_version.tenant_id
     AND legal_version.id = psu_version.legal_entity_version_id
    WHERE group_version.tenant_id = p_tenant_id
      AND group_version.id = v_pay_group_version_id
      AND group_version.approval_status = 'APPROVED'
      AND group_version.effective_from <= v_period_start
      AND (group_version.effective_to IS NULL
           OR group_version.effective_to > v_period_end)
      AND psu_version.approval_status = 'APPROVED'
      AND psu_version.effective_from <= v_period_start
      AND (psu_version.effective_to IS NULL
           OR psu_version.effective_to > v_period_end)
      AND legal_version.approval_status = 'APPROVED'
      AND legal_version.effective_from <= v_period_start
      AND (legal_version.effective_to IS NULL
           OR legal_version.effective_to > v_period_end)
  ) THEN
    RAISE EXCEPTION
      'pay-group, PSU and legal-employer configuration must remain approved and effective'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.population_decision decision
    LEFT JOIN compensation.salary_structure_version structure_version
      ON structure_version.tenant_id = decision.tenant_id
     AND structure_version.id = decision.salary_structure_version_id
    WHERE decision.tenant_id = p_tenant_id
      AND decision.payroll_cycle_id = p_payroll_cycle_id
      AND decision.population_resolution_id = v_resolution_id
      AND decision.decision = 'INCLUDED'
      AND (
        structure_version.id IS NULL
        OR structure_version.approval_status <> 'APPROVED'
        OR structure_version.effective_from > v_period_start
        OR (
          structure_version.effective_to IS NOT NULL
          AND structure_version.effective_to <= v_period_end
        )
        OR NOT EXISTS (
          SELECT 1
          FROM compensation.salary_structure_line line
          JOIN compensation.pay_component_version component_version
            ON component_version.tenant_id = line.tenant_id
           AND component_version.id = line.component_version_id
          WHERE line.tenant_id = decision.tenant_id
            AND line.salary_structure_version_id = decision.salary_structure_version_id
            AND line.effective_from <= v_period_start
            AND (line.effective_to IS NULL OR line.effective_to > v_period_end)
            AND component_version.approval_status = 'APPROVED'
            AND component_version.effective_from <= v_period_start
            AND (
              component_version.effective_to IS NULL
              OR component_version.effective_to > v_period_end
            )
        )
      )
  ) THEN
    RAISE EXCEPTION
      'salary configuration is missing, unapproved, expired or incomplete'
      USING ERRCODE = '23514';
  END IF;

  v_payload := payroll_ops.foundation_configuration_payload(
    p_tenant_id,
    p_payroll_cycle_id
  );
  v_version_set := payroll_ops.foundation_configuration_version_set(
    p_tenant_id,
    p_payroll_cycle_id
  );

  IF v_payload IS NULL
     OR v_version_set IS NULL
     OR jsonb_array_length(v_version_set) < 6 THEN
    RAISE EXCEPTION
      'foundation configuration material could not be resolved'
      USING ERRCODE = '23514';
  END IF;

  v_snapshot_hash := encode(
    public.digest(v_payload::text, 'sha256'::text),
    'hex'
  );
  v_configuration_count := jsonb_array_length(v_version_set);
  v_snapshot_id := gen_random_uuid();

  INSERT INTO payroll_ops.foundation_configuration_snapshot(
    id,
    tenant_id,
    payroll_cycle_id,
    population_resolution_id,
    pay_group_version_id,
    pay_period_id,
    payroll_calendar_id,
    payroll_statutory_unit_version_id,
    legal_entity_version_id,
    snapshot_schema_version,
    configuration_version_set,
    configuration_count,
    snapshot_payload,
    snapshot_hash,
    sealed_at,
    sealed_by,
    created_at,
    created_by
  ) VALUES (
    v_snapshot_id,
    p_tenant_id,
    p_payroll_cycle_id,
    v_resolution_id,
    v_pay_group_version_id,
    v_pay_period_id,
    v_calendar_id,
    v_psu_version_id,
    v_legal_version_id,
    1,
    v_version_set,
    v_configuration_count,
    v_payload,
    v_snapshot_hash,
    p_sealed_at,
    p_actor,
    p_sealed_at,
    p_actor
  );

  PERFORM set_config(
    'payroll_ops.population_mutation',
    'allowed',
    true
  );

  UPDATE payroll_ops.payroll_cycle cycle
  SET foundation_config_snapshot_id = v_snapshot_id,
      foundation_config_snapshot_hash = v_snapshot_hash,
      foundation_config_sealed_at = p_sealed_at,
      foundation_config_sealed_by = p_actor,
      foundation_config_count = v_configuration_count
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.foundation_config_snapshot_id IS NULL;

  RETURN QUERY
  SELECT v_snapshot_id, v_snapshot_hash, v_configuration_count;
END $$;

CREATE OR REPLACE FUNCTION payroll_ops.bind_foundation_snapshot_to_input()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_ops,
  platform AS $$
DECLARE
  v_snapshot_id uuid;
  v_snapshot_hash char(64);
  v_current_payload jsonb;
  v_current_hash char(64);
BEGIN
  IF NEW.payload_schema_version = 0 THEN
    RETURN NEW;
  END IF;

  SELECT
    cycle.foundation_config_snapshot_id,
    cycle.foundation_config_snapshot_hash
  INTO
    v_snapshot_id,
    v_snapshot_hash
  FROM payroll_ops.payroll_cycle cycle
  WHERE cycle.tenant_id = NEW.tenant_id
    AND cycle.id = NEW.payroll_cycle_id;

  IF v_snapshot_id IS NULL OR v_snapshot_hash IS NULL THEN
    SELECT snapshot_id, snapshot_hash
    INTO v_snapshot_id, v_snapshot_hash
    FROM payroll_ops.ensure_foundation_configuration_snapshot(
      NEW.tenant_id,
      NEW.payroll_cycle_id,
      NEW.created_by,
      NEW.sealed_at
    );
  END IF;

  v_current_payload := payroll_ops.foundation_configuration_payload(
    NEW.tenant_id,
    NEW.payroll_cycle_id
  );
  v_current_hash := encode(
    public.digest(v_current_payload::text, 'sha256'::text),
    'hex'
  );

  IF v_current_payload IS NULL
     OR v_current_hash IS DISTINCT FROM v_snapshot_hash THEN
    RAISE EXCEPTION
      'foundation configuration drifted after sealing'
      USING ERRCODE = '23514';
  END IF;

  NEW.foundation_config_snapshot_id := v_snapshot_id;
  NEW.foundation_config_snapshot_hash := v_snapshot_hash;
  NEW.snapshot_payload := NEW.snapshot_payload || jsonb_build_object(
    'foundationConfigurationSnapshotId', v_snapshot_id::text,
    'foundationConfigurationSnapshotHash', v_snapshot_hash
  );
  NEW.snapshot_hash := encode(
    public.digest(NEW.snapshot_payload::text, 'sha256'::text),
    'hex'
  );
  RETURN NEW;
END $$;

CREATE TRIGGER input_snapshot_foundation_binding
  BEFORE INSERT ON payroll_ops.input_snapshot
  FOR EACH ROW
  EXECUTE FUNCTION payroll_ops.bind_foundation_snapshot_to_input();

CREATE OR REPLACE FUNCTION payroll_calc.bind_foundation_snapshot_to_request()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_calc,
  payroll_ops AS $$
DECLARE
  v_snapshot_id uuid;
  v_snapshot_hash char(64);
BEGIN
  IF NEW.request_schema_version = 0 THEN
    RETURN NEW;
  END IF;

  SELECT
    cycle.foundation_config_snapshot_id,
    cycle.foundation_config_snapshot_hash
  INTO
    v_snapshot_id,
    v_snapshot_hash
  FROM payroll_ops.payroll_cycle cycle
  WHERE cycle.tenant_id = NEW.tenant_id
    AND cycle.id = NEW.payroll_cycle_id;

  IF v_snapshot_id IS NULL OR v_snapshot_hash IS NULL THEN
    RAISE EXCEPTION
      'calculation requires sealed foundation configuration'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.input_snapshot input
    WHERE input.tenant_id = NEW.tenant_id
      AND input.payroll_cycle_id = NEW.payroll_cycle_id
      AND (
        input.foundation_config_snapshot_id IS DISTINCT FROM v_snapshot_id
        OR input.foundation_config_snapshot_hash IS DISTINCT FROM v_snapshot_hash
      )
  ) THEN
    RAISE EXCEPTION
      'input snapshots do not share the cycle foundation configuration lineage'
      USING ERRCODE = '23514';
  END IF;

  NEW.foundation_config_snapshot_id := v_snapshot_id;
  NEW.foundation_config_snapshot_hash := v_snapshot_hash;
  RETURN NEW;
END $$;

CREATE TRIGGER calculation_request_foundation_binding
  BEFORE INSERT ON payroll_calc.calculation_request
  FOR EACH ROW
  EXECUTE FUNCTION payroll_calc.bind_foundation_snapshot_to_request();

CREATE OR REPLACE FUNCTION payroll_calc.bind_foundation_snapshot_to_result()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  payroll_calc,
  payroll_ops AS $$
DECLARE
  v_snapshot_id uuid;
  v_snapshot_hash char(64);
  v_input_snapshot_id uuid;
  v_input_snapshot_hash char(64);
BEGIN
  IF NEW.result_schema_version = 0 THEN
    RETURN NEW;
  END IF;

  SELECT
    request.foundation_config_snapshot_id,
    request.foundation_config_snapshot_hash
  INTO
    v_snapshot_id,
    v_snapshot_hash
  FROM payroll_calc.calculation_request request
  WHERE request.tenant_id = NEW.tenant_id
    AND request.id = NEW.calculation_request_id;

  SELECT
    input.foundation_config_snapshot_id,
    input.foundation_config_snapshot_hash
  INTO
    v_input_snapshot_id,
    v_input_snapshot_hash
  FROM payroll_ops.input_snapshot input
  WHERE input.tenant_id = NEW.tenant_id
    AND input.id = NEW.input_snapshot_id;

  IF v_snapshot_id IS NULL
     OR v_snapshot_hash IS NULL
     OR v_input_snapshot_id IS DISTINCT FROM v_snapshot_id
     OR v_input_snapshot_hash IS DISTINCT FROM v_snapshot_hash THEN
    RAISE EXCEPTION
      'calculation result foundation configuration lineage is inconsistent'
      USING ERRCODE = '23514';
  END IF;

  NEW.foundation_config_snapshot_id := v_snapshot_id;
  NEW.foundation_config_snapshot_hash := v_snapshot_hash;
  NEW.result_payload := NEW.result_payload || jsonb_build_object(
    'foundationConfigurationSnapshotId', v_snapshot_id::text,
    'foundationConfigurationSnapshotHash', v_snapshot_hash
  );
  NEW.result_hash := encode(
    public.digest(NEW.result_payload::text, 'sha256'::text),
    'hex'
  );
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_result_foundation_binding
  BEFORE INSERT ON payroll_calc.payroll_result
  FOR EACH ROW
  EXECUTE FUNCTION payroll_calc.bind_foundation_snapshot_to_result();

CREATE INDEX input_snapshot_foundation_ix
  ON payroll_ops.input_snapshot(
    tenant_id,
    foundation_config_snapshot_id,
    payroll_cycle_id
  );

CREATE INDEX calculation_request_foundation_ix
  ON payroll_calc.calculation_request(
    tenant_id,
    foundation_config_snapshot_id,
    payroll_cycle_id
  );

CREATE INDEX payroll_result_foundation_ix
  ON payroll_calc.payroll_result(
    tenant_id,
    foundation_config_snapshot_id,
    payroll_cycle_id
  );

REVOKE ALL ON FUNCTION payroll_ops.foundation_configuration_payload(uuid, uuid)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_ops.foundation_configuration_version_set(uuid, uuid)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_ops.bind_foundation_snapshot_to_input()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_calc.bind_foundation_snapshot_to_request()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_calc.bind_foundation_snapshot_to_result()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION payroll_ops.ensure_foundation_configuration_snapshot(
  uuid, uuid, varchar, timestamptz
) FROM PUBLIC;

GRANT USAGE ON SCHEMA payroll_ops TO payroll_app;
GRANT SELECT
  ON payroll_ops.foundation_configuration_snapshot
  TO payroll_app;
REVOKE INSERT, UPDATE, DELETE
  ON payroll_ops.foundation_configuration_snapshot
  FROM payroll_app;
REVOKE CREATE ON SCHEMA payroll_ops FROM payroll_app;

ALTER TABLE payroll_ops.foundation_configuration_snapshot
  ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.foundation_configuration_snapshot
  FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON payroll_ops.foundation_configuration_snapshot
  USING (tenant_id = platform.current_tenant_id())
  WITH CHECK (tenant_id = platform.current_tenant_id());

ALTER TABLE organisation.pay_group_version FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_calendar FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.pay_period FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_statutory_unit_version FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.legal_entity_version FORCE ROW LEVEL SECURITY;
ALTER TABLE organisation.payroll_jurisdiction_version FORCE ROW LEVEL SECURITY;
ALTER TABLE statutory.registration_type_version FORCE ROW LEVEL SECURITY;
ALTER TABLE statutory.registration_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_line FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.pay_component_version FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.payroll_cycle FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_resolution FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.population_decision FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_ops.input_snapshot FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.calculation_request FORCE ROW LEVEL SECURITY;
ALTER TABLE payroll_calc.payroll_result FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE payroll_ops.foundation_configuration_snapshot IS
  'Immutable cycle-level exact foundation configuration material used to reproduce payroll without rereading mutable current master state.';
COMMENT ON COLUMN payroll_ops.foundation_configuration_snapshot.configuration_version_set IS
  'Ordered exact identity/version set captured for the bounded executable foundation.';
COMMENT ON COLUMN payroll_ops.foundation_configuration_snapshot.snapshot_hash IS
  'SHA-256 identity of canonical JSONB snapshot payload.';
COMMENT ON COLUMN payroll_ops.input_snapshot.foundation_config_snapshot_id IS
  'Exact immutable foundation configuration snapshot bound before input sealing.';
COMMENT ON COLUMN payroll_calc.calculation_request.foundation_config_snapshot_id IS
  'Exact immutable foundation configuration snapshot bound to the official calculation request.';
COMMENT ON COLUMN payroll_calc.payroll_result.foundation_config_snapshot_id IS
  'Exact immutable foundation configuration snapshot bound to payroll result evidence.';
