-- P5-FBA-01 G01 foundation banking and authority database foundation.
--
-- Forward-only from V034. V001-V034 remain immutable.
-- This migration introduces tenant-scoped employer bank-account and authorised-
-- signatory identities, immutable effective-dated versions, delegated-authority
-- scopes, RLS and controlled lifecycle transitions.
--
-- Bank account plaintext is intentionally NOT represented by any column.
-- Application code stores AES-256-GCM ciphertext/IV/key-version and a separate
-- keyed HMAC-SHA-256 fingerprint plus safe last-four masking metadata.
--
-- Explicitly excluded: employee bank accounts, payment execution/file
-- generation, immutable configuration snapshots and complete readiness closure.

CREATE TABLE organisation.employer_bank_account (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  owner_kind varchar(30) NOT NULL,
  legal_entity_id uuid,
  payroll_statutory_unit_id uuid,
  owner_key varchar(80)
    GENERATED ALWAYS AS (
      CASE owner_kind
        WHEN 'LEGAL_ENTITY'
          THEN 'LEGAL_ENTITY:' || legal_entity_id::text
        WHEN 'PAYROLL_STATUTORY_UNIT'
          THEN 'PAYROLL_STATUTORY_UNIT:' || payroll_statutory_unit_id::text
        ELSE NULL
      END
    ) STORED,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  UNIQUE (tenant_id, id, owner_key),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (owner_kind IN ('LEGAL_ENTITY', 'PAYROLL_STATUTORY_UNIT')),
  CHECK (
    (owner_kind = 'LEGAL_ENTITY'
      AND legal_entity_id IS NOT NULL
      AND payroll_statutory_unit_id IS NULL)
    OR
    (owner_kind = 'PAYROLL_STATUTORY_UNIT'
      AND legal_entity_id IS NULL
      AND payroll_statutory_unit_id IS NOT NULL)
  ),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, legal_entity_id)
    REFERENCES organisation.legal_entity(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_statutory_unit_id)
    REFERENCES organisation.payroll_statutory_unit(tenant_id, id)
);

CREATE TABLE organisation.employer_bank_account_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  employer_bank_account_id uuid NOT NULL,
  owner_key varchar(80) NOT NULL,
  version_sequence integer NOT NULL,
  bank_name varchar(160) NOT NULL,
  branch_name varchar(160),
  routing_code varchar(80),
  account_holder_name varchar(160) NOT NULL,
  currency_code char(3) NOT NULL,
  account_number_ciphertext bytea NOT NULL,
  account_number_iv bytea NOT NULL,
  encryption_key_version varchar(40) NOT NULL,
  account_number_fingerprint char(64) NOT NULL,
  account_number_last4 varchar(4) NOT NULL,
  is_default boolean NOT NULL DEFAULT false,
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(30) NOT NULL DEFAULT 'DRAFT',
  verification_evidence_ref varchar(240),
  verified_at timestamptz,
  verified_by varchar(160),
  approved_at timestamptz,
  approved_by varchar(160),
  approval_evidence_ref varchar(240),
  rejected_at timestamptz,
  rejected_by varchar(160),
  rejection_reason varchar(500),
  rejection_evidence_ref varchar(240),
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
  UNIQUE (tenant_id, id, employer_bank_account_id),
  UNIQUE (tenant_id, employer_bank_account_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(bank_name) <> ''),
  CHECK (branch_name IS NULL OR btrim(branch_name) <> ''),
  CHECK (routing_code IS NULL OR btrim(routing_code) <> ''),
  CHECK (btrim(account_holder_name) <> ''),
  CHECK (currency_code ~ '^[A-Z]{3}$'),
  CHECK (octet_length(account_number_ciphertext) >= 20),
  CHECK (octet_length(account_number_iv) = 12),
  CHECK (btrim(encryption_key_version) <> ''),
  CHECK (account_number_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (account_number_last4 ~ '^[A-Z0-9]{4}$'),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN (
    'DRAFT',
    'PENDING_VERIFICATION',
    'VERIFIED',
    'APPROVAL_PENDING',
    'ACTIVE',
    'REJECTED',
    'SUSPENDED',
    'EXPIRED',
    'SUPERSEDED'
  )),
  CHECK (
    lifecycle_status NOT IN (
      'VERIFIED', 'APPROVAL_PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED'
    )
    OR (
      verification_evidence_ref IS NOT NULL
      AND btrim(verification_evidence_ref) <> ''
      AND verified_at IS NOT NULL
      AND verified_by IS NOT NULL
      AND btrim(verified_by) <> ''
    )
  ),
  CHECK (
    lifecycle_status NOT IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')
    OR (
      approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
      AND approval_evidence_ref IS NOT NULL
      AND btrim(approval_evidence_ref) <> ''
    )
  ),
  CHECK (
    lifecycle_status <> 'REJECTED'
    OR (
      rejected_at IS NOT NULL
      AND rejected_by IS NOT NULL
      AND btrim(rejected_by) <> ''
      AND rejection_reason IS NOT NULL
      AND length(btrim(rejection_reason)) BETWEEN 1 AND 500
      AND rejection_evidence_ref IS NOT NULL
      AND btrim(rejection_evidence_ref) <> ''
    )
  ),
  CHECK (
    lifecycle_status = 'REJECTED'
    OR (
      rejected_at IS NULL
      AND rejected_by IS NULL
      AND rejection_reason IS NULL
      AND rejection_evidence_ref IS NULL
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
  CHECK (
    lifecycle_status = 'SUSPENDED'
    OR (
      suspended_at IS NULL
      AND suspended_by IS NULL
      AND suspension_reason IS NULL
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  CONSTRAINT employer_bank_account_version_identity_fk
    FOREIGN KEY (tenant_id, employer_bank_account_id, owner_key)
    REFERENCES organisation.employer_bank_account(tenant_id, id, owner_key),
  CONSTRAINT employer_bank_account_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      employer_bank_account_id
    )
    REFERENCES organisation.employer_bank_account_version(
      tenant_id,
      id,
      employer_bank_account_id
    )
);

ALTER TABLE organisation.employer_bank_account_version
  ADD CONSTRAINT employer_bank_account_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    employer_bank_account_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

ALTER TABLE organisation.employer_bank_account_version
  ADD CONSTRAINT employer_bank_account_default_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    owner_key WITH =,
    currency_code WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE' AND is_default);

ALTER TABLE organisation.employer_bank_account_version
  ADD CONSTRAINT employer_bank_account_fingerprint_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    owner_key WITH =,
    currency_code WITH =,
    account_number_fingerprint WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

CREATE UNIQUE INDEX employer_bank_account_one_successor_uk
  ON organisation.employer_bank_account_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX employer_bank_account_version_current_ix
  ON organisation.employer_bank_account_version(
    tenant_id,
    employer_bank_account_id,
    effective_from DESC
  );

CREATE INDEX employer_bank_account_owner_currency_ix
  ON organisation.employer_bank_account_version(
    tenant_id,
    owner_key,
    currency_code,
    lifecycle_status,
    effective_from DESC
  );

CREATE INDEX employer_bank_account_fingerprint_ix
  ON organisation.employer_bank_account_version(
    tenant_id,
    owner_key,
    currency_code,
    account_number_fingerprint
  );


CREATE TABLE organisation.authorised_signatory (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  owner_kind varchar(30) NOT NULL,
  legal_entity_id uuid,
  payroll_statutory_unit_id uuid,
  owner_key varchar(80)
    GENERATED ALWAYS AS (
      CASE owner_kind
        WHEN 'LEGAL_ENTITY'
          THEN 'LEGAL_ENTITY:' || legal_entity_id::text
        WHEN 'PAYROLL_STATUTORY_UNIT'
          THEN 'PAYROLL_STATUTORY_UNIT:' || payroll_statutory_unit_id::text
        ELSE NULL
      END
    ) STORED,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  UNIQUE (tenant_id, id, owner_key),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (owner_kind IN ('LEGAL_ENTITY', 'PAYROLL_STATUTORY_UNIT')),
  CHECK (
    (owner_kind = 'LEGAL_ENTITY'
      AND legal_entity_id IS NOT NULL
      AND payroll_statutory_unit_id IS NULL)
    OR
    (owner_kind = 'PAYROLL_STATUTORY_UNIT'
      AND legal_entity_id IS NULL
      AND payroll_statutory_unit_id IS NOT NULL)
  ),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, legal_entity_id)
    REFERENCES organisation.legal_entity(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_statutory_unit_id)
    REFERENCES organisation.payroll_statutory_unit(tenant_id, id)
);

CREATE TABLE organisation.authorised_signatory_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  authorised_signatory_id uuid NOT NULL,
  owner_key varchar(80) NOT NULL,
  version_sequence integer NOT NULL,
  full_name varchar(160) NOT NULL,
  designation varchar(120),
  authority_reference varchar(240) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  lifecycle_status varchar(30) NOT NULL DEFAULT 'DRAFT',
  verification_evidence_ref varchar(240),
  verified_at timestamptz,
  verified_by varchar(160),
  approved_at timestamptz,
  approved_by varchar(160),
  approval_evidence_ref varchar(240),
  rejected_at timestamptz,
  rejected_by varchar(160),
  rejection_reason varchar(500),
  rejection_evidence_ref varchar(240),
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
  UNIQUE (tenant_id, id, authorised_signatory_id),
  UNIQUE (tenant_id, authorised_signatory_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(full_name) <> ''),
  CHECK (designation IS NULL OR btrim(designation) <> ''),
  CHECK (btrim(authority_reference) <> ''),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (lifecycle_status IN (
    'DRAFT',
    'PENDING_VERIFICATION',
    'VERIFIED',
    'APPROVAL_PENDING',
    'ACTIVE',
    'REJECTED',
    'SUSPENDED',
    'EXPIRED',
    'SUPERSEDED'
  )),
  CHECK (
    lifecycle_status NOT IN (
      'VERIFIED', 'APPROVAL_PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED'
    )
    OR (
      verification_evidence_ref IS NOT NULL
      AND btrim(verification_evidence_ref) <> ''
      AND verified_at IS NOT NULL
      AND verified_by IS NOT NULL
      AND btrim(verified_by) <> ''
    )
  ),
  CHECK (
    lifecycle_status NOT IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')
    OR (
      approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
      AND approval_evidence_ref IS NOT NULL
      AND btrim(approval_evidence_ref) <> ''
    )
  ),
  CHECK (
    lifecycle_status <> 'REJECTED'
    OR (
      rejected_at IS NOT NULL
      AND rejected_by IS NOT NULL
      AND btrim(rejected_by) <> ''
      AND rejection_reason IS NOT NULL
      AND length(btrim(rejection_reason)) BETWEEN 1 AND 500
      AND rejection_evidence_ref IS NOT NULL
      AND btrim(rejection_evidence_ref) <> ''
    )
  ),
  CHECK (
    lifecycle_status = 'REJECTED'
    OR (
      rejected_at IS NULL
      AND rejected_by IS NULL
      AND rejection_reason IS NULL
      AND rejection_evidence_ref IS NULL
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
  CHECK (
    lifecycle_status = 'SUSPENDED'
    OR (
      suspended_at IS NULL
      AND suspended_by IS NULL
      AND suspension_reason IS NULL
    )
  ),
  CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> id),
  CHECK (btrim(created_by) <> ''),
  CHECK (btrim(updated_by) <> ''),
  CONSTRAINT authorised_signatory_version_identity_fk
    FOREIGN KEY (tenant_id, authorised_signatory_id, owner_key)
    REFERENCES organisation.authorised_signatory(tenant_id, id, owner_key),
  CONSTRAINT authorised_signatory_version_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      authorised_signatory_id
    )
    REFERENCES organisation.authorised_signatory_version(
      tenant_id,
      id,
      authorised_signatory_id
    )
);

ALTER TABLE organisation.authorised_signatory_version
  ADD CONSTRAINT authorised_signatory_active_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    authorised_signatory_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status = 'ACTIVE');

CREATE UNIQUE INDEX authorised_signatory_one_successor_uk
  ON organisation.authorised_signatory_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX authorised_signatory_version_current_ix
  ON organisation.authorised_signatory_version(
    tenant_id,
    authorised_signatory_id,
    effective_from DESC
  );

CREATE INDEX authorised_signatory_owner_ix
  ON organisation.authorised_signatory_version(
    tenant_id,
    owner_key,
    lifecycle_status,
    effective_from DESC
  );

CREATE TABLE organisation.authorised_signatory_scope (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  authorised_signatory_id uuid NOT NULL,
  authorised_signatory_version_id uuid NOT NULL,
  purpose_code varchar(60) NOT NULL,
  currency_code char(3),
  maximum_amount numeric(19,4),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE NULLS NOT DISTINCT (
    tenant_id,
    authorised_signatory_version_id,
    purpose_code,
    currency_code
  ),
  CHECK (purpose_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'),
  CHECK (maximum_amount IS NULL OR maximum_amount > 0),
  CHECK (maximum_amount IS NULL OR currency_code IS NOT NULL),
  CHECK (btrim(created_by) <> ''),
  CONSTRAINT authorised_signatory_scope_version_fk
    FOREIGN KEY (
      tenant_id,
      authorised_signatory_version_id,
      authorised_signatory_id
    )
    REFERENCES organisation.authorised_signatory_version(
      tenant_id,
      id,
      authorised_signatory_id
    )
);

CREATE INDEX authorised_signatory_scope_evaluation_ix
  ON organisation.authorised_signatory_scope(
    tenant_id,
    authorised_signatory_version_id,
    purpose_code,
    currency_code
  );

CREATE OR REPLACE FUNCTION organisation.assert_banking_owner_active()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  owner_status varchar(24);
BEGIN
  IF NEW.owner_kind = 'LEGAL_ENTITY' THEN
    SELECT status
      INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.legal_entity_id;
  ELSE
    SELECT status
      INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.payroll_statutory_unit_id;
  END IF;

  IF owner_status IS NULL THEN
    RAISE EXCEPTION 'banking configuration owner does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'banking configuration owner must be active'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER employer_bank_account_owner_active
  BEFORE INSERT
  ON organisation.employer_bank_account
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_banking_owner_active();

CREATE TRIGGER authorised_signatory_owner_active
  BEFORE INSERT
  ON organisation.authorised_signatory
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_banking_owner_active();

CREATE OR REPLACE FUNCTION organisation.assert_employer_bank_account_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(24);
  identity_owner_key varchar(80);
  identity_owner_kind varchar(30);
  identity_legal_entity_id uuid;
  identity_psu_id uuid;
  owner_status varchar(24);
  prior_sequence integer;
  prior_effective_from date;
BEGIN
  SELECT
    status,
    owner_key,
    owner_kind,
    legal_entity_id,
    payroll_statutory_unit_id
    INTO
      identity_status,
      identity_owner_key,
      identity_owner_kind,
      identity_legal_entity_id,
      identity_psu_id
    FROM organisation.employer_bank_account
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.employer_bank_account_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'employer-bank-account identity does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired employer-bank-account cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF identity_owner_key IS DISTINCT FROM NEW.owner_key THEN
    RAISE EXCEPTION 'employer-bank-account version owner does not match identity'
      USING ERRCODE = '23514';
  END IF;

  IF identity_owner_kind = 'LEGAL_ENTITY' THEN
    SELECT status INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = NEW.tenant_id
       AND id = identity_legal_entity_id;
  ELSE
    SELECT status INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = NEW.tenant_id
       AND id = identity_psu_id;
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'employer-bank-account owner must be active'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.lifecycle_status <> 'DRAFT'
     OR NEW.verification_evidence_ref IS NOT NULL
     OR NEW.verified_at IS NOT NULL
     OR NEW.verified_by IS NOT NULL
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.approval_evidence_ref IS NOT NULL
     OR NEW.rejected_at IS NOT NULL
     OR NEW.rejected_by IS NOT NULL
     OR NEW.rejection_reason IS NOT NULL
     OR NEW.rejection_evidence_ref IS NOT NULL
     OR NEW.suspended_at IS NOT NULL
     OR NEW.suspended_by IS NOT NULL
     OR NEW.suspension_reason IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'employer-bank-account versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM organisation.employer_bank_account_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.employer_bank_account_id =
                NEW.employer_bank_account_id
       ) THEN
      RAISE EXCEPTION 'first employer-bank-account version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later employer-bank-account versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;

    SELECT version_sequence, effective_from
      INTO prior_sequence, prior_effective_from
      FROM organisation.employer_bank_account_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND employer_bank_account_id = NEW.employer_bank_account_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'employer-bank-account version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.effective_from < prior_effective_from THEN
      RAISE EXCEPTION 'employer-bank-account successor cannot start before its predecessor'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER employer_bank_account_version_dependencies
  BEFORE INSERT
  ON organisation.employer_bank_account_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_employer_bank_account_version();

CREATE OR REPLACE FUNCTION organisation.assert_authorised_signatory_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(24);
  identity_owner_key varchar(80);
  identity_owner_kind varchar(30);
  identity_legal_entity_id uuid;
  identity_psu_id uuid;
  owner_status varchar(24);
  prior_sequence integer;
  prior_effective_from date;
BEGIN
  SELECT
    status,
    owner_key,
    owner_kind,
    legal_entity_id,
    payroll_statutory_unit_id
    INTO
      identity_status,
      identity_owner_key,
      identity_owner_kind,
      identity_legal_entity_id,
      identity_psu_id
    FROM organisation.authorised_signatory
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.authorised_signatory_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'authorised-signatory identity does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired authorised signatory cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF identity_owner_key IS DISTINCT FROM NEW.owner_key THEN
    RAISE EXCEPTION 'authorised-signatory version owner does not match identity'
      USING ERRCODE = '23514';
  END IF;

  IF identity_owner_kind = 'LEGAL_ENTITY' THEN
    SELECT status INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = NEW.tenant_id
       AND id = identity_legal_entity_id;
  ELSE
    SELECT status INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = NEW.tenant_id
       AND id = identity_psu_id;
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'authorised-signatory owner must be active'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.lifecycle_status <> 'DRAFT'
     OR NEW.verification_evidence_ref IS NOT NULL
     OR NEW.verified_at IS NOT NULL
     OR NEW.verified_by IS NOT NULL
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.approval_evidence_ref IS NOT NULL
     OR NEW.rejected_at IS NOT NULL
     OR NEW.rejected_by IS NOT NULL
     OR NEW.rejection_reason IS NOT NULL
     OR NEW.rejection_evidence_ref IS NOT NULL
     OR NEW.suspended_at IS NOT NULL
     OR NEW.suspended_by IS NOT NULL
     OR NEW.suspension_reason IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'authorised-signatory versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM organisation.authorised_signatory_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.authorised_signatory_id =
                NEW.authorised_signatory_id
       ) THEN
      RAISE EXCEPTION 'first authorised-signatory version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later authorised-signatory versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;

    SELECT version_sequence, effective_from
      INTO prior_sequence, prior_effective_from
      FROM organisation.authorised_signatory_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND authorised_signatory_id = NEW.authorised_signatory_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'authorised-signatory version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.effective_from < prior_effective_from THEN
      RAISE EXCEPTION 'authorised-signatory successor cannot start before its predecessor'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER authorised_signatory_version_dependencies
  BEFORE INSERT
  ON organisation.authorised_signatory_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_authorised_signatory_version();

CREATE OR REPLACE FUNCTION organisation.assert_authorised_signatory_scope()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  version_status varchar(30);
BEGIN
  SELECT lifecycle_status
    INTO version_status
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.authorised_signatory_version_id
     AND authorised_signatory_id = NEW.authorised_signatory_id;

  IF version_status IS NULL THEN
    RAISE EXCEPTION 'authorised-signatory version does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF version_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'authority scopes may only be added while signatory version is draft'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER authorised_signatory_scope_dependencies
  BEFORE INSERT
  ON organisation.authorised_signatory_scope
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_authorised_signatory_scope();

CREATE OR REPLACE FUNCTION organisation.lock_employer_bank_account_identity(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  PERFORM 1
    FROM organisation.employer_bank_account
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

CREATE OR REPLACE FUNCTION organisation.lock_authorised_signatory_identity(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  PERFORM 1
    FROM organisation.authorised_signatory
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

CREATE OR REPLACE FUNCTION organisation.submit_employer_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.employer_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor <> maker THEN
    RAISE EXCEPTION 'bank-account maker must submit the draft'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'PENDING_VERIFICATION',
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.verify_employer_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_verified_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.employer_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'PENDING_VERIFICATION'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent bank-account verification evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'VERIFIED',
         verification_evidence_ref = p_evidence_ref,
         verified_at = p_verified_at,
         verified_by = p_actor,
         updated_at = p_verified_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'PENDING_VERIFICATION'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.request_employer_bank_account_approval(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  verifier varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT verified_by
    INTO verifier
    FROM organisation.employer_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor <> verifier THEN
    RAISE EXCEPTION 'bank-account verifier must request final approval'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'APPROVAL_PENDING',
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.activate_employer_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  verifier varchar(160);
  identity_id uuid;
  owner_kind_value varchar(30);
  legal_id uuid;
  psu_id uuid;
  owner_status varchar(24);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT
    version.created_by,
    version.verified_by,
    version.employer_bank_account_id,
    identity.owner_kind,
    identity.legal_entity_id,
    identity.payroll_statutory_unit_id
    INTO
      maker,
      verifier,
      identity_id,
      owner_kind_value,
      legal_id,
      psu_id
    FROM organisation.employer_bank_account_version version
    JOIN organisation.employer_bank_account identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.employer_bank_account_id
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.lifecycle_status = 'APPROVAL_PENDING'
     AND version.version_no = p_expected_version
   FOR UPDATE OF version, identity;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_actor = verifier
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent final bank-account approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  IF owner_kind_value = 'LEGAL_ENTITY' THEN
    SELECT status INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = p_tenant_id
       AND id = legal_id;
  ELSE
    SELECT status INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = p_tenant_id
       AND id = psu_id;
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'bank-account owner is no longer active'
      USING ERRCODE = '23514';
  END IF;

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'SUPERSEDED',
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND employer_bank_account_id = identity_id
     AND id <> p_version_id
     AND lifecycle_status = 'ACTIVE';

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'ACTIVE',
         approved_at = p_approved_at,
         approved_by = p_actor,
         approval_evidence_ref = p_evidence_ref,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'APPROVAL_PENDING'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE organisation.employer_bank_account
       SET status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = identity_id
       AND status = 'PENDING_APPROVAL';
  END IF;

  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.reject_employer_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_evidence_ref varchar,
  p_rejected_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.employer_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION', 'VERIFIED', 'APPROVAL_PENDING'
     )
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_reason IS NULL
     OR btrim(p_reason) = ''
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent bank-account rejection evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.employer_bank_account_version
     SET lifecycle_status = 'REJECTED',
         rejected_at = p_rejected_at,
         rejected_by = p_actor,
         rejection_reason = p_reason,
         rejection_evidence_ref = p_evidence_ref,
         updated_at = p_rejected_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION', 'VERIFIED', 'APPROVAL_PENDING'
     )
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.suspend_employer_bank_account_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_suspended_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.employer_bank_account_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'ACTIVE'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_reason IS NULL
     OR btrim(p_reason) = '' THEN
    RAISE EXCEPTION 'independent bank-account suspension actor and reason are required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.employer_bank_account_version
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

CREATE OR REPLACE FUNCTION organisation.submit_authorised_signatory_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor <> maker THEN
    RAISE EXCEPTION 'signatory maker must submit the draft'
      USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM organisation.authorised_signatory_scope scope
     WHERE scope.tenant_id = p_tenant_id
       AND scope.authorised_signatory_version_id = p_version_id
  ) THEN
    RAISE EXCEPTION 'signatory version requires at least one authority scope'
      USING ERRCODE = '23514';
  END IF;

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'PENDING_VERIFICATION',
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.verify_authorised_signatory_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_verified_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'PENDING_VERIFICATION'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent signatory verification evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'VERIFIED',
         verification_evidence_ref = p_evidence_ref,
         verified_at = p_verified_at,
         verified_by = p_actor,
         updated_at = p_verified_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'PENDING_VERIFICATION'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.request_authorised_signatory_approval(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  verifier varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT verified_by
    INTO verifier
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor <> verifier THEN
    RAISE EXCEPTION 'signatory verifier must request final approval'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'APPROVAL_PENDING',
         updated_at = p_changed_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'VERIFIED'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.activate_authorised_signatory_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  verifier varchar(160);
  identity_id uuid;
  owner_kind_value varchar(30);
  legal_id uuid;
  psu_id uuid;
  owner_status varchar(24);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT
    version.created_by,
    version.verified_by,
    version.authorised_signatory_id,
    identity.owner_kind,
    identity.legal_entity_id,
    identity.payroll_statutory_unit_id
    INTO
      maker,
      verifier,
      identity_id,
      owner_kind_value,
      legal_id,
      psu_id
    FROM organisation.authorised_signatory_version version
    JOIN organisation.authorised_signatory identity
      ON identity.tenant_id = version.tenant_id
     AND identity.id = version.authorised_signatory_id
   WHERE version.tenant_id = p_tenant_id
     AND version.id = p_version_id
     AND version.lifecycle_status = 'APPROVAL_PENDING'
     AND version.version_no = p_expected_version
   FOR UPDATE OF version, identity;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_actor = verifier
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent final signatory approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  IF owner_kind_value = 'LEGAL_ENTITY' THEN
    SELECT status INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = p_tenant_id
       AND id = legal_id;
  ELSE
    SELECT status INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = p_tenant_id
       AND id = psu_id;
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'signatory owner is no longer active'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM organisation.authorised_signatory_scope scope
     WHERE scope.tenant_id = p_tenant_id
       AND scope.authorised_signatory_version_id = p_version_id
  ) THEN
    RAISE EXCEPTION 'signatory version requires at least one authority scope'
      USING ERRCODE = '23514';
  END IF;

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'SUPERSEDED',
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND authorised_signatory_id = identity_id
     AND id <> p_version_id
     AND lifecycle_status = 'ACTIVE';

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'ACTIVE',
         approved_at = p_approved_at,
         approved_by = p_actor,
         approval_evidence_ref = p_evidence_ref,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'APPROVAL_PENDING'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE organisation.authorised_signatory
       SET status = 'ACTIVE',
           updated_at = p_approved_at,
           updated_by = p_actor,
           version_no = version_no + 1
     WHERE tenant_id = p_tenant_id
       AND id = identity_id
       AND status = 'PENDING_APPROVAL';
  END IF;

  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.reject_authorised_signatory_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_evidence_ref varchar,
  p_rejected_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION', 'VERIFIED', 'APPROVAL_PENDING'
     )
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_reason IS NULL
     OR btrim(p_reason) = ''
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent signatory rejection evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.authorised_signatory_version
     SET lifecycle_status = 'REJECTED',
         rejected_at = p_rejected_at,
         rejected_by = p_actor,
         rejection_reason = p_reason,
         rejection_evidence_ref = p_evidence_ref,
         updated_at = p_rejected_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION', 'VERIFIED', 'APPROVAL_PENDING'
     )
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION organisation.suspend_authorised_signatory_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_suspended_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by
    INTO maker
    FROM organisation.authorised_signatory_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'ACTIVE'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_reason IS NULL
     OR btrim(p_reason) = '' THEN
    RAISE EXCEPTION 'independent signatory suspension actor and reason are required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.authorised_signatory_version
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

DO $$
DECLARE
  relation_name text;
BEGIN
  FOREACH relation_name IN ARRAY ARRAY[
    'employer_bank_account',
    'employer_bank_account_version',
    'authorised_signatory',
    'authorised_signatory_version',
    'authorised_signatory_scope'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE organisation.%I ENABLE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'ALTER TABLE organisation.%I FORCE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON organisation.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      relation_name
    );
  END LOOP;
END $$;

REVOKE ALL ON FUNCTION organisation.assert_banking_owner_active()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.assert_employer_bank_account_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.assert_authorised_signatory_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.assert_authorised_signatory_scope()
  FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.lock_employer_bank_account_identity(
  uuid, uuid
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.lock_authorised_signatory_identity(
  uuid, uuid
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.submit_employer_bank_account_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.verify_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.request_employer_bank_account_approval(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.activate_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.reject_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.suspend_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.submit_authorised_signatory_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.verify_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.request_authorised_signatory_approval(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.activate_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.reject_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.suspend_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;

GRANT USAGE ON SCHEMA organisation TO payroll_app;

GRANT SELECT, INSERT
  ON organisation.employer_bank_account,
     organisation.employer_bank_account_version,
     organisation.authorised_signatory,
     organisation.authorised_signatory_version,
     organisation.authorised_signatory_scope
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON organisation.employer_bank_account,
     organisation.employer_bank_account_version,
     organisation.authorised_signatory,
     organisation.authorised_signatory_version,
     organisation.authorised_signatory_scope
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION organisation.lock_employer_bank_account_identity(
  uuid, uuid
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.lock_authorised_signatory_identity(
  uuid, uuid
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.submit_employer_bank_account_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.verify_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.request_employer_bank_account_approval(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.activate_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.reject_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.suspend_employer_bank_account_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.submit_authorised_signatory_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.verify_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.request_authorised_signatory_approval(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.activate_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.reject_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.suspend_authorised_signatory_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;

REVOKE CREATE ON SCHEMA organisation FROM payroll_app;

COMMENT ON TABLE organisation.employer_bank_account IS
  'Stable tenant-scoped employer funding-bank-account identity owned by a legal entity or PSU.';
COMMENT ON TABLE organisation.employer_bank_account_version IS
  'Effective-dated encrypted employer bank-account configuration and controlled lifecycle evidence.';
COMMENT ON COLUMN organisation.employer_bank_account_version.account_number_ciphertext IS
  'AES-256-GCM ciphertext only; plaintext account numbers must never be persisted.';
COMMENT ON COLUMN organisation.employer_bank_account_version.account_number_iv IS
  'Twelve-byte random AES-GCM IV generated by the application.';
COMMENT ON COLUMN organisation.employer_bank_account_version.encryption_key_version IS
  'Application key identifier used to select the decrypt key during rotation.';
COMMENT ON COLUMN organisation.employer_bank_account_version.account_number_fingerprint IS
  'Lowercase hex HMAC-SHA-256 fingerprint; active duplicates are blocked per tenant owner currency and effective period.';
COMMENT ON COLUMN organisation.employer_bank_account_version.account_number_last4 IS
  'Safe masking metadata derived from the canonical account number.';
COMMENT ON TABLE organisation.authorised_signatory IS
  'Stable legal-authority identity; does not itself grant application access.';
COMMENT ON TABLE organisation.authorised_signatory_version IS
  'Effective-dated authorised-signatory legal authority and controlled lifecycle evidence.';
COMMENT ON TABLE organisation.authorised_signatory_scope IS
  'Immutable purpose/currency/optional amount scope for an exact signatory version.';
