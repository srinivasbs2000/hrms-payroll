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
| MDR-015 | V001-V030 are immutable; V031 remains unreserved | Database | IMPLEMENTED | Merged main | Reserve explicitly |
| MDR-016 | Generic statutory infrastructure is implemented; country-specific legal rules remain excluded | Product | CONTROLLED | Sprint 4 | Require legal design |
| MDR-017 | Retro, off-cycle, settlement, payments, accounting and legal payslip remain outside implemented baseline | Product | APPROVED | Master design/gap assessment | Remove only through approved stories |
| MDR-018 | Historical Thread 1 decisions are evidence, not automatic current state | Process | APPROVED | Thread 1 extract | Reconcile first |
| MDR-019 | Feed-dependent OWASP data requires cached/scheduled handling | Security | TEMPORARY/DEBT | CI/handoff | Close with cache design |
| MDR-020 | Each chat thread starts from repository authorities | Process | APPROVED | Thread protocol | Use start prompt |
| MDR-021 | Repository HEAD and latest product merge are distinct baselines | Process | APPROVED | PR #21 and Sprint 4 | Preserve both |
| MDR-022 | Sprint 4 is functionally merged but not fully automated until S4-06A/S4-06B are resolved | Quality | APPROVED | Closure report | Keep separate |
| MDR-023 | Text artifacts use UTF-8 and must not contain mojibake | Process | APPROVED | Repository cleanup | Reject corruption |
| MDR-024 | Identify historical work by capability, migration, commit and PR when labels diverge | Process | APPROVED | Reconciliation | Use durable identifiers |
| MDR-025 | PowerShell variable-cardinality output is array-captured, checked and cast | Engineering | APPROVED | Phase A failure | Apply always |
| MDR-026 | Repository updates use standard tools or deterministic full-file payloads; exit code is authoritative | Engineering | APPROVED | Phase A failures | Prefer Copy-Item/Git |
| MDR-027 | Recovery preserves the original checklist; no silent omission or deferral | Governance | APPROVED | Phase A | Compare planned/actual |
| MDR-028 | Product Charter, Iterations 1-12, blueprint, backlog, DDL and API/event catalogue are the recovered full-product source set | Product/Governance | APPROVED | Source register/history record | Preserve identity/checksums |
| MDR-029 | `docs/product/payroll-product-scope-and-epic-catalog.md` is the complete Payroll scope authority | Product | APPROVED | Full-product publication | Sprint backlog cannot replace it |
| MDR-030 | Original implementation control structure is 18 epics and 72 backlog rows across R1-R4 | Planning | APPROVED | Master backlog | Retain source IDs/acceptance |
| MDR-031 | Sprint 0-4 is a bounded vertical slice; no original epic is complete against all source acceptance | Product/Quality | APPROVED | Gap assessment/lineage | Report partial scope honestly |
| MDR-032 | PLN-01 decomposition of E01-E18 using Iterations 1-12 is mandatory before the next new product-feature sprint | Planning | APPROVED | Scope catalog/roadmap | Complete in Thread 1 |
| MDR-033 | Original canonical DDL is logical design and must not be applied directly as a current Flyway migration | Database | APPROVED | Source register/master design | Reconcile via forward migrations |

## Adding a decision

A material decision must include a stable ID, exact decision, type/status,
evidence, implementation state, conflict/supersession relationship and handoff
action. Do not record brainstorms as approved decisions.
