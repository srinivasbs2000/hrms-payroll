-- P5-JRF-01 G02-A jurisdiction and registration database foundation.
--
-- Forward-only from V033. V001-V033 remain immutable.
-- This migration introduces generic, jurisdiction-neutral work-location,
-- payroll-jurisdiction, resolution-evidence and statutory-registration
-- foundations. It intentionally excludes India-specific legal rates/formulas,
-- employee payroll-assignment changes, filing/remittance and payroll calculation.

CREATE TABLE organisation.payroll_jurisdiction (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE organisation.payroll_jurisdiction_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_jurisdiction_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  country_code char(2) NOT NULL,
  level_code varchar(30) NOT NULL,
  level_rank smallint NOT NULL,
  parent_jurisdiction_id uuid,
  parent_jurisdiction_version_id uuid,
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
  UNIQUE (tenant_id, id, payroll_jurisdiction_id),
  UNIQUE (tenant_id, payroll_jurisdiction_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (country_code ~ '^[A-Z]{2}$'),
  CHECK (level_code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
  CHECK (level_rank > 0),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (parent_jurisdiction_id IS NULL AND parent_jurisdiction_version_id IS NULL)
    OR
    (parent_jurisdiction_id IS NOT NULL AND parent_jurisdiction_version_id IS NOT NULL)
  ),
  CHECK (
    parent_jurisdiction_id IS NULL
    OR parent_jurisdiction_id <> payroll_jurisdiction_id
  ),
  CHECK (
    supersedes_version_id IS NULL
    OR supersedes_version_id <> id
  ),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  FOREIGN KEY (tenant_id, payroll_jurisdiction_id)
    REFERENCES organisation.payroll_jurisdiction(tenant_id, id),
  CONSTRAINT payroll_jurisdiction_parent_version_fk
    FOREIGN KEY (
      tenant_id,
      parent_jurisdiction_version_id,
      parent_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    ),
  CONSTRAINT payroll_jurisdiction_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      payroll_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    )
);

ALTER TABLE organisation.payroll_jurisdiction_version
  ADD CONSTRAINT payroll_jurisdiction_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    payroll_jurisdiction_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX payroll_jurisdiction_one_successor_uk
  ON organisation.payroll_jurisdiction_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX payroll_jurisdiction_version_current_ix
  ON organisation.payroll_jurisdiction_version(
    tenant_id,
    payroll_jurisdiction_id,
    effective_from DESC
  );

CREATE TABLE organisation.work_location (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE organisation.work_location_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  work_location_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  establishment_version_id uuid,
  payroll_jurisdiction_id uuid NOT NULL,
  payroll_jurisdiction_version_id uuid NOT NULL,
  address_line1 varchar(200),
  address_line2 varchar(200),
  locality varchar(120),
  state_code varchar(40),
  postal_code varchar(24),
  country_code char(2) NOT NULL,
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
  UNIQUE (tenant_id, id, work_location_id),
  UNIQUE (tenant_id, work_location_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (country_code ~ '^[A-Z]{2}$'),
  CHECK (state_code IS NULL OR btrim(state_code) <> ''),
  CHECK (postal_code IS NULL OR btrim(postal_code) <> ''),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    supersedes_version_id IS NULL
    OR supersedes_version_id <> id
  ),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  FOREIGN KEY (tenant_id, work_location_id)
    REFERENCES organisation.work_location(tenant_id, id),
  FOREIGN KEY (tenant_id, establishment_version_id)
    REFERENCES organisation.establishment_version(tenant_id, id),
  CONSTRAINT work_location_jurisdiction_version_fk
    FOREIGN KEY (
      tenant_id,
      payroll_jurisdiction_version_id,
      payroll_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    ),
  CONSTRAINT work_location_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      work_location_id
    )
    REFERENCES organisation.work_location_version(
      tenant_id,
      id,
      work_location_id
    )
);

ALTER TABLE organisation.work_location_version
  ADD CONSTRAINT work_location_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    work_location_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX work_location_one_successor_uk
  ON organisation.work_location_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX work_location_version_current_ix
  ON organisation.work_location_version(
    tenant_id,
    work_location_id,
    effective_from DESC
  );

CREATE TABLE organisation.jurisdiction_resolution_override (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  target_kind varchar(24) NOT NULL,
  work_location_version_id uuid,
  establishment_version_id uuid,
  target_key varchar(80)
    GENERATED ALWAYS AS (
      CASE target_kind
        WHEN 'WORK_LOCATION'
          THEN 'WORK_LOCATION:' || work_location_version_id::text
        WHEN 'ESTABLISHMENT'
          THEN 'ESTABLISHMENT:' || establishment_version_id::text
        ELSE NULL
      END
    ) STORED,
  payroll_jurisdiction_id uuid NOT NULL,
  payroll_jurisdiction_version_id uuid NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
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
  CHECK (target_kind IN ('WORK_LOCATION', 'ESTABLISHMENT')),
  CHECK (
    (target_kind = 'WORK_LOCATION'
      AND work_location_version_id IS NOT NULL
      AND establishment_version_id IS NULL)
    OR
    (target_kind = 'ESTABLISHMENT'
      AND work_location_version_id IS NULL
      AND establishment_version_id IS NOT NULL)
  ),
  CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  FOREIGN KEY (tenant_id, work_location_version_id)
    REFERENCES organisation.work_location_version(tenant_id, id),
  FOREIGN KEY (tenant_id, establishment_version_id)
    REFERENCES organisation.establishment_version(tenant_id, id),
  CONSTRAINT override_jurisdiction_version_fk
    FOREIGN KEY (
      tenant_id,
      payroll_jurisdiction_version_id,
      payroll_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    )
);

ALTER TABLE organisation.jurisdiction_resolution_override
  ADD CONSTRAINT jurisdiction_override_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    target_key WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE INDEX jurisdiction_override_lookup_ix
  ON organisation.jurisdiction_resolution_override(
    tenant_id,
    target_kind,
    target_key,
    effective_from DESC
  );

CREATE TABLE organisation.jurisdiction_resolution_evidence (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  as_of_date date NOT NULL,
  work_location_version_id uuid,
  establishment_version_id uuid,
  override_id uuid,
  resolved_jurisdiction_id uuid,
  resolved_jurisdiction_version_id uuid,
  resolution_source varchar(30) NOT NULL,
  resolution_status varchar(20) NOT NULL,
  input_fingerprint varchar(64) NOT NULL,
  result_fingerprint varchar(64) NOT NULL,
  finding_codes jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  CHECK (resolution_source IN (
    'EXPLICIT_OVERRIDE',
    'WORK_LOCATION',
    'ESTABLISHMENT_FALLBACK',
    'NONE'
  )),
  CHECK (resolution_status IN ('RESOLVED', 'UNRESOLVED', 'CONFLICT')),
  CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (result_fingerprint ~ '^[0-9a-f]{64}$'),
  CHECK (jsonb_typeof(finding_codes) = 'array'),
  CHECK (
    (resolution_status = 'RESOLVED'
      AND resolved_jurisdiction_id IS NOT NULL
      AND resolved_jurisdiction_version_id IS NOT NULL)
    OR
    (resolution_status <> 'RESOLVED'
      AND resolved_jurisdiction_id IS NULL
      AND resolved_jurisdiction_version_id IS NULL)
  ),
  CHECK (
    (resolution_source = 'EXPLICIT_OVERRIDE' AND override_id IS NOT NULL)
    OR
    (resolution_source <> 'EXPLICIT_OVERRIDE' AND override_id IS NULL)
  ),
  FOREIGN KEY (tenant_id, work_location_version_id)
    REFERENCES organisation.work_location_version(tenant_id, id),
  FOREIGN KEY (tenant_id, establishment_version_id)
    REFERENCES organisation.establishment_version(tenant_id, id),
  FOREIGN KEY (tenant_id, override_id)
    REFERENCES organisation.jurisdiction_resolution_override(tenant_id, id),
  CONSTRAINT evidence_jurisdiction_version_fk
    FOREIGN KEY (
      tenant_id,
      resolved_jurisdiction_version_id,
      resolved_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    )
);

CREATE INDEX jurisdiction_resolution_evidence_lookup_ix
  ON organisation.jurisdiction_resolution_evidence(
    tenant_id,
    as_of_date DESC,
    resolution_status
  );

CREATE TABLE statutory.registration_type (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  code varchar(60) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, code),
  CHECK (code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RETIRED')),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id)
);

CREATE TABLE statutory.registration_type_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  registration_type_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  name varchar(160) NOT NULL,
  obligation_code varchar(60) NOT NULL,
  authority_code varchar(60) NOT NULL,
  jurisdiction_level_code varchar(30) NOT NULL,
  identifier_pattern varchar(240),
  identifier_pattern_dialect varchar(30) NOT NULL DEFAULT 'JAVA_REGEX_V1',
  identifier_case_policy varchar(20) NOT NULL DEFAULT 'UPPER',
  parent_required boolean NOT NULL DEFAULT false,
  parent_registration_type_id uuid,
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
  UNIQUE (tenant_id, id, registration_type_id),
  UNIQUE (tenant_id, registration_type_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (btrim(name) <> ''),
  CHECK (obligation_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (jurisdiction_level_code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
  CHECK (
    identifier_pattern IS NULL
    OR length(btrim(identifier_pattern)) BETWEEN 1 AND 240
  ),
  CHECK (identifier_pattern_dialect = 'JAVA_REGEX_V1'),
  CHECK (identifier_case_policy IN ('UPPER', 'PRESERVE')),
  CHECK (
    NOT parent_required
    OR parent_registration_type_id IS NOT NULL
  ),
  CHECK (
    parent_registration_type_id IS NULL
    OR parent_registration_type_id <> registration_type_id
  ),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    supersedes_version_id IS NULL
    OR supersedes_version_id <> id
  ),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR
    (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  FOREIGN KEY (tenant_id, registration_type_id)
    REFERENCES statutory.registration_type(tenant_id, id),
  FOREIGN KEY (tenant_id, parent_registration_type_id)
    REFERENCES statutory.registration_type(tenant_id, id),
  CONSTRAINT registration_type_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      registration_type_id
    )
    REFERENCES statutory.registration_type_version(
      tenant_id,
      id,
      registration_type_id
    )
);

ALTER TABLE statutory.registration_type_version
  ADD CONSTRAINT registration_type_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    registration_type_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX registration_type_one_successor_uk
  ON statutory.registration_type_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX registration_type_version_current_ix
  ON statutory.registration_type_version(
    tenant_id,
    registration_type_id,
    effective_from DESC
  );

CREATE TABLE statutory.registration_type_owner_kind (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  registration_type_id uuid NOT NULL,
  registration_type_version_id uuid NOT NULL,
  owner_kind varchar(30) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    registration_type_version_id,
    owner_kind
  ),
  CHECK (owner_kind IN (
    'LEGAL_ENTITY',
    'PAYROLL_STATUTORY_UNIT',
    'ESTABLISHMENT'
  )),
  CONSTRAINT registration_type_owner_version_fk
    FOREIGN KEY (
      tenant_id,
      registration_type_version_id,
      registration_type_id
    )
    REFERENCES statutory.registration_type_version(
      tenant_id,
      id,
      registration_type_id
    )
);

CREATE INDEX registration_type_owner_lookup_ix
  ON statutory.registration_type_owner_kind(
    tenant_id,
    registration_type_version_id,
    owner_kind
  );

CREATE TABLE statutory.registration (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  registration_type_id uuid NOT NULL,
  reference_code varchar(60) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, registration_type_id),
  UNIQUE (tenant_id, reference_code),
  CHECK (reference_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  FOREIGN KEY (tenant_id, registration_type_id)
    REFERENCES statutory.registration_type(tenant_id, id)
);

CREATE TABLE statutory.registration_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  registration_id uuid NOT NULL,
  registration_type_id uuid NOT NULL,
  registration_type_version_id uuid NOT NULL,
  version_sequence integer NOT NULL,
  identifier_raw varchar(160) NOT NULL,
  identifier_normalized varchar(160) NOT NULL,
  owner_kind varchar(30) NOT NULL,
  legal_entity_id uuid,
  payroll_statutory_unit_id uuid,
  establishment_id uuid,
  owner_key varchar(80)
    GENERATED ALWAYS AS (
      CASE owner_kind
        WHEN 'LEGAL_ENTITY'
          THEN 'LEGAL_ENTITY:' || legal_entity_id::text
        WHEN 'PAYROLL_STATUTORY_UNIT'
          THEN 'PAYROLL_STATUTORY_UNIT:' || payroll_statutory_unit_id::text
        WHEN 'ESTABLISHMENT'
          THEN 'ESTABLISHMENT:' || establishment_id::text
        ELSE NULL
      END
    ) STORED,
  payroll_jurisdiction_id uuid NOT NULL,
  payroll_jurisdiction_version_id uuid NOT NULL,
  parent_registration_id uuid,
  parent_registration_version_id uuid,
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
  authority_reference varchar(240),
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
  UNIQUE (tenant_id, id, registration_id),
  UNIQUE (tenant_id, registration_id, version_sequence),
  CHECK (version_sequence > 0),
  CHECK (length(btrim(identifier_raw)) BETWEEN 1 AND 160),
  CHECK (length(btrim(identifier_normalized)) BETWEEN 1 AND 160),
  CHECK (owner_kind IN (
    'LEGAL_ENTITY',
    'PAYROLL_STATUTORY_UNIT',
    'ESTABLISHMENT'
  )),
  CHECK (
    num_nonnulls(
      legal_entity_id,
      payroll_statutory_unit_id,
      establishment_id
    ) = 1
  ),
  CHECK (
    (owner_kind = 'LEGAL_ENTITY'
      AND legal_entity_id IS NOT NULL
      AND payroll_statutory_unit_id IS NULL
      AND establishment_id IS NULL)
    OR
    (owner_kind = 'PAYROLL_STATUTORY_UNIT'
      AND legal_entity_id IS NULL
      AND payroll_statutory_unit_id IS NOT NULL
      AND establishment_id IS NULL)
    OR
    (owner_kind = 'ESTABLISHMENT'
      AND legal_entity_id IS NULL
      AND payroll_statutory_unit_id IS NULL
      AND establishment_id IS NOT NULL)
  ),
  CHECK (
    (parent_registration_id IS NULL AND parent_registration_version_id IS NULL)
    OR
    (parent_registration_id IS NOT NULL AND parent_registration_version_id IS NOT NULL)
  ),
  CHECK (
    parent_registration_id IS NULL
    OR parent_registration_id <> registration_id
  ),
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
      'VERIFIED',
      'APPROVAL_PENDING',
      'ACTIVE',
      'SUSPENDED',
      'EXPIRED'
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
      AND authority_reference IS NOT NULL
      AND btrim(authority_reference) <> ''
    )
  ),
  CHECK (
    lifecycle_status = 'REJECTED'
    OR (
      rejected_at IS NULL
      AND rejected_by IS NULL
      AND rejection_reason IS NULL
      AND rejection_evidence_ref IS NULL
      AND authority_reference IS NULL
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
  CHECK (
    supersedes_version_id IS NULL
    OR supersedes_version_id <> id
  ),
  CONSTRAINT registration_version_identity_fk
    FOREIGN KEY (
      tenant_id,
      registration_id,
      registration_type_id
    )
    REFERENCES statutory.registration(
      tenant_id,
      id,
      registration_type_id
    ),
  CONSTRAINT registration_version_type_fk
    FOREIGN KEY (
      tenant_id,
      registration_type_version_id,
      registration_type_id
    )
    REFERENCES statutory.registration_type_version(
      tenant_id,
      id,
      registration_type_id
    ),
  FOREIGN KEY (tenant_id, legal_entity_id)
    REFERENCES organisation.legal_entity(tenant_id, id),
  FOREIGN KEY (tenant_id, payroll_statutory_unit_id)
    REFERENCES organisation.payroll_statutory_unit(tenant_id, id),
  FOREIGN KEY (tenant_id, establishment_id)
    REFERENCES organisation.establishment(tenant_id, id),
  CONSTRAINT registration_jurisdiction_version_fk
    FOREIGN KEY (
      tenant_id,
      payroll_jurisdiction_version_id,
      payroll_jurisdiction_id
    )
    REFERENCES organisation.payroll_jurisdiction_version(
      tenant_id,
      id,
      payroll_jurisdiction_id
    ),
  CONSTRAINT registration_parent_version_fk
    FOREIGN KEY (
      tenant_id,
      parent_registration_version_id,
      parent_registration_id
    )
    REFERENCES statutory.registration_version(
      tenant_id,
      id,
      registration_id
    ),
  CONSTRAINT registration_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_version_id,
      registration_id
    )
    REFERENCES statutory.registration_version(
      tenant_id,
      id,
      registration_id
    )
);

ALTER TABLE statutory.registration_version
  ADD CONSTRAINT registration_owner_period_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    registration_type_id WITH =,
    owner_key WITH =,
    payroll_jurisdiction_id WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status IN ('ACTIVE', 'SUSPENDED'));

ALTER TABLE statutory.registration_version
  ADD CONSTRAINT registration_identifier_period_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    registration_type_id WITH =,
    identifier_normalized WITH =,
    daterange(effective_from, effective_to, '[)') WITH &&
  ) WHERE (lifecycle_status IN ('ACTIVE', 'SUSPENDED'));

CREATE UNIQUE INDEX registration_one_successor_uk
  ON statutory.registration_version(
    tenant_id,
    supersedes_version_id
  )
  WHERE supersedes_version_id IS NOT NULL;

CREATE INDEX registration_version_current_ix
  ON statutory.registration_version(
    tenant_id,
    registration_id,
    effective_from DESC
  );

CREATE INDEX registration_readiness_lookup_ix
  ON statutory.registration_version(
    tenant_id,
    registration_type_id,
    owner_key,
    payroll_jurisdiction_id,
    lifecycle_status,
    effective_from DESC
  );

CREATE OR REPLACE FUNCTION organisation.assert_payroll_jurisdiction_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
  parent_level_rank smallint;
  parent_country char(2);
  parent_status varchar(20);
  parent_from date;
  parent_to date;
BEGIN
  SELECT status
    INTO identity_status
    FROM organisation.payroll_jurisdiction
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payroll_jurisdiction_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'payroll-jurisdiction identity does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired payroll-jurisdiction identity cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'INSERT'
     AND (
       NEW.approval_status <> 'DRAFT'
       OR NEW.approved_at IS NOT NULL
       OR NEW.approved_by IS NOT NULL
       OR NEW.version_no <> 0
     ) THEN
    RAISE EXCEPTION 'payroll-jurisdiction versions must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM organisation.payroll_jurisdiction_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.payroll_jurisdiction_id = NEW.payroll_jurisdiction_id
       ) THEN
      RAISE EXCEPTION 'first payroll-jurisdiction version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    IF NEW.supersedes_version_id IS NULL THEN
      RAISE EXCEPTION 'later payroll-jurisdiction versions must supersede the prior version'
        USING ERRCODE = '23514';
    END IF;

    SELECT version_sequence
      INTO prior_sequence
      FROM organisation.payroll_jurisdiction_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND payroll_jurisdiction_id = NEW.payroll_jurisdiction_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'payroll-jurisdiction version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  IF NEW.parent_jurisdiction_version_id IS NULL THEN
    IF NEW.level_rank <> 1 THEN
      RAISE EXCEPTION 'root payroll jurisdiction must use level rank 1'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    SELECT
      level_rank,
      country_code,
      approval_status,
      effective_from,
      effective_to
      INTO
        parent_level_rank,
        parent_country,
        parent_status,
        parent_from,
        parent_to
      FROM organisation.payroll_jurisdiction_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.parent_jurisdiction_version_id
       AND payroll_jurisdiction_id = NEW.parent_jurisdiction_id;

    IF parent_level_rank IS NULL THEN
      RAISE EXCEPTION 'parent payroll-jurisdiction version does not exist'
        USING ERRCODE = '23503';
    END IF;

    IF parent_status <> 'APPROVED'
       OR parent_country <> NEW.country_code
       OR parent_level_rank >= NEW.level_rank
       OR parent_from > NEW.effective_from
       OR (
         parent_to IS NOT NULL
         AND (
           NEW.effective_to IS NULL
           OR parent_to < NEW.effective_to
         )
       ) THEN
      RAISE EXCEPTION 'parent payroll jurisdiction does not cover the child version'
        USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
      WITH RECURSIVE ancestors AS (
        SELECT
          id,
          payroll_jurisdiction_id,
          parent_jurisdiction_version_id
        FROM organisation.payroll_jurisdiction_version
        WHERE tenant_id = NEW.tenant_id
          AND id = NEW.parent_jurisdiction_version_id

        UNION ALL

        SELECT
          parent.id,
          parent.payroll_jurisdiction_id,
          parent.parent_jurisdiction_version_id
        FROM organisation.payroll_jurisdiction_version parent
        JOIN ancestors child
          ON parent.tenant_id = NEW.tenant_id
         AND parent.id = child.parent_jurisdiction_version_id
      )
      SELECT 1
      FROM ancestors
      WHERE payroll_jurisdiction_id = NEW.payroll_jurisdiction_id
    ) THEN
      RAISE EXCEPTION 'payroll-jurisdiction hierarchy cannot contain cycles'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER payroll_jurisdiction_version_dependencies
  BEFORE INSERT
  ON organisation.payroll_jurisdiction_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_payroll_jurisdiction_version();

CREATE OR REPLACE FUNCTION organisation.assert_work_location_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
  jurisdiction_status varchar(20);
  jurisdiction_country char(2);
  jurisdiction_from date;
  jurisdiction_to date;
  establishment_status varchar(20);
  establishment_from date;
  establishment_to date;
BEGIN
  SELECT status
    INTO identity_status
    FROM organisation.work_location
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.work_location_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'work-location identity does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired work-location identity cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.approval_status <> 'DRAFT'
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'work-location versions must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM organisation.work_location_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.work_location_id = NEW.work_location_id
       ) THEN
      RAISE EXCEPTION 'first work-location version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    SELECT version_sequence
      INTO prior_sequence
      FROM organisation.work_location_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND work_location_id = NEW.work_location_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'work-location version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  SELECT
    approval_status,
    country_code,
    effective_from,
    effective_to
    INTO
      jurisdiction_status,
      jurisdiction_country,
      jurisdiction_from,
      jurisdiction_to
    FROM organisation.payroll_jurisdiction_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payroll_jurisdiction_version_id
     AND payroll_jurisdiction_id = NEW.payroll_jurisdiction_id;

  IF jurisdiction_status <> 'APPROVED'
     OR jurisdiction_country <> NEW.country_code
     OR jurisdiction_from > NEW.effective_from
     OR (
       jurisdiction_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR jurisdiction_to < NEW.effective_to
       )
     ) THEN
    RAISE EXCEPTION 'approved payroll jurisdiction must cover work-location dates'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.establishment_version_id IS NOT NULL THEN
    SELECT approval_status, effective_from, effective_to
      INTO establishment_status, establishment_from, establishment_to
      FROM organisation.establishment_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.establishment_version_id;

    IF establishment_status <> 'APPROVED'
       OR establishment_from > NEW.effective_from
       OR (
         establishment_to IS NOT NULL
         AND (
           NEW.effective_to IS NULL
           OR establishment_to < NEW.effective_to
         )
       ) THEN
      RAISE EXCEPTION 'approved establishment must cover work-location dates'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER work_location_version_dependencies
  BEFORE INSERT
  ON organisation.work_location_version
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_work_location_version();

CREATE OR REPLACE FUNCTION organisation.assert_jurisdiction_override()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  target_status varchar(20);
  target_from date;
  target_to date;
  jurisdiction_status varchar(20);
  jurisdiction_from date;
  jurisdiction_to date;
BEGIN
  IF NEW.approval_status <> 'DRAFT'
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'jurisdiction overrides must be inserted as drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.target_kind = 'WORK_LOCATION' THEN
    SELECT approval_status, effective_from, effective_to
      INTO target_status, target_from, target_to
      FROM organisation.work_location_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.work_location_version_id;
  ELSE
    SELECT approval_status, effective_from, effective_to
      INTO target_status, target_from, target_to
      FROM organisation.establishment_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.establishment_version_id;
  END IF;

  IF target_status <> 'APPROVED'
     OR target_from > NEW.effective_from
     OR (
       target_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR target_to < NEW.effective_to
       )
     ) THEN
    RAISE EXCEPTION 'approved override target must cover override dates'
      USING ERRCODE = '23514';
  END IF;

  SELECT approval_status, effective_from, effective_to
    INTO jurisdiction_status, jurisdiction_from, jurisdiction_to
    FROM organisation.payroll_jurisdiction_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payroll_jurisdiction_version_id
     AND payroll_jurisdiction_id = NEW.payroll_jurisdiction_id;

  IF jurisdiction_status <> 'APPROVED'
     OR jurisdiction_from > NEW.effective_from
     OR (
       jurisdiction_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR jurisdiction_to < NEW.effective_to
       )
     ) THEN
    RAISE EXCEPTION 'approved payroll jurisdiction must cover override dates'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER jurisdiction_override_dependencies
  BEFORE INSERT
  ON organisation.jurisdiction_resolution_override
  FOR EACH ROW
  EXECUTE FUNCTION organisation.assert_jurisdiction_override();

CREATE OR REPLACE FUNCTION statutory.assert_registration_type_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  identity_status varchar(24);
  prior_sequence integer;
BEGIN
  SELECT status
    INTO identity_status
    FROM statutory.registration_type
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.registration_type_id;

  IF identity_status IS NULL THEN
    RAISE EXCEPTION 'registration-type identity does not exist'
      USING ERRCODE = '23503';
  END IF;

  IF identity_status = 'RETIRED' THEN
    RAISE EXCEPTION 'retired registration type cannot accept versions'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.approval_status <> 'DRAFT'
     OR NEW.approved_at IS NOT NULL
     OR NEW.approved_by IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'registration-type versions must be inserted as new drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM statutory.registration_type_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.registration_type_id = NEW.registration_type_id
       ) THEN
      RAISE EXCEPTION 'first registration-type version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    SELECT version_sequence
      INTO prior_sequence
      FROM statutory.registration_type_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND registration_type_id = NEW.registration_type_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'registration-type version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER registration_type_version_dependencies
  BEFORE INSERT
  ON statutory.registration_type_version
  FOR EACH ROW
  EXECUTE FUNCTION statutory.assert_registration_type_version();

CREATE OR REPLACE FUNCTION statutory.assert_registration_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, organisation, platform AS $$
DECLARE
  identity_type_id uuid;
  prior_sequence integer;
  type_status varchar(20);
  type_parent_required boolean;
  type_parent_id uuid;
  type_from date;
  type_to date;
  jurisdiction_status varchar(20);
  jurisdiction_from date;
  jurisdiction_to date;
  owner_status varchar(24);
  parent_type_id uuid;
  parent_status varchar(30);
  parent_from date;
  parent_to date;
  parent_reg_jurisdiction_id uuid;
  parent_reg_jurisdiction_version_id uuid;
BEGIN
  SELECT registration_type_id
    INTO identity_type_id
    FROM statutory.registration
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.registration_id;

  IF identity_type_id IS DISTINCT FROM NEW.registration_type_id THEN
    RAISE EXCEPTION 'registration identity/type mismatch'
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
     OR NEW.authority_reference IS NOT NULL
     OR NEW.suspended_at IS NOT NULL
     OR NEW.suspended_by IS NOT NULL
     OR NEW.suspension_reason IS NOT NULL
     OR NEW.version_no <> 0 THEN
    RAISE EXCEPTION 'registration versions must be inserted as clean drafts'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.version_sequence = 1 THEN
    IF NEW.supersedes_version_id IS NOT NULL
       OR EXISTS (
         SELECT 1
           FROM statutory.registration_version existing
          WHERE existing.tenant_id = NEW.tenant_id
            AND existing.registration_id = NEW.registration_id
       ) THEN
      RAISE EXCEPTION 'first registration version must start a new chain'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    SELECT version_sequence
      INTO prior_sequence
      FROM statutory.registration_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.supersedes_version_id
       AND registration_id = NEW.registration_id;

    IF prior_sequence IS NULL
       OR NEW.version_sequence <> prior_sequence + 1 THEN
      RAISE EXCEPTION 'registration version sequence is invalid'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  SELECT
    approval_status,
    parent_required,
    parent_registration_type_id,
    effective_from,
    effective_to
    INTO
      type_status,
      type_parent_required,
      type_parent_id,
      type_from,
      type_to
    FROM statutory.registration_type_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.registration_type_version_id
     AND registration_type_id = NEW.registration_type_id;

  IF type_status <> 'APPROVED'
     OR type_from > NEW.effective_from
     OR (
       type_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR type_to < NEW.effective_to
       )
     ) THEN
    RAISE EXCEPTION 'approved registration type must cover registration dates'
      USING ERRCODE = '23514';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM statutory.registration_type_owner_kind owner_kind
     WHERE owner_kind.tenant_id = NEW.tenant_id
       AND owner_kind.registration_type_version_id =
           NEW.registration_type_version_id
       AND owner_kind.owner_kind = NEW.owner_kind
  ) THEN
    RAISE EXCEPTION 'owner kind is not allowed by the registration type'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.owner_kind = 'LEGAL_ENTITY' THEN
    SELECT status
      INTO owner_status
      FROM organisation.legal_entity
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.legal_entity_id;
  ELSIF NEW.owner_kind = 'PAYROLL_STATUTORY_UNIT' THEN
    SELECT status
      INTO owner_status
      FROM organisation.payroll_statutory_unit
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.payroll_statutory_unit_id;
  ELSE
    SELECT status
      INTO owner_status
      FROM organisation.establishment
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.establishment_id;
  END IF;

  IF owner_status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'registration owner must be an active organisation identity'
      USING ERRCODE = '23514';
  END IF;

  SELECT approval_status, effective_from, effective_to
    INTO jurisdiction_status, jurisdiction_from, jurisdiction_to
    FROM organisation.payroll_jurisdiction_version
   WHERE tenant_id = NEW.tenant_id
     AND id = NEW.payroll_jurisdiction_version_id
     AND payroll_jurisdiction_id = NEW.payroll_jurisdiction_id;

  IF jurisdiction_status <> 'APPROVED'
     OR jurisdiction_from > NEW.effective_from
     OR (
       jurisdiction_to IS NOT NULL
       AND (
         NEW.effective_to IS NULL
         OR jurisdiction_to < NEW.effective_to
       )
     ) THEN
    RAISE EXCEPTION 'approved payroll jurisdiction must cover registration dates'
      USING ERRCODE = '23514';
  END IF;

  IF type_parent_required AND NEW.parent_registration_version_id IS NULL THEN
    RAISE EXCEPTION 'registration type requires an active parent registration'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.parent_registration_version_id IS NOT NULL THEN
    SELECT
      registration_type_id,
      lifecycle_status,
      effective_from,
      effective_to,
      payroll_jurisdiction_id,
      payroll_jurisdiction_version_id
      INTO
        parent_type_id,
        parent_status,
        parent_from,
        parent_to,
        parent_reg_jurisdiction_id,
        parent_reg_jurisdiction_version_id
      FROM statutory.registration_version
     WHERE tenant_id = NEW.tenant_id
       AND id = NEW.parent_registration_version_id
       AND registration_id = NEW.parent_registration_id;

    IF parent_status <> 'ACTIVE'
       OR type_parent_id IS DISTINCT FROM parent_type_id
       OR parent_from > NEW.effective_from
       OR (
         parent_to IS NOT NULL
         AND (
           NEW.effective_to IS NULL
           OR parent_to < NEW.effective_to
         )
       ) THEN
      RAISE EXCEPTION 'parent registration is not compatible or effective'
        USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
      WITH RECURSIVE jurisdiction_lineage AS (
        SELECT
          id,
          payroll_jurisdiction_id,
          parent_jurisdiction_version_id
        FROM organisation.payroll_jurisdiction_version
        WHERE tenant_id = NEW.tenant_id
          AND id = NEW.payroll_jurisdiction_version_id

        UNION ALL

        SELECT
          parent.id,
          parent.payroll_jurisdiction_id,
          parent.parent_jurisdiction_version_id
        FROM organisation.payroll_jurisdiction_version parent
        JOIN jurisdiction_lineage child
          ON parent.tenant_id = NEW.tenant_id
         AND parent.id = child.parent_jurisdiction_version_id
      )
      SELECT 1
      FROM jurisdiction_lineage
      WHERE id = parent_reg_jurisdiction_version_id
        AND payroll_jurisdiction_id = parent_reg_jurisdiction_id
    ) THEN
      RAISE EXCEPTION
        'parent registration jurisdiction must be the same jurisdiction or an ancestor'
        USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
      WITH RECURSIVE ancestors AS (
        SELECT
          id,
          registration_id,
          parent_registration_version_id
        FROM statutory.registration_version
        WHERE tenant_id = NEW.tenant_id
          AND id = NEW.parent_registration_version_id

        UNION ALL

        SELECT
          parent.id,
          parent.registration_id,
          parent.parent_registration_version_id
        FROM statutory.registration_version parent
        JOIN ancestors child
          ON parent.tenant_id = NEW.tenant_id
         AND parent.id = child.parent_registration_version_id
      )
      SELECT 1
      FROM ancestors
      WHERE registration_id = NEW.registration_id
    ) THEN
      RAISE EXCEPTION 'registration hierarchy cannot contain cycles'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER registration_version_dependencies
  BEFORE INSERT
  ON statutory.registration_version
  FOR EACH ROW
  EXECUTE FUNCTION statutory.assert_registration_version();

CREATE OR REPLACE FUNCTION organisation.approve_payroll_jurisdiction_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  identity_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by, payroll_jurisdiction_id
    INTO maker, identity_id
    FROM organisation.payroll_jurisdiction_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker THEN
    RAISE EXCEPTION 'independent approver is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.payroll_jurisdiction_version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE organisation.payroll_jurisdiction
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

CREATE OR REPLACE FUNCTION organisation.approve_work_location_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  identity_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT created_by, work_location_id
    INTO maker, identity_id
    FROM organisation.work_location_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker THEN
    RAISE EXCEPTION 'independent approver is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.work_location_version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE organisation.work_location
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

CREATE OR REPLACE FUNCTION organisation.approve_jurisdiction_override(
  p_tenant_id uuid,
  p_override_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
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
    FROM organisation.jurisdiction_resolution_override
   WHERE tenant_id = p_tenant_id
     AND id = p_override_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker THEN
    RAISE EXCEPTION 'independent approver is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE organisation.jurisdiction_resolution_override
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_override_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.approve_registration_type_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  identity_id uuid;
  requires_parent boolean;
  parent_type_id uuid;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT
    created_by,
    registration_type_id,
    parent_required,
    parent_registration_type_id
    INTO maker, identity_id, requires_parent, parent_type_id
    FROM statutory.registration_type_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor = maker THEN
    RAISE EXCEPTION 'independent approver is required'
      USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM statutory.registration_type_owner_kind owner_kind
     WHERE owner_kind.tenant_id = p_tenant_id
       AND owner_kind.registration_type_version_id = p_version_id
  ) THEN
    RAISE EXCEPTION 'registration type requires at least one owner kind'
      USING ERRCODE = '23514';
  END IF;

  IF requires_parent
     AND NOT EXISTS (
       SELECT 1
         FROM statutory.registration_type parent_type
        WHERE parent_type.tenant_id = p_tenant_id
          AND parent_type.id = parent_type_id
          AND parent_type.status = 'ACTIVE'
     ) THEN
    RAISE EXCEPTION 'required parent registration type must be active'
      USING ERRCODE = '23514';
  END IF;

  UPDATE statutory.registration_type_version
     SET approval_status = 'APPROVED',
         approved_at = p_approved_at,
         approved_by = p_actor,
         updated_at = p_approved_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND approval_status = 'DRAFT'
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;

  IF affected = 1 THEN
    UPDATE statutory.registration_type
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

CREATE OR REPLACE FUNCTION statutory.submit_registration_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
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
    FROM statutory.registration_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'DRAFT'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' OR p_actor <> maker THEN
    RAISE EXCEPTION 'registration maker must submit the draft'
      USING ERRCODE = '42501';
  END IF;

  UPDATE statutory.registration_version
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

CREATE OR REPLACE FUNCTION statutory.verify_registration_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_verified_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
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
    FROM statutory.registration_version
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
    RAISE EXCEPTION 'independent verification evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE statutory.registration_version
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

CREATE OR REPLACE FUNCTION statutory.request_registration_approval(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_changed_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
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

  UPDATE statutory.registration_version
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

CREATE OR REPLACE FUNCTION statutory.activate_registration_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_evidence_ref varchar,
  p_approved_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, organisation, platform AS $$
DECLARE
  affected bigint;
  maker varchar(160);
  verifier varchar(160);
  type_version_id uuid;
  owner_kind_value varchar(30);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT
    created_by,
    verified_by,
    registration_type_version_id,
    owner_kind
    INTO
      maker,
      verifier,
      type_version_id,
      owner_kind_value
    FROM statutory.registration_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status = 'APPROVAL_PENDING'
     AND version_no = p_expected_version
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  IF p_actor IS NULL
     OR btrim(p_actor) = ''
     OR p_actor = maker
     OR p_actor = verifier
     OR p_evidence_ref IS NULL
     OR btrim(p_evidence_ref) = '' THEN
    RAISE EXCEPTION 'independent final approval evidence is required'
      USING ERRCODE = '42501';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM statutory.registration_type_version type_version
     WHERE type_version.tenant_id = p_tenant_id
       AND type_version.id = type_version_id
       AND type_version.approval_status = 'APPROVED'
  ) OR NOT EXISTS (
    SELECT 1
      FROM statutory.registration_type_owner_kind allowed_owner
     WHERE allowed_owner.tenant_id = p_tenant_id
       AND allowed_owner.registration_type_version_id = type_version_id
       AND allowed_owner.owner_kind = owner_kind_value
  ) THEN
    RAISE EXCEPTION 'registration configuration is no longer approved'
      USING ERRCODE = '23514';
  END IF;

  UPDATE statutory.registration_version
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
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.reject_registration_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_evidence_ref varchar,
  p_authority_reference varchar,
  p_rejected_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
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
    FROM statutory.registration_version
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION',
       'VERIFIED',
       'APPROVAL_PENDING'
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
     OR btrim(p_evidence_ref) = ''
     OR p_authority_reference IS NULL
     OR btrim(p_authority_reference) = '' THEN
    RAISE EXCEPTION 'independent rejection evidence is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE statutory.registration_version
     SET lifecycle_status = 'REJECTED',
         rejected_at = p_rejected_at,
         rejected_by = p_actor,
         rejection_reason = p_reason,
         rejection_evidence_ref = p_evidence_ref,
         authority_reference = p_authority_reference,
         updated_at = p_rejected_at,
         updated_by = p_actor,
         version_no = version_no + 1
   WHERE tenant_id = p_tenant_id
     AND id = p_version_id
     AND lifecycle_status IN (
       'PENDING_VERIFICATION',
       'VERIFIED',
       'APPROVAL_PENDING'
     )
     AND version_no = p_expected_version;

  GET DIAGNOSTICS affected = ROW_COUNT;
  RETURN affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.suspend_registration_version(
  p_tenant_id uuid,
  p_version_id uuid,
  p_expected_version bigint,
  p_actor varchar,
  p_reason varchar,
  p_suspended_at timestamptz
) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
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
    FROM statutory.registration_version
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
     OR p_reason IS NULL
     OR btrim(p_reason) = '' THEN
    RAISE EXCEPTION 'suspension actor and reason are required'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor = maker THEN
    RAISE EXCEPTION 'independent suspension actor is required'
      USING ERRCODE = '42501';
  END IF;

  UPDATE statutory.registration_version
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
    'payroll_jurisdiction',
    'payroll_jurisdiction_version',
    'work_location',
    'work_location_version',
    'jurisdiction_resolution_override',
    'jurisdiction_resolution_evidence'
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

DO $$
DECLARE
  relation_name text;
BEGIN
  FOREACH relation_name IN ARRAY ARRAY[
    'registration_type',
    'registration_type_version',
    'registration_type_owner_kind',
    'registration',
    'registration_version'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE statutory.%I ENABLE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'ALTER TABLE statutory.%I FORCE ROW LEVEL SECURITY',
      relation_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON statutory.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      relation_name
    );
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION organisation.lock_payroll_jurisdiction_identity(
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
    FROM organisation.payroll_jurisdiction
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

CREATE OR REPLACE FUNCTION organisation.lock_work_location_identity(
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
    FROM organisation.work_location
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

CREATE OR REPLACE FUNCTION statutory.lock_registration_type_identity(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  PERFORM 1
    FROM statutory.registration_type
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

CREATE OR REPLACE FUNCTION statutory.lock_registration_identity(
  p_tenant_id uuid,
  p_identity_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  PERFORM 1
    FROM statutory.registration
   WHERE tenant_id = p_tenant_id
     AND id = p_identity_id
   FOR UPDATE;

  RETURN FOUND;
END $$;

REVOKE ALL ON FUNCTION organisation.assert_payroll_jurisdiction_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.assert_work_location_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.assert_jurisdiction_override()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.assert_registration_type_version()
  FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.assert_registration_version()
  FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.lock_payroll_jurisdiction_identity(
  uuid, uuid
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.lock_work_location_identity(
  uuid, uuid
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.lock_registration_type_identity(
  uuid, uuid
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.lock_registration_identity(
  uuid, uuid
) FROM PUBLIC;

REVOKE ALL ON FUNCTION organisation.approve_payroll_jurisdiction_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.approve_work_location_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION organisation.approve_jurisdiction_override(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.approve_registration_type_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.submit_registration_version(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.verify_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.request_registration_approval(
  uuid, uuid, bigint, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.activate_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.reject_registration_version(
  uuid, uuid, bigint, varchar, varchar, varchar, varchar, timestamptz
) FROM PUBLIC;
REVOKE ALL ON FUNCTION statutory.suspend_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) FROM PUBLIC;

GRANT USAGE ON SCHEMA organisation, statutory TO payroll_app;

GRANT SELECT, INSERT
  ON organisation.payroll_jurisdiction,
     organisation.payroll_jurisdiction_version,
     organisation.work_location,
     organisation.work_location_version,
     organisation.jurisdiction_resolution_override,
     organisation.jurisdiction_resolution_evidence
  TO payroll_app;

GRANT SELECT, INSERT
  ON statutory.registration_type,
     statutory.registration_type_version,
     statutory.registration_type_owner_kind,
     statutory.registration,
     statutory.registration_version
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON organisation.payroll_jurisdiction,
     organisation.payroll_jurisdiction_version,
     organisation.work_location,
     organisation.work_location_version,
     organisation.jurisdiction_resolution_override,
     organisation.jurisdiction_resolution_evidence
  FROM payroll_app;

REVOKE UPDATE, DELETE
  ON statutory.registration_type,
     statutory.registration_type_version,
     statutory.registration_type_owner_kind,
     statutory.registration,
     statutory.registration_version
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION organisation.lock_payroll_jurisdiction_identity(
  uuid, uuid
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.lock_work_location_identity(
  uuid, uuid
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.lock_registration_type_identity(
  uuid, uuid
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.lock_registration_identity(
  uuid, uuid
) TO payroll_app;

GRANT EXECUTE ON FUNCTION organisation.approve_payroll_jurisdiction_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.approve_work_location_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION organisation.approve_jurisdiction_override(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.approve_registration_type_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.submit_registration_version(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.verify_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.request_registration_approval(
  uuid, uuid, bigint, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.activate_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.reject_registration_version(
  uuid, uuid, bigint, varchar, varchar, varchar, varchar, timestamptz
) TO payroll_app;
GRANT EXECUTE ON FUNCTION statutory.suspend_registration_version(
  uuid, uuid, bigint, varchar, varchar, timestamptz
) TO payroll_app;

REVOKE CREATE ON SCHEMA organisation, statutory FROM payroll_app;

COMMENT ON TABLE organisation.payroll_jurisdiction IS
  'Stable tenant-scoped payroll-jurisdiction identity.';
COMMENT ON TABLE organisation.payroll_jurisdiction_version IS
  'Approved effective-dated jurisdiction hierarchy with exact parent-version lineage.';
COMMENT ON TABLE organisation.work_location IS
  'Stable payroll-relevant work-location identity distinct from organisation hierarchy nodes.';
COMMENT ON TABLE organisation.work_location_version IS
  'Effective-dated work-location attributes and exact payroll-jurisdiction attribution.';
COMMENT ON TABLE organisation.jurisdiction_resolution_override IS
  'Controlled effective-dated explicit override above work-location and establishment fallback.';
COMMENT ON TABLE organisation.jurisdiction_resolution_evidence IS
  'Immutable decision evidence for resolved, unresolved or conflicting jurisdiction outcomes.';
COMMENT ON TABLE statutory.registration_type IS
  'Stable generic statutory-registration type identity.';
COMMENT ON TABLE statutory.registration_type_version IS
  'Effective-dated registration metadata, authority, jurisdiction level and parent requirements.';
COMMENT ON TABLE statutory.registration_type_owner_kind IS
  'Owner kinds allowed by an exact registration-type version.';
COMMENT ON TABLE statutory.registration IS
  'Stable tenant-scoped statutory-registration identity.';
COMMENT ON TABLE statutory.registration_version IS
  'Effective-dated registration identifier, owner, jurisdiction, parent and controlled lifecycle evidence.';
