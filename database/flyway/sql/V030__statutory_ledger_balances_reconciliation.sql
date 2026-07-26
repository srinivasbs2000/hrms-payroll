-- S4-04 statutory ledger, balance, correction and reconciliation foundation.
--
-- V029 persists immutable statutory evaluation evidence for an exact active
-- payroll calculation request. V030 posts that evidence into an append-only
-- statutory ledger. Recalculations are represented by exact reversal entries
-- followed by the replacement evaluation; corrections are signed delta entries.
-- PTD/YTD values are derived snapshots, never mutable source balances.
-- Remittance summaries are preparation evidence only: no filing, payment,
-- acknowledgement or authority-specific return semantics are introduced.

CREATE TABLE statutory.statutory_balance_year (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  balance_year_code varchar(60) NOT NULL,
  version_sequence integer NOT NULL,
  period_start date NOT NULL,
  period_end date NOT NULL,
  approval_status varchar(20) NOT NULL DEFAULT 'DRAFT',
  approved_at timestamptz,
  approved_by varchar(160),
  supersedes_balance_year_id uuid,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    jurisdiction_code,
    authority_code
  ),
  UNIQUE (
    tenant_id,
    id,
    jurisdiction_code,
    authority_code,
    balance_year_code
  ),
  UNIQUE (
    tenant_id,
    jurisdiction_code,
    authority_code,
    balance_year_code,
    version_sequence
  ),
  CHECK (jurisdiction_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (balance_year_code ~ '^[A-Z0-9][A-Z0-9_-]{1,59}$'),
  CHECK (version_sequence > 0),
  CHECK (period_end > period_start),
  CHECK (approval_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
  CHECK (
    (
      approval_status = 'APPROVED'
      AND approved_at IS NOT NULL
      AND approved_by IS NOT NULL
      AND btrim(approved_by) <> ''
    )
    OR (
      approval_status <> 'APPROVED'
      AND approved_at IS NULL
      AND approved_by IS NULL
    )
  ),
  CHECK (
    supersedes_balance_year_id IS NULL
    OR supersedes_balance_year_id <> id
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CONSTRAINT statutory_balance_year_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_balance_year_id,
      jurisdiction_code,
      authority_code,
      balance_year_code
    ) REFERENCES statutory.statutory_balance_year(
      tenant_id,
      id,
      jurisdiction_code,
      authority_code,
      balance_year_code
    )
);

ALTER TABLE statutory.statutory_balance_year
  ADD CONSTRAINT statutory_balance_year_approved_no_overlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    jurisdiction_code WITH =,
    authority_code WITH =,
    daterange(period_start, period_end, '[)') WITH &&
  ) WHERE (approval_status = 'APPROVED');

CREATE UNIQUE INDEX statutory_balance_year_one_successor_uk
  ON statutory.statutory_balance_year(
    tenant_id,
    supersedes_balance_year_id
  )
  WHERE supersedes_balance_year_id IS NOT NULL;

CREATE INDEX statutory_balance_year_lookup_ix
  ON statutory.statutory_balance_year(
    tenant_id,
    jurisdiction_code,
    authority_code,
    period_start,
    period_end
  )
  WHERE approval_status = 'APPROVED';

ALTER TABLE payroll_ops.payroll_cycle
  ADD CONSTRAINT payroll_cycle_statutory_period_uk
  UNIQUE (tenant_id, id, pay_period_id);

ALTER TABLE statutory.statutory_input_snapshot
  ADD CONSTRAINT statutory_input_snapshot_ledger_lineage_uk
  UNIQUE (
    tenant_id,
    id,
    employee_statutory_profile_id,
    statutory_rule_id,
    statutory_rule_version_id,
    employee_statutory_rule_assignment_id
  );

ALTER TABLE statutory.statutory_result
  ADD CONSTRAINT statutory_result_ledger_lineage_uk
  UNIQUE (
    tenant_id,
    id,
    evaluation_request_id,
    statutory_input_snapshot_id,
    statutory_rule_version_id
  );

CREATE TABLE statutory.statutory_ledger_batch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  calculation_request_id uuid NOT NULL,
  batch_kind varchar(20) NOT NULL,
  attempt_no integer NOT NULL,
  supersedes_batch_id uuid,
  idempotency_key varchar(120) NOT NULL,
  request_hash char(64) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'POSTING',
  posted_at timestamptz NOT NULL,
  posted_by varchar(160) NOT NULL,
  completed_at timestamptz,
  completed_by varchar(160),
  entry_count integer,
  balance_snapshot_count integer,
  remittance_summary_count integer,
  employee_delta_total numeric(19,4),
  employer_delta_total numeric(19,4),
  cycle_employee_total numeric(19,4),
  cycle_employer_total numeric(19,4),
  ledger_set_hash char(64),
  reconciliation_hash char(64),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_by varchar(160) NOT NULL,
  version_no bigint NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, id, payroll_cycle_id),
  UNIQUE (
    tenant_id,
    id,
    payroll_cycle_id,
    pay_period_id
  ),
  UNIQUE (
    tenant_id,
    id,
    payroll_cycle_id,
    evaluation_request_id
  ),
  UNIQUE (
    tenant_id,
    id,
    payroll_cycle_id,
    pay_period_id,
    evaluation_request_id
  ),
  UNIQUE (tenant_id, idempotency_key),
  UNIQUE (tenant_id, payroll_cycle_id, attempt_no),
  CHECK (batch_kind IN ('INITIAL', 'REPLACEMENT', 'CORRECTION')),
  CHECK (attempt_no > 0),
  CHECK (
    (
      batch_kind = 'INITIAL'
      AND attempt_no = 1
      AND supersedes_batch_id IS NULL
    )
    OR (
      batch_kind IN ('REPLACEMENT', 'CORRECTION')
      AND attempt_no > 1
      AND supersedes_batch_id IS NOT NULL
    )
  ),
  CHECK (length(btrim(idempotency_key)) BETWEEN 8 AND 120),
  CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  CHECK (status IN ('POSTING', 'COMPLETED')),
  CHECK (btrim(posted_by) <> ''),
  CHECK (
    (
      status = 'POSTING'
      AND completed_at IS NULL
      AND completed_by IS NULL
      AND entry_count IS NULL
      AND balance_snapshot_count IS NULL
      AND remittance_summary_count IS NULL
      AND employee_delta_total IS NULL
      AND employer_delta_total IS NULL
      AND cycle_employee_total IS NULL
      AND cycle_employer_total IS NULL
      AND ledger_set_hash IS NULL
      AND reconciliation_hash IS NULL
    )
    OR (
      status = 'COMPLETED'
      AND completed_at IS NOT NULL
      AND completed_by IS NOT NULL
      AND btrim(completed_by) <> ''
      AND entry_count IS NOT NULL
      AND entry_count > 0
      AND balance_snapshot_count IS NOT NULL
      AND balance_snapshot_count > 0
      AND remittance_summary_count IS NOT NULL
      AND remittance_summary_count > 0
      AND employee_delta_total IS NOT NULL
      AND employer_delta_total IS NOT NULL
      AND cycle_employee_total IS NOT NULL
      AND cycle_employer_total IS NOT NULL
      AND ledger_set_hash IS NOT NULL
      AND ledger_set_hash ~ '^[0-9a-f]{64}$'
      AND reconciliation_hash IS NOT NULL
      AND reconciliation_hash ~ '^[0-9a-f]{64}$'
    )
  ),
  FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id),
  CONSTRAINT statutory_ledger_batch_cycle_period_fk
    FOREIGN KEY (tenant_id, payroll_cycle_id, pay_period_id)
    REFERENCES payroll_ops.payroll_cycle(
      tenant_id,
      id,
      pay_period_id
    ),
  CONSTRAINT statutory_ledger_batch_evaluation_fk
    FOREIGN KEY (
      tenant_id,
      evaluation_request_id,
      calculation_request_id,
      payroll_cycle_id
    ) REFERENCES statutory.statutory_evaluation_request(
      tenant_id,
      id,
      calculation_request_id,
      payroll_cycle_id
    ),
  CONSTRAINT statutory_ledger_batch_supersedes_fk
    FOREIGN KEY (
      tenant_id,
      supersedes_batch_id,
      payroll_cycle_id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id
    )
);

CREATE UNIQUE INDEX statutory_ledger_batch_one_successor_uk
  ON statutory.statutory_ledger_batch(
    tenant_id,
    supersedes_batch_id
  )
  WHERE supersedes_batch_id IS NOT NULL;

CREATE UNIQUE INDEX statutory_ledger_batch_one_evaluation_posting_uk
  ON statutory.statutory_ledger_batch(
    tenant_id,
    evaluation_request_id
  )
  WHERE batch_kind IN ('INITIAL', 'REPLACEMENT');

CREATE INDEX statutory_ledger_batch_cycle_ix
  ON statutory.statutory_ledger_batch(
    tenant_id,
    payroll_cycle_id,
    attempt_no DESC,
    posted_at DESC
  );

CREATE TABLE statutory.statutory_ledger_entry (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ledger_batch_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  source_evaluation_request_id uuid NOT NULL,
  statutory_result_id uuid NOT NULL,
  statutory_input_snapshot_id uuid NOT NULL,
  employee_statutory_profile_id uuid NOT NULL,
  employee_statutory_rule_assignment_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  balance_year_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  sequence_no integer NOT NULL,
  entry_kind varchar(20) NOT NULL,
  source_entry_id uuid,
  currency platform.currency_code NOT NULL,
  employee_amount_delta numeric(19,4) NOT NULL,
  employer_amount_delta numeric(19,4) NOT NULL,
  reason_code varchar(60) NOT NULL,
  reason_detail varchar(500),
  entry_schema_version smallint NOT NULL DEFAULT 1,
  entry_payload jsonb NOT NULL,
  entry_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    id,
    payroll_cycle_id,
    employee_statutory_profile_id,
    statutory_rule_id
  ),
  UNIQUE (tenant_id, ledger_batch_id, sequence_no),
  CHECK (sequence_no > 0),
  CHECK (entry_kind IN ('EVALUATION', 'REVERSAL', 'CORRECTION')),
  CHECK (
    entry_kind = 'REVERSAL'
    OR source_evaluation_request_id = evaluation_request_id
  ),
  CHECK (
    (
      entry_kind = 'EVALUATION'
      AND source_entry_id IS NULL
      AND reason_code = 'EVALUATION'
      AND reason_detail IS NULL
    )
    OR (
      entry_kind = 'REVERSAL'
      AND source_entry_id IS NOT NULL
      AND reason_code = 'RECALCULATION_REPLACEMENT'
      AND reason_detail IS NOT NULL
      AND length(btrim(reason_detail)) BETWEEN 8 AND 500
    )
    OR (
      entry_kind = 'CORRECTION'
      AND source_entry_id IS NOT NULL
      AND reason_code = 'CORRECTION'
      AND reason_detail IS NOT NULL
      AND length(btrim(reason_detail)) BETWEEN 8 AND 500
      AND (
        employee_amount_delta <> 0
        OR employer_amount_delta <> 0
      )
    )
  ),
  CHECK (jurisdiction_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CHECK (authority_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CHECK (entry_schema_version = 1),
  CHECK (entry_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    entry_hash = encode(
      public.digest(entry_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_ledger_entry_batch_fk
    FOREIGN KEY (
      tenant_id,
      ledger_batch_id,
      payroll_cycle_id,
      pay_period_id,
      evaluation_request_id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id,
      pay_period_id,
      evaluation_request_id
    ),
  CONSTRAINT statutory_ledger_entry_snapshot_fk
    FOREIGN KEY (
      tenant_id,
      statutory_input_snapshot_id,
      employee_statutory_profile_id,
      statutory_rule_id,
      statutory_rule_version_id,
      employee_statutory_rule_assignment_id
    ) REFERENCES statutory.statutory_input_snapshot(
      tenant_id,
      id,
      employee_statutory_profile_id,
      statutory_rule_id,
      statutory_rule_version_id,
      employee_statutory_rule_assignment_id
    ),
  CONSTRAINT statutory_ledger_entry_result_fk
    FOREIGN KEY (
      tenant_id,
      statutory_result_id,
      source_evaluation_request_id,
      statutory_input_snapshot_id,
      statutory_rule_version_id
    ) REFERENCES statutory.statutory_result(
      tenant_id,
      id,
      evaluation_request_id,
      statutory_input_snapshot_id,
      statutory_rule_version_id
    ),
  CONSTRAINT statutory_ledger_entry_balance_year_fk
    FOREIGN KEY (
      tenant_id,
      balance_year_id,
      jurisdiction_code,
      authority_code
    ) REFERENCES statutory.statutory_balance_year(
      tenant_id,
      id,
      jurisdiction_code,
      authority_code
    ),
  CONSTRAINT statutory_ledger_entry_source_fk
    FOREIGN KEY (
      tenant_id,
      source_entry_id,
      payroll_cycle_id,
      employee_statutory_profile_id,
      statutory_rule_id
    ) REFERENCES statutory.statutory_ledger_entry(
      tenant_id,
      id,
      payroll_cycle_id,
      employee_statutory_profile_id,
      statutory_rule_id
    )
);

CREATE UNIQUE INDEX statutory_ledger_entry_one_batch_source_kind_uk
  ON statutory.statutory_ledger_entry(
    tenant_id,
    ledger_batch_id,
    source_entry_id,
    entry_kind
  )
  WHERE source_entry_id IS NOT NULL;

CREATE INDEX statutory_ledger_entry_cycle_balance_ix
  ON statutory.statutory_ledger_entry(
    tenant_id,
    payroll_cycle_id,
    employee_statutory_profile_id,
    statutory_rule_id,
    balance_year_id,
    sequence_no
  );

CREATE INDEX statutory_ledger_entry_period_ix
  ON statutory.statutory_ledger_entry(
    tenant_id,
    pay_period_id,
    statutory_rule_id,
    balance_year_id
  );

CREATE TABLE statutory.statutory_balance_snapshot (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ledger_batch_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  employee_statutory_profile_id uuid NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  balance_year_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  currency platform.currency_code NOT NULL,
  period_employee_amount numeric(19,4) NOT NULL,
  period_employer_amount numeric(19,4) NOT NULL,
  cycle_employee_amount numeric(19,4) NOT NULL,
  cycle_employer_amount numeric(19,4) NOT NULL,
  year_employee_amount numeric(19,4) NOT NULL,
  year_employer_amount numeric(19,4) NOT NULL,
  snapshot_schema_version smallint NOT NULL DEFAULT 1,
  snapshot_payload jsonb NOT NULL,
  snapshot_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    ledger_batch_id,
    employee_statutory_profile_id,
    statutory_rule_id,
    balance_year_id
  ),
  CHECK (snapshot_schema_version = 1),
  CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    snapshot_hash = encode(
      public.digest(snapshot_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_balance_snapshot_batch_fk
    FOREIGN KEY (
      tenant_id,
      ledger_batch_id,
      payroll_cycle_id,
      pay_period_id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id,
      pay_period_id
    ),
  CONSTRAINT statutory_balance_snapshot_profile_fk
    FOREIGN KEY (tenant_id, employee_statutory_profile_id)
    REFERENCES statutory.employee_statutory_profile(tenant_id, id),
  CONSTRAINT statutory_balance_snapshot_rule_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    ) REFERENCES statutory.statutory_rule_version(
      tenant_id,
      id,
      statutory_rule_id
    ),
  CONSTRAINT statutory_balance_snapshot_year_fk
    FOREIGN KEY (
      tenant_id,
      balance_year_id,
      jurisdiction_code,
      authority_code
    ) REFERENCES statutory.statutory_balance_year(
      tenant_id,
      id,
      jurisdiction_code,
      authority_code
    )
);

CREATE INDEX statutory_balance_snapshot_lookup_ix
  ON statutory.statutory_balance_snapshot(
    tenant_id,
    employee_statutory_profile_id,
    statutory_rule_id,
    balance_year_id,
    created_at DESC
  );

CREATE TABLE statutory.statutory_reconciliation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ledger_batch_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  evaluation_request_id uuid NOT NULL,
  currency platform.currency_code NOT NULL,
  source_employee_total numeric(19,4) NOT NULL,
  source_employer_total numeric(19,4) NOT NULL,
  correction_employee_total numeric(19,4) NOT NULL,
  correction_employer_total numeric(19,4) NOT NULL,
  expected_employee_total numeric(19,4) NOT NULL,
  expected_employer_total numeric(19,4) NOT NULL,
  ledger_employee_total numeric(19,4) NOT NULL,
  ledger_employer_total numeric(19,4) NOT NULL,
  employee_variance numeric(19,4) NOT NULL,
  employer_variance numeric(19,4) NOT NULL,
  reconciliation_status varchar(20) NOT NULL,
  reconciliation_schema_version smallint NOT NULL DEFAULT 1,
  reconciliation_payload jsonb NOT NULL,
  reconciliation_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, ledger_batch_id),
  CHECK (expected_employee_total =
    source_employee_total + correction_employee_total),
  CHECK (expected_employer_total =
    source_employer_total + correction_employer_total),
  CHECK (employee_variance =
    ledger_employee_total - expected_employee_total),
  CHECK (employer_variance =
    ledger_employer_total - expected_employer_total),
  CHECK (
    reconciliation_status = 'MATCHED'
    AND employee_variance = 0
    AND employer_variance = 0
  ),
  CHECK (reconciliation_schema_version = 1),
  CHECK (reconciliation_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    reconciliation_hash = encode(
      public.digest(reconciliation_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_reconciliation_batch_fk
    FOREIGN KEY (
      tenant_id,
      ledger_batch_id,
      payroll_cycle_id,
      evaluation_request_id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id,
      evaluation_request_id
    )
);

CREATE TABLE statutory.statutory_remittance_summary (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  ledger_batch_id uuid NOT NULL,
  payroll_cycle_id uuid NOT NULL,
  pay_period_id uuid NOT NULL,
  balance_year_id uuid NOT NULL,
  jurisdiction_code varchar(40) NOT NULL,
  authority_code varchar(60) NOT NULL,
  statutory_rule_id uuid NOT NULL,
  statutory_rule_version_id uuid NOT NULL,
  currency platform.currency_code NOT NULL,
  batch_employee_delta numeric(19,4) NOT NULL,
  batch_employer_delta numeric(19,4) NOT NULL,
  period_employee_total numeric(19,4) NOT NULL,
  period_employer_total numeric(19,4) NOT NULL,
  year_employee_total numeric(19,4) NOT NULL,
  year_employer_total numeric(19,4) NOT NULL,
  remittance_amount numeric(19,4) NOT NULL,
  remittance_position varchar(20) NOT NULL,
  summary_schema_version smallint NOT NULL DEFAULT 1,
  summary_payload jsonb NOT NULL,
  summary_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  created_by varchar(160) NOT NULL,
  UNIQUE (tenant_id, id),
  UNIQUE (
    tenant_id,
    ledger_batch_id,
    balance_year_id,
    statutory_rule_id
  ),
  CHECK (
    remittance_amount =
      period_employee_total + period_employer_total
  ),
  CHECK (
    (
      remittance_amount > 0
      AND remittance_position = 'PAYABLE'
    )
    OR (
      remittance_amount < 0
      AND remittance_position = 'CREDIT'
    )
    OR (
      remittance_amount = 0
      AND remittance_position = 'ZERO'
    )
  ),
  CHECK (summary_schema_version = 1),
  CHECK (summary_hash ~ '^[0-9a-f]{64}$'),
  CHECK (
    summary_hash = encode(
      public.digest(summary_payload::text, 'sha256'::text),
      'hex'
    )
  ),
  CONSTRAINT statutory_remittance_summary_batch_fk
    FOREIGN KEY (
      tenant_id,
      ledger_batch_id,
      payroll_cycle_id,
      pay_period_id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id,
      pay_period_id
    ),
  CONSTRAINT statutory_remittance_summary_year_fk
    FOREIGN KEY (
      tenant_id,
      balance_year_id,
      jurisdiction_code,
      authority_code
    ) REFERENCES statutory.statutory_balance_year(
      tenant_id,
      id,
      jurisdiction_code,
      authority_code
    ),
  CONSTRAINT statutory_remittance_summary_rule_version_fk
    FOREIGN KEY (
      tenant_id,
      statutory_rule_version_id,
      statutory_rule_id
    ) REFERENCES statutory.statutory_rule_version(
      tenant_id,
      id,
      statutory_rule_id
    )
);

CREATE INDEX statutory_remittance_summary_lookup_ix
  ON statutory.statutory_remittance_summary(
    tenant_id,
    jurisdiction_code,
    authority_code,
    balance_year_id,
    pay_period_id,
    statutory_rule_id
  );

ALTER TABLE payroll_ops.payroll_cycle
  ADD COLUMN active_statutory_ledger_batch_id uuid,
  ADD COLUMN statutory_posted_at timestamptz,
  ADD COLUMN statutory_posted_by varchar(160),
  ADD COLUMN statutory_employee_total numeric(19,4),
  ADD COLUMN statutory_employer_total numeric(19,4),
  ADD COLUMN statutory_ledger_set_hash char(64),
  ADD CONSTRAINT payroll_cycle_statutory_posting_shape_ck
    CHECK (
      (
        active_statutory_ledger_batch_id IS NULL
        AND statutory_posted_at IS NULL
        AND statutory_posted_by IS NULL
        AND statutory_employee_total IS NULL
        AND statutory_employer_total IS NULL
        AND statutory_ledger_set_hash IS NULL
      )
      OR (
        active_statutory_ledger_batch_id IS NOT NULL
        AND statutory_posted_at IS NOT NULL
        AND statutory_posted_by IS NOT NULL
        AND btrim(statutory_posted_by) <> ''
        AND statutory_employee_total IS NOT NULL
        AND statutory_employer_total IS NOT NULL
        AND statutory_ledger_set_hash IS NOT NULL
        AND statutory_ledger_set_hash ~ '^[0-9a-f]{64}$'
        AND status = 'CALCULATED'
      )
    ),
  ADD CONSTRAINT payroll_cycle_active_statutory_batch_fk
    FOREIGN KEY (
      tenant_id,
      active_statutory_ledger_batch_id,
      id
    ) REFERENCES statutory.statutory_ledger_batch(
      tenant_id,
      id,
      payroll_cycle_id
    );

CREATE OR REPLACE FUNCTION
  statutory.assert_statutory_balance_year_dependencies()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  v_parent_sequence integer;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.approval_status <> 'DRAFT'
       OR NEW.approved_at IS NOT NULL
       OR NEW.approved_by IS NOT NULL
       OR NEW.version_no <> 0 THEN
      RAISE EXCEPTION
        'statutory balance years must be inserted as new drafts'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.version_sequence = 1 THEN
      IF NEW.supersedes_balance_year_id IS NOT NULL
         OR EXISTS (
           SELECT 1
           FROM statutory.statutory_balance_year existing_year
           WHERE existing_year.tenant_id = NEW.tenant_id
             AND existing_year.jurisdiction_code = NEW.jurisdiction_code
             AND existing_year.authority_code = NEW.authority_code
             AND existing_year.balance_year_code = NEW.balance_year_code
         ) THEN
        RAISE EXCEPTION
          'first statutory balance year version must start a new chain'
          USING ERRCODE = '23514';
      END IF;
    ELSE
      IF NEW.supersedes_balance_year_id IS NULL THEN
        RAISE EXCEPTION
          'later statutory balance year versions must supersede the prior version'
          USING ERRCODE = '23514';
      END IF;

      SELECT parent_year.version_sequence
      INTO v_parent_sequence
      FROM statutory.statutory_balance_year parent_year
      WHERE parent_year.tenant_id = NEW.tenant_id
        AND parent_year.id = NEW.supersedes_balance_year_id
        AND parent_year.jurisdiction_code = NEW.jurisdiction_code
        AND parent_year.authority_code = NEW.authority_code
        AND parent_year.balance_year_code = NEW.balance_year_code
      FOR UPDATE OF parent_year;

      IF v_parent_sequence IS NULL THEN
        RAISE EXCEPTION
          'superseded statutory balance year does not exist in the current chain'
          USING ERRCODE = '23503';
      END IF;

      IF NEW.version_sequence <> v_parent_sequence + 1 THEN
        RAISE EXCEPTION
          'statutory balance year sequence must follow its parent'
          USING ERRCODE = '23514';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER statutory_balance_year_dependencies
  BEFORE INSERT OR UPDATE OF
    tenant_id,
    jurisdiction_code,
    authority_code,
    balance_year_code,
    version_sequence,
    supersedes_balance_year_id
  ON statutory.statutory_balance_year
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.assert_statutory_balance_year_dependencies();

REVOKE ALL ON FUNCTION
  statutory.assert_statutory_balance_year_dependencies()
  FROM PUBLIC;

CREATE TRIGGER statutory_balance_year_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_balance_year
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_configuration_mutation();

CREATE OR REPLACE FUNCTION
  statutory.reject_uncontrolled_statutory_ledger_batch_mutation()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_setting(
       'statutory.ledger_mutation',
       true
     ) IS DISTINCT FROM 'allowed' THEN
    RAISE EXCEPTION
      'statutory ledger batches may change only through controlled commands'
      USING ERRCODE = '42501';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER statutory_ledger_batch_controlled_mutation
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_ledger_batch
  FOR EACH ROW
  EXECUTE FUNCTION
    statutory.reject_uncontrolled_statutory_ledger_batch_mutation();

REVOKE ALL ON FUNCTION
  statutory.reject_uncontrolled_statutory_ledger_batch_mutation()
  FROM PUBLIC;

CREATE TRIGGER statutory_ledger_entry_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_ledger_entry
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER statutory_balance_snapshot_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_balance_snapshot
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER statutory_reconciliation_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_reconciliation
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE TRIGGER statutory_remittance_summary_immutable
  BEFORE UPDATE OR DELETE
  ON statutory.statutory_remittance_summary
  FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

CREATE OR REPLACE FUNCTION
  statutory.approve_statutory_balance_year(
    p_tenant_id uuid,
    p_balance_year_id uuid,
    p_actor varchar,
    p_approved_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  v_affected bigint;
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

  PERFORM 1
  FROM statutory.statutory_balance_year balance_year
  WHERE balance_year.tenant_id = p_tenant_id
    AND balance_year.id = p_balance_year_id
    AND balance_year.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_balance_year successor
      WHERE successor.tenant_id = balance_year.tenant_id
        AND successor.supersedes_balance_year_id = balance_year.id
    )
  FOR UPDATE OF balance_year;

  IF NOT FOUND THEN
    RETURN 0;
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_balance_year balance_year
  SET approval_status = 'APPROVED',
      approved_at = p_approved_at,
      approved_by = p_actor,
      updated_at = p_approved_at,
      updated_by = p_actor,
      version_no = balance_year.version_no + 1
  WHERE balance_year.tenant_id = p_tenant_id
    AND balance_year.id = p_balance_year_id
    AND balance_year.approval_status = 'DRAFT'
    AND NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_balance_year successor
      WHERE successor.tenant_id = balance_year.tenant_id
        AND successor.supersedes_balance_year_id = balance_year.id
    );

  GET DIAGNOSTICS v_affected = ROW_COUNT;
  RETURN v_affected;
END $$;

CREATE OR REPLACE FUNCTION
  statutory.end_date_statutory_balance_year(
    p_tenant_id uuid,
    p_balance_year_id uuid,
    p_period_end date,
    p_expected_version bigint,
    p_actor varchar,
    p_changed_at timestamptz
  ) RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, statutory, platform AS $$
DECLARE
  v_affected bigint;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_period_end IS NULL THEN
    RAISE EXCEPTION 'period-end date is required'
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

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_ledger_entry ledger_entry
    WHERE ledger_entry.tenant_id = p_tenant_id
      AND ledger_entry.balance_year_id = p_balance_year_id
  ) THEN
    RAISE EXCEPTION
      'a statutory balance year referenced by ledger evidence cannot be end-dated'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.configuration_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_balance_year balance_year
  SET period_end = p_period_end,
      updated_at = p_changed_at,
      updated_by = p_actor,
      version_no = balance_year.version_no + 1
  WHERE balance_year.tenant_id = p_tenant_id
    AND balance_year.id = p_balance_year_id
    AND balance_year.approval_status = 'APPROVED'
    AND balance_year.version_no = p_expected_version
    AND balance_year.period_start < p_period_end
    AND balance_year.period_end > p_period_end;

  GET DIAGNOSTICS v_affected = ROW_COUNT;
  RETURN v_affected;
END $$;

CREATE OR REPLACE FUNCTION statutory.finalize_statutory_ledger_batch(
  p_tenant_id uuid,
  p_ledger_batch_id uuid,
  p_payroll_cycle_id uuid,
  p_evaluation_request_id uuid,
  p_expected_cycle_version bigint,
  p_actor varchar,
  p_completed_at timestamptz
) RETURNS TABLE (
  posted_entry_count integer,
  employee_delta_total numeric(19,4),
  employer_delta_total numeric(19,4),
  cycle_employee_total numeric(19,4),
  cycle_employer_total numeric(19,4),
  ledger_set_hash char(64),
  cycle_version_no bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  statutory,
  payroll_ops,
  payroll_calc,
  organisation,
  platform AS $$
DECLARE
  v_cycle_version bigint;
  v_batch_status varchar(20);
  v_pay_period_id uuid;
  v_source_employee_total numeric(19,4);
  v_source_employer_total numeric(19,4);
  v_entry_count integer;
  v_snapshot_count integer;
  v_remittance_count integer;
  v_employee_delta numeric(19,4);
  v_employer_delta numeric(19,4);
  v_cycle_employee numeric(19,4);
  v_cycle_employer numeric(19,4);
  v_correction_employee numeric(19,4);
  v_correction_employer numeric(19,4);
  v_employee_variance numeric(19,4);
  v_employer_variance numeric(19,4);
  v_ledger_hash char(64);
  v_reconciliation_hash char(64);
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  SELECT
    cycle.version_no,
    ledger_batch.status,
    ledger_batch.pay_period_id,
    evaluation.employee_total,
    evaluation.employer_total
  INTO
    v_cycle_version,
    v_batch_status,
    v_pay_period_id,
    v_source_employee_total,
    v_source_employer_total
  FROM payroll_ops.payroll_cycle cycle
  JOIN statutory.statutory_ledger_batch ledger_batch
    ON ledger_batch.tenant_id = cycle.tenant_id
   AND ledger_batch.payroll_cycle_id = cycle.id
  JOIN statutory.statutory_evaluation_request evaluation
    ON evaluation.tenant_id = ledger_batch.tenant_id
   AND evaluation.id = ledger_batch.evaluation_request_id
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND ledger_batch.id = p_ledger_batch_id
    AND ledger_batch.evaluation_request_id = p_evaluation_request_id
  FOR UPDATE OF cycle, ledger_batch;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory ledger batch does not exist with exact cycle and evaluation lineage'
      USING ERRCODE = '23503';
  END IF;

  IF v_cycle_version <> p_expected_cycle_version THEN
    RAISE EXCEPTION 'payroll cycle changed while statutory ledger was posting'
      USING ERRCODE = '40001';
  END IF;

  IF v_batch_status <> 'POSTING' THEN
    RAISE EXCEPTION 'statutory ledger batch is not in posting state'
      USING ERRCODE = '23514';
  END IF;

  WITH touched_identity AS (
    SELECT DISTINCT
      ledger_entry.employee_statutory_profile_id,
      ledger_entry.statutory_rule_id,
      ledger_entry.balance_year_id,
      ledger_entry.jurisdiction_code,
      ledger_entry.authority_code,
      ledger_entry.currency
    FROM statutory.statutory_ledger_entry ledger_entry
    WHERE ledger_entry.tenant_id = p_tenant_id
      AND ledger_entry.ledger_batch_id = p_ledger_batch_id
  ),
  touched AS (
    SELECT
      touched_identity.*,
      (
        SELECT current_entry.statutory_rule_version_id
        FROM statutory.statutory_ledger_entry current_entry
        WHERE current_entry.tenant_id = p_tenant_id
          AND current_entry.ledger_batch_id = p_ledger_batch_id
          AND current_entry.employee_statutory_profile_id =
              touched_identity.employee_statutory_profile_id
          AND current_entry.statutory_rule_id =
              touched_identity.statutory_rule_id
          AND current_entry.balance_year_id =
              touched_identity.balance_year_id
        ORDER BY
          CASE current_entry.entry_kind
            WHEN 'EVALUATION' THEN 1
            WHEN 'CORRECTION' THEN 2
            ELSE 3
          END,
          current_entry.sequence_no DESC,
          current_entry.id DESC
        LIMIT 1
      ) AS statutory_rule_version_id
    FROM touched_identity
  ),
  prepared AS (
    SELECT
      touched.*,
      (
        SELECT coalesce(
          sum(period_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry period_entry
        WHERE period_entry.tenant_id = p_tenant_id
          AND period_entry.pay_period_id = v_pay_period_id
          AND period_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND period_entry.statutory_rule_id =
              touched.statutory_rule_id
          AND period_entry.balance_year_id =
              touched.balance_year_id
      ) AS period_employee_amount,
      (
        SELECT coalesce(
          sum(period_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry period_entry
        WHERE period_entry.tenant_id = p_tenant_id
          AND period_entry.pay_period_id = v_pay_period_id
          AND period_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND period_entry.statutory_rule_id =
              touched.statutory_rule_id
          AND period_entry.balance_year_id =
              touched.balance_year_id
      ) AS period_employer_amount,
      (
        SELECT coalesce(
          sum(cycle_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry cycle_entry
        WHERE cycle_entry.tenant_id = p_tenant_id
          AND cycle_entry.payroll_cycle_id = p_payroll_cycle_id
          AND cycle_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND cycle_entry.statutory_rule_id =
              touched.statutory_rule_id
      ) AS cycle_employee_amount,
      (
        SELECT coalesce(
          sum(cycle_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry cycle_entry
        WHERE cycle_entry.tenant_id = p_tenant_id
          AND cycle_entry.payroll_cycle_id = p_payroll_cycle_id
          AND cycle_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND cycle_entry.statutory_rule_id =
              touched.statutory_rule_id
      ) AS cycle_employer_amount,
      (
        SELECT coalesce(
          sum(year_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry year_entry
        WHERE year_entry.tenant_id = p_tenant_id
          AND year_entry.balance_year_id = touched.balance_year_id
          AND year_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND year_entry.statutory_rule_id =
              touched.statutory_rule_id
      ) AS year_employee_amount,
      (
        SELECT coalesce(
          sum(year_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry year_entry
        WHERE year_entry.tenant_id = p_tenant_id
          AND year_entry.balance_year_id = touched.balance_year_id
          AND year_entry.employee_statutory_profile_id =
              touched.employee_statutory_profile_id
          AND year_entry.statutory_rule_id =
              touched.statutory_rule_id
      ) AS year_employer_amount
    FROM touched
  ),
  payloads AS (
    SELECT
      prepared.*,
      jsonb_build_object(
        'schemaVersion', 1,
        'ledgerBatchId', p_ledger_batch_id::text,
        'payrollCycleId', p_payroll_cycle_id::text,
        'payPeriodId', v_pay_period_id::text,
        'employeeStatutoryProfileId',
          prepared.employee_statutory_profile_id::text,
        'statutoryRuleId', prepared.statutory_rule_id::text,
        'statutoryRuleVersionId',
          prepared.statutory_rule_version_id::text,
        'balanceYearId', prepared.balance_year_id::text,
        'jurisdictionCode', prepared.jurisdiction_code,
        'authorityCode', prepared.authority_code,
        'currency', prepared.currency,
        'periodEmployeeAmount', prepared.period_employee_amount,
        'periodEmployerAmount', prepared.period_employer_amount,
        'cycleEmployeeAmount', prepared.cycle_employee_amount,
        'cycleEmployerAmount', prepared.cycle_employer_amount,
        'yearEmployeeAmount', prepared.year_employee_amount,
        'yearEmployerAmount', prepared.year_employer_amount
      ) AS snapshot_payload
    FROM prepared
  )
  INSERT INTO statutory.statutory_balance_snapshot(
    tenant_id,
    ledger_batch_id,
    payroll_cycle_id,
    pay_period_id,
    employee_statutory_profile_id,
    statutory_rule_id,
    statutory_rule_version_id,
    balance_year_id,
    jurisdiction_code,
    authority_code,
    currency,
    period_employee_amount,
    period_employer_amount,
    cycle_employee_amount,
    cycle_employer_amount,
    year_employee_amount,
    year_employer_amount,
    snapshot_payload,
    snapshot_hash,
    created_at,
    created_by
  )
  SELECT
    p_tenant_id,
    p_ledger_batch_id,
    p_payroll_cycle_id,
    v_pay_period_id,
    payloads.employee_statutory_profile_id,
    payloads.statutory_rule_id,
    payloads.statutory_rule_version_id,
    payloads.balance_year_id,
    payloads.jurisdiction_code,
    payloads.authority_code,
    payloads.currency,
    payloads.period_employee_amount,
    payloads.period_employer_amount,
    payloads.cycle_employee_amount,
    payloads.cycle_employer_amount,
    payloads.year_employee_amount,
    payloads.year_employer_amount,
    payloads.snapshot_payload,
    encode(
      public.digest(payloads.snapshot_payload::text, 'sha256'::text),
      'hex'
    ),
    p_completed_at,
    p_actor
  FROM payloads;

  GET DIAGNOSTICS v_snapshot_count = ROW_COUNT;

  SELECT
    count(*)::integer,
    coalesce(sum(ledger_entry.employee_amount_delta), 0)::numeric(19,4),
    coalesce(sum(ledger_entry.employer_amount_delta), 0)::numeric(19,4)
  INTO
    v_entry_count,
    v_employee_delta,
    v_employer_delta
  FROM statutory.statutory_ledger_entry ledger_entry
  WHERE ledger_entry.tenant_id = p_tenant_id
    AND ledger_entry.ledger_batch_id = p_ledger_batch_id;

  SELECT
    coalesce(sum(ledger_entry.employee_amount_delta), 0)::numeric(19,4),
    coalesce(sum(ledger_entry.employer_amount_delta), 0)::numeric(19,4)
  INTO
    v_cycle_employee,
    v_cycle_employer
  FROM statutory.statutory_ledger_entry ledger_entry
  WHERE ledger_entry.tenant_id = p_tenant_id
    AND ledger_entry.payroll_cycle_id = p_payroll_cycle_id;

  SELECT
    coalesce(sum(ledger_entry.employee_amount_delta), 0)::numeric(19,4),
    coalesce(sum(ledger_entry.employer_amount_delta), 0)::numeric(19,4)
  INTO
    v_correction_employee,
    v_correction_employer
  FROM statutory.statutory_ledger_entry ledger_entry
  WHERE ledger_entry.tenant_id = p_tenant_id
    AND ledger_entry.payroll_cycle_id = p_payroll_cycle_id
    AND ledger_entry.evaluation_request_id = p_evaluation_request_id
    AND ledger_entry.entry_kind = 'CORRECTION';

  v_employee_variance :=
    v_cycle_employee -
      (v_source_employee_total + v_correction_employee);
  v_employer_variance :=
    v_cycle_employer -
      (v_source_employer_total + v_correction_employer);

  IF v_employee_variance <> 0 OR v_employer_variance <> 0 THEN
    RAISE EXCEPTION
      'statutory ledger reconciliation variance: employee %, employer %',
      v_employee_variance,
      v_employer_variance
      USING ERRCODE = '23514';
  END IF;

  WITH prepared AS (
    SELECT jsonb_build_object(
      'schemaVersion', 1,
      'ledgerBatchId', p_ledger_batch_id::text,
      'payrollCycleId', p_payroll_cycle_id::text,
      'evaluationRequestId', p_evaluation_request_id::text,
      'currency', 'INR',
      'sourceEmployeeTotal', v_source_employee_total,
      'sourceEmployerTotal', v_source_employer_total,
      'correctionEmployeeTotal', v_correction_employee,
      'correctionEmployerTotal', v_correction_employer,
      'expectedEmployeeTotal',
        v_source_employee_total + v_correction_employee,
      'expectedEmployerTotal',
        v_source_employer_total + v_correction_employer,
      'ledgerEmployeeTotal', v_cycle_employee,
      'ledgerEmployerTotal', v_cycle_employer,
      'employeeVariance', v_employee_variance,
      'employerVariance', v_employer_variance,
      'status', 'MATCHED'
    ) AS reconciliation_payload
  )
  INSERT INTO statutory.statutory_reconciliation(
    tenant_id,
    ledger_batch_id,
    payroll_cycle_id,
    evaluation_request_id,
    currency,
    source_employee_total,
    source_employer_total,
    correction_employee_total,
    correction_employer_total,
    expected_employee_total,
    expected_employer_total,
    ledger_employee_total,
    ledger_employer_total,
    employee_variance,
    employer_variance,
    reconciliation_status,
    reconciliation_payload,
    reconciliation_hash,
    created_at,
    created_by
  )
  SELECT
    p_tenant_id,
    p_ledger_batch_id,
    p_payroll_cycle_id,
    p_evaluation_request_id,
    'INR',
    v_source_employee_total,
    v_source_employer_total,
    v_correction_employee,
    v_correction_employer,
    v_source_employee_total + v_correction_employee,
    v_source_employer_total + v_correction_employer,
    v_cycle_employee,
    v_cycle_employer,
    v_employee_variance,
    v_employer_variance,
    'MATCHED',
    prepared.reconciliation_payload,
    encode(
      public.digest(
        prepared.reconciliation_payload::text,
        'sha256'::text
      ),
      'hex'
    ),
    p_completed_at,
    p_actor
  FROM prepared
  RETURNING reconciliation_hash
  INTO v_reconciliation_hash;

  WITH touched_identity AS (
    SELECT DISTINCT
      ledger_entry.balance_year_id,
      ledger_entry.jurisdiction_code,
      ledger_entry.authority_code,
      ledger_entry.statutory_rule_id,
      ledger_entry.currency
    FROM statutory.statutory_ledger_entry ledger_entry
    WHERE ledger_entry.tenant_id = p_tenant_id
      AND ledger_entry.ledger_batch_id = p_ledger_batch_id
  ),
  touched AS (
    SELECT
      touched_identity.*,
      (
        SELECT current_entry.statutory_rule_version_id
        FROM statutory.statutory_ledger_entry current_entry
        WHERE current_entry.tenant_id = p_tenant_id
          AND current_entry.ledger_batch_id = p_ledger_batch_id
          AND current_entry.balance_year_id =
              touched_identity.balance_year_id
          AND current_entry.statutory_rule_id =
              touched_identity.statutory_rule_id
        ORDER BY
          CASE current_entry.entry_kind
            WHEN 'EVALUATION' THEN 1
            WHEN 'CORRECTION' THEN 2
            ELSE 3
          END,
          current_entry.sequence_no DESC,
          current_entry.id DESC
        LIMIT 1
      ) AS statutory_rule_version_id
    FROM touched_identity
  ),
  prepared AS (
    SELECT
      touched.*,
      (
        SELECT coalesce(
          sum(batch_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry batch_entry
        WHERE batch_entry.tenant_id = p_tenant_id
          AND batch_entry.ledger_batch_id = p_ledger_batch_id
          AND batch_entry.balance_year_id = touched.balance_year_id
          AND batch_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS batch_employee_delta,
      (
        SELECT coalesce(
          sum(batch_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry batch_entry
        WHERE batch_entry.tenant_id = p_tenant_id
          AND batch_entry.ledger_batch_id = p_ledger_batch_id
          AND batch_entry.balance_year_id = touched.balance_year_id
          AND batch_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS batch_employer_delta,
      (
        SELECT coalesce(
          sum(period_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry period_entry
        WHERE period_entry.tenant_id = p_tenant_id
          AND period_entry.pay_period_id = v_pay_period_id
          AND period_entry.balance_year_id = touched.balance_year_id
          AND period_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS period_employee_total,
      (
        SELECT coalesce(
          sum(period_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry period_entry
        WHERE period_entry.tenant_id = p_tenant_id
          AND period_entry.pay_period_id = v_pay_period_id
          AND period_entry.balance_year_id = touched.balance_year_id
          AND period_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS period_employer_total,
      (
        SELECT coalesce(
          sum(year_entry.employee_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry year_entry
        WHERE year_entry.tenant_id = p_tenant_id
          AND year_entry.balance_year_id = touched.balance_year_id
          AND year_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS year_employee_total,
      (
        SELECT coalesce(
          sum(year_entry.employer_amount_delta),
          0
        )::numeric(19,4)
        FROM statutory.statutory_ledger_entry year_entry
        WHERE year_entry.tenant_id = p_tenant_id
          AND year_entry.balance_year_id = touched.balance_year_id
          AND year_entry.statutory_rule_id = touched.statutory_rule_id
      ) AS year_employer_total
    FROM touched
  ),
  payloads AS (
    SELECT
      prepared.*,
      prepared.period_employee_total +
        prepared.period_employer_total AS remittance_amount,
      CASE
        WHEN prepared.period_employee_total +
             prepared.period_employer_total > 0 THEN 'PAYABLE'
        WHEN prepared.period_employee_total +
             prepared.period_employer_total < 0 THEN 'CREDIT'
        ELSE 'ZERO'
      END AS remittance_position,
      jsonb_build_object(
        'schemaVersion', 1,
        'ledgerBatchId', p_ledger_batch_id::text,
        'payrollCycleId', p_payroll_cycle_id::text,
        'payPeriodId', v_pay_period_id::text,
        'balanceYearId', prepared.balance_year_id::text,
        'jurisdictionCode', prepared.jurisdiction_code,
        'authorityCode', prepared.authority_code,
        'statutoryRuleId', prepared.statutory_rule_id::text,
        'statutoryRuleVersionId',
          prepared.statutory_rule_version_id::text,
        'currency', prepared.currency,
        'batchEmployeeDelta', prepared.batch_employee_delta,
        'batchEmployerDelta', prepared.batch_employer_delta,
        'periodEmployeeTotal', prepared.period_employee_total,
        'periodEmployerTotal', prepared.period_employer_total,
        'yearEmployeeTotal', prepared.year_employee_total,
        'yearEmployerTotal', prepared.year_employer_total,
        'remittanceAmount',
          prepared.period_employee_total +
            prepared.period_employer_total,
        'remittancePosition',
          CASE
            WHEN prepared.period_employee_total +
                 prepared.period_employer_total > 0 THEN 'PAYABLE'
            WHEN prepared.period_employee_total +
                 prepared.period_employer_total < 0 THEN 'CREDIT'
            ELSE 'ZERO'
          END
      ) AS summary_payload
    FROM prepared
  )
  INSERT INTO statutory.statutory_remittance_summary(
    tenant_id,
    ledger_batch_id,
    payroll_cycle_id,
    pay_period_id,
    balance_year_id,
    jurisdiction_code,
    authority_code,
    statutory_rule_id,
    statutory_rule_version_id,
    currency,
    batch_employee_delta,
    batch_employer_delta,
    period_employee_total,
    period_employer_total,
    year_employee_total,
    year_employer_total,
    remittance_amount,
    remittance_position,
    summary_payload,
    summary_hash,
    created_at,
    created_by
  )
  SELECT
    p_tenant_id,
    p_ledger_batch_id,
    p_payroll_cycle_id,
    v_pay_period_id,
    payloads.balance_year_id,
    payloads.jurisdiction_code,
    payloads.authority_code,
    payloads.statutory_rule_id,
    payloads.statutory_rule_version_id,
    payloads.currency,
    payloads.batch_employee_delta,
    payloads.batch_employer_delta,
    payloads.period_employee_total,
    payloads.period_employer_total,
    payloads.year_employee_total,
    payloads.year_employer_total,
    payloads.remittance_amount,
    payloads.remittance_position,
    payloads.summary_payload,
    encode(
      public.digest(payloads.summary_payload::text, 'sha256'::text),
      'hex'
    ),
    p_completed_at,
    p_actor
  FROM payloads;

  GET DIAGNOSTICS v_remittance_count = ROW_COUNT;

  SELECT encode(
    public.digest(
      string_agg(
        ledger_entry.id::text || ':' || ledger_entry.entry_hash,
        '|'
        ORDER BY
          ledger_batch.attempt_no,
          ledger_entry.sequence_no,
          ledger_entry.id
      ),
      'sha256'::text
    ),
    'hex'
  )
  INTO v_ledger_hash
  FROM statutory.statutory_ledger_entry ledger_entry
  JOIN statutory.statutory_ledger_batch ledger_batch
    ON ledger_batch.tenant_id = ledger_entry.tenant_id
   AND ledger_batch.id = ledger_entry.ledger_batch_id
  WHERE ledger_entry.tenant_id = p_tenant_id
    AND ledger_entry.payroll_cycle_id = p_payroll_cycle_id;

  IF v_entry_count < 1
     OR v_snapshot_count < 1
     OR v_remittance_count < 1
     OR v_ledger_hash IS NULL THEN
    RAISE EXCEPTION
      'statutory ledger batch lacks complete derived evidence'
      USING ERRCODE = '23514';
  END IF;

  PERFORM set_config(
    'statutory.ledger_mutation',
    'allowed',
    true
  );

  UPDATE statutory.statutory_ledger_batch ledger_batch
  SET status = 'COMPLETED',
      completed_at = p_completed_at,
      completed_by = p_actor,
      entry_count = v_entry_count,
      balance_snapshot_count = v_snapshot_count,
      remittance_summary_count = v_remittance_count,
      employee_delta_total = v_employee_delta,
      employer_delta_total = v_employer_delta,
      cycle_employee_total = v_cycle_employee,
      cycle_employer_total = v_cycle_employer,
      ledger_set_hash = v_ledger_hash,
      reconciliation_hash = v_reconciliation_hash,
      updated_at = p_completed_at,
      updated_by = p_actor,
      version_no = ledger_batch.version_no + 1
  WHERE ledger_batch.tenant_id = p_tenant_id
    AND ledger_batch.id = p_ledger_batch_id
    AND ledger_batch.status = 'POSTING';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory ledger batch changed while it was being completed'
      USING ERRCODE = '40001';
  END IF;

  PERFORM set_config(
    'payroll_ops.population_mutation',
    'allowed',
    true
  );

  UPDATE payroll_ops.payroll_cycle cycle
  SET active_statutory_ledger_batch_id = p_ledger_batch_id,
      statutory_posted_at = p_completed_at,
      statutory_posted_by = p_actor,
      statutory_employee_total = v_cycle_employee,
      statutory_employer_total = v_cycle_employer,
      statutory_ledger_set_hash = v_ledger_hash,
      updated_at = p_completed_at,
      updated_by = p_actor,
      version_no = cycle.version_no + 1
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.version_no = p_expected_cycle_version
    AND cycle.status = 'CALCULATED';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'payroll cycle changed while statutory posting was finalized'
      USING ERRCODE = '40001';
  END IF;

  RETURN QUERY
  SELECT
    v_entry_count,
    v_employee_delta,
    v_employer_delta,
    v_cycle_employee,
    v_cycle_employer,
    v_ledger_hash,
    v_cycle_version + 1;
END $$;

REVOKE ALL ON FUNCTION statutory.finalize_statutory_ledger_batch(
  uuid,
  uuid,
  uuid,
  uuid,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

CREATE OR REPLACE FUNCTION statutory.post_statutory_evaluation(
  p_tenant_id uuid,
  p_evaluation_request_id uuid,
  p_expected_cycle_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_actor varchar,
  p_posted_at timestamptz
) RETURNS TABLE (
  ledger_batch_id uuid,
  attempt_no integer,
  batch_kind varchar,
  posted_entry_count integer,
  employee_delta_total numeric(19,4),
  employer_delta_total numeric(19,4),
  cycle_employee_total numeric(19,4),
  cycle_employer_total numeric(19,4),
  ledger_set_hash char(64),
  cycle_version_no bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  statutory,
  payroll_ops,
  payroll_calc,
  organisation,
  platform AS $$
DECLARE
  v_existing statutory.statutory_ledger_batch%ROWTYPE;
  v_evaluation_status varchar(20);
  v_evaluation_cycle_id uuid;
  v_evaluation_calculation_request_id uuid;
  v_cycle_status payroll_ops.cycle_status;
  v_cycle_version bigint;
  v_active_calculation_request_id uuid;
  v_active_batch_id uuid;
  v_pay_period_id uuid;
  v_payment_date date;
  v_batch_id uuid := gen_random_uuid();
  v_attempt_no integer;
  v_batch_kind varchar(20);
  v_reversal_count integer := 0;
  v_final record;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_idempotency_key IS NULL
     OR length(btrim(p_idempotency_key)) < 8
     OR length(btrim(p_idempotency_key)) > 120 THEN
    RAISE EXCEPTION
      'idempotency key must contain between 8 and 120 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_request_hash IS NULL
     OR p_request_hash !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'request hash must be a lowercase SHA-256 value'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_posted_at IS NULL THEN
    RAISE EXCEPTION 'posting timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT ledger_batch.*
  INTO v_existing
  FROM statutory.statutory_ledger_batch ledger_batch
  WHERE ledger_batch.tenant_id = p_tenant_id
    AND ledger_batch.idempotency_key = btrim(p_idempotency_key)
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.evaluation_request_id <> p_evaluation_request_id
       OR v_existing.request_hash <> p_request_hash::char(64)
       OR v_existing.batch_kind NOT IN ('INITIAL', 'REPLACEMENT') THEN
      RAISE EXCEPTION
        'idempotency key was already used with a different statutory posting'
        USING ERRCODE = '23505';
    END IF;

    IF v_existing.status = 'COMPLETED' THEN
      RETURN QUERY
      SELECT
        v_existing.id,
        v_existing.attempt_no,
        v_existing.batch_kind,
        v_existing.entry_count,
        v_existing.employee_delta_total,
        v_existing.employer_delta_total,
        v_existing.cycle_employee_total,
        v_existing.cycle_employer_total,
        v_existing.ledger_set_hash,
        (
          SELECT cycle.version_no
          FROM payroll_ops.payroll_cycle cycle
          WHERE cycle.tenant_id = v_existing.tenant_id
            AND cycle.id = v_existing.payroll_cycle_id
        );
      RETURN;
    END IF;

    RAISE EXCEPTION 'statutory posting request is already in progress'
      USING ERRCODE = '40001';
  END IF;

  SELECT
    evaluation.status,
    evaluation.payroll_cycle_id,
    evaluation.calculation_request_id,
    cycle.status,
    cycle.version_no,
    cycle.active_calculation_request_id,
    cycle.active_statutory_ledger_batch_id,
    cycle.pay_period_id,
    period.payment_date
  INTO
    v_evaluation_status,
    v_evaluation_cycle_id,
    v_evaluation_calculation_request_id,
    v_cycle_status,
    v_cycle_version,
    v_active_calculation_request_id,
    v_active_batch_id,
    v_pay_period_id,
    v_payment_date
  FROM statutory.statutory_evaluation_request evaluation
  JOIN payroll_ops.payroll_cycle cycle
    ON cycle.tenant_id = evaluation.tenant_id
   AND cycle.id = evaluation.payroll_cycle_id
  JOIN organisation.pay_period period
    ON period.tenant_id = cycle.tenant_id
   AND period.id = cycle.pay_period_id
  WHERE evaluation.tenant_id = p_tenant_id
    AND evaluation.id = p_evaluation_request_id
  FOR UPDATE OF cycle, evaluation;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory evaluation request does not exist in the current tenant'
      USING ERRCODE = '23503';
  END IF;

  IF v_cycle_version <> p_expected_cycle_version THEN
    RAISE EXCEPTION 'payroll cycle changed since it was read'
      USING ERRCODE = '40001';
  END IF;

  IF v_evaluation_status <> 'COMPLETED'
     OR v_cycle_status <> 'CALCULATED'
     OR v_active_calculation_request_id IS DISTINCT FROM
        v_evaluation_calculation_request_id THEN
    RAISE EXCEPTION
      'statutory posting requires the completed evaluation of the active calculated payroll'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_ledger_batch existing_posting
    WHERE existing_posting.tenant_id = p_tenant_id
      AND existing_posting.evaluation_request_id =
          p_evaluation_request_id
      AND existing_posting.batch_kind IN ('INITIAL', 'REPLACEMENT')
  ) THEN
    RAISE EXCEPTION
      'statutory evaluation is already posted under another idempotency key'
      USING ERRCODE = '23505';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM statutory.statutory_result statutory_result
    JOIN statutory.statutory_input_snapshot statutory_snapshot
      ON statutory_snapshot.tenant_id = statutory_result.tenant_id
     AND statutory_snapshot.id =
         statutory_result.statutory_input_snapshot_id
    JOIN statutory.statutory_rule statutory_rule
      ON statutory_rule.tenant_id = statutory_snapshot.tenant_id
     AND statutory_rule.id = statutory_snapshot.statutory_rule_id
    WHERE statutory_result.tenant_id = p_tenant_id
      AND statutory_result.evaluation_request_id =
          p_evaluation_request_id
      AND (
        SELECT count(*)
        FROM statutory.statutory_balance_year balance_year
        WHERE balance_year.tenant_id = statutory_result.tenant_id
          AND balance_year.jurisdiction_code =
              statutory_rule.jurisdiction_code
          AND balance_year.authority_code =
              statutory_rule.authority_code
          AND balance_year.approval_status = 'APPROVED'
          AND balance_year.period_start <= v_payment_date
          AND balance_year.period_end > v_payment_date
          AND NOT EXISTS (
            SELECT 1
            FROM statutory.statutory_balance_year successor
            WHERE successor.tenant_id = balance_year.tenant_id
              AND successor.supersedes_balance_year_id =
                  balance_year.id
          )
      ) <> 1
  ) THEN
    RAISE EXCEPTION
      'each statutory result requires exactly one approved current balance year covering the payment date'
      USING ERRCODE = '23514';
  END IF;

  SELECT coalesce(max(ledger_batch.attempt_no), 0) + 1
  INTO v_attempt_no
  FROM statutory.statutory_ledger_batch ledger_batch
  WHERE ledger_batch.tenant_id = p_tenant_id
    AND ledger_batch.payroll_cycle_id = v_evaluation_cycle_id;

  IF v_active_batch_id IS NULL THEN
    v_batch_kind := 'INITIAL';
    IF v_attempt_no <> 1 THEN
      RAISE EXCEPTION
        'initial statutory posting requires the first cycle attempt'
        USING ERRCODE = '23514';
    END IF;
  ELSE
    v_batch_kind := 'REPLACEMENT';
    IF NOT EXISTS (
      SELECT 1
      FROM statutory.statutory_ledger_batch active_batch
      WHERE active_batch.tenant_id = p_tenant_id
        AND active_batch.id = v_active_batch_id
        AND active_batch.payroll_cycle_id =
            v_evaluation_cycle_id
        AND active_batch.status = 'COMPLETED'
    ) THEN
      RAISE EXCEPTION
        'active statutory ledger batch is not completed'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  INSERT INTO statutory.statutory_ledger_batch(
    id,
    tenant_id,
    payroll_cycle_id,
    pay_period_id,
    evaluation_request_id,
    calculation_request_id,
    batch_kind,
    attempt_no,
    supersedes_batch_id,
    idempotency_key,
    request_hash,
    status,
    posted_at,
    posted_by,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_batch_id,
    p_tenant_id,
    v_evaluation_cycle_id,
    v_pay_period_id,
    p_evaluation_request_id,
    v_evaluation_calculation_request_id,
    v_batch_kind,
    v_attempt_no,
    v_active_batch_id,
    btrim(p_idempotency_key),
    p_request_hash::char(64),
    'POSTING',
    p_posted_at,
    p_actor,
    p_posted_at,
    p_actor,
    p_posted_at,
    p_actor
  );

  IF v_active_batch_id IS NOT NULL THEN
    WITH sources AS (
      SELECT
        ledger_entry.*,
        row_number() OVER (
          ORDER BY
            source_batch.attempt_no,
            ledger_entry.sequence_no,
            ledger_entry.id
        )::integer AS reversal_sequence
      FROM statutory.statutory_ledger_entry ledger_entry
      JOIN statutory.statutory_ledger_batch source_batch
        ON source_batch.tenant_id = ledger_entry.tenant_id
       AND source_batch.id = ledger_entry.ledger_batch_id
      WHERE ledger_entry.tenant_id = p_tenant_id
        AND ledger_entry.payroll_cycle_id =
            v_evaluation_cycle_id
        AND ledger_entry.entry_kind IN ('EVALUATION', 'CORRECTION')
        AND source_batch.attempt_no >= (
          SELECT max(posting_batch.attempt_no)
          FROM statutory.statutory_ledger_batch posting_batch
          WHERE posting_batch.tenant_id = p_tenant_id
            AND posting_batch.payroll_cycle_id =
                v_evaluation_cycle_id
            AND posting_batch.status = 'COMPLETED'
            AND posting_batch.batch_kind IN ('INITIAL', 'REPLACEMENT')
        )
    ),
    payloads AS (
      SELECT
        sources.*,
        jsonb_build_object(
          'schemaVersion', 1,
          'ledgerBatchId', v_batch_id::text,
          'payrollCycleId', v_evaluation_cycle_id::text,
          'evaluationRequestId', p_evaluation_request_id::text,
          'entryKind', 'REVERSAL',
          'sourceEntryId', sources.id::text,
          'sourceLedgerBatchId', sources.ledger_batch_id::text,
          'sourceEvaluationRequestId',
            sources.source_evaluation_request_id::text,
          'statutoryResultId', sources.statutory_result_id::text,
          'statutoryInputSnapshotId',
            sources.statutory_input_snapshot_id::text,
          'employeeStatutoryProfileId',
            sources.employee_statutory_profile_id::text,
          'employeeStatutoryRuleAssignmentId',
            sources.employee_statutory_rule_assignment_id::text,
          'statutoryRuleId', sources.statutory_rule_id::text,
          'statutoryRuleVersionId',
            sources.statutory_rule_version_id::text,
          'balanceYearId', sources.balance_year_id::text,
          'employeeAmountDelta',
            -sources.employee_amount_delta,
          'employerAmountDelta',
            -sources.employer_amount_delta,
          'currency', sources.currency,
          'reasonCode', 'RECALCULATION_REPLACEMENT',
          'reasonDetail',
            'Reverse prior cycle ledger evidence before replacement evaluation'
        ) AS reversal_payload
      FROM sources
    )
    INSERT INTO statutory.statutory_ledger_entry(
      tenant_id,
      ledger_batch_id,
      payroll_cycle_id,
      pay_period_id,
      evaluation_request_id,
      source_evaluation_request_id,
      statutory_result_id,
      statutory_input_snapshot_id,
      employee_statutory_profile_id,
      employee_statutory_rule_assignment_id,
      statutory_rule_id,
      statutory_rule_version_id,
      balance_year_id,
      jurisdiction_code,
      authority_code,
      sequence_no,
      entry_kind,
      source_entry_id,
      currency,
      employee_amount_delta,
      employer_amount_delta,
      reason_code,
      reason_detail,
      entry_payload,
      entry_hash,
      created_at,
      created_by
    )
    SELECT
      p_tenant_id,
      v_batch_id,
      v_evaluation_cycle_id,
      v_pay_period_id,
      p_evaluation_request_id,
      payloads.source_evaluation_request_id,
      payloads.statutory_result_id,
      payloads.statutory_input_snapshot_id,
      payloads.employee_statutory_profile_id,
      payloads.employee_statutory_rule_assignment_id,
      payloads.statutory_rule_id,
      payloads.statutory_rule_version_id,
      payloads.balance_year_id,
      payloads.jurisdiction_code,
      payloads.authority_code,
      payloads.reversal_sequence,
      'REVERSAL',
      payloads.id,
      payloads.currency,
      -payloads.employee_amount_delta,
      -payloads.employer_amount_delta,
      'RECALCULATION_REPLACEMENT',
      'Reverse prior cycle ledger evidence before replacement evaluation',
      payloads.reversal_payload,
      encode(
        public.digest(
          payloads.reversal_payload::text,
          'sha256'::text
        ),
        'hex'
      ),
      p_posted_at,
      p_actor
    FROM payloads;

    GET DIAGNOSTICS v_reversal_count = ROW_COUNT;
  END IF;

  WITH results AS (
    SELECT
      statutory_result.id AS statutory_result_id,
      statutory_result.statutory_input_snapshot_id,
      statutory_result.employee_amount,
      statutory_result.employer_amount,
      statutory_result.currency,
      statutory_snapshot.employee_statutory_profile_id,
      statutory_snapshot.employee_statutory_rule_assignment_id,
      statutory_snapshot.statutory_rule_id,
      statutory_snapshot.statutory_rule_version_id,
      statutory_rule.jurisdiction_code,
      statutory_rule.authority_code,
      balance_year.id AS balance_year_id,
      row_number() OVER (
        ORDER BY
          statutory_snapshot.employee_statutory_profile_id,
          statutory_snapshot.statutory_rule_id,
          statutory_result.id
      )::integer + v_reversal_count AS posting_sequence
    FROM statutory.statutory_result statutory_result
    JOIN statutory.statutory_input_snapshot statutory_snapshot
      ON statutory_snapshot.tenant_id = statutory_result.tenant_id
     AND statutory_snapshot.id =
         statutory_result.statutory_input_snapshot_id
    JOIN statutory.statutory_rule statutory_rule
      ON statutory_rule.tenant_id = statutory_snapshot.tenant_id
     AND statutory_rule.id = statutory_snapshot.statutory_rule_id
    JOIN statutory.statutory_balance_year balance_year
      ON balance_year.tenant_id = statutory_result.tenant_id
     AND balance_year.jurisdiction_code =
         statutory_rule.jurisdiction_code
     AND balance_year.authority_code =
         statutory_rule.authority_code
     AND balance_year.approval_status = 'APPROVED'
     AND balance_year.period_start <= v_payment_date
     AND balance_year.period_end > v_payment_date
     AND NOT EXISTS (
       SELECT 1
       FROM statutory.statutory_balance_year successor
       WHERE successor.tenant_id = balance_year.tenant_id
         AND successor.supersedes_balance_year_id = balance_year.id
     )
    WHERE statutory_result.tenant_id = p_tenant_id
      AND statutory_result.evaluation_request_id =
          p_evaluation_request_id
  ),
  payloads AS (
    SELECT
      results.*,
      jsonb_build_object(
        'schemaVersion', 1,
        'ledgerBatchId', v_batch_id::text,
        'payrollCycleId', v_evaluation_cycle_id::text,
        'payPeriodId', v_pay_period_id::text,
        'evaluationRequestId', p_evaluation_request_id::text,
        'calculationRequestId',
          v_evaluation_calculation_request_id::text,
        'sourceEvaluationRequestId', p_evaluation_request_id::text,
        'entryKind', 'EVALUATION',
        'statutoryResultId', results.statutory_result_id::text,
        'statutoryInputSnapshotId',
          results.statutory_input_snapshot_id::text,
        'employeeStatutoryProfileId',
          results.employee_statutory_profile_id::text,
        'employeeStatutoryRuleAssignmentId',
          results.employee_statutory_rule_assignment_id::text,
        'statutoryRuleId', results.statutory_rule_id::text,
        'statutoryRuleVersionId',
          results.statutory_rule_version_id::text,
        'balanceYearId', results.balance_year_id::text,
        'jurisdictionCode', results.jurisdiction_code,
        'authorityCode', results.authority_code,
        'currency', results.currency,
        'employeeAmountDelta', results.employee_amount,
        'employerAmountDelta', results.employer_amount,
        'reasonCode', 'EVALUATION'
      ) AS posting_payload
    FROM results
  )
  INSERT INTO statutory.statutory_ledger_entry(
    tenant_id,
    ledger_batch_id,
    payroll_cycle_id,
    pay_period_id,
    evaluation_request_id,
    source_evaluation_request_id,
    statutory_result_id,
    statutory_input_snapshot_id,
    employee_statutory_profile_id,
    employee_statutory_rule_assignment_id,
    statutory_rule_id,
    statutory_rule_version_id,
    balance_year_id,
    jurisdiction_code,
    authority_code,
    sequence_no,
    entry_kind,
    source_entry_id,
    currency,
    employee_amount_delta,
    employer_amount_delta,
    reason_code,
    reason_detail,
    entry_payload,
    entry_hash,
    created_at,
    created_by
  )
  SELECT
    p_tenant_id,
    v_batch_id,
    v_evaluation_cycle_id,
    v_pay_period_id,
    p_evaluation_request_id,
    p_evaluation_request_id,
    payloads.statutory_result_id,
    payloads.statutory_input_snapshot_id,
    payloads.employee_statutory_profile_id,
    payloads.employee_statutory_rule_assignment_id,
    payloads.statutory_rule_id,
    payloads.statutory_rule_version_id,
    payloads.balance_year_id,
    payloads.jurisdiction_code,
    payloads.authority_code,
    payloads.posting_sequence,
    'EVALUATION',
    NULL,
    payloads.currency,
    payloads.employee_amount,
    payloads.employer_amount,
    'EVALUATION',
    NULL,
    payloads.posting_payload,
    encode(
      public.digest(payloads.posting_payload::text, 'sha256'::text),
      'hex'
    ),
    p_posted_at,
    p_actor
  FROM payloads;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'completed statutory evaluation contains no result evidence'
      USING ERRCODE = '23514';
  END IF;

  SELECT *
  INTO v_final
  FROM statutory.finalize_statutory_ledger_batch(
    p_tenant_id,
    v_batch_id,
    v_evaluation_cycle_id,
    p_evaluation_request_id,
    p_expected_cycle_version,
    p_actor,
    p_posted_at
  );

  RETURN QUERY
  SELECT
    v_batch_id,
    v_attempt_no,
    v_batch_kind,
    v_final.posted_entry_count,
    v_final.employee_delta_total,
    v_final.employer_delta_total,
    v_final.cycle_employee_total,
    v_final.cycle_employer_total,
    v_final.ledger_set_hash,
    v_final.cycle_version_no;
END $$;

CREATE OR REPLACE FUNCTION statutory.post_statutory_correction(
  p_tenant_id uuid,
  p_payroll_cycle_id uuid,
  p_statutory_result_id uuid,
  p_employee_amount_delta numeric,
  p_employer_amount_delta numeric,
  p_reason varchar,
  p_expected_cycle_version bigint,
  p_idempotency_key varchar,
  p_request_hash varchar,
  p_actor varchar,
  p_posted_at timestamptz
) RETURNS TABLE (
  ledger_batch_id uuid,
  attempt_no integer,
  posted_entry_count integer,
  employee_delta_total numeric(19,4),
  employer_delta_total numeric(19,4),
  cycle_employee_total numeric(19,4),
  cycle_employer_total numeric(19,4),
  ledger_set_hash char(64),
  cycle_version_no bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path =
  pg_catalog,
  statutory,
  payroll_ops,
  payroll_calc,
  organisation,
  platform AS $$
DECLARE
  v_existing statutory.statutory_ledger_batch%ROWTYPE;
  v_cycle_version bigint;
  v_active_batch_id uuid;
  v_active_evaluation_request_id uuid;
  v_active_calculation_request_id uuid;
  v_pay_period_id uuid;
  v_source_entry statutory.statutory_ledger_entry%ROWTYPE;
  v_batch_id uuid := gen_random_uuid();
  v_attempt_no integer;
  v_correction_payload jsonb;
  v_final record;
BEGIN
  IF p_tenant_id IS DISTINCT FROM platform.current_tenant_id() THEN
    RAISE EXCEPTION 'tenant context mismatch'
      USING ERRCODE = '42501';
  END IF;

  IF p_employee_amount_delta IS NULL
     OR p_employer_amount_delta IS NULL
     OR (
       p_employee_amount_delta = 0
       AND p_employer_amount_delta = 0
     ) THEN
    RAISE EXCEPTION
      'correction requires at least one non-zero signed delta'
      USING ERRCODE = '23514';
  END IF;

  IF p_reason IS NULL
     OR length(btrim(p_reason)) < 8
     OR length(btrim(p_reason)) > 500 THEN
    RAISE EXCEPTION
      'correction reason must contain between 8 and 500 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_idempotency_key IS NULL
     OR length(btrim(p_idempotency_key)) < 8
     OR length(btrim(p_idempotency_key)) > 120 THEN
    RAISE EXCEPTION
      'idempotency key must contain between 8 and 120 characters'
      USING ERRCODE = '23514';
  END IF;

  IF p_request_hash IS NULL
     OR p_request_hash !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'request hash must be a lowercase SHA-256 value'
      USING ERRCODE = '23514';
  END IF;

  IF p_actor IS NULL OR btrim(p_actor) = '' THEN
    RAISE EXCEPTION 'actor is required'
      USING ERRCODE = '23514';
  END IF;

  IF p_posted_at IS NULL THEN
    RAISE EXCEPTION 'posting timestamp is required'
      USING ERRCODE = '23514';
  END IF;

  SELECT ledger_batch.*
  INTO v_existing
  FROM statutory.statutory_ledger_batch ledger_batch
  WHERE ledger_batch.tenant_id = p_tenant_id
    AND ledger_batch.idempotency_key = btrim(p_idempotency_key)
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.payroll_cycle_id <> p_payroll_cycle_id
       OR v_existing.request_hash <> p_request_hash::char(64)
       OR v_existing.batch_kind <> 'CORRECTION' THEN
      RAISE EXCEPTION
        'idempotency key was already used with a different statutory correction'
        USING ERRCODE = '23505';
    END IF;

    IF v_existing.status = 'COMPLETED' THEN
      RETURN QUERY
      SELECT
        v_existing.id,
        v_existing.attempt_no,
        v_existing.entry_count,
        v_existing.employee_delta_total,
        v_existing.employer_delta_total,
        v_existing.cycle_employee_total,
        v_existing.cycle_employer_total,
        v_existing.ledger_set_hash,
        (
          SELECT cycle.version_no
          FROM payroll_ops.payroll_cycle cycle
          WHERE cycle.tenant_id = v_existing.tenant_id
            AND cycle.id = v_existing.payroll_cycle_id
        );
      RETURN;
    END IF;

    RAISE EXCEPTION 'statutory correction request is already in progress'
      USING ERRCODE = '40001';
  END IF;

  SELECT
    cycle.version_no,
    cycle.active_statutory_ledger_batch_id,
    active_batch.evaluation_request_id,
    active_batch.calculation_request_id,
    cycle.pay_period_id
  INTO
    v_cycle_version,
    v_active_batch_id,
    v_active_evaluation_request_id,
    v_active_calculation_request_id,
    v_pay_period_id
  FROM payroll_ops.payroll_cycle cycle
  JOIN statutory.statutory_ledger_batch active_batch
    ON active_batch.tenant_id = cycle.tenant_id
   AND active_batch.id = cycle.active_statutory_ledger_batch_id
   AND active_batch.payroll_cycle_id = cycle.id
   AND active_batch.status = 'COMPLETED'
  WHERE cycle.tenant_id = p_tenant_id
    AND cycle.id = p_payroll_cycle_id
    AND cycle.status = 'CALCULATED'
  FOR UPDATE OF cycle, active_batch;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory correction requires an active completed ledger batch'
      USING ERRCODE = '23514';
  END IF;

  IF v_cycle_version <> p_expected_cycle_version THEN
    RAISE EXCEPTION 'payroll cycle changed since it was read'
      USING ERRCODE = '40001';
  END IF;

  SELECT ledger_entry.*
  INTO v_source_entry
  FROM statutory.statutory_ledger_entry ledger_entry
  WHERE ledger_entry.tenant_id = p_tenant_id
    AND ledger_entry.payroll_cycle_id = p_payroll_cycle_id
    AND ledger_entry.evaluation_request_id =
        v_active_evaluation_request_id
    AND ledger_entry.statutory_result_id =
        p_statutory_result_id
    AND ledger_entry.entry_kind = 'EVALUATION'
  ORDER BY ledger_entry.created_at DESC, ledger_entry.id DESC
  LIMIT 1
  FOR UPDATE OF ledger_entry;

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'statutory result does not belong to the active posted evaluation'
      USING ERRCODE = '23503';
  END IF;

  SELECT coalesce(max(ledger_batch.attempt_no), 0) + 1
  INTO v_attempt_no
  FROM statutory.statutory_ledger_batch ledger_batch
  WHERE ledger_batch.tenant_id = p_tenant_id
    AND ledger_batch.payroll_cycle_id = p_payroll_cycle_id;

  INSERT INTO statutory.statutory_ledger_batch(
    id,
    tenant_id,
    payroll_cycle_id,
    pay_period_id,
    evaluation_request_id,
    calculation_request_id,
    batch_kind,
    attempt_no,
    supersedes_batch_id,
    idempotency_key,
    request_hash,
    status,
    posted_at,
    posted_by,
    created_at,
    created_by,
    updated_at,
    updated_by
  ) VALUES (
    v_batch_id,
    p_tenant_id,
    p_payroll_cycle_id,
    v_pay_period_id,
    v_active_evaluation_request_id,
    v_active_calculation_request_id,
    'CORRECTION',
    v_attempt_no,
    v_active_batch_id,
    btrim(p_idempotency_key),
    p_request_hash::char(64),
    'POSTING',
    p_posted_at,
    p_actor,
    p_posted_at,
    p_actor,
    p_posted_at,
    p_actor
  );

  v_correction_payload := jsonb_build_object(
    'schemaVersion', 1,
    'ledgerBatchId', v_batch_id::text,
    'payrollCycleId', p_payroll_cycle_id::text,
    'payPeriodId', v_pay_period_id::text,
    'evaluationRequestId', v_active_evaluation_request_id::text,
    'sourceEvaluationRequestId',
      v_active_evaluation_request_id::text,
    'calculationRequestId', v_active_calculation_request_id::text,
    'entryKind', 'CORRECTION',
    'sourceEntryId', v_source_entry.id::text,
    'statutoryResultId', v_source_entry.statutory_result_id::text,
    'statutoryInputSnapshotId',
      v_source_entry.statutory_input_snapshot_id::text,
    'employeeStatutoryProfileId',
      v_source_entry.employee_statutory_profile_id::text,
    'employeeStatutoryRuleAssignmentId',
      v_source_entry.employee_statutory_rule_assignment_id::text,
    'statutoryRuleId', v_source_entry.statutory_rule_id::text,
    'statutoryRuleVersionId',
      v_source_entry.statutory_rule_version_id::text,
    'balanceYearId', v_source_entry.balance_year_id::text,
    'jurisdictionCode', v_source_entry.jurisdiction_code,
    'authorityCode', v_source_entry.authority_code,
    'currency', v_source_entry.currency,
    'employeeAmountDelta', p_employee_amount_delta,
    'employerAmountDelta', p_employer_amount_delta,
    'reasonCode', 'CORRECTION',
    'reasonDetail', btrim(p_reason)
  );

  INSERT INTO statutory.statutory_ledger_entry(
    tenant_id,
    ledger_batch_id,
    payroll_cycle_id,
    pay_period_id,
    evaluation_request_id,
    source_evaluation_request_id,
    statutory_result_id,
    statutory_input_snapshot_id,
    employee_statutory_profile_id,
    employee_statutory_rule_assignment_id,
    statutory_rule_id,
    statutory_rule_version_id,
    balance_year_id,
    jurisdiction_code,
    authority_code,
    sequence_no,
    entry_kind,
    source_entry_id,
    currency,
    employee_amount_delta,
    employer_amount_delta,
    reason_code,
    reason_detail,
    entry_payload,
    entry_hash,
    created_at,
    created_by
  ) VALUES (
    p_tenant_id,
    v_batch_id,
    p_payroll_cycle_id,
    v_pay_period_id,
    v_active_evaluation_request_id,
    v_active_evaluation_request_id,
    v_source_entry.statutory_result_id,
    v_source_entry.statutory_input_snapshot_id,
    v_source_entry.employee_statutory_profile_id,
    v_source_entry.employee_statutory_rule_assignment_id,
    v_source_entry.statutory_rule_id,
    v_source_entry.statutory_rule_version_id,
    v_source_entry.balance_year_id,
    v_source_entry.jurisdiction_code,
    v_source_entry.authority_code,
    1,
    'CORRECTION',
    v_source_entry.id,
    v_source_entry.currency,
    p_employee_amount_delta,
    p_employer_amount_delta,
    'CORRECTION',
    btrim(p_reason),
    v_correction_payload,
    encode(
      public.digest(v_correction_payload::text, 'sha256'::text),
      'hex'
    ),
    p_posted_at,
    p_actor
  );

  SELECT *
  INTO v_final
  FROM statutory.finalize_statutory_ledger_batch(
    p_tenant_id,
    v_batch_id,
    p_payroll_cycle_id,
    v_active_evaluation_request_id,
    p_expected_cycle_version,
    p_actor,
    p_posted_at
  );

  RETURN QUERY
  SELECT
    v_batch_id,
    v_attempt_no,
    v_final.posted_entry_count,
    v_final.employee_delta_total,
    v_final.employer_delta_total,
    v_final.cycle_employee_total,
    v_final.cycle_employer_total,
    v_final.ledger_set_hash,
    v_final.cycle_version_no;
END $$;

REVOKE ALL ON FUNCTION statutory.approve_statutory_balance_year(
  uuid,
  uuid,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION statutory.end_date_statutory_balance_year(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION statutory.post_statutory_evaluation(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

REVOKE ALL ON FUNCTION statutory.post_statutory_correction(
  uuid,
  uuid,
  uuid,
  numeric,
  numeric,
  varchar,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) FROM PUBLIC;

GRANT SELECT, INSERT
  ON statutory.statutory_balance_year
  TO payroll_app;

GRANT SELECT
  ON statutory.statutory_ledger_batch,
     statutory.statutory_ledger_entry,
     statutory.statutory_balance_snapshot,
     statutory.statutory_reconciliation,
     statutory.statutory_remittance_summary
  TO payroll_app;

REVOKE UPDATE, DELETE
  ON statutory.statutory_balance_year
  FROM payroll_app;

REVOKE INSERT, UPDATE, DELETE
  ON statutory.statutory_ledger_batch,
     statutory.statutory_ledger_entry,
     statutory.statutory_balance_snapshot,
     statutory.statutory_reconciliation,
     statutory.statutory_remittance_summary
  FROM payroll_app;

GRANT EXECUTE ON FUNCTION statutory.approve_statutory_balance_year(
  uuid,
  uuid,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION statutory.end_date_statutory_balance_year(
  uuid,
  uuid,
  date,
  bigint,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION statutory.post_statutory_evaluation(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

GRANT EXECUTE ON FUNCTION statutory.post_statutory_correction(
  uuid,
  uuid,
  uuid,
  numeric,
  numeric,
  varchar,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) TO payroll_app;

DO $$
DECLARE
  v_table_name text;
BEGIN
  FOREACH v_table_name IN ARRAY ARRAY[
    'statutory_balance_year',
    'statutory_ledger_batch',
    'statutory_ledger_entry',
    'statutory_balance_snapshot',
    'statutory_reconciliation',
    'statutory_remittance_summary'
  ]
  LOOP
    EXECUTE format(
      'ALTER TABLE statutory.%I ENABLE ROW LEVEL SECURITY',
      v_table_name
    );
    EXECUTE format(
      'ALTER TABLE statutory.%I FORCE ROW LEVEL SECURITY',
      v_table_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON statutory.%I '
        || 'USING (tenant_id = platform.current_tenant_id()) '
        || 'WITH CHECK (tenant_id = platform.current_tenant_id())',
      v_table_name
    );
  END LOOP;
END $$;

REVOKE CREATE ON SCHEMA statutory FROM payroll_app;

COMMENT ON TABLE statutory.statutory_balance_year IS
  'Approved jurisdiction/authority balance-year boundaries used for deterministic YTD grouping.';
COMMENT ON TABLE statutory.statutory_ledger_batch IS
  'Controlled append-only posting attempt for initial evaluation, recalculation replacement or correction.';
COMMENT ON TABLE statutory.statutory_ledger_entry IS
  'Immutable signed statutory accounting event with exact evaluation, result, profile, rule and balance-year lineage.';
COMMENT ON TABLE statutory.statutory_balance_snapshot IS
  'Immutable PTD, cycle and YTD snapshot derived from statutory ledger entries after one completed batch.';
COMMENT ON TABLE statutory.statutory_reconciliation IS
  'Immutable zero-variance comparison between source evaluation, approved corrections and cycle ledger totals.';
COMMENT ON TABLE statutory.statutory_remittance_summary IS
  'Immutable authority/rule period and YTD liability position prepared for later remittance workflows.';
COMMENT ON FUNCTION statutory.post_statutory_evaluation(
  uuid,
  uuid,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Posts a completed active statutory evaluation; reverses prior cycle ledger evidence when replacing a recalculated evaluation.';
COMMENT ON FUNCTION statutory.post_statutory_correction(
  uuid,
  uuid,
  uuid,
  numeric,
  numeric,
  varchar,
  bigint,
  varchar,
  varchar,
  varchar,
  timestamptz
) IS
  'Posts one signed correction against an exact result in the active statutory evaluation and regenerates reconciled PTD/YTD evidence.';
