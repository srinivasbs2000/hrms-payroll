# P5-A3 Salary Structure, CTC, Eligibility and Simulation Scope

**Status:** ACTIVE — PREPARATION ONLY
**Capability owner:** P5-A3
**Repository baseline:** `main` at `887347fb23b35ca72c479f377c0f6e3a1bf89722`
**Branch:** `feature/p5-a3-salary-structure-ctc-eligibility-simulation`
**Migration reservation:** V033 exclusively reserved; SQL creation is not authorised
**Maximum write boundary:** exactly 69 paths
**Planning package:** `HRMS-Payroll-P5-A3-Planning-and-Critical-Review-v1.0.zip`
**Planning package SHA-256:** `d704409e9fb4792f15ce05d5ade5cb4f04c80be04e0dc1d31d357402f12e5f77`
**Owner authorisation:** 5 August 2026

## 1. Activation authority

This activation grants only:

1. exclusive P5-A3 capability ownership;
2. the branch named above;
3. exclusive reservation of V033;
4. the exact 69-path maximum boundary below;
5. preservation of the approved planning and critical-review constraints.

This activation does not authorise:

- creation of `V033__salary_structure_ctc_eligibility_simulation.sql`;
- product implementation;
- a product commit, push or pull request beyond this activation commit;
- changes outside the exact boundary;
- branch deletion;
- formula-engine, legal-rule, employee-assignment or official-payroll changes.

Product implementation requires a later, separate explicit owner authorisation.

## 2. Corrected capability boundary

P5-A3 is a configuration-design capability containing:

- schema-versioned salary-structure design;
- versioned CTC policies and distinct offered, target, accrued and
  actual-employer-cost views;
- typed, effective-dated eligibility-rule configuration;
- deterministic design-time simulation, comparison and validation;
- an exact passing validation/configuration fingerprint required for structure
  approval.

P5-A3 is not a general calculation-engine increment. It does not execute
official payroll, calculate current statutory or tax liabilities, change
employee compensation assignments, or store live employee eligibility
decisions.

## 3. Approved story set

### P5-A3-01 — Salary-structure design schema and lifecycle enhancement

- preserve every V020 identity/version/line UUID and V021 assignment reference;
- retain schema-0 history and require complete schema-1 runtime writes;
- split identity-create and version-write contracts;
- add exact CTC-policy and optional eligibility-rule version references;
- add controlled design metadata, minimum/maximum, mandatory and display
  attributes;
- preserve fixed, percentage-of-earlier-component and residual value shapes;
- require maker-checker, effective-date containment, one-successor lineage,
  retirement blockers and a current passing validation fingerprint.

### P5-A3-02 — Versioned CTC policy and cost views

- stable policy identity and immutable effective-dated versions;
- exact component-version treatment rows;
- distinct `OFFERED`, `TARGET`, `ACCRUED` and `ACTUAL_EMPLOYER_COST` views;
- deterministic annualisation, decimal-string tolerance and one configured
  non-negative residual;
- reuse P5-A2 named bases and memberships rather than duplicating membership
  truth;
- maker-checker, overlap prevention, audit, outbox and controlled retirement.

### P5-A3-03 — Controlled eligibility-rule configuration

- stable rule identity, versions and ordered typed criteria;
- allow-listed fact keys and operators only;
- conjunctive evaluation only;
- results limited to `ELIGIBLE`, `NOT_ELIGIBLE` and `REQUIRES_APPROVAL`;
- evaluate synthetic or supplied test facts only;
- no arbitrary code, SQL, SpEL, JavaScript or unrestricted JSON logic;
- no employee eligibility, override, assignment or readiness persistence.

### P5-A3-04 — Deterministic design-time simulation and impact analysis

- exact structure, CTC-policy and optional eligibility-rule versions;
- exact approved component versions and P5-A2 base memberships;
- annual/monthly design values, cost views, residual, reconciliation,
  eligibility test result, warnings, blockers and version comparison;
- exact request, configuration and result hashes;
- ordinary preview may remain transient; approval evidence is immutable;
- every output must state:
  `DESIGN-TIME SIMULATION — NOT AN OFFICIAL PAYROLL RESULT`;
- no payroll-result, trace, cycle, assignment or component mutation.

## 4. Expected V033 design if later authorised

V033 is expected to introduce:

- enhancements to `compensation.salary_structure_version`;
- enhancements to `compensation.salary_structure_line`;
- `compensation.ctc_policy`;
- `compensation.ctc_policy_version`;
- `compensation.ctc_policy_treatment`;
- `compensation.eligibility_rule`;
- `compensation.eligibility_rule_version`;
- `compensation.eligibility_rule_criterion`;
- `compensation.salary_structure_validation`;
- `compensation.salary_structure_validation_line`;
- controlled lifecycle functions, tenant-safe FKs, indexes, RLS, least
  privilege, audit and outbox support.

V001–V032 remain byte-for-byte immutable. Existing structure, line, component,
base, membership, employee-assignment and payroll-result lineage must not be
rewritten.

## 5. Blocking critical-review controls

The following remain binding:

- no general formula DSL, rate tables or executable arbitrary expressions;
- no official gross-to-net, target-net, gross-up, tax or statutory calculation;
- no assertion of India minimum-wage or statutory compliance without a later
  legally revalidated rule pack;
- no employee salary assignment, revision, override, readiness or live
  eligibility change;
- no flexible-benefit elections or supplemental-plan assignment;
- no multi-currency payroll execution;
- exact BigDecimal/database-numeric and decimal-string money only;
- structure approval rejects missing or stale validation fingerprints;
- simulation and comparison enforce confidentiality permissions;
- outbox events carry IDs, hashes and summaries rather than sensitive detailed
  compensation evidence;
- every additional path requires stop-and-split approval.

## 6. Required acceptance and regression evidence

- populated V032-to-V033 upgrade preserving exact UUID lineage;
- all new tenant objects use tenant-safe FKs and ENABLE/FORCE RLS;
- app role remains non-owner and `NOBYPASSRLS`;
- approved configuration and validation evidence are immutable;
- maker cannot approve their own configuration;
- deterministic CTC reconciliation and non-negative residual;
- typed eligibility criterion negative paths;
- repeat simulation produces identical hashes and ordering;
- simulation creates no official payroll or employee-assignment writes;
- V025/V026 golden calculation totals, hashes and trace remain unchanged;
- V032 catalogue/base behavior remains unchanged;
- idempotency, optimistic concurrency, audit and outbox exactly-once evidence;
- OpenAPI, Keycloak and frontend permission alignment;
- focused tests, full migration/RLS suite, Maven verify, frontend tests/build,
  OpenAPI, secret/dependency/SBOM and exact-path checks.

## 7. Exact maximum path boundary

Any path not listed below requires separate explicit approval. The migration
path is reserved but must not be created during activation.

1. `database/flyway/sql/V033__salary_structure_ctc_eligibility_simulation.sql`
2. `database/flyway/README.md`
3. `database/flyway/verification/verify_vertical_slice.sql`
4. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/SalaryStructureCtcEligibilityMigrationIT.java`
5. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/SalaryStructureMigrationIT.java`
6. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java`
7. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/FoundationNegativePathMigrationIT.java`
8. `docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md`
9. `docs/quality/p5-a3-salary-structure-ctc-eligibility-simulation.md`
10. `docs/runbooks/salary-structure-ctc-configuration.md`
11. `docs/design/decision-register.md`
12. `docs/design/hrms-payroll-master-design.md`
13. `docs/governance/payroll-feature-delivery-lineage.md`
14. `docs/governance/thread-registry.md`
15. `docs/runbooks/project-continuation-handoff.md`
16. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureController.java`
17. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureView.java`
18. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureCreateRequest.java`
19. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureVersionWriteRequest.java`
20. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureWriteRequest.java`
21. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureLineWriteRequest.java`
22. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureLineView.java`
23. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyController.java`
24. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyCreateRequest.java`
25. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyVersionWriteRequest.java`
26. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyView.java`
27. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyTreatmentWriteRequest.java`
28. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/CtcPolicyTreatmentView.java`
29. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityRuleController.java`
30. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityRuleCreateRequest.java`
31. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityRuleVersionWriteRequest.java`
32. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityRuleView.java`
33. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityCriterionWriteRequest.java`
34. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/EligibilityCriterionView.java`
35. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureSimulationController.java`
36. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureSimulationRequest.java`
37. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureSimulationView.java`
38. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/SalaryStructureComparisonView.java`
39. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/SalaryStructureService.java`
40. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/infrastructure/SalaryStructureRepository.java`
41. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/CtcPolicyService.java`
42. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/infrastructure/CtcPolicyRepository.java`
43. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/EligibilityRuleService.java`
44. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/infrastructure/EligibilityRuleRepository.java`
45. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/SalaryStructureSimulationService.java`
46. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/SalaryStructureValidator.java`
47. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/SalaryStructureContractTest.java`
48. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/CtcPolicyContractTest.java`
49. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/EligibilityRuleContractTest.java`
50. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/SalaryStructureSimulationContractTest.java`
51. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/SalaryStructureApiIT.java`
52. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/CtcPolicyApiIT.java`
53. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/EligibilityRuleApiIT.java`
54. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/SalaryStructureSimulationApiIT.java`
55. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/P5A3CalculationCompatibilityIT.java`
56. `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
57. `deploy/local/keycloak/payroll-realm.json`
58. `frontend/payroll-web/src/App.tsx`
59. `frontend/payroll-web/src/App.test.tsx`
60. `frontend/payroll-web/src/features/salary-structure/SalaryStructurePage.tsx`
61. `frontend/payroll-web/src/features/salary-structure/SalaryStructurePage.test.tsx`
62. `frontend/payroll-web/src/features/salary-structure/salary-structure-api.ts`
63. `frontend/payroll-web/src/features/salary-structure/CtcPolicyPanel.tsx`
64. `frontend/payroll-web/src/features/salary-structure/CtcPolicyPanel.test.tsx`
65. `frontend/payroll-web/src/features/salary-structure/EligibilityRulePanel.tsx`
66. `frontend/payroll-web/src/features/salary-structure/EligibilityRulePanel.test.tsx`
67. `frontend/payroll-web/src/features/salary-structure/SalaryStructureSimulationPanel.tsx`
68. `frontend/payroll-web/src/features/salary-structure/SalaryStructureSimulationPanel.test.tsx`
69. `frontend/payroll-web/src/styles.css`

## 8. Stop conditions

Stop immediately for:

- any V001–V032 modification;
- any path outside the 69-path boundary;
- creation of the V033 SQL file before implementation authorisation;
- calculation-result or trace semantic change;
- legal rule/rate implementation;
- employee compensation or eligibility persistence;
- dependency, CI/workflow or deployment change;
- real employee compensation data in tests or previews.

## 9. Next gate

After the activation commit is returned and independently verified, obtain a
separate explicit authorisation for P5-A3 product implementation. No implicit
continuation is permitted.
