# P5-EOR-01 — Employee Payroll Onboarding, Readiness, Holds & Snapshot Completion

**Authority type:** GOV-01 fast-lane G01 + activation + implementation authority
**Capability:** `P5-EOR-01`
**G01 verdict:** `PASS`
**GOV-01 eligibility:** `ELIGIBLE`
**Migration verdict:** `ADDITIVE_V052_REQUIRED`
**Snapshot/E06 split:** `NO_SEPARATE_E06_PUBLIC_CONTRACT_AMENDMENT_REQUIRED`
**Authority effect:** Executable product-write authority begins only when the governance commit containing this document is merged to `main`. Before that merge, remote `main` has no P5-EOR-01 product-write owner and V052 remains unreserved.

## 1. R3-selected business boundary

Selected canonical E05 stories:

- `PLN-E05-003` — operate employee payroll onboarding lifecycle;
- `PLN-E05-004` — evaluate multidimensional payroll readiness;
- `PLN-E05-017` — apply payroll holds with scoped impact;
- `PLN-E05-019` — create immutable employee payroll snapshots;
- `PLN-E05-020` — provide onboarding, readiness and exception workbenches.

Explicitly excluded statutory/tax stories remain outside this capability:

- `PLN-E05-013` — PF, ESI and NPS membership profiles;
- `PLN-E05-014` — generic statutory memberships;
- `PLN-E05-015` — tax profiles and regime elections by tax year;
- `PLN-E05-016` — previous-employer income, declarations and proofs.

Canonical story statuses are not changed by this activation/implementation authority.

## 2. G01 read-only verdict

`P5_EOR_G01: PASS`

The capability is bounded and coherent, required React product UI is revalidated, existing E05/E01/E02/E04 architecture provides a non-competing implementation path, tenant/RLS/audit/security patterns already exist, and no selected story requires this capability to define country-specific statutory or tax truth.

Independent repository reconciliation confirms the canonical Flyway root is `database/flyway/sql`, V001–V051 are committed and immutable, V052 is absent/unreserved, the selected/excluded story boundary is unchanged, and no G01 evidence collection mutated product state.

## 3. GOV-01 fast-lane verdict

`GOV01_FAST_LANE: ELIGIBLE`

Eligibility is conditional on these hard boundaries:

1. statutory and tax dimensions expose caller/policy/provider evidence only; this capability must not infer jurisdictional obligations, rates, thresholds, memberships, tax regime truth or filing/remittance rules;
2. an absent/unimplemented statutory or tax provider is `BLOCKED` or `NOT_EVALUATED`, never silently ready;
3. `EXPLICIT_NOT_APPLICABLE` requires configured/approved organisational policy evidence and must never be inferred from jurisdiction;
4. P5-EIP-01 payment-only restrictions remain separate from generic payroll holds;
5. E06 calculation public contracts are not expanded by this capability;
6. any implementation discovery that requires country-specific legal/statutory/tax truth, a calculation public-contract break, or files outside the allow-lists below stops product write and requires a governance amendment.

## 4. Deterministic migration authority

`MIGRATION_VERDICT: ADDITIVE_V052_REQUIRED`

On merge of this governance authority, V052 is reserved exclusively to P5-EOR-01. V001–V051 remain immutable.

Exact migration path:

`database/flyway/sql/V052__employee_payroll_onboarding_readiness_holds_snapshot.sql`

V052 may contain only additive structures needed for:

- onboarding lifecycle/history/evidence;
- readiness policy and evaluated evidence;
- generic payroll hold identity/version/scope/approval/release/expiry;
- tenant-safe keys, RLS, indexes, constraints and bounded database functions;
- additive evolution of `payroll_ops.input_snapshot` sealing/payload schema so the sealed snapshot captures exact approved E05 employee facts, readiness evidence and active hold IDs/scopes;
- a payload schema-version bump and fail-closed sealing checks for applicable blocking readiness or calculation-scope holds.

V052 must not introduce country-specific tax/statutory rule tables, calculation results, payment execution, accounting/posting balances, filing/remittance logic or production-migration/cutover logic.

## 5. Immutable snapshot / E06 boundary

`SNAPSHOT_E06_SPLIT: NO_SEPARATE_E06_PUBLIC_CONTRACT_AMENDMENT_REQUIRED`

ADR-005 and V024 already establish an immutable sealed `payroll_ops.input_snapshot` with exact lineage and hash binding. P5-EOR-01 extends that existing database snapshot payload/sealing authority; it does not replace it. Calculation continues to reference sealed snapshot identity/hash. No calculation-engine OpenAPI or public endpoint change is authorised.

The V052 database migration may replace/extend the existing `payroll_ops.seal_payroll_inputs` database function as required to add the authorised E05 evidence. Java/OpenAPI changes under `payroll-operations` are not authorised. If implementation proves such a Java/OpenAPI change unavoidable, stop and return for authority amendment before editing it.

## 6. Multidimensional readiness contract

Required dimensions:

`IDENTITY`, `ASSIGNMENT`, `COMPENSATION`, `CALENDAR`, `PAYMENT`, `STATUTORY`, `TAX`, `DOCUMENTATION`, `APPROVAL`, `INTEGRATION`.

Organisation/PSU policy may classify evaluated findings as `BLOCKING`, `WARNING`, `INFORMATIONAL` or approved `EXPLICIT_NOT_APPLICABLE`. Every finding must expose deterministic source lineage/evidence. No operator boolean may substitute for evidence-driven readiness.

## 7. Generic payroll-hold contract

A payroll hold has stable identity and history-preserving/effective-dated versions. Supported impact scopes are exactly:

- `CALCULATION`;
- `PAYMENT`;
- `DOCUMENT_PUBLICATION`;
- `STATUTORY_SUBMISSION`.

Every hold records reason, source/reference and lifecycle evidence. Approval is maker/checker separated; the maker cannot approve their own hold. Release preserves history. Expired holds are not active but remain auditable. Holds active at snapshot time are represented by exact hold identities/versions/scopes in the sealed snapshot.

## 8. Permission boundary

New permissions authorised only for this capability:

- `employee-payroll.onboarding.read`
- `employee-payroll.onboarding.write`
- `employee-payroll.onboarding.approve`
- `employee-payroll.readiness.read`
- `employee-payroll.readiness-policy.read`
- `employee-payroll.readiness-policy.write`
- `employee-payroll.hold.read`
- `employee-payroll.hold.write`
- `employee-payroll.hold.approve`
- `employee-payroll.hold.release`
- `employee-payroll.workbench.read`

Read-only roles receive only required read/readiness/workbench permissions. They receive no write, approve or release permission.

## 9. Backend/database/contracts allow-list

Existing files that may be changed when required:

- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollPermissions.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollProfileController.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/application/EmployeePayrollProfileService.java`
- `contracts/openapi/employee-payroll-openapi-v1.yaml`
- `deploy/local/keycloak/payroll-realm.json`
- `docs/runbooks/employee-payroll-api.md`
- `docs/runbooks/employee-payroll-application-layer.md`
- `database/flyway/verification/verify_vertical_slice.sql`

New files authorised:

- `database/flyway/sql/V052__employee_payroll_onboarding_readiness_holds_snapshot.sql`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollOnboardingController.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollOnboardingModels.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollReadinessController.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollReadinessModels.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollHoldController.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollHoldModels.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollWorkbenchController.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollWorkbenchModels.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/application/EmployeePayrollOnboardingService.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/application/EmployeePayrollReadinessService.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/application/EmployeePayrollHoldService.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/application/EmployeePayrollWorkbenchService.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/infrastructure/EmployeePayrollOnboardingRepository.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/infrastructure/EmployeePayrollReadinessRepository.java`
- `backend/employee-payroll/src/main/java/com/acme/hrms/payroll/employeepayroll/internal/infrastructure/EmployeePayrollHoldRepository.java`
- `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/EmployeePayrollOnboardingReadinessHoldsSnapshotMigrationIT.java`
- `backend/employee-payroll/src/test/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollOnboardingReadinessContractTest.java`
- `backend/employee-payroll/src/test/java/com/acme/hrms/payroll/employeepayroll/EmployeePayrollHoldContractTest.java`
- `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/EmployeePayrollOnboardingReadinessApiIT.java`

Changes to calculation-engine source, compensation, organisation, statutory-deductions, payroll-operations Java/OpenAPI or any other unlisted product path require a pre-write authority amendment. No unlisted existing test file may be changed under this authority.

## 10. UI allow-list

Existing UI files that may be changed:

- `src/features/employee-payroll/EmployeePayrollPage.tsx`
- `src/features/employee-payroll/EmployeePayrollPage.test.tsx`
- `src/features/employee-payroll/employee-payroll-api.ts`

New UI files authorised:

- `src/features/employee-payroll/EmployeePayrollOnboardingPanel.tsx`
- `src/features/employee-payroll/EmployeePayrollOnboardingPanel.test.tsx`
- `src/features/employee-payroll/EmployeePayrollReadinessPanel.tsx`
- `src/features/employee-payroll/EmployeePayrollReadinessPanel.test.tsx`
- `src/features/employee-payroll/EmployeePayrollHoldPanel.tsx`
- `src/features/employee-payroll/EmployeePayrollHoldPanel.test.tsx`
- `src/features/employee-payroll/EmployeePayrollWorkbenchPanel.tsx`
- `src/features/employee-payroll/EmployeePayrollWorkbenchPanel.test.tsx`
- `src/features/employee-payroll/employee-payroll-operations-api.ts`
- `src/features/employee-payroll/employee-payroll-operations-api.test.ts`
- `e2e/p5-eor-01-g02b.spec.ts`
- `e2e/p5-eor-01.config.ts`

No new product route is authorised; extend the existing Employee Payroll workspace.

## 11. Required validation and browser evidence

Backend/local:

1. V052 migration integration test plus fresh Flyway/RLS verification;
2. employee-payroll contract/unit/API integration tests;
3. full Maven verify;
4. OpenAPI validation;
5. authorization/permission negative-path evidence.

UI/local:

1. lint;
2. unit tests;
3. production build;
4. npm audit under the existing program policy;
5. real-backend Playwright against the exact merged backend product head.

Required browser journeys:

1. onboarding happy path plus missing blocker, cancellation/hold and history preservation;
2. all ten readiness dimensions with source lineage and statutory/tax provider absence failing closed;
3. hold maker/checker separation, scoped blocking, release and expiry;
4. sealed snapshot immutability before/after live employee/readiness/hold changes;
5. workbench filters, masking and read-only denial;
6. cross-tenant/RLS denial;
7. permission matrix for read/write/approve/release roles.

Hosted publication must retain the repository's required Maven, Flyway/RLS, OpenAPI, auth, secret scan, dependency review and SBOM checks; UI publication must retain the repository's required lint/test/build/security/browser checks.

## 12. Freshness guards

Before product write begins, fail closed unless all are true:

- backend remote `main` equals the merge commit containing this exact authority, with no intervening conflicting E05 product/API/schema/security change;
- UI remote `main` equals `00368e714665785000002fe4cbd330bc1e5cc180` unless a later read-only reconciliation proves no conflicting Employee Payroll UI change;
- canonical story blob remains `77b367387ff3036c3eb2fbeb511083c7a2c27214` unless semantically re-reconciled;
- UI applicability blob remains `532feab6311982d518379f393e4bf4cbbb1d436a` unless semantically re-reconciled;
- P5-EOR-01 state is `IMPLEMENTATION_AUTHORIZED` on merged `main`;
- `currentExecutionCapability` is `P5-EOR-01`;
- V052 is reserved by P5-EOR-01 and no committed V052 exists before implementation;
- selected/excluded story boundaries and legal flags have not materially changed.

A later unrelated governance-only main commit does not automatically invalidate G01, but exact repository authority must be reconciled before product mutation. Any conflicting E05 product/API/schema/security change invalidates implementation start and requires G01 re-evaluation.

## 13. Sequencing after this authority merges

1. G02A backend/database/OpenAPI/security implementation and local verification;
2. publish/merge exact backend product authority after required checks;
3. G02B React implementation against exact merged backend authority;
4. real-backend browser E2E and hosted UI verification;
5. generic post-merge closure/reconciliation.

No product implementation, push, PR, merge or migration file is performed by the local-prep package that creates this authority.
