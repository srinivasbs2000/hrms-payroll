-- P5-SSC-01 G02A: salary-structure supplemental-plan composition.
-- Forward-only from V041. V001-V041 are immutable.
-- No country-specific statutory rate, threshold or legal conclusion is encoded here.

CREATE TABLE compensation.salary_supplemental_plan (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE compensation.salary_supplemental_plan_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  supplemental_plan_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  plan_type varchar(24) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
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
  UNIQUE (tenant_id, id, supplemental_plan_id),
  UNIQUE (tenant_id, supplemental_plan_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (plan_type IN ('ALLOWANCE','BENEFIT','INCENTIVE')),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK (
    (approval_status='APPROVED' AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL AND btrim(approved_by) <> '')
    OR
    (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no >= 0),
  FOREIGN KEY (tenant_id, supplemental_plan_id)
    REFERENCES compensation.salary_supplemental_plan(tenant_id, id),
  CONSTRAINT salary_supplemental_plan_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, supplemental_plan_id)
    REFERENCES compensation.salary_supplemental_plan_version(
      tenant_id, id, supplemental_plan_id)
);

ALTER TABLE compensation.salary_supplemental_plan_version
  ADD CONSTRAINT salary_supplemental_plan_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    supplemental_plan_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX salary_supplemental_plan_one_successor_uk
  ON compensation.salary_supplemental_plan_version(
    tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE TABLE compensation.salary_supplemental_plan_line (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  supplemental_plan_id uuid NOT NULL,
  supplemental_plan_version_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  sequence_no integer NOT NULL,
  default_amount numeric(19,4),
  default_percentage numeric(12,6),
  minimum_amount numeric(19,4),
  maximum_amount numeric(19,4),
  employee_override_allowed boolean NOT NULL DEFAULT false,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, supplemental_plan_version_id, sequence_no),
  UNIQUE (tenant_id, supplemental_plan_version_id, component_id),
  CHECK (sequence_no > 0),
  CHECK (default_amount IS NULL OR default_amount >= 0),
  CHECK (
    default_percentage IS NULL
    OR (default_percentage > 0 AND default_percentage <= 100)
  ),
  CHECK (NOT (default_amount IS NOT NULL AND default_percentage IS NOT NULL)),
  CHECK (minimum_amount IS NULL OR minimum_amount >= 0),
  CHECK (maximum_amount IS NULL OR maximum_amount >= 0),
  CHECK (
    minimum_amount IS NULL OR maximum_amount IS NULL
    OR maximum_amount >= minimum_amount
  ),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no >= 0),
  CONSTRAINT salary_supplemental_plan_line_plan_version_fk
    FOREIGN KEY (
      tenant_id, supplemental_plan_version_id, supplemental_plan_id
    )
    REFERENCES compensation.salary_supplemental_plan_version(
      tenant_id, id, supplemental_plan_id
    ),
  CONSTRAINT salary_supplemental_plan_line_component_version_fk
    FOREIGN KEY (tenant_id, component_version_id, component_id)
    REFERENCES compensation.pay_component_version(tenant_id, id, component_id)
);

CREATE INDEX salary_supplemental_plan_line_component_ix
  ON compensation.salary_supplemental_plan_line(
    tenant_id, component_id, component_version_id);

CREATE TABLE compensation.salary_structure_supplemental_plan_binding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  salary_structure_id uuid NOT NULL,
  salary_structure_version_id uuid NOT NULL,
  supplemental_plan_id uuid NOT NULL,
  supplemental_plan_version_id uuid NOT NULL,
  sequence_no integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id, salary_structure_version_id, supplemental_plan_version_id
  ),
  UNIQUE (tenant_id, salary_structure_version_id, sequence_no),
  CHECK (sequence_no > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (version_no >= 0),
  CONSTRAINT salary_structure_supplemental_binding_structure_fk
    FOREIGN KEY (
      tenant_id, salary_structure_version_id, salary_structure_id
    )
    REFERENCES compensation.salary_structure_version(
      tenant_id, id, salary_structure_id
    ),
  CONSTRAINT salary_structure_supplemental_binding_plan_fk
    FOREIGN KEY (
      tenant_id, supplemental_plan_version_id, supplemental_plan_id
    )
    REFERENCES compensation.salary_supplemental_plan_version(
      tenant_id, id, supplemental_plan_id
    )
);

CREATE INDEX salary_structure_supplemental_binding_structure_ix
  ON compensation.salary_structure_supplemental_plan_binding(
    tenant_id, salary_structure_id, salary_structure_version_id);

CREATE OR REPLACE FUNCTION compensation.require_supplemental_plan_runtime_defaults()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    IF TG_TABLE_NAME='salary_supplemental_plan'
       AND NEW.lifecycle_status <> 'PENDING_APPROVAL' THEN
      RAISE EXCEPTION 'runtime supplemental-plan identities must start pending approval'
        USING ERRCODE='23514';
    END IF;
    IF TG_TABLE_NAME='salary_supplemental_plan_version'
       AND NEW.approval_status <> 'DRAFT' THEN
      RAISE EXCEPTION 'runtime supplemental-plan versions must start as drafts'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_supplemental_plan_runtime_default
  BEFORE INSERT ON compensation.salary_supplemental_plan
  FOR EACH ROW
  EXECUTE FUNCTION compensation.require_supplemental_plan_runtime_defaults();

CREATE TRIGGER salary_supplemental_plan_version_runtime_default
  BEFORE INSERT ON compensation.salary_supplemental_plan_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.require_supplemental_plan_runtime_defaults();

CREATE OR REPLACE FUNCTION compensation.assert_salary_supplemental_plan_line()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  plan_status varchar;
  plan_from date;
  plan_to date;
  component_status varchar;
  component_lifecycle varchar;
  component_from date;
  component_to date;
BEGIN
  SELECT v.approval_status, v.effective_from, v.effective_to
    INTO plan_status, plan_from, plan_to
    FROM compensation.salary_supplemental_plan_version v
   WHERE v.tenant_id=NEW.tenant_id
     AND v.id=NEW.supplemental_plan_version_id
     AND v.supplemental_plan_id=NEW.supplemental_plan_id;

  IF plan_status IS NULL THEN
    RAISE EXCEPTION 'supplemental-plan version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF plan_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'supplemental-plan lines can be added only to a draft version'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < plan_from
     OR (plan_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > plan_to)) THEN
    RAISE EXCEPTION 'supplemental-plan line must stay inside plan effective range'
      USING ERRCODE='23514';
  END IF;

  SELECT pv.approval_status, p.lifecycle_status,
         pv.effective_from, pv.effective_to
    INTO component_status, component_lifecycle, component_from, component_to
    FROM compensation.pay_component_version pv
    JOIN compensation.pay_component p
      ON p.tenant_id=pv.tenant_id AND p.id=pv.component_id
   WHERE pv.tenant_id=NEW.tenant_id
     AND pv.id=NEW.component_version_id
     AND pv.component_id=NEW.component_id;

  IF component_status IS NULL THEN
    RAISE EXCEPTION 'component version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF component_status <> 'APPROVED' OR component_lifecycle <> 'ACTIVE' THEN
    RAISE EXCEPTION 'supplemental-plan lines require an active approved component'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < component_from
     OR (component_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > component_to)) THEN
    RAISE EXCEPTION 'supplemental-plan line exceeds component-version range'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_supplemental_plan_line_dependencies
  BEFORE INSERT ON compensation.salary_supplemental_plan_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_supplemental_plan_line();

CREATE OR REPLACE FUNCTION compensation.assert_salary_structure_supplemental_binding()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE
  structure_status varchar;
  structure_schema smallint;
  structure_from date;
  structure_to date;
  structure_fingerprint varchar(64);
  plan_status varchar;
  plan_lifecycle varchar;
  plan_from date;
  plan_to date;
BEGIN
  SELECT v.approval_status, v.structure_schema_version,
         v.effective_from, v.effective_to, v.validation_fingerprint
    INTO structure_status, structure_schema, structure_from, structure_to,
         structure_fingerprint
    FROM compensation.salary_structure_version v
   WHERE v.tenant_id=NEW.tenant_id
     AND v.id=NEW.salary_structure_version_id
     AND v.salary_structure_id=NEW.salary_structure_id;

  IF structure_status IS NULL THEN
    RAISE EXCEPTION 'salary-structure version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF structure_status <> 'DRAFT' OR structure_schema <> 1 THEN
    RAISE EXCEPTION 'supplemental plans bind only to schema-1 draft structures'
      USING ERRCODE='23514';
  END IF;
  IF structure_fingerprint IS NOT NULL THEN
    RAISE EXCEPTION 'supplemental plans cannot be bound after validation is bound'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < structure_from
     OR (structure_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > structure_to)) THEN
    RAISE EXCEPTION 'supplemental-plan binding exceeds salary-structure range'
      USING ERRCODE='23514';
  END IF;

  SELECT v.approval_status, p.lifecycle_status,
         v.effective_from, v.effective_to
    INTO plan_status, plan_lifecycle, plan_from, plan_to
    FROM compensation.salary_supplemental_plan_version v
    JOIN compensation.salary_supplemental_plan p
      ON p.tenant_id=v.tenant_id AND p.id=v.supplemental_plan_id
   WHERE v.tenant_id=NEW.tenant_id
     AND v.id=NEW.supplemental_plan_version_id
     AND v.supplemental_plan_id=NEW.supplemental_plan_id;

  IF plan_status IS NULL THEN
    RAISE EXCEPTION 'supplemental-plan version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF plan_status <> 'APPROVED' OR plan_lifecycle <> 'ACTIVE' THEN
    RAISE EXCEPTION 'only active approved supplemental plans can be bound'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < plan_from
     OR (plan_to IS NOT NULL
         AND (NEW.effective_to IS NULL OR NEW.effective_to > plan_to)) THEN
    RAISE EXCEPTION 'binding exceeds supplemental-plan effective range'
      USING ERRCODE='23514';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_supplemental_plan_line plan_line
      JOIN compensation.salary_structure_line structure_line
        ON structure_line.tenant_id=plan_line.tenant_id
       AND structure_line.salary_structure_version_id=NEW.salary_structure_version_id
      JOIN compensation.pay_component_version base_component_version
        ON base_component_version.tenant_id=structure_line.tenant_id
       AND base_component_version.id=structure_line.component_version_id
       AND base_component_version.component_id=plan_line.component_id
     WHERE plan_line.tenant_id=NEW.tenant_id
       AND plan_line.supplemental_plan_version_id=NEW.supplemental_plan_version_id
       AND daterange(plan_line.effective_from,plan_line.effective_to,'[)')
           && daterange(NEW.effective_from,NEW.effective_to,'[)')
  ) THEN
    RAISE EXCEPTION
      'supplemental plan duplicates an active component already present in the base structure'
      USING ERRCODE='23514';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM compensation.salary_structure_supplemental_plan_binding existing
      JOIN compensation.salary_supplemental_plan_line existing_line
        ON existing_line.tenant_id=existing.tenant_id
       AND existing_line.supplemental_plan_version_id=existing.supplemental_plan_version_id
      JOIN compensation.salary_supplemental_plan_line new_line
        ON new_line.tenant_id=existing_line.tenant_id
       AND new_line.supplemental_plan_version_id=NEW.supplemental_plan_version_id
       AND new_line.component_id=existing_line.component_id
     WHERE existing.tenant_id=NEW.tenant_id
       AND existing.salary_structure_version_id=NEW.salary_structure_version_id
       AND daterange(existing.effective_from,existing.effective_to,'[)')
           && daterange(NEW.effective_from,NEW.effective_to,'[)')
       AND daterange(existing_line.effective_from,existing_line.effective_to,'[)')
           && daterange(new_line.effective_from,new_line.effective_to,'[)')
  ) THEN
    RAISE EXCEPTION
      'supplemental plans cannot contribute duplicate active component identities'
      USING ERRCODE='23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_supplemental_binding_dependencies
  BEFORE INSERT ON compensation.salary_structure_supplemental_plan_binding
  FOR EACH ROW
  EXECUTE FUNCTION compensation.assert_salary_structure_supplemental_binding();

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
     ) THEN
    RAISE EXCEPTION
      'supplemental-plan composition requires completed P5-SSC-01 validation integration before structure approval'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_structure_supplemental_approval_guard
  BEFORE UPDATE OF approval_status ON compensation.salary_structure_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.prevent_unvalidated_supplemental_composition_approval();

CREATE OR REPLACE FUNCTION compensation.reject_supplemental_plan_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    RAISE EXCEPTION 'immutable supplemental-plan configuration: %.%',
      TG_TABLE_SCHEMA, TG_TABLE_NAME
      USING ERRCODE='42501';
  END IF;
  IF TG_OP='DELETE' THEN
    RAISE EXCEPTION 'supplemental-plan versions, lines and bindings cannot be deleted'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER salary_supplemental_plan_version_immutable
  BEFORE UPDATE OR DELETE ON compensation.salary_supplemental_plan_version
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_supplemental_plan_mutation();

CREATE TRIGGER salary_supplemental_plan_line_immutable
  BEFORE UPDATE OR DELETE ON compensation.salary_supplemental_plan_line
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_supplemental_plan_mutation();

CREATE TRIGGER salary_structure_supplemental_binding_immutable
  BEFORE UPDATE OR DELETE ON compensation.salary_structure_supplemental_plan_binding
  FOR EACH ROW
  EXECUTE FUNCTION compensation.reject_supplemental_plan_mutation();

CREATE OR REPLACE FUNCTION compensation.lock_salary_supplemental_plan(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  status varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;

  SELECT lifecycle_status
    INTO status
    FROM compensation.salary_supplemental_plan
   WHERE tenant_id=p_tenant_id AND id=p_identity_id
   FOR UPDATE;

  RETURN status;
END $$;

CREATE OR REPLACE FUNCTION compensation.approve_salary_supplemental_plan_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=pg_catalog,compensation,platform AS $$
DECLARE
  affected bigint;
  target_plan_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' OR p_approved_at IS NULL THEN
    RAISE EXCEPTION 'actor and approval timestamp are required'
      USING ERRCODE='23514';
  END IF;

  UPDATE compensation.salary_supplemental_plan_version version
     SET approval_status='APPROVED',
         approved_at=p_approved_at,
         approved_by=p_actor,
         updated_at=p_approved_at,
         updated_by=p_actor,
         version_no=version_no+1
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id
     AND version.approval_status='DRAFT'
     AND version.created_by<>p_actor
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_supplemental_plan identity
        WHERE identity.tenant_id=version.tenant_id
          AND identity.id=version.supplemental_plan_id
          AND identity.lifecycle_status<>'RETIRED'
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_supplemental_plan_version successor
        WHERE successor.tenant_id=version.tenant_id
          AND successor.supersedes_version_id=version.id
     )
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_supplemental_plan_line line
        WHERE line.tenant_id=version.tenant_id
          AND line.supplemental_plan_version_id=version.id
     )
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.salary_supplemental_plan_line line
         LEFT JOIN compensation.pay_component_version component_version
           ON component_version.tenant_id=line.tenant_id
          AND component_version.id=line.component_version_id
         LEFT JOIN compensation.pay_component component
           ON component.tenant_id=component_version.tenant_id
          AND component.id=component_version.component_id
        WHERE line.tenant_id=version.tenant_id
          AND line.supplemental_plan_version_id=version.id
          AND (
            component_version.id IS NULL
            OR component_version.approval_status<>'APPROVED'
            OR component.lifecycle_status<>'ACTIVE'
            OR line.effective_from<version.effective_from
            OR (
              version.effective_to IS NOT NULL
              AND (line.effective_to IS NULL OR line.effective_to>version.effective_to)
            )
            OR line.effective_from<component_version.effective_from
            OR (
              component_version.effective_to IS NOT NULL
              AND (line.effective_to IS NULL OR line.effective_to>component_version.effective_to)
            )
          )
     )
  RETURNING version.supplemental_plan_id INTO target_plan_id;

  GET DIAGNOSTICS affected=ROW_COUNT;

  IF affected=1 THEN
    UPDATE compensation.salary_supplemental_plan
       SET lifecycle_status='ACTIVE',
           updated_at=p_approved_at,
           updated_by=p_actor,
           version_no=version_no+1
     WHERE tenant_id=p_tenant_id
       AND id=target_plan_id
       AND lifecycle_status='PENDING_APPROVAL';
  END IF;

  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.require_supplemental_plan_runtime_defaults() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_salary_supplemental_plan_line() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_salary_structure_supplemental_binding() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_supplemental_plan_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.prevent_unvalidated_supplemental_composition_approval() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.lock_salary_supplemental_plan(uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_salary_supplemental_plan_version(uuid,uuid,varchar,timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION compensation.lock_salary_supplemental_plan(uuid,uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_salary_supplemental_plan_version(uuid,uuid,varchar,timestamptz) TO payroll_app;

ALTER TABLE compensation.salary_supplemental_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_supplemental_plan FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_supplemental_plan_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_supplemental_plan_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_supplemental_plan_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_supplemental_plan_line FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_supplemental_plan_binding ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.salary_structure_supplemental_plan_binding FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON compensation.salary_supplemental_plan
  USING (tenant_id=platform.current_tenant_id())
  WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.salary_supplemental_plan_version
  USING (tenant_id=platform.current_tenant_id())
  WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.salary_supplemental_plan_line
  USING (tenant_id=platform.current_tenant_id())
  WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.salary_structure_supplemental_plan_binding
  USING (tenant_id=platform.current_tenant_id())
  WITH CHECK (tenant_id=platform.current_tenant_id());

GRANT SELECT, INSERT ON
  compensation.salary_supplemental_plan,
  compensation.salary_supplemental_plan_version,
  compensation.salary_supplemental_plan_line,
  compensation.salary_structure_supplemental_plan_binding
TO payroll_app;

REVOKE UPDATE, DELETE ON
  compensation.salary_supplemental_plan,
  compensation.salary_supplemental_plan_version,
  compensation.salary_supplemental_plan_line,
  compensation.salary_structure_supplemental_plan_binding
FROM payroll_app;

REVOKE CREATE ON SCHEMA compensation FROM payroll_app;

COMMENT ON TABLE compensation.salary_supplemental_plan IS
  'Stable tenant-scoped identity for a reusable salary-structure supplemental plan.';
COMMENT ON TABLE compensation.salary_supplemental_plan_version IS
  'Immutable effective-dated supplemental-plan version with maker-checker approval.';
COMMENT ON TABLE compensation.salary_supplemental_plan_line IS
  'Exact approved component-version contribution owned by a supplemental-plan version.';
COMMENT ON TABLE compensation.salary_structure_supplemental_plan_binding IS
  'Immutable binding of an approved supplemental plan to a schema-1 draft salary structure; salary structure is the base, so deep inheritance is structurally impossible.';
