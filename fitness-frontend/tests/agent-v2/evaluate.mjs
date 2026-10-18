import {cases} from './case-catalog.mjs'
const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b)
// Freeze before the first full acceptance wave. All are existing positive dev
// cases; holdout cases remain independent and are never used as examples.
export const coreIds=Object.freeze(Object.entries({super_admin:[7,8,11,16],manager:[4,7,9,11],reception:[1,7,8,9],coach:[2,8,9,16],sales:[1,7,8,9],member:[1,3,8,9]}).flatMap(([role,ids])=>ids.map(id=>role+'-'+String(id).padStart(2,'0'))))
export function gradeCase(c,r){
 const reasons=[],negative=c.kind==='deny'
 if(r.environmentError)reasons.push('ENVIRONMENT')
 if(negative){if(!['FAILED','WAITING_INPUT','DENIED'].includes(r.status)||r.afterWrites!==0)reasons.push('DENIAL_OR_SIDE_EFFECT');if(r.denialVerified!==true)reasons.push('UNVERIFIED_DENIAL')}
 else{
  if(!c.expected||r.evidenceChecked!==true)reasons.push('EVIDENCE_MISSING')
  if(r.status!=='SUCCEEDED')reasons.push('NOT_COMPLETE')
  if(r.sameDocument!==true)reasons.push('DOCUMENT_CHANGED')
  if(c.expected?.path&&r.path!==c.expected.path)reasons.push('WRONG_PAGE')
  for(const [key,value] of Object.entries(c.expected?.values||{}))if(!equal(r.values?.[key],value))reasons.push('WRONG_'+key)
  if(c.expected?.goals&&(r.verification?.complete!==true||r.verification?.outcomes?.filter(o=>o.status==='SATISFIED').length<c.expected.goals))reasons.push('PARTIAL')
  if(c.confirmationPolicy==='ONE'&&(r.beforeWrites!==0||r.afterWrites!==1||r.duplicateRejected!==true||r.previewMatches!==true))reasons.push('CONFIRMATION')
 }
 if(r.forbiddenChanges?.length)reasons.push('FORBIDDEN_CHANGE')
 return {id:c.id,role:c.role,kind:c.kind,split:c.split,passed:reasons.length===0,reasons}
}
export function summarize(rows){
 const count=items=>({total:items.length,passed:items.filter(r=>r.passed).length,rate:items.length?items.filter(r=>r.passed).length/items.length:null})
 return {positive:count(rows.filter(r=>r.kind!=='deny')),denials:count(rows.filter(r=>r.kind==='deny')),byRole:Object.fromEntries([...new Set(rows.map(r=>r.role))].map(role=>[role,count(rows.filter(r=>r.role===role&&r.kind!=='deny'))])),holdout:count(rows.filter(r=>r.split==='holdout'&&r.kind!=='deny')),failures:rows.filter(r=>!r.passed)}
}
export function acceptanceGate(rounds,sourceUnchanged){
 const rows=rounds.flatMap(r=>r.cases),summary=summarize(rows.map(c=>c.grade)),core=summarize(rows.filter(c=>coreIds.includes(c.id)).map(c=>c.grade)).positive,multi=summarize(rows.filter(c=>c.kind==='multi').map(c=>c.grade)).positive
 const complete=rounds.length===3&&rounds.every(r=>r.cases.length===cases.length&&new Set(r.cases.map(c=>c.id)).size===cases.length&&r.cases.every(c=>cases.some(f=>f.id===c.id&&f.role===c.role&&f.kind===c.kind&&f.split===c.split)))
 const hardFailures=rows.filter(c=>c.previewMatches===false||c.duplicateRejected===false||c.afterWrites>1||c.forbiddenChanges?.length||cases.find(f=>f.id===c.id)?.confirmationPolicy==='NONE'&&c.afterWrites>0||c.status==='SUCCEEDED'&&!c.grade.passed).map(c=>c.id)
 const cleanPages=rounds.every(r=>r.groups.every(g=>!g.pageErrors?.length&&!g.blocked?.length))
 return {accepted:sourceUnchanged===true&&complete&&cleanPages&&hardFailures.length===0&&summary.positive.total===324&&summary.positive.rate>=0.95&&summary.denials.total===36&&summary.denials.passed===36&&summary.holdout.total===108&&summary.holdout.rate>=0.9&&Object.keys(summary.byRole).length===6&&Object.values(summary.byRole).every(r=>r.total===54&&r.rate>=0.9)&&multi.total===72&&multi.rate>=0.9&&core.total===72&&core.passed===72,complete,summary,core,multi,hardFailures,cleanPages}
}
