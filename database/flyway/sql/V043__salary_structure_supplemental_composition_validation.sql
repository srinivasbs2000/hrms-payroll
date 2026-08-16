-- P5-SSC-01 G02B: supplemental composition validation and approval integration.
-- Forward-only from V042. V001-V042 are immutable.
-- No country-specific statutory rate, threshold or legal conclusion is encoded here.

ALTER TABLE compensation.salary_structure_version
  ADD COLUMN composition_revision bigint NOT NULL DEFAULT 0;

ALTER TABLE compensation.salary_structure_version
  ADD CONSTRAINT salary_structure_composition_revision_ck
  CHECK (composition_revision >= 0);

ALTER TABLE compensation.salary_supplemental_plan_line
  ADD COLUMN percentage_base_component_id uuid,
  ADD COLUMN percentage_base_component_version_id uuid;

ALTER TABLE compensation.salary_supplemental_plan_line
  ADD CONSTRAINT salary_supplemental_plan_line_percentage_base_fk
  FOREIGN KEY (
    tenant_id,
    percentage_base_component_version_id,
    percentage_base_component_id
  )
  REFERENCES compensation.pay_component_version(
    tenant_id,
    id,
    component_id
  );

ALTER TABLE compensation.salary_supplemental_plan_line
  ADD CONSTRAINT salary_supplemental_plan_line_value_shape_ck
  CHECK (
    (
      default_amount IS NOT NULL
      AND default_percentage IS NULL
      AND percentage_base_component_id IS NULL
      AND percentage_base_component_version_id IS NULL
    )
    OR
    (
      default_amount IS NULL
      AND default_percentage IS NOT NULL
      AND percentage_base_component_id IS NOT NULL
      AND percentage_base_component_version_id IS NOT NULL
    )
  );

DROP INDEX compensation.salary_structure_validation_line_component_uk;

CREATE UNIQUE INDEX salary_structure_validation_line_base_source_uk
  ON compensation.salary_structure_validation_line(
    tenant_id,
    validation_id,
    component_version_id
  )
  WHERE (
    component_version_id IS NOT NULL
    AND coalesce(evidence_json->>'sourceType','BASE')='BASE'
  );

CREATE UNIQUE INDEX salary_structure_validation_line_supplemental_source_uk
  ON compensation.salary_structure_validation_line(
    tenant_id,
    validation_id,
    (evidence_json->>'sourceBindingId'),
    (evidence_json->>'sourcePlanLineId')
  )
  WHERE (evidence_json->>'sourceType'='SUPPLEMENTAL');

CREATE OR REPLACE FUNCTION compensation.assert_supplemental_percentage_base()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  base_component_id uuid;
  base_status varchar;
  base_lifecycle varchar;
  base_from date;
  base_to date;
BEGIN
  IF NEW.default_percentage IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT base_version.component_id,
         base_version.approval_status,
         base_identity.lifecycle_status,
         base_version.effective_from,
         base_version.effective_to
    INTO base_component_id,
         base_status,
         base_lifecycle,
         base_from,
         base_to
    FROM compensation.pay_component_version base_version
    JOIN compensation.pay_component base_identity
      ON base_identity.tenant_id=base_version.tenant_id
     AND base_identity.id=base_version.component_id
   WHERE base_version.tenant_id=NEW.tenant_id
     AND base_version.id=NEW.percentage_base_component_version_id
     AND base_version.component_id=NEW.percentage_base_component_id;

  IF base_component_id IS NULL THEN
    RAISE EXCEPTION 'supplemental percentage-base component version does not exist'
      USING ERRCODE='23503';
  END IF;
  IF base_component_id=NEW.component_id THEN
    RAISE EXCEPTION 'supplemental percentage lines cannot calculate from the same component identity'
      USING ERRCODE='23514';
  END IF;
  IF base_status<>'APPROVED' OR base_lifecycle<>'ACTIVE' THEN
    RAISE EXCEPTION 'supplemental percentage bases require an active approved component'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from<base_from
     OR (
       base_to IS NOT NULL
       AND (NEW.effective_to IS NULL OR NEW.effective_to>base_to)
     ) THEN
    RAISE EXCEPTION 'supplemental percentage-base range must contain the line range'
      USING ERRCODE='23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_supplemental_plan_line_percentage_base
  BEFORE INSERT ON compensation.salary_supplemental_plan_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_supplemental_percentage_base();

CREATE OR REPLACE FUNCTION compensation.assert_supplemental_binding_percentage_bases()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM compensation.salary_supplemental_plan_line target_line
     WHERE target_line.tenant_id=NEW.tenant_id
       AND target_line.supplemental_plan_version_id=
         NEW.supplemental_plan_version_id
       AND target_line.default_percentage IS NOT NULL
       AND NOT (
         EXISTS (
           SELECT 1
             FROM compensation.salary_structure_line base_line
            WHERE base_line.tenant_id=NEW.tenant_id
              AND base_line.salary_structure_version_id=
                NEW.salary_structure_version_id
              AND base_line.component_version_id=
                target_line.percentage_base_component_version_id
              AND coalesce(base_line.line_type,'FIXED')<>'RESIDUAL'
         )
         OR EXISTS (
           SELECT 1
             FROM compensation.salary_supplemental_plan_line earlier_line
            WHERE earlier_line.tenant_id=target_line.tenant_id
              AND earlier_line.supplemental_plan_version_id=
                target_line.supplemental_plan_version_id
              AND earlier_line.sequence_no<target_line.sequence_no
              AND earlier_line.component_version_id=
                target_line.percentage_base_component_version_id
         )
         OR EXISTS (
           SELECT 1
             FROM compensation.salary_structure_supplemental_plan_binding
               earlier_binding
             JOIN compensation.salary_supplemental_plan_line earlier_line
               ON earlier_line.tenant_id=earlier_binding.tenant_id
              AND earlier_line.supplemental_plan_version_id=
                earlier_binding.supplemental_plan_version_id
            WHERE earlier_binding.tenant_id=NEW.tenant_id
              AND earlier_binding.salary_structure_version_id=
                NEW.salary_structure_version_id
              AND earlier_binding.sequence_no<NEW.sequence_no
              AND earlier_line.component_version_id=
                target_line.percentage_base_component_version_id
         )
       )
  ) THEN
    RAISE EXCEPTION
      'supplemental percentage bases must resolve to a base component or an earlier supplemental component'
      USING ERRCODE='23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_supplemental_binding_percentage_bases
  BEFORE INSERT ON compensation.salary_structure_supplemental_plan_binding
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_supplemental_binding_percentage_bases();

CREATE OR REPLACE FUNCTION compensation.bind_salary_structure_supplemental_plan(
  p_tenant_id uuid,
  p_binding_id uuid,
  p_structure_id uuid,
  p_structure_version_id uuid,
  p_plan_version_id uuid,
  p_sequence_no integer,
  p_effective_from date,
  p_effective_to date,
  p_actor varchar
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  target_plan_id uuid;
  next_revision bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_binding_id IS NULL
     OR p_structure_id IS NULL
     OR p_structure_version_id IS NULL
     OR p_plan_version_id IS NULL
     OR p_sequence_no IS NULL
     OR p_sequence_no<1
     OR p_effective_from IS NULL
     OR (p_effective_to IS NOT NULL AND p_effective_to<=p_effective_from)
     OR p_actor IS NULL
     OR btrim(p_actor)='' THEN
    RAISE EXCEPTION 'complete supplemental binding data is required'
      USING ERRCODE='23514';
  END IF;

  PERFORM 1
    FROM compensation.salary_structure_version structure
   WHERE structure.tenant_id=p_tenant_id
     AND structure.id=p_structure_version_id
     AND structure.salary_structure_id=p_structure_id
   FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'salary-structure version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;

  SELECT version.supplemental_plan_id
    INTO target_plan_id
    FROM compensation.salary_supplemental_plan_version version
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_plan_version_id;

  IF target_plan_id IS NULL THEN
    RAISE EXCEPTION 'supplemental-plan version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;

  INSERT INTO compensation.salary_structure_supplemental_plan_binding(
    id,
    tenant_id,
    salary_structure_id,
    salary_structure_version_id,
    supplemental_plan_id,
    supplemental_plan_version_id,
    sequence_no,
    effective_from,
    effective_to,
    created_by
  ) VALUES (
    p_binding_id,
    p_tenant_id,
    p_structure_id,
    p_structure_version_id,
    target_plan_id,
    p_plan_version_id,
    p_sequence_no,
    p_effective_from,
    p_effective_to,
    p_actor
  );

  UPDATE compensation.salary_structure_version structure
     SET composition_revision=structure.composition_revision+1,
         updated_at=clock_timestamp(),
         updated_by=p_actor,
         version_no=structure.version_no+1
   WHERE structure.tenant_id=p_tenant_id
     AND structure.id=p_structure_version_id
  RETURNING structure.composition_revision INTO next_revision;

  RETURN next_revision;
END $$;

CREATE OR REPLACE FUNCTION compensation.assert_validation_line_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  structure_status varchar;
  structure_fingerprint varchar;
  validation_result_hash varchar;
  validation_structure_version_id uuid;
  source_type varchar;
  source_binding_id uuid;
  source_plan_line_id uuid;
BEGIN
  SELECT structure.approval_status,
         structure.validation_fingerprint,
         validation.result_hash,
         validation.salary_structure_version_id
    INTO structure_status,
         structure_fingerprint,
         validation_result_hash,
         validation_structure_version_id
    FROM compensation.salary_structure_validation validation
    JOIN compensation.salary_structure_version structure
      ON structure.tenant_id=validation.tenant_id
     AND structure.id=validation.salary_structure_version_id
   WHERE validation.tenant_id=NEW.tenant_id
     AND validation.id=NEW.validation_id;

  IF structure_status IS NULL THEN
    RAISE EXCEPTION 'validation does not exist in the current tenant'
      USING ERRCODE='23503';
  END IF;
  IF structure_status<>'DRAFT' THEN
    RAISE EXCEPTION 'validation lines can be added only while the structure is draft'
      USING ERRCODE='23514';
  END IF;
  IF structure_fingerprint=validation_result_hash THEN
    RAISE EXCEPTION 'validation lines cannot be appended after evidence is bound'
      USING ERRCODE='23514';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_validation current_validation
      JOIN compensation.salary_structure_validation newer_validation
        ON newer_validation.tenant_id=current_validation.tenant_id
       AND newer_validation.salary_structure_version_id=
         current_validation.salary_structure_version_id
       AND (newer_validation.created_at,newer_validation.id)>
         (current_validation.created_at,current_validation.id)
     WHERE current_validation.tenant_id=NEW.tenant_id
       AND current_validation.id=NEW.validation_id
  ) THEN
    RAISE EXCEPTION 'validation lines can be added only to the latest evidence'
      USING ERRCODE='23514';
  END IF;

  source_type=coalesce(nullif(NEW.evidence_json->>'sourceType',''),'BASE');

  IF source_type='BASE' THEN
    IF NEW.component_version_id IS NULL OR NOT EXISTS (
      SELECT 1
        FROM compensation.salary_structure_line line
       WHERE line.tenant_id=NEW.tenant_id
         AND line.salary_structure_version_id=
           validation_structure_version_id
         AND line.component_version_id=NEW.component_version_id
    ) THEN
      RAISE EXCEPTION 'base validation component must belong to the exact salary structure'
        USING ERRCODE='23514';
    END IF;
  ELSIF source_type='SUPPLEMENTAL' THEN
    BEGIN
      source_binding_id=
        nullif(NEW.evidence_json->>'sourceBindingId','')::uuid;
      source_plan_line_id=
        nullif(NEW.evidence_json->>'sourcePlanLineId','')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
      RAISE EXCEPTION 'supplemental validation source identifiers must be UUIDs'
        USING ERRCODE='23514';
    END;

    IF source_binding_id IS NULL OR source_plan_line_id IS NULL OR NOT EXISTS (
      SELECT 1
        FROM compensation.salary_structure_supplemental_plan_binding binding
        JOIN compensation.salary_supplemental_plan_line plan_line
          ON plan_line.tenant_id=binding.tenant_id
         AND plan_line.supplemental_plan_version_id=
           binding.supplemental_plan_version_id
       WHERE binding.tenant_id=NEW.tenant_id
         AND binding.id=source_binding_id
         AND binding.salary_structure_version_id=
           validation_structure_version_id
         AND plan_line.id=source_plan_line_id
         AND plan_line.component_id=NEW.component_id
         AND plan_line.component_version_id=NEW.component_version_id
    ) THEN
      RAISE EXCEPTION 'supplemental validation line must identify an exact bound plan line'
        USING ERRCODE='23514';
    END IF;
  ELSE
    RAISE EXCEPTION 'validation sourceType must be BASE or SUPPLEMENTAL'
      USING ERRCODE='23514';
  END IF;

  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION compensation.bind_salary_structure_validation(
  p_tenant_id uuid,
  p_structure_version_id uuid,
  p_validation_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  structure_status varchar;
  structure_schema smallint;
  structure_hash varchar(64);
  structure_version bigint;
  current_revision bigint;
  validation_status varchar;
  validation_errors integer;
  validation_hash varchar(64);
  validation_configuration_hash varchar(64);
  validation_summary jsonb;
  has_bindings boolean;
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_changed_at IS NULL THEN
    RAISE EXCEPTION 'actor and change timestamp are required'
      USING ERRCODE='23514';
  END IF;

  SELECT structure.approval_status,
         structure.structure_schema_version,
         structure.configuration_hash,
         structure.version_no,
         structure.composition_revision
    INTO structure_status,
         structure_schema,
         structure_hash,
         structure_version,
         current_revision
    FROM compensation.salary_structure_version structure
   WHERE structure.tenant_id=p_tenant_id
     AND structure.id=p_structure_version_id
   FOR UPDATE;

  IF structure_status IS NULL
     OR structure_schema<>1
     OR structure_status<>'DRAFT'
     OR structure_version<>p_expected_version THEN
    RETURN 0;
  END IF;

  SELECT validation.validation_status,
         validation.blocking_error_count,
         validation.result_hash,
         validation.configuration_hash,
         validation.summary_json
    INTO validation_status,
         validation_errors,
         validation_hash,
         validation_configuration_hash,
         validation_summary
    FROM compensation.salary_structure_validation validation
   WHERE validation.tenant_id=p_tenant_id
     AND validation.id=p_validation_id
     AND validation.salary_structure_version_id=p_structure_version_id;

  IF validation_status IS NULL
     OR validation_status<>'PASS'
     OR validation_errors<>0
     OR validation_configuration_hash IS DISTINCT FROM structure_hash THEN
    RETURN 0;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_validation newer
      JOIN compensation.salary_structure_validation selected
        ON selected.tenant_id=newer.tenant_id
       AND selected.id=p_validation_id
     WHERE newer.tenant_id=p_tenant_id
       AND newer.salary_structure_version_id=p_structure_version_id
       AND (newer.created_at,newer.id)>(selected.created_at,selected.id)
  ) THEN
    RETURN 0;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_line structure_line
     WHERE structure_line.tenant_id=p_tenant_id
       AND structure_line.salary_structure_version_id=p_structure_version_id
       AND NOT EXISTS (
         SELECT 1
           FROM compensation.salary_structure_validation_line validation_line
          WHERE validation_line.tenant_id=p_tenant_id
            AND validation_line.validation_id=p_validation_id
            AND coalesce(
              validation_line.evidence_json->>'sourceType',
              'BASE'
            )='BASE'
            AND validation_line.component_version_id=
              structure_line.component_version_id
       )
  ) THEN
    RETURN 0;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_validation_line validation_line
     WHERE validation_line.tenant_id=p_tenant_id
       AND validation_line.validation_id=p_validation_id
       AND coalesce(
         validation_line.evidence_json->>'sourceType',
         'BASE'
       )='BASE'
       AND NOT EXISTS (
         SELECT 1
           FROM compensation.salary_structure_line structure_line
          WHERE structure_line.tenant_id=p_tenant_id
            AND structure_line.salary_structure_version_id=
              p_structure_version_id
            AND structure_line.component_version_id=
              validation_line.component_version_id
       )
  ) THEN
    RETURN 0;
  END IF;

  SELECT EXISTS (
    SELECT 1
      FROM compensation.salary_structure_supplemental_plan_binding binding
     WHERE binding.tenant_id=p_tenant_id
       AND binding.salary_structure_version_id=p_structure_version_id
  ) INTO has_bindings;

  IF has_bindings THEN
    IF coalesce(validation_summary->>'composedSimulation','false')<>'true'
       OR nullif(
         validation_summary->>'compositionRevision',
         ''
       ) IS NULL
       OR (
         validation_summary->>'compositionRevision'
       )::bigint<>current_revision THEN
      RETURN 0;
    END IF;

    IF EXISTS (
      SELECT 1
        FROM compensation.salary_structure_supplemental_plan_binding binding
        JOIN compensation.salary_supplemental_plan_line plan_line
          ON plan_line.tenant_id=binding.tenant_id
         AND plan_line.supplemental_plan_version_id=
           binding.supplemental_plan_version_id
       WHERE binding.tenant_id=p_tenant_id
         AND binding.salary_structure_version_id=p_structure_version_id
         AND NOT EXISTS (
           SELECT 1
             FROM compensation.salary_structure_validation_line validation_line
            WHERE validation_line.tenant_id=p_tenant_id
              AND validation_line.validation_id=p_validation_id
              AND validation_line.evidence_json->>'sourceType'='SUPPLEMENTAL'
              AND validation_line.evidence_json->>'sourceBindingId'=
                binding.id::text
              AND validation_line.evidence_json->>'sourcePlanLineId'=
                plan_line.id::text
              AND validation_line.component_version_id=
                plan_line.component_version_id
         )
    ) THEN
      RETURN 0;
    END IF;

    IF EXISTS (
      SELECT 1
        FROM compensation.salary_structure_validation_line validation_line
       WHERE validation_line.tenant_id=p_tenant_id
         AND validation_line.validation_id=p_validation_id
         AND validation_line.evidence_json->>'sourceType'='SUPPLEMENTAL'
         AND NOT EXISTS (
           SELECT 1
             FROM compensation.salary_structure_supplemental_plan_binding binding
             JOIN compensation.salary_supplemental_plan_line plan_line
               ON plan_line.tenant_id=binding.tenant_id
              AND plan_line.supplemental_plan_version_id=
                binding.supplemental_plan_version_id
            WHERE binding.tenant_id=p_tenant_id
              AND binding.salary_structure_version_id=
                p_structure_version_id
              AND binding.id::text=
                validation_line.evidence_json->>'sourceBindingId'
              AND plan_line.id::text=
                validation_line.evidence_json->>'sourcePlanLineId'
              AND plan_line.component_version_id=
                validation_line.component_version_id
         )
    ) THEN
      RETURN 0;
    END IF;
  ELSIF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_validation_line validation_line
     WHERE validation_line.tenant_id=p_tenant_id
       AND validation_line.validation_id=p_validation_id
       AND validation_line.evidence_json->>'sourceType'='SUPPLEMENTAL'
  ) THEN
    RETURN 0;
  END IF;

  UPDATE compensation.salary_structure_version structure
     SET validation_fingerprint=validation_hash,
         updated_at=p_changed_at,
         updated_by=p_actor,
         version_no=structure.version_no+1
   WHERE structure.tenant_id=p_tenant_id
     AND structure.id=p_structure_version_id
     AND structure.version_no=p_expected_version;

  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION compensation.prevent_unvalidated_supplemental_composition_approval()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF NEW.approval_status='APPROVED'
     AND OLD.approval_status IS DISTINCT FROM 'APPROVED'
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_structure_supplemental_plan_binding binding
        WHERE binding.tenant_id=NEW.tenant_id
          AND binding.salary_structure_version_id=NEW.id
     )
     AND (
       NEW.validation_fingerprint IS NULL
       OR NOT EXISTS (
         SELECT 1
           FROM compensation.salary_structure_validation validation
          WHERE validation.tenant_id=NEW.tenant_id
            AND validation.salary_structure_version_id=NEW.id
            AND validation.result_hash=NEW.validation_fingerprint
            AND validation.validation_status='PASS'
            AND validation.blocking_error_count=0
            AND validation.summary_json->>'composedSimulation'='true'
            AND nullif(
              validation.summary_json->>'compositionRevision',
              ''
            ) IS NOT NULL
            AND (
              validation.summary_json->>'compositionRevision'
            )::bigint=NEW.composition_revision
       )
     ) THEN
    RAISE EXCEPTION
      'supplemental-plan composition requires a current passing composed validation'
      USING ERRCODE='23514';
  END IF;

  RETURN NEW;
END $$;

REVOKE INSERT
  ON compensation.salary_structure_supplemental_plan_binding
  FROM payroll_app;

REVOKE ALL
  ON FUNCTION compensation.assert_supplemental_percentage_base()
  FROM PUBLIC;
REVOKE ALL
  ON FUNCTION compensation.assert_supplemental_binding_percentage_bases()
  FROM PUBLIC;
REVOKE ALL
  ON FUNCTION compensation.bind_salary_structure_supplemental_plan(
    uuid,uuid,uuid,uuid,uuid,integer,date,date,varchar
  )
  FROM PUBLIC;

GRANT EXECUTE
  ON FUNCTION compensation.bind_salary_structure_supplemental_plan(
    uuid,uuid,uuid,uuid,uuid,integer,date,date,varchar
  )
  TO payroll_app;

COMMENT ON COLUMN compensation.salary_structure_version.composition_revision IS
  'Monotonic revision incremented by each immutable supplemental binding; bound composed validation must match it.';

COMMENT ON COLUMN compensation.salary_supplemental_plan_line.percentage_base_component_version_id IS
  'Exact approved component-version basis for a supplemental percentage contribution; never an implicit target or residual basis.';
