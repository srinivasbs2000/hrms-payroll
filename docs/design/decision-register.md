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
| MDR-004 | Stable identity plus immutable effective-dated exact versions | Architecture | IMPLEMENTED | V015, V017, V019–V021 and later lineage | Document mapping in future upgrades |
| MDR-005 | Half-open effective ranges and no overlap for approved versions | Architecture | IMPLEMENTED | Migrations/tests | Preserve negative tests |
| MDR-006 | Tenant-safe composite FKs plus ENABLE/FORCE RLS | Security | IMPLEMENTED | V011 onward and verification SQL | Verify every new tenant-owned object |
| MDR-007 | Runtime roles are non-owner and `NOBYPASSRLS`; transactions use `SET LOCAL` | Security | IMPLEMENTED | Bootstrap, RLS tests, services | Never replace with application filtering alone |
| MDR-008 | Immutable sealed inputs, results, trace, payslip and statutory evidence | Architecture | IMPLEMENTED | V023–V030 | Corrections append new evidence |
| MDR-009 | Idempotency, audit and outbox evidence commit atomically with business changes | Reliability | IMPLEMENTED pattern | Integrations and application services | Verify rollback/commit paths |
| MDR-010 | Money uses `BigDecimal`/decimal strings; binary floating point prohibited | Architecture | IMPLEMENTED policy | Code/contracts/tests | Preserve exact scale and serialization |
| MDR-011 | `mvn verify` is the backend gate; `mvn test` is insufficient | Process | IMPLEMENTED | `AGENTS.md`, Failsafe lessons | Show Failsafe phases in evidence |
| MDR-012 | Frontend lint, tests and production build are separate gates | Process | IMPLEMENTED | CI/regression | Do not infer lint from tests/build |
| MDR-013 | High-risk payroll changes require independent critical review | Process | IMPLEMENTED | `AGENTS.md` | Attach findings before completion |
| MDR-014 | One write-capable thread/agent owns overlapping files at a time | Process | APPROVED | Thread-maintenance protocol | Track ownership in thread registry |
| MDR-015 | V001–V030 are immutable; new schema work begins at V031 | Database | IMPLEMENTED | `AGENTS.md`, merged main | Update after future merged migrations |
| MDR-016 | Country-neutral statutory infrastructure is implemented; country-specific legal rules remain excluded | Product | IMPLEMENTED/CONTROLLED | Sprint 4, README | Require separate legal design |
| MDR-017 | Retro, off-cycle, final settlement, banking, accounting and legal payslip remain excluded | Product | APPROVED | README/AGENTS | Remove only through approved design |
| MDR-018 | Thread 1 design decisions are historical evidence, not automatic current state | Process | APPROVED | Thread 1 extract | Reconcile against current repository |
| MDR-019 | Dependency-review PR gate is deterministic; feed-dependent OWASP data requires cached/scheduled handling | Security | TEMPORARY/DEBT | CI reports/handoff | Close with centralized cache design |
| MDR-020 | Separate chat threads cannot implicitly share context; each must start from repository authority files | Process | APPROVED | Thread protocol | Use standard thread-start prompt |

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
