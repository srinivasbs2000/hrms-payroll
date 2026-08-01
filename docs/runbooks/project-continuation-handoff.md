# HRMS Payroll Project Continuation Handoff

**Repository revision:** Cross-thread Phase A Lite reconciliation prepared
**Updated:** 1 August 2026
**Repository:** `srinivasbs2000/hrms-payroll`
**Local repository:** `C:\dev\hrms-payroll`
**Mandatory status:** Read this after `AGENTS.md` and validate it against live local and remote evidence.

## 1. Current verified checkpoint

| Item | Current fact | Evidence class |
|---|---|---|
| Current remote `main` | `4b5da975eb851434957667bdecf138ea9b43f929` | VERIFIED - REMOTE |
| Current repository commit | PR #20 living-design/thread-governance merge | VERIFIED - REMOTE |
| Current product implementation baseline | Sprint 4 merge `def3dd2e212f85c440eee5497e292be2f1f2bf64` | VERIFIED - REMOTE |
| Latest merged product sprint | Sprint 4 | VERIFIED - REMOTE |
| Open pull requests | None established at the reconciliation cut-off | VERIFIED - REMOTE |
| Latest verified CI | `payroll-baseline` run 83 succeeded on PR #20 head `20935aa4f73dc7e6262cf4bf5f82a3d0b81c2395` | VERIFIED - REMOTE |
| CI directly on merge commit `4b5da975...` | No run returned by the available lookup | NOT VERIFIED |
| Migrations | V001-V030 committed and immutable | VERIFIED - REMOTE |
| Next possible migration | V031, unreserved | DESIGN BASELINE |
| Current active implementation branch | None | VERIFIED - REMOTE |
| Current local Phase A branch after package application | `docs/cross-thread-reconciliation` at `4b5da975...` | EXPECTED LOCAL STATE |
| Current local Phase A working tree after package application | Exact approved 8-file Phase A Lite authority/history subset modified; Git index empty | EXPECTED LOCAL STATE |
| Current persistent PostgreSQL/Flyway state | Not supplied for this reconciliation | NOT VERIFIED |

The repository HEAD and product implementation baseline are deliberately
recorded separately. The governance merge changed repository documentation but
did not add a later product sprint.

## 2. Current stage

**Stage:** Phase A Lite documentation-only cross-thread reconciliation.

**Active owner:** Thread 1, `RECOVERY/HANDOFF`, limited to the exact Phase A
documentation allow-list in `docs/governance/thread-registry.md`.

**Current goal:** publish the reconciled Thread 2-5 history, normalize the
thread registry and running handoff, preserve final Sprint 4 closure evidence
and unblock the S4-06A implementation transition.

Mojibake cleanup, master-design metadata, decision-register indexing, README,
AGENTS, backlog and manual-smoke wording are deliberately deferred so
governance maintenance does not continue to block business-value work.

**Next proposed implementation:** S4-06A statutory API integration closure in a
new Thread 6. S4-06B remains planned but is not authorised for implementation.
No Sprint 5 feature has been selected.

## 3. Thread disposition

| Thread | Current disposition |
|---|---|
| Thread 1 | Active Phase A governance/recovery owner |
| Thread 2 | CLOSED |
| Thread 3 | CLOSED |
| Thread 4 | CLOSED |
| Thread 5 | RECOVERY/HANDOFF, no write ownership; close after transfer |
| Thread 6 | Planned and inactive until Phase A merge and explicit ownership transfer |

## 4. Verified delivery baseline

| Sprint | Durable outcome | Migration range | Status |
|---|---|---|---|
| Sprint 0 | Repository, security, tenancy, migration and vertical-slice baseline | V001-V013 | Merged |
| Sprint 1 | Organisation lifecycle, event reliability, audit and architecture boundaries | V014-V016 | Merged through PR #2 |
| Sprint 2 | Payroll configuration and employee-payroll foundation | V017-V022 | Merged through PR #3 |
| Sprint 3 | Payroll execution, sealed inputs, deterministic calculation, recalculation, draft payslip and browser E2E | V023-V026 | Merged through PR #18 |
| Sprint 4 | Jurisdiction-neutral statutory rules, profiles, evaluation, ledger, balances, reconciliation, API/UI and exact money | V027-V030 | Merged through PR #19 |
| Governance | Master design, decision register, registry and thread protocol | None | Merged through PR #20 |

## 5. Open controlled debt

1. **S4-06A:** no dedicated Spring Boot/PostgreSQL 17 statutory real-HTTP
   integration test currently proves the complete secured statutory lifecycle
   and resulting database evidence.
2. **S4-06B:** no dedicated statutory Playwright full-stack scenario currently
   extends the generic Payroll browser suite.
3. The committed Sprint 4 manual-smoke checklist has no completed signed tester
   and reviewer record. PR #19 metadata records successful checks, but a blank
   template is not equivalent to durable signed evidence.
4. Cached/scheduled OWASP Dependency Check data remains follow-up work.
5. Production broker replay, alerting and operational controls remain debt.
6. Country-specific legal statutory rules, returns, settlement, retro,
   off-cycle, final settlement, payments, accounting and legal payslips remain
   excluded.

## 6. S4-06A approved design boundary

**Story:** S4-06A - Statutory API Integration Closure.
**Proposed branch:** `quality/s4-06a-statutory-api-integration`.
**Migration reservation:** `NONE`.
**Initial production-code changes:** prohibited.
**Initial dependency, migration, OpenAPI, Keycloak, frontend and CI changes:** prohibited.

Initial implementation allow-list after Thread 6 is activated:

1. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/StatutoryApiIT.java`
2. `docs/quality/s4-06a-statutory-api-integration.md`
3. `docs/runbooks/project-continuation-handoff.md`
4. `docs/governance/thread-registry.md`

If the test exposes a production defect, Thread 6 must stop, record the evidence
and request a separate bounded increment. It must not fix the defect inside the
test-only scope without approval.

## 7. Source precedence

1. Current local working tree and complete uncommitted diff.
2. Current remote branch, commit, PR and CI evidence.
3. Committed migrations, code, tests, OpenAPI, ADRs, backlog, README,
   `AGENTS.md`, CI and security policies.
4. Master design and decision register.
5. This running handoff.
6. Committed quality reports and history records.
7. Conversation summaries only as locators.

Record disagreements as `DOCUMENTATION CONFLICT`. Mark unavailable evidence as
`NOT VERIFIED`. Do not silently resolve or infer missing state.

## 8. PowerShell output-shape rule

Phase A package v1.0 exposed a recurring PowerShell failure: a function that
returned one Git output line collapsed to a scalar string, and direct `[0]`
indexing returned `System.Char`; `.Trim()` then failed.

Permanent rule for every project thread and generated script:

- capture variable-cardinality output with `@(...)`;
- validate its exact cardinality;
- cast the selected element to `[string]`;
- only then invoke string methods;
- keep logging out of data-producing pipelines;
- runtime-test zero, one and many output cases in the actual PowerShell host.

This rule is binding through this running handoff, which every continuation
thread must read. Formal AGENTS/decision-register indexing is deferred and must
not block S4-06A.

### Structural repository-update rule

Phase A package v1.1 exposed another recurring generated-script failure: an
exact full-line Markdown preimage did not match because the committed row used
different Unicode punctuation. The intended document structure was present,
but the updater treated byte-level text identity as the contract.

Permanent rule for repository update automation:

- prefer complete payload replacement from a pinned commit/blob;
- otherwise update a marker-bounded block or a unique heading/metadata prefix;
- normalize line endings before structural matching;
- require exactly one structural anchor and report missing/ambiguous anchors;
- do not use exact full-line/full-paragraph preimages in living documents;
- keep the operation idempotent and roll back all file writes on failure;
- provide a plan-only mode that resolves all anchors and validates the allow-list without writing;
- test the updater against the exact pinned repository base before publication.

This rule is binding through this running handoff. Formal AGENTS/decision-
register indexing is deferred and must not block S4-06A.

### Native command exit-code and whitespace rule

Phase A package v1.2 exposed two more automation defects:

1. harmless Git CRLF warnings written to stderr were treated as command failure
   even though the native process could succeed;
2. Markdown hard-break spaces were generated while `git diff --check` correctly
   rejects trailing whitespace.

Permanent rule for every generated command wrapper:

- determine native-command success from its exit code, not from the presence of
  stderr text;
- preserve stderr as diagnostics and throw only when the exit code is non-zero;
- do not generate Markdown hard line breaks using trailing spaces; use blank
  lines or explicit markup where a break is necessary;
- run a trailing-whitespace scan before packaging;
- prefer deterministic full-file payload copy over generated patch logic.

These rules are binding through this handoff and are carried in the Thread 5
failure register.

## 9. Phase A verification checkpoint

After copying the Phase A Lite payload and before any staging action, verify:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --check
git diff --name-only
git diff --cached --name-only
```

Expected:

- branch `docs/cross-thread-reconciliation`;
- HEAD `4b5da975eb851434957667bdecf138ea9b43f929`;
- only the approved 8 Phase A Lite paths differ;
- `git diff --check` passes;
- Git index remains empty.

## 10. Prohibited actions at this checkpoint

Until separately authorised:

- do not stage;
- do not commit;
- do not push;
- do not create or update a pull request;
- do not merge;
- do not reserve V031;
- do not begin S4-06A or S4-06B implementation;
- do not modify application code, migrations, dependencies, contracts,
  Keycloak, frontend or CI;
- do not delete historical branches.

## 11. Durable history

- `docs/history/thread-1-decision-extract.md`
- `docs/history/thread-2-reconciliation.md`
- `docs/history/thread-3-reconciliation.md`
- `docs/history/thread-4-reconciliation.md`
- `docs/history/thread-5-reconciliation.md`
- `docs/history/cross-thread-project-restart-record.md`

Historical continuation cards and thread checkpoints are evidence, not current
state. Current live evidence and this normalized checkpoint supersede the old
pre-merge PR #19 card.

## 12. Next authorised action

Copy and verify the deterministic Phase A Lite full-file payload on the dedicated branch.
Then present the unstaged diff and verification result for review. Staging,
commit, push, PR creation and merge remain separate future decisions.
