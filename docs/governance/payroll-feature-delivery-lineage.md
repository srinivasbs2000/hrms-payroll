# Payroll Feature Delivery Lineage

**Repository baseline:** `23df1f7a11f4090cef8715eba7104f5b1138b760`
**Product implementation baseline:** `b4f3013e1d7404d09eac64a305ad3736e5a28a5c`
**Purpose:** Trace original epic -> original backlog row -> current story -> migration -> commit -> PR/merge -> evidence -> remaining scope.

## 1. Epic-level lineage

| Epic | Current Stories | Migrations | Commit Evidence | PR/Merge | Classification |
|---|---|---|---|---|---|
| E01 | S1-00, S1-01, S1-02, S1-03, S1-04, S1-05, S1-06 | V014-V016 (plus foundational V001-V013) | b9f6bcf888c4d22c04a237f0a13c37d4d4d24c36; 47654eb11c69838924da172479a4c4d72a5c2729; da5dad33d7d228e1e7ee1467fb198ec21d0934ca | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab | PARTIALLY IMPLEMENTED |
| E02 | S2-01, S2-02; population execution contributes through S3-01 | V017-V018 | Inherited Sprint 2 range through 24f2ed4893a90627eb6be69aa3747eba4343e195 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 | PARTIALLY IMPLEMENTED |
| E03 | S2-03; P5-A2 general catalogue and named bases | V019; V032 | Sprint 2 foundation plus `c30cb1f2f0c16cd78387bb9551b93825bc7ef688` | PR #3; PR #30 merge `aeb4b1560e7c7d6147bb288ef989b15ad1be4946` | PARTIALLY IMPLEMENTED |
| E04 | S2-04 | V020 | Inherited Sprint 2 entry head 24f2ed4893a90627eb6be69aa3747eba4343e195 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 | PARTIALLY IMPLEMENTED |
| E05 | S2-05, S2-06; generic statutory profile contribution from S4-02 | V021-V022; V028 for generic statutory profiles | adf3769b945d56828aa984e634e6e1bbb62582d7; 1575cbc373bf4dc22ff116b1ea4bbfb7e5a19288; 63c9b1a719765fce3868eb7fc69fac37bc196dc9; 12536c3f629cf567022f3fd50998397d1d0b5911; e98f70b0346a13e463f8e768ab4014be0e30ca0f | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 | PARTIALLY IMPLEMENTED |
| E06 | S3-03, S3-04, S3-05 | V025-V026 | c9ada6bad94071d70a6d10fbcfec085d476a6279; f7eb7fa1fc152b8da4088b881f03bff18558d140; db644298ab3197a6931cd9c6b8d9875ef30d28c5 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca | PARTIALLY IMPLEMENTED |
| E07 | S3-05 and controlled recalculation increment; S4-04 for statutory balances | V026 and V030 | db644298ab3197a6931cd9c6b8d9875ef30d28c5; 34a3af93433eb61b801db36c8ff84fe1ccfad874 | PR #18 and PR #19 | PARTIALLY IMPLEMENTED |
| E08 | S3-01, S3-02, S3-07 | V023-V024 | 5bc08e440c21bbeeddc3c1bb4e28ad04943ac9cd; 64b4ca7b2a7a53c373b56d5f6767192a000dd60f; 625e38dc1fed649eb37ec6c1d1171f142430403a; 134fe3e63e6b04f2da08df957f4d415a1fd97606; 7fee492bd8899269fe588c9d3ab8202029a8b0b5 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca | PARTIALLY IMPLEMENTED |
| E09 | S4-01B, S4-02, S4-03, S4-04, S4-05A, S4-05B provide only a jurisdiction-neutral foundation | V027-V030 (generic foundation only) | 7a98bef0e239972b8200b363138e5b35007948da; 218c099fcbfa4218f4a949673de7268c243e37ed; 49e72119a3daa567ae989af3b237da383cdbaebb; 34a3af93433eb61b801db36c8ff84fe1ccfad874; 206881e088b8a2d4226cee5db9ca079fcb975e7a; 6cf39fc1734a50a514cfee22db2fd78bd41b80cc | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 | REQUIRES LEGAL OR DOMAIN REVALIDATION |
| E10 | None; only reusable correction/recalculation patterns exist | None | None | None | NOT STARTED |
| E11 | None | None | None | None | NOT STARTED |
| E12 | None | None | None | None | NOT STARTED |
| E13 | S3-06 | Uses calculation evidence through V026; no legal publication aggregate | 7fee492bd8899269fe588c9d3ab8202029a8b0b5; d54085b87b6fd7a92b0d3b20a35618ff2f169663 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca | PARTIALLY IMPLEMENTED |
| E14 | None | None | None | None | NOT STARTED |
| E15 | S0-03, S0-05, S0-06, S0-07; S1-00, S1-01, S1-02, S1-05, S1-06; cross-cutting controls in S2-S4 | V002, V010-V016 and later tenant/audit controls | b9f6bcf888c4d22c04a237f0a13c37d4d4d24c36; 47654eb11c69838924da172479a4c4d72a5c2729; plus cross-sprint commits | PR #2, PR #3, PR #18 and PR #19 | PARTIALLY IMPLEMENTED |
| E16 | None; Flyway schema migrations are not business-data migration | None for product migration | None | None | NOT STARTED |
| E17 | None | None | None | None | NOT STARTED |
| E18 | S0-06, S0-07, S0-08; S1-00 and cross-sprint CI/reliability controls | Cross-cutting | Repository/CI baseline and b9f6bcf888c4d22c04a237f0a13c37d4d4d24c36 | Baseline plus PR #2-#19 | PARTIALLY IMPLEMENTED |

## 2. Current sprint delivery ledger

| Story | Sprint | Feature | Status | Original Epic Mapping | Migration | PR/Merge |
|---|---|---|---|---|---|---|
| S0-01 | Sprint 0 | Freeze approved versions and dependency policy | MERGED BASELINE | E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-02 | Sprint 0 | Create monorepo, branch protections and CODEOWNERS | MERGED BASELINE | E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-03 | Sprint 0 | Approve modular monolith, RLS and outbox ADRs | MERGED BASELINE | E01/E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-04 | Sprint 0 | Create migration pipeline and schema verification | MERGED BASELINE | E01/E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-05 | Sprint 0 | Configure development OIDC realm and tenant claim | MERGED BASELINE | E15 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-06 | Sprint 0 | Configure unit, integration, architecture and frontend gates | MERGED BASELINE | E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-07 | Sprint 0 | Establish correlation IDs, structured logs and metrics | MERGED BASELINE | E15/E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S0-08 | Sprint 0 | Publish Compose setup and golden seed | MERGED BASELINE | E18 | V001-V013 | Baseline main bba8a51d17147443e400f51bc9ccf769b8bd1af8 |
| S1-00 | Sprint 1 | Outbox/inbox and idempotency gate | MERGED | E01/E15/E18 | V014 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-01 | Sprint 1 | Tenant context and SET LOCAL | MERGED | E01/E15 | V014-V016 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-02 | Sprint 1 | OIDC issuer/subject and permissions | MERGED | E01/E15 | V014-V016 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-03 | Sprint 1 | Legal entity and PSU APIs/UI | MERGED | E01 | V015-V016 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-04 | Sprint 1 | Establishment API/UI | MERGED | E01 | V015-V016 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-05 | Sprint 1 | Append-only audit writer | MERGED | E01/E15 | V014-V016 | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S1-06 | Sprint 1 | Spring Modulith verification | MERGED | E15/E18 | None | PR #2; merge 27947e1202ff018c3494a32584487ff3879876ab |
| S2-01 | Sprint 2 | Monthly calendar and period generation | MERGED | E02 | V018 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S2-02 | Sprint 2 | Pay group and proration policy | MERGED | E02 | V017 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S2-03 | Sprint 2 | BASIC, HRA and SPECIAL components | MERGED | E03 | V019 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S2-04 | Sprint 2 | Salary structure and dependency validation | MERGED | E04 | V020 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S2-05 | Sprint 2 | Relationship, assignment and payroll profile | MERGED | E05 | V021-V022 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S2-06 | Sprint 2 | Salary and pay-group assignments | MERGED | E05 | V021-V022 | PR #3; merge 84530e1fe975dbe5f2a45feb3ceabd44d8b4fbb9 |
| S3-01 | Sprint 3 | Payroll cycle and population | MERGED | E02/E08 | V023 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-02 | Sprint 3 | Seal immutable calculation input | MERGED | E08 | V024 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-03 | Sprint 3 | Compile BASIC-HRA-SPECIAL plan | MERGED | E06 | V025 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-04 | Sprint 3 | Calendar-day proration | MERGED | E06 | V025-V026 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-05 | Sprint 3 | Immutable result and component trace | MERGED | E06/E07 | V025-V026 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-06 | Sprint 3 | Immutable draft-payslip view | MERGED | E13 | V026 / calculation evidence | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S3-07 | Sprint 3 | Organisation-to-payslip golden scenario | MERGED | E06/E08/E13/E18 | V023-V026 | PR #18; merge 73c356662b1888194a72c7006a66bd91443550ca |
| S4-01B | Sprint 4 | Jurisdiction-neutral statutory rule foundation | MERGED | E09 foundation only | V027 | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-02 | Sprint 4 | Employee statutory profiles and assignments | MERGED | E05/E09 foundation only | V028 | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-03 | Sprint 4 | Deterministic statutory evaluation | MERGED | E09 foundation only | V029 | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-04 | Sprint 4 | Ledger, balances and reconciliation | MERGED | E07/E09 foundation only | V030 | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-05A | Sprint 4 | Controlled statutory APIs | MERGED / QUALITY CLOSURE PENDING | E09 foundation only | V027-V030 | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-05B | Sprint 4 | Permission-aware statutory workspace | MERGED / QUALITY CLOSURE PENDING | E09 foundation only | None | PR #19; merge def3dd2e212f85c440eee5497e292be2f1f2bf64 |
| S4-06A | Sprint 4 | Real secured HTTP/PostgreSQL statutory integration test | SELECTED / NOT STARTED | E09 foundation quality | NONE | No branch or PR |
| S4-06B | Sprint 4 | Statutory-specific Playwright E2E | PLANNED / NOT AUTHORISED | E09 foundation quality | NONE | No branch or PR |

## 3. Delivery interpretation

- 34 current repository stories/slices are merged or part of the merged baseline.
- S4-06A is selected but not started.
- S4-06B is planned but not authorised.
- The merged stories do not close the original 72 backlog rows automatically.
- Detailed row-by-row lineage is in `payroll-master-implementation-backlog-draft.xlsx`.

## 4. Documentation conflict

`main` is `18d5ca3554ff217140b7e3c443d086d63bd02070`, but the committed master design, handoff and thread
registry still contain the earlier `4b5da975...` pre-PR-21 checkpoint. This is a
documentation-state conflict, not a product implementation conflict.

## 5. Lineage update rule

Every future detailed story must retain:

- original epic ID;
- source iteration and source story ID;
- approved sprint story ID;
- migration or `NONE`;
- implementation commit or commit range;
- pull request and merge commit;
- verification/closure artifact;
- remaining gap or approved deferral.

A story may be marked complete only when its approved acceptance criteria and
required verification evidence are committed.

## P5 product-increment closure

| Increment | Capability | Activation evidence | Implementation and publication evidence | Migration |
|---|---|---|---|---|
| P5-A2 | General pay-component catalogue and named payroll bases | `e9e297de5e59762f3701ce39ca2295e1839d7d16` | Implementation `c30cb1f2f0c16cd78387bb9551b93825bc7ef688`; PR #30 merged as `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`; post-merge workflow run `30957450623` successful; authority closed | V032 committed and immutable |

P5-A2's exact 46-path boundary is preserved in
`docs/planning/pln-01/p5-a2-compensation-configuration-scope.md`. E03 remains
partially implemented because formula execution through named bases and the
broader compensation-design scope remain outside P5-A2. V033 and P5-A3 remain
separately controlled.

## P5-A3 local delivery lineage

| Increment | Capability | Migration | Local evidence | Publication |
|---|---|---|---|---|
| P5-A3 | Salary-structure design, versioned CTC policies, typed eligibility rules and deterministic design-time simulation/validation | V033 | G02-B–G07; OpenAPI valid; 25 focused and 85 complete frontend tests; frontend build; Maven build success; 220 backend tests; PostgreSQL 17.10 | Not yet committed or published |

P5-A3 remains configuration-design scope. Formula-engine expansion, official
payroll, legal rules/rates, employee assignment and live eligibility persistence
remain outside this increment.

<!-- P5-A3-PROGRAM-RECONCILIATION -->
## P5-A3 merge and program reconciliation

Current P5-A3 merged through PR #32 and the React test-hygiene follow-up merged
through PR #33.

Primary original-package mapping:

- current P5-A1 -> original P5-A1;
- current P5-A2 -> primarily original P5-B1 and selected P5-B3 controls;
- current P5-A3 -> primarily original P5-B4, P5-B5 and selected P5-B6 controls.

Reconciled detailed-story counts:

- 11 implemented;
- 155 partially implemented;
- 94 not evidenced;
- 159 not started;
- 31 legal/domain revalidation.

The complete story ledger is
`backlog/payroll-detailed-story-status.csv`.

Recommended next package: original P5-A2 jurisdiction and registration
foundations. This recommendation grants no implementation or migration
authority.
