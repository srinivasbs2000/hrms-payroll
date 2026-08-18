-- P5-EPA-01 G02A: employee payroll assignment and compensation binding completion.
-- Forward-only from V049. V001-V049 are immutable.
--
-- This migration preserves every V021/V022 stable identity and historical UUID.
-- Legacy rows are not assigned guessed PSU, aggregation, work-assignment or target
-- facts. New authoritative approvals require the complete V050 contract.
-- Country/currency authority remains organisation/compensation owned.

ALTER TABLE employee_payroll.payroll_relationship_version
  ADD COLUMN boundary_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN payroll_statutory_unit_version_id uuid,
  ADD COLUMN aggregation_boundary_key varchar(120),
  ADD CONSTRAINT payroll_relationship_boundary_schema_ck
    CHECK (boundary_schema_version IN (0, 1)),
  ADD CONSTRAINT payroll_relationship_boundary_shape_ck
    CHECK (
      (boundary_schema_version = 0
       AND payroll_statutory_unit_version_id IS NULL
       AND aggregation_boundary_key IS NULL)
      OR
      (boundary_schema_version = 1
       AND payroll_statutory_unit_version_id IS NOT NULL
       AND aggregation_boundary_key IS NOT NULL
       AND btrim(aggregation_boundary_key) <> '')
    ),
  ADD CONSTRAINT payroll_relationship_boundary_psu_fk
    FOREIGN KEY (tenant_id, payroll_statutory_unit_version_id)
    REFERENCES organisation.payroll_statutory_unit_version(tenant_id, id);

ALTER TABLE employee_payroll.payroll_assignment
  ADD COLUMN source_work_assignment_ref varchar(160);

CREATE UNIQUE INDEX payroll_assignment_source_work_ref_uk
  ON employee_payroll.payroll_assignment(
    tenant_id, payroll_relationship_id, source_work_assignment_ref)
  WHERE source_work_assignment_ref IS NOT NULL;

ALTER TABLE employee_payroll.payroll_assignment_version
  ADD COLUMN binding_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN payroll_role varchar(12),
  ADD COLUMN payroll_eligibility_from date,
  ADD COLUMN payroll_eligibility_to date,
  ADD CONSTRAINT payroll_assignment_binding_schema_ck
    CHECK (binding_schema_version IN (0, 1)),
  ADD CONSTRAINT payroll_assignment_binding_shape_ck
    CHECK (
      (binding_schema_version = 0
       AND payroll_role IS NULL
       AND payroll_eligibility_from IS NULL
       AND payroll_eligibility_to IS NULL)
      OR
      (binding_schema_version = 1
       AND payroll_role IN ('PRIMARY', 'SECONDARY')
       AND payroll_eligibility_from IS NOT NULL
       AND (payroll_eligibility_to IS NULL
            OR payroll_eligibility_to > payroll_eligibility_from))
    );

ALTER TABLE employee_payroll.pay_group_assignment
  ADD COLUMN contract_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN impact_assessment_through date,
  ADD COLUMN impact_assessed_at timestamptz,
  ADD COLUMN impact_assessed_by varchar(160),
  ADD CONSTRAINT pay_group_assignment_contract_schema_ck
    CHECK (contract_schema_version IN (0, 1)),
  ADD CONSTRAINT pay_group_assignment_impact_shape_ck
    CHECK (
      (contract_schema_version = 0
       AND impact_assessment_through IS NULL
       AND impact_assessed_at IS NULL
       AND impact_assessed_by IS NULL)
      OR
      (contract_schema_version = 1
       AND impact_assessment_through IS NOT NULL
       AND impact_assessed_at IS NOT NULL
       AND impact_assessed_by IS NOT NULL
       AND btrim(impact_assessed_by) <> '')
    );

ALTER TABLE employee_payroll.salary_assignment
  DROP CONSTRAINT salary_assignment_currency_ck,
  ALTER COLUMN monthly_amount DROP NOT NULL,
  ADD COLUMN contract_schema_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN target_type varchar(40),
  ADD COLUMN target_value numeric(19,4),
  ADD COLUMN target_frequency varchar(20),
  ADD COLUMN source_compensation_event_id uuid,
  ADD CONSTRAINT salary_assignment_contract_schema_ck
    CHECK (contract_schema_version IN (0, 1)),
  ADD CONSTRAINT salary_assignment_contract_shape_ck
    CHECK (
      (contract_schema_version = 0
       AND monthly_amount IS NOT NULL
       AND target_type IS NULL
       AND target_value IS NULL
       AND target_frequency IS NULL
       AND source_compensation_event_id IS NULL)
      OR
      (contract_schema_version = 1
       AND monthly_amount IS NULL
       AND target_type IS NOT NULL
       AND target_value IS NOT NULL
       AND target_value >= 0
       AND target_frequency IS NOT NULL
       AND source_compensation_event_id IS NOT NULL)
    );

CREATE TABLE employee_payroll.pay_group_assignment_impact_period (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  pay_group_assignment_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  reason_code varchar(40) NOT NULL DEFAULT 'EFFECTIVE_RANGE_OVERLAP',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, pay_group_assignment_id, pay_period_id),
  CHECK (reason_code IN ('EFFECTIVE_RANGE_OVERLAP')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, pay_group_assignment_id)
    REFERENCES employee_payroll.pay_group_assignment(tenant_id, id),
  FOREIGN KEY (tenant_id, pay_period_id)
    REFERENCES organisation.pay_period(tenant_id, id)
);

CREATE TABLE employee_payroll.compensation_change_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_assignment_id uuid NOT NULL,
  event_type varchar(24) NOT NULL,
  effective_date date NOT NULL,
  source_event_id uuid,
  reason varchar(500) NOT NULL,
  assessment_through date,
  impact_assessed_at timestamptz,
  impact_assessed_by varchar(160),
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (event_type IN (
    'PROSPECTIVE','CURRENT_PERIOD','RETROSPECTIVE','CORRECTION','REVERSAL')),
  CHECK (btrim(reason) <> ''),
  CHECK (assessment_through IS NULL OR assessment_through >= effective_date),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK (
    (event_type IN ('CORRECTION','REVERSAL') AND source_event_id IS NOT NULL)
    OR
    (event_type NOT IN ('CORRECTION','REVERSAL') AND source_event_id IS NULL)
  ),
  CHECK (
    (approval_status='APPROVED' AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL AND btrim(approved_by) <> '')
    OR
    (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)
  ),
  CHECK (
    (assessment_through IS NULL AND impact_assessed_at IS NULL
      AND impact_assessed_by IS NULL)
    OR
    (assessment_through IS NOT NULL AND impact_assessed_at IS NOT NULL
      AND impact_assessed_by IS NOT NULL AND btrim(impact_assessed_by) <> '')
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_assignment_id)
    REFERENCES employee_payroll.payroll_assignment(tenant_id, id),
  FOREIGN KEY (tenant_id, source_event_id)
    REFERENCES employee_payroll.compensation_change_event(tenant_id, id)
);

ALTER TABLE employee_payroll.salary_assignment
  ADD CONSTRAINT salary_assignment_source_event_fk
    FOREIGN KEY (tenant_id, source_compensation_event_id)
    REFERENCES employee_payroll.compensation_change_event(tenant_id, id);

CREATE TABLE employee_payroll.compensation_change_impact (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  compensation_change_event_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  reason_code varchar(40) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, compensation_change_event_id, pay_period_id),
  CHECK (reason_code IN ('EFFECTIVE_DATE_OVERLAP')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, compensation_change_event_id)
    REFERENCES employee_payroll.compensation_change_event(tenant_id, id),
  FOREIGN KEY (tenant_id, pay_period_id)
    REFERENCES organisation.pay_period(tenant_id, id)
);

CREATE TABLE employee_payroll.employee_component_override (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_assignment_version_id uuid NOT NULL,
  salary_assignment_id uuid NOT NULL,
  salary_structure_line_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  override_kind varchar(20) NOT NULL,
  override_value numeric(19,6) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_override_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (override_kind IN ('AMOUNT','PERCENTAGE')),
  CHECK (override_value >= 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK (
    (approval_status='APPROVED' AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL AND btrim(approved_by) <> '')
    OR
    (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)
  ),
  CHECK (supersedes_override_id IS NULL OR supersedes_override_id <> id),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, payroll_assignment_version_id)
    REFERENCES employee_payroll.payroll_assignment_version(tenant_id, id),
  FOREIGN KEY (tenant_id, salary_assignment_id, payroll_assignment_version_id)
    REFERENCES employee_payroll.salary_assignment(
      tenant_id, id, payroll_assignment_version_id),
  FOREIGN KEY (tenant_id, salary_structure_line_id)
    REFERENCES compensation.salary_structure_line(tenant_id, id),
  FOREIGN KEY (tenant_id, component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id, id),
  FOREIGN KEY (tenant_id, supersedes_override_id)
    REFERENCES employee_payroll.employee_component_override(tenant_id, id)
);

ALTER TABLE employee_payroll.employee_component_override
  ADD CONSTRAINT employee_component_override_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_assignment_version_id WITH =,
    salary_structure_line_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX employee_component_override_one_successor_uk
  ON employee_payroll.employee_component_override(
    tenant_id, supersedes_override_id)
  WHERE supersedes_override_id IS NOT NULL;

CREATE TABLE employee_payroll.payroll_lifecycle_lineage (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  event_type varchar(32) NOT NULL,
  relationship_decision varchar(16) NOT NULL,
  predecessor_relationship_id uuid,
  successor_relationship_id uuid,
  predecessor_assignment_id uuid,
  successor_assignment_id uuid,
  effective_date date NOT NULL,
  reason varchar(500) NOT NULL,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (event_type IN ('TRANSFER','REHIRE','CONCURRENT_ASSIGNMENT')),
  CHECK (relationship_decision IN ('CONTINUE','SUCCESSOR')),
  CHECK (btrim(reason) <> ''),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK (
    (approval_status='APPROVED' AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL AND btrim(approved_by) <> '')
    OR
    (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, predecessor_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id),
  FOREIGN KEY (tenant_id, successor_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id),
  FOREIGN KEY (tenant_id, predecessor_assignment_id)
    REFERENCES employee_payroll.payroll_assignment(tenant_id, id),
  FOREIGN KEY (tenant_id, successor_assignment_id)
    REFERENCES employee_payroll.payroll_assignment(tenant_id, id)
);

CREATE OR REPLACE FUNCTION employee_payroll.require_v050_runtime_draft_insert()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner'
     AND (NEW.approval_status <> 'DRAFT'
          OR NEW.approved_at IS NOT NULL
          OR NEW.approved_by IS NOT NULL) THEN
    RAISE EXCEPTION 'runtime employee-payroll approval aggregates must start as drafts'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

-- Keep compensation-change-only fields out of the shared draft trigger.
-- PostgreSQL resolves NEW record fields against the table that fired the
-- trigger; referencing assessment fields from a trigger shared with lifecycle
-- lineage/component-override rows therefore fails at runtime. This dedicated
-- trigger owns only the compensation-change assessment contract.
CREATE OR REPLACE FUNCTION
  employee_payroll.require_compensation_change_runtime_assessment_control()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user <> 'payroll_owner'
     AND (NEW.assessment_through IS NOT NULL
          OR NEW.impact_assessed_at IS NOT NULL
          OR NEW.impact_assessed_by IS NOT NULL) THEN
    RAISE EXCEPTION 'runtime compensation changes must be assessed through the controlled function'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER compensation_change_event_runtime_draft
  BEFORE INSERT ON employee_payroll.compensation_change_event
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.require_v050_runtime_draft_insert();
CREATE TRIGGER compensation_change_event_runtime_assessment_control
  BEFORE INSERT ON employee_payroll.compensation_change_event
  FOR EACH ROW EXECUTE FUNCTION
    employee_payroll.require_compensation_change_runtime_assessment_control();
CREATE TRIGGER employee_component_override_runtime_draft
  BEFORE INSERT ON employee_payroll.employee_component_override
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.require_v050_runtime_draft_insert();
CREATE TRIGGER payroll_lifecycle_lineage_runtime_draft
  BEFORE INSERT ON employee_payroll.payroll_lifecycle_lineage
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.require_v050_runtime_draft_insert();

-- Legacy-compatible direct SQL inserts remain schema 0 by default so V001-V049
-- test fixtures and pre-V050 integrations are not reinterpreted. The application
-- repository explicitly writes schema 1 only when the complete V050 contract is
-- present. The V050 approval functions preserve schema-0 approval semantics for
-- historical/direct-SQL compatibility while schema-1 rows require the complete
-- new contract. Salary schema-0 approval remains bounded to an exact legacy
-- schema-0 salary structure.

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_payroll_relationship_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  legal_status varchar;
  legal_from date;
  legal_to date;
  psu_status varchar;
  psu_legal uuid;
  psu_from date;
  psu_to date;
BEGIN
  SELECT approval_status, effective_from, effective_to
    INTO legal_status, legal_from, legal_to
    FROM organisation.legal_entity_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.legal_entity_version_id;
  IF legal_status IS NULL THEN
    RAISE EXCEPTION 'legal-entity version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF legal_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'payroll relationship requires an approved legal entity'
      USING ERRCODE='23514';
  END IF;
  IF NEW.relationship_start < legal_from
     OR (legal_to IS NOT NULL AND
         (NEW.relationship_end IS NULL OR NEW.relationship_end > legal_to)) THEN
    RAISE EXCEPTION 'relationship range exceeds legal-entity version'
      USING ERRCODE='23514';
  END IF;

  IF NEW.boundary_schema_version=1 THEN
    SELECT approval_status, legal_entity_version_id, effective_from, effective_to
      INTO psu_status, psu_legal, psu_from, psu_to
      FROM organisation.payroll_statutory_unit_version
     WHERE tenant_id=NEW.tenant_id
       AND id=NEW.payroll_statutory_unit_version_id;
    IF psu_status IS NULL THEN
      RAISE EXCEPTION 'payroll statutory unit version does not exist in current tenant'
        USING ERRCODE='23503';
    END IF;
    IF psu_status <> 'APPROVED' OR psu_legal <> NEW.legal_entity_version_id THEN
      RAISE EXCEPTION 'relationship PSU must be approved and belong to the exact legal entity version'
        USING ERRCODE='23514';
    END IF;
    IF NEW.relationship_start < psu_from
       OR (psu_to IS NOT NULL AND
           (NEW.relationship_end IS NULL OR NEW.relationship_end > psu_to)) THEN
      RAISE EXCEPTION 'relationship range exceeds payroll statutory unit version'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_payroll_relationship_version(
    p_tenant_id uuid, p_version_id uuid, p_actor varchar,
    p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,organisation,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor)='' THEN
    RAISE EXCEPTION 'actor is required' USING ERRCODE='23514';
  END IF;
  UPDATE employee_payroll.payroll_relationship_version v
     SET approval_status='APPROVED', approved_at=p_approved_at,
         approved_by=p_actor, updated_at=p_approved_at,
         updated_by=p_actor, version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND approval_status='DRAFT'
     AND (
       boundary_schema_version=0
       OR (
         boundary_schema_version=1
         AND payroll_statutory_unit_version_id IS NOT NULL
         AND aggregation_boundary_key IS NOT NULL
         AND btrim(aggregation_boundary_key)<>''
       )
     )
     AND NOT EXISTS (
       SELECT 1 FROM employee_payroll.payroll_relationship_version successor
        WHERE successor.tenant_id=v.tenant_id
          AND successor.supersedes_version_id=v.id);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.bind_payroll_assignment_source_ref(
    p_tenant_id uuid, p_assignment_id uuid, p_source_ref varchar,
    p_expected_version bigint, p_actor varchar, p_changed_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  IF p_source_ref IS NULL OR btrim(p_source_ref)='' THEN
    RAISE EXCEPTION 'source work assignment reference is required' USING ERRCODE='23514';
  END IF;
  UPDATE employee_payroll.payroll_assignment
     SET source_work_assignment_ref=p_source_ref,
         updated_at=p_changed_at, updated_by=p_actor, version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_assignment_id
     AND source_work_assignment_ref IS NULL
     AND version_no=p_expected_version;
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_payroll_assignment_version_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  relationship_status varchar;
  relationship_identity uuid;
  relationship_from date;
  relationship_to date;
  relationship_psu uuid;
  establishment_status varchar;
  establishment_psu uuid;
  establishment_from date;
  establishment_to date;
  identity_relationship uuid;
  source_ref varchar;
BEGIN
  SELECT approval_status,payroll_relationship_id,relationship_start,
         relationship_end,payroll_statutory_unit_version_id
    INTO relationship_status,relationship_identity,relationship_from,
         relationship_to,relationship_psu
    FROM employee_payroll.payroll_relationship_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.payroll_relationship_version_id;
  IF relationship_status IS NULL THEN
    RAISE EXCEPTION 'payroll relationship version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF relationship_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'payroll assignment requires an approved relationship version'
      USING ERRCODE='23514';
  END IF;
  SELECT payroll_relationship_id, source_work_assignment_ref
    INTO identity_relationship, source_ref
    FROM employee_payroll.payroll_assignment
   WHERE tenant_id=NEW.tenant_id AND id=NEW.payroll_assignment_id;
  IF identity_relationship IS DISTINCT FROM relationship_identity THEN
    RAISE EXCEPTION 'assignment identity and relationship version must share the relationship identity'
      USING ERRCODE='23514';
  END IF;
  IF NEW.binding_schema_version=1
     AND (source_ref IS NULL OR btrim(source_ref)='') THEN
    RAISE EXCEPTION 'complete payroll assignment requires source work assignment reference'
      USING ERRCODE='23514';
  END IF;
  IF NEW.assignment_start < relationship_from
     OR (relationship_to IS NOT NULL AND
         (NEW.assignment_end IS NULL OR NEW.assignment_end > relationship_to)) THEN
    RAISE EXCEPTION 'assignment range exceeds relationship version'
      USING ERRCODE='23514';
  END IF;
  SELECT approval_status,payroll_statutory_unit_version_id,effective_from,effective_to
    INTO establishment_status,establishment_psu,establishment_from,establishment_to
    FROM organisation.establishment_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.establishment_version_id;
  IF establishment_status IS NULL THEN
    RAISE EXCEPTION 'establishment version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF establishment_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'payroll assignment requires an approved establishment version'
      USING ERRCODE='23514';
  END IF;
  IF relationship_psu IS NOT NULL AND establishment_psu <> relationship_psu THEN
    RAISE EXCEPTION 'assignment establishment PSU must match payroll relationship PSU'
      USING ERRCODE='23514';
  END IF;
  IF NEW.assignment_start < establishment_from
     OR (establishment_to IS NOT NULL AND
         (NEW.assignment_end IS NULL OR NEW.assignment_end > establishment_to)) THEN
    RAISE EXCEPTION 'assignment range exceeds establishment version'
      USING ERRCODE='23514';
  END IF;
  IF NEW.binding_schema_version=1 THEN
    IF NEW.payroll_eligibility_from < NEW.assignment_start
       OR (NEW.assignment_end IS NOT NULL AND
           (NEW.payroll_eligibility_to IS NULL
            OR NEW.payroll_eligibility_to > NEW.assignment_end)) THEN
      RAISE EXCEPTION 'payroll eligibility range must be contained by assignment range'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_payroll_assignment_version(
    p_tenant_id uuid, p_version_id uuid, p_actor varchar,
    p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.payroll_assignment_version v
     SET approval_status='APPROVED', approved_at=p_approved_at,
         approved_by=p_actor, updated_at=p_approved_at,
         updated_by=p_actor, version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_version_id
     AND approval_status='DRAFT'
     AND (
       binding_schema_version=0
       OR (
         binding_schema_version=1
         AND payroll_role IS NOT NULL
         AND payroll_eligibility_from IS NOT NULL
         AND EXISTS (
           SELECT 1 FROM employee_payroll.payroll_assignment i
            WHERE i.tenant_id=v.tenant_id AND i.id=v.payroll_assignment_id
              AND i.source_work_assignment_ref IS NOT NULL
              AND btrim(i.source_work_assignment_ref)<>''
         )
       )
     )
     AND NOT EXISTS (
       SELECT 1 FROM employee_payroll.payroll_assignment_version successor
        WHERE successor.tenant_id=v.tenant_id
          AND successor.supersedes_version_id=v.id)
     AND NOT (
       v.binding_schema_version=1
       AND v.payroll_role='PRIMARY'
       AND EXISTS (
         SELECT 1
           FROM employee_payroll.payroll_assignment_version other
           JOIN employee_payroll.payroll_assignment oi
             ON oi.tenant_id=other.tenant_id
            AND oi.id=other.payroll_assignment_id
           JOIN employee_payroll.payroll_assignment vi
             ON vi.tenant_id=v.tenant_id AND vi.id=v.payroll_assignment_id
          WHERE other.tenant_id=v.tenant_id
            AND oi.payroll_relationship_id=vi.payroll_relationship_id
            AND other.id<>v.id
            AND other.approval_status='APPROVED'
            AND other.binding_schema_version=1
            AND other.payroll_role='PRIMARY'
            AND daterange(other.payroll_eligibility_from,
                          other.payroll_eligibility_to,'[)')
                && daterange(v.payroll_eligibility_from,
                             v.payroll_eligibility_to,'[)')));
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION
  employee_payroll.assert_pay_group_assignment_dependencies()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  assignment_status varchar;
  assignment_from date;
  assignment_to date;
  v_establishment_id uuid;
  v_relationship_version_id uuid;
  relationship_psu uuid;
  establishment_psu uuid;
  group_status varchar;
  group_psu uuid;
  group_from date;
  group_to date;
BEGIN
  SELECT approval_status,assignment_start,assignment_end,
         establishment_version_id,payroll_relationship_version_id
    INTO assignment_status,assignment_from,assignment_to,
         v_establishment_id,v_relationship_version_id
    FROM employee_payroll.payroll_assignment_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.payroll_assignment_version_id;
  IF assignment_status IS NULL THEN
    RAISE EXCEPTION 'payroll assignment version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF assignment_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'pay-group assignment requires approved payroll assignment'
      USING ERRCODE='23514';
  END IF;
  SELECT payroll_statutory_unit_version_id INTO relationship_psu
    FROM employee_payroll.payroll_relationship_version
   WHERE tenant_id=NEW.tenant_id AND id=v_relationship_version_id;
  SELECT payroll_statutory_unit_version_id INTO establishment_psu
    FROM organisation.establishment_version
   WHERE tenant_id=NEW.tenant_id AND id=v_establishment_id;
  SELECT approval_status,payroll_statutory_unit_version_id,effective_from,effective_to
    INTO group_status,group_psu,group_from,group_to
    FROM organisation.pay_group_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.pay_group_version_id;
  IF group_status IS NULL THEN
    RAISE EXCEPTION 'pay-group version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF group_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'pay-group assignment requires approved pay group'
      USING ERRCODE='23514';
  END IF;
  -- Preserve the V021/V038 write invariant for every assignment, including
  -- legacy/schema-0 rows: the explicit pay group must belong to the same PSU
  -- as the payroll assignment's establishment.
  IF group_psu IS DISTINCT FROM establishment_psu THEN
    RAISE EXCEPTION 'pay-group PSU must match payroll assignment establishment PSU'
      USING ERRCODE='23514';
  END IF;

  -- V050 schema-1 adds the exact relationship PSU boundary on top of the
  -- inherited establishment/pay-group compatibility contract. Legacy rows do
  -- not manufacture a relationship PSU, but they must still satisfy V021/V038.
  IF NEW.contract_schema_version=1
     AND (relationship_psu IS NULL
          OR group_psu IS DISTINCT FROM relationship_psu
          OR establishment_psu IS DISTINCT FROM relationship_psu) THEN
    RAISE EXCEPTION 'relationship, establishment and pay-group PSU must match'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < assignment_from
     OR (assignment_to IS NOT NULL AND
         (NEW.effective_to IS NULL OR NEW.effective_to > assignment_to))
     OR NEW.effective_from < group_from
     OR (group_to IS NOT NULL AND
         (NEW.effective_to IS NULL OR NEW.effective_to > group_to)) THEN
    RAISE EXCEPTION 'pay-group assignment effective range exceeds its dependencies'
      USING ERRCODE='23514';
  END IF;
  IF NEW.contract_schema_version=1
     AND NEW.impact_assessment_through < NEW.effective_from THEN
    RAISE EXCEPTION 'pay-group impact assessment cannot end before effective date'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.populate_pay_group_assignment_impact()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,organisation AS $$
BEGIN
  IF NEW.contract_schema_version=1 THEN
    INSERT INTO employee_payroll.pay_group_assignment_impact_period(
      tenant_id,pay_group_assignment_id,pay_period_id,created_by)
    SELECT NEW.tenant_id,NEW.id,period.id,NEW.created_by
      FROM organisation.pay_group_version group_version
      JOIN organisation.pay_period period
        ON period.tenant_id=group_version.tenant_id
       AND period.calendar_id=group_version.calendar_id
     WHERE group_version.tenant_id=NEW.tenant_id
       AND group_version.id=NEW.pay_group_version_id
       AND period.period_end >= NEW.effective_from
       AND period.period_start <= NEW.impact_assessment_through
    ON CONFLICT DO NOTHING;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER pay_group_assignment_impact_population
  AFTER INSERT ON employee_payroll.pay_group_assignment
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.populate_pay_group_assignment_impact();

CREATE OR REPLACE FUNCTION
  employee_payroll.approve_pay_group_assignment(
    p_tenant_id uuid,p_assignment_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.pay_group_assignment a
     SET approval_status='APPROVED',approved_at=p_approved_at,
         approved_by=p_actor,updated_at=p_approved_at,
         updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_assignment_id
     AND approval_status='DRAFT'
     AND (
       contract_schema_version=0
       OR (
         contract_schema_version=1
         AND impact_assessment_through IS NOT NULL
         AND impact_assessed_at IS NOT NULL
         AND impact_assessed_by IS NOT NULL
       )
     )
     AND NOT EXISTS (
       SELECT 1 FROM employee_payroll.pay_group_assignment successor
        WHERE successor.tenant_id=a.tenant_id
          AND successor.supersedes_assignment_id=a.id);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.assert_compensation_change_event()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source_type varchar; source_status varchar; source_assignment uuid;
BEGIN
  IF NEW.source_event_id IS NOT NULL THEN
    SELECT event_type,approval_status,payroll_assignment_id
      INTO source_type,source_status,source_assignment
      FROM employee_payroll.compensation_change_event
     WHERE tenant_id=NEW.tenant_id AND id=NEW.source_event_id;
    IF source_type IS NULL THEN
      RAISE EXCEPTION 'source compensation event does not exist in current tenant'
        USING ERRCODE='23503';
    END IF;
    IF NEW.source_event_id=NEW.id THEN
      RAISE EXCEPTION 'compensation event cannot reference itself'
        USING ERRCODE='23514';
    END IF;
    IF source_status <> 'APPROVED' THEN
      RAISE EXCEPTION 'correction/reversal source event must already be approved'
        USING ERRCODE='23514';
    END IF;
    IF source_assignment <> NEW.payroll_assignment_id THEN
      RAISE EXCEPTION 'correction/reversal source event must belong to the same payroll assignment'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER compensation_change_event_dependencies
  BEFORE INSERT ON employee_payroll.compensation_change_event
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.assert_compensation_change_event();

CREATE OR REPLACE FUNCTION employee_payroll.assess_compensation_change(
  p_tenant_id uuid,p_event_id uuid,p_assessment_through date,
  p_actor varchar,p_assessed_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,organisation,platform AS $$
DECLARE event_assignment uuid; event_date date; affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  SELECT payroll_assignment_id,effective_date
    INTO event_assignment,event_date
    FROM employee_payroll.compensation_change_event
   WHERE tenant_id=p_tenant_id AND id=p_event_id
     AND approval_status='DRAFT' AND assessment_through IS NULL;
  IF event_assignment IS NULL THEN RETURN 0; END IF;
  IF p_assessment_through < event_date THEN
    RAISE EXCEPTION 'assessmentThrough cannot precede compensation effective date'
      USING ERRCODE='23514';
  END IF;
  INSERT INTO employee_payroll.compensation_change_impact(
    tenant_id,compensation_change_event_id,pay_period_id,reason_code,created_by)
  SELECT DISTINCT p_tenant_id,p_event_id,period.id,'EFFECTIVE_DATE_OVERLAP',p_actor
    FROM employee_payroll.payroll_assignment_version av
    JOIN employee_payroll.pay_group_assignment ga
      ON ga.tenant_id=av.tenant_id
     AND ga.payroll_assignment_version_id=av.id
     AND ga.approval_status='APPROVED'
    JOIN organisation.pay_group_version gv
      ON gv.tenant_id=ga.tenant_id AND gv.id=ga.pay_group_version_id
    JOIN organisation.pay_period period
      ON period.tenant_id=gv.tenant_id AND period.calendar_id=gv.calendar_id
   WHERE av.tenant_id=p_tenant_id
     AND av.payroll_assignment_id=event_assignment
     AND av.approval_status='APPROVED'
     AND period.period_end >= event_date
     AND period.period_start <= p_assessment_through
     AND daterange(period.period_start,period.period_end + 1,'[)')
         && daterange(ga.effective_from,ga.effective_to,'[)')
  ON CONFLICT DO NOTHING;
  UPDATE employee_payroll.compensation_change_event
     SET assessment_through=p_assessment_through,
         impact_assessed_at=p_assessed_at,impact_assessed_by=p_actor,
         updated_at=p_assessed_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_event_id AND approval_status='DRAFT';
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.approve_compensation_change_event(
  p_tenant_id uuid,p_event_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.compensation_change_event
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_event_id AND approval_status='DRAFT'
     AND assessment_through IS NOT NULL AND impact_assessed_at IS NOT NULL;
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.assert_salary_assignment_dependencies()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  assignment_status varchar; assignment_from date; assignment_to date;
  structure_status varchar; structure_from date; structure_to date;
  structure_currency varchar; structure_target_type varchar;
  structure_target_frequency varchar; structure_pay_frequency varchar;
  event_status varchar; event_assignment uuid;
  stable_assignment uuid; group_calendar uuid; calendar_frequency varchar;
BEGIN
  SELECT approval_status,assignment_start,assignment_end,payroll_assignment_id
    INTO assignment_status,assignment_from,assignment_to,stable_assignment
    FROM employee_payroll.payroll_assignment_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.payroll_assignment_version_id;
  IF assignment_status IS NULL THEN
    RAISE EXCEPTION 'payroll assignment version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF assignment_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'salary assignment requires approved payroll assignment'
      USING ERRCODE='23514';
  END IF;
  SELECT approval_status,effective_from,effective_to,currency,target_type,
         target_frequency,pay_frequency
    INTO structure_status,structure_from,structure_to,structure_currency,
         structure_target_type,structure_target_frequency,structure_pay_frequency
    FROM compensation.salary_structure_version
   WHERE tenant_id=NEW.tenant_id AND id=NEW.salary_structure_version_id;
  IF structure_status IS NULL THEN
    RAISE EXCEPTION 'salary-structure version does not exist in current tenant'
      USING ERRCODE='23503';
  END IF;
  IF structure_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'salary assignment requires approved salary structure'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < assignment_from
     OR (assignment_to IS NOT NULL AND
         (NEW.effective_to IS NULL OR NEW.effective_to > assignment_to))
     OR NEW.effective_from < structure_from
     OR (structure_to IS NOT NULL AND
         (NEW.effective_to IS NULL OR NEW.effective_to > structure_to)) THEN
    RAISE EXCEPTION 'salary assignment range exceeds assignment or structure range'
      USING ERRCODE='23514';
  END IF;
  IF NEW.contract_schema_version=0 THEN
    IF NEW.currency <> 'INR' OR NEW.currency <> structure_currency THEN
      RAISE EXCEPTION 'legacy salary-assignment draft requires historical INR compatibility'
        USING ERRCODE='23514';
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.currency <> structure_currency
     OR NEW.target_type <> structure_target_type
     OR NEW.target_frequency <> structure_target_frequency THEN
    RAISE EXCEPTION 'employee target must match approved salary-structure target contract'
      USING ERRCODE='23514';
  END IF;
  SELECT approval_status,payroll_assignment_id
    INTO event_status,event_assignment
    FROM employee_payroll.compensation_change_event
   WHERE tenant_id=NEW.tenant_id AND id=NEW.source_compensation_event_id;
  IF event_status <> 'APPROVED' OR event_assignment <> stable_assignment THEN
    RAISE EXCEPTION 'salary assignment requires approved compensation event for same assignment identity'
      USING ERRCODE='23514';
  END IF;
  SELECT gv.calendar_id INTO group_calendar
    FROM employee_payroll.pay_group_assignment ga
    JOIN organisation.pay_group_version gv
      ON gv.tenant_id=ga.tenant_id AND gv.id=ga.pay_group_version_id
   WHERE ga.tenant_id=NEW.tenant_id
     AND ga.payroll_assignment_version_id=NEW.payroll_assignment_version_id
     AND ga.approval_status='APPROVED'
     AND daterange(ga.effective_from,ga.effective_to,'[)')
         @> NEW.effective_from
   ORDER BY ga.effective_from DESC LIMIT 1;
  IF group_calendar IS NULL THEN
    RAISE EXCEPTION 'salary assignment requires an active approved regular pay group'
      USING ERRCODE='23514';
  END IF;
  SELECT frequency INTO calendar_frequency
    FROM organisation.payroll_calendar
   WHERE tenant_id=NEW.tenant_id AND id=group_calendar;
  IF NOT (
    (structure_pay_frequency='MONTHLY' AND calendar_frequency='MONTHLY') OR
    (structure_pay_frequency='WEEKLY' AND calendar_frequency='WEEKLY') OR
    (structure_pay_frequency='BIWEEKLY' AND calendar_frequency='FORTNIGHTLY')
  ) THEN
    RAISE EXCEPTION 'salary-structure pay frequency is incompatible with active pay group calendar'
      USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.approve_salary_assignment(
  p_tenant_id uuid,p_assignment_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.salary_assignment a
     SET approval_status='APPROVED',approved_at=p_approved_at,
         approved_by=p_actor,updated_at=p_approved_at,
         updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_assignment_id
     AND approval_status='DRAFT'
     AND (
       (contract_schema_version=1
        AND target_type IS NOT NULL AND target_value IS NOT NULL
        AND target_frequency IS NOT NULL AND source_compensation_event_id IS NOT NULL)
       OR
       (contract_schema_version=0 AND EXISTS (
          SELECT 1 FROM compensation.salary_structure_version legacy_structure
           WHERE legacy_structure.tenant_id=a.tenant_id
             AND legacy_structure.id=a.salary_structure_version_id
             AND legacy_structure.structure_schema_version=0))
     )
     AND NOT EXISTS (
       SELECT 1 FROM employee_payroll.salary_assignment successor
        WHERE successor.tenant_id=a.tenant_id
          AND successor.supersedes_assignment_id=a.id);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.assert_employee_component_override()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  salary_status varchar; salary_structure_version uuid;
  salary_from date; salary_to date;
  line_component_version uuid; line_type varchar; override_policy varchar;
  minimum_amount numeric; maximum_amount numeric;
  component_status varchar; component_lifecycle varchar;
  predecessor_assignment_version uuid; predecessor_salary_assignment uuid;
  predecessor_line uuid; predecessor_component_version uuid;
  predecessor_status varchar;
BEGIN
  SELECT approval_status,salary_structure_version_id,effective_from,effective_to
    INTO salary_status,salary_structure_version,salary_from,salary_to
    FROM employee_payroll.salary_assignment
   WHERE tenant_id=NEW.tenant_id AND id=NEW.salary_assignment_id
     AND payroll_assignment_version_id=NEW.payroll_assignment_version_id;
  IF salary_status <> 'APPROVED' THEN
    RAISE EXCEPTION 'employee override requires approved salary assignment'
      USING ERRCODE='23514';
  END IF;
  IF NEW.supersedes_override_id IS NOT NULL THEN
    SELECT payroll_assignment_version_id,salary_assignment_id,
           salary_structure_line_id,component_version_id,approval_status
      INTO predecessor_assignment_version,predecessor_salary_assignment,
           predecessor_line,predecessor_component_version,predecessor_status
      FROM employee_payroll.employee_component_override
     WHERE tenant_id=NEW.tenant_id AND id=NEW.supersedes_override_id;
    IF predecessor_assignment_version IS NULL THEN
      RAISE EXCEPTION 'superseded employee override does not exist in current tenant'
        USING ERRCODE='23503';
    END IF;
    IF predecessor_status <> 'DRAFT' THEN
      RAISE EXCEPTION 'employee override correction requires a draft predecessor'
        USING ERRCODE='23514';
    END IF;
    IF predecessor_assignment_version <> NEW.payroll_assignment_version_id
       OR predecessor_salary_assignment <> NEW.salary_assignment_id
       OR predecessor_line <> NEW.salary_structure_line_id
       OR predecessor_component_version <> NEW.component_version_id THEN
      RAISE EXCEPTION 'employee override successor must retain assignment, salary, line and component lineage'
        USING ERRCODE='23514';
    END IF;
  END IF;
  SELECT component_version_id,line_type,override_policy,minimum_amount,maximum_amount
    INTO line_component_version,line_type,override_policy,minimum_amount,maximum_amount
    FROM compensation.salary_structure_line
   WHERE tenant_id=NEW.tenant_id AND id=NEW.salary_structure_line_id
     AND salary_structure_version_id=salary_structure_version;
  IF line_component_version IS NULL THEN
    RAISE EXCEPTION 'salary-structure line is not part of assigned structure version'
      USING ERRCODE='23503';
  END IF;
  IF line_component_version <> NEW.component_version_id THEN
    RAISE EXCEPTION 'override component version must match exact salary-structure line'
      USING ERRCODE='23514';
  END IF;
  IF override_policy IS NULL OR override_policy NOT IN ('CONTROLLED','ALLOWED') THEN
    RAISE EXCEPTION 'employee override requires an E04 line with CONTROLLED or ALLOWED policy'
      USING ERRCODE='23514';
  END IF;
  SELECT v.approval_status,p.lifecycle_status
    INTO component_status,component_lifecycle
    FROM compensation.pay_component_version v
    JOIN compensation.pay_component p
      ON p.tenant_id=v.tenant_id AND p.id=v.component_id
   WHERE v.tenant_id=NEW.tenant_id AND v.id=NEW.component_version_id;
  IF component_status <> 'APPROVED' OR component_lifecycle <> 'ACTIVE' THEN
    RAISE EXCEPTION 'employee override requires active approved component version'
      USING ERRCODE='23514';
  END IF;
  IF NEW.effective_from < salary_from
     OR (salary_to IS NOT NULL AND
         (NEW.effective_to IS NULL OR NEW.effective_to > salary_to)) THEN
    RAISE EXCEPTION 'employee override range exceeds salary assignment'
      USING ERRCODE='23514';
  END IF;
  IF NEW.override_kind='AMOUNT' THEN
    IF line_type='PERCENTAGE' THEN
      RAISE EXCEPTION 'percentage line requires percentage override'
        USING ERRCODE='23514';
    END IF;
    IF minimum_amount IS NOT NULL AND NEW.override_value < minimum_amount THEN
      RAISE EXCEPTION 'employee override is below approved minimum amount'
        USING ERRCODE='23514';
    END IF;
    IF maximum_amount IS NOT NULL AND NEW.override_value > maximum_amount THEN
      RAISE EXCEPTION 'employee override exceeds approved maximum amount'
        USING ERRCODE='23514';
    END IF;
  ELSE
    IF line_type <> 'PERCENTAGE' OR NEW.override_value <= 0 OR NEW.override_value > 100 THEN
      RAISE EXCEPTION 'percentage override requires PERCENTAGE line and value in (0,100]'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER employee_component_override_dependencies
  BEFORE INSERT ON employee_payroll.employee_component_override
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.assert_employee_component_override();

CREATE OR REPLACE FUNCTION employee_payroll.approve_employee_component_override(
  p_tenant_id uuid,p_override_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.employee_component_override o
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_override_id
     AND approval_status='DRAFT'
     AND NOT EXISTS (
       SELECT 1 FROM employee_payroll.employee_component_override successor
        WHERE successor.tenant_id=o.tenant_id
          AND successor.supersedes_override_id=o.id);
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.assert_payroll_lifecycle_lineage()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE predecessor_assignment_relationship uuid;
        successor_assignment_relationship uuid;
BEGIN
  IF NEW.predecessor_assignment_id IS NOT NULL THEN
    SELECT payroll_relationship_id INTO predecessor_assignment_relationship
      FROM employee_payroll.payroll_assignment
     WHERE tenant_id=NEW.tenant_id AND id=NEW.predecessor_assignment_id;
    IF predecessor_assignment_relationship IS NULL THEN
      RAISE EXCEPTION 'predecessor payroll assignment does not exist in current tenant'
        USING ERRCODE='23503';
    END IF;
  END IF;
  IF NEW.successor_assignment_id IS NOT NULL THEN
    SELECT payroll_relationship_id INTO successor_assignment_relationship
      FROM employee_payroll.payroll_assignment
     WHERE tenant_id=NEW.tenant_id AND id=NEW.successor_assignment_id;
    IF successor_assignment_relationship IS NULL THEN
      RAISE EXCEPTION 'successor payroll assignment does not exist in current tenant'
        USING ERRCODE='23503';
    END IF;
  END IF;

  IF NEW.event_type IN ('TRANSFER','REHIRE') THEN
    IF NEW.predecessor_relationship_id IS NULL OR NEW.successor_relationship_id IS NULL THEN
      RAISE EXCEPTION 'transfer/rehire requires predecessor and successor relationships'
        USING ERRCODE='23514';
    END IF;
    IF NEW.relationship_decision='CONTINUE'
       AND NEW.predecessor_relationship_id <> NEW.successor_relationship_id THEN
      RAISE EXCEPTION 'CONTINUE decision must retain relationship identity'
        USING ERRCODE='23514';
    END IF;
    IF NEW.relationship_decision='SUCCESSOR'
       AND NEW.predecessor_relationship_id = NEW.successor_relationship_id THEN
      RAISE EXCEPTION 'SUCCESSOR decision requires distinct relationship identity'
        USING ERRCODE='23514';
    END IF;
    IF predecessor_assignment_relationship IS NOT NULL
       AND predecessor_assignment_relationship <> NEW.predecessor_relationship_id THEN
      RAISE EXCEPTION 'predecessor assignment must belong to predecessor relationship'
        USING ERRCODE='23514';
    END IF;
    IF successor_assignment_relationship IS NOT NULL
       AND successor_assignment_relationship <> NEW.successor_relationship_id THEN
      RAISE EXCEPTION 'successor assignment must belong to successor relationship'
        USING ERRCODE='23514';
    END IF;
  ELSE
    IF NEW.relationship_decision <> 'CONTINUE'
       OR NEW.predecessor_relationship_id IS NULL
       OR NEW.successor_relationship_id IS NULL
       OR NEW.predecessor_relationship_id <> NEW.successor_relationship_id
       OR NEW.predecessor_assignment_id IS NULL
       OR NEW.successor_assignment_id IS NULL
       OR NEW.predecessor_assignment_id=NEW.successor_assignment_id THEN
      RAISE EXCEPTION 'concurrent assignment requires one continuing relationship and distinct assignment identities'
        USING ERRCODE='23514';
    END IF;
    IF predecessor_assignment_relationship <> NEW.predecessor_relationship_id
       OR successor_assignment_relationship <> NEW.successor_relationship_id THEN
      RAISE EXCEPTION 'concurrent assignments must belong to the continuing relationship'
        USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_lifecycle_lineage_dependencies
  BEFORE INSERT ON employee_payroll.payroll_lifecycle_lineage
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.assert_payroll_lifecycle_lineage();

CREATE OR REPLACE FUNCTION employee_payroll.approve_payroll_lifecycle_lineage(
  p_tenant_id uuid,p_lineage_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,employee_payroll,platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE='42501';
  END IF;
  UPDATE employee_payroll.payroll_lifecycle_lineage
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE tenant_id=p_tenant_id AND id=p_lineage_id AND approval_status='DRAFT';
  GET DIAGNOSTICS affected=ROW_COUNT;
  RETURN affected;
END $$;

DO $$
DECLARE table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'pay_group_assignment_impact_period',
    'compensation_change_event',
    'compensation_change_impact',
    'employee_component_override',
    'payroll_lifecycle_lineage'
  ] LOOP
    EXECUTE format('ALTER TABLE employee_payroll.%I ENABLE ROW LEVEL SECURITY',table_name);
    EXECUTE format('ALTER TABLE employee_payroll.%I FORCE ROW LEVEL SECURITY',table_name);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON employee_payroll.%I USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id())',
      table_name);
  END LOOP;
END $$;

CREATE TRIGGER compensation_change_event_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.compensation_change_event
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.reject_uncontrolled_configuration_mutation();
CREATE TRIGGER compensation_change_impact_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.compensation_change_impact
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.reject_uncontrolled_configuration_mutation();
CREATE TRIGGER employee_component_override_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.employee_component_override
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.reject_uncontrolled_configuration_mutation();
CREATE TRIGGER payroll_lifecycle_lineage_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.payroll_lifecycle_lineage
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.reject_uncontrolled_configuration_mutation();
CREATE TRIGGER pay_group_assignment_impact_immutable
  BEFORE UPDATE OR DELETE ON employee_payroll.pay_group_assignment_impact_period
  FOR EACH ROW EXECUTE FUNCTION employee_payroll.reject_uncontrolled_configuration_mutation();

GRANT SELECT ON
  employee_payroll.pay_group_assignment_impact_period,
  employee_payroll.compensation_change_impact TO payroll_app;
REVOKE INSERT, UPDATE, DELETE ON
  employee_payroll.pay_group_assignment_impact_period,
  employee_payroll.compensation_change_impact FROM payroll_app;
GRANT SELECT, INSERT ON
  employee_payroll.compensation_change_event,
  employee_payroll.employee_component_override,
  employee_payroll.payroll_lifecycle_lineage TO payroll_app;
REVOKE UPDATE, DELETE ON
  employee_payroll.compensation_change_event,
  employee_payroll.employee_component_override,
  employee_payroll.payroll_lifecycle_lineage FROM payroll_app;

REVOKE ALL ON FUNCTION employee_payroll.bind_payroll_assignment_source_ref(
  uuid,uuid,varchar,bigint,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION employee_payroll.bind_payroll_assignment_source_ref(
  uuid,uuid,varchar,bigint,varchar,timestamptz) TO payroll_app;
REVOKE ALL ON FUNCTION employee_payroll.assess_compensation_change(
  uuid,uuid,date,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION employee_payroll.assess_compensation_change(
  uuid,uuid,date,varchar,timestamptz) TO payroll_app;
REVOKE ALL ON FUNCTION employee_payroll.approve_compensation_change_event(
  uuid,uuid,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION employee_payroll.approve_compensation_change_event(
  uuid,uuid,varchar,timestamptz) TO payroll_app;
REVOKE ALL ON FUNCTION employee_payroll.approve_employee_component_override(
  uuid,uuid,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION employee_payroll.approve_employee_component_override(
  uuid,uuid,varchar,timestamptz) TO payroll_app;
REVOKE ALL ON FUNCTION employee_payroll.approve_payroll_lifecycle_lineage(
  uuid,uuid,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION employee_payroll.approve_payroll_lifecycle_lineage(
  uuid,uuid,varchar,timestamptz) TO payroll_app;

COMMENT ON TABLE employee_payroll.compensation_change_impact IS
  'Design-time affected-period evidence only; never payroll result, balance, payment or accounting execution.';
COMMENT ON COLUMN employee_payroll.salary_assignment.monthly_amount IS
  'Legacy V006/V021 compatibility only. V050 authoritative writes use target_* and source_compensation_event_id.';
