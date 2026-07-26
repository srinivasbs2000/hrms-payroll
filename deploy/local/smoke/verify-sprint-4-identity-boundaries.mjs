import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';

const repository = process.env.HRMS_PAYROLL_REPOSITORY;
const evidenceDirectory = process.env.HRMS_PAYROLL_EVIDENCE;
const password = process.env.HRMS_PAYROLL_SYNTHETIC_PASSWORD;
const adminUsername = process.env.HRMS_PAYROLL_ADMIN_USERNAME ?? 'payroll.admin';
const noReadUsername =
  process.env.HRMS_PAYROLL_NO_READ_USERNAME ?? 'payroll.no-stat-read';
const crossTenantUsername =
  process.env.HRMS_PAYROLL_CROSS_TENANT_USERNAME ?? 'payroll.cross-tenant';

for (const [name, value] of Object.entries({
  HRMS_PAYROLL_REPOSITORY: repository,
  HRMS_PAYROLL_EVIDENCE: evidenceDirectory,
  HRMS_PAYROLL_SYNTHETIC_PASSWORD: password
})) {
  if (!value) {
    throw new Error(`Missing required environment variable ${name}`);
  }
}

const requireFromFrontend = createRequire(
  path.join(repository, 'frontend', 'payroll-web', 'package.json')
);
const {chromium} = requireFromFrontend('@playwright/test');

const frontend = 'http://localhost:5173';
const tenantA = '00000000-0000-0000-0000-000000000001';
const tenantB = '00000000-0000-0000-0000-000000000002';
const checks = [];
const evidence = {};

function record(name, status, detail = '') {
  checks.push({name, status, detail});
  const suffix = detail ? ` — ${detail}` : '';
  console.log(`${status}: ${name}${suffix}`);
}

function assertCheck(condition, name, detail = '') {
  if (!condition) {
    record(name, 'FAIL', detail);
    throw new Error(`${name}${detail ? `: ${detail}` : ''}`);
  }

  record(name, 'PASS', detail);
}

async function login(page, username) {
  await page.goto(frontend, {waitUntil: 'domcontentloaded'});
  await page.getByRole('button', {name: 'Sign in with Keycloak'}).waitFor();
  await page.getByRole('button', {name: 'Sign in with Keycloak'}).click();
  await page.locator('#username').waitFor();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await page.waitForURL(url => url.origin === frontend, {timeout: 45_000});
  await page.getByRole('button', {name: 'Sign out'}).waitFor({timeout: 45_000});
  await page.getByText(username, {exact: true}).waitFor({timeout: 30_000});
}

async function api(page, apiPath) {
  return page.evaluate(async apiPathValue => {
    const token = window.payrollSession?.accessToken;
    if (!token) {
      throw new Error('No in-memory payroll access token is available.');
    }

    const response = await fetch(`/api/v1${apiPathValue}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Correlation-ID': crypto.randomUUID()
      }
    });

    const text = await response.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {}

    return {
      ok: response.ok,
      status: response.status,
      detail: json?.detail ?? json?.title ?? text,
      json
    };
  }, apiPath);
}

async function storageHasToken(page) {
  return page.evaluate(() => {
    const content = storage => {
      const rows = [];
      for (let index = 0; index < storage.length; index += 1) {
        const key = storage.key(index) ?? '';
        rows.push(`${key}=${storage.getItem(key) ?? ''}`);
      }
      return rows.join('\n');
    };

    const combined = `${content(localStorage)}\n${content(sessionStorage)}`;
    return /(access[_-]?token|refresh[_-]?token|eyJ[a-zA-Z0-9_-]{20,}\.)/i
      .test(combined);
  });
}

async function newSession(browser) {
  const context = await browser.newContext({
    viewport: {width: 1600, height: 1000},
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata'
  });
  return {context, page: await context.newPage()};
}

const browser = await chromium.launch({headless: true});

try {
  const admin = await newSession(browser);
  await login(admin.page, adminUsername);
  assertCheck(
    await admin.page.getByText(`Tenant ${tenantA}`, {exact: true}).count() === 1,
    'Administrator tenant-A claim displayed'
  );

  const adminCycles = await api(admin.page, '/payroll-cycles');
  assertCheck(
    adminCycles.status === 200 && Array.isArray(adminCycles.json),
    'Administrator payroll-cycle API readable',
    `HTTP ${adminCycles.status}`
  );

  const tenantACycle = adminCycles.json.find(item =>
    item.status === 'CALCULATED' &&
    (
      String(item.periodCode).toLowerCase().includes('jul') ||
      String(item.periodCode).includes('2026-07') ||
      String(item.periodStart).startsWith('2026-07')
    )
  );
  assertCheck(Boolean(tenantACycle), 'Tenant-A July 2026 cycle located');
  evidence.tenantACycleId = tenantACycle.id;
  evidence.tenantAPeriodCode = tenantACycle.periodCode;
  await admin.context.close();

  const noRead = await newSession(browser);
  await login(noRead.page, noReadUsername);
  assertCheck(
    await noRead.page.getByText(`Tenant ${tenantA}`, {exact: true}).count() === 1,
    'No-read user tenant-A claim displayed'
  );
  assertCheck(
    !(await storageHasToken(noRead.page)),
    'No-read session stores no access or refresh token'
  );
  assertCheck(
    await noRead.page.getByRole('link', {name: 'Statutory'}).count() === 0,
    'No-read user has no Statutory navigation link'
  );

  const noReadCycles = await api(noRead.page, '/payroll-cycles');
  assertCheck(
    noReadCycles.status === 200 &&
      Array.isArray(noReadCycles.json) &&
      noReadCycles.json.some(item => item.id === tenantACycle.id),
    'No-read user retains payroll-cycle read access',
    `HTTP ${noReadCycles.status}`
  );

  const noReadEvaluation = await api(
    noRead.page,
    `/payroll-cycles/${tenantACycle.id}/statutory/evaluations`
  );
  assertCheck(
    noReadEvaluation.status === 403,
    'No-read user is denied statutory evidence by backend',
    `HTTP ${noReadEvaluation.status}`
  );

  await noRead.page.goto(`${frontend}/statutory`, {
    waitUntil: 'domcontentloaded'
  });
  await noRead.page.getByRole('heading', {name: 'Statutory execution'}).waitFor();
  const noReadAlert = noRead.page.getByRole('alert');
  await noReadAlert.waitFor();
  assertCheck(
    /do not have a supported statutory evidence read permission/i.test(
      await noReadAlert.textContent() ?? ''
    ),
    'No-read direct route shows explicit permission boundary'
  );
  await noRead.page.screenshot({
    path: path.join(evidenceDirectory, '20-no-statutory-read.png'),
    fullPage: true
  });
  await noRead.context.close();

  const crossTenant = await newSession(browser);
  await login(crossTenant.page, crossTenantUsername);
  assertCheck(
    await crossTenant.page.getByText(`Tenant ${tenantB}`, {exact: true}).count() === 1,
    'Cross-tenant user tenant-B claim displayed'
  );
  assertCheck(
    !(await storageHasToken(crossTenant.page)),
    'Cross-tenant session stores no access or refresh token'
  );
  assertCheck(
    await crossTenant.page.getByRole('link', {name: 'Statutory'}).count() === 1,
    'Cross-tenant user retains statutory-read navigation permission'
  );

  const crossCycles = await api(crossTenant.page, '/payroll-cycles');
  const crossListIsEmpty =
    crossCycles.status === 200 &&
    Array.isArray(crossCycles.json) &&
    crossCycles.json.length === 0;
  const crossListIsDenied = [403, 404].includes(crossCycles.status);
  assertCheck(
    crossListIsEmpty || crossListIsDenied,
    'Tenant-B cycle list cannot expose tenant-A data',
    crossListIsEmpty
      ? 'HTTP 200; empty tenant-scoped list'
      : `HTTP ${crossCycles.status}; tenant access securely denied`
  );
  evidence.crossTenantListMode =
    crossListIsEmpty ? 'EMPTY_TENANT_SCOPE' : 'SECURE_DENIAL';

  const crossCycle = await api(
    crossTenant.page,
    `/payroll-cycles/${tenantACycle.id}`
  );
  assertCheck(
    [403, 404].includes(crossCycle.status),
    'Tenant-B user cannot read tenant-A cycle by identifier',
    `HTTP ${crossCycle.status}`
  );

  const crossEvaluation = await api(
    crossTenant.page,
    `/payroll-cycles/${tenantACycle.id}/statutory/evaluations`
  );
  assertCheck(
    [403, 404].includes(crossEvaluation.status),
    'Tenant-B user cannot read tenant-A statutory evidence by identifier',
    `HTTP ${crossEvaluation.status}`
  );

  await crossTenant.page.goto(`${frontend}/statutory`, {
    waitUntil: 'domcontentloaded'
  });
  await crossTenant.page.getByRole(
    'heading',
    {name: 'Statutory execution'}
  ).waitFor();
  await crossTenant.page.waitForFunction(() => {
    const body = document.body.innerText;
    return body.includes('No payroll cycles exist yet.') ||
      document.querySelector('[role="alert"]') !== null;
  });
  const crossTenantBody = await crossTenant.page.locator('body').innerText();
  assertCheck(
    crossTenantBody.includes('No payroll cycles exist yet.') ||
      /403|404|denied|permission|tenant|not exist|request failed/i
        .test(crossTenantBody),
    'Tenant-B UI resolves to an empty scope or secure denial'
  );
  assertCheck(
    await crossTenant.page.getByText(
      String(tenantACycle.periodCode),
      {exact: true}
    ).count() === 0,
    'Tenant-A payroll period is absent from tenant-B UI'
  );
  await crossTenant.page.screenshot({
    path: path.join(evidenceDirectory, '21-cross-tenant-empty.png'),
    fullPage: true
  });
  await crossTenant.context.close();

  const failures = checks.filter(item => item.status === 'FAIL');
  const report = {
    generatedAt: new Date().toISOString(),
    overall: failures.length === 0 ? 'PASS' : 'FAIL',
    users: {
      noRead: noReadUsername,
      crossTenant: crossTenantUsername
    },
    tenants: {tenantA, tenantB},
    evidence,
    checks
  };

  fs.writeFileSync(
    path.join(
      evidenceDirectory,
      'HRMS-Payroll-S4-Step03-Identity-Boundaries.json'
    ),
    JSON.stringify(report, null, 2),
    'utf8'
  );

  const rows = checks.map(item =>
    `| ${item.name.replaceAll('|', '\\|')} | ${item.status} | ${
      (item.detail ?? '').replaceAll('|', '\\|')
    } |`
  ).join('\n');

  const markdown = `# Sprint 4 Step 03 — Synthetic Identity Boundaries

**Generated:** ${report.generatedAt}<br>
**Overall:** **${report.overall}**

## Identities

| Purpose | Username | Tenant |
|---|---|---|
| Payroll-cycle read without statutory evidence read | \`${noReadUsername}\` | \`${tenantA}\` |
| Statutory-read identity in another tenant | \`${crossTenantUsername}\` | \`${tenantB}\` |

The users were created only for this local test run and are deleted by the
Step 03 runner after the browser test.

## Results

| Check | Result | Detail |
|---|---|---|
${rows}

## Screenshots

- \`20-no-statutory-read.png\`
- \`21-cross-tenant-empty.png\`

No passwords, cookies, access tokens or refresh tokens are persisted in this
report.
`;

  fs.writeFileSync(
    path.join(
      evidenceDirectory,
      'HRMS-Payroll-S4-Step03-Identity-Boundaries.md'
    ),
    markdown,
    'utf8'
  );

  console.log(`OVERALL: ${report.overall}`);
  if (failures.length > 0) {
    process.exitCode = 1;
  }
} catch (error) {
  record(
    'Identity-boundary automation execution',
    'FAIL',
    error instanceof Error ? error.message : String(error)
  );

  const failure = {
    generatedAt: new Date().toISOString(),
    overall: 'FAIL',
    evidence,
    checks,
    error: error instanceof Error ? error.stack : String(error)
  };

  fs.writeFileSync(
    path.join(
      evidenceDirectory,
      'HRMS-Payroll-S4-Step03-Identity-Boundaries-FAILURE.json'
    ),
    JSON.stringify(failure, null, 2),
    'utf8'
  );

  console.error(error);
  process.exitCode = 1;
} finally {
  await browser.close().catch(() => {});
}
