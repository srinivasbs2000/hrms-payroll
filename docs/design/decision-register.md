# HRMS Payroll Decision Register

**Purpose:** Compact index of approved, superseded and unresolved material
decisions. Detailed rationale belongs in ADRs, design documents and cited
evidence.

## Status values

- `APPROVED`
- `IMPLEMENTED`
- `SUPERSEDED`
- `TEMPORARY`
- `OPEN`
- `DOCUMENTATION CONFLICT`
- `NOT VERIFIED`

## Decisions

| ID | Decision | Type | Status | Evidence/implementation | Handoff action |
|---|---|---|---|---|---|
| MDR-001 | Repository evidence, not conversation memory, is project truth | Process | IMPLEMENTED | `AGENTS.md`, continuation handoff | Enforce in every thread |
| MDR-002 | Master design owns architecture and implementation relationship; product scope catalog owns the complete capability boundary | Process | APPROVED | Master design and product scope catalog | Maintain without duplicating detailed rows |
| MDR-003 | Modular monolith with `payroll-boot` composition root | Architecture | IMPLEMENTED | Modules and architecture tests | Preserve boundaries |
| MDR-004 | Stable identity plus immutable effective-dated exact versions | Architecture | IMPLEMENTED | V015, V017, V019-V021 and later lineage | Document future mappings |
| MDR-005 | Half-open effective ranges and no approved overlap | Architecture | IMPLEMENTED | Migrations/tests | Preserve negative tests |
| MDR-006 | Tenant-safe composite FKs plus ENABLE/FORCE RLS | Security | IMPLEMENTED | V011 onward | Verify every tenant object |
| MDR-007 | Runtime roles are non-owner and `NOBYPASSRLS`; transactions use `SET LOCAL` | Security | IMPLEMENTED | Bootstrap/RLS/services | Never replace with filtering |
| MDR-008 | Sealed inputs, results, trace, draft-payslip and statutory evidence are immutable | Architecture | IMPLEMENTED | V023-V030 | Corrections append evidence |
| MDR-009 | Idempotency, audit and outbox commit atomically with business changes | Reliability | IMPLEMENTED pattern | Integrations/services | Verify rollback/commit |
| MDR-010 | Money uses `BigDecimal`/decimal strings; floating point prohibited | Architecture | IMPLEMENTED policy | Code/contracts/tests | Preserve scale |
| MDR-011 | `mvn verify` is the backend gate | Process | IMPLEMENTED | `AGENTS.md` | Show Failsafe phases |
| MDR-012 | Frontend lint, tests and build are separate gates | Process | IMPLEMENTED | CI | Verify separately |
| MDR-013 | High-risk Payroll changes require independent critical review | Process | IMPLEMENTED | `AGENTS.md` | Attach findings |
| MDR-014 | One write-capable thread owns overlapping files | Process | APPROVED | Registry/protocol | Track explicitly |
| MDR-015 | P5-A3 activation reserved V033 exclusively before its implementation was separately authorised | Database | SUPERSEDED | P5-A3 activation from `887347fb23b35ca72c479f377c0f6e3a1bf89722`; P5-A3 later merged through PR #32 | Historical activation only; current migration authority is MDR-064 |
| MDR-016 | Generic statutory infrastructure is implemented; country-specific legal rules remain excluded | Product | CONTROLLED | Sprint 4 | Require legal design |
| MDR-017 | Retro, off-cycle, settlement, payments, accounting and legal payslip remain outside implemented baseline | Product | APPROVED | Master design/gap assessment | Remove only through approved stories |
| MDR-018 | Historical Thread 1 decisions are evidence, not automatic current state | Process | APPROVED | Thread 1 extract | Reconcile first |
| MDR-019 | Feed-dependent OWASP data requires cached/scheduled handling | Security | TEMPORARY/DEBT | CI/handoff | Close with cache design |
| MDR-020 | Each chat thread starts from repository authorities | Process | APPROVED | Thread protocol | Use start prompt |
| MDR-021 | Repository HEAD and latest product merge are distinct baselines | Process | APPROVED | PR #39 governance closure after PR #36 product merge demonstrates the distinction; current repository HEAD is always live-verified rather than hard-coded as product state | Preserve product/reconciliation SHAs as evidence and resolve current HEAD live |
| MDR-022 | Sprint 4 is functionally merged; S4-06A secured HTTP/PostgreSQL integration closure is merged and S4-06B remains separate and unauthorized | Quality | IMPLEMENTED | PR #28; merge `12f3210c91ca95f3f331911d4cdc1755f2afd701`; CI run 100 | Preserve S4-06A closure without absorbing S4-06B |
| MDR-023 | Text artifacts use UTF-8 and must not contain mojibake | Process | APPROVED | Repository cleanup | Reject corruption |
| MDR-024 | Identify historical work by capability, migration, commit and PR when labels diverge | Process | APPROVED | Reconciliation | Use durable identifiers |
| MDR-025 | PowerShell variable-cardinality output is array-captured, checked and cast | Engineering | APPROVED | Phase A failure | Apply always |
| MDR-026 | Repository updates use standard tools or deterministic full-file payloads; exit code is authoritative | Engineering | APPROVED | Phase A failures | Prefer Copy-Item/Git |
| MDR-027 | Recovery preserves the original checklist; no silent omission or deferral | Governance | APPROVED | Phase A | Compare planned/actual |
| MDR-028 | Product Charter, Iterations 1-12, blueprint, backlog, DDL and API/event catalogue are the recovered full-product source set | Product/Governance | APPROVED | Source register/history record | Preserve identity/checksums |
| MDR-029 | `docs/product/payroll-product-scope-and-epic-catalog.md` is the complete Payroll scope authority | Product | APPROVED | Full-product publication | Sprint backlog cannot replace it |
| MDR-030 | Original implementation control structure is 18 epics and 72 backlog rows across R1-R4 | Planning | APPROVED | Master backlog | Retain source IDs/acceptance |
| MDR-031 | Sprint 0-4 is a bounded vertical slice; no original epic is complete against all source acceptance | Product/Quality | APPROVED | Gap assessment/lineage | Report partial scope honestly |
| MDR-032 | PLN-01 decomposition of E01-E18 using Iterations 1-12 is mandatory before the next new product-feature sprint | Planning | APPROVED | Scope catalog/roadmap | P5-A1 package selection is the approved planning result |
| MDR-033 | Original canonical DDL is logical design and must not be applied directly as a current Flyway migration | Database | APPROVED | Source register/master design | Reconcile via forward migrations |
| MDR-034 | Every project task defaults to non-Codex downloadable payload execution; Downloads/repository path conventions and explicit user-action sections are standing requirements | Process/Cost control | APPROVED | `docs/governance/hrms-payroll-execution-norm.md` | Carry into every handoff and response |
| MDR-035 | Organisation identities use `PENDING_APPROVAL`, `ACTIVE`, `RETIRED`; first independent approval activates and controlled retirement preserves immutable version history | Architecture/Governance | IMPLEMENTED | V031; PR #25; CI run 94 | Preserve maker-checker, dependency and concurrency controls |
| MDR-036 | PSU responsibility scope and establishment type are effective-dated version attributes with controlled vocabularies | Domain | IMPLEMENTED | V031; PR #25 | Revalidate vocabulary before jurisdiction-specific expansion |
| MDR-037 | Every project-supplied PowerShell script must pass the real PowerShell parser before execution; validator-first wrappers fail closed and ambiguous `$name:` interpolation is prohibited | Engineering/Quality | APPROVED | P5-A1 G05 v1.0 parser failure and v1.1 correction | Apply to every package and handoff |

| MDR-038 | PowerShell native-command helpers preserve flat zero/one/many output cardinality; unary-comma nested-array returns are prohibited and semantic cardinality tests are mandatory | Engineering | APPROVED | P5-A1 G05 v1.1 failure evidence and v1.2 semantic gate | Apply to every generated script and handoff |

| MDR-039 | GitHub-dependent scripts perform bounded remote connectivity, exact base-SHA and branch-state checks before mutation; offline bypass is prohibited | Engineering | APPROVED | P5-A1 G05 v1.2 network failure evidence and v1.3 network gate | Apply to every GitHub-dependent package and handoff |

| MDR-040 | Git commands whose stdout is consumed as data must capture stdout and stderr separately; diagnostic warnings must never enter path, SHA or allow-list comparisons | Engineering | APPROVED | P5-A1 G05 v1.2 allow-list false positive after LF-to-CRLF warnings | Use stream-separated native execution and semantic warning tests in every generated script |

| MDR-041 | Generated PowerShell must obtain native success/failure from the launched process object's ExitCode; `$LASTEXITCODE` is not authoritative for controlled gates | Engineering | APPROVED | P5-A1 G05 v1.4 returned the approved SHA while `$LASTEXITCODE` remained -1 | Use `System.Diagnostics.Process`, separate streams and semantic nonzero-exit tests |

| MDR-042 | Assistant and agent GitHub access is strictly read-only; all GitHub mutations are executed locally by the project owner from deterministic packages and then verified read-only | Process/Security | APPROVED | P5-A1 G08-G10 operating constraint and evidence | Never attempt connector mutations; carry into every handoff and package |

| MDR-043 | P5-A1 is merged through PR #25; its authority reconciliation is merged through PR #26; V032 was unreserved when that increment closed | Governance | IMPLEMENTED | `main` `961465cb551f3757a6f51f1322e6b46c32317b16` | Preserve the historical closure; current V032 state is governed by MDR-015 and MDR-045 |

| MDR-044 | S4-06A completed as an exact six-file test-only quality closure; no production, migration, contract, security, dependency, frontend or CI change was introduced and V032 remained unreserved at its closure | Quality/Governance | IMPLEMENTED | PR #28; merge `12f3210c91ca95f3f331911d4cdc1755f2afd701`; CI run 100 | Preserve the merged evidence and historical stop-and-split rule |
| MDR-045 | P5-A2 completed the general pay-component catalogue and named payroll-base foundation within the reviewed 46-path boundary; V032 is committed and authority is released | Product/Governance | IMPLEMENTED | Activation `e9e297de5e59762f3701ce39ca2295e1839d7d16`; implementation `c30cb1f2f0c16cd78387bb9551b93825bc7ef688`; PR #30 merge `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`; workflow run `30957450623` | Preserve closed evidence; V033, P5-A3 and S4-06B require separate authorization |
| MDR-046 | HRMS Payroll is greenfield with no evidenced production deployment; migration/version controls protect deterministic local/CI state and future upgrades rather than a current production migration | Architecture/Delivery | APPROVED | Repository state, synthetic development seed and P5-A2 activation clarification | Keep compatibility tests proportionate but do not weaken committed lineage controls |
| MDR-047 | P5-A3 was activated on `feature/p5-a3-salary-structure-ctc-eligibility-simulation`, reserving V033 and the reviewed 69-path maximum boundary | Product/Governance | SUPERSEDED | P5-A3 planning package `d704409e9fb4792f15ce05d5ade5cb4f04c80be04e0dc1d31d357402f12e5f77`; owner authorisation 5 August 2026; later PR #32/#33 closure | Historical activation only; V033 is immutable |
| MDR-048 | P5-A3 is implemented as a configuration-design capability through V033; official payroll calculation and employee assignment remain unchanged | Product/Architecture | IMPLEMENTED | V033; PR #32 | Preserve design-time versus official-payroll boundary |
| MDR-049 | Salary-structure approval is bound to the newest exact passing validation fingerprint | Architecture/Quality | IMPLEMENTED | P5-A3 validation model; V033; PR #32 | Reject stale validation evidence |
| MDR-050 | Eligibility criteria remain allow-listed, typed and conjunctive; arbitrary executable expressions are prohibited | Architecture/Security | IMPLEMENTED | P5-A3 eligibility model; V033; PR #32 | Preserve typed non-executable criteria |
| MDR-051 | Design-time simulation is deterministic, immutable when retained as approval evidence, and always identified as non-payroll | Architecture/Quality | IMPLEMENTED | P5-A3 simulation/validation; PR #32 | Never mutate official payroll results from design-time simulation |
| MDR-052 | The initially planned separate simulation classes were superseded by the consolidated salary-structure controller/service/validation implementation | Architecture | IMPLEMENTED | P5-A3 final implementation; PR #32 | Treat consolidation as approved implementation shape, not missing scope |
| MDR-053 | P5-A3 G07 closed local quality/governance only; Git publication remained a separate owner-controlled action | Process/Governance | IMPLEMENTED | P5-A3 G07; later PR #32 publication | Preserve local-closure versus publication distinction |
| MDR-054 | PostgreSQL 17 remains the approved P5-JRF-01 persistence/security platform; hypothetical Oracle or multi-RDBMS support does not weaken PostgreSQL-native integrity/RLS design | Architecture | IMPLEMENTED | P5-JRF architecture consistency checkpoint; V034; AC-G03-B2 | Treat any future Oracle requirement as a separately approved replatforming program |
| MDR-055 | Generic payroll-jurisdiction resolution precedence is approved explicit override -> approved work-location version -> establishment-derived fallback -> UNRESOLVED; material work-location/establishment disagreement is CONFLICT | Domain | IMPLEMENTED | V034; jurisdiction resolution service/repository/tests | Country/state rule packs may refine behavior only through separately approved jurisdiction-specific design |
| MDR-056 | Registration identifier format metadata uses the explicit application-level `JAVA_REGEX_V1` dialect with whole-string Java matching; PostgreSQL stores but does not interpret business regex | Architecture/Security | IMPLEMENTED | AC-G03-B1 v1.3 | Validate malformed patterns before type approval/activation |
| MDR-057 | Routine statutory-registration APIs/events expose masked/minimized registration identifiers; exact reveal requires `statutory-registration.identifier.read` and audited explicit access | Security | IMPLEMENTED | AC-G03-B1 v1.3; OpenAPI; Keycloak; integration tests | Never place exact identifiers in URLs, logs, errors, audit or outbox payloads |
| MDR-058 | JRF successor creation preserves runtime least privilege through narrow tenant-checked controlled row-lock functions; direct runtime table UPDATE remains revoked | Security/Concurrency | IMPLEMENTED | AC-G03-B2 v1.1-v1.2; V034; integration tests | Preserve RLS, tenant checks and EXECUTE-only boundary |
| MDR-059 | A future/unapproved successor draft must not hide the currently effective approved/active jurisdiction, work location, registration type or statutory registration | Domain/Effective dating | IMPLEMENTED | AC-G03-B2 v1.2 integration coverage | Keep current-effective reads independent from merely existing draft successors |
| MDR-060 | Newly published high-severity frontend dependency advisories found at P5-JRF-01 closure were remediated by patch-level dependency updates and removal of the temporary React Router audit exception; no vulnerable dependency is allow-listed when a compatible fix exists | Security/Supply chain | IMPLEMENTED | G03-C security closure; npm audit diagnostic 2026-08-08 | Security exception scope was limited to package.json, package-lock.json and verify-npm-audit.mjs |
| MDR-061 | P5-JRF-01 is merged and post-merge product authority is closed; V034 is committed and immutable, JRF ownership is released, V035 remains unreserved and no next product capability is implicitly activated | Product/Governance | IMPLEMENTED | PR #36 product merge `6ee101bd398b745a0078bd0517b4e3797c571c2b`; PR #39 post-merge authority closure | Verify current repository HEAD live; separately activate any next capability and migration |

| MDR-062 | Payroll UI is history-preservingly separated into `srinivasbs2000/hrms-payroll-web`; `hrms-payroll` remains the backend/program/API/OpenAPI/database/Keycloak governance authority, the web repository owns frontend CI/Dependabot/SBOM/browser E2E, and no third contract repository is introduced | Architecture/Delivery | IMPLEMENTED | HK-UI-SPLIT-01A seam PR #41; UI extraction/provenance; UI PR #1 / `ac677b4de57f9620a3ad255e5d72a406dc8f6c53`; HK-UI-SPLIT-01D source cleanup/closure | Preserve two-repository ownership and cross-repository E2E; current migration authority is MDR-067 |
| MDR-063 | P5-FBA-01 is merged and post-merge authority is closed; V035 is committed and immutable, FBA ownership is released, and V036 was unreserved at closure | Product/Governance | IMPLEMENTED | Backend PR #44 / `a0234d94ef280a41a744ea6e8483f786a497d211`; UI PR #12 / `5c45ab41ee3cb4466fac822c04c771f5de0ba119`; status-closure PR #45 | Preserve FBA evidence; do not reopen V035 |
| MDR-064 | P5-FSR-01 — Foundation Snapshot & Readiness Closure activation reserved V036 exclusively while that capability was active | Product/Governance | SUPERSEDED | R3 reconciliation 10 Aug 2026; P5-FSR-01 scope authority; closure PR #52 / `940c24d85a11dfaf293fc1d660ede4132fd53acb` | Historical activation authority only; P5-FSR-01 is closed, V036 is immutable, and current migration authority is MDR-067 |
| MDR-065 | P5-FSR-01 configuration sealing is owned by payroll-operations and captures exact cross-domain approved version lineage through controlled database-level sealing; calculation binds to the immutable configuration snapshot identity/hash rather than rereading mutable master state | Architecture | APPROVED | V024 immutable input-snapshot precedent; P5-FSR-01 scope authority | Implement through V036/public contracts without module-internal imports |
| MDR-066 | P5-FSR-01 readiness is a composed foundation-readiness contract and must remain bounded; it does not assert payment, country-specific statutory, retro/off-cycle/settlement, accounting or production-cutover readiness | Product/Architecture | APPROVED | PLN-E01-012 acceptance plus JRF/FBA bounded-readiness precedent | Keep blocker/warning semantics explicit and avoid global-ready overclaim |
| MDR-067 | P5-FAD-01 — Foundation Approval & Delegation is the active bounded capability after activation-authority merge; V037 is reserved exclusively to it and activation changes no story status | Product/Governance | APPROVED | P5-FSR-01 closure `940c24d85a11dfaf293fc1d660ede4132fd53acb`; fresh R3 reconciliation 11 Aug 2026; PLN-E01-011 | Create product branch only from activation-merged main; release V037 only at status closure |
| MDR-068 | Shared application approval authority is owned by `security`; business modules consume only a public authority facade and retain their own lifecycle state machines. `security` must not import business-module internals | Architecture/Security | APPROVED | PLN-E01-011 remaining gap; existing organisation/statutory/FBA maker-checker implementations | Add one-way public dependency only; prohibit internal imports/cycles |
| MDR-069 | Legal authorised-signatory authority and payroll-system application approval authority are distinct. Signatory legal authority must not grant application permissions or entity/PSU approval authorization | Security/Product | APPROVED | P5-FBA-01 separation-of-concerns evidence; PLN-E01-011 | Preserve legal-authority evidence while adding separate application approval scope |
| MDR-070 | P5-EIP-01 G02A may temporarily amend `.github/workflows/ci.yml` only to supply synthetic employee-sensitive test keys for exact reviewed recovery commit `74c649fa4b9d7df34da4c7b7b4836e3787215305`; original product scope and allow-list remain unchanged | Governance/Quality | TEMPORARY | P5-EIP-01 G02A hosted-CI recovery amendment; local full Maven GREEN; independent technical review GREEN | Authority becomes effective only after amendment merge, expires after PR #88 hosted reconciliation, and does not permit further CI edits without new authority |

| MDR-071 | HRMS Payroll adopts the execution optimization standard: schema-first structured-data handling, collect-all preflight, generic-engine-first orchestration, exact-repository release testing, exact-hash validation reuse, one evidence bundle, Authority Snapshot and machine-readable capability state | Process/Engineering | APPROVED | `docs/governance/payroll-execution-optimization-standard.md`; owner approval 24 Aug 2026 | Apply to every new gate/tooling increment; do not weaken existing safety controls |
| MDR-072 | A governed fast lane may combine completed R3 selection plus completed read-only G01 verdict into one activation/implementation-authority governance PR only for an eligible bounded non-legal/non-statutory capability; product write remains blocked until that authority merges | Governance/Delivery | APPROVED | Execution optimization review + standard; owner approval 24 Aug 2026 | Require every fast-lane eligibility criterion and deterministic migration verdict; otherwise use normal activation + G01 sequence |
| MDR-073 | After G01 freezes the contract, backend/UI analysis and test design are contract-first and shared once while repository ownership, PRs, hosted checks and real-backend E2E remain separate; post-merge closure continues through the generic closure engine | Architecture/Delivery | APPROVED | `docs/governance/payroll-execution-optimization-standard.md` | Use one capability delivery plan without collapsing repository/security/quality boundaries |

## Decision identity reconciliation — 9 August 2026

PR #32 assigned MDR-048 through MDR-053 to P5-A3 implemented decisions first.
PR #36 later reused MDR-048 through MDR-054 for P5-JRF-01 decisions. A
repository-wide `git grep` at clean base `022dd94864ed007e8053d3a22986c9d8007ec9ca`
found no MDR-048 through MDR-054 references outside this decision register.

This reconciliation preserves the first durable assignment and rekeys the later
P5-JRF-01 block as follows:

`048 -> 054`, `049 -> 055`, `050 -> 056`, `051 -> 057`, `052 -> 058`,
`053 -> 059`, `054 -> 060`.

## Adding a decision

A material decision must include a stable ID, exact decision, type/status,
evidence, implementation state, conflict/supersession relationship and handoff
action. Do not record brainstorms as approved decisions.

## P5-A2 delivered decisions and closure evidence

- `pay_component.component_type` remains the calculation-direction contract;
  schema-1 versions carry the broader business catalogue classification.
- Existing approved versions remain schema 0; complete schema-1 runtime writes
  and maker-checker approval govern new configuration.
- Named-base membership uses one append-only table with exact identity/version
  lineage, approved-range non-overlap and decimal-string `numeric(12,8)`
  inclusion percentages.
- The V025/V026 starter calculator remains unchanged; named bases are not yet
  consumed by the calculation engine.
- PR #30 merged as `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`; post-merge workflow run `30957450623`
  succeeded; P5-A2 write ownership is released. The later P5-A3 V033 activation
  is historical under MDR-015/MDR-047; current migration state is MDR-061.

## Historical P5-A3 activated preparation decisions

- P5-A3 is configuration-design scope only: richer salary structures, versioned
  CTC policies, typed eligibility rules and deterministic design-time
  simulation/validation.
- P5-A3 does not introduce a general formula engine, official payroll
  calculation, legal rule truth, employee compensation changes or live
  eligibility persistence.
- Existing V020/V021 UUID lineage, V025/V026 calculation behaviour and V032
  component/base behaviour must remain unchanged.
- Structure approval requires the newest passing validation fingerprint to
  match the exact current configuration.
- At activation V033 was exclusively reserved and
  `V033__salary_structure_ctc_eligibility_simulation.sql` was not authorised
  until the later implementation approval; V033 is now committed and immutable.
- The exact 69-path maximum boundary is recorded in
  `docs/planning/pln-01/p5-a3-salary-structure-ctc-eligibility-simulation-scope.md`.

## P5-A3 implemented decisions

- MDR-048 — P5-A3 is implemented as configuration-design capability through
  V033; official payroll calculation and employee assignment remain unchanged.
- MDR-049 — Salary-structure approval is bound to the newest exact passing
  validation fingerprint.
- MDR-050 — Eligibility criteria remain allow-listed, typed and conjunctive;
  arbitrary executable expressions are prohibited.
- MDR-051 — Design-time simulation is deterministic, immutable when retained as
  approval evidence, and always identified as non-payroll.
- MDR-052 — Initially planned separate simulation classes were superseded by the
  consolidated salary-structure controller/service/validation model.
- MDR-053 — G07 closes local quality and governance only; Git publication is a
  separate owner-controlled action.

## P5-EIP-01 G02B auth-runtime boundary amendment

<!-- P5-EIP-01-G02B-AUTH-RUNTIME-AMENDMENT-V1 -->
Decision: do not weaken OIDC issuer validation and do not depend on the ambient localhost:8081 Keycloak. Authorize only `src/auth/keycloak-client.ts` and `src/auth/keycloak-client.test.ts` so the frontend honors its configured Vite Keycloak endpoint; keep P5-EIP-specific dynamic-port login changes inside the already-owned `e2e/p5-eip-01*.ts` boundary.
