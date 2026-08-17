#!/usr/bin/env node
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";
import { TextDecoder } from "node:util";

const UTF8 = new TextDecoder("utf-8", { fatal: true });

function nowIso() { return new Date().toISOString(); }
function normSha(value, label) {
  if (typeof value !== "string" || !/^[0-9a-f]{40}$/i.test(value)) {
    throw new Error(`${label} must be a 40-character Git SHA`);
  }
  return value.toLowerCase();
}
function sha256(buffer) { return crypto.createHash("sha256").update(buffer).digest("hex"); }
function safeRel(rel, label) {
  if (typeof rel !== "string" || !rel || path.isAbsolute(rel) || rel.includes("\0")) {
    throw new Error(`${label} must be a non-empty relative path`);
  }
  const normalized = rel.replaceAll("\\", "/");
  if (normalized === ".." || normalized.startsWith("../") || normalized.includes("/../")) {
    throw new Error(`${label} must not escape its root`);
  }
  return normalized;
}
function assertKnownKeys(obj, allowed, label) {
  const unknown = Object.keys(obj ?? {}).filter(k => !allowed.includes(k));
  if (unknown.length) throw new Error(`${label} contains unsupported field(s): ${unknown.join(", ")}`);
}
function assertUnique(values, label) {
  const seen = new Set();
  const dupes = [];
  for (const value of values) {
    if (seen.has(value)) dupes.push(value);
    seen.add(value);
  }
  if (dupes.length) throw new Error(`${label} contains duplicates: ${[...new Set(dupes)].join(", ")}`);
}
function sorted(values) { return [...values].sort((a,b) => a.localeCompare(b)); }
function sameSet(a,b) {
  const aa = sorted(a), bb = sorted(b);
  return aa.length === bb.length && aa.every((v,i) => v === bb[i]);
}
function countOccurrences(text, needle) {
  if (needle === "") return 0;
  let count = 0, pos = 0;
  while ((pos = text.indexOf(needle, pos)) !== -1) { count++; pos += needle.length; }
  return count;
}
function parseArgs(argv) {
  const out = { selfTest: false, preflightOnly: false };
  for (let i=0; i<argv.length; i++) {
    const arg = argv[i];
    if (arg === "--repo-root") out.repoRoot = argv[++i];
    else if (arg === "--manifest") out.manifestPath = argv[++i];
    else if (arg === "--preflight-only") out.preflightOnly = true;
    else if (arg === "--self-test") out.selfTest = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return out;
}
function spawnOwned(command, args, { cwd, env, input, allowFailure=false, encoding="utf8" } = {}) {
  const result = spawnSync(command, args, {
    cwd,
    env: env ? { ...process.env, ...env } : process.env,
    input,
    encoding: encoding === null ? undefined : encoding,
    windowsHide: true,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  const stdout = result.stdout ?? (encoding === null ? Buffer.alloc(0) : "");
  const stderr = result.stderr ?? (encoding === null ? Buffer.alloc(0) : "");
  if (!allowFailure && result.status !== 0) {
    const errText = encoding === null ? Buffer.from(stderr).toString("utf8") : stderr;
    throw new Error(`${command} ${args.join(" ")} failed with exit code ${result.status}: ${errText.trim()}`);
  }
  return { code: result.status ?? -1, stdout, stderr };
}
function makeLogger() {
  const lines = [];
  const emit = (level, message) => {
    const line = `[${nowIso()}] [${level}] ${message}`;
    lines.push(line);
    console.log(line);
  };
  return {
    lines,
    info: m => emit("INFO", m),
    pass: m => emit("PASS", m),
    warn: m => emit("WARN", m),
    error: m => emit("ERROR", m),
  };
}
function run(log, command, args, opts={}) {
  log.info(`RUN: ${command} ${args.map(v => /\s/.test(v) ? JSON.stringify(v) : v).join(" ")}`);
  const r = spawnOwned(command, args, opts);
  const outText = Buffer.isBuffer(r.stdout) ? r.stdout.toString("utf8") : r.stdout;
  const errText = Buffer.isBuffer(r.stderr) ? r.stderr.toString("utf8") : r.stderr;
  if (outText.trim()) for (const line of outText.replace(/\r\n/g,"\n").trimEnd().split("\n")) log.info(`STDOUT: ${line}`);
  if (errText.trim()) for (const line of errText.replace(/\r\n/g,"\n").trimEnd().split("\n")) log.info(`STDERR: ${line}`);
  return r;
}
function gitText(log, repoRoot, args, opts={}) {
  return run(log, "git", args, { cwd: repoRoot, ...opts }).stdout.trim();
}
function gitBuffer(repoRoot, args, opts={}) {
  return spawnOwned("git", args, { cwd: repoRoot, encoding: null, ...opts }).stdout;
}
function ghJson(log, repoRoot, args) {
  const text = run(log, "gh", args, { cwd: repoRoot }).stdout;
  try { return JSON.parse(text); }
  catch (e) { throw new Error(`Failed to parse gh JSON for: gh ${args.join(" ")}: ${e.message}`); }
}
function validateManifest(raw) {
  const errors = [];
  const need = (condition, msg) => { if (!condition) errors.push(msg); };
  need(raw && typeof raw === "object" && !Array.isArray(raw), "manifest root must be an object");
  if (!raw || typeof raw !== "object") throw new Error(errors.join(" | "));
  assertKnownKeys(raw, ["schemaVersion","capabilityId","title","repository","productAuthorities","storyLedger","filePayloads","finalAssertions","migrationBoundary","publication"], "manifest");
  need(raw.schemaVersion === 1, "schemaVersion must be 1");
  need(typeof raw.capabilityId === "string" && raw.capabilityId.trim(), "capabilityId is required");
  need(typeof raw.title === "string" && raw.title.trim(), "title is required");
  need(raw.repository && typeof raw.repository === "object", "repository is required");
  need(raw.publication && typeof raw.publication === "object", "publication is required");
  need(Array.isArray(raw.productAuthorities), "productAuthorities must be an array");
  need(Array.isArray(raw.filePayloads), "filePayloads must be an array");
  need(Array.isArray(raw.finalAssertions), "finalAssertions must be an array");
  if (raw.repository) {
    assertKnownKeys(raw.repository, ["fullName","baseBranch","expectedBaseSha","closureBranch"], "repository");
    need(typeof raw.repository.fullName === "string" && /^[^/]+\/[^/]+$/.test(raw.repository.fullName), "repository.fullName must be owner/repo");
    need(typeof raw.repository.expectedBaseSha === "string" && /^[0-9a-f]{40}$/i.test(raw.repository.expectedBaseSha), "repository.expectedBaseSha must be a Git SHA");
    need(typeof raw.repository.closureBranch === "string" && raw.repository.closureBranch.trim(), "repository.closureBranch is required");
    need((raw.repository.baseBranch ?? "main") === "main", "repository.baseBranch must be main");
  }
  if (raw.publication) {
    assertKnownKeys(raw.publication, ["commitMessage","prTitle","prBody","expectedChecks","mergeMethod","deleteBranch","allowedPaths","passMarker","pollSeconds","maxWaitSeconds","evidenceRoot","evidencePrefix"], "publication");
    need(typeof raw.publication.commitMessage === "string" && raw.publication.commitMessage.trim(), "publication.commitMessage is required");
    need(typeof raw.publication.prTitle === "string" && raw.publication.prTitle.trim(), "publication.prTitle is required");
    need(typeof raw.publication.prBody === "string", "publication.prBody is required");
    need(Array.isArray(raw.publication.expectedChecks) && raw.publication.expectedChecks.length > 0, "publication.expectedChecks must be non-empty");
    need(raw.publication.mergeMethod === "merge", "publication.mergeMethod must be merge");
    need(raw.publication.deleteBranch === false, "publication.deleteBranch must be false");
    need(typeof raw.publication.passMarker === "string" && raw.publication.passMarker.trim(), "publication.passMarker is required");
  }
  if (raw.storyLedger !== undefined) {
    need(raw.storyLedger && typeof raw.storyLedger === "object", "storyLedger must be an object when present");
    if (raw.storyLedger) {
      assertKnownKeys(raw.storyLedger, ["path","sourceBlob","expectedRows","idColumn","rows"], "storyLedger");
      need(typeof raw.storyLedger.path === "string" && raw.storyLedger.path.trim(), "storyLedger.path is required");
      need(Number.isInteger(raw.storyLedger.expectedRows) && raw.storyLedger.expectedRows > 0, "storyLedger.expectedRows must be positive integer");
      need(typeof raw.storyLedger.idColumn === "string" && raw.storyLedger.idColumn, "storyLedger.idColumn is required");
      need(Array.isArray(raw.storyLedger.rows), "storyLedger.rows must be an array");
    }
  }
  if (errors.length) throw new Error(`Manifest contract errors: ${errors.join(" | ")}`);
  const m = structuredClone(raw);
  m.repository.expectedBaseSha = normSha(m.repository.expectedBaseSha, "repository.expectedBaseSha");
  m.repository.baseBranch = "main";
  m.repository.closureBranch = safeRel(m.repository.closureBranch, "repository.closureBranch");
  if (!m.repository.closureBranch.startsWith("docs/")) throw new Error("repository.closureBranch must use docs/ prefix for governance closure");
  m.filePayloads = m.filePayloads.map((p, i) => ({
    ...p,
    path: safeRel(p.path, `filePayloads[${i}].path`),
    payload: safeRel(p.payload, `filePayloads[${i}].payload`),
    sourceBlob: p.sourceBlob === null ? null : normSha(p.sourceBlob, `filePayloads[${i}].sourceBlob`),
    payloadSha256: String(p.payloadSha256 ?? "").toLowerCase(),
  }));
  for (const [i,p] of m.filePayloads.entries()) {
    assertKnownKeys(p, ["path","sourceBlob","payload","payloadSha256"], `filePayloads[${i}]`);
    if (!/^[0-9a-f]{64}$/.test(p.payloadSha256)) throw new Error(`filePayloads[${i}].payloadSha256 must be SHA-256`);
  }
  if (m.storyLedger) {
    m.storyLedger.path = safeRel(m.storyLedger.path, "storyLedger.path");
    if (m.storyLedger.sourceBlob) m.storyLedger.sourceBlob = normSha(m.storyLedger.sourceBlob, "storyLedger.sourceBlob");
    for (const [i,row] of (m.storyLedger.rows ?? []).entries()) {
      if (!row || typeof row !== "object") throw new Error(`storyLedger.rows[${i}] must be an object`);
      assertKnownKeys(row, ["id","expect","set"], `storyLedger.rows[${i}]`);
    }
  }
  if (m.migrationBoundary) assertKnownKeys(m.migrationBoundary, ["immutableThrough","nextUnreserved"], "migrationBoundary");
  m.publication.expectedChecks = m.publication.expectedChecks.map(String);
  assertUnique(m.publication.expectedChecks, "publication.expectedChecks");
  for (const [i,a] of m.finalAssertions.entries()) {
    if (!a || typeof a !== "object") throw new Error(`finalAssertions[${i}] must be an object`);
    assertKnownKeys(a, ["path","contains","notContains"], `finalAssertions[${i}]`);
    for (const [j,c] of (a.contains ?? []).entries()) {
      if (!c || typeof c !== "object") throw new Error(`finalAssertions[${i}].contains[${j}] must be an object`);
      assertKnownKeys(c, ["text","count"], `finalAssertions[${i}].contains[${j}]`);
    }
  }
  const changed = [...m.filePayloads.map(p => p.path)];
  if (m.storyLedger) changed.push(m.storyLedger.path);
  assertUnique(changed, "changed paths");
  if (!Array.isArray(m.publication.allowedPaths)) throw new Error("publication.allowedPaths must be an array");
  m.publication.allowedPaths = m.publication.allowedPaths.map((p,i)=>safeRel(p,`publication.allowedPaths[${i}]`));
  assertUnique(m.publication.allowedPaths, "publication.allowedPaths");
  if (!sameSet(changed, m.publication.allowedPaths)) {
    throw new Error(`publication.allowedPaths does not equal generated target set. generated=${sorted(changed).join(", ")} allowed=${sorted(m.publication.allowedPaths).join(", ")}`);
  }
  for (const [i,a] of m.productAuthorities.entries()) {
    if (!a || typeof a !== "object") throw new Error(`productAuthorities[${i}] must be an object`);
    assertKnownKeys(a, ["repo","pr","headSha","mergeSha"], `productAuthorities[${i}]`);
    if (typeof a.repo !== "string" || !/^[^/]+\/[^/]+$/.test(a.repo)) throw new Error(`productAuthorities[${i}].repo must be owner/repo`);
    if (!Number.isInteger(a.pr) || a.pr <= 0) throw new Error(`productAuthorities[${i}].pr must be positive integer`);
    a.headSha = normSha(a.headSha, `productAuthorities[${i}].headSha`);
    a.mergeSha = normSha(a.mergeSha, `productAuthorities[${i}].mergeSha`);
  }
  return m;
}

// CSV parser that preserves each raw record. Newlines inside quoted fields are supported.
function splitCsvRecords(text) {
  const records = [];
  let start = 0, inQuotes = false;
  for (let i=0; i<text.length; i++) {
    const c = text[i];
    if (c === '"') {
      if (inQuotes && text[i+1] === '"') { i++; continue; }
      inQuotes = !inQuotes;
    } else if (!inQuotes && (c === "\n" || c === "\r")) {
      let end = i;
      let newline = c;
      if (c === "\r" && text[i+1] === "\n") { newline = "\r\n"; i++; }
      records.push({ raw: text.slice(start, end), newline });
      start = i + 1;
    }
  }
  if (inQuotes) throw new Error("CSV has an unterminated quoted field");
  if (start < text.length) records.push({ raw: text.slice(start), newline: "" });
  return records.filter((r,idx)=> !(idx === records.length-1 && r.raw === "" && r.newline === ""));
}
function parseCsvRecord(raw) {
  const values = [];
  let value = "", inQuotes = false, fieldStarted = false;
  for (let i=0; i<=raw.length; i++) {
    const c = i < raw.length ? raw[i] : ",";
    if (inQuotes) {
      if (c === '"') {
        if (raw[i+1] === '"') { value += '"'; i++; }
        else inQuotes = false;
      } else value += c;
    } else {
      if (c === "," || i === raw.length) {
        values.push(value); value=""; fieldStarted=false;
      } else if (c === '"' && !fieldStarted) {
        inQuotes = true; fieldStarted = true;
      } else {
        value += c; fieldStarted = true;
      }
    }
  }
  if (inQuotes) throw new Error("CSV record has unterminated quoted field");
  return values;
}
function csvField(value) {
  const s = value == null ? "" : String(value);
  if (/[",\r\n]/.test(s) || /^\s|\s$/.test(s)) return `"${s.replaceAll('"','""')}"`;
  return s;
}
function serializeCsv(values) { return values.map(csvField).join(","); }

function applyStoryLedger(baseText, spec) {
  const records = splitCsvRecords(baseText);
  if (records.length < 2) throw new Error("Story ledger has no data rows");
  const header = parseCsvRecord(records[0].raw);
  const col = new Map(header.map((v,i)=>[v,i]));
  if (!col.has(spec.idColumn)) throw new Error(`Story ledger missing id column: ${spec.idColumn}`);
  const idIndex = col.get(spec.idColumn);
  const rows = records.slice(1).map((record, idx) => {
    const values = parseCsvRecord(record.raw);
    if (values.length !== header.length) throw new Error(`Story ledger record ${idx+2} has ${values.length} fields; expected ${header.length}`);
    return { ...record, values, id: values[idIndex] };
  });
  if (rows.length !== spec.expectedRows) throw new Error(`Story ledger row count: expected ${spec.expectedRows}; got ${rows.length}`);
  assertUnique(rows.map(r=>r.id), "Story ledger IDs");
  const byId = new Map(rows.map(r=>[r.id,r]));
  const targets = spec.rows ?? [];
  assertUnique(targets.map(r=>r.id), "storyLedger.rows IDs");
  const changedIds = new Set();
  const delta = [];
  for (const target of targets) {
    const row = byId.get(target.id);
    if (!row) throw new Error(`Story ledger target missing: ${target.id}`);
    for (const [name, expected] of Object.entries(target.expect ?? {})) {
      if (!col.has(name)) throw new Error(`Story ledger expected column missing: ${name}`);
      const actual = row.values[col.get(name)];
      if (actual !== String(expected)) throw new Error(`Story ${target.id} pre-value mismatch for ${name}: expected ${JSON.stringify(String(expected))}; got ${JSON.stringify(actual)}`);
    }
    const before = [...row.values];
    for (const [name, value] of Object.entries(target.set ?? {})) {
      if (!col.has(name)) throw new Error(`Story ledger set column missing: ${name}`);
      row.values[col.get(name)] = String(value);
    }
    const changedColumns = header.filter((name,i)=>before[i] !== row.values[i]);
    const declared = Object.keys(target.set ?? {});
    if (!sameSet(changedColumns, declared.filter(name => before[col.get(name)] !== String(target.set[name])))) {
      throw new Error(`Story ${target.id} changed unexpected fields`);
    }
    row.raw = serializeCsv(row.values);
    changedIds.add(target.id);
    delta.push({ id: target.id, changedColumns, before: Object.fromEntries(changedColumns.map(n=>[n,before[col.get(n)]])), after: Object.fromEntries(changedColumns.map(n=>[n,row.values[col.get(n)]])) });
  }
  // Unlisted rows remain raw-identical by construction.
  const output = records[0].raw + records[0].newline + rows.map(r=>r.raw+r.newline).join("");
  const verifyRecords = splitCsvRecords(output);
  if (verifyRecords.length !== records.length) throw new Error("Story ledger record cardinality changed unexpectedly");
  return { text: output, delta, header, changedIds };
}
function lsTreeEntry(log, repoRoot, commit, relPath) {
  const out = gitText(log, repoRoot, ["ls-tree", commit, "--", relPath], { allowFailure: false });
  if (!out) return null;
  const tab = out.indexOf("\t");
  if (tab < 0) throw new Error(`Unexpected ls-tree output for ${relPath}`);
  const left = out.slice(0,tab).trim().split(/\s+/);
  return { mode:left[0], type:left[1], sha:left[2], path:out.slice(tab+1) };
}
function decodeUtf8(buffer, label) {
  try { return UTF8.decode(buffer); }
  catch { throw new Error(`${label} is not valid UTF-8`); }
}
function loadTargetContent(log, repoRoot, base, manifest, manifestDir) {
  const errors = [];
  const targets = new Map();
  const sourceProof = [];
  const addError = e => errors.push(e instanceof Error ? e.message : String(e));
  let ledgerDelta = [];
  if (manifest.storyLedger) {
    try {
      const entry = lsTreeEntry(log, repoRoot, base, manifest.storyLedger.path);
      if (!entry || entry.type !== "blob") throw new Error(`Story ledger missing at base: ${manifest.storyLedger.path}`);
      if (manifest.storyLedger.sourceBlob && entry.sha.toLowerCase() !== manifest.storyLedger.sourceBlob) {
        throw new Error(`Story ledger source blob mismatch: expected ${manifest.storyLedger.sourceBlob}; got ${entry.sha}`);
      }
      const baseBuf = gitBuffer(repoRoot, ["show", `${base}:${manifest.storyLedger.path}`]);
      const baseText = decodeUtf8(baseBuf, manifest.storyLedger.path);
      const applied = applyStoryLedger(baseText, manifest.storyLedger);
      const targetBuf = Buffer.from(applied.text, "utf8");
      targets.set(manifest.storyLedger.path, { buffer: targetBuf, mode: entry.mode, sourceBlob: entry.sha, type:"ledger" });
      ledgerDelta = applied.delta;
      sourceProof.push({ path:manifest.storyLedger.path, sourceBlob:entry.sha, targetSha256:sha256(targetBuf), kind:"story-ledger" });
    } catch (e) { addError(e); }
  }
  for (const p of manifest.filePayloads) {
    try {
      const entry = lsTreeEntry(log, repoRoot, base, p.path);
      if (p.sourceBlob === null) {
        if (entry) throw new Error(`New payload path already exists at base: ${p.path} (${entry.sha})`);
      } else {
        if (!entry || entry.type !== "blob") throw new Error(`Payload source path missing at base: ${p.path}`);
        if (entry.sha.toLowerCase() !== p.sourceBlob) throw new Error(`Source blob mismatch for ${p.path}: expected ${p.sourceBlob}; got ${entry.sha}`);
      }
      const payloadAbs = path.resolve(manifestDir, p.payload);
      const relCheck = path.relative(manifestDir, payloadAbs);
      if (relCheck.startsWith("..") || path.isAbsolute(relCheck)) throw new Error(`Payload escapes manifest directory: ${p.payload}`);
      const buffer = fs.readFileSync(payloadAbs);
      decodeUtf8(buffer, p.payload);
      const actualHash = sha256(buffer);
      if (actualHash !== p.payloadSha256) throw new Error(`Payload SHA-256 mismatch for ${p.path}: expected ${p.payloadSha256}; got ${actualHash}`);
      targets.set(p.path, { buffer, mode:entry?.mode ?? "100644", sourceBlob:entry?.sha ?? null, type:"payload" });
      sourceProof.push({ path:p.path, sourceBlob:entry?.sha ?? null, targetSha256:actualHash, kind:"payload" });
    } catch (e) { addError(e); }
  }
  for (const assertion of manifest.finalAssertions) {
    try {
      const rel = safeRel(assertion.path, "finalAssertions.path");
      const target = targets.get(rel);
      if (!target) throw new Error(`Final assertion path is not a changed target: ${rel}`);
      const text = decodeUtf8(target.buffer, rel);
      for (const item of assertion.contains ?? []) {
        const expectedCount = item.count ?? 1;
        const actualCount = countOccurrences(text, String(item.text));
        if (actualCount !== expectedCount) throw new Error(`${rel}: expected ${expectedCount} occurrence(s) of ${JSON.stringify(item.text)}; got ${actualCount}`);
      }
      for (const item of assertion.notContains ?? []) {
        if (text.includes(String(item))) throw new Error(`${rel}: forbidden text remains: ${JSON.stringify(item)}`);
      }
    } catch (e) { addError(e); }
  }
  if (errors.length) throw new Error(`Aggregate closure content preflight failed (${errors.length}):\n- ${errors.join("\n- ")}`);
  const targetPaths = [...targets.keys()];
  if (!sameSet(targetPaths, manifest.publication.allowedPaths)) throw new Error("Generated target paths no longer match allowedPaths");
  return { targets, ledgerDelta, sourceProof };
}
function validateRepoOperationState(repoRoot) {
  const gitDir = spawnOwned("git", ["rev-parse","--git-dir"], { cwd:repoRoot }).stdout.trim();
  const absGit = path.resolve(repoRoot, gitDir);
  const conflictMarkers = ["MERGE_HEAD","CHERRY_PICK_HEAD","REVERT_HEAD"];
  for (const marker of conflictMarkers) if (fs.existsSync(path.join(absGit,marker))) throw new Error(`Repository operation in progress: ${marker}`);
  for (const dir of ["rebase-merge","rebase-apply"]) if (fs.existsSync(path.join(absGit,dir))) throw new Error(`Repository operation in progress: ${dir}`);
}
function captureUserState(repoRoot) {
  const branch = spawnOwned("git", ["branch","--show-current"], { cwd:repoRoot }).stdout.trim();
  const head = spawnOwned("git", ["rev-parse","HEAD"], { cwd:repoRoot }).stdout.trim();
  const status = spawnOwned("git", ["status","--porcelain=v1","-z","--untracked-files=all"], { cwd:repoRoot, encoding:null }).stdout;
  return { branch, head, statusHash:sha256(status), statusBytes:status };
}
function assertUserStatePreserved(before, after) {
  if (before.branch !== after.branch || before.head !== after.head || !before.statusBytes.equals(after.statusBytes)) {
    throw new Error(`User checkout/status changed. before=${before.branch || "(detached)"}@${before.head}/${before.statusHash} after=${after.branch || "(detached)"}@${after.head}/${after.statusHash}`);
  }
}
function verifyOrigin(repoRoot, fullName) {
  const origin = spawnOwned("git", ["remote","get-url","origin"], { cwd:repoRoot }).stdout.trim();
  const escaped = fullName.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");
  if (!new RegExp(`github\\.com[/:]${escaped}(?:\\.git)?$`,"i").test(origin)) throw new Error(`origin does not match ${fullName}: ${origin}`);
  return origin;
}
function remoteRef(repoRoot, ref) {
  const r = spawnOwned("git", ["ls-remote","origin",ref], { cwd:repoRoot });
  const line = r.stdout.trim();
  if (!line) return null;
  const [sha, gotRef] = line.split(/\s+/);
  if (gotRef !== ref) throw new Error(`Unexpected ls-remote ref: ${line}`);
  return normSha(sha, `remote ${ref}`);
}
function fetchRef(log, repoRoot, ref, expectedSha) {
  run(log, "git", ["fetch","--no-tags","origin",ref], { cwd:repoRoot });
  const got = normSha(gitText(log, repoRoot, ["rev-parse","FETCH_HEAD"]), "FETCH_HEAD");
  if (expectedSha && got !== expectedSha) throw new Error(`Fetched ${ref} mismatch: expected ${expectedSha}; got ${got}`);
  gitText(log, repoRoot, ["cat-file","-e",`${got}^{commit}`]);
  return got;
}
function verifyProductAuthorities(log, repoRoot, authorities) {
  for (const a of authorities) {
    const pr = ghJson(log, repoRoot, ["api",`repos/${a.repo}/pulls/${a.pr}`]);
    const merged = Boolean(pr.merged_at || pr.merged === true);
    if (!merged) throw new Error(`Product PR not merged: ${a.repo}#${a.pr}`);
    const head = normSha(pr.head?.sha, `${a.repo}#${a.pr} head`);
    const merge = normSha(pr.merge_commit_sha, `${a.repo}#${a.pr} merge`);
    if (head !== a.headSha || merge !== a.mergeSha) throw new Error(`Product authority mismatch for ${a.repo}#${a.pr}: head=${head}, merge=${merge}`);
    log.pass(`Product authority verified: ${a.repo}#${a.pr} head=${head} merge=${merge}`);
  }
}
function listClosurePrs(log, repoRoot, repoFullName, branch) {
  const rows = ghJson(log, repoRoot, ["pr","list","--repo",repoFullName,"--head",branch,"--state","all","--limit","20","--json","number,url,state"]);
  return rows;
}
function fetchPr(log, repoRoot, repoFullName, number) {
  return ghJson(log, repoRoot, ["api",`repos/${repoFullName}/pulls/${number}`]);
}
function buildCommit(log, repoRoot, base, manifest, prepared) {
  const indexFile = path.join(os.tmpdir(), `hrms-payroll-closure-${process.pid}-${Date.now()}.index`);
  try {
    const env = { GIT_INDEX_FILE:indexFile };
    run(log, "git", ["read-tree",base], { cwd:repoRoot, env });
    for (const rel of sorted(prepared.targets.keys())) {
      const target = prepared.targets.get(rel);
      const blob = run(log, "git", ["hash-object","-w","--stdin"], { cwd:repoRoot, input:target.buffer, env }).stdout.trim();
      run(log, "git", ["update-index","--add","--cacheinfo",`${target.mode},${blob},${rel}`], { cwd:repoRoot, env });
    }
    const tree = run(log, "git", ["write-tree"], { cwd:repoRoot, env }).stdout.trim();
    const commit = run(log, "git", ["commit-tree",tree,"-p",base,"-m",manifest.publication.commitMessage], { cwd:repoRoot, env }).stdout.trim();
    return normSha(commit, "closure commit");
  } finally {
    try { fs.rmSync(indexFile,{force:true}); } catch {}
  }
}
function readCommitTarget(repoRoot, commit, rel) {
  return gitBuffer(repoRoot, ["show",`${commit}:${rel}`]);
}
function validateCommitContract(log, repoRoot, base, head, manifest, prepared) {
  gitText(log, repoRoot, ["cat-file","-e",`${head}^{commit}`]);
  const parentLine = gitText(log, repoRoot, ["rev-list","--parents","-n","1",head]).split(/\s+/);
  if (parentLine.length !== 2 || parentLine[1].toLowerCase() !== base) throw new Error(`Closure head parent mismatch: ${parentLine.join(" ")}`);
  const subject = gitText(log, repoRoot, ["log","-1","--pretty=%s",head]);
  if (subject !== manifest.publication.commitMessage) throw new Error(`Closure commit subject mismatch: ${subject}`);
  const pathsText = gitText(log, repoRoot, ["diff-tree","--no-commit-id","--name-only","-r",head]);
  const paths = pathsText ? pathsText.split(/\r?\n/).filter(Boolean) : [];
  if (!sameSet(paths, manifest.publication.allowedPaths)) throw new Error(`Closure changed-path mismatch: got=${sorted(paths).join(", ")} expected=${sorted(manifest.publication.allowedPaths).join(", ")}`);
  const diffCheck = spawnOwned("git", ["diff","--check",base,head], { cwd:repoRoot, allowFailure:true });
  if (diffCheck.code !== 0 || diffCheck.stdout.trim() || diffCheck.stderr.trim()) throw new Error(`git diff --check failed: ${(diffCheck.stdout+diffCheck.stderr).trim()}`);
  for (const [rel,target] of prepared.targets) {
    const actual = readCommitTarget(repoRoot, head, rel);
    if (!actual.equals(target.buffer)) throw new Error(`Closure commit content mismatch for ${rel}`);
  }
  log.pass(`Closure commit contract PASS: ${head}`);
}
function validateRemoteClosureHead(log, repoRoot, branchSha, base, manifest, prepared) {
  fetchRef(log, repoRoot, `refs/heads/${manifest.repository.closureBranch}`, branchSha);
  validateCommitContract(log, repoRoot, base, branchSha, manifest, prepared);
  return branchSha;
}
function latestCheckByName(checkRuns) {
  const map = new Map();
  for (const run of checkRuns) {
    const current = map.get(run.name);
    if (!current || Number(run.id) > Number(current.id)) map.set(run.name, run);
  }
  return map;
}
function sleepMs(ms) {
  const end = Date.now()+ms;
  while (Date.now()<end) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,Math.min(1000,end-Date.now()));
}
function waitChecks(log, repoRoot, manifest, head, base) {
  const expected = manifest.publication.expectedChecks;
  const pollSeconds = Number(manifest.publication.pollSeconds ?? 10);
  const maxWaitSeconds = Number(manifest.publication.maxWaitSeconds ?? 3600);
  const deadline = Date.now() + maxWaitSeconds*1000;
  while (true) {
    const liveMain = remoteRef(repoRoot, "refs/heads/main");
    if (liveMain !== base) throw new Error(`Live main drifted while closure PR was unmerged: expected ${base}; got ${liveMain}`);
    const branchSha = remoteRef(repoRoot, `refs/heads/${manifest.repository.closureBranch}`);
    if (branchSha !== head) throw new Error(`Closure branch head drifted while waiting for CI: expected ${head}; got ${branchSha}`);
    const response = ghJson(log, repoRoot, ["api",`repos/${manifest.repository.fullName}/commits/${head}/check-runs?per_page=100`]);
    const map = latestCheckByName(response.check_runs ?? []);
    const pending = [];
    for (const name of expected) {
      const check = map.get(name);
      if (!check) { pending.push(`${name}: unregistered`); continue; }
      if (check.status === "completed") {
        if (check.conclusion !== "success") throw new Error(`Required hosted check failed: ${name}: ${check.conclusion}`);
      } else pending.push(`${name}: ${check.status}`);
    }
    if (!pending.length) { log.pass(`Hosted closure CI PASS: ${expected.length}/${expected.length}`); return; }
    log.info(`Hosted checks ${expected.length-pending.length}/${expected.length}; pending=${pending.join(" | ")}`);
    if (Date.now() >= deadline) throw new Error(`Timed out waiting for hosted checks: ${pending.join(" | ")}`);
    sleepMs(pollSeconds*1000);
  }
}
function makeEvidenceMarkdown(state) {
  const rows = [];
  rows.push(`# ${state.manifest.capabilityId} capability closure evidence`);
  rows.push("");
  rows.push(`- Result: **${state.result}**`);
  rows.push(`- Base: \`${state.base ?? "n/a"}\``);
  rows.push(`- Closure state: \`${state.closureState ?? "n/a"}\``);
  rows.push(`- Closure head: \`${state.head ?? "n/a"}\``);
  rows.push(`- Closure PR: ${state.prNumber ? `#${state.prNumber}` : "n/a"}`);
  rows.push(`- Merge commit: \`${state.mergeSha ?? "n/a"}\``);
  rows.push(`- Current live main: \`${state.currentMain ?? "n/a"}\``);
  rows.push(`- Pre-run checkout: \`${state.before?.branch || "(detached)"}@${state.before?.head ?? "n/a"}\``);
  rows.push(`- Pre-run status SHA-256: \`${state.before?.statusHash ?? "n/a"}\``);
  rows.push(`- Post-run checkout: \`${state.after?.branch || "(detached)"}@${state.after?.head ?? "n/a"}\``);
  rows.push(`- Post-run status SHA-256: \`${state.after?.statusHash ?? "n/a"}\``);
  rows.push("");
  rows.push("## Changed paths");
  rows.push("");
  for (const p of state.manifest.publication.allowedPaths) rows.push(`- \`${p}\``);
  if (state.prepared?.ledgerDelta?.length) {
    rows.push("");
    rows.push("## Story-ledger delta");
    rows.push("");
    for (const d of state.prepared.ledgerDelta) {
      const changes = d.changedColumns.map(c=>`${c}: ${JSON.stringify(d.before[c])} -> ${JSON.stringify(d.after[c])}`).join("; ");
      rows.push(`- \`${d.id}\`: ${changes}`);
    }
  }
  rows.push("");
  rows.push("## Product authorities");
  rows.push("");
  for (const a of state.manifest.productAuthorities) rows.push(`- \`${a.repo}#${a.pr}\`: head \`${a.headSha}\`; merge \`${a.mergeSha}\``);
  rows.push("");
  rows.push("## Hosted checks");
  rows.push("");
  for (const c of state.manifest.publication.expectedChecks) rows.push(`- ${c}`);
  rows.push("");
  if (state.error) { rows.push("## Failure"); rows.push(""); rows.push("```text"); rows.push(state.error); rows.push("```"); rows.push(""); }
  rows.push(state.result === "PASS" ? state.manifest.publication.passMarker + ": PASS" : state.manifest.publication.passMarker + ": FAIL");
  rows.push("");
  return rows.join("\n");
}
function evidencePaths(repoRoot, manifest, manifestDir) {
  const evidenceRoot = manifest.publication.evidenceRoot
    ? path.resolve(manifest.publication.evidenceRoot)
    : path.resolve(repoRoot, "..", "hrms-payroll-artifacts");
  fs.mkdirSync(evidenceRoot,{recursive:true});
  const stamp = nowIso().replace(/[:.]/g,"-");
  const prefix = String(manifest.publication.evidencePrefix ?? `${manifest.capabilityId}-Post-Merge-Closure`).replace(/[^A-Za-z0-9._-]+/g,"-");
  return {
    log:path.join(evidenceRoot,`${prefix}-${stamp}.log`),
    md:path.join(evidenceRoot,`${prefix}-${stamp}.md`),
  };
}
function writeEvidence(paths, log, state) {
  fs.writeFileSync(paths.log, log.lines.join(os.EOL)+os.EOL, "utf8");
  fs.writeFileSync(paths.md, makeEvidenceMarkdown(state), "utf8");
}
function verifyMergedState(log, repoRoot, manifest, base, prepared, pr) {
  const head = normSha(pr.head?.sha, "merged PR head");
  validateCommitContract(log, repoRoot, base, head, manifest, prepared);
  if (!pr.merged_at) throw new Error(`Closure PR #${pr.number} is not merged`);
  const mergeSha = normSha(pr.merge_commit_sha, "closure merge SHA");
  const currentMain = remoteRef(repoRoot,"refs/heads/main");
  fetchRef(log, repoRoot, "refs/heads/main", currentMain);
  gitText(log, repoRoot, ["cat-file","-e",`${mergeSha}^{commit}`]);
  const ancestry = spawnOwned("git",["merge-base","--is-ancestor",mergeSha,currentMain],{cwd:repoRoot,allowFailure:true});
  if (ancestry.code !== 0) throw new Error(`Closure merge ${mergeSha} is not an ancestor of current main ${currentMain}`);
  const parents = gitText(log, repoRoot, ["rev-list","--parents","-n","1",mergeSha]).split(/\s+/).slice(1).map(s=>s.toLowerCase());
  if (!parents.includes(head)) throw new Error(`Closure head ${head} is not a parent of merge ${mergeSha}`);
  return { head, mergeSha, currentMain };
}
async function runClosure({repoRoot, manifestPath, preflightOnly=false, logger=makeLogger()}) {
  repoRoot = path.resolve(repoRoot);
  manifestPath = path.resolve(manifestPath);
  const manifestDir = path.dirname(manifestPath);
  const manifest = validateManifest(JSON.parse(fs.readFileSync(manifestPath,"utf8")));
  const log = logger;
  const state = { manifest, result:"FAIL", base:manifest.repository.expectedBaseSha };
  const ev = evidencePaths(repoRoot,manifest,manifestDir);
  let before;
  try {
    log.info(`${manifest.capabilityId} standard capability closure starting. Repo-owned branch-free engine; manifest=${manifestPath}`);
    if (!fs.existsSync(repoRoot)) throw new Error(`Repository root does not exist: ${repoRoot}`);
    spawnOwned("git",["rev-parse","--show-toplevel"],{cwd:repoRoot});
    validateRepoOperationState(repoRoot);
    before = captureUserState(repoRoot); state.before=before;
    const origin = verifyOrigin(repoRoot,manifest.repository.fullName);
    log.pass(`Repository/origin verified: ${origin}`);
    spawnOwned("gh",["auth","status","--hostname","github.com"],{cwd:repoRoot});
    const userName = spawnOwned("git",["config","user.name"],{cwd:repoRoot}).stdout.trim();
    const userEmail = spawnOwned("git",["config","user.email"],{cwd:repoRoot}).stdout.trim();
    if (!userName || !userEmail) throw new Error("Git user.name and user.email are required");
    verifyProductAuthorities(log,repoRoot,manifest.productAuthorities);

    const prs = listClosurePrs(log,repoRoot,manifest.repository.fullName,manifest.repository.closureBranch);
    if (prs.length > 1) throw new Error(`Multiple closure PRs found for ${manifest.repository.closureBranch}: ${prs.map(p=>p.number).join(", ")}`);
    const prMeta = prs.length === 1 ? fetchPr(log,repoRoot,manifest.repository.fullName,prs[0].number) : null;

    // If the exact closure is already merged, validate against the pinned base even if main has advanced.
    if (prMeta?.merged_at) {
      fetchRef(log,repoRoot,"refs/heads/main",remoteRef(repoRoot,"refs/heads/main"));
      // Ensure base object is available; fetch pull head if branch was deleted.
      if (spawnOwned("git",["cat-file","-e",`${manifest.repository.expectedBaseSha}^{commit}`],{cwd:repoRoot,allowFailure:true}).code !== 0) {
        run(log,"git",["fetch","--no-tags","origin",`pull/${prMeta.number}/head`],{cwd:repoRoot});
      }
      const prepared = loadTargetContent(log,repoRoot,manifest.repository.expectedBaseSha,manifest,manifestDir);
      state.prepared=prepared; state.closureState="ALREADY_MERGED"; state.prNumber=prMeta.number;
      const merged = verifyMergedState(log,repoRoot,manifest,manifest.repository.expectedBaseSha,prepared,prMeta);
      Object.assign(state,merged);
      state.result="PASS";
      log.pass(`${manifest.publication.passMarker}: PASS`);
      return {state,log,evidence:ev};
    }

    const liveMain = remoteRef(repoRoot,"refs/heads/main");
    if (liveMain !== manifest.repository.expectedBaseSha) throw new Error(`Live main mismatch before closure: expected ${manifest.repository.expectedBaseSha}; got ${liveMain}`);
    const base = fetchRef(log,repoRoot,"refs/heads/main",manifest.repository.expectedBaseSha);
    state.base=base;
    const prepared = loadTargetContent(log,repoRoot,base,manifest,manifestDir);
    state.prepared=prepared;
    log.pass(`Aggregate content preflight PASS: ${prepared.targets.size} changed path(s)`);

    if (preflightOnly) {
      state.closureState="PREFLIGHT_ONLY"; state.result="PASS"; state.currentMain=liveMain;
      log.pass(`${manifest.publication.passMarker}: PASS (PREFLIGHT ONLY)`);
      return {state,log,evidence:ev};
    }

    let branchSha = remoteRef(repoRoot,`refs/heads/${manifest.repository.closureBranch}`);
    let head;
    if (branchSha) {
      state.closureState = prMeta ? "RESUME_PR" : "RESUME_BRANCH";
      head = validateRemoteClosureHead(log,repoRoot,branchSha,base,manifest,prepared);
    } else {
      if (prMeta) throw new Error(`Closure PR #${prMeta.number} exists but closure branch is absent before merge`);
      state.closureState="BUILD_PUSH_CREATE";
      head = buildCommit(log,repoRoot,base,manifest,prepared);
      validateCommitContract(log,repoRoot,base,head,manifest,prepared);
      run(log,"git",["push","origin",`${head}:refs/heads/${manifest.repository.closureBranch}`],{cwd:repoRoot});
      const remoteHead = remoteRef(repoRoot,`refs/heads/${manifest.repository.closureBranch}`);
      if (remoteHead !== head) throw new Error(`Published closure head mismatch: expected ${head}; got ${remoteHead}`);
      log.pass(`Published exact closure head: ${head}`);
    }
    state.head=head;

    let pr;
    if (!prMeta) {
      const create = run(log,"gh",["pr","create","--repo",manifest.repository.fullName,"--base","main","--head",manifest.repository.closureBranch,"--title",manifest.publication.prTitle,"--body",manifest.publication.prBody],{cwd:repoRoot});
      log.info(`PR create output: ${create.stdout.trim()}`);
      const nowPrs = listClosurePrs(log,repoRoot,manifest.repository.fullName,manifest.repository.closureBranch);
      if (nowPrs.length !== 1) throw new Error(`Expected exactly one closure PR after creation; found ${nowPrs.length}`);
      pr = fetchPr(log,repoRoot,manifest.repository.fullName,nowPrs[0].number);
    } else pr = prMeta;
    state.prNumber=pr.number;
    if (pr.merged_at) {
      const merged = verifyMergedState(log,repoRoot,manifest,base,prepared,pr);
      Object.assign(state,merged);
      state.result="PASS"; log.pass(`${manifest.publication.passMarker}: PASS`);
      return {state,log,evidence:ev};
    }
    if (pr.state !== "open") throw new Error(`Closure PR #${pr.number} is ${pr.state} and not merged`);
    if (pr.base?.ref !== "main") throw new Error(`Closure PR #${pr.number} base is not main`);
    if (pr.head?.ref !== manifest.repository.closureBranch) throw new Error(`Closure PR #${pr.number} head branch mismatch`);
    if (normSha(pr.head?.sha,"closure PR head") !== head) throw new Error(`Closure PR #${pr.number} head SHA mismatch`);

    waitChecks(log,repoRoot,manifest,head,base);
    const preMergeMain = remoteRef(repoRoot,"refs/heads/main");
    const preMergeBranch = remoteRef(repoRoot,`refs/heads/${manifest.repository.closureBranch}`);
    if (preMergeMain !== base || preMergeBranch !== head) throw new Error(`Authority drift immediately before merge: main=${preMergeMain} branch=${preMergeBranch}`);
    const freshPr = fetchPr(log,repoRoot,manifest.repository.fullName,pr.number);
    if (freshPr.state !== "open" || freshPr.merged_at) throw new Error(`Closure PR #${pr.number} changed state before merge`);
    run(log,"gh",["pr","merge",String(pr.number),"--repo",manifest.repository.fullName,"--merge","--match-head-commit",head],{cwd:repoRoot});
    const mergedPr = fetchPr(log,repoRoot,manifest.repository.fullName,pr.number);
    const merged = verifyMergedState(log,repoRoot,manifest,base,prepared,mergedPr);
    Object.assign(state,merged);
    state.result="PASS";
    log.pass(`${manifest.publication.passMarker}: PASS`);
    return {state,log,evidence:ev};
  } catch (e) {
    state.error = e?.stack ?? String(e);
    logger.error(state.error);
    throw Object.assign(new Error(e?.message ?? String(e)), { closureState:state, logger, evidence:ev });
  } finally {
    try {
      if (before) {
        const after = captureUserState(repoRoot); state.after=after;
        assertUserStatePreserved(before,after);
        logger.pass(`User checkout/status preserved exactly: ${after.branch || "(detached)"}@${after.head}; status=${after.statusHash}`);
      }
    } catch (preserveErr) {
      state.error = `${state.error ? state.error+"\n" : ""}USER STATE PRESERVATION FAILURE: ${preserveErr.stack ?? preserveErr}`;
      state.result="FAIL";
      logger.error(`USER STATE PRESERVATION FAILURE: ${preserveErr.message}`);
    }
    try { writeEvidence(ev,logger,state); } catch (writeErr) { console.error(`Evidence write failed: ${writeErr.message}`); }
  }
}

async function selfTest() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(),"payroll-closure-selftest-"));
  try {
    spawnOwned("git",["init","-q"],{cwd:tmp});
    spawnOwned("git",["config","user.name","Closure Self Test"],{cwd:tmp});
    spawnOwned("git",["config","user.email","closure-selftest@example.invalid"],{cwd:tmp});
    fs.mkdirSync(path.join(tmp,"backlog"),{recursive:true});
    fs.mkdirSync(path.join(tmp,"docs"),{recursive:true});
    const csv = [
      "Detailed Story ID,Reconciled Evidence Status,Reconciliation Basis,Legal/Domain Revalidation",
      'PLN-X-001,NOT EVIDENCED,"old, basis",NO SPECIAL FLAG',
      "PLN-X-002,NOT STARTED,untouched,LEGAL/DOMAIN REVIEW REQUIRED",
      "PLN-X-003,PARTIALLY IMPLEMENTED,untouched,NO SPECIAL FLAG",
    ].join("\n")+"\n";
    fs.writeFileSync(path.join(tmp,"backlog","stories.csv"),csv);
    fs.writeFileSync(path.join(tmp,"docs","status.md"),"CURRENT: old\n");
    spawnOwned("git",["add","--","backlog/stories.csv","docs/status.md"],{cwd:tmp});
    spawnOwned("git",["commit","-qm","base"],{cwd:tmp});
    const base=spawnOwned("git",["rev-parse","HEAD"],{cwd:tmp}).stdout.trim();
    const ledgerBlob=spawnOwned("git",["rev-parse",`${base}:backlog/stories.csv`],{cwd:tmp}).stdout.trim();
    const statusBlob=spawnOwned("git",["rev-parse",`${base}:docs/status.md`],{cwd:tmp}).stdout.trim();
    // Dirty user state must survive branch-free commit construction.
    fs.writeFileSync(path.join(tmp,"local scratch.txt"),"keep me\n");
    const before=captureUserState(tmp);
    const packageDir=fs.mkdtempSync(path.join(os.tmpdir(),"closure package with spaces "));
    fs.mkdirSync(path.join(packageDir,"payload"),{recursive:true});
    const statusPayload=Buffer.from("CURRENT: closed\n","utf8");
    fs.writeFileSync(path.join(packageDir,"payload","status.md"),statusPayload);
    const manifest=validateManifest({
      schemaVersion:1, capabilityId:"SELF-TEST", title:"Self test",
      repository:{fullName:"owner/repo",baseBranch:"main",expectedBaseSha:base,closureBranch:"docs/self-test-closure"},
      productAuthorities:[],
      storyLedger:{
        path:"backlog/stories.csv",sourceBlob:ledgerBlob,expectedRows:3,idColumn:"Detailed Story ID",
        rows:[{id:"PLN-X-001",expect:{"Reconciled Evidence Status":"NOT EVIDENCED"},set:{"Reconciled Evidence Status":"IMPLEMENTED","Reconciliation Basis":"new basis"}}]
      },
      filePayloads:[{path:"docs/status.md",sourceBlob:statusBlob,payload:"payload/status.md",payloadSha256:sha256(statusPayload)}],
      finalAssertions:[{path:"docs/status.md",contains:[{text:"CURRENT: closed",count:1}],notContains:["CURRENT: old"]}],
      publication:{commitMessage:"self test closure",prTitle:"self test",prBody:"",expectedChecks:["check-a"],mergeMethod:"merge",deleteBranch:false,allowedPaths:["backlog/stories.csv","docs/status.md"],passMarker:"SELF_TEST"}
    });
    const prepared=loadTargetContent(makeLogger(),tmp,base,manifest,packageDir);
    if (prepared.ledgerDelta.length!==1) throw new Error("self-test ledger delta mismatch");
    const head=buildCommit(makeLogger(),tmp,base,manifest,prepared);
    validateCommitContract(makeLogger(),tmp,base,head,manifest,prepared);
    const after=captureUserState(tmp);
    assertUserStatePreserved(before,after);
    const out=decodeUtf8(readCommitTarget(tmp,head,"backlog/stories.csv"),"selftest ledger");
    if (!out.includes("PLN-X-001,IMPLEMENTED,new basis,NO SPECIAL FLAG")) throw new Error("self-test target row missing");
    if (!out.includes("PLN-X-002,NOT STARTED,untouched,LEGAL/DOMAIN REVIEW REQUIRED")) throw new Error("self-test untouched legal row changed");
    // Negative cases.
    let failed=0;
    try { applyStoryLedger(csv,{expectedRows:3,idColumn:"Detailed Story ID",rows:[{id:"PLN-X-001",expect:{"Reconciled Evidence Status":"IMPLEMENTED"},set:{"Reconciled Evidence Status":"IMPLEMENTED"}}]}); }
    catch { failed++; }
    try { validateManifest({...manifest,publication:{...manifest.publication,allowedPaths:["docs/status.md"]}}); }
    catch { failed++; }
    if (failed!==2) throw new Error(`self-test expected 2 negative failures; got ${failed}`);
    console.log("PAYROLL_CAPABILITY_CLOSURE_ENGINE_SELF_TEST: PASS");
  } finally {
    fs.rmSync(tmp,{recursive:true,force:true});
  }
}

async function main() {
  const args=parseArgs(process.argv.slice(2));
  if (args.selfTest) { await selfTest(); return; }
  if (!args.repoRoot || !args.manifestPath) throw new Error("--repo-root and --manifest are required");
  try {
    await runClosure({repoRoot:args.repoRoot,manifestPath:args.manifestPath,preflightOnly:args.preflightOnly});
  } catch (e) {
    const st=e.closureState;
    if (st?.manifest?.publication?.passMarker) console.error(`${st.manifest.publication.passMarker}: FAIL`);
    process.exitCode=1;
  }
}
const invoked = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invoked) main().catch(e=>{console.error(e.stack??e); process.exitCode=1;});

export {
  validateManifest, splitCsvRecords, parseCsvRecord, applyStoryLedger,
  loadTargetContent, buildCommit, validateCommitContract, captureUserState,
  assertUserStatePreserved, sha256, runClosure, selfTest
};
