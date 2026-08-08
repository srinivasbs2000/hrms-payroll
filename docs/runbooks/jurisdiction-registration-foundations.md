# Jurisdiction & Registration Foundations Runbook

**Capability:** P5-JRF-01
**State:** Local implementation verified; publication pending

## Operating model

The capability spans:

- `organisation`: work locations, payroll jurisdictions, overrides and
  deterministic resolution/evidence;
- `statutory-deductions`: registration type metadata, registration lifecycle,
  parent lineage and bounded readiness;
- `payroll-boot`: composition and secured PostgreSQL integration evidence;
- React: Organisation Setup and Statutory Registration operator workspaces.

No additional Maven module is introduced.

## Jurisdiction resolution

Generic precedence:

1. approved explicit override;
2. approved effective work-location jurisdiction;
3. establishment-derived fallback;
4. unresolved.

A material work-location/establishment disagreement is a conflict and must not
be silently resolved.

Resolution evidence preserves exact versions, status, source, findings,
fingerprints, actor and time.

## Registration lifecycle

Normal lifecycle:

`DRAFT -> PENDING_VERIFICATION -> VERIFIED -> APPROVAL_PENDING -> ACTIVE`

Additional states include `REJECTED`, `SUSPENDED`, `EXPIRED` and `SUPERSEDED`.

Controls:

- maker submits;
- verifier is independent from maker;
- final approver is independent from maker and verifier;
- rejection retains reason/evidence reference;
- renewal creates a successor version;
- future/unapproved successors do not hide the current effective active version.

## Identifier handling

Registration-type identifier patterns use `JAVA_REGEX_V1` whole-string Java
matching. PostgreSQL stores the metadata but does not execute the business regex.

Routine API/UI reads are masked. Exact reveal requires
`statutory-registration.identifier.read` and is explicitly audited. Do not put
exact identifiers in URLs, logs, errors, audit payloads or outbox payloads.

## Parent registration

A child references an exact parent registration version. Parent jurisdiction
must be identical to or an approved ancestor of the child jurisdiction.

Readiness blocks when the exact parent is no longer effective/active.

## Runtime database privilege

Tenant-owned JRF tables retain FORCE RLS. Runtime direct UPDATE is not granted
just to obtain row locks. Successor creation uses narrow tenant-checked
controlled row-lock functions and EXECUTE-only runtime permission.

## Failure handling

If an API returns 401/403:

1. distinguish Spring Security permission failure from database SQLSTATE 42501;
2. verify the endpoint's declared permission;
3. verify the runtime role still has only the approved grants;
4. do not broaden table UPDATE privileges as a convenience fix.

If current approved configuration disappears after a future draft is created,
treat that as a query-semantics defect: drafts must not hide the currently
effective approved/active version.

## Verification

Pre-publication authority is the G03-C log under:

`C:\dev\hrms-payroll-artifacts\P5-JRF-01-G03-C\verify-g03-c-v1.0.log`

After publication, normal repository CI becomes the durable verification
authority.
