# HRMS Payroll Thread and Capability Registry

**Last verified:** 8 August 2026
**Repository baseline:** `main` at `ff581cafce3be5495d93932abfae3931b139358f`
**Latest merged product increment:** Current P5-A3 through PR #32
**Latest merged quality increment:** PR #33

Thread numbers are historical conversation labels, not implementation
authority. Only an explicitly active capability entry may own files or a
migration number.

## Historical threads

| Thread | Status | Scope | Ownership |
|---|---|---|---|
| Thread 1 | INACTIVE | Full-product authority and planning history | None |
| Thread 2 | CLOSED | Sprint 2 and early Sprint 3 | None |
| Thread 3 | CLOSED | Sprint 3 completion | None |
| Thread 4 | CLOSED | Sprint 4 generic statutory foundation | None |
| Thread 5 | CLOSED | Recovery and process audit | None |
| Thread 6 | CLOSED | P5 planning and P5-A1 history | None |
| Thread 7 | CLOSED | S4-06A quality closure | None |

## Capability history

| Execution label | Status | Primary scope | PR/merge | Migration | Ownership |
|---|---|---|---|---|---|
| P5-A1 | CLOSED | Organisation hierarchy lifecycle closure | PR #25 merged | V031 immutable | None |
| P5-A2 | CLOSED | Component catalogue and named payroll bases | PR #30 merged; PR #31 closure | V032 immutable | None |
| P5-A3 | CLOSED | Salary structure, CTC, eligibility and design-time simulation | PR #32 merged; PR #33 quality follow-up | V033 immutable | None |
| S4-06A | CLOSED | Secured statutory API integration quality | PR #28 merged; PR #29 closure | None | None |
| P5-JRF-01 | ACTIVE — LOCAL GREEN | Jurisdiction and registration foundations | Local AC-G03-B1 v1.3 and AC-G03-B2 v1.2 evidence; publication pending | V034 reserved / uncommitted | `feature/p5-jrf-01-jurisdiction-registration-foundations`; reviewed 88-path maximum |

## Active capability workstream

**P5-JRF-01 — LOCAL IMPLEMENTATION VERIFIED / PUBLICATION PENDING.**

- Active branch: `feature/p5-jrf-01-jurisdiction-registration-foundations`.
- Starting main authority: `ff581cafce3be5495d93932abfae3931b139358f`.
- Only the reviewed 88-path maximum boundary is authorized.
- V001–V033 are immutable.
- V034 is locally implemented and remains exclusively reserved until
  publication and explicit authority closure.
- Assistant/agent GitHub access remains read-only.
- S4-06B remains planned and unauthorized.
- Historical branches are retained and must not be reused or deleted implicitly.

## Program-status closure process

The documentation branch created for the program-status closure owns only its
exact ten-path documentation/governance boundary until its PR is merged or
closed. It grants no product or migration authority.

After each future product increment:

1. verify product merge;
2. reconcile the detailed-story ledger;
3. update `docs/governance/payroll-program-status.md`;
4. merge a small status-closure PR;
5. release ownership and migration reservation;
6. separately authorize the next capability.

## Mandatory GitHub boundary

Assistant and agent GitHub access remains strictly read-only. Repository
mutations are performed by the project owner through deterministic local
packages and returned evidence.
