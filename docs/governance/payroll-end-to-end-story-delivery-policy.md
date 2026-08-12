# Payroll End-to-End Story Delivery Policy

**Status:** Mandatory story completion governance
**Effective:** 13 August 2026
**Backend authority:** `srinivasbs2000/hrms-payroll`
**UI authority:** `srinivasbs2000/hrms-payroll-web`

## 1. Story completion model

A detailed story is a vertical product slice. `IMPLEMENTED` is permitted only
when every applicable delivery surface is complete and evidenced.

Mandatory delivery-surface decision before implementation:

| Surface | Required decision |
|---|---|
| Data / migration | REQUIRED or N/A |
| Domain / backend | REQUIRED or N/A |
| API / OpenAPI | REQUIRED or N/A |
| Product UI / workflow | REQUIRED or N/A |
| Operational / admin / audit UI | REQUIRED or N/A |
| Authorization / SoD | REQUIRED or N/A |
| Audit / lineage | REQUIRED or N/A |
| Automated tests | REQUIRED or N/A |
| Real-backend browser E2E | REQUIRED or N/A |

## 2. UI applicability rule

- `Business / Workflow` defaults to `REQUIRED_PRODUCT_UI`.
- Human operational visibility defaults to `REQUIRED_OPERATIONAL_UI`.
- Security administration requires an application or explicitly governed
  external administration surface.
- Auditor/lineage stories require read-only inspection capability.
- API-contract, event-contract and technical-test stories may be
  `NOT_REQUIRED_DIRECTLY`; their consuming business stories still carry the
  product UI obligation.
- An exception to a required UI surface must be explicit in activation authority
  with business justification. Silence is not an exception.

## 3. Hard closure gate

For any story whose UI applicability is not `NOT_REQUIRED_DIRECTLY`:

`IMPLEMENTED` requires:

1. backend/domain acceptance criteria green;
2. API/OpenAPI green where applicable;
3. required operator/admin/audit UI complete;
4. authorization and tenant boundaries enforced in the UI and API;
5. frontend tests/build green;
6. real-browser E2E against the authoritative backend green; and
7. story-level evidence recorded in the UI applicability matrix.

Backend-only completion is `PARTIALLY IMPLEMENTED`.

## 4. Cross-repository capability rule

Capability activation must identify both backend and UI repository ownership
when any selected story requires UI. Product closure must reconcile both merge
SHAs and browser evidence before story status can become `IMPLEMENTED`.

## 5. Canonical matrix

`backlog/payroll-story-ui-applicability.csv` is mandatory input to every future
R3 reconciliation and capability activation.