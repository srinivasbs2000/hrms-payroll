# P5-EIP-01 G02A — Hosted CI Recovery Amendment Authority

**Status:** TEMPORARY RECOVERY AUTHORITY — becomes effective only after this governance PR merges
**Capability:** P5-EIP-01 — Employee Identity, Bank & Payment Readiness
**Gate:** G02A hosted-CI recovery for existing backend PR #88
**Authority base:** backend/program `main` `2ea18e662a40df80389d5cfd4296fc6bbe2485f6`
**Existing PR branch before recovery publication:** `feature/p5-eip-01-g02a-identity-payment-readiness` at `45a5841fb108c25b4fb4a91ce532c6fd2cbe83b2`
**Reviewed local recovery commit:** `74c649fa4b9d7df34da4c7b7b4836e3787215305`
**Migration:** V001–V050 immutable; V051 remains reserved exclusively to P5-EIP-01 G02
**Product scope/status:** unchanged

## 1. Reason for this amendment

The merged P5-EIP-01 G01 authority intentionally limited G02 product ownership to
the exact paths in
`docs/planning/pln-01/p5-eip-01-employee-identity-bank-payment-readiness-scope.md`.

During hosted-CI recovery, local evidence proved the P5-EIP-01 product increment,
V051, OpenAPI contracts and architecture tests are technically green, but the
hosted Maven environment also requires employee-sensitive synthetic key material
for the test process. The bounded recovery uses the existing test-only
configuration contract instead of weakening production cryptography or
reintroducing a cross-module test mock.

The required CI-path change is outside the original G02 product allow-list.
This amendment therefore authorizes that path explicitly rather than widening
ownership silently.

## 2. Exact temporary recovery write authority

After this authority merges, P5-EIP-01 G02A may publish **only the exact reviewed
recovery commit**:

`74c649fa4b9d7df34da4c7b7b4836e3787215305`

Its recovery delta relative to
`45a5841fb108c25b4fb4a91ce532c6fd2cbe83b2` is limited to:

1. `.github/workflows/ci.yml`
   - keep the Maven command exactly `./mvnw --batch-mode verify`;
   - supply only these test-process environment contracts:
     - `PAYROLL_EMPLOYEE_SENSITIVE_ACTIVE_KEY_VERSION`;
     - `PAYROLL_EMPLOYEE_SENSITIVE_ENCRYPTION_KEYS`;
     - `PAYROLL_EMPLOYEE_SENSITIVE_FINGERPRINT_KEY`;
   - derive synthetic non-production key bytes at workflow runtime;
   - do not add a production secret, repository secret requirement or plaintext
     employee identifier/bank value;
   - do not change Maven project selection, test selection, branch protection,
     security scans, Flyway jobs, OpenAPI jobs, dependency review, SBOM or other
     workflow jobs.

2. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/EmployeePayrollApiIT.java`
   - restore the exact original G02A product blob
     `c0178ed5c486c86644314c8447e5b9bd6baf845a`;
   - do not import or mock `employee-payroll.internal.*` implementation classes.

No other path is added to G02A ownership by this amendment.

## 3. Evidence already required and satisfied locally

Before this authority was created, the reviewed local recovery state proved:

- governed worktree branch:
  `feature/p5-eip-01-g02a-identity-payment-readiness`;
- local recovery HEAD:
  `74c649fa4b9d7df34da4c7b7b4836e3787215305`;
- clean worktree and `git diff --check`;
- V051 blob unchanged by the recovery commit;
- both employee-payroll OpenAPI contract blobs unchanged by the recovery commit;
- `EmployeePayrollApiIT` restored to the original G02A blob;
- full root Maven `verify` exit code `0` and `BUILD SUCCESS`;
- `ArchitectureRulesTest` 3/3 green;
- `EmployeePayrollApiIT` 4/4 green under Failsafe;
- independent technical review green.

These facts authorize no GitHub mutation by themselves; they are prerequisites
for this bounded amendment.

## 4. Publication sequence after this authority merges

1. Revalidate live `main`, PR #88 branch head and local recovery HEAD.
2. Publish only existing commit
   `74c649fa4b9d7df34da4c7b7b4836e3787215305`
   to the existing PR #88 branch.
3. Do not create another G02A product PR.
4. Evaluate the exact hosted PR #88 workflow/check set.
5. If hosted CI is green, perform the separately controlled exact-head merge.
6. If hosted CI fails and any additional `.github/workflows/ci.yml` modification
   would be required, stop and obtain a new bounded amendment. Do not extend this
   authority by inference.

## 5. Explicit non-authority

This amendment does **not**:

- widen P5-EIP-01 business scope;
- change any canonical story status;
- authorize G02B UI work before G02A merge;
- authorize V052;
- change V051;
- authorize organisation, compensation, statutory, calculation or payment-
  execution product changes;
- authorize production cryptography weakening;
- authorize static production secrets;
- authorize force-push, rebase, reset, clean or history rewriting;
- authorize a second G02A PR;
- authorize merge before hosted checks are green.

## 6. Expiry

This temporary recovery authority expires when the exact reviewed recovery
commit has been published to PR #88 and its hosted result has been reconciled.
After PR #88 merges, `.github/workflows/ci.yml` returns to normal repository-wide
governance ownership. Any later change requires its own authority.

The underlying P5-EIP-01 G02 product allow-list remains unchanged.
