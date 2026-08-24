#!/usr/bin/env node
import { resolve } from "node:path";
import { git, gitState, remoteHead, ensureCommit, csvObjects, keyedRows, scanMigrationsAt, writeJson, readJson, assert, selfTestCommon, showAt, blobAt } from "./lib/common.mjs";

const argv=process.argv.slice(2), get=n=>{const i=argv.indexOf(n);return i>=0?argv[i+1]:null};
if(argv.includes("--self-test")){selfTestCommon();console.log("PAYROLL_AUTHORITY_SNAPSHOT_SELF_TEST: PASS");process.exit(0)}
const configPath=get("--config"), outputPath=get("--output");assert(configPath&&outputPath,"Usage: --config <json> --output <json>","USAGE");
const c=readJson(resolve(configPath));const failures=[];
async function repoAuthority(p,branch,expected){
  const root=git(resolve(p),["rev-parse","--show-toplevel"]).stdout.trim();const origin=git(root,["config","--get","remote.origin.url"]).stdout.trim();
  const remoteMain=await remoteHead(root,branch||"main");await ensureCommit(root,remoteMain);
  if(expected&&remoteMain!==expected)failures.push({code:"REMOTE_MAIN_MISMATCH",repo:root,expected,actual:remoteMain});
  return {root,origin,remoteMain,...gitState(root)};
}
const backend=await repoAuthority(c.backendRepo,c.backendBranch||"main",c.expectedBackendRemoteMain);const ui=c.uiRepo?await repoAuthority(c.uiRepo,c.uiBranch||"main",c.expectedUiRemoteMain):null;
function readCsvSource(source,label){
  if(!source)return null;
  try{
    const text=showAt(backend.root,backend.remoteMain,source.path), parsed=csvObjects(text);for(const h of source.requiredHeaders||[])if(!parsed.header.includes(h))failures.push({code:`${label}_HEADER_MISSING`,header:h});
    const key=source.keyField||"Detailed Story ID";if(!parsed.header.includes(key))failures.push({code:`${label}_KEY_HEADER_MISSING`,header:key});
    const {map,duplicates}=keyedRows(parsed.rows,key);for(const d of duplicates)failures.push({code:`${label}_DUPLICATE_KEY`,key:d});
    const ids=source.ids||[];const selected=[];for(const id of ids){if(!map.has(id))failures.push({code:`${label}_ID_MISSING`,id});else selected.push(map.get(id))}
    return {path:source.path,blob:blobAt(backend.root,backend.remoteMain,source.path),header:parsed.header,keyField:key,requestedIds:ids,rows:selected};
  }catch(e){failures.push({code:`${label}_READ_FAILED`,message:e.message});return null}
}
const stories=readCsvSource(c.storySource,"STORY"), uiApplicability=readCsvSource(c.uiSource,"UI");
let capabilityState=null;
try{const p=c.capabilityStatePath||"docs/governance/payroll-capability-state.json";capabilityState={path:p,blob:blobAt(backend.root,backend.remoteMain,p),value:JSON.parse(showAt(backend.root,backend.remoteMain,p))}}catch(e){failures.push({code:"CAPABILITY_STATE_READ_FAILED",message:e.message})}
const migrations=scanMigrationsAt(backend.root,backend.remoteMain,c.migrationRoots||[]), versions=migrations.map(x=>x.version), max=versions.length?Math.max(...versions):0, next=`V${String(max+1).padStart(3,"0")}`;
const duplicateVersions=[...new Set(versions.filter((v,i)=>versions.indexOf(v)!==i))];for(const v of duplicateVersions)failures.push({code:"MIGRATION_VERSION_DUPLICATE",version:v});
if(capabilityState?.value?.migration?.next&&capabilityState.value.migration.next!==next)failures.push({code:"MIGRATION_NEXT_STATE_MISMATCH",computed:next,state:capabilityState.value.migration.next});
const out={schemaVersion:"2.0",generatedAt:new Date().toISOString(),backend,ui,capabilityState,migration:{source:"REMOTE_MAIN_TREE",maxCommitted:max,next,reservedBy:capabilityState?.value?.migration?.reservedBy??null,rows:migrations},stories,uiApplicability,failures};
writeJson(resolve(outputPath),out);console.log(failures.length?"PAYROLL_AUTHORITY_SNAPSHOT: BLOCKED":"PAYROLL_AUTHORITY_SNAPSHOT: PASS");process.exit(failures.length?2:0);
