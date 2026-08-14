# P5-E2E-UI-01-B02-R01 — Routing Rule Correction/End-Dating Database Contract Amendment

**Status:** CLOSED — database contract published through PR #66
**Parent capability:** P5-E2E-UI-01-B02 — Remaining E02 Contract Boundary Amendment  
**Authority baseline:** activation merge `3c42d057e4e0bf941af7589d62087721bf88ea81`
**Migration position after closure:** V001-V039 immutable; V040 remains unreserved
**P5-A5/E03:** NOT ACTIVATED

## 1. Trigger

B02-G01 implementation preflight proved that calendar configuration,
compatibility/readiness exposure and routing inspection/create can use the
existing V038 foundation, but safe routing-rule correction/effective end-dating
cannot.

V038 provides:

- `organisation.create_pay_group_routing_rule`;
- `organisation.retire_pay_group_routing_rule`;
- read access to `organisation.pay_group_routing_rule`;
- deterministic routing resolution and compatibility functions;
- tenant/RLS protection.

V038 does not provide a function that can safely effective-end an existing
routing rule. Direct INSERT/UPDATE/DELETE on the routing table is revoked from
`payroll_app`.

The parent B02 scope explicitly requires this sub-boundary to stop and return
for separately reviewed amendment authority.

## 2. R01 purpose

R01 authorizes only the smallest database-contract addition needed to express a
safe effective-end/correction workflow for an existing routing rule.

After this activation merges, R01-G01 may reserve V039 for that exact contract
if live/local migration preflight still proves V039 is the next unreserved
migration.

The intended correction model is:

1. effective-end the existing ACTIVE routing rule through a governed database
   function with optimistic version control;
2. when replacement data is needed, create the replacement through the existing
   `organisation.create_pay_group_routing_rule` contract;
3. preserve existing deterministic resolution and overlap constraints.

R01 does not authorize arbitrary direct UPDATE of routing-rule business
attributes.

## 3. Authorized database-contract boundary

R01-G01 may add a single bounded routing-rule effective-end function contract,
with the exact SQL name finalized during implementation artifact-contract
preflight.

The function must:

- require current tenant context and reject tenant mismatch;
- require a nonblank actor;
- operate only on the addressed tenant/rule;
- use `version_no` optimistic concurrency;
- validate the requested effective end against the rule effective start;
- preserve the existing dependency trigger and exclusion constraints;
- update only the minimum lifecycle/effective-end and audit/version fields
  required by the contract;
- return deterministic affected-row/result evidence;
- preserve direct table DML revocation for `payroll_app`;
- grant only the minimum EXECUTE privilege required by `payroll_app`.

The implementation must prefer an effective-end function plus the existing
create function over a broad generic update function.

## 4. Migration authority

This activation does not create or reserve a migration.

Only after this activation merges, R01-G01 may reserve V039 if and only if:

- local and remote `main` still show V001-V038 immutable;
- no other capability has reserved V039;
- the implementation remains limited to the function/grant/test contract in
  this scope.

R01 does not authorize:

- new tables;
- new columns;
- new indexes;
- data backfill;
- history rewrite;
- changes to existing migration files;
- routing policy redesign.

If any of those becomes necessary, R01-G01 must stop for separate review.

## 5. Security and audit requirements

R01-G01 must preserve:

- forced tenant isolation/RLS;
- current `payroll_app` direct DML revocations;
- actor/audit conventions;
- optimistic concurrency;
- least privilege;
- deterministic effective/ranked routing semantics.

No new application permission is authorized by R01 activation.

## 6. Required R01-G01 evidence

Before R01 database-contract publication:

- migration position preflight proves V039 is still unreserved;
- repository PowerShell/package gates pass;
- Flyway applies V001 through the newly authorized migration on the isolated
  PostgreSQL fixture;
- database-contract tests prove successful end-dating;
- invalid effective range is rejected;
- stale `version_no` is rejected/deterministically returns no update;
- cross-tenant access is rejected;
- inactive/non-addressable rule behavior is deterministic;
- direct routing-table DML remains unavailable to `payroll_app`;
- existing create/retire/resolve/compatibility behavior remains green;
- full affected Maven/reactor validation remains green.

Hosted backend publication remains a later, separate gate.

## 7. Explicit exclusions

R01 does not authorize:

- calendar milestone/holiday Java or HTTP implementation;
- compatibility/readiness Java or HTTP implementation;
- routing Java or HTTP implementation;
- UI work;
- P5-A5/E03;
- payroll calculation/statutory/payment expansion;
- arbitrary routing-rule mutation;
- direct routing-table DML grants;
- any migration beyond the single bounded R01 contract.

Those B02-G01 product contracts resume only after the R01 database contract is
published.

## 8. Activation success criteria

Activation is complete only when:

1. this R01 scope and bounded parent governance reconciliation merge through
   hosted CI;
2. no product/database code is changed by activation;
3. V039 remains unreserved in the activation commit;
4. story totals remain unchanged;
5. P5-A5/E03 remains inactive;
6. the next controlled action is R01-G01 database-contract implementation
   preflight against the activation merge SHA.

## 9. Next controlled action after activation merge

Run R01-G01 database-contract implementation preflight. If V039 is still the
next unreserved migration, reserve it only for the bounded routing effective-end
function/grant/tests described above.

<!-- P5-E2E-UI-01-B02-R01-G01-CLOSURE -->
## 10. R01-G01 closure evidence

The preflight chain closed with v1.1 as the authoritative evidence, superseding
v1.0. It proved local/remote main at the activation merge, V039 unreserved, and
the exact bounded migration/function/grant/test contract.

The implementation commit `6d528362b6d9ccb5066f5c033caa8035b0f6ab82`
merged through PR #66 at `246ca75983b37293b74fdb4baa44e093fa546f8f`.
Evidence proves:

- successful effective end-dating with optimistic concurrency;
- deterministic stale, inactive and non-addressable outcomes;
- invalid-range and tenant-mismatch rejection;
- replacement routing after the prior range is shortened;
- actor, timestamp and version audit updates;
- direct routing-table DML remains unavailable to `payroll_app`;
- targeted 7/7 migration tests, full Maven verification and all seven hosted
  backend checks passed.

R01-G01 is CLOSED and owns no further write. V039 is committed and immutable;
V040 is unreserved. Story totals remain unchanged because this database-only
amendment does not complete the parent end-to-end UI stories. P5-A5/E03 remains
inactive.

The parent B02-G01 Java/HTTP implementation may resume only after this closure
reconciliation merges, using `246ca75983b37293b74fdb4baa44e093fa546f8f` as
its backend product baseline.
