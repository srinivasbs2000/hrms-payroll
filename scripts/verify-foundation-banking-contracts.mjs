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
function exactSet(actual, expected, label) {
  const a = [...new Set(actual)].sort();
  const e = [...new Set(expected)].sort();
  assert(
    a.length === e.length && a.every((value, index) => value === e[index]),
    `${label} mismatch. Expected [${e.join(', ')}], observed [${a.join(', ')}].`
  );
}
function authorities(relative) {
  return [...read(relative).matchAll(/hasAuthority\('([^']+)'\)/g)].map((m) => m[1]);
}
function block(text, startMarker, endMarker) {
  const start = text.indexOf(startMarker);
  assert(start >= 0, `Missing marker: ${startMarker}`);
  const end = text.indexOf(endMarker, start + startMarker.length);
  assert(end > start, `Missing end marker: ${endMarker}`);
  return text.slice(start, end);
}

const expectedRuntimePermissions = [
  'organisation.bank-account.approve',
  'organisation.bank-account.read',
  'organisation.bank-account.reveal',
  'organisation.bank-account.verify',
  'organisation.bank-account.write',
  'organisation.banking-readiness.read',
  'organisation.signatory.approve',
  'organisation.signatory.read',
  'organisation.signatory.verify',
  'organisation.signatory.write',
];

const runtimePermissions = [
  ...authorities('backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/EmployerBankAccountController.java'),
  ...authorities('backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/AuthorisedSignatoryController.java'),
  ...authorities('backend/organisation/src/main/java/com/acme/hrms/payroll/organisation/BankingReadinessController.java'),
];
exactSet(runtimePermissions, expectedRuntimePermissions, 'Runtime FBA permissions');

const openapi = read('contracts/openapi/payroll-vertical-slice-openapi-v1.yaml');
const requiredPaths = [
  '/employer-bank-accounts:',
  '/employer-bank-accounts/{identityId}:',
  '/employer-bank-accounts/{identityId}/versions:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/submit:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/verify:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/request-approval:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/approve:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/reject:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/suspend:',
  '/employer-bank-accounts/{identityId}/versions/{versionId}/reveal:',
  '/authorised-signatories:',
  '/authorised-signatories/{identityId}:',
  '/authorised-signatories/{identityId}/versions:',
  '/authorised-signatories/{identityId}/versions/{versionId}/submit:',
  '/authorised-signatories/{identityId}/versions/{versionId}/verify:',
  '/authorised-signatories/{identityId}/versions/{versionId}/request-approval:',
  '/authorised-signatories/{identityId}/versions/{versionId}/approve:',
  '/authorised-signatories/{identityId}/versions/{versionId}/reject:',
  '/authorised-signatories/{identityId}/versions/{versionId}/suspend:',
  '/authorised-signatories/authority-evaluations:',
  '/banking-readiness:',
];
for (const apiPath of requiredPaths) {
  assert(openapi.includes(`  ${apiPath}`), `OpenAPI path missing: ${apiPath}`);
}
for (const permission of expectedRuntimePermissions) {
  assert(openapi.includes(`x-permission: ${permission}`), `OpenAPI x-permission missing: ${permission}`);
}
assert(
  !openapi.includes('banking, accounting, retro, final settlement and payment execution remain outside this contract'),
  'OpenAPI still declares banking outside the contract.'
);

const normalBankSchema = block(
  openapi,
  '    FbaEmployerBankAccountView:',
  '    FbaEmployerBankAccountRevealView:'
);
assert(
  !/\n\s+accountNumber:/.test(normalBankSchema),
  'Normal employer bank-account view must not expose accountNumber.'
);
assert(
  normalBankSchema.includes('maskedAccountNumber:'),
  'Normal employer bank-account view must expose maskedAccountNumber.'
);

const revealSchema = block(
  openapi,
  '    FbaEmployerBankAccountRevealView:',
  '    FbaSignatoryScopeRequest:'
);
assert(/\n\s+accountNumber:/.test(revealSchema), 'Reveal view must contain accountNumber.');

const revealPath = block(
  openapi,
  '  /employer-bank-accounts/{identityId}/versions/{versionId}/reveal:',
  '  /authorised-signatories:'
);
assert(revealPath.includes('x-permission: organisation.bank-account.reveal'), 'Reveal permission mismatch.');
assert(revealPath.includes('Cache-Control:'), 'Reveal response must document Cache-Control.');
assert(revealPath.includes('const: no-store'), 'Reveal response must document no-store.');
assert(revealPath.includes('Pragma:'), 'Reveal response must document Pragma no-cache.');

const realm = JSON.parse(read('deploy/local/keycloak/payroll-realm.json'));
const admin = realm.users.find((user) => user.username === 'payroll.admin');
const smoke = realm.users.find((user) => user.username === 'payroll.smoke');
assert(admin, 'payroll.admin missing from Keycloak realm.');
assert(smoke, 'payroll.smoke missing from Keycloak realm.');

const adminPermissions = admin.attributes?.permissions ?? [];
for (const permission of expectedRuntimePermissions) {
  assert(adminPermissions.includes(permission), `payroll.admin missing permission: ${permission}`);
}

const smokeExpected = [
  'organisation.bank-account.read',
  'organisation.banking-readiness.read',
  'organisation.signatory.read',
];
for (const permission of smokeExpected) {
  assert((smoke.attributes?.permissions ?? []).includes(permission), `payroll.smoke missing permission: ${permission}`);
}
for (const permission of expectedRuntimePermissions.filter((value) => !smokeExpected.includes(value))) {
  assert(
    !(smoke.attributes?.permissions ?? []).includes(permission),
    `payroll.smoke must not receive privileged FBA permission: ${permission}`
  );
}

console.log('P5-FBA-01 foundation banking contract alignment: PASS');
console.log(`Runtime permissions: ${expectedRuntimePermissions.length}`);
console.log(`OpenAPI paths: ${requiredPaths.length}`);
console.log('Keycloak admin FBA permissions: PASS');
console.log('Keycloak smoke least-privilege FBA permissions: PASS');
console.log('Bank-account normal/reveal secret boundary: PASS');
