-- P5-SSC-01 G02D: complete compensation target contract.
-- Forward-only from V043. V001-V043 remain immutable.
-- NET_PAY_TARGET execution remains calculation-engine owned.

ALTER TABLE compensation.salary_structure_version
  ADD COLUMN target_source_amount numeric(19,4),
  ADD COLUMN target_frequency varchar(16),
  ADD COLUMN target_annualization_factor numeric(19,4),
  ADD COLUMN target_execution_mode varchar(32),
  ADD COLUMN inclusive_payroll_base_version_id uuid,
  ADD COLUMN exclusive_payroll_base_version_id uuid;

-- V033 persisted the supplied value directly in target_annual_amount.
-- G02C is local-only, so an upgrade entering V044 from V043 still follows
-- that persisted contract. Capture source first, then normalize. An already
-- approved MONTHLY_GROSS row cannot be reinterpreted silently; require explicit
-- pre-upgrade remediation instead.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_version
     WHERE structure_schema_version = 1
       AND target_type = 'MONTHLY_GROSS'
       AND approval_status = 'APPROVED'
  ) THEN
    RAISE EXCEPTION
      'V044 requires approved MONTHLY_GROSS structures to be remediated before target normalization'
      USING ERRCODE = '23514';
  END IF;
END $$;

UPDATE compensation.salary_structure_version
   SET target_source_amount = target_annual_amount,
       target_frequency = CASE target_type
         WHEN 'MONTHLY_GROSS' THEN 'MONTHLY'
         ELSE 'ANNUAL'
       END,
       target_annualization_factor = CASE target_type
         WHEN 'MONTHLY_GROSS' THEN 12.0000
         ELSE 1.0000
       END,
       target_execution_mode = 'STRUCTURAL'
 WHERE structure_schema_version = 1;

UPDATE compensation.salary_structure_version
   SET target_annual_amount =
       round(target_source_amount * target_annualization_factor, 4),
       validation_fingerprint = CASE
         WHEN target_type = 'MONTHLY_GROSS' THEN NULL
         ELSE validation_fingerprint
       END
 WHERE structure_schema_version = 1;

ALTER TABLE compensation.salary_structure_version
  DROP CONSTRAINT salary_structure_version_p5a3_shape_ck;

ALTER TABLE compensation.salary_structure_version
  ADD CONSTRAINT salary_structure_version_target_inclusive_base_fk
    FOREIGN KEY (tenant_id, inclusive_payroll_base_version_id)
    REFERENCES compensation.payroll_base_version(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_target_exclusive_base_fk
    FOREIGN KEY (tenant_id, exclusive_payroll_base_version_id)
    REFERENCES compensation.payroll_base_version(tenant_id, id),
  ADD CONSTRAINT salary_structure_version_target_distinct_bases_ck
    CHECK (
      inclusive_payroll_base_version_id IS NULL
      OR exclusive_payroll_base_version_id IS NULL
      OR inclusive_payroll_base_version_id <> exclusive_payroll_base_version_id
    ),
  ADD CONSTRAINT salary_structure_version_p5ssc_target_shape_ck
    CHECK (
      (
        structure_schema_version = 0
        AND structure_type IS NULL
        AND pay_frequency IS NULL
        AND confidentiality_level IS NULL
        AND ctc_policy_version_id IS NULL
        AND eligibility_rule_version_id IS NULL
        AND target_type IS NULL
        AND target_source_amount IS NULL
        AND target_frequency IS NULL
        AND target_annualization_factor IS NULL
        AND target_execution_mode IS NULL
        AND inclusive_payroll_base_version_id IS NULL
        AND exclusive_payroll_base_version_id IS NULL
        AND target_annual_amount IS NULL
        AND tolerance_amount IS NULL
        AND residual_component_version_id IS NULL
        AND configuration_hash IS NULL
        AND validation_fingerprint IS NULL
      )
      OR
      (
        structure_schema_version = 1
        AND structure_type IS NOT NULL
        AND structure_type IN (
          'STANDARD', 'EXECUTIVE', 'SALES', 'HOURLY', 'CONTRACT'
        )
        AND pay_frequency IS NOT NULL
        AND pay_frequency IN (
          'MONTHLY', 'WEEKLY', 'BIWEEKLY', 'SEMIMONTHLY'
        )
        AND confidentiality_level IS NOT NULL
        AND confidentiality_level IN (
          'STANDARD', 'RESTRICTED', 'EXECUTIVE'
        )
        AND ctc_policy_version_id IS NOT NULL
        AND target_type IS NOT NULL
        AND target_type IN (
          'ANNUAL_CTC',
          'ANNUAL_TOTAL_CTC',
          'ANNUAL_FIXED_CTC',
          'ANNUAL_GROSS',
          'MONTHLY_GROSS',
          'ANNUAL_BASIC',
          'HOURLY_RATE',
          'DAILY_RATE',
          'GRADE_MIDPOINT',
          'TOTAL_CASH_TARGET',
          'NET_PAY_TARGET',
          'EMPLOYER_COST_TARGET'
        )
        AND target_source_amount IS NOT NULL
        AND target_source_amount > 0
        AND target_frequency IS NOT NULL
        AND target_frequency IN ('ANNUAL', 'MONTHLY', 'HOURLY', 'DAILY')
        AND (
          target_annualization_factor IS NULL
          OR target_annualization_factor > 0
        )
        AND target_execution_mode IS NOT NULL
        AND target_execution_mode IN (
          'STRUCTURAL', 'TARGET_RESOLVER_REQUIRED', 'CALCULATION_ENGINE'
        )
        AND (
          target_annual_amount IS NULL
          OR target_annual_amount > 0
        )
        AND tolerance_amount IS NOT NULL
        AND tolerance_amount >= 0
        AND residual_component_version_id IS NOT NULL
        AND configuration_hash IS NOT NULL
        AND configuration_hash ~ '^[0-9a-f]{64}$'
        AND (
          validation_fingerprint IS NULL
          OR validation_fingerprint ~ '^[0-9a-f]{64}$'
        )
        AND (
          (
            target_type IN (
              'ANNUAL_CTC',
              'ANNUAL_TOTAL_CTC',
              'ANNUAL_GROSS'
            )
            AND target_frequency = 'ANNUAL'
            AND target_annualization_factor = 1.0000
            AND target_execution_mode = 'STRUCTURAL'
          )
          OR
          (
            target_type IN (
              'ANNUAL_FIXED_CTC',
              'ANNUAL_BASIC',
              'GRADE_MIDPOINT',
              'TOTAL_CASH_TARGET',
              'EMPLOYER_COST_TARGET'
            )
            AND target_frequency = 'ANNUAL'
            AND target_annualization_factor = 1.0000
            AND target_execution_mode = 'TARGET_RESOLVER_REQUIRED'
            AND inclusive_payroll_base_version_id IS NOT NULL
          )
          OR
          (
            target_type = 'MONTHLY_GROSS'
            AND target_frequency = 'MONTHLY'
            AND target_annualization_factor = 12.0000
            AND target_execution_mode = 'STRUCTURAL'
          )
          OR
          (
            target_type = 'HOURLY_RATE'
            AND target_frequency = 'HOURLY'
            AND target_annualization_factor IS NULL
            AND target_annual_amount IS NULL
            AND target_execution_mode = 'CALCULATION_ENGINE'
          )
          OR
          (
            target_type = 'DAILY_RATE'
            AND target_frequency = 'DAILY'
            AND target_annualization_factor IS NULL
            AND target_annual_amount IS NULL
            AND target_execution_mode = 'CALCULATION_ENGINE'
          )
          OR
          (
            target_type = 'NET_PAY_TARGET'
            AND target_frequency = 'ANNUAL'
            AND target_annualization_factor = 1.0000
            AND target_execution_mode = 'CALCULATION_ENGINE'
          )
        )
        AND (
          target_annual_amount IS NULL
          OR target_annual_amount =
            round(target_source_amount * target_annualization_factor, 4)
        )
      )
    );

CREATE OR REPLACE FUNCTION compensation.assert_salary_structure_target_bases()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  base_version_id uuid;
  base_from date;
  base_to date;
  base_status varchar;
  base_lifecycle varchar;
  base_category varchar;
BEGIN
  IF NEW.structure_schema_version <> 1 THEN
    RETURN NEW;
  END IF;

  FOREACH base_version_id IN ARRAY ARRAY[
    NEW.inclusive_payroll_base_version_id,
    NEW.exclusive_payroll_base_version_id
  ]
  LOOP
    IF base_version_id IS NULL THEN
      CONTINUE;
    END IF;

    SELECT version.effective_from,
           version.effective_to,
           version.approval_status,
           identity.lifecycle_status,
           version.base_category
      INTO base_from, base_to, base_status, base_lifecycle, base_category
      FROM compensation.payroll_base_version version
      JOIN compensation.payroll_base identity
        ON identity.tenant_id = version.tenant_id
       AND identity.id = version.payroll_base_id
     WHERE version.tenant_id = NEW.tenant_id
       AND version.id = base_version_id;

    IF base_from IS NULL
       OR base_status <> 'APPROVED'
       OR base_lifecycle <> 'ACTIVE' THEN
      RAISE EXCEPTION
        'salary-structure target bases require active approved payroll-base versions'
        USING ERRCODE = '23514';
    END IF;

    IF base_category NOT IN ('CALCULATION', 'CTC') THEN
      RAISE EXCEPTION
        'salary-structure target bases must be CALCULATION or CTC bases'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.effective_from < base_from
       OR (
         base_to IS NOT NULL
         AND (
           NEW.effective_to IS NULL
           OR NEW.effective_to > base_to
         )
       ) THEN
      RAISE EXCEPTION
        'salary-structure range must be contained by each target payroll-base version'
        USING ERRCODE = '23514';
    END IF;
  END LOOP;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_target_base_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    structure_schema_version,
    inclusive_payroll_base_version_id,
    exclusive_payroll_base_version_id,
    effective_from,
    effective_to
  ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_structure_target_bases();
