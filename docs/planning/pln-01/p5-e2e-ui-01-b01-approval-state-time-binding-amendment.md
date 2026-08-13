# P5-E2E-UI-01-B01 — Approval Lifecycle Timestamptz Binding Amendment

**Status:** locally activated by the B01 authority commit before implementation
**Parent capability:** P5-E2E-UI-01
**Backend baseline:** `92cc4af17d631530fb34435fedb80a87aeeda551`
**UI working baseline:** existing uncommitted P5-E2E-UI-01 nine-file product state in `hrms-payroll-web`
**Migration:** NONE; V039 remains unreserved
**OpenAPI:** unchanged
**Permissions:** unchanged

## 1. Demonstrated defect

Real-browser P5-E2E-UI-01 execution reached approval-delegation revocation and
received HTTP 500. The backend stack trace proves PostgreSQL JDBC cannot infer a
SQL type for `java.time.Instant` supplied to
`security.revoke_approval_delegation(..., p_changed_at timestamptz)`.

Independent source review proves the same raw `Instant changedAt` binding is
used by all three V037 state functions:

- `security.suspend_approval_authority`;
- `security.retire_approval_authority`;
- `security.revoke_approval_delegation`.

This is a backend implementation defect exposed by the authorized UI E2E. It is
not a UI defect and does not require a schema or contract change.

## 2. Exact backend product authority

After the B01 activation commit, backend product writes are authorized only to:

1. `backend/security/src/main/java/com/acme/hrms/payroll/security/internal/infrastructure/ApprovalAuthorityRepository.java`
2. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/ApprovalAuthorityEnforcementApiIT.java`

No other backend product path is authorized.

## 3. Required implementation

Preserve the application `Instant` clock model. At the JDBC boundary only,
convert each state-function `changedAt` value to UTC `OffsetDateTime` using
`changedAt.atOffset(ZoneOffset.UTC)` so PostgreSQL JDBC can bind the existing
`timestamptz` parameter without changing the represented instant.

Do not modify V037 or any migration. Do not change endpoint semantics, OpenAPI,
permissions, RLS, actor identity, idempotency or optimistic locking.

## 4. Required proof

The existing `ApprovalAuthorityEnforcementApiIT` must gain real-PostgreSQL API
coverage proving all three state paths execute successfully:

- authority suspension;
- authority retirement after suspension;
- delegation revocation by its delegator.

Then run:

1. full backend Maven `verify`;
2. explicit failsafe-report proof that the new lifecycle test executed green;
3. existing `p5-e2e-ui-01` browser project against the exact local B01 backend
   product commit and the preserved UI working tree.

## 5. Publication and closure

This authority allows local activation and local implementation commits only.
It does not authorize push, PR creation, merge or story closure.

After local B01 validation is green:

1. independently review the exact backend diff;
2. publish B01 through a dedicated backend PR and hosted CI;
3. merge B01;
4. rerun P5-E2E-UI-01 browser evidence against the authoritative merged backend;
5. continue the existing UI branch without replaying already-green UI gates.

V039 remains unreserved throughout.