# P5-A2 Compensation Configuration Scope and Closure

**Status:** CLOSED — PR #30 merged; product and migration write authority released
**Repository baseline:** `aeb4b1560e7c7d6147bb288ef989b15ad1be4946`
**Capability branch:** `feature/p5-a2-compensation-catalogue-named-bases`
**Migration state:** V001-V032 committed and immutable; V033 unreserved
**Activated:** 4 August 2026
**Closed:** 5 August 2026
**Conversation context:** Payroll System Design – Thread 6
**Repository owner label:** P5-A2 capability workstream, not a new numbered chat thread

## Greenfield product position

HRMS Payroll remains a greenfield product. There is no evidenced production
deployment, live payroll migration or customer production data estate.

Forward-only Flyway migrations, immutable committed migration history,
schema-version markers and populated-upgrade tests are retained because they:

- make developer, CI and test environments reproducible;
- protect committed fixtures and local databases;
- detect accidental data-lineage breakage early;
- provide a safe upgrade path when production deployment begins.

They do not imply that a production upgrade is currently being performed.

## Historical activation boundary

P5-A2 activation authorised the capability branch, exclusive V032 reservation,
the exact maximum 46-path allow-list and implementation preparation. Product
implementation was separately authorised and completed within that boundary.

The activation never authorised legal or statutory truth, calculator semantic
changes, salary-structure/CTC expansion, dependency or deployment changes, or
branch deletion. Those exclusions remain historical delivery controls.

## Corrected architecture controls

- Preserve `pay_component.component_type` as
  `EARNING|DEDUCTION|INFORMATION`.
- Add broader business catalogue classification at component-version level.
- Preserve existing approval history; use `catalogue_schema_version=0` for
  pre-V032 versions and `1` for complete P5-A2 versions.
- Split component identity-creation and version-write request contracts.
- Enforce maker-checker for schema-1 component versions, payroll-base versions
  and component/base memberships.
- Use append-only exact-version memberships with supersession and approved
  non-overlap.
- Keep membership treatments non-executable:
  `INCLUDE`, `EXCLUDE`, `ADD_BACK`, `ELIGIBILITY_ONLY`,
  `CONTRIBUTION_ONLY`, `NOTIONAL`.
- Store inclusion percentages as `numeric(12,8)` and expose decimal strings.
- Preserve existing calculation direction, gross/deduction/net totals, hashes
  and trace lineage.
- Introduce no Indian legal rates, thresholds or statutory conclusions.

## Exact maximum implementation allow-list

1. `database/flyway/sql/V032__compensation_catalogue_named_bases.sql`
2. `database/flyway/README.md`
3. `database/flyway/verification/verify_vertical_slice.sql`
4. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/CompensationCatalogueMigrationIT.java`
5. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/PayComponentMigrationIT.java`
6. `backend/database-migrations/src/test/java/com/acme/hrms/payroll/migrations/RowLevelSecurityIT.java`
7. `docs/planning/pln-01/p5-a2-compensation-configuration-scope.md`
8. `docs/quality/p5-a2-compensation-catalogue-named-bases.md`
9. `docs/runbooks/pay-component-configuration.md`
10. `docs/runbooks/payroll-base-configuration.md`
11. `docs/design/decision-register.md`
12. `docs/design/hrms-payroll-master-design.md`
13. `docs/governance/payroll-feature-delivery-lineage.md`
14. `docs/governance/thread-registry.md`
15. `docs/runbooks/project-continuation-handoff.md`
16. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayComponentController.java`
17. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayComponentView.java`
18. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayComponentWriteRequest.java`
19. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayComponentCreateRequest.java`
20. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayComponentVersionWriteRequest.java`
21. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/PayComponentService.java`
22. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/infrastructure/PayComponentRepository.java`
23. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/PayComponentContractTest.java`
24. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayrollBaseController.java`
25. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayrollBaseView.java`
26. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayrollBaseCreateRequest.java`
27. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/PayrollBaseVersionWriteRequest.java`
28. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/ComponentBaseMembershipView.java`
29. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/ComponentBaseMembershipWriteRequest.java`
30. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/application/PayrollBaseService.java`
31. `backend/compensation/src/main/java/com/acme/hrms/payroll/compensation/internal/infrastructure/PayrollBaseRepository.java`
32. `backend/compensation/src/test/java/com/acme/hrms/payroll/compensation/PayrollBaseContractTest.java`
33. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/PayComponentApiIT.java`
34. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/CompensationCatalogueApiIT.java`
35. `backend/payroll-boot/src/test/java/com/acme/hrms/payroll/CompensationCatalogueCalculationCompatibilityIT.java`
36. `contracts/openapi/payroll-vertical-slice-openapi-v1.yaml`
37. `deploy/local/keycloak/payroll-realm.json`
38. `frontend/payroll-web/src/App.tsx`
39. `frontend/payroll-web/src/App.test.tsx`
40. `frontend/payroll-web/src/features/pay-component/PayComponentPage.tsx`
41. `frontend/payroll-web/src/features/pay-component/PayComponentPage.test.tsx`
42. `frontend/payroll-web/src/features/pay-component/pay-component-api.ts`
43. `frontend/payroll-web/src/features/payroll-base/PayrollBasePage.tsx`
44. `frontend/payroll-web/src/features/payroll-base/PayrollBasePage.test.tsx`
45. `frontend/payroll-web/src/features/payroll-base/payroll-base-api.ts`
46. `frontend/payroll-web/src/styles.css`

## Stop-and-split boundary

Stop and obtain separate approval for:

- any path outside the list above;
- any modification of V001-V031;
- any legal rate, threshold or statutory classification;
- any current calculator/result/trace semantic change;
- any unrestricted expression execution;
- any salary-structure, CTC, eligibility or employee-assignment expansion;
- any dependency, CI/workflow or deployment change;
- any inability to preserve exact UUID and tenant-safe FK lineage.

## Post-merge authority closure

P5-A2 was activated at `e9e297de5e59762f3701ce39ca2295e1839d7d16`, implemented at
`c30cb1f2f0c16cd78387bb9551b93825bc7ef688`, and merged through PR #30 as
`aeb4b1560e7c7d6147bb288ef989b15ad1be4946`. The post-merge `payroll-baseline` workflow run
`30957450623` completed successfully.

The exact 46-path boundary above is historical delivery evidence and grants no
continuing write ownership. V032 is committed and immutable. V033 is not
reserved. The historical feature branch remains retained. P5-A3 and S4-06B are
not activated or authorised by this closure.
