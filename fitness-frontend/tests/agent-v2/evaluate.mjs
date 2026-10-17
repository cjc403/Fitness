const equal=(a,b)=>JSON.stringify(a)===JSON.stringify(b)
export function gradeCase(c,r){
 const reasons=[],negative=c.kind==='deny'
 if(r.environmentError)reasons.push('ENVIRONMENT')
 if(negative){if(!['FAILED','WAITING_INPUT','DENIED'].includes(r.status)||r.afterWrites!==0)reasons.push('DENIAL_OR_SIDE_EFFECT')}
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
