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
| MDR-015 | V001-V032 are committed and immutable; V033 is reserved exclusively for P5-A3 but its SQL file and product implementation are not yet authorised | Database | APPROVED | P5-A3 activation from `887347fb23b35ca72c479f377c0f6e3a1bf89722` | Preserve the reservation until P5-A3 closes or is explicitly released |
| MDR-016 | Generic statutory infrastructure is implemented; country-specific legal rules remain excluded | Product | CONTROLLED | Sprint 4 | Require legal design |
| MDR-017 | Retro, off-cycle, settlement, payments, accounting and legal payslip remain outside implemented baseline | Product | APPROVED | Master design/gap assessment | Remove only through approved stories |
| MDR-018 | Historical Thread 1 decisions are evidence, not automatic current state | Process | APPROVED | Thread 1 extract | Reconcile first |
| MDR-019 | Feed-dependent OWASP data requires cached/scheduled handling | Security | TEMPORARY/DEBT | CI/handoff | Close with cache design |
| MDR-020 | Each chat thread starts from repository authorities | Process | APPROVED | Thread protocol | Use start prompt |
| MDR-021 | Repository HEAD and latest product merge are distinct baselines | Process | APPROVED | PR #31 merge `887347fb23b35ca72c479f377c0f6e3a1bf89722` is the current repository authority; PR #30 merge `aeb4b1560e7c7d6147bb288ef989b15ad1be4946` is the latest product increment; Sprint 4 remains the prior sprint baseline | Preserve all labels explicitly |
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
| MDR-047 | P5-A3 is the active preparation workstream on `feature/p5-a3-salary-structure-ctc-eligibility-simulation`; it exclusively reserves V033 and owns only the reviewed 69-path maximum boundary | Product/Governance | APPROVED | P5-A3 planning package `d704409e9fb4792f15ce05d5ade5cb4f04c80be04e0dc1d31d357402f12e5f77`; independent critical review; owner authorisation on 5 August 2026 | Stop before V033 SQL creation or product implementation until separately authorised |

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
  succeeded; P5-A2 write ownership is released. V033 was unreserved at P5-A2
  closure; its current state is governed by MDR-015 and MDR-047.

## P5-A3 activated preparation decisions

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
- V033 is exclusively reserved, but `V033__salary_structure_ctc_eligibility_simulation.sql` must not be created until
  product implementation is separately authorised.
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
