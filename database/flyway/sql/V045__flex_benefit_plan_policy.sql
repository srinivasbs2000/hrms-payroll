-- P5-SSC-01 G02F flexible-benefit plan and election-policy configuration.
-- Forward-only from V044. V001-V044 remain immutable.
-- No employee election persistence and no official payroll/tax calculation.

CREATE TABLE compensation.flex_benefit_plan (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(40) NOT NULL,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (lifecycle_status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no>=0),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE compensation.flex_benefit_plan_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  flex_benefit_plan_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  currency char(3) NOT NULL DEFAULT 'INR',
  supplemental_plan_id uuid NOT NULL,
  supplemental_plan_version_id uuid NOT NULL,
  eligibility_rule_version_id uuid,
  annual_basket_amount numeric(19,4) NOT NULL,
  election_window_start date NOT NULL,
  election_window_end date NOT NULL,
  mid_year_joining_rule varchar(30) NOT NULL,
  joining_election_window_days integer,
  mid_year_change_rule varchar(30) NOT NULL,
  unused_balance_rule varchar(30) NOT NULL,
  carry_forward_limit numeric(19,4),
  taxable_fallback_component_version_id uuid,
  encashment_component_version_id uuid,
  final_settlement_rule varchar(30) NOT NULL,
  retro_correction_rule varchar(30) NOT NULL,
  allow_total_compensation_change boolean NOT NULL DEFAULT false,
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
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,id,flex_benefit_plan_id),
  UNIQUE (tenant_id,flex_benefit_plan_id,version_sequence),
  CHECK (version_sequence>0),
  CHECK (btrim(name)<>''),
  CHECK (currency='INR'),
  CHECK (annual_basket_amount>0),
  CHECK (election_window_end>election_window_start),
  CHECK (election_window_start>=effective_from),
  CHECK (effective_to IS NULL OR election_window_end<=effective_to),
  CHECK (effective_to IS NULL OR effective_to>effective_from),
  CHECK (mid_year_joining_rule IN ('OPEN_SPECIAL_WINDOW','DEFAULT_ELECTION','NEXT_WINDOW','APPROVAL_REQUIRED')),
  CHECK ((mid_year_joining_rule='OPEN_SPECIAL_WINDOW' AND joining_election_window_days BETWEEN 1 AND 365)
      OR (mid_year_joining_rule<>'OPEN_SPECIAL_WINDOW' AND joining_election_window_days IS NULL)),
  CHECK (mid_year_change_rule IN ('PROHIBITED','QUALIFYING_EVENT_ONLY','APPROVAL_REQUIRED')),
  CHECK (unused_balance_rule IN ('CARRY_FORWARD','TAXABLE_FALLBACK','ENCASH','FORFEIT')),
  CHECK ((unused_balance_rule='CARRY_FORWARD' AND carry_forward_limit IS NOT NULL
          AND carry_forward_limit>=annual_basket_amount)
      OR (unused_balance_rule<>'CARRY_FORWARD' AND carry_forward_limit IS NULL)),
  CHECK (unused_balance_rule<>'TAXABLE_FALLBACK' OR taxable_fallback_component_version_id IS NOT NULL),
  CHECK (unused_balance_rule<>'ENCASH' OR encashment_component_version_id IS NOT NULL),
  CHECK (final_settlement_rule IN ('ENCASH','TAXABLE_FALLBACK','FORFEIT','POLICY_ENGINE')),
  CHECK (final_settlement_rule<>'TAXABLE_FALLBACK' OR taxable_fallback_component_version_id IS NOT NULL),
  CHECK (final_settlement_rule<>'ENCASH' OR encashment_component_version_id IS NOT NULL),
  CHECK (retro_correction_rule IN ('PROHIBITED','OPEN_PERIOD_ONLY','APPROVAL_REQUIRED')),
  CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
  CHECK ((approval_status='APPROVED' AND approved_at IS NOT NULL AND approved_by IS NOT NULL AND btrim(approved_by)<>'')
      OR (approval_status<>'APPROVED' AND approved_at IS NULL AND approved_by IS NULL)),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id<>id),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no>=0),
  FOREIGN KEY (tenant_id,flex_benefit_plan_id)
    REFERENCES compensation.flex_benefit_plan(tenant_id,id),
  CONSTRAINT flex_benefit_plan_version_supplemental_fk
    FOREIGN KEY (tenant_id,supplemental_plan_version_id,supplemental_plan_id)
    REFERENCES compensation.salary_supplemental_plan_version(tenant_id,id,supplemental_plan_id),
  CONSTRAINT flex_benefit_plan_version_eligibility_fk
    FOREIGN KEY (tenant_id,eligibility_rule_version_id)
    REFERENCES compensation.eligibility_rule_version(tenant_id,id),
  CONSTRAINT flex_benefit_plan_version_taxable_fallback_fk
    FOREIGN KEY (tenant_id,taxable_fallback_component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id,id),
  CONSTRAINT flex_benefit_plan_version_encashment_fk
    FOREIGN KEY (tenant_id,encashment_component_version_id)
    REFERENCES compensation.pay_component_version(tenant_id,id),
  CONSTRAINT flex_benefit_plan_version_supersedes_fk
    FOREIGN KEY (tenant_id,supersedes_version_id,flex_benefit_plan_id)
    REFERENCES compensation.flex_benefit_plan_version(tenant_id,id,flex_benefit_plan_id)
);

ALTER TABLE compensation.flex_benefit_plan_version
  ADD CONSTRAINT flex_benefit_plan_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    flex_benefit_plan_id WITH =,
    daterange(effective_from,effective_to,'[)') WITH &&
  ) WHERE (approval_status='APPROVED');

CREATE UNIQUE INDEX flex_benefit_plan_one_successor_uk
  ON compensation.flex_benefit_plan_version(tenant_id,supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;
CREATE INDEX flex_benefit_plan_current_ix
  ON compensation.flex_benefit_plan_version(tenant_id,flex_benefit_plan_id,effective_from DESC);

CREATE TABLE compensation.flex_benefit_option (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  flex_benefit_plan_id uuid NOT NULL,
  flex_benefit_plan_version_id uuid NOT NULL,
  component_id uuid NOT NULL,
  component_version_id uuid NOT NULL,
  option_sequence integer NOT NULL,
  minimum_annual_amount numeric(19,4) NOT NULL DEFAULT 0,
  maximum_annual_amount numeric(19,4),
  default_annual_amount numeric(19,4) NOT NULL DEFAULT 0,
  proof_required boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,flex_benefit_plan_version_id,option_sequence),
  UNIQUE (tenant_id,flex_benefit_plan_version_id,component_version_id),
  CHECK (option_sequence>0),
  CHECK (minimum_annual_amount>=0),
  CHECK (maximum_annual_amount IS NULL OR maximum_annual_amount>=0),
  CHECK (maximum_annual_amount IS NULL OR maximum_annual_amount>=minimum_annual_amount),
  CHECK (default_annual_amount>=minimum_annual_amount),
  CHECK (maximum_annual_amount IS NULL OR default_annual_amount<=maximum_annual_amount),
  CHECK (length(btrim(created_by)) BETWEEN 1 AND 160),
  CHECK (length(btrim(updated_by)) BETWEEN 1 AND 160),
  CHECK (version_no>=0),
  CONSTRAINT flex_benefit_option_plan_version_fk
    FOREIGN KEY (tenant_id,flex_benefit_plan_version_id,flex_benefit_plan_id)
    REFERENCES compensation.flex_benefit_plan_version(tenant_id,id,flex_benefit_plan_id),
  CONSTRAINT flex_benefit_option_component_version_fk
    FOREIGN KEY (tenant_id,component_version_id,component_id)
    REFERENCES compensation.pay_component_version(tenant_id,id,component_id)
);
CREATE INDEX flex_benefit_option_component_ix
  ON compensation.flex_benefit_option(tenant_id,component_id,component_version_id);

CREATE OR REPLACE FUNCTION compensation.require_flex_benefit_runtime_defaults()
RETURNS trigger LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
BEGIN
  IF current_user <> 'payroll_owner' THEN
    IF TG_TABLE_NAME='flex_benefit_plan' AND NEW.lifecycle_status<>'PENDING_APPROVAL' THEN
      RAISE EXCEPTION 'runtime flex-benefit identities must start pending approval' USING ERRCODE='23514';
    END IF;
    IF TG_TABLE_NAME='flex_benefit_plan_version' AND NEW.approval_status<>'DRAFT' THEN
      RAISE EXCEPTION 'runtime flex-benefit versions must start as drafts' USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER flex_benefit_plan_runtime_default
  BEFORE INSERT ON compensation.flex_benefit_plan
  FOR EACH ROW EXECUTE FUNCTION compensation.require_flex_benefit_runtime_defaults();
CREATE TRIGGER flex_benefit_plan_version_runtime_default
  BEFORE INSERT ON compensation.flex_benefit_plan_version
  FOR EACH ROW EXECUTE FUNCTION compensation.require_flex_benefit_runtime_defaults();

CREATE OR REPLACE FUNCTION compensation.assert_flex_benefit_option_parent_draft()
RETURNS trigger LANGUAGE plpgsql
SET search_path=pg_catalog,compensation AS $$
DECLARE parent_status varchar;
BEGIN
  SELECT approval_status INTO parent_status
    FROM compensation.flex_benefit_plan_version
   WHERE tenant_id=NEW.tenant_id
     AND id=NEW.flex_benefit_plan_version_id
     AND flex_benefit_plan_id=NEW.flex_benefit_plan_id;
  IF parent_status IS NULL THEN
    RAISE EXCEPTION 'flex-benefit option parent version was not found' USING ERRCODE='23503';
  END IF;
  IF parent_status<>'DRAFT' THEN
    RAISE EXCEPTION 'flex-benefit options can only be inserted into draft versions' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER flex_benefit_option_parent_draft
  BEFORE INSERT ON compensation.flex_benefit_option
  FOR EACH ROW EXECUTE FUNCTION compensation.assert_flex_benefit_option_parent_draft();

CREATE OR REPLACE FUNCTION compensation.reject_approved_flex_benefit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.approval_status='APPROVED' THEN
    RAISE EXCEPTION 'approved flex-benefit plan versions are immutable';
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER flex_benefit_plan_version_approved_immutable
  BEFORE UPDATE OR DELETE ON compensation.flex_benefit_plan_version
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_approved_flex_benefit_mutation();

CREATE OR REPLACE FUNCTION compensation.reject_flex_benefit_option_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'flex-benefit options are append-only';
END $$;
CREATE TRIGGER flex_benefit_option_append_only
  BEFORE UPDATE OR DELETE ON compensation.flex_benefit_option
  FOR EACH ROW EXECUTE FUNCTION compensation.reject_flex_benefit_option_mutation();

CREATE OR REPLACE FUNCTION compensation.lock_flex_benefit_plan(p_tenant_id uuid,p_plan_id uuid)
RETURNS varchar LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE result varchar;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN RAISE EXCEPTION 'tenant mismatch'; END IF;
  SELECT lifecycle_status INTO result FROM compensation.flex_benefit_plan
   WHERE tenant_id=p_tenant_id AND id=p_plan_id FOR UPDATE;
  RETURN result;
END $$;

CREATE OR REPLACE FUNCTION compensation.approve_flex_benefit_plan_version(
  p_tenant_id uuid,p_version_id uuid,p_actor varchar,p_approved_at timestamptz)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE affected bigint; target_plan_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN RAISE EXCEPTION 'tenant mismatch'; END IF;

  UPDATE compensation.flex_benefit_plan_version version
     SET approval_status='APPROVED',approved_at=p_approved_at,approved_by=p_actor,
         updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
   WHERE version.tenant_id=p_tenant_id
     AND version.id=p_version_id
     AND version.approval_status='DRAFT'
     AND version.created_by<>p_actor
     AND NOT EXISTS (
       SELECT 1 FROM compensation.flex_benefit_plan_version successor
        WHERE successor.tenant_id=version.tenant_id AND successor.supersedes_version_id=version.id)
     AND EXISTS (
       SELECT 1
         FROM compensation.salary_supplemental_plan_version spv
         JOIN compensation.salary_supplemental_plan sp
           ON sp.tenant_id=spv.tenant_id AND sp.id=spv.supplemental_plan_id
        WHERE spv.tenant_id=version.tenant_id
          AND spv.id=version.supplemental_plan_version_id
          AND spv.supplemental_plan_id=version.supplemental_plan_id
          AND spv.plan_type='BENEFIT'
          AND spv.approval_status='APPROVED'
          AND sp.lifecycle_status='ACTIVE'
          AND spv.effective_from<=version.effective_from
          AND (spv.effective_to IS NULL OR
               (version.effective_to IS NOT NULL AND spv.effective_to>=version.effective_to)))
     AND (version.eligibility_rule_version_id IS NULL OR NOT EXISTS (
       SELECT 1
         FROM compensation.eligibility_rule_version erv
         JOIN compensation.eligibility_rule er
           ON er.tenant_id=erv.tenant_id AND er.id=erv.eligibility_rule_id
        WHERE erv.tenant_id=version.tenant_id
          AND erv.id=version.eligibility_rule_version_id
          AND (erv.approval_status<>'APPROVED' OR er.lifecycle_status<>'ACTIVE'
               OR erv.effective_from>version.effective_from
               OR (erv.effective_to IS NOT NULL AND
                   (version.effective_to IS NULL OR erv.effective_to<version.effective_to)))))
     AND EXISTS (
       SELECT 1 FROM compensation.flex_benefit_option o
        WHERE o.tenant_id=version.tenant_id AND o.flex_benefit_plan_version_id=version.id)
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.flex_benefit_option o
         LEFT JOIN compensation.pay_component_version pcv
           ON pcv.tenant_id=o.tenant_id AND pcv.id=o.component_version_id
         LEFT JOIN compensation.pay_component pc
           ON pc.tenant_id=pcv.tenant_id AND pc.id=pcv.component_id
         LEFT JOIN compensation.salary_supplemental_plan_line spl
           ON spl.tenant_id=o.tenant_id
          AND spl.supplemental_plan_version_id=version.supplemental_plan_version_id
          AND spl.component_version_id=o.component_version_id
        WHERE o.tenant_id=version.tenant_id
          AND o.flex_benefit_plan_version_id=version.id
          AND (pcv.id IS NULL OR pcv.approval_status<>'APPROVED' OR pc.lifecycle_status<>'ACTIVE'
               OR pcv.effective_from>version.effective_from
               OR (pcv.effective_to IS NOT NULL AND
                   (version.effective_to IS NULL OR pcv.effective_to<version.effective_to))
               OR spl.component_version_id IS NULL
               OR spl.effective_from>version.effective_from
               OR (spl.effective_to IS NOT NULL AND
                   (version.effective_to IS NULL OR spl.effective_to<version.effective_to))))
     AND (SELECT coalesce(sum(o.minimum_annual_amount),0)
            FROM compensation.flex_benefit_option o
           WHERE o.tenant_id=version.tenant_id
             AND o.flex_benefit_plan_version_id=version.id) <= version.annual_basket_amount
     AND (SELECT coalesce(sum(o.default_annual_amount),0)
            FROM compensation.flex_benefit_option o
           WHERE o.tenant_id=version.tenant_id
             AND o.flex_benefit_plan_version_id=version.id) <= version.annual_basket_amount
     AND NOT EXISTS (
       SELECT 1
         FROM compensation.pay_component_version pcv
         JOIN compensation.pay_component pc
           ON pc.tenant_id=pcv.tenant_id AND pc.id=pcv.component_id
        WHERE pcv.tenant_id=version.tenant_id
          AND pcv.id IN (version.taxable_fallback_component_version_id,version.encashment_component_version_id)
          AND (pcv.approval_status<>'APPROVED' OR pc.lifecycle_status<>'ACTIVE'
               OR pcv.effective_from>version.effective_from
               OR (pcv.effective_to IS NOT NULL AND
                   (version.effective_to IS NULL OR pcv.effective_to<version.effective_to))))
  RETURNING version.flex_benefit_plan_id INTO target_plan_id;

  GET DIAGNOSTICS affected=ROW_COUNT;
  IF affected=1 THEN
    UPDATE compensation.flex_benefit_plan
       SET lifecycle_status='ACTIVE',updated_at=p_approved_at,updated_by=p_actor,version_no=version_no+1
     WHERE tenant_id=p_tenant_id AND id=target_plan_id AND lifecycle_status='PENDING_APPROVAL';
  END IF;
  RETURN affected;
END $$;

REVOKE ALL ON FUNCTION compensation.require_flex_benefit_runtime_defaults() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.assert_flex_benefit_option_parent_draft() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_approved_flex_benefit_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.reject_flex_benefit_option_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.lock_flex_benefit_plan(uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION compensation.approve_flex_benefit_plan_version(uuid,uuid,varchar,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compensation.lock_flex_benefit_plan(uuid,uuid) TO payroll_app;
GRANT EXECUTE ON FUNCTION compensation.approve_flex_benefit_plan_version(uuid,uuid,varchar,timestamptz) TO payroll_app;

ALTER TABLE compensation.flex_benefit_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.flex_benefit_plan FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.flex_benefit_plan_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.flex_benefit_plan_version FORCE ROW LEVEL SECURITY;
ALTER TABLE compensation.flex_benefit_option ENABLE ROW LEVEL SECURITY;
ALTER TABLE compensation.flex_benefit_option FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON compensation.flex_benefit_plan
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.flex_benefit_plan_version
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());
CREATE POLICY tenant_isolation ON compensation.flex_benefit_option
  USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id());

GRANT SELECT,INSERT ON compensation.flex_benefit_plan,compensation.flex_benefit_plan_version,
  compensation.flex_benefit_option TO payroll_app;
REVOKE UPDATE,DELETE ON compensation.flex_benefit_plan,compensation.flex_benefit_plan_version,
  compensation.flex_benefit_option FROM payroll_app;
REVOKE CREATE ON SCHEMA compensation FROM payroll_app;

COMMENT ON TABLE compensation.flex_benefit_plan IS
  'Stable tenant-scoped identity for reusable flexible-benefit configuration.';
COMMENT ON TABLE compensation.flex_benefit_plan_version IS
  'Immutable effective-dated election-policy version pinned to one approved BENEFIT supplemental-plan version; no employee election state is stored here.';
COMMENT ON TABLE compensation.flex_benefit_option IS
  'Exact component-version option and annual allocation limits for a flexible-benefit policy version.';
