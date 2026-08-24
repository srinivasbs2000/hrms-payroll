#!/usr/bin/env node
import { resolve } from "node:path";
import { git, remoteHead, ensureCommit, csvObjects, keyedRows, writeJson, readJson, assert, selfTestCommon, showAt, blobAt } from "./lib/common.mjs";
const argv=process.argv.slice(2), get=n=>{const i=argv.indexOf(n);return i>=0?argv[i+1]:null};
if(argv.includes("--self-test")){selfTestCommon();console.log("PAYROLL_R3_RECONCILIATION_SELF_TEST: PASS");process.exit(0)}
const manifestPath=get("--manifest");assert(manifestPath,"Usage: --manifest <json>","USAGE");const m=readJson(resolve(manifestPath)), repo=resolve(m.repoRoot), issues=[];
const origin=git(repo,["config","--get","remote.origin.url"]).stdout.trim();let remoteMain=null;
try{remoteMain=await remoteHead(repo,m.remoteBranch||"main");await ensureCommit(repo,remoteMain);if(m.expectedRemoteMain&&remoteMain!==m.expectedRemoteMain)issues.push({code:"REMOTE_MAIN_MISMATCH",expected:m.expectedRemoteMain,actual:remoteMain})}catch(e){issues.push({code:"REMOTE_AUTHORITY_UNAVAILABLE",message:e.message})}
function load(source,label){
  if(!source){issues.push({code:`${label}_SOURCE_REQUIRED`});return {source:null,header:[],map:new Map(),rows:[]}}
  if(!remoteMain)return {source:null,header:[],map:new Map(),rows:[]};
  try{const text=showAt(repo,remoteMain,source.path), parsed=csvObjects(text), key=source.keyField||"Detailed Story ID";for(const h of source.requiredHeaders||[])if(!parsed.header.includes(h))issues.push({code:`${label}_HEADER_MISSING`,header:h});if(!parsed.header.includes(key))issues.push({code:`${label}_KEY_HEADER_MISSING`,header:key});const keyed=keyedRows(parsed.rows,key);for(const d of keyed.duplicates)issues.push({code:`${label}_DUPLICATE_KEY`,key:d});return {source:{path:source.path,blob:blobAt(repo,remoteMain,source.path),keyField:key},header:parsed.header,map:keyed.map,rows:parsed.rows}}
  catch(e){issues.push({code:`${label}_READ_FAILED`,message:e.message});return {source:null,header:[],map:new Map(),rows:[]}}
}
const story=load(m.storySource,"STORY"), ui=load(m.uiSource,"UI");
function interpret(spec,kind){const out=[];for(const c of spec||[]){const sr=story.map.get(c.id),ur=ui.map.get(c.id);if(!sr)issues.push({code:`${kind}_STORY_MISSING`,id:c.id});if(!ur)issues.push({code:`${kind}_UI_MISSING`,id:c.id});const mm=[];for(const [f,v] of Object.entries(c.expect?.story||{}))if(sr&&sr[f]!==v)mm.push({source:"story",field:f,expected:v,actual:sr[f]});for(const [f,v] of Object.entries(c.expect?.ui||{}))if(ur&&ur[f]!==v)mm.push({source:"ui",field:f,expected:v,actual:ur[f]});if(mm.length)issues.push({code:`${kind}_PRECONDITION_MISMATCH`,id:c.id,mismatches:mm});out.push({id:c.id,story:sr||null,ui:ur||null,classification:c.classification||null})}return out}
const selected=interpret(m.candidates,"CANDIDATE"), comparisons=interpret(m.comparisons,"COMPARISON");
const out={engineVersion:"2.0.0",generatedAt:new Date().toISOString(),result:issues.length?"BLOCKED":"PASS",repository:{origin,remoteMain},schema:{story:{...story.source,header:story.header},ui:{...ui.source,header:ui.header}},selected,comparisons,issues,decision:m.decision||null,next:m.next||null};
writeJson(resolve(m.output),out);if(m.failureMatrix)writeJson(resolve(m.failureMatrix),issues);console.log(issues.length?"PAYROLL_R3_RECONCILIATION: BLOCKED":"PAYROLL_R3_RECONCILIATION: PASS");process.exit(issues.length?2:0);
