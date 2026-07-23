-- S2-QA-01A completed-foundation negative-path hardening.
--
-- V015/V016 introduced immutable organisation versions and controlled lifecycle
-- commands. The initial approval command did not require an approved parent for
-- payroll-statutory-unit or establishment versions, and the generic end-date
-- command did not protect child version ranges. V017/V018 also did not prove
-- that a payroll cycle's exact pay-group version covers the referenced period.
--
-- This forward-only migration closes those gaps without rewriting V015-V021.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM organisation.payroll_statutory_unit_version child
    JOIN organisation.legal_entity_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.legal_entity_version_id
    WHERE child.approval_status = 'APPROVED'
      AND parent.approval_status <> 'APPROVED'
  ) THEN
    RAISE EXCEPTION
      'approved payroll-statutory-unit versions require approved legal-entity parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.establishment_version child
    JOIN organisation.payroll_statutory_unit_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.payroll_statutory_unit_version_id
    WHERE child.approval_status = 'APPROVED'
      AND parent.approval_status <> 'APPROVED'
  ) THEN
    RAISE EXCEPTION
      'approved establishment versions require approved payroll-statutory-unit parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.pay_group_version child
    JOIN organisation.payroll_statutory_unit_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.payroll_statutory_unit_version_id
    WHERE child.approval_status = 'APPROVED'
      AND parent.approval_status <> 'APPROVED'
  ) THEN
    RAISE EXCEPTION
      'approved pay-group versions require approved payroll-statutory-unit parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.payroll_statutory_unit_version child
    JOIN organisation.legal_entity_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.legal_entity_version_id
    WHERE child.effective_from < parent.effective_from
       OR (
         parent.effective_to IS NOT NULL
         AND (
           child.effective_to IS NULL
           OR child.effective_to > parent.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'payroll-statutory-unit version ranges must remain inside legal-entity parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.establishment_version child
    JOIN organisation.payroll_statutory_unit_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.payroll_statutory_unit_version_id
    WHERE child.effective_from < parent.effective_from
       OR (
         parent.effective_to IS NOT NULL
         AND (
           child.effective_to IS NULL
           OR child.effective_to > parent.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'establishment version ranges must remain inside payroll-statutory-unit parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM organisation.pay_group_version child
    JOIN organisation.payroll_statutory_unit_version parent
      ON parent.tenant_id = child.tenant_id
     AND parent.id = child.payroll_statutory_unit_version_id
    WHERE child.effective_from < parent.effective_from
       OR (
         parent.effective_to IS NOT NULL
         AND (
           child.effective_to IS NULL
           OR child.effective_to > parent.effective_to
         )
       )
  ) THEN
    RAISE EXCEPTION
      'pay-group version ranges must remain inside payroll-statutory-unit parents';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    JOIN organisation.pay_group_version group_version
      ON group_version.tenant_id = cycle.tenant_id
     AND group_version.id = cycle.pay_group_id
    JOIN organisation.pay_period period
      ON period.tenant_id = cycle.tenant_id
     AND period.id = cycle.pay_period_id
    WHERE period.period_start < group_version.effective_from
       OR (
         group_version.effective_to IS NOT NULL
         AND period.period_end >= group_version.effective_to
       )
  ) THEN
    RAISE EXCEPTION
      'payroll-cycle periods must be contained by their exact pay-group versions';
  END IF;
END $$;

CREATE OR REPLACE FUNCTION organisation.approve_version(
  p_kind varchar,
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
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

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      UPDATE organisation.legal_entity_version version
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
          FROM organisation.legal_entity_version successor
          WHERE successor.tenant_id = version.tenant_id
            AND successor.supersedes_version_id = version.id
        );

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      UPDATE organisation.payroll_statutory_unit_version version
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
          FROM organisation.payroll_statutory_unit_version successor
          WHERE successor.tenant_id = version.tenant_id
            AND successor.supersedes_version_id = version.id
        )
        AND EXISTS (
          SELECT 1
          FROM organisation.legal_entity_version parent
          WHERE parent.tenant_id = version.tenant_id
            AND parent.id = version.legal_entity_version_id
            AND parent.approval_status = 'APPROVED'
            AND version.effective_from >= parent.effective_from
            AND (
              parent.effective_to IS NULL
              OR (
                version.effective_to IS NOT NULL
                AND version.effective_to <= parent.effective_to
              )
            )
        );

    WHEN 'ESTABLISHMENT' THEN
      UPDATE organisation.establishment_version version
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
          FROM organisation.establishment_version successor
          WHERE successor.tenant_id = version.tenant_id
            AND successor.supersedes_version_id = version.id
        )
        AND EXISTS (
          SELECT 1
          FROM organisation.payroll_statutory_unit_version parent
          WHERE parent.tenant_id = version.tenant_id
            AND parent.id = version.payroll_statutory_unit_version_id
            AND parent.approval_status = 'APPROVED'
            AND version.effective_from >= parent.effective_from
            AND (
              parent.effective_to IS NULL
              OR (
                version.effective_to IS NOT NULL
                AND version.effective_to <= parent.effective_to
              )
            )
        );

    ELSE
      RAISE EXCEPTION 'unsupported organisation kind';
  END CASE;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.end_date_version(
  p_kind varchar,
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
  organisation,
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

  CASE p_kind
    WHEN 'LEGAL_ENTITY' THEN
      UPDATE organisation.legal_entity_version version
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
          FROM organisation.payroll_statutory_unit_version child
          WHERE child.tenant_id = version.tenant_id
            AND child.legal_entity_version_id = version.id
            AND (
              child.effective_from >= p_effective_to
              OR child.effective_to IS NULL
              OR child.effective_to > p_effective_to
            )
        )
        AND NOT EXISTS (
          SELECT 1
          FROM employee_payroll.payroll_relationship_version child
          WHERE child.tenant_id = version.tenant_id
            AND child.legal_entity_version_id = version.id
            AND (
              child.relationship_start >= p_effective_to
              OR child.relationship_end IS NULL
              OR child.relationship_end > p_effective_to
            )
        );

    WHEN 'PAYROLL_STATUTORY_UNIT' THEN
      UPDATE organisation.payroll_statutory_unit_version version
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
          FROM organisation.establishment_version child
          WHERE child.tenant_id = version.tenant_id
            AND child.payroll_statutory_unit_version_id = version.id
            AND (
              child.effective_from >= p_effective_to
              OR child.effective_to IS NULL
              OR child.effective_to > p_effective_to
            )
        )
        AND NOT EXISTS (
          SELECT 1
          FROM organisation.pay_group_version child
          WHERE child.tenant_id = version.tenant_id
            AND child.payroll_statutory_unit_version_id = version.id
            AND (
              child.effective_from >= p_effective_to
              OR child.effective_to IS NULL
              OR child.effective_to > p_effective_to
            )
        );

    WHEN 'ESTABLISHMENT' THEN
      UPDATE organisation.establishment_version version
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
          FROM employee_payroll.payroll_assignment_version child
          WHERE child.tenant_id = version.tenant_id
            AND child.establishment_version_id = version.id
            AND (
              child.assignment_start >= p_effective_to
              OR child.assignment_end IS NULL
              OR child.assignment_end > p_effective_to
            )
        );

    ELSE
      RAISE EXCEPTION 'unsupported organisation kind';
  END CASE;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  organisation.guard_organisation_parent_end_date()
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

  IF TG_TABLE_NAME = 'legal_entity_version'
     AND EXISTS (
       SELECT 1
       FROM organisation.payroll_statutory_unit_version child
       WHERE child.tenant_id = OLD.tenant_id
         AND child.legal_entity_version_id = OLD.id
         AND (
           child.effective_from >= NEW.effective_to
           OR child.effective_to IS NULL
           OR child.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'legal-entity version cannot end before dependent payroll-statutory-unit versions'
      USING ERRCODE = '23514';
  END IF;

  IF TG_TABLE_NAME = 'payroll_statutory_unit_version'
     AND EXISTS (
       SELECT 1
       FROM organisation.establishment_version child
       WHERE child.tenant_id = OLD.tenant_id
         AND child.payroll_statutory_unit_version_id = OLD.id
         AND (
           child.effective_from >= NEW.effective_to
           OR child.effective_to IS NULL
           OR child.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'payroll-statutory-unit version cannot end before dependent establishments'
      USING ERRCODE = '23514';
  END IF;

  IF TG_TABLE_NAME = 'payroll_statutory_unit_version'
     AND EXISTS (
       SELECT 1
       FROM organisation.pay_group_version child
       WHERE child.tenant_id = OLD.tenant_id
         AND child.payroll_statutory_unit_version_id = OLD.id
         AND (
           child.effective_from >= NEW.effective_to
           OR child.effective_to IS NULL
           OR child.effective_to > NEW.effective_to
         )
     ) THEN
    RAISE EXCEPTION
      'payroll-statutory-unit version cannot end before dependent pay-group versions'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER legal_entity_version_organisation_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.legal_entity_version
  FOR EACH ROW
  EXECUTE FUNCTION
    organisation.guard_organisation_parent_end_date();

CREATE TRIGGER psu_version_organisation_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.payroll_statutory_unit_version
  FOR EACH ROW
  EXECUTE FUNCTION
    organisation.guard_organisation_parent_end_date();

CREATE OR REPLACE FUNCTION
  payroll_ops.assert_payroll_cycle_pay_group_range()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, payroll_ops, organisation, platform AS $$
DECLARE
  group_from date;
  group_to date;
  period_from date;
  period_to date;
BEGIN
  SELECT effective_from, effective_to
  INTO group_from, group_to
  FROM organisation.pay_group_version
  WHERE tenant_id = NEW.tenant_id
    AND id = NEW.pay_group_id;

  SELECT period_start, period_end
  INTO period_from, period_to
  FROM organisation.pay_period
  WHERE tenant_id = NEW.tenant_id
    AND id = NEW.pay_period_id;

  IF group_from IS NULL OR period_from IS NULL THEN
    RAISE EXCEPTION
      'payroll-cycle dependencies do not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF period_from < group_from
     OR (
       group_to IS NOT NULL
       AND period_to >= group_to
     ) THEN
    RAISE EXCEPTION
      'pay period must be contained by the exact pay-group version'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_cycle_pay_group_range
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    pay_group_id,
    pay_period_id
  ON payroll_ops.payroll_cycle
  FOR EACH ROW
  EXECUTE FUNCTION
    payroll_ops.assert_payroll_cycle_pay_group_range();

CREATE OR REPLACE FUNCTION
  payroll_ops.guard_pay_group_version_end_date()
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

  IF EXISTS (
    SELECT 1
    FROM payroll_ops.payroll_cycle cycle
    JOIN organisation.pay_period period
      ON period.tenant_id = cycle.tenant_id
     AND period.id = cycle.pay_period_id
    WHERE cycle.tenant_id = OLD.tenant_id
      AND cycle.pay_group_id = OLD.id
      AND (
        period.period_start >= NEW.effective_to
        OR period.period_end >= NEW.effective_to
      )
  ) THEN
    RAISE EXCEPTION
      'pay-group version cannot end before dependent payroll-cycle periods'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_version_payroll_cycle_dependents
  BEFORE UPDATE OF effective_to
  ON organisation.pay_group_version
  FOR EACH ROW
  EXECUTE FUNCTION
    payroll_ops.guard_pay_group_version_end_date();

CREATE OR REPLACE FUNCTION organisation.end_date_pay_group_version(
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
  organisation,
  employee_payroll,
  payroll_ops,
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

  UPDATE organisation.pay_group_version version
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
      FROM employee_payroll.pay_group_assignment assignment
      WHERE assignment.tenant_id = version.tenant_id
        AND assignment.pay_group_version_id = version.id
        AND (
          assignment.effective_from >= p_effective_to
          OR assignment.effective_to IS NULL
          OR assignment.effective_to > p_effective_to
        )
    )
    AND NOT EXISTS (
      SELECT 1
      FROM payroll_ops.payroll_cycle cycle
      JOIN organisation.pay_period period
        ON period.tenant_id = cycle.tenant_id
       AND period.id = cycle.pay_period_id
      WHERE cycle.tenant_id = version.tenant_id
        AND cycle.pay_group_id = version.id
        AND (
          period.period_start >= p_effective_to
          OR period.period_end >= p_effective_to
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
    )
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.payroll_assignment_version child
      WHERE child.tenant_id = version.tenant_id
        AND child.payroll_relationship_version_id = version.id
        AND (
          child.assignment_start >= p_relationship_end
          OR child.assignment_end IS NULL
          OR child.assignment_end > p_relationship_end
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
    )
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.pay_group_assignment child
      WHERE child.tenant_id = version.tenant_id
        AND child.payroll_assignment_version_id = version.id
        AND (
          child.effective_from >= p_assignment_end
          OR child.effective_to IS NULL
          OR child.effective_to > p_assignment_end
        )
    )
    AND NOT EXISTS (
      SELECT 1
      FROM employee_payroll.salary_assignment child
      WHERE child.tenant_id = version.tenant_id
        AND child.payroll_assignment_version_id = version.id
        AND (
          child.effective_from >= p_assignment_end
          OR child.effective_to IS NULL
          OR child.effective_to > p_assignment_end
        )
    )
    AND NOT EXISTS (
      SELECT 1
      FROM payroll_ops.population_member member
      JOIN payroll_ops.payroll_cycle cycle
        ON cycle.tenant_id = member.tenant_id
       AND cycle.id = member.payroll_cycle_id
      JOIN organisation.pay_period period
        ON period.tenant_id = cycle.tenant_id
       AND period.id = cycle.pay_period_id
      WHERE member.tenant_id = version.tenant_id
        AND member.payroll_assignment_version_id = version.id
        AND period.period_end >= p_assignment_end
    );

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

COMMENT ON FUNCTION organisation.approve_version(
  varchar,
  uuid,
  uuid,
  varchar,
  timestamptz
) IS
  'Approves organisation versions only when exact parent versions are approved and range-compatible.';

COMMENT ON FUNCTION organisation.end_date_version(
  varchar,
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) IS
  'Optimistically end-dates organisation versions without truncating exact child lineage.';

COMMENT ON FUNCTION organisation.end_date_pay_group_version(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) IS
  'Optimistically end-dates a pay-group version without truncating employee assignments or payroll cycles.';
