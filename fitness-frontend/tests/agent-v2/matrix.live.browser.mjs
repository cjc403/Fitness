// Full frozen acceptance; opt in explicitly. Every subprocess uses real paid-model
// turns and the same marked database checks. This is deliberately outside npm test.
import assert from 'node:assert/strict'
import {spawn} from 'node:child_process'
import {createHash} from 'node:crypto'
import {readFile,writeFile,readdir,mkdir} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import {cases} from './case-catalog.mjs'
import {summarize,acceptanceGate,coreIds} from './evaluate.mjs'
import {assertV2Target} from './fixtures.mjs'
assert.equal(process.env.AGENT_V2_FULL_MATRIX,'true','Set AGENT_V2_FULL_MATRIX=true to opt in to all paid-model runs')
const repo=fileURLToPath(new URL('../../../',import.meta.url)),out=path.join(repo,'docs/qa/agent-v2-20261017'),prefix=process.env.AGENT_V2_WAVE||('candidate-'+Date.now()),rounds=Number(process.env.AGENT_V2_ROUNDS||3)
assert.ok(Number.isInteger(rounds)&&rounds>=1&&rounds<=3);await mkdir(out,{recursive:true});await assertV2Target()
async function fingerprint(){
 const files=[];async function walk(dir){for(const e of await readdir(path.join(repo,dir),{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())await walk(p);else files.push(p)}}
 for(const root of ['fitness-backend/src/main','fitness-frontend/src','fitness-frontend/tests/agent-v2'])await walk(root)
 files.push('fitness-backend/pom.xml','fitness-frontend/package-lock.json')
 const hash=createHash('sha256');for(const p of files.sort()){hash.update(p.replaceAll('\\','/'));hash.update(await readFile(path.join(repo,p)))}return hash.digest('hex')
}
const source=await fingerprint(),report={prefix,sourceFingerprint:source,coreIds,startedAt:new Date().toISOString(),rounds:[],totalExpected:cases.length*rounds}
const groups=[
 {name:'queries',script:'evaluate.browser.mjs',select:c=>['query','deny'].includes(c.kind)||c.kind==='multi'&&c.confirmationPolicy==='NONE'},
 {name:'writes',script:'workflows.live.browser.mjs',select:c=>c.kind==='write'||c.kind==='multi'&&c.confirmationPolicy==='ONE'},
 {name:'recovery',script:'recovery.live.browser.mjs',select:c=>c.kind==='recovery'}
]
async function save(){await writeFile(path.join(out,'matrix-'+prefix+'.json'),JSON.stringify(report,null,2))}
for(let round=1;round<=rounds;round++){
 const row={round,cases:[],groups:[]};report.rounds.push(row)
 for(const group of groups){
  assert.equal(await fingerprint(),source,'Product source changed during acceptance; this wave is invalid')
  const wave=prefix+'-r'+round+'-'+group.name,selected=cases.filter(group.select),script=fileURLToPath(new URL('./'+group.script,import.meta.url));assert.ok(selected.length)
  console.log('START '+wave+' '+selected.length+' cases')
  const code=await new Promise((resolve,reject)=>{const child=spawn(process.execPath,[script],{cwd:repo,env:{...process.env,AGENT_V2_WAVE:wave,AGENT_V2_CASE_IDS:selected.map(c=>c.id).join(',')},windowsHide:true,stdio:'inherit'});child.on('error',reject);child.on('exit',resolve)})
  const evidence=JSON.parse(await readFile(path.join(out,'evaluation-'+wave+'.json'),'utf8'));assert.deepEqual(evidence.cases.map(c=>c.id).sort(),selected.map(c=>c.id).sort(),'Every selected frozen case must be reported')
  row.cases.push(...evidence.cases);row.groups.push({wave,exitCode:code,pageErrors:evidence.errors,blocked:evidence.blocked});row.summary=summarize(row.cases.map(c=>c.grade));await save()
 }
 const s=row.summary;row.accepted=s.positive.total===108&&s.denials.total===12&&s.positive.rate>=0.95&&s.denials.passed===12&&s.holdout.rate>=0.9&&Object.values(s.byRole).every(r=>r.rate>=0.9)&&row.groups.every(g=>!g.pageErrors?.length&&!g.blocked?.length)
 row.multi=summarize(row.cases.filter(c=>c.kind==='multi').map(c=>c.grade)).positive;row.accepted&&=row.multi.rate>=0.9
 // A confirmed wrong payload, unexpected business write or duplicate is a hard gate,
 // regardless of the statistical completion threshold.
 row.hardFailures=row.cases.filter(c=>c.previewMatches===false||c.duplicateRejected===false||c.afterWrites>1||c.forbiddenChanges?.length||cases.find(f=>f.id===c.id).confirmationPolicy==='NONE'&&c.afterWrites>0||c.status==='SUCCEEDED'&&!c.grade.passed).map(c=>c.id)
 row.accepted&&=row.hardFailures.length===0;await save();console.log('ROUND '+round+' '+JSON.stringify({accepted:row.accepted,summary:row.summary}))
}
report.sourceUnchanged=await fingerprint()===source;report.completedAt=new Date().toISOString();report.gate=acceptanceGate(report.rounds,report.sourceUnchanged);report.accepted=report.gate.accepted;await save();console.log('MATRIX '+JSON.stringify({accepted:report.accepted,sourceUnchanged:report.sourceUnchanged,gate:report.gate}));if(!report.accepted)process.exitCode=1
