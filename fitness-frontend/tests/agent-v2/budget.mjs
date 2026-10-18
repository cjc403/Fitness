// Real provider usage only. Missing diagnostics must stop a budgeted wave.
export function usageOf(rows){
 const result={tokens:0,calls:0,unknown:[]}
 for(const row of rows){
  if(row.environmentError&&!row.taskId)continue
  const d=row.diagnostics
  if(!row.taskId||d?.usageComplete!==true||!Number.isSafeInteger(d?.totalTokens)||d.totalTokens<0||!Number.isSafeInteger(d?.modelCalls)||d.modelCalls<0){result.unknown.push(row.id);continue}
  result.tokens+=d.totalTokens;result.calls+=d.modelCalls
 }return result
}
export function usageStats(rows){
 const measured=rows.filter(r=>r.taskId&&r.diagnostics?.usageComplete===true&&Number.isSafeInteger(r.diagnostics.totalTokens)&&r.diagnostics.totalTokens>=0&&Number.isSafeInteger(r.diagnostics.modelCalls)&&r.diagnostics.modelCalls>=0)
 const stats=values=>{values=values.filter(Number.isFinite).sort((a,b)=>a-b);return {p50:values[Math.max(0,Math.ceil(values.length*.5)-1)]??null,p95:values[Math.max(0,Math.ceil(values.length*.95)-1)]??null,max:values.at(-1)??null}}
 return {measured:measured.length,totals:usageOf(rows),tokens:stats(measured.map(r=>r.diagnostics.totalTokens)),calls:stats(measured.map(r=>r.diagnostics.modelCalls)),durationSeconds:stats(measured.map(r=>(Date.parse(r.endedAt)-Date.parse(r.startedAt))/1000).filter(v=>v>=0))}
}
export function guardWaveBudget(report){
 const limit=Number(process.env.AGENT_V2_WAVE_TOKEN_BUDGET||0);if(!limit)return
 if(!Number.isSafeInteger(limit)||limit<0)throw Error('Invalid real-model token budget')
 const usage=usageOf(report.cases);report.usage=usage
 if(usage.unknown.length)throw Error('Cannot verify provider usage; stop before another task: '+usage.unknown.join(','))
 // Reserve a whole bounded task, rather than initiating another task at the edge.
 if(limit-usage.tokens<200000)throw Error('Real-model wave budget exhausted; remaining cases are NOT_RUN')
}
export async function captureUsage(page,entry){
 if(!page||!entry.taskId)return
 try{entry.diagnostics=await page.evaluate(async id=>{const r=await fetch('/api/v1/agent/tasks/'+id+'/diagnostics',{headers:{authorization:'Bearer '+localStorage.token},cache:'no-store'}).then(r=>r.json());if(r.code!==0)throw Error('Diagnostics unavailable');return r.data},entry.taskId)}catch{entry.usageUnverified=true}
}
