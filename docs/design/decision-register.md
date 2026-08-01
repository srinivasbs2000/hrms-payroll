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
| MDR-002 | Master design owns approved scope/architecture; running handoff owns current state | Process | APPROVED | Master-design bootstrap | Maintain both without duplication |
| MDR-003 | Modular monolith with `payroll-boot` composition root | Architecture | IMPLEMENTED | Repository modules and architecture tests | Preserve boundaries |
| MDR-004 | Stable identity plus immutable effective-dated exact versions | Architecture | IMPLEMENTED | V015, V017, V019-V021 and later lineage | Document mapping in future upgrades |
| MDR-005 | Half-open effective ranges and no overlap for approved versions | Architecture | IMPLEMENTED | Migrations/tests | Preserve negative tests |
| MDR-006 | Tenant-safe composite FKs plus ENABLE/FORCE RLS | Security | IMPLEMENTED | V011 onward and verification SQL | Verify every new tenant-owned object |
| MDR-007 | Runtime roles are non-owner and `NOBYPASSRLS`; transactions use `SET LOCAL` | Security | IMPLEMENTED | Bootstrap, RLS tests, services | Never replace with application filtering alone |
| MDR-008 | Immutable sealed inputs, results, trace, payslip and statutory evidence | Architecture | IMPLEMENTED | V023-V030 | Corrections append new evidence |
| MDR-009 | Idempotency, audit and outbox evidence commit atomically with business changes | Reliability | IMPLEMENTED pattern | Integrations and application services | Verify rollback/commit paths |
| MDR-010 | Money uses `BigDecimal`/decimal strings; binary floating point prohibited | Architecture | IMPLEMENTED policy | Code/contracts/tests | Preserve exact scale and serialization |
| MDR-011 | `mvn verify` is the backend gate; `mvn test` is insufficient | Process | IMPLEMENTED | `AGENTS.md`, Failsafe lessons | Show Failsafe phases in evidence |
| MDR-012 | Frontend lint, tests and production build are separate gates | Process | IMPLEMENTED | CI/regression | Do not infer lint from tests/build |
| MDR-013 | High-risk payroll changes require independent critical review | Process | IMPLEMENTED | `AGENTS.md` | Attach findings before completion |
| MDR-014 | One write-capable thread/agent owns overlapping files at a time | Process | APPROVED | Thread-maintenance protocol | Track ownership in thread registry |
| MDR-015 | V001-V030 are immutable; V031 remains unreserved | Database | IMPLEMENTED | `AGENTS.md`, merged main | Reserve V031 only through explicit approval |
| MDR-016 | Country-neutral statutory infrastructure is implemented; country-specific legal rules remain excluded | Product | IMPLEMENTED/CONTROLLED | Sprint 4, README | Require separate legal design |
| MDR-017 | Retro, off-cycle, final settlement, banking, accounting and legal payslip remain excluded | Product | APPROVED | README/AGENTS | Remove only through approved design |
| MDR-018 | Thread 1 design decisions are historical evidence, not automatic current state | Process | APPROVED | Thread 1 extract | Reconcile against current repository |
| MDR-019 | Dependency-review PR gate is deterministic; feed-dependent OWASP data requires cached/scheduled handling | Security | TEMPORARY/DEBT | CI reports/handoff | Close with centralized cache design |
| MDR-020 | Separate chat threads cannot implicitly share context; each must start from repository authority files | Process | APPROVED | Thread protocol | Use standard thread-start prompt |
| MDR-021 | Current repository HEAD and latest product implementation merge are distinct baselines and must be reported separately | Process | APPROVED | PR #20 merge `4b5da975...`; Sprint 4 merge `def3dd2e...` | Preserve both values in handoffs until a later product merge |
| MDR-022 | Sprint 4 is functionally merged but is not described as fully automated until statutory API integration and statutory browser E2E gaps are closed | Quality | APPROVED | Sprint 4 closure report, CI inventory | Implement S4-06A first; retain S4-06B as planned |
| MDR-023 | Repository text artifacts use explicit UTF-8 and must not introduce or preserve mojibake | Process | APPROVED | README/AGENTS cleanup | Reject corrupted punctuation during review |
| MDR-024 | When historical story labels diverge, identify work by capability, migration range, commit and PR rather than label alone | Process | APPROVED | Cross-thread reconciliation records | Use durable identifiers in history and handoffs |
| MDR-025 | PowerShell command output must be normalized with `@(...)`, cardinality checked and cast to `[string]` before string methods or indexing assumptions | Engineering | APPROVED | Phase A v1.0 failure: `System.Char.Trim()` | Apply to all generated PowerShell |
| MDR-026 | Repository update automation must use standard tools or deterministic complete-file payloads; avoid brittle literal preimages and treat process exit code as authoritative rather than warning text on stderr | Engineering | APPROVED | Phase A v1.1/v1.2 failures | Prefer `git apply`, `Copy-Item`, manifests and built-in checks |
| MDR-027 | Recovery work must preserve the original approved completion checklist; a phase cannot be declared complete while planned items are silently omitted | Governance | APPROVED | Cross-thread Phase A recovery | Reconcile planned versus actual files before PR/thread transition |

## Adding a decision

A material decision entry must include:

1. a stable ID;
2. exact decision text;
3. type and status;
4. rationale/evidence link;
5. implementation status;
6. conflict or supersession relationship;
7. handoff action.

Do not record brainstorms as approved decisions.
