-- P5-EIP-01 G02A employee identity, bank and payment readiness.
-- Forward-only from V050. V001-V050 remain immutable.
--
-- Plaintext payroll identifiers, bank-account numbers and mismatch comparison
-- values are intentionally not represented by any column. Application code
-- stores AES-256-GCM ciphertext/IV/key-version for revealable secrets and
-- domain-separated HMAC-SHA-256 fingerprints for duplicate/comparison checks.
--
-- Explicitly excluded: country-specific statutory membership/rates/rules,
-- tax/declaration truth, generic payroll holds, calculation, bank-file/payment
-- execution, settlement, balances, accounting, remittance and payslip scope.

CREATE TABLE employee_payroll.payroll_identifier (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  scheme_code varchar(40) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_relationship_id, scheme_code),
  UNIQUE (tenant_id, payroll_relationship_id, scheme_code),
  CHECK (scheme_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.payroll_identifier_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_identifier_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  scheme_code varchar(40) NOT NULL,
  version_sequence integer NOT NULL,
  identifier_ciphertext bytea NOT NULL,
  identifier_iv bytea NOT NULL,
  encryption_key_version varchar(40) NOT NULL,
  identifier_fingerprint char(64) NOT NULL,
  masked_identifier varchar(80) NOT NULL,
  source_authority varchar(120),
  source_reference varchar(240),
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(30) NOT NULL DEFAULT 'DRAFT',
  verification_evidence_ref varchar(240),
  verified_at timestamptz,
  verified_by varchar(160),
  approved_at timestamptz,
  approved_by varchar(160),
  approval_evidence_ref varchar(240),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_identifier_id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, payroll_identifier_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (scheme_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (octet_length(identifier_ciphertext) >= 20),
  CHECK (octet_length(identifier_iv) = 12),
  CHECK (btrim(encryption_key_version) <> ''),
  CHECK (identifier_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (btrim(masked_identifier) <> ''),
  CHECK (source_authority IS NULL OR btrim(source_authority) <> ''),
  CHECK (source_reference IS NULL OR btrim(source_reference) <> ''),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN ('DRAFT','VERIFIED','ACTIVE','REJECTED','SUSPENDED','SUPERSEDED')),
  CHECK (
    lifecycle_status NOT IN ('VERIFIED','ACTIVE','SUSPENDED')
    OR (
      verification_evidence_ref IS NOT NULL
      AND btrim(verification_evidence_ref) <> ''
      AND verified_at IS NOT NULL
      AND verified_by IS NOT NULL
      AND btrim(verified_by) <> ''
    )
  ),
  CHECK (
    lifecycle_status NOT IN ('ACTIVE','SUSPENDED')
    OR (
      approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
      AND approval_evidence_ref IS NOT NULL
      AND btrim(approval_evidence_ref) <> ''
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  CONSTRAINT payroll_identifier_version_identity_fk
    FOREIGN KEY (
      tenant_id, payroll_identifier_id, payroll_relationship_id, scheme_code
    )
    REFERENCES employee_payroll.payroll_identifier(
      tenant_id, id, payroll_relationship_id, scheme_code
    ),
  CONSTRAINT payroll_identifier_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, payroll_identifier_id)
    REFERENCES employee_payroll.payroll_identifier_version(
      tenant_id, id, payroll_identifier_id
    )
);

ALTER TABLE employee_payroll.payroll_identifier_version
  ADD CONSTRAINT payroll_identifier_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_identifier_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

ALTER TABLE employee_payroll.payroll_identifier_version
  ADD CONSTRAINT payroll_identifier_fingerprint_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    scheme_code WITH =,
    identifier_fingerprint WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

CREATE UNIQUE INDEX payroll_identifier_one_successor_uk
  ON employee_payroll.payroll_identifier_version(tenant_id, supersedes_version_id)
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX payroll_identifier_relationship_ix
  ON employee_payroll.payroll_identifier(
    tenant_id, payroll_relationship_id, scheme_code
  );

CREATE TABLE employee_payroll.identity_mismatch_case (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  affected_field varchar(40) NOT NULL,
  source_kind varchar(40) NOT NULL,
  source_authority varchar(120),
  source_reference varchar(240),
  authoritative_fingerprint char(64),
  observed_fingerprint char(64),
  classification varchar(40) NOT NULL,
  payment_impact varchar(20) NOT NULL,
  correction_owner varchar(120) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'OPEN',
  detected_at timestamptz NOT NULL,
  resolved_at timestamptz,
  resolved_by varchar(160),
  version_no bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  CHECK (affected_field ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (source_kind ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (classification ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (payment_impact IN ('BLOCKING','WARNING','INFORMATIONAL')),
  CHECK (status IN ('OPEN','RESOLVED','ACCEPTED_VARIANCE')),
  CHECK (authoritative_fingerprint IS NULL OR authoritative_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (observed_fingerprint IS NULL OR observed_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (btrim(correction_owner) <> ''),
  CHECK (btrim(created_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.identity_mismatch_resolution (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  mismatch_case_id uuid NOT NULL,
  resolution varchar(40) NOT NULL,
  reason varchar(500) NOT NULL,
  evidence_ref varchar(240) NOT NULL,
  occurred_at timestamptz NOT NULL,
  actor varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  CHECK (resolution IN ('CORRECTED_AT_SOURCE','ACCEPTED_VARIANCE','FALSE_POSITIVE')),
  CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
  CHECK (btrim(evidence_ref) <> ''),
  CHECK (btrim(actor) <> ''),
  FOREIGN KEY (tenant_id, mismatch_case_id)
    REFERENCES employee_payroll.identity_mismatch_case(tenant_id, id)
);

CREATE INDEX identity_mismatch_relationship_ix
  ON employee_payroll.identity_mismatch_case(
    tenant_id, payroll_relationship_id, status, payment_impact, detected_at DESC
  );

CREATE TABLE employee_payroll.employee_bank_account (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, payroll_relationship_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.employee_bank_account_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  employee_bank_account_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  bank_name varchar(160) NOT NULL,
  branch_name varchar(160),
  routing_code varchar(80),
  account_holder_fingerprint char(64) NOT NULL,
  masked_account_holder_name varchar(160) NOT NULL,
  currency_code char(3) NOT NULL,
  account_number_ciphertext bytea NOT NULL,
  account_number_iv bytea NOT NULL,
  encryption_key_version varchar(40) NOT NULL,
  account_number_fingerprint char(64) NOT NULL,
  account_number_last4 varchar(4) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(30) NOT NULL DEFAULT 'DRAFT',
  verification_evidence_ref varchar(240),
  verified_at timestamptz,
  verified_by varchar(160),
  impact_reviewed_at timestamptz,
  impact_reviewed_by varchar(160),
  impact_review_evidence_ref varchar(240),
  approved_at timestamptz,
  approved_by varchar(160),
  approval_evidence_ref varchar(240),
  suspended_at timestamptz,
  suspended_by varchar(160),
  suspension_reason varchar(500),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, employee_bank_account_id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, employee_bank_account_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(bank_name) <> ''),
  CHECK (branch_name IS NULL OR btrim(branch_name) <> ''),
  CHECK (routing_code IS NULL OR btrim(routing_code) <> ''),
  CHECK (account_holder_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (btrim(masked_account_holder_name) <> ''),
  CHECK (currency_code ~ '^[A-Z]{3}$'),
  CHECK (octet_length(account_number_ciphertext) >= 20),
  CHECK (octet_length(account_number_iv) = 12),
  CHECK (btrim(encryption_key_version) <> ''),
  CHECK (account_number_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (account_number_last4 ~ '^[A-Z0-9]{4}$'),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN ('DRAFT','VERIFIED','ACTIVE','REJECTED','SUSPENDED','SUPERSEDED')),
  CHECK (
    lifecycle_status NOT IN ('VERIFIED','ACTIVE','SUSPENDED')
    OR (
      verification_evidence_ref IS NOT NULL
      AND btrim(verification_evidence_ref) <> ''
      AND verified_at IS NOT NULL
      AND verified_by IS NOT NULL
      AND btrim(verified_by) <> ''
    )
  ),
  CHECK (
    lifecycle_status NOT IN ('ACTIVE','SUSPENDED')
    OR (
      approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
      AND approval_evidence_ref IS NOT NULL
      AND btrim(approval_evidence_ref) <> ''
    )
  ),
  CHECK (
    impact_reviewed_at IS NULL
    OR (
      impact_reviewed_by IS NOT NULL
      AND btrim(impact_reviewed_by) <> ''
      AND impact_review_evidence_ref IS NOT NULL
      AND btrim(impact_review_evidence_ref) <> ''
    )
  ),
  CHECK (
    lifecycle_status <> 'SUSPENDED'
    OR (
      suspended_at IS NOT NULL
      AND suspended_by IS NOT NULL
      AND btrim(suspended_by) <> ''
      AND suspension_reason IS NOT NULL
      AND length(btrim(suspension_reason)) BETWEEN 1 AND 500
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  CONSTRAINT employee_bank_account_version_identity_fk
    FOREIGN KEY (tenant_id, employee_bank_account_id, payroll_relationship_id)
    REFERENCES employee_payroll.employee_bank_account(
      tenant_id, id, payroll_relationship_id
    ),
  CONSTRAINT employee_bank_account_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, employee_bank_account_id)
    REFERENCES employee_payroll.employee_bank_account_version(
      tenant_id, id, employee_bank_account_id
    )
);

ALTER TABLE employee_payroll.employee_bank_account_version
  ADD CONSTRAINT employee_bank_account_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    employee_bank_account_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

ALTER TABLE employee_payroll.employee_bank_account_version
  ADD CONSTRAINT employee_bank_account_fingerprint_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_relationship_id WITH =,
    currency_code WITH =,
    account_number_fingerprint WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

CREATE UNIQUE INDEX employee_bank_account_one_successor_uk
  ON employee_payroll.employee_bank_account_version(
    tenant_id, supersedes_version_id
  ) WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX employee_bank_account_relationship_ix
  ON employee_payroll.employee_bank_account(
    tenant_id, payroll_relationship_id, code
  );

CREATE TABLE employee_payroll.payment_instruction_set (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, payroll_relationship_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('PENDING_APPROVAL','ACTIVE','RETIRED')),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.payment_instruction_set_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payment_instruction_set_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  currency_code char(3) NOT NULL,
  allocation_mode varchar(30) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(24) NOT NULL DEFAULT 'DRAFT',
  impact_reviewed_at timestamptz,
  impact_reviewed_by varchar(160),
  impact_review_evidence_ref varchar(240),
  approved_at timestamptz,
  approved_by varchar(160),
  approval_evidence_ref varchar(240),
  supersedes_version_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payment_instruction_set_id),
  UNIQUE (tenant_id, id, payroll_relationship_id),
  UNIQUE (tenant_id, payment_instruction_set_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (currency_code ~ '^[A-Z]{3}$'),
  CHECK (allocation_mode IN ('PERCENTAGE','FIXED_THEN_REMAINDER')),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN ('DRAFT','ACTIVE','REJECTED','SUSPENDED','SUPERSEDED')),
  CHECK (
    lifecycle_status <> 'ACTIVE'
    OR (
      approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
      AND approval_evidence_ref IS NOT NULL
      AND btrim(approval_evidence_ref) <> ''
    )
  ),
  CHECK (
    impact_reviewed_at IS NULL
    OR (
      impact_reviewed_by IS NOT NULL
      AND btrim(impact_reviewed_by) <> ''
      AND impact_review_evidence_ref IS NOT NULL
      AND btrim(impact_review_evidence_ref) <> ''
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> '' AND btrim(updated_by) <> ''),
  CONSTRAINT payment_instruction_set_version_identity_fk
    FOREIGN KEY (
      tenant_id, payment_instruction_set_id, payroll_relationship_id
    )
    REFERENCES employee_payroll.payment_instruction_set(
      tenant_id, id, payroll_relationship_id
    ),
  CONSTRAINT payment_instruction_set_version_supersedes_fk
    FOREIGN KEY (tenant_id, supersedes_version_id, payment_instruction_set_id)
    REFERENCES employee_payroll.payment_instruction_set_version(
      tenant_id, id, payment_instruction_set_id
    )
);

CREATE TABLE employee_payroll.payment_instruction_line (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payment_instruction_set_version_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  line_sequence integer NOT NULL,
  employee_bank_account_version_id uuid NOT NULL,
  line_type varchar(30) NOT NULL,
  percentage numeric(9,6),
  fixed_amount numeric(19,4),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, payment_instruction_set_version_id, line_sequence),
  CHECK (line_sequence > 0),
  CHECK (line_type IN ('PERCENTAGE','FIXED_AMOUNT','REMAINING_BALANCE')),
  CHECK (
    (line_type = 'PERCENTAGE' AND percentage > 0 AND percentage <= 100 AND fixed_amount IS NULL)
    OR
    (line_type = 'FIXED_AMOUNT' AND fixed_amount > 0 AND percentage IS NULL)
    OR
    (line_type = 'REMAINING_BALANCE' AND percentage IS NULL AND fixed_amount IS NULL)
  ),
  CHECK (btrim(created_by) <> ''),
  CONSTRAINT payment_instruction_line_version_fk
    FOREIGN KEY (
      tenant_id, payment_instruction_set_version_id, payroll_relationship_id
    )
    REFERENCES employee_payroll.payment_instruction_set_version(
      tenant_id, id, payroll_relationship_id
    ),
  CONSTRAINT payment_instruction_line_bank_fk
    FOREIGN KEY (
      tenant_id, employee_bank_account_version_id, payroll_relationship_id
    )
    REFERENCES employee_payroll.employee_bank_account_version(
      tenant_id, id, payroll_relationship_id
    )
);

ALTER TABLE employee_payroll.payment_instruction_set_version
  ADD CONSTRAINT payment_instruction_active_relationship_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_relationship_id WITH =,
    currency_code WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

CREATE UNIQUE INDEX payment_instruction_one_successor_uk
  ON employee_payroll.payment_instruction_set_version(
    tenant_id, supersedes_version_id
  ) WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX payment_instruction_relationship_ix
  ON employee_payroll.payment_instruction_set(
    tenant_id, payroll_relationship_id, code
  );

CREATE TABLE employee_payroll.payment_restriction (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_relationship_id uuid NOT NULL,
  restriction_kind varchar(30) NOT NULL,
  source_reference varchar(240) NOT NULL,
  reason_code varchar(80) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  current_state varchar(20) NOT NULL DEFAULT 'IMPOSED',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (restriction_kind IN ('FRAUD','SECURITY','BENEFICIARY')),
  CHECK (btrim(source_reference) <> ''),
  CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (current_state IN ('IMPOSED','CLEARED')),
  CHECK (btrim(created_by) <> ''),
  FOREIGN KEY (tenant_id, payroll_relationship_id)
    REFERENCES employee_payroll.payroll_relationship(tenant_id, id)
);

CREATE TABLE employee_payroll.payment_restriction_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payment_restriction_id uuid NOT NULL,
  event_sequence integer NOT NULL,
  event_type varchar(20) NOT NULL,
  evidence_ref varchar(240) NOT NULL,
  occurred_at timestamptz NOT NULL,
  actor varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, payment_restriction_id, event_sequence),
  CHECK (event_sequence > 0),
  CHECK (event_type IN ('IMPOSED','CLEARED')),
  CHECK (btrim(evidence_ref) <> ''),
  CHECK (btrim(actor) <> ''),
  FOREIGN KEY (tenant_id, payment_restriction_id)
    REFERENCES employee_payroll.payment_restriction(tenant_id, id)
);

CREATE INDEX payment_restriction_relationship_ix
  ON employee_payroll.payment_restriction(
    tenant_id, payroll_relationship_id, effective_from, effective_to
  );


-- Runtime INSERT is intentionally retained for draft aggregate construction,
-- but database triggers fail closed if callers attempt to manufacture approved
-- lifecycle state instead of using the controlled SECURITY DEFINER functions.

CREATE OR REPLACE FUNCTION employee_payroll.assert_eip_stable_identity_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
BEGIN
  IF NEW.status <> 'PENDING_APPROVAL' OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'P5-EIP stable identities must be inserted in clean pending state'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_identifier_clean_insert
  BEFORE INSERT ON employee_payroll.payroll_identifier
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_eip_stable_identity_insert();

CREATE TRIGGER employee_bank_account_clean_insert
  BEFORE INSERT ON employee_payroll.employee_bank_account
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_eip_stable_identity_insert();

CREATE TRIGGER payment_instruction_set_clean_insert
  BEFORE INSERT ON employee_payroll.payment_instruction_set
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_eip_stable_identity_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_payroll_identifier_version_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
  prior_effective_from date;
BEGIN
  SELECT status INTO identity_status
    FROM employee_payroll.payroll_identifier
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payroll_identifier_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'payroll identifier identity does not exist'
      USING ERRCODE = '23503';
  END IF;
  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired payroll identifier cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.lifecycle_status <> 'DRAFT'
     OR NEW.verification_evidence_ref IS NOT NULL
     OR NEW.verified_at IS NOT NULL
     OR NEW.verified_by IS NOT NULL
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.approval_evidence_ref IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'payroll identifier versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM employee_payroll.payroll_identifier_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.payroll_identifier_id = NEW.payroll_identifier_id
       ) THEN
      RAISE EXCEPTION 'first payroll identifier version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later payroll identifier versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;
    SELECT version_sequence, effective_from
      INTO prior_sequence, prior_effective_from
      FROM employee_payroll.payroll_identifier_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND payroll_identifier_id = NEW.payroll_identifier_id;
    IF prior_sequence IS NULL OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'payroll identifier version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.effective_from < prior_effective_from THEN
      RAISE EXCEPTION 'payroll identifier successor cannot start before its predecessor'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payroll_identifier_version_clean_insert
  BEFORE INSERT ON employee_payroll.payroll_identifier_version
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_payroll_identifier_version_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_employee_bank_account_version_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
  prior_effective_from date;
BEGIN
  SELECT status INTO identity_status
    FROM employee_payroll.employee_bank_account
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.employee_bank_account_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'employee bank-account identity does not exist'
      USING ERRCODE = '23503';
  END IF;
  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired employee bank account cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.lifecycle_status <> 'DRAFT'
     OR NEW.verification_evidence_ref IS NOT NULL
     OR NEW.verified_at IS NOT NULL
     OR NEW.verified_by IS NOT NULL
     OR NEW.impact_reviewed_at IS NOT NULL
     OR NEW.impact_reviewed_by IS NOT NULL
     OR NEW.impact_review_evidence_ref IS NOT NULL
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.approval_evidence_ref IS NOT NULL
     OR NEW.suspended_at IS NOT NULL
     OR NEW.suspended_by IS NOT NULL
     OR NEW.suspension_reason IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'employee bank-account versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM employee_payroll.employee_bank_account_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.employee_bank_account_id = NEW.employee_bank_account_id
       ) THEN
      RAISE EXCEPTION 'first employee bank-account version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later employee bank-account versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;
    SELECT version_sequence, effective_from
      INTO prior_sequence, prior_effective_from
      FROM employee_payroll.employee_bank_account_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND employee_bank_account_id = NEW.employee_bank_account_id;
    IF prior_sequence IS NULL OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'employee bank-account version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.effective_from < prior_effective_from THEN
      RAISE EXCEPTION 'employee bank-account successor cannot start before its predecessor'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER employee_bank_account_version_clean_insert
  BEFORE INSERT ON employee_payroll.employee_bank_account_version
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_employee_bank_account_version_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_payment_instruction_version_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
  prior_effective_from date;
BEGIN
  SELECT status INTO identity_status
    FROM employee_payroll.payment_instruction_set
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payment_instruction_set_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'payment instruction identity does not exist'
      USING ERRCODE = '23503';
  END IF;
  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired payment instruction cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.lifecycle_status <> 'DRAFT'
     OR NEW.impact_reviewed_at IS NOT NULL
     OR NEW.impact_reviewed_by IS NOT NULL
     OR NEW.impact_review_evidence_ref IS NOT NULL
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.approval_evidence_ref IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'payment instruction versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM employee_payroll.payment_instruction_set_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.payment_instruction_set_id = NEW.payment_instruction_set_id
       ) THEN
      RAISE EXCEPTION 'first payment instruction version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later payment instruction versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;
    SELECT version_sequence, effective_from
      INTO prior_sequence, prior_effective_from
      FROM employee_payroll.payment_instruction_set_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND payment_instruction_set_id = NEW.payment_instruction_set_id;
    IF prior_sequence IS NULL OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'payment instruction version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
    IF NEW.effective_from < prior_effective_from THEN
      RAISE EXCEPTION 'payment instruction successor cannot start before its predecessor'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payment_instruction_version_clean_insert
  BEFORE INSERT ON employee_payroll.payment_instruction_set_version
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_payment_instruction_version_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_payment_instruction_line_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  parent_status varchar(24);
BEGIN
  SELECT lifecycle_status INTO parent_status
    FROM employee_payroll.payment_instruction_set_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payment_instruction_set_version_id;

  IF parent_status IS NULL THEN
    RAISE EXCEPTION 'payment instruction version does not exist'
      USING ERRCODE = '23503';
  END IF;
  IF parent_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'payment instruction lines are immutable after approval'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payment_instruction_line_draft_only
  BEFORE INSERT ON employee_payroll.payment_instruction_line
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_payment_instruction_line_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_identity_mismatch_case_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
BEGIN
  IF NEW.status <> 'OPEN'
     OR NEW.resolved_at IS NOT NULL
     OR NEW.resolved_by IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'identity mismatch cases must be inserted open and unresolved'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER identity_mismatch_case_clean_insert
  BEFORE INSERT ON employee_payroll.identity_mismatch_case
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_identity_mismatch_case_insert();

CREATE OR REPLACE FUNCTION employee_payroll.assert_payment_restriction_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
BEGIN
  IF NEW.current_state <> 'IMPOSED' OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'payment restrictions must be inserted in clean imposed state'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER payment_restriction_clean_insert
  BEFORE INSERT ON employee_payroll.payment_restriction
  FOR EACH ROW
  EXECUTE FUNCTION employee_payroll.assert_payment_restriction_insert();


CREATE OR REPLACE FUNCTION employee_payroll.verify_payroll_identifier_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_verified_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT created_by INTO maker
    FROM employee_payroll.payroll_identifier_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent identifier verification evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE employee_payroll.payroll_identifier_version
     SET lifecycle_status = 'VERIFIED',
         verification_evidence_ref = p_evidence_ref,
         verified_at = p_verified_at,
         verified_by = p_actor,
         updated_at = p_verified_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.activate_payroll_identifier_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  verifier varchar(160);
  identity_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT created_by, verified_by, payroll_identifier_id
    INTO maker, verifier, identity_id
    FROM employee_payroll.payroll_identifier_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_actor = maker OR p_actor = verifier
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent final identifier approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE employee_payroll.payroll_identifier_version
     SET lifecycle_status = 'SUPERSEDED',
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND payroll_identifier_id = identity_id
     AND id <> p_version_id
     AND lifecycle_status = 'ACTIVE';

  UPDATE employee_payroll.payroll_identifier_version
     SET lifecycle_status = 'ACTIVE',
         approved_at = p_approved_at,
         approved_by = p_actor,
         approval_evidence_ref = p_evidence_ref,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected = 1 THEN
    UPDATE employee_payroll.payroll_identifier
       SET status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id AND id = identity_id;
  END IF;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.resolve_identity_mismatch(
  p_tenant_id uuid,
  p_case_id uuid,
  p_expected_version bigint,
  p_resolution varchar,
  p_reason varchar,
  p_evidence_ref varchar,
  p_actor varchar,
  p_resolved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT created_by INTO maker
    FROM employee_payroll.identity_mismatch_case
   WHERE tenant_id = p_tenant_id
     AND id = p_case_id
     AND status = 'OPEN'
     AND version_no = p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker THEN
    RAISE EXCEPTION 'independent mismatch resolver is required'
      USING ERRCODE = '42501';
  END IF;
  IF p_resolution NOT IN ('CORRECTED_AT_SOURCE','ACCEPTED_VARIANCE','FALSE_POSITIVE')
     OR p_reason IS NULL OR btrim(p_reason) = ''
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'valid mismatch resolution evidence is required'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO employee_payroll.identity_mismatch_resolution(
    tenant_id, mismatch_case_id, resolution, reason, evidence_ref,
    occurred_at, actor
  ) VALUES (
    p_tenant_id, p_case_id, p_resolution, p_reason, p_evidence_ref,
    p_resolved_at, p_actor
  );

  UPDATE employee_payroll.identity_mismatch_case
     SET status = CASE
                    WHEN p_resolution = 'ACCEPTED_VARIANCE'
                      THEN 'ACCEPTED_VARIANCE'
                    ELSE 'RESOLVED'
                  END,
         resolved_at = p_resolved_at,
         resolved_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_case_id
     AND status = 'OPEN'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.verify_employee_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_verified_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  SELECT created_by INTO maker
    FROM employee_payroll.employee_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;
  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent employee-bank verification evidence is required'
      USING ERRCODE = '42501';
  END IF;
  UPDATE employee_payroll.employee_bank_account_version
     SET lifecycle_status = 'VERIFIED',
         verification_evidence_ref = p_evidence_ref,
         verified_at = p_verified_at,
         verified_by = p_actor,
         updated_at = p_verified_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.review_employee_bank_account_impact(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_reviewed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'payment impact review evidence is required'
      USING ERRCODE = '23514';
  END IF;
  UPDATE employee_payroll.employee_bank_account_version
     SET impact_reviewed_at = p_reviewed_at,
         impact_reviewed_by = p_actor,
         impact_review_evidence_ref = p_evidence_ref,
         updated_at = p_reviewed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version
     AND supersedes_version_id IS NOT NULL;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.activate_employee_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  verifier varchar(160);
  identity_id uuid;
  predecessor_approved_at timestamptz;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT version.created_by, version.verified_by,
         version.employee_bank_account_id, predecessor.approved_at
    INTO maker, verifier, identity_id, predecessor_approved_at
    FROM employee_payroll.employee_bank_account_version version
    LEFT JOIN employee_payroll.employee_bank_account_version predecessor
      ON predecessor.tenant_id = version.tenant_id
     AND predecessor.id = version.supersedes_version_id
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.lifecycle_status = 'VERIFIED'
     AND version.version_no = p_expected_version
   FOR UPDATE OF version;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_actor = maker OR p_actor = verifier
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent final employee-bank approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  IF predecessor_approved_at IS NOT NULL AND NOT EXISTS (
    SELECT 1
      FROM employee_payroll.employee_bank_account_version v
     WHERE v.tenant_id = p_tenant_id
       AND v.id = p_version_id
       AND v.impact_reviewed_at IS NOT NULL
       AND v.impact_review_evidence_ref IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'approved bank successor requires payment impact review'
      USING ERRCODE = '23514';
  END IF;

  UPDATE employee_payroll.employee_bank_account_version
     SET lifecycle_status = 'SUPERSEDED',
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND employee_bank_account_id = identity_id
     AND id <> p_version_id
     AND lifecycle_status = 'ACTIVE';

  UPDATE employee_payroll.employee_bank_account_version
     SET lifecycle_status = 'ACTIVE',
         approved_at = p_approved_at,
         approved_by = p_actor,
         approval_evidence_ref = p_evidence_ref,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE employee_payroll.employee_bank_account
       SET status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id AND id = identity_id;
  END IF;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.suspend_employee_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_suspended_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_reason IS NULL OR btrim(p_reason) = '' THEN
    RAISE EXCEPTION 'bank suspension actor and reason are required'
      USING ERRCODE = '23514';
  END IF;
  UPDATE employee_payroll.employee_bank_account_version
     SET lifecycle_status = 'SUSPENDED',
         suspended_at = p_suspended_at,
         suspended_by = p_actor,
         suspension_reason = p_reason,
         updated_at = p_suspended_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'ACTIVE'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.review_payment_instruction_impact(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_reviewed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_actor IS NULL OR btrim(p_actor) = ''
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'payment instruction impact review evidence is required'
      USING ERRCODE = '23514';
  END IF;
  UPDATE employee_payroll.payment_instruction_set_version
     SET impact_reviewed_at = p_reviewed_at,
         impact_reviewed_by = p_actor,
         impact_review_evidence_ref = p_evidence_ref,
         updated_at = p_reviewed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
     AND supersedes_version_id IS NOT NULL;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.activate_payment_instruction_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  identity_id uuid;
  relationship_id uuid;
  mode_value varchar(30);
  currency_value char(3);
  from_value date;
  to_value date;
  predecessor_approved_at timestamptz;
  line_count integer;
  percentage_count integer;
  fixed_count integer;
  remainder_count integer;
  percentage_total numeric(12,6);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT version.created_by, version.payment_instruction_set_id,
         version.payroll_relationship_id, version.allocation_mode,
         version.currency_code, version.effective_from, version.effective_to,
         predecessor.approved_at
    INTO maker, identity_id, relationship_id, mode_value, currency_value,
         from_value, to_value, predecessor_approved_at
    FROM employee_payroll.payment_instruction_set_version version
    LEFT JOIN employee_payroll.payment_instruction_set_version predecessor
      ON predecessor.tenant_id = version.tenant_id
     AND predecessor.id = version.supersedes_version_id
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.lifecycle_status = 'DRAFT'
     AND version.version_no = p_expected_version
   FOR UPDATE OF version;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent payment instruction approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  IF predecessor_approved_at IS NOT NULL AND NOT EXISTS (
    SELECT 1
      FROM employee_payroll.payment_instruction_set_version v
     WHERE v.tenant_id = p_tenant_id
       AND v.id = p_version_id
       AND v.impact_reviewed_at IS NOT NULL
       AND v.impact_review_evidence_ref IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'approved payment instruction successor requires impact review'
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*),
         count(*) FILTER (WHERE line_type = 'PERCENTAGE'),
         count(*) FILTER (WHERE line_type = 'FIXED_AMOUNT'),
         count(*) FILTER (WHERE line_type = 'REMAINING_BALANCE'),
         coalesce(sum(percentage) FILTER (WHERE line_type = 'PERCENTAGE'), 0)
    INTO line_count, percentage_count, fixed_count, remainder_count, percentage_total
    FROM employee_payroll.payment_instruction_line
   WHERE tenant_id = p_tenant_id
     AND payment_instruction_set_version_id = p_version_id;

  IF mode_value = 'PERCENTAGE' THEN
    IF line_count = 0 OR percentage_count <> line_count
       OR remainder_count <> 0 OR fixed_count <> 0
       OR percentage_total <> 100.000000 THEN
      RAISE EXCEPTION 'percentage payment allocation must total exactly 100 percent'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF line_count = 0 OR percentage_count <> 0
       OR fixed_count < 1 OR remainder_count <> 1
       OR fixed_count + remainder_count <> line_count THEN
      RAISE EXCEPTION 'fixed allocation requires fixed lines and exactly one remaining balance'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM employee_payroll.payment_instruction_line line
      JOIN employee_payroll.employee_bank_account_version bank
        ON bank.tenant_id = line.tenant_id
       AND bank.id = line.employee_bank_account_version_id
     WHERE line.tenant_id = p_tenant_id
       AND line.payment_instruction_set_version_id = p_version_id
       AND (
         bank.lifecycle_status <> 'ACTIVE'
         OR bank.currency_code <> currency_value
         OR bank.effective_from > from_value
         OR (to_value IS NULL AND bank.effective_to IS NOT NULL)
         OR (to_value IS NOT NULL
             AND bank.effective_to IS NOT NULL
             AND bank.effective_to < to_value)
       )
  ) THEN
    RAISE EXCEPTION 'payment instruction references unavailable or incompatible bank account'
      USING ERRCODE = '23514';
  END IF;

  UPDATE employee_payroll.payment_instruction_set_version
     SET lifecycle_status = 'SUPERSEDED',
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND payment_instruction_set_id = identity_id
     AND id <> p_version_id
     AND lifecycle_status = 'ACTIVE';

  UPDATE employee_payroll.payment_instruction_set_version
     SET lifecycle_status = 'ACTIVE',
         approved_at = p_approved_at,
         approved_by = p_actor,
         approval_evidence_ref = p_evidence_ref,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE employee_payroll.payment_instruction_set
       SET status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id AND id = identity_id;
  END IF;
  RETURN affected;
END $$;


CREATE OR REPLACE FUNCTION employee_payroll.create_payment_restriction(
  p_tenant_id uuid,
  p_restriction_id uuid,
  p_payroll_relationship_id uuid,
  p_restriction_kind varchar,
  p_source_reference varchar,
  p_reason_code varchar,
  p_evidence_ref varchar,
  p_effective_from date,
  p_effective_to date,
  p_actor varchar,
  p_created_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_restriction_id IS NULL OR p_payroll_relationship_id IS NULL
     OR p_restriction_kind NOT IN ('FRAUD','SECURITY','BENEFICIARY')
     OR p_source_reference IS NULL OR btrim(p_source_reference) = ''
     OR p_reason_code IS NULL OR p_reason_code !~ '^[A-Z][A-Z0-9_]{1,79}$'
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = ''
     OR p_effective_from IS NULL
     OR (p_effective_to IS NOT NULL AND p_effective_to <= p_effective_from)
     OR p_actor IS NULL OR btrim(p_actor) = ''
     OR p_created_at IS NULL THEN
    RAISE EXCEPTION 'valid payment restriction evidence is required'
      USING ERRCODE = '23514';
  END IF;

  INSERT INTO employee_payroll.payment_restriction(
    id, tenant_id, payroll_relationship_id, restriction_kind,
    source_reference, reason_code, effective_from, effective_to,
    created_at, created_by
  ) VALUES (
    p_restriction_id, p_tenant_id, p_payroll_relationship_id,
    p_restriction_kind, p_source_reference, p_reason_code,
    p_effective_from, p_effective_to, p_created_at, p_actor
  );

  INSERT INTO employee_payroll.payment_restriction_event(
    tenant_id, payment_restriction_id, event_sequence, event_type,
    evidence_ref, occurred_at, actor
  ) VALUES (
    p_tenant_id, p_restriction_id, 1, 'IMPOSED',
    p_evidence_ref, p_created_at, p_actor
  );
  RETURN 1;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.clear_payment_restriction(
  p_tenant_id uuid,
  p_restriction_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_cleared_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  next_sequence integer;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;

  SELECT created_by INTO maker
    FROM employee_payroll.payment_restriction
   WHERE tenant_id = p_tenant_id
     AND id = p_restriction_id
     AND current_state = 'IMPOSED'
     AND version_no = p_expected_version
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker
     OR p_evidence_ref IS NULL OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent payment restriction clear evidence is required'
      USING ERRCODE = '42501';
  END IF;

  SELECT coalesce(max(event_sequence),0) + 1
    INTO next_sequence
    FROM employee_payroll.payment_restriction_event
   WHERE tenant_id = p_tenant_id
     AND payment_restriction_id = p_restriction_id;

  INSERT INTO employee_payroll.payment_restriction_event(
    tenant_id, payment_restriction_id, event_sequence, event_type,
    evidence_ref, occurred_at, actor
  ) VALUES (
    p_tenant_id, p_restriction_id, next_sequence, 'CLEARED',
    p_evidence_ref, p_cleared_at, p_actor
  );

  UPDATE employee_payroll.payment_restriction
     SET current_state = 'CLEARED',
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_restriction_id
     AND current_state = 'IMPOSED'
     AND version_no = p_expected_version;
  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION employee_payroll.payment_readiness_findings(
  p_tenant_id uuid,
  p_payroll_relationship_id uuid,
  p_currency_code varchar,
  p_as_of date
) RETURNS TABLE (
  severity varchar,
  finding_code varchar,
  detail varchar
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, employee_payroll, platform AS $$
DECLARE
  instruction_version_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch' USING ERRCODE = '42501';
  END IF;
  IF p_as_of IS NULL OR p_currency_code !~ '^[A-Z]{3}$' THEN
    RAISE EXCEPTION 'as-of date and ISO currency are required'
      USING ERRCODE = '23514';
  END IF;

  SELECT version.id INTO instruction_version_id
    FROM employee_payroll.payment_instruction_set_version version
   WHERE version.tenant_id = p_tenant_id
     AND version.payroll_relationship_id = p_payroll_relationship_id
     AND version.currency_code = p_currency_code
     AND version.lifecycle_status = 'ACTIVE'
     AND version.effective_from <= p_as_of
     AND (version.effective_to IS NULL OR p_as_of < version.effective_to)
   ORDER BY version.version_sequence DESC
   LIMIT 1;

  IF instruction_version_id IS NULL THEN
    RETURN QUERY SELECT
      'BLOCKER'::varchar,
      'PAYMENT_INSTRUCTION_MISSING'::varchar,
      'No approved effective payment instruction exists for the requested currency.'::varchar;
  ELSE
    RETURN QUERY
    SELECT
      'BLOCKER'::varchar,
      'BANK_ACCOUNT_UNAVAILABLE'::varchar,
      ('Referenced employee bank account ' || line.employee_bank_account_version_id
       || ' is not approved/effective for payment.')::varchar
      FROM employee_payroll.payment_instruction_line line
      JOIN employee_payroll.employee_bank_account_version bank
        ON bank.tenant_id = line.tenant_id
       AND bank.id = line.employee_bank_account_version_id
     WHERE line.tenant_id = p_tenant_id
       AND line.payment_instruction_set_version_id = instruction_version_id
       AND (
         bank.lifecycle_status <> 'ACTIVE'
         OR bank.currency_code <> p_currency_code
         OR bank.effective_from > p_as_of
         OR (bank.effective_to IS NOT NULL AND p_as_of >= bank.effective_to)
       );
  END IF;

  RETURN QUERY
  SELECT
    CASE mismatch.payment_impact
      WHEN 'BLOCKING' THEN 'BLOCKER'::varchar
      ELSE 'WARNING'::varchar
    END,
    ('IDENTITY_MISMATCH_' || mismatch.affected_field)::varchar,
    ('Unresolved ' || mismatch.source_kind || ' identity mismatch requires source-authority resolution.')::varchar
    FROM employee_payroll.identity_mismatch_case mismatch
   WHERE mismatch.tenant_id = p_tenant_id
     AND mismatch.payroll_relationship_id = p_payroll_relationship_id
     AND mismatch.status = 'OPEN'
     AND mismatch.payment_impact IN ('BLOCKING','WARNING');

  RETURN QUERY
  SELECT
    'BLOCKER'::varchar,
    ('PAYMENT_RESTRICTION_' || restriction.restriction_kind)::varchar,
    ('Active ' || restriction.restriction_kind || ' payment restriction: '
      || restriction.reason_code)::varchar
    FROM employee_payroll.payment_restriction restriction
    JOIN LATERAL (
      SELECT event.event_type
        FROM employee_payroll.payment_restriction_event event
       WHERE event.tenant_id = restriction.tenant_id
         AND event.payment_restriction_id = restriction.id
         AND event.occurred_at::date <= p_as_of
       ORDER BY event.occurred_at DESC, event.event_sequence DESC
       LIMIT 1
    ) latest ON true
   WHERE restriction.tenant_id = p_tenant_id
     AND restriction.payroll_relationship_id = p_payroll_relationship_id
     AND restriction.effective_from <= p_as_of
     AND (restriction.effective_to IS NULL OR p_as_of < restriction.effective_to)
     AND latest.event_type = 'IMPOSED';

  IF NOT EXISTS (
    SELECT 1
      FROM employee_payroll.salary_assignment salary
      JOIN employee_payroll.payroll_assignment_version assignment_version
        ON assignment_version.tenant_id = salary.tenant_id
       AND assignment_version.id = salary.payroll_assignment_version_id
      JOIN employee_payroll.payroll_assignment assignment
        ON assignment.tenant_id = assignment_version.tenant_id
       AND assignment.id = assignment_version.payroll_assignment_id
     WHERE salary.tenant_id = p_tenant_id
       AND assignment.payroll_relationship_id = p_payroll_relationship_id
       AND salary.approval_status = 'APPROVED'
       AND salary.effective_from <= p_as_of
       AND (salary.effective_to IS NULL OR p_as_of < salary.effective_to)
  ) THEN
    RETURN QUERY SELECT
      'BLOCKER'::varchar,
      'COMPENSATION_BINDING_MISSING'::varchar,
      'No approved effective salary assignment exists for payment-currency validation.'::varchar;
  ELSIF EXISTS (
    SELECT 1
      FROM employee_payroll.salary_assignment salary
      JOIN employee_payroll.payroll_assignment_version assignment_version
        ON assignment_version.tenant_id = salary.tenant_id
       AND assignment_version.id = salary.payroll_assignment_version_id
      JOIN employee_payroll.payroll_assignment assignment
        ON assignment.tenant_id = assignment_version.tenant_id
       AND assignment.id = assignment_version.payroll_assignment_id
     WHERE salary.tenant_id = p_tenant_id
       AND assignment.payroll_relationship_id = p_payroll_relationship_id
       AND salary.approval_status = 'APPROVED'
       AND salary.effective_from <= p_as_of
       AND (salary.effective_to IS NULL OR p_as_of < salary.effective_to)
       AND salary.currency <> p_currency_code
  ) THEN
    RETURN QUERY SELECT
      'BLOCKER'::varchar,
      'PAYMENT_CURRENCY_MISMATCH'::varchar,
      'Payment instruction currency is incompatible with an approved effective salary assignment.'::varchar;
  END IF;
END $$;

DO $$
DECLARE
  relation_name text;
BEGIN
  FOREACH relation_name IN ARRAY ARRAY[
    'payroll_identifier',
    'payroll_identifier_version',
    'identity_mismatch_case',
    'identity_mismatch_resolution',
    'employee_bank_account',
    'employee_bank_account_version',
    'payment_instruction_set',
    'payment_instruction_set_version',
    'payment_instruction_line',
    'payment_restriction',
    'payment_restriction_event'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE employee_payroll.%I ENABLE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'ALTER TABLE employee_payroll.%I FORCE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON employee_payroll.%I '
      || 'USING (tenant_id = platform.current_tenant_id()) '
      || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      relation_name
    );
  END LOOP;
END $$;

GRANT USAGE ON SCHEMA employee_payroll TO payroll_app;

GRANT SELECT, INSERT
  ON employee_payroll.payroll_identifier,
     employee_payroll.payroll_identifier_version,
     employee_payroll.identity_mismatch_case,
     employee_payroll.employee_bank_account,
     employee_payroll.employee_bank_account_version,
     employee_payroll.payment_instruction_set,
     employee_payroll.payment_instruction_set_version,
     employee_payroll.payment_instruction_line
  TO payroll_app;

GRANT SELECT
  ON employee_payroll.identity_mismatch_resolution,
     employee_payroll.payment_restriction,
     employee_payroll.payment_restriction_event
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON employee_payroll.payroll_identifier,
     employee_payroll.payroll_identifier_version,
     employee_payroll.identity_mismatch_case,
     employee_payroll.identity_mismatch_resolution,
     employee_payroll.employee_bank_account,
     employee_payroll.employee_bank_account_version,
     employee_payroll.payment_instruction_set,
     employee_payroll.payment_instruction_set_version,
     employee_payroll.payment_instruction_line,
     employee_payroll.payment_restriction,
     employee_payroll.payment_restriction_event
  FROM payroll_app;

REVOKE ALL ON FUNCTION employee_payroll.verify_payroll_identifier_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.activate_payroll_identifier_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.resolve_identity_mismatch(
  uuid, uuid, bigint, varchar, varchar, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.verify_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.review_employee_bank_account_impact(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.activate_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.suspend_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.review_payment_instruction_impact(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.activate_payment_instruction_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.create_payment_restriction(
  uuid, uuid, uuid, varchar, varchar, varchar, varchar, date, date, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.clear_payment_restriction(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION employee_payroll.payment_readiness_findings(
  uuid, uuid, varchar, date
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION employee_payroll.verify_payroll_identifier_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.activate_payroll_identifier_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.resolve_identity_mismatch(
  uuid, uuid, bigint, varchar, varchar, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.verify_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.review_employee_bank_account_impact(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.activate_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.suspend_employee_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.review_payment_instruction_impact(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.activate_payment_instruction_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.create_payment_restriction(
  uuid, uuid, uuid, varchar, varchar, varchar, varchar, date, date, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.clear_payment_restriction(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION employee_payroll.payment_readiness_findings(
  uuid, uuid, varchar, date
) TO payroll_app;

REVOKE CREATE ON SCHEMA employee_payroll FROM payroll_app;

COMMENT ON TABLE employee_payroll.payroll_identifier_version IS
  'Effective-dated encrypted employee payroll identifiers; plaintext is never persisted.';
COMMENT ON TABLE employee_payroll.identity_mismatch_case IS
  'Source-authority mismatch case retaining non-reversible comparison fingerprints and payment impact.';
COMMENT ON TABLE employee_payroll.employee_bank_account_version IS
  'Effective-dated encrypted employee bank account version; account-holder name is fingerprinted/masked only.';
COMMENT ON TABLE employee_payroll.payment_instruction_set_version IS
  'Versioned employee payment-allocation instruction set, separate from employee bank-account identity.';
COMMENT ON TABLE employee_payroll.payment_restriction_event IS
  'Append-only FRAUD/SECURITY/BENEFICIARY payment restriction lifecycle evidence.';
