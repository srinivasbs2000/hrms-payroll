# P5-FAD-01 approval endpoint inventory

**Baseline:** `b4267168892eb602764d194eb0f303f8d8233323`
**Capability:** P5-FAD-01 — Foundation Approval & Delegation
**Migration:** V037
**Inventory purpose:** freeze implemented approval surfaces before product mutation.

## Owner-scoped G02 integration matrix

| Surface | Command | Existing permission | FAD role | Domain | Action | FAD owner resolution |
| --- | --- | --- | --- | --- | --- | --- |
| Legal entity version | `.../approval` | `organisation.approve` | FINAL_APPROVER | ORGANISATION_CONFIG | APPROVE | LEGAL_ENTITY / identity |
| PSU version | `.../approval` | `organisation.approve` | FINAL_APPROVER | ORGANISATION_CONFIG | APPROVE | PAYROLL_STATUTORY_UNIT / identity |
| Establishment version | `.../approval` | `organisation.approve` | FINAL_APPROVER | ORGANISATION_CONFIG | APPROVE | Parent PSU stable identity |
| Work-location version | `.../approval` | `organisation.approve` | FINAL_APPROVER | WORK_LOCATION | APPROVE | PSU from exact establishment version |
| Jurisdiction override | `.../approval` | `organisation.approve` | FINAL_APPROVER | JURISDICTION_OVERRIDE | APPROVE | PSU from exact establishment/work-location version |
| Employer bank account | `.../verify` | `organisation.bank-account.verify` | VERIFIER | EMPLOYER_BANK_ACCOUNT | VERIFY | Existing LE/PSU owner |
| Employer bank account | `.../request-approval` | `organisation.bank-account.verify` | VERIFIER | EMPLOYER_BANK_ACCOUNT | REQUEST_APPROVAL | Existing LE/PSU owner |
| Employer bank account | `.../approve` | `organisation.bank-account.approve` | FINAL_APPROVER | EMPLOYER_BANK_ACCOUNT | APPROVE | Existing LE/PSU owner |
| Employer bank account | `.../reject` | `organisation.bank-account.approve` | FINAL_APPROVER | EMPLOYER_BANK_ACCOUNT | REJECT | Existing LE/PSU owner |
| Employer bank account | `.../suspend` | `organisation.bank-account.approve` | FINAL_APPROVER | EMPLOYER_BANK_ACCOUNT | SUSPEND | Existing LE/PSU owner |
| Authorised signatory | `.../verify` | `organisation.signatory.verify` | VERIFIER | AUTHORISED_SIGNATORY | VERIFY | Existing LE/PSU owner |
| Authorised signatory | `.../request-approval` | `organisation.signatory.verify` | VERIFIER | AUTHORISED_SIGNATORY | REQUEST_APPROVAL | Existing LE/PSU owner |
| Authorised signatory | `.../approve` | `organisation.signatory.approve` | FINAL_APPROVER | AUTHORISED_SIGNATORY | APPROVE | Existing LE/PSU owner |
| Authorised signatory | `.../reject` | `organisation.signatory.approve` | FINAL_APPROVER | AUTHORISED_SIGNATORY | REJECT | Existing LE/PSU owner |
| Authorised signatory | `.../suspend` | `organisation.signatory.approve` | FINAL_APPROVER | AUTHORISED_SIGNATORY | SUSPEND | Existing LE/PSU owner |
| Statutory registration | `.../verification` | `statutory-registration.verify` | VERIFIER | STATUTORY_REGISTRATION | VERIFY | LE/PSU direct; establishment -> parent PSU |
| Statutory registration | `.../approval-request` | `statutory-registration.verify` | VERIFIER | STATUTORY_REGISTRATION | REQUEST_APPROVAL | LE/PSU direct; establishment -> parent PSU |
| Statutory registration | `.../approval` | `statutory-registration.approve` | FINAL_APPROVER | STATUTORY_REGISTRATION | APPROVE | LE/PSU direct; establishment -> parent PSU |
| Statutory registration | `.../rejection` | `statutory-registration.approve` | FINAL_APPROVER | STATUTORY_REGISTRATION | REJECT | LE/PSU direct; establishment -> parent PSU |
| Statutory registration | `.../suspension` | `statutory-registration.approve` | FINAL_APPROVER | STATUTORY_REGISTRATION | SUSPEND | LE/PSU direct; establishment -> parent PSU |

The persisted FAD owner vocabulary remains exactly `LEGAL_ENTITY` and
`PAYROLL_STATUTORY_UNIT`. Establishment-owned workflows inherit the parent PSU
scope; no third owner kind is introduced.

## Inventory-confirmed G02 exclusions

These implemented approvals are tenant-global/catalogue configuration and have
no existing LE/PSU owner. Forcing them into FAD would invent scope not supported
by PLN-E01-011:

- payroll-jurisdiction version approval;
- statutory-registration-type approval;
- pay-component approval;
- payroll-base version and membership approval;
- salary-structure approval;
- CTC-policy approval;
- eligibility-rule approval.

They retain existing endpoint permission and maker-checker controls. Adding an
owner dimension to those product models requires separate authority.

## G01/G02 split

G01 creates only the V037/security authority core and administration contract.
It changes no business-domain approval transition.

G02 may mutate only owner-scoped surfaces in the matrix above. Existing domain
lifecycle state machines remain authoritative. Endpoint permission and shared
authority must both pass. Legal authorised-signatory authority remains separate
from application approval authority.
