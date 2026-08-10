#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relative) {
  return fs.readFileSync(path.join(root, ...relative.split('/')), 'utf8').replace(/\r\n/g, '\n');
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}
function authorities(relative) {
  return [...read(relative).matchAll(/hasAuthority\('([^']+)'\)/g)].map((m) => m[1]).sort();
}

const controller =
  'backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/FoundationReadinessController.java';
const expectedAuthorities = [
  'organisation.banking-readiness.read',
  'payroll-cycle.read',
  'statutory-registration.read',
].sort();
const observedAuthorities = authorities(controller);
assert(
  JSON.stringify(observedAuthorities) === JSON.stringify(expectedAuthorities),
  `Foundation readiness authority mismatch. Expected ${expectedAuthorities}, observed ${observedAuthorities}`
);

const bankingFacade = read(
  'backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/BankingReadinessFacade.java'
);
assert(bankingFacade.includes('BankingReadinessService'), 'Banking facade must delegate to BankingReadinessService.');
assert(bankingFacade.includes('return service.readiness('), 'Banking facade must not reimplement readiness rules.');

const registrationFacade = read(
  'backend/statutory-deductions/src/main/java/com/acme/hrms/payroll/statutory/RegistrationReadinessFacade.java'
);
assert(registrationFacade.includes('RegistrationReadinessService'), 'Registration facade must delegate to RegistrationReadinessService.');
assert(registrationFacade.includes('return service.evaluate(request);'), 'Registration facade must not reimplement readiness rules.');

const service = read(
  'backend/payroll-operations/src/main/java/com/acme/hrms/payroll/payrolloperations/internal/application/FoundationReadinessService.java'
);
for (const token of [
  'FOUNDATION_ONLY',
  'CALLER_DECLARED_REQUIREMENTS_ONLY',
  'CONFIGURATION_SNAPSHOT',
  'BANK_ACCOUNT',
  'SIGNATORY_AUTHORITY',
  'JURISDICTION_REGISTRATION',
  'COUNTRY_SPECIFIC_STATUTORY_RULES_RATES',
  'PAYMENT_EXECUTION_BANK_INTEGRATION',
]) {
  assert(service.includes(token), `Foundation readiness service missing bounded contract token: ${token}`);
}
assert(!service.includes('productionReady'), 'Foundation readiness must not expose production-ready semantics.');
assert(!service.includes('globalReady'), 'Foundation readiness must not expose global-ready semantics.');

const fragment = read('contracts/openapi/payroll-operations-openapi-v1.yaml');
for (const token of [
  'PayrollCycleFoundationReadiness:',
  'FoundationReadinessRequest:',
  'FoundationReadinessView:',
  'FoundationReadinessDimension:',
  'FoundationReadinessRegistrationCheck:',
  'FoundationReadinessFinding:',
  'x-permission: payroll-cycle.read',
  'organisation.banking-readiness.read',
  'statutory-registration.read',
  'CALLER_DECLARED_REQUIREMENTS_ONLY',
]) {
  assert(fragment.includes(token), `Payroll operations OpenAPI missing token: ${token}`);
}

const vertical = read('contracts/openapi/payroll-vertical-slice-openapi-v1.yaml');
assert(
  vertical.includes('/payroll-cycles/{cycleId}/foundation-readiness:'),
  'Vertical-slice OpenAPI missing composed foundation-readiness path.'
);
assert(
  vertical.includes(
    "$ref: './payroll-operations-openapi-v1.yaml#/pathItems/PayrollCycleFoundationReadiness'"
  ),
  'Vertical-slice foundation-readiness path must reference payroll-operations contract.'
);

const pom = read('backend/payroll-operations/pom.xml');
assert(pom.includes('<artifactId>organisation</artifactId>'), 'Payroll operations must depend on organisation public facade.');
assert(pom.includes('<artifactId>statutory-deductions</artifactId>'), 'Payroll operations must depend on statutory public facade.');

console.log('P5-FSR-01 G02 composed foundation-readiness contract alignment: PASS');
console.log('Cross-module readiness facades: PASS');
console.log('Bounded readiness scope/exclusions: PASS');
console.log('OpenAPI composition: PASS');
console.log('Security authority composition: PASS');
