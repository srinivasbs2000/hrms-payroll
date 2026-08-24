import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";

export function assert(cond,msg,code="ASSERTION_FAILED"){if(!cond){const e=new Error(msg);e.code=code;throw e}}
export function sha256Bytes(b){return createHash("sha256").update(b).digest("hex")}
export function sha256File(p){return sha256Bytes(readFileSync(p))}
export function ensureDir(p){mkdirSync(p,{recursive:true})}
export function readJson(p){return JSON.parse(readFileSync(p,"utf8"))}
export function writeJson(p,v){ensureDir(dirname(p));writeFileSync(p,JSON.stringify(v,null,2)+"\n","utf8")}
export function canonical(v){if(Array.isArray(v))return v.map(canonical);if(v&&typeof v==="object")return Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])]));return v}
export function sameJson(a,b){return JSON.stringify(canonical(a))===JSON.stringify(canonical(b))}
export function validSha(s){return typeof s==="string"&&/^[0-9a-f]{40}$/i.test(s)}

export function run(cmd,args,{cwd,allow=[0],env}={}){
  const r=spawnSync(cmd,args,{cwd,encoding:"utf8",windowsHide:true,env:{...process.env,...(env||{})}});
  const exitCode=Number.isInteger(r.status)?r.status:-1;
  const out={command:cmd,args:[...args],cwd:cwd||null,exitCode,stdout:r.stdout||"",stderr:r.stderr||"",error:r.error?String(r.error):null};
  if(!allow.includes(exitCode)) throw Object.assign(new Error(`${cmd} exited ${exitCode}: ${out.stderr||out.error||""}`),{code:"PROCESS_FAILED",detail:out});
  return out;
}
export function git(cwd,args,opt={}){return run("git",args,{cwd,...opt})}
export async function retry(fn,{attempts=3,delayMs=300}={}){let last;for(let i=0;i<attempts;i++){try{return await fn(i)}catch(e){last=e;if(i+1<attempts)await new Promise(r=>setTimeout(r,delayMs*(i+1)))}}throw last}
export function nulList(s){return s?s.split("\0").filter(Boolean):[]}
export function gitState(cwd){return {
  head:git(cwd,["rev-parse","HEAD"]).stdout.trim(),
  branch:git(cwd,["symbolic-ref","--short","-q","HEAD"],{allow:[0,1]}).stdout.trim()||"DETACHED",
  staged:nulList(git(cwd,["diff","--cached","--name-only","-z"]).stdout).sort(),
  tracked:nulList(git(cwd,["diff","--name-only","-z"]).stdout).sort(),
  untracked:nulList(git(cwd,["ls-files","--others","--exclude-standard","-z"]).stdout).sort()
}}
export async function remoteHead(cwd,branch="main",remote="origin"){
  const r=await retry(async()=>git(cwd,["ls-remote",remote,`refs/heads/${branch}`]));
  const sha=r.stdout.trim().split(/\s+/)[0]||null;
  assert(validSha(sha),`Remote branch ${branch} not found or invalid`,"REMOTE_BRANCH_MISSING");
  return sha;
}
export async function ensureCommit(cwd,sha,{remote="origin"}={}){
  assert(validSha(sha),`Invalid commit SHA ${sha||"<empty>"}`,"INVALID_COMMIT_SHA");
  let have=git(cwd,["cat-file","-e",`${sha}^{commit}`],{allow:[0,1,128]}).exitCode===0;
  if(!have){await retry(async()=>git(cwd,["fetch","--no-tags",remote,sha]));have=git(cwd,["cat-file","-e",`${sha}^{commit}`],{allow:[0,1,128]}).exitCode===0}
  assert(have,`Commit ${sha} unavailable after fetch`,"REMOTE_OBJECT_UNAVAILABLE");
  const exact=git(cwd,["rev-parse",`${sha}^{commit}`]).stdout.trim();
  assert(exact===sha,`Fetched commit mismatch ${exact}`,"FETCHED_COMMIT_MISMATCH");
  return sha;
}
export function showAt(cwd,sha,path){return git(cwd,["show",`${sha}:${path}`]).stdout}
export function blobAt(cwd,sha,path){return git(cwd,["rev-parse",`${sha}:${path}`]).stdout.trim()}
export function pathExistsAt(cwd,sha,path){return git(cwd,["cat-file","-e",`${sha}:${path}`],{allow:[0,1,128]}).exitCode===0}
export function treePathsAt(cwd,sha,roots=[]){const a=["ls-tree","-r","--name-only",sha];if(roots.length)a.push("--",...roots);return git(cwd,a).stdout.split(/\r?\n/).filter(Boolean)}
export function scanMigrationsAt(cwd,sha,roots=[]){const rows=[];for(const p of treePathsAt(cwd,sha,roots)){const n=p.split("/").pop();const m=/^V(\d+)__.*\.sql$/i.exec(n);if(m)rows.push({path:p,version:Number(m[1])})}return rows.sort((a,b)=>a.version-b.version||a.path.localeCompare(b.path))}

export function parseCsv(text){
  const rows=[];let row=[],field="",quoted=false;
  for(let i=0;i<text.length;i++){
    const c=text[i];
    if(quoted){if(c==='"'&&text[i+1]==='"'){field+='"';i++}else if(c==='"')quoted=false;else field+=c}
    else if(c==='"')quoted=true;else if(c===','){row.push(field);field=""}else if(c==='\n'){row.push(field.replace(/\r$/,""));rows.push(row);row=[];field=""}else field+=c;
  }
  assert(!quoted,"Unterminated CSV quote","CSV_QUOTE_UNTERMINATED");
  if(field.length||row.length){row.push(field.replace(/\r$/,""));rows.push(row)}
  return rows;
}
export function csvObjects(text){
  const rows=parseCsv(text);if(!rows.length)return {header:[],rows:[]};
  const header=rows[0];assert(header.every(h=>h!==""),"CSV contains empty header","CSV_HEADER_EMPTY");
  assert(new Set(header).size===header.length,"CSV contains duplicate headers","CSV_HEADER_DUPLICATE");
  const objects=[];
  for(const r of rows.slice(1).filter(r=>r.some(x=>x!==""))){assert(r.length<=header.length,"CSV row has more fields than header","CSV_ROW_TOO_WIDE");objects.push(Object.fromEntries(header.map((h,i)=>[h,r[i]??""])))}
  return {header,rows:objects};
}
export function keyedRows(rows,keyField){
  const map=new Map(),dupes=[];
  for(const row of rows){const k=row[keyField];if(map.has(k))dupes.push(k);else map.set(k,row)}
  return {map,duplicates:[...new Set(dupes)].sort()};
}
export function selfTestCommon(){
  const o=csvObjects('ID,Status,Text\nA,OPEN,"x,y"\n');assert(o.rows[0].Text==="x,y","CSV parser failed");
  let failed=false;try{csvObjects("A,A\n1,2\n")}catch(e){failed=e.code==="CSV_HEADER_DUPLICATE"}assert(failed,"duplicate-header test failed");
  assert(sha256Bytes(Buffer.from("abc"))==="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","SHA test failed");
}
